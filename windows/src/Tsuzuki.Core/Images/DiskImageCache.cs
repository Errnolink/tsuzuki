using System.Collections.Concurrent;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Images;

public sealed record ImageFetch(Uri Url, bool Success, long Bytes, TimeSpan Duration, bool Cached);

public sealed class DiskImageCache : IImageCache
{
    private static readonly TimeSpan TouchInterval = TimeSpan.FromDays(1);
    private static readonly TimeSpan RecentGrace = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan PartLifetime = TimeSpan.FromHours(1);

    private readonly string root;
    private readonly HttpClient http;
    private readonly Func<long, ISource?> sources;
    private readonly long maxBytes;
    private readonly SemaphoreSlim visibleSlots;
    private readonly SemaphoreSlim backgroundSlots;
    private readonly ConcurrentDictionary<string, Lazy<Download>> inflight = new();
    private int downloadsSinceTrim;

    private readonly HostGate gate;
    private static readonly TimeSpan RefusalLifetime = TimeSpan.FromMinutes(30);
    private readonly System.Collections.Concurrent.ConcurrentDictionary<string, DateTimeOffset> refused = new();

    public DiskImageCache(string root, HttpClient http, Func<long, ISource?> sources, long maxBytes, int visibleConcurrency = 6, int backgroundConcurrency = 3, HostGate? gate = null)
    {
        this.root = root;
        this.http = http;
        this.sources = sources;
        this.maxBytes = maxBytes;
        this.gate = gate ?? HostGate.Polite;
        visibleSlots = new SemaphoreSlim(visibleConcurrency);
        backgroundSlots = new SemaphoreSlim(backgroundConcurrency);
        Directory.CreateDirectory(root);
    }

    public TimeSpan DownloadTimeout { get; init; } = TimeSpan.FromSeconds(45);

    public TimeSpan IdleReadTimeout { get; init; } = TimeSpan.FromSeconds(15);

    public event EventHandler<ImageFetch>? Fetched;

    public string PathFor(ImageRequest image)
    {
        var hash = Convert.ToHexStringLower(SHA1.HashData(Encoding.UTF8.GetBytes(image.Key)));
        var ext = Path.GetExtension(image.Url.AbsolutePath);
        if (ext.Length is 0 or > 5)
        {
            ext = ".img";
        }
        return Path.Combine(root, hash[..2], hash + ext);
    }

    public Task<string> GetFileAsync(ImageRequest image, ImagePriority priority, CancellationToken ct = default)
    {
        if (image.Url.IsFile)
        {
            return File.Exists(image.Url.LocalPath)
                ? Task.FromResult(image.Url.LocalPath)
                : Task.FromException<string>(new FileNotFoundException("The downloaded page is missing.", image.Url.LocalPath));
        }
        var path = PathFor(image);
        while (true)
        {
            if (TryHit(path))
            {
                return Task.FromResult(path);
            }
            if (refused.TryGetValue(path, out var until))
            {
                if (until > DateTimeOffset.UtcNow)
                {
                    return Task.FromException<string>(new HttpRequestException($"{image.Url.Host} refused this image recently."));
                }
                refused.TryRemove(path, out _);
            }
            var lazy = inflight.GetOrAdd(path, _ => new Lazy<Download>(() => new Download(this, image, path, priority)));
            var download = lazy.Value;
            if (!download.Join())
            {
                inflight.TryRemove(new KeyValuePair<string, Lazy<Download>>(path, lazy));
                continue;
            }
            if (priority == ImagePriority.Visible) download.Promote();
            return download.WaitAsync(ct);
        }
    }

