using System.Text;
using System.Text.Json;
using Tsuzuki.Core.Images;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed class MangaDexAtHomeReporter : IDisposable
{
    public const string ReportEndpoint = "https://api.mangadex.network/report";

    private readonly HttpClient http;
    private DiskImageCache? pages;

    private MangaDexAtHomeReporter(HttpClient http)
    {
        this.http = http;
    }

    public static MangaDexAtHomeReporter Attach(DiskImageCache pages, HttpClient http)
    {
        var reporter = new MangaDexAtHomeReporter(http);
        pages.Fetched += reporter.OnFetched;
        reporter.pages = pages;
        return reporter;
    }

    public void Dispose()
    {
        pages?.Fetched -= OnFetched;
        pages = null;
    }

    private void OnFetched(object? sender, ImageFetch fetch)
    {
        if (!fetch.Url.Host.EndsWith(".mangadex.network", StringComparison.OrdinalIgnoreCase))
        {
            return;
        }
        _ = Task.Run(() => ReportAsync(fetch));
    }

    private async Task ReportAsync(ImageFetch fetch)
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, ReportEndpoint);
            request.Headers.UserAgent.ParseAdd(MangaDexClient.UserAgent);
            var body = new ImageReport(
                fetch.Url.ToString(),
                fetch.Success,
                (int)Math.Clamp(fetch.Bytes, 0, int.MaxValue),
                fetch.Cached,
                (long)fetch.Duration.TotalMilliseconds);
            request.Content = new StringContent(
                JsonSerializer.Serialize(body, MangaDexJsonContext.Default.ImageReport),
                Encoding.UTF8,
                "application/json");
            using var response = await http.SendAsync(request).ConfigureAwait(false);
        }
        catch (Exception)
        {
        }
    }
}
