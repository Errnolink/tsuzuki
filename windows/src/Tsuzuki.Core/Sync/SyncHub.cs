using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Google.Protobuf;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Connections;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Storage;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Sync;

/// <summary>
/// SyncYomi protocol v2 server on Kestrel, bound to all IPv4 interfaces. Endpoints,
/// headers, status codes and merge behaviour follow docs/research/desktop-v2/01-sync-hub-spec.md.
/// </summary>
public sealed class SyncHub : ISyncHub, IDisposable
{
    private const string HeaderDeviceId = "X-Device-ID";
    private const string HeaderCursor = "X-Sync-Cursor";
    private const string HeaderFull = "X-Sync-Full";
    private const string HeaderFullRequested = "X-Sync-Full-Requested";
    private const string HeaderChanged = "X-Sync-Changed";
    private const string HeaderDeletedCategories = "X-Sync-Deleted-Categories";
    private const string HeaderSyncCaps = "X-Sync-Caps";
    private const string CapChapterDelta = "chapter-delta";
    private const string CapVersionWait = "version-wait";
    private const int MaxDeviceIdLength = 128;
    private const long DefaultMaxBodyBytes = 64L * 1024 * 1024;

    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;
    private readonly long _maxBodyBytes;
    private readonly Dictionary<string, (int Count, DateTimeOffset Start)> _authFailures = new();
    private readonly object _authGate = new();
    private readonly JsonSerializerOptions _eventJson = new() { PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower };

    private WebApplication? _app;
    private HubStatus _status = new(false, 0, Array.Empty<string>(), null);
    private TaskCompletionSource _seqSignal = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private const int MaxVersionWaitSeconds = 30;
    private static readonly TimeSpan VersionRecheck = TimeSpan.FromSeconds(1);
    private readonly object _debounceGate = new();
    private Timer? _progressDebounceTimer;