    public void Prefetch(IEnumerable<ImageRequest> images)
    {
        foreach (var image in images)
        {
            _ = GetFileAsync(image, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
    }

    public void Invalidate(ImageRequest request)
    {
        if (request.Url.IsFile)
        {
            return;
        }
        try
        {
            File.Delete(PathFor(request));
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    private static bool TryHit(string path)
    {
        var file = new FileInfo(path);
        if (!file.Exists)
        {
            return false;
        }
        var now = DateTime.UtcNow;
        if (now - file.LastWriteTimeUtc >= TouchInterval)
        {
            try
            {
                file.LastWriteTimeUtc = now;
            }
            catch (IOException)
            {
            }
            catch (UnauthorizedAccessException)
            {
            }
        }
        return true;
    }

    private sealed class Download
    {
        private readonly DiskImageCache cache;
        private readonly ImageRequest image;
        private readonly string path;
        private readonly CancellationTokenSource abandon = new();
        private readonly TaskCompletionSource promoted = new(TaskCreationOptions.RunContinuationsAsynchronously);
        private readonly object gate = new();
        private int waiters;
        private bool abandoned;

        public Download(DiskImageCache cache, ImageRequest image, string path, ImagePriority priority)
        {
            this.cache = cache;
            this.image = image;
            this.path = path;
            if (priority == ImagePriority.Visible) promoted.TrySetResult();
            Task = RunAsync();
        }

        public Task<string> Task { get; }

        public bool Join()
        {
            lock (gate)
            {
                if (abandoned || Task.IsCompleted) return false;
                waiters++;
                return true;
            }
        }

        public void Promote() => promoted.TrySetResult();

        public async Task<string> WaitAsync(CancellationToken ct)
        {
            try
            {
                return await Task.WaitAsync(ct).ConfigureAwait(false);
            }
            finally
            {
                lock (gate)
                {
                    if (--waiters == 0 && !Task.IsCompleted)
                    {
                        abandoned = true;
                        abandon.Cancel();
                    }
                }
            }
        }

        private async Task<string> RunAsync()
        {
            await System.Threading.Tasks.Task.Yield();
            try
            {
                var slots = await AcquireSlotAsync().ConfigureAwait(false);
                try
                {
                    return await cache.FetchAsync(image, path, abandon.Token).ConfigureAwait(false);
                }
                finally
                {
                    slots.Release();
                }
            }
            finally
            {
                if (cache.inflight.TryGetValue(path, out var lazy) && lazy.IsValueCreated && ReferenceEquals(lazy.Value, this))
                {
                    cache.inflight.TryRemove(new KeyValuePair<string, Lazy<Download>>(path, lazy));
                }
            }
        }

        private async Task<SemaphoreSlim> AcquireSlotAsync()
        {
            var token = abandon.Token;
            if (promoted.Task.IsCompleted)
            {
                await cache.visibleSlots.WaitAsync(token).ConfigureAwait(false);
                return cache.visibleSlots;
            }
            using var background = CancellationTokenSource.CreateLinkedTokenSource(token);
            var slot = cache.backgroundSlots.WaitAsync(background.Token);
            var first = await System.Threading.Tasks.Task.WhenAny(slot, promoted.Task).ConfigureAwait(false);
            if (first == slot)
            {
                await slot.ConfigureAwait(false);
                return cache.backgroundSlots;
            }
            background.Cancel();
            try
            {
                await slot.ConfigureAwait(false);
                return cache.backgroundSlots;
            }
            catch (OperationCanceledException)
            {
            }
            await cache.visibleSlots.WaitAsync(token).ConfigureAwait(false);
            return cache.visibleSlots;
        }
    }

    private async Task<string> FetchAsync(ImageRequest image, string path, CancellationToken abandoned)
    {
        var watch = Stopwatch.StartNew();
        long bytes = 0;
        var cached = false;
        var success = false;
        string? temp = null;
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(abandoned);
        deadline.CancelAfter(DownloadTimeout);
        using var idle = CancellationTokenSource.CreateLinkedTokenSource(deadline.Token);
        try
        {
            using var politeness = await gate.EnterAsync(image.Url, deadline.Token).ConfigureAwait(false);
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            temp = path + "." + Guid.NewGuid().ToString("N")[..8] + ".part";
            var head = new byte[16];
            var headLength = 0;
            HttpResponseMessage? response = null;
            Stream source;
            if (sources(image.Source) is ISourceImageStreams streams)
            {
                source = await streams.OpenImageAsync(image.Url, deadline.Token).ConfigureAwait(false);
            }
            else
            {
                using var request = sources(image.Source)?.CreateImageRequest(image.Url) ?? new HttpRequestMessage(HttpMethod.Get, image.Url);
                response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, deadline.Token).ConfigureAwait(false);
                response.EnsureSuccessStatusCode();
                cached = response.Headers.TryGetValues("X-Cache", out var values) && values.Any(v => v.StartsWith("HIT", StringComparison.OrdinalIgnoreCase));
                source = await response.Content.ReadAsStreamAsync(deadline.Token).ConfigureAwait(false);
            }
            using var owned = response;
            await using (source)
            await using (var target = File.Create(temp))
            {
                var buffer = new byte[81920];
                while (true)
                {
                    idle.CancelAfter(IdleReadTimeout);
                    var read = await source.ReadAsync(buffer, idle.Token).ConfigureAwait(false);
                    if (read == 0) break;
                    if (headLength < head.Length)
                    {
                        var take = Math.Min(read, head.Length - headLength);
                        Array.Copy(buffer, 0, head, headLength, take);
                        headLength += take;
                    }
                    await target.WriteAsync(buffer.AsMemory(0, read), deadline.Token).ConfigureAwait(false);
                }
                bytes = target.Length;
            }
            if (bytes == 0)
            {
                throw new HttpRequestException($"Empty image response for {image.Url}");
            }
            if (!ImageMagic.IsImage(head.AsSpan(0, headLength)))
            {
                throw new HttpRequestException($"Response for {image.Url} is not an image");
            }
            File.Move(temp, path, overwrite: true);
            temp = null;
            success = true;
            if (Interlocked.Increment(ref downloadsSinceTrim) >= 200)
            {
                Interlocked.Exchange(ref downloadsSinceTrim, 0);
                _ = Task.Run(Trim);
            }
            return path;
        }
        catch (OperationCanceledException ex) when (!abandoned.IsCancellationRequested)
        {
            throw new HttpRequestException($"Image download timed out for {image.Url}", ex);
        }
        catch (HttpRequestException ex) when (ex.StatusCode is System.Net.HttpStatusCode.Forbidden or System.Net.HttpStatusCode.NotFound or System.Net.HttpStatusCode.Gone
            || ex.InnerException is System.Security.Authentication.AuthenticationException)
        {
            refused[path] = DateTimeOffset.UtcNow + RefusalLifetime;
            throw;
        }
        finally
        {
            watch.Stop();
            if (temp != null)
            {
                try
                {
                    File.Delete(temp);
                }
                catch (IOException)
                {
                }
                catch (UnauthorizedAccessException)
                {
                }
            }
            if (!abandoned.IsCancellationRequested)
            {
                Fetched?.Invoke(this, new ImageFetch(image.Url, success, bytes, watch.Elapsed, cached));
            }
        }
    }

    public void Trim()
    {
        List<FileInfo> files;
        try
        {
            files = new DirectoryInfo(root).EnumerateFiles("*", SearchOption.AllDirectories).ToList();
        }
        catch (IOException)
        {
            return;
        }
        catch (UnauthorizedAccessException)
        {
            return;
        }
        var now = DateTime.UtcNow;
        var images = new List<FileInfo>();
        foreach (var file in files)
        {
            if (!file.Name.EndsWith(".part", StringComparison.Ordinal))
            {
                images.Add(file);
            }
            else if (now - file.LastWriteTimeUtc > PartLifetime)
            {
                TryDelete(file);
            }
        }
        var total = images.Sum(f => f.Length);
        if (total <= maxBytes)
        {
            return;
        }
        foreach (var file in images.OrderBy(f => f.LastWriteTimeUtc))
        {
            if (total <= maxBytes * 0.85)
            {
                break;
            }
            if (now - file.LastWriteTimeUtc < RecentGrace) continue;
            var length = file.Length;
            if (TryDelete(file)) total -= length;
        }
    }

    private static bool TryDelete(FileInfo file)
    {
        try
        {
            file.Delete();
            return true;
        }
        catch (IOException)
        {
            return false;
        }
        catch (UnauthorizedAccessException)
        {
            return false;
        }
    }
}

internal static class ImageMagic
{
    public static bool IsImage(ReadOnlySpan<byte> head)
    {
        if (head.StartsWith((ReadOnlySpan<byte>)[0xFF, 0xD8, 0xFF])) return true;
        if (head.StartsWith((ReadOnlySpan<byte>)[0x89, (byte)'P', (byte)'N', (byte)'G', 0x0D, 0x0A, 0x1A, 0x0A])) return true;
        if (head.StartsWith("GIF87a"u8) || head.StartsWith("GIF89a"u8)) return true;
        if (head.StartsWith("BM"u8)) return true;
        if (head.Length >= 12 && head.StartsWith("RIFF"u8) && head.Slice(8, 4).SequenceEqual("WEBP"u8)) return true;
        if (head.Length >= 12 && head.Slice(4, 4).SequenceEqual("ftyp"u8))
        {
            var brand = head.Slice(8, 4);
            if (brand.SequenceEqual("avif"u8) || brand.SequenceEqual("avis"u8)) return true;
        }
        if (head.StartsWith((ReadOnlySpan<byte>)[0xFF, 0x0A])) return true;
        if (head.StartsWith((ReadOnlySpan<byte>)[0x00, 0x00, 0x00, 0x0C, (byte)'J', (byte)'X', (byte)'L', (byte)' ', 0x0D, 0x0A, 0x87, 0x0A])) return true;
        return false;
    }
}
