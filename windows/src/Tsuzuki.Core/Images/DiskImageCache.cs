using System.Collections.Concurrent;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;

namespace Tsuzuki.Core.Images;

public sealed record ImageFetch(Uri Url, bool Success, long Bytes, TimeSpan Duration, bool Cached);

public sealed class DiskImageCache : IImageCache
{
    private readonly string root;
    private readonly HttpClient http;
    private readonly long maxBytes;
    private readonly SemaphoreSlim visibleSlots;
    private readonly SemaphoreSlim backgroundSlots;
    private readonly ConcurrentDictionary<string, Task<string>> inflight = new();
    private int downloadsSinceTrim;

    public DiskImageCache(string root, HttpClient http, long maxBytes, int visibleConcurrency = 6, int backgroundConcurrency = 3)
    {
        this.root = root;
        this.http = http;
        this.maxBytes = maxBytes;
        visibleSlots = new SemaphoreSlim(visibleConcurrency);
        backgroundSlots = new SemaphoreSlim(backgroundConcurrency);
        Directory.CreateDirectory(root);
    }

    public event EventHandler<ImageFetch>? Fetched;

    public string PathFor(Uri url)
    {
        var hash = Convert.ToHexStringLower(SHA1.HashData(Encoding.UTF8.GetBytes(url.AbsoluteUri)));
        var ext = Path.GetExtension(url.AbsolutePath);
        if (ext.Length is 0 or > 5)
        {
            ext = ".img";
        }
        return Path.Combine(root, hash[..2], hash + ext);
    }

    public Task<string> GetFileAsync(Uri url, ImagePriority priority, CancellationToken ct = default)
    {
        var path = PathFor(url);
        if (File.Exists(path))
        {
            return Task.FromResult(path);
        }
        var task = inflight.GetOrAdd(path, _ => DownloadAsync(url, path, priority));
        return task.WaitAsync(ct);
    }

    public void Prefetch(IEnumerable<Uri> urls)
    {
        foreach (var url in urls)
        {
            _ = GetFileAsync(url, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
    }

    private async Task<string> DownloadAsync(Uri url, string path, ImagePriority priority)
    {
        var slots = priority == ImagePriority.Visible ? visibleSlots : backgroundSlots;
        await slots.WaitAsync().ConfigureAwait(false);
        var watch = Stopwatch.StartNew();
        long bytes = 0;
        var cached = false;
        var success = false;
        try
        {
            using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();
            cached = response.Headers.TryGetValues("X-Cache", out var values) && values.Any(v => v.StartsWith("HIT", StringComparison.OrdinalIgnoreCase));
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var temp = path + "." + Guid.NewGuid().ToString("N")[..8] + ".part";
            await using (var source = await response.Content.ReadAsStreamAsync().ConfigureAwait(false))
            await using (var target = File.Create(temp))
            {
                await source.CopyToAsync(target).ConfigureAwait(false);
                bytes = target.Length;
            }
            if (bytes == 0)
            {
                File.Delete(temp);
                throw new IOException($"Empty image response for {url}");
            }
            File.Move(temp, path, overwrite: true);
            success = true;
            if (Interlocked.Increment(ref downloadsSinceTrim) >= 200)
            {
                Interlocked.Exchange(ref downloadsSinceTrim, 0);
                _ = Task.Run(Trim);
            }
            return path;
        }
        finally
        {
            watch.Stop();
            slots.Release();
            inflight.TryRemove(path, out _);
            Fetched?.Invoke(this, new ImageFetch(url, success, bytes, watch.Elapsed, cached));
        }
    }

    public void Trim()
    {
        try
        {
            var files = new DirectoryInfo(root).EnumerateFiles("*", SearchOption.AllDirectories)
                .Where(f => !f.Name.EndsWith(".part", StringComparison.Ordinal))
                .ToList();
            var total = files.Sum(f => f.Length);
            if (total <= maxBytes)
            {
                return;
            }
            foreach (var file in files.OrderBy(f => f.LastAccessTimeUtc))
            {
                if (total <= maxBytes * 0.85)
                {
                    break;
                }
                total -= file.Length;
                file.Delete();
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