    private void SignalSeq()
    {
        lock (_debounceGate)
        {
            _progressDebounceTimer?.Dispose();
            _progressDebounceTimer = null;
        }
        Interlocked.Exchange(ref _seqSignal, new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously)).TrySetResult();
    }

    private bool ProgressPending
    {
        get
        {
            lock (_debounceGate) return _progressDebounceTimer != null;
        }
    }

    private void DebounceSignalSeq()
    {
        lock (_debounceGate)
        {
            if (_progressDebounceTimer != null)
            {
                _progressDebounceTimer.Change(TimeSpan.FromSeconds(3), Timeout.InfiniteTimeSpan);
            }
            else
            {
                _progressDebounceTimer = new Timer(_ =>
                {
                    lock (_debounceGate)
                    {
                        _progressDebounceTimer?.Dispose();
                        _progressDebounceTimer = null;
                    }
                    Interlocked.Exchange(ref _seqSignal, new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously)).TrySetResult();
                }, null, TimeSpan.FromSeconds(3), Timeout.InfiniteTimeSpan);
            }
        }
    }

    public SyncHub(LibraryDatabase db, SqliteLibraryStore store, int preferredPort = 7351, long maxBodyBytes = DefaultMaxBodyBytes)
    {
        _db = db;
        _store = store;
        _store.Changed += (_, e) =>
        {
            if (e.IsProgressOnly) DebounceSignalSeq();
            else SignalSeq();
        };
        PreferredPort = preferredPort;
        _maxBodyBytes = maxBodyBytes;
    }

    public int PreferredPort { get; }

    public HubStatus Status => _status;

    public event EventHandler<DeviceSyncedEventArgs>? DeviceSynced;

    public event EventHandler? StatusChanged;

    public async Task StartAsync(CancellationToken ct = default)
    {
        if (_app != null) return;
        Exception? failure = null;
        for (var offset = 0; offset <= 20; offset++)
        {
            var port = PreferredPort + offset;
            try
            {
                var app = BuildApp(port);
                await app.StartAsync(ct).ConfigureAwait(false);
                var boundPort = ActualPort(app, port);
                _app = app;
                _status = new HubStatus(true, boundPort, LanAddresses(boundPort), null);
                StatusChanged?.Invoke(this, EventArgs.Empty);
                _ = new SyncEngine(_db, _store).BeginWarmSnapshot();
                return;
            }
            catch (Exception ex) when (ex is AddressInUseException or IOException or SocketException)
            {
                failure = ex;
            }
        }
        _status = new HubStatus(false, 0, Array.Empty<string>(), failure?.Message ?? "no free port");
        StatusChanged?.Invoke(this, EventArgs.Empty);
        throw new InvalidOperationException("could not bind the sync hub", failure);
    }

    public async Task StopAsync()
    {
        if (_app == null) return;
        var app = _app;
        _app = null;
        await app.StopAsync().ConfigureAwait(false);
        await app.DisposeAsync().ConfigureAwait(false);
        _status = new HubStatus(false, 0, Array.Empty<string>(), null);
        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    public PairingInfo GetPairing()
    {
        var key = EnsureApiKey();
        var name = PcName();
        var urls = _status.Running && _status.Port != 0 ? _status.Addresses : LanAddresses(PreferredPort);
        var primary = urls.Count > 0 ? urls[0] : $"http://127.0.0.1:{PreferredPort}";
        var link = "tsuzuki://pair?host=" + Uri.EscapeDataString(primary)
            + "&key=" + Uri.EscapeDataString(key)
            + "&name=" + Uri.EscapeDataString(name);
        return new PairingInfo(name, primary, urls, key, link);
    }

    public async Task<PairingInfo> RegenerateKeyAsync(CancellationToken ct = default)
    {
        var key = NewApiKey();
        await _db.SetMetaAsync("api_key", key).ConfigureAwait(false);
        return GetPairing();
    }

    public Task<IReadOnlyList<SyncDevice>> GetDevicesAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => (IReadOnlyList<SyncDevice>)db.Query(
                "SELECT id, name, last_seen, cursor FROM device ORDER BY last_seen DESC")
            .Select(r => new SyncDevice(r.Text("id"), r.Text("name"), r.Long("last_seen"), r.Long("cursor")))
            .ToList());
    public Task RemoveDeviceAsync(string deviceId, CancellationToken ct = default) =>
        _db.WriteAsync(db => db.ExecuteNonQuery("DELETE FROM device WHERE id = $id", ("$id", deviceId)));

    internal string PcName()
    {
        using var db = _db.Open();
        var stored = _db.GetMeta(db, "pc_name");
        if (stored.Length > 0) return stored;
        var machine = Environment.MachineName;
        _db.SetMeta(db, "pc_name", machine);
        return machine;
    }

    internal string EnsureApiKey()
    {
        using var db = _db.Open();
        var existing = _db.GetMeta(db, "api_key");
        if (existing.Length > 0) return existing;
        var key = NewApiKey();
        _db.SetMeta(db, "api_key", key);
        return key;
    }

    private static string NewApiKey()
    {
        const string alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        var bytes = RandomNumberGenerator.GetBytes(12);
        var groups = new string[3];
        for (var group = 0; group < 3; group++)
        {
            groups[group] = new string(Enumerable.Range(0, 4).Select(i => alphabet[bytes[group * 4 + i] % alphabet.Length]).ToArray());
        }
        return string.Join('-', groups);
    }

    private static int ActualPort(WebApplication app, int requested)
    {
        var features = ((IApplicationBuilder)app).ServerFeatures;
        var addresses = features.Get<Microsoft.AspNetCore.Hosting.Server.Features.IServerAddressesFeature>()?.Addresses;
        foreach (var address in addresses ?? Array.Empty<string>())
        {
            if (Uri.TryCreate(address, UriKind.Absolute, out var uri)) return uri.Port;
        }
        return requested;
    }

    private WebApplication BuildApp(int port)
    {
        var builder = WebApplication.CreateBuilder(new WebApplicationOptions { Args = Array.Empty<string>() });
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options => options.Listen(IPAddress.Any, port));

        var app = builder.Build();
        app.Use(async (ctx, next) => await InvokeAuth(ctx, next).ConfigureAwait(false));
        MapEndpoints(app);
        return app;
    }

    private void MapEndpoints(WebApplication app)
    {
        app.MapGet("/api/sync/v2/capabilities", async ctx =>
        {
            var seq = await _db.ReadAsync(db => _db.GetSeq(db)).ConfigureAwait(false);
            ctx.Response.Headers[HeaderSyncCaps] = CapChapterDelta + "," + CapVersionWait;
            await ctx.Response.WriteAsJsonAsync(new
            {
                version = 2,
                merge = "/api/sync/v2/merge",
                snapshot = "/api/sync/v2/snapshot",
                seq,
                caps = new[] { CapChapterDelta, CapVersionWait },
            }).ConfigureAwait(false);
        });

        app.MapGet("/api/sync/v2/version", async ctx =>
        {
            var since = long.TryParse(ctx.Request.Query["since"], out var s) ? s : (long?)null;
            var waitSeconds = int.TryParse(ctx.Request.Query["wait"], out var w) ? Math.Clamp(w, 0, MaxVersionWaitSeconds) : 0;
            var deadline = DateTime.UtcNow.AddSeconds(waitSeconds);
            long seq;
            while (true)
            {
                var signal = _seqSignal.Task;
                seq = await _db.ReadAsync(db => _db.GetSeq(db)).ConfigureAwait(false);
                var remaining = deadline - DateTime.UtcNow;
                if (since is null || (seq > since && !ProgressPending) || remaining <= TimeSpan.Zero || ctx.RequestAborted.IsCancellationRequested) break;
                await Task.WhenAny(signal, Task.Delay(remaining < VersionRecheck ? remaining : VersionRecheck, ctx.RequestAborted)).ConfigureAwait(false);
            }
            ctx.Response.ContentType = "application/json";
            await ctx.Response.WriteAsync("{\"seq\":" + seq.ToString() + "}").ConfigureAwait(false);
        });

        app.MapPost("/api/sync/v2/merge", async ctx =>
        {
            var deviceId = Header(ctx, HeaderDeviceId);
            if (deviceId.Length == 0 || deviceId.Length > MaxDeviceIdLength)
            {
                await BadRequest(ctx, "missing or invalid " + HeaderDeviceId).ConfigureAwait(false);
                return;
            }
            if (!TryParseCursor(Header(ctx, HeaderCursor), out var cursor))
            {
                await BadRequest(ctx, "invalid " + HeaderCursor).ConfigureAwait(false);
                return;
            }
            if (!TryParseDeletedCategories(Header(ctx, HeaderDeletedCategories), out var deleted))
            {
                await BadRequest(ctx, "invalid " + HeaderDeletedCategories).ConfigureAwait(false);
                return;
            }
            var (body, bodyError) = await ReadBody(ctx).ConfigureAwait(false);
            if (bodyError != null)
            {
                await WriteError(ctx, bodyError.Value).ConfigureAwait(false);
                return;
            }

            BackupMessage incoming;
            try
            {
                incoming = DecodeBackup(body.Buffer, body.Length);
            }
            catch (InvalidProtocolBufferException)
            {
                await BadRequest(ctx, "body is not a valid backup").ConfigureAwait(false);
                return;
            }

            var deviceName = Header(ctx, "X-Device-Name");
            var chapterDelta = Header(ctx, HeaderSyncCaps)
                .Split(',')
                .Any(cap => cap.Trim().Equals(CapChapterDelta, StringComparison.OrdinalIgnoreCase));
            var engine = new SyncEngine(_db, _store);
            var result = await engine.Merge(deviceId, deviceName, incoming, cursor,
                string.Equals(Header(ctx, HeaderFull), "true", StringComparison.OrdinalIgnoreCase), deleted, body.Buffer.AsMemory(0, body.Length), chapterDelta).ConfigureAwait(false);

            var data = result.Wire ?? result.Backup.ToByteArray();
            ctx.Response.Headers[HeaderCursor] = result.Cursor.ToString();
            ctx.Response.Headers[HeaderChanged] = result.Changed ? "true" : "false";
            if (result.FullRequested) ctx.Response.Headers[HeaderFullRequested] = "true";
            ctx.Response.Headers.ETag = "seq=" + result.Cursor;
            OnMerged(deviceId, deviceName, result);
            SignalSeq();
            await WriteBackup(ctx, data, result.WireGzip).ConfigureAwait(false);
        });

        app.MapGet("/api/sync/v2/snapshot", async ctx =>
        {
            if (!TryParseCursor(Header(ctx, HeaderCursor), out var cursor))
            {
                await BadRequest(ctx, "invalid " + HeaderCursor).ConfigureAwait(false);
                return;
            }
            var engine = new SyncEngine(_db, _store);
            var (snapshot, wire, wireGzip, seq) = engine.Snapshot();
            if (snapshot == null && wire == null)
            {
                ctx.Response.StatusCode = 404;
                return;
            }
            ctx.Response.Headers[HeaderCursor] = seq.ToString();
            ctx.Response.Headers.ETag = "seq=" + seq;
            if (cursor != 0 && cursor == seq)
            {
                ctx.Response.StatusCode = 304;
                return;
            }
            var data = wire ?? snapshot!.ToByteArray();
            await WriteBackup(ctx, data, wireGzip).ConfigureAwait(false);
        });

        app.MapPost("/api/sync/event", async ctx =>
        {
            EventBody? body;
            try
            {
                body = await JsonSerializer.DeserializeAsync<EventBody>(ctx.Request.Body, _eventJson).ConfigureAwait(false);
            }
            catch (JsonException)
            {
                await BadRequest(ctx, "invalid JSON body").ConfigureAwait(false);
                return;
            }
            if (body == null || body.Event is null || body.Event.Length == 0)
            {
                await BadRequest(ctx, "event is required").ConfigureAwait(false);
                return;
            }
            if (!IsValidEvent(body.Event))
            {
                await BadRequest(ctx, "invalid sync event").ConfigureAwait(false);
                return;
            }
            ctx.Response.StatusCode = 204;
        });
    }

    private async Task InvokeAuth(HttpContext ctx, Func<Task> next)
    {
        if (!ctx.Request.Path.StartsWithSegments("/api"))
        {
            await next().ConfigureAwait(false);
            return;
        }
        var remote = ctx.Connection.RemoteIpAddress?.ToString() ?? "";
        var key = Header(ctx, "X-API-Token");
        var expected = EnsureApiKey();
        if (!FixedTimeEquals(key, expected))
        {
            if (Throttled(remote))
            {
                ctx.Response.StatusCode = 429;
                ctx.Response.ContentType = "text/plain";
                await ctx.Response.WriteAsync("too many failed attempts").ConfigureAwait(false);
                return;
            }
            RecordFailure(remote);
            ctx.Response.StatusCode = 401;
            ctx.Response.ContentType = "text/plain";
            await ctx.Response.WriteAsync("Unauthorized").ConfigureAwait(false);
            return;
        }
        lock (_authGate) _authFailures.Remove(remote);
        await next().ConfigureAwait(false);
    }

    private bool Throttled(string remote)
    {
        lock (_authGate)
        {
            if (!_authFailures.TryGetValue(remote, out var entry)) return false;
            if (DateTimeOffset.UtcNow - entry.Start > TimeSpan.FromSeconds(60))
            {
                _authFailures.Remove(remote);
                return false;
            }
            return entry.Count >= 10;
        }
    }

    private void RecordFailure(string remote)
    {
        lock (_authGate)
        {
            var now = DateTimeOffset.UtcNow;
            if (_authFailures.TryGetValue(remote, out var entry) && now - entry.Start <= TimeSpan.FromSeconds(60))
            {
                _authFailures[remote] = (entry.Count + 1, entry.Start);
            }
            else
            {
                _authFailures[remote] = (1, now);
            }
        }
    }

    private static bool FixedTimeEquals(string provided, string expected)
    {
        var a = Encoding.UTF8.GetBytes(provided);
        var b = Encoding.UTF8.GetBytes(expected);
        return a.Length == b.Length && CryptographicOperations.FixedTimeEquals(a, b);
    }

    private void OnMerged(string deviceId, string deviceName, EngineResult result)
    {
        if (result.MangaIds.Count > 0 || result.Handoffs.Count > 0)
        {
            _store.RaiseChanged(result.MangaIds, ChangeOrigin.Remote, deviceName.Length > 0 ? deviceName : null, result.Handoffs);
        }
        DeviceSynced?.Invoke(this, new DeviceSyncedEventArgs(
            new SyncDevice(SyncEngine.DeviceKey(deviceId, deviceName), deviceName, SqliteLibraryStore.NowMs(), result.Cursor),
            result.ItemsReceived, result.Sent));
    }

    private static string Header(HttpContext ctx, string name) => ctx.Request.Headers[name].FirstOrDefault() ?? "";

    private async Task<((byte[] Buffer, int Length) Data, ErrorBody? Error)> ReadBody(HttpContext ctx)
    {
        var (raw, overflow) = await ReadUpTo(ctx.Request.Body, _maxBodyBytes + 1).ConfigureAwait(false);
        if (overflow)
        {
            return ((Array.Empty<byte>(), 0), new ErrorBody(413, """{"message":"request body too large"}"""));
        }
        var gzipped = ctx.Request.Headers.ContentEncoding.ToString().Contains("gzip", StringComparison.OrdinalIgnoreCase)
            || (raw.Length >= 2 && raw.Buffer[0] == 0x1f && raw.Buffer[1] == 0x8b);
        if (!gzipped) return (raw, null);
        try
        {
            using var gzip = new System.IO.Compression.GZipStream(new MemoryStream(raw.Buffer, 0, raw.Length, false), System.IO.Compression.CompressionMode.Decompress);
            var (data, inflatedOverflow) = await ReadUpTo(gzip, _maxBodyBytes + 1).ConfigureAwait(false);
            if (inflatedOverflow)
            {
                return ((Array.Empty<byte>(), 0), new ErrorBody(413, """{"message":"request body too large"}"""));
            }
            return (data, null);
        }
        catch (InvalidDataException)
        {
            return ((Array.Empty<byte>(), 0), new ErrorBody(400, """{"message":"body is not a valid backup"}"""));
        }
    }

    private static async Task<((byte[] Buffer, int Length), bool Overflow)> ReadUpTo(Stream stream, long limit)
    {
        var output = new MemoryStream();
        var buffer = new byte[64 * 1024];
        while (true)
        {
            var read = await stream.ReadAsync(buffer).ConfigureAwait(false);
            if (read == 0) break;
            if (output.Length + read > limit) return ((Array.Empty<byte>(), 0), true);
            output.Write(buffer, 0, read);
        }
        return ((output.GetBuffer(), (int)output.Length), false);
    }

    private static BackupMessage DecodeBackup(byte[] buffer, int length)
    {
        if (length >= 2 && buffer[0] == 0x1f && buffer[1] == 0x8b)
        {
            using var gzip = new System.IO.Compression.GZipStream(new MemoryStream(buffer, 0, length, false), System.IO.Compression.CompressionMode.Decompress);
            using var output = new MemoryStream();
            gzip.CopyTo(output);
            buffer = output.ToArray();
            length = buffer.Length;
        }
        return length == 0 ? new BackupMessage() : BackupMessage.Parser.ParseFrom(new ReadOnlySpan<byte>(buffer, 0, length));
    }

    private static async Task WriteBackup(HttpContext ctx, byte[] data, byte[]? gzData = null)
    {
        if (data.Length > 0 && ctx.Request.Headers.AcceptEncoding.ToString().Contains("gzip", StringComparison.OrdinalIgnoreCase))
        {
            if (gzData != null)
            {
                data = gzData;
            }
            else
            {
                using var compressed = new MemoryStream();
                using (var gzip = new System.IO.Compression.GZipStream(compressed, System.IO.Compression.CompressionLevel.Fastest))
                {
                    gzip.Write(data, 0, data.Length);
                }
                data = compressed.ToArray();
            }
            ctx.Response.Headers.ContentEncoding = "gzip";
        }
        ctx.Response.ContentType = "application/octet-stream";
        ctx.Response.ContentLength = data.Length;
        await ctx.Response.Body.WriteAsync(data).ConfigureAwait(false);
    }

    private static async Task BadRequest(HttpContext ctx, string message)
    {
        ctx.Response.StatusCode = 400;
        ctx.Response.ContentType = "application/json";
        await ctx.Response.WriteAsync(JsonSerializer.Serialize(new { message })).ConfigureAwait(false);
    }

    private static async Task WriteError(HttpContext ctx, ErrorBody error)
    {
        ctx.Response.StatusCode = error.StatusCode;
        ctx.Response.ContentType = "application/json";
        await ctx.Response.WriteAsync(error.Json).ConfigureAwait(false);
    }

    private readonly record struct ErrorBody(int StatusCode, string Json);

    private static bool TryParseCursor(string? value, out long cursor)
    {
        cursor = 0;
        if (string.IsNullOrEmpty(value)) return true;
        if (!long.TryParse(value, out cursor) || cursor < 0) return false;
        return true;
    }

    private static bool TryParseDeletedCategories(string? value, out List<long> uids)
    {
        uids = new List<long>();
        if (string.IsNullOrWhiteSpace(value)) return true;
        foreach (var part in value.Split(','))
        {
            var trimmed = part.Trim();
            if (trimmed.Length == 0) continue;
            if (!long.TryParse(trimmed, out var uid)) return false;
            uids.Add(uid);
        }
        return true;
    }

    private static bool IsValidEvent(string name) =>
        name is "SYNC_STARTED" or "SYNC_SUCCESS" or "SYNC_FAILED" or "SYNC_ERROR" or "SYNC_CANCELLED";

    private static IReadOnlyList<string> LanAddresses(int port)
    {
        var candidates = new List<(string Ip, int Score)>();
        try
        {
            foreach (var adapter in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (adapter.OperationalStatus != OperationalStatus.Up) continue;
                var label = adapter.Name + " " + adapter.Description;
                if (VirtualAdapter.IsMatch(label)) continue;
                var properties = adapter.GetIPProperties();
                var score = 0;
                if (properties.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork && !g.Address.Equals(System.Net.IPAddress.Any))) score += 100;
                if (adapter.NetworkInterfaceType is NetworkInterfaceType.Wireless80211 or NetworkInterfaceType.Ethernet) score += 50;
                if (adapter.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) score += 5;
                if (label.Contains("Tailscale", StringComparison.OrdinalIgnoreCase)) score = 10;
                foreach (var address in properties.UnicastAddresses)
                {
                    if (address.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    var ip = address.Address.ToString();
                    if (ip == "127.0.0.1" || ip.StartsWith("169.254.")) continue;
                    candidates.Add((ip, score));
                }
            }
        }
        catch (NetworkInformationException)
        {
        }
        return candidates
            .OrderByDescending(c => c.Score)
            .Select(c => c.Ip)
            .Distinct()
            .Select(ip => $"http://{ip}:{port}")
            .ToList();
    }

    private static readonly System.Text.RegularExpressions.Regex VirtualAdapter = new(
        "Hyper-V|vEthernet|VirtualBox|VMware|WSL|Loopback|Bluetooth|Cloudflare|WARP|Npcap|TAP-",
        System.Text.RegularExpressions.RegexOptions.IgnoreCase);

    private sealed record EventBody(string Event, string DeviceId, string DeviceName, string Message);

    public void Dispose()
    {
        if (_app != null) _ = StopAsync();
    }
}
