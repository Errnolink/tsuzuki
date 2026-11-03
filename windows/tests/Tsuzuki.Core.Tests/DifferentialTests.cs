using System.Net;
using System.Net.Http.Headers;
using System.Text;
using Google.Protobuf;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

/// <summary>
/// Replays an identical v2 request sequence against the real SyncYomi 1.5.8 reference
/// binary and the Tsuzuki hub, comparing status, protocol headers and canonical bodies.
/// Runs only when TSUZUKI_DIFF_REF points at a prepared reference server; the reference
/// database path comes in TSUZUKI_DIFF_REF_DB so the test can seed the shared API key.
/// </summary>
public class DifferentialTests
{
    private const string ApiKey = "DIFF-TEST-KEY1";
    private const long MdSource = 2499283573021220255;

    private static string? ReferenceUrl => Environment.GetEnvironmentVariable("TSUZUKI_DIFF_REF");
    private static string? ReferenceDb => Environment.GetEnvironmentVariable("TSUZUKI_DIFF_REF_DB");

    [Fact]
    public async Task ProtocolSequenceMatchesReference()
    {
        if (ReferenceUrl == null) Assert.Skip("reference SyncYomi not started (TSUZUKI_DIFF_REF unset)");
        Assert.NotNull(ReferenceDb);
        SeedReferenceKey(ReferenceDb!);

        var (db, store) = TestLib.NewLibrary();
        await db.SetMetaAsync("api_key", ApiKey);
        using var hub = new SyncHub(db, store, preferredPort: 0);
        await hub.StartAsync();
        using var tsuzuki = new HttpClient { BaseAddress = new Uri($"http://127.0.0.1:{hub.Status.Port}") };
        using var reference = new HttpClient { BaseAddress = new Uri(ReferenceUrl) };

        var manga = TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
            TestLib.Chapter("/chapter/2", number: 2, version: 5, modified: 100));
        var fullPayload = TestLib.Backup(manga)
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900));

        await Expect("snapshot before any merge is 404", () => Send(reference, HttpMethod.Get, "/api/sync/v2/snapshot", ApiKey, null, null, null, null),
            () => Send(tsuzuki, HttpMethod.Get, "/api/sync/v2/snapshot", ApiKey, null, null, null, null), compareBody: false);

        await Expect("cold full upload", () => SendMerge(reference, "A", 0, fullPayload, full: true),
            () => SendMerge(tsuzuki, "A", 0, fullPayload, full: true));

        await Expect("second device cold join", () => SendMerge(reference, "B", 0, new BackupMessage()),
            () => SendMerge(tsuzuki, "B", 0, new BackupMessage()));

        await Expect("no-op echo", () => SendMerge(reference, "A", 1, fullPayload),
            () => SendMerge(tsuzuki, "A", 1, fullPayload));

        var changedManga = TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 100,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
            TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000));
        await Expect("self chapter write", () => SendMerge(reference, "A", 1, TestLib.Backup(changedManga)),
            () => SendMerge(tsuzuki, "A", 1, TestLib.Backup(changedManga)));

        await Expect("other device receives whole manga delta", () => SendMerge(reference, "B", 1, new BackupMessage()),
            () => SendMerge(tsuzuki, "B", 1, new BackupMessage()));

        await Expect("stale version returns server copy", () => SendMerge(reference, "B", 2,
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Stale", version: 4, modified: 9999))),
            () => SendMerge(tsuzuki, "B", 2,
                TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Stale", version: 4, modified: 9999))));

        await Expect("category tombstone", () => SendMerge(reference, "A", 2, new BackupMessage(), deletedCategories: "3"),
            () => SendMerge(tsuzuki, "A", 2, new BackupMessage(), deletedCategories: "3"));

        await Expect("other device sees tombstone", () => SendMerge(reference, "B", 2, new BackupMessage()),
            () => SendMerge(tsuzuki, "B", 2, new BackupMessage()));

        await Expect("repeated tombstone is idempotent", () => SendMerge(reference, "A", 3, new BackupMessage(), deletedCategories: "3"),
            () => SendMerge(tsuzuki, "A", 3, new BackupMessage(), deletedCategories: "3"));

        await Expect("cursor ahead forces full", () => SendMerge(reference, "A", 99, new BackupMessage()),
            () => SendMerge(tsuzuki, "A", 99, new BackupMessage()));

        await Expect("snapshot returns full store", () => Send(reference, HttpMethod.Get, "/api/sync/v2/snapshot", ApiKey, null, null, null, null),
            () => Send(tsuzuki, HttpMethod.Get, "/api/sync/v2/snapshot", ApiKey, null, null, null, null));

        await Expect("capabilities", () => Send(reference, HttpMethod.Get, "/api/sync/v2/capabilities", ApiKey, null, null, null, null),
            () => Send(tsuzuki, HttpMethod.Get, "/api/sync/v2/capabilities", ApiKey, null, null, null, null),
            compareBody: false);
        await ExpectCapabilitiesFields("capabilities fields", reference, tsuzuki);

        await Expect("event accepted", () => SendEvent(reference, ApiKey, "SYNC_STARTED", "Pixel"),
            () => SendEvent(tsuzuki, ApiKey, "SYNC_STARTED", "Pixel"));

        await Expect("unknown event rejected", () => SendEvent(reference, ApiKey, "SYNC_WHATEVER", "Pixel"),
            () => SendEvent(tsuzuki, ApiKey, "SYNC_WHATEVER", "Pixel"));

        await Expect("garbage event rejected", () => SendEventRaw(reference, ApiKey, "{oops"),
            () => SendEventRaw(tsuzuki, ApiKey, "{oops"));

        await Expect("event without key rejected", () => SendEvent(reference, null, "SYNC_SUCCESS", "Pixel"),
            () => SendEvent(tsuzuki, null, "SYNC_SUCCESS", "Pixel"));

        await Expect("truncated protobuf rejected", () => SendMerge(reference, "A", 3, new byte[] { 0x0a, 0xff, 0x01 }),
            () => SendMerge(tsuzuki, "A", 3, new byte[] { 0x0a, 0xff, 0x01 }));

        await Expect("invalid cursor rejected", () => SendMergeWithExtra(reference, "A", new BackupMessage(),
            ("X-Sync-Cursor", "-1")),
            () => SendMergeWithExtra(tsuzuki, "A", new BackupMessage(), ("X-Sync-Cursor", "-1")));

        await Expect("invalid deleted categories rejected", () => SendMergeWithExtra(reference, "A", new BackupMessage(),
            ("X-Sync-Deleted-Categories", "x")),
            () => SendMergeWithExtra(tsuzuki, "A", new BackupMessage(), ("X-Sync-Deleted-Categories", "x")));
        await Expect("missing device rejected", () => SendMerge(reference, "", 3, new BackupMessage()),
            () => SendMerge(tsuzuki, "", 3, new BackupMessage()));

        await Expect("missing token rejected", () => SendMerge(reference, "A", 3, new BackupMessage(), token: "WRONG"),
            () => SendMerge(tsuzuki, "A", 3, new BackupMessage(), token: "WRONG"));

        await Expect("gzip payload without content encoding accepted", () => SendGzip(reference, "A", 3,
            TestLib.Backup(TestLib.Manga(2, "/manga/gz", "Gz", version: 1, modified: 5)), includeHeader: false),
            () => SendGzip(tsuzuki, "A", 3,
                TestLib.Backup(TestLib.Manga(2, "/manga/gz", "Gz", version: 1, modified: 5)), includeHeader: false));

        await Expect("transport gzip accepted", () => SendGzip(reference, "A", 4, new BackupMessage(), includeHeader: true),
            () => SendGzip(tsuzuki, "A", 4, new BackupMessage(), includeHeader: true));
    }

    private static void SeedReferenceKey(string dbPath)
    {
        using var db = new Microsoft.Data.Sqlite.SqliteConnection(
            new Microsoft.Data.Sqlite.SqliteConnectionStringBuilder { DataSource = dbPath }.ToString());
        db.Open();
        using var cmd = db.CreateCommand();
        cmd.CommandText = "INSERT OR REPLACE INTO api_key(name, key, scopes, created_at) VALUES ('diff', $k, '{}', CURRENT_TIMESTAMP)";
        cmd.Parameters.AddWithValue("$k", ApiKey);
        cmd.ExecuteNonQuery();
    }

    private static async Task<HttpResponseMessage> SendMerge(HttpClient client, string device, long cursor,
        BackupMessage body, bool full = false, string? deletedCategories = null, string? token = null) =>
        await SendMergeRaw(client, device, body.ToByteArray(), cursor, full, deletedCategories, token);

    private static async Task<HttpResponseMessage> SendMerge(HttpClient client, string device, long cursor,
        byte[] rawBody, bool full = false, string? deletedCategories = null, string? token = null) =>
        await SendMergeRaw(client, device, rawBody, cursor, full, deletedCategories, token);

    private static async Task<HttpResponseMessage> SendMergeRaw(HttpClient client, string device, byte[] rawBody,
        long cursor, bool full, string? deletedCategories, string? token)
    {
        var headers = new List<(string, string)> { ("X-Sync-Cursor", cursor.ToString()) };
        if (full) headers.Add(("X-Sync-Full", "true"));
        if (deletedCategories != null) headers.Add(("X-Sync-Deleted-Categories", deletedCategories));
        return await Send(client, HttpMethod.Post, "/api/sync/v2/merge", token ?? ApiKey, device, headers,
            "application/octet-stream", rawBody);
    }

    private static async Task<HttpResponseMessage> SendMergeWithExtra(HttpClient client, string device,
        BackupMessage body, params (string Name, string Value)[] extra)
    {
        var headers = extra.Select(e => (e.Name, e.Value)).ToList();
        return await Send(client, HttpMethod.Post, "/api/sync/v2/merge", ApiKey, device, headers,
            "application/octet-stream", body.ToByteArray());
    }

    private static async Task<HttpResponseMessage> SendGzip(HttpClient client, string device, long cursor,
        BackupMessage body, bool includeHeader)
    {
        var gzipped = TestLib.Gzip(body.ToByteArray());
        return await Send(client, HttpMethod.Post, "/api/sync/v2/merge", ApiKey, device,
            includeHeader ? new List<(string, string)> { ("Content-Encoding", "gzip"), ("X-Sync-Cursor", cursor.ToString()) }
                          : new List<(string, string)> { ("X-Sync-Cursor", cursor.ToString()) },
            "application/octet-stream", gzipped);
    }

    private static async Task<HttpResponseMessage> SendEvent(HttpClient client, string? key, string eventName, string deviceName)
    {
        var json = "{\"event\":\"" + eventName + "\",\"device_name\":\"" + deviceName + "\"}";
        return await Send(client, HttpMethod.Post, "/api/sync/event", key, null, new List<(string, string)>(),
            "application/json", Encoding.UTF8.GetBytes(json));
    }

    private static async Task<HttpResponseMessage> SendEventRaw(HttpClient client, string? key, string raw)
    {
        return await Send(client, HttpMethod.Post, "/api/sync/event", key, null, new List<(string, string)>(),
            "application/json", Encoding.UTF8.GetBytes(raw));
    }

    private static async Task<HttpResponseMessage> Send(HttpClient client, HttpMethod method, string path,
        string? token, string? device, List<(string, string)>? headers, string? contentType, byte[]? body)
    {
        var request = new HttpRequestMessage(method, path);
        if (token != null) request.Headers.TryAddWithoutValidation("X-API-Token", token);
        if (device != null) request.Headers.TryAddWithoutValidation("X-Device-ID", device);
        foreach (var (name, value) in headers ?? new List<(string, string)>())
        {
            request.Headers.TryAddWithoutValidation(name, value);
        }
        if (body != null)
        {
            request.Content = new ByteArrayContent(body);
            if (contentType != null) request.Content.Headers.ContentType = new MediaTypeHeaderValue(contentType);
        }
        return await client.SendAsync(request);
    }

    private static async Task Expect(string label, Func<Task<HttpResponseMessage>> toReference,
        Func<Task<HttpResponseMessage>> toTsuzuki, bool compareBody = true)
    {
        using var referenceResponse = await toReference();
        using var tsuzukiResponse = await toTsuzuki();
        Assert.True(referenceResponse.StatusCode == tsuzukiResponse.StatusCode,
            $"{label}: status reference={(int)referenceResponse.StatusCode} tsuzuki={(int)tsuzukiResponse.StatusCode} body={await tsuzukiResponse.Content.ReadAsStringAsync()}");
        Assert.True(label.Contains("rejected") || label.Contains("404") || (int)tsuzukiResponse.StatusCode < 400,
            $"{label}: expected success but both servers answered {(int)tsuzukiResponse.StatusCode}");

        foreach (var header in new[] { "X-Sync-Cursor", "X-Sync-Changed", "X-Sync-Full-Requested", "ETag" })
        {
            var fromReference = referenceResponse.Headers.TryGetValues(header, out var r) ? r.FirstOrDefault() : null;
            var fromTsuzuki = tsuzukiResponse.Headers.TryGetValues(header, out var t) ? t.FirstOrDefault() : null;
            Assert.True(fromReference == fromTsuzuki,
                $"{label}: header {header} reference={fromReference ?? "<absent>"} tsuzuki={fromTsuzuki ?? "<absent>"}");
        }

        var referenceBytes = await referenceResponse.Content.ReadAsByteArrayAsync();
        var tsuzukiBytes = await tsuzukiResponse.Content.ReadAsByteArrayAsync();
        if (!compareBody) return;

        if (referenceResponse.StatusCode != HttpStatusCode.OK) return;
        BackupMessage referenceBackup;
        BackupMessage tsuzukiBackup;
        try
        {
            referenceBackup = BackupMessage.Parser.ParseFrom(referenceBytes);
            tsuzukiBackup = BackupMessage.Parser.ParseFrom(tsuzukiBytes);
        }
        catch (InvalidProtocolBufferException)
        {
            if (referenceResponse.Content.Headers.ContentType?.MediaType?.Contains("json") == true)
            {
                var a = System.Text.Json.Nodes.JsonNode.Parse(referenceBytes);
                var b = System.Text.Json.Nodes.JsonNode.Parse(tsuzukiBytes);
                Assert.True(System.Text.Json.Nodes.JsonNode.DeepEquals(a, b), $"{label}: JSON differs\nreference: {a}\ntsuzuki: {b}");
                return;
            }
            Assert.True(referenceBytes.AsSpan().SequenceEqual(tsuzukiBytes), label + ": body bytes differ");
            return;
        }
        Assert.True(Canonical(referenceBackup).AsSpan().SequenceEqual(Canonical(tsuzukiBackup)),
            $"{label}: body differs\nreference:\n{referenceBackup}\ntsuzuki:\n{tsuzukiBackup}");
    }

    private static async Task ExpectCapabilitiesFields(string label, HttpClient reference, HttpClient tsuzuki)
    {
        using var a = await Send(reference, HttpMethod.Get, "/api/sync/v2/capabilities", ApiKey, null, null, null, null);
        using var b = await Send(tsuzuki, HttpMethod.Get, "/api/sync/v2/capabilities", ApiKey, null, null, null, null);
        Assert.Equal(a.StatusCode, b.StatusCode);
        var ja = System.Text.Json.Nodes.JsonNode.Parse(await a.Content.ReadAsStringAsync());
        var jb = System.Text.Json.Nodes.JsonNode.Parse(await b.Content.ReadAsStringAsync());
        foreach (var field in new[] { "version", "merge", "snapshot" })
        {
            Assert.True(System.Text.Json.Nodes.JsonNode.DeepEquals(ja[field], jb[field]),
                $"{label}: {field} differs: reference={ja[field]} tsuzuki={jb[field]}");
        }
    }

    private static byte[] Canonical(BackupMessage backup)
    {
        var normalized = new BackupMessage();
        normalized.BackupCategories.AddRange(backup.BackupCategories.OrderBy(c => c.Order).ThenBy(c => c.Uid));
        normalized.BackupManga.AddRange(backup.BackupManga.OrderBy(m => m.Source).ThenBy(m => m.Url, StringComparer.Ordinal));
        foreach (var manga in normalized.BackupManga)
        {
            var orders = manga.Categories.OrderBy(c => c).ToList();
            manga.Categories.Clear();
            manga.Categories.AddRange(orders);
            var chapters = manga.Chapters.OrderBy(c => c.Url, StringComparer.Ordinal).ToList();
            manga.Chapters.Clear();
            manga.Chapters.AddRange(chapters);
        }
        normalized.BackupSources.AddRange(backup.BackupSources.OrderBy(s => s.SourceId));
        normalized.BackupPreferences.AddRange(backup.BackupPreferences.OrderBy(p => p.Key, StringComparer.Ordinal));
        normalized.BackupSourcePreferences.AddRange(backup.BackupSourcePreferences.OrderBy(g => g.SourceKey, StringComparer.Ordinal));
        foreach (var group in normalized.BackupSourcePreferences)
        {
            var prefs = group.Prefs.OrderBy(p => p.Key, StringComparer.Ordinal).ToList();
            group.Prefs.Clear();
            group.Prefs.AddRange(prefs);
        }
        normalized.BackupExtensionStores.AddRange(backup.BackupExtensionStores.OrderBy(e => e.IndexUrl, StringComparer.Ordinal));
        normalized.BackupSavedSearches.AddRange(backup.BackupSavedSearches.OrderBy(s => s.Name, StringComparer.Ordinal));
        return normalized.ToByteArray();
    }
}
