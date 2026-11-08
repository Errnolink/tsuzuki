using System.Net;
using System.Text.Json;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed record MangaStatistics(double Rating, long Follows, long Comments);

public sealed class MangaDexClient
{
    public const string ApiBaseUrl = "https://api.mangadex.org";
    public const string CdnBaseUrl = "https://uploads.mangadex.org";
    public const string UserAgent = "Tsuzuki/0.1 (Windows)";

    private readonly SourceHttp http;
    private readonly TokenBucket apiTokens = new(5, 5);
    private readonly TokenBucket atHomeTokens = new(40, 40.0 / 60.0);

    public MangaDexClient(HttpClient http, Func<int, TimeSpan>? retryDelay = null)
    {
        this.http = new SourceHttp(http, MangaDexIds.SourceName, retryDelay);
    }

    public async Task<T?> GetAsync<T>(string path, CancellationToken ct = default) where T : class
    {
        var bucket = path.StartsWith("/at-home/", StringComparison.Ordinal) ? atHomeTokens : apiTokens;
        using var response = await http.SendAsync(() =>
        {
            var request = new HttpRequestMessage(HttpMethod.Get, ApiBaseUrl + path);
            request.Headers.UserAgent.ParseAdd(UserAgent);
            return request;
        }, bucket, ct).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return null;
        }
        await using var body = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        try
        {
            return (T?)await JsonSerializer.DeserializeAsync(body, typeof(T), MangaDexJsonContext.Default, ct).ConfigureAwait(false);
        }
        catch (JsonException ex)
        {
            throw new HttpRequestException($"MangaDex returned an unreadable response for {path}", ex);
        }
    }

    public async Task<MangaStatistics> GetStatisticsAsync(string mangaUuid, CancellationToken ct = default)
    {
        var response = await GetAsync<MangaStatisticsResponse>($"/statistics/manga/{mangaUuid}", ct).ConfigureAwait(false);
        var entry = response?.Statistics.Values.FirstOrDefault()
            ?? throw new HttpRequestException($"MangaDex returned no statistics for {mangaUuid}");
        return new MangaStatistics(
            entry.Rating?.Bayesian ?? 0,
            entry.Follows,
            entry.Comments?.RepliesCount ?? entry.Comments?.Chapter ?? 0);
    }
}
