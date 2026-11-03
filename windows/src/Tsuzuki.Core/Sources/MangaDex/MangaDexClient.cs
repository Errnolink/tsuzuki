using System.Net;
using System.Text.Json;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed record MangaStatistics(double Rating, long Follows, long Comments);

public sealed class MangaDexClient
{
    public const string ApiBaseUrl = "https://api.mangadex.org";
    public const string CdnBaseUrl = "https://uploads.mangadex.org";
    public const string UserAgent = "Tsuzuki/0.1 (Windows)";

    private const int MaxRetries = 3;
    private static readonly TimeSpan Max429Wait = TimeSpan.FromSeconds(60);

    private readonly HttpClient http;
    private readonly TokenBucket apiTokens = new(5, 5);
    private readonly TokenBucket atHomeTokens = new(40, 40.0 / 60.0);
    private readonly Func<int, TimeSpan> retryDelay;

    public MangaDexClient(HttpClient http, Func<int, TimeSpan>? retryDelay = null)
    {
        this.http = http;
        this.retryDelay = retryDelay ?? (attempt => TimeSpan.FromSeconds(1 << attempt));
    }

    public async Task<T?> GetAsync<T>(string path, CancellationToken ct = default) where T : class
    {
        var bucket = path.StartsWith("/at-home/", StringComparison.Ordinal) ? atHomeTokens : apiTokens;
        for (var attempt = 0; ; attempt++)
        {
            var (status, retryAfter, body) = await SendAsync(path, bucket, ct).ConfigureAwait(false);
            if (status == HttpStatusCode.NoContent)
            {
                return null;
            }
            if ((int)status is >= 200 and < 300)
            {
                return (T?)JsonSerializer.Deserialize(body, typeof(T), MangaDexJsonContext.Default);
            }
            if (status == HttpStatusCode.TooManyRequests)
            {
                if (attempt >= MaxRetries)
                {
                    throw new HttpRequestException($"MangaDex API kept returning 429 for {path}");
                }
                await Task.Delay(retryAfter ?? retryDelay(attempt), ct).ConfigureAwait(false);
                continue;
            }
            if ((int)status >= 500)
            {
                if (attempt >= MaxRetries)
                {
                    throw new HttpRequestException($"MangaDex API returned {(int)status} for {path}");
                }
                await Task.Delay(retryDelay(attempt), ct).ConfigureAwait(false);
                continue;
            }
            throw new HttpRequestException($"MangaDex API returned {(int)status} for {path}");
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

    private async Task<(HttpStatusCode Status, TimeSpan? RetryAfter, string Body)> SendAsync(
        string path, TokenBucket bucket, CancellationToken ct)
    {
        await bucket.AcquireAsync(ct).ConfigureAwait(false);
        using var request = new HttpRequestMessage(HttpMethod.Get, ApiBaseUrl + path);
        request.Headers.UserAgent.ParseAdd(UserAgent);
        using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
        var body = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        return (response.StatusCode, RetryAfterFrom(response), body);
    }

    private static TimeSpan? RetryAfterFrom(HttpResponseMessage response)
    {
        var unix = response.Headers.NonValidated.TryGetValues("X-RateLimit-Retry-After", out var unixValues)
            && long.TryParse(unixValues.FirstOrDefault(), out var unixSeconds)
            ? DateTimeOffset.FromUnixTimeSeconds(unixSeconds) - DateTimeOffset.UtcNow
            : (TimeSpan?)null;
        if (unix is { TotalMilliseconds: > 0 })
        {
            return Clamp(unix.Value + TimeSpan.FromMilliseconds(250));
        }

        if (response.Headers.RetryAfter is { } retry)
        {
            var wait = retry.Delta ?? (retry.Date is { } date ? date - DateTimeOffset.UtcNow : (TimeSpan?)null);
            if (wait is { TotalMilliseconds: > 0 })
            {
                return Clamp(wait.Value + TimeSpan.FromMilliseconds(250));
            }
        }
        return null;
    }

    private static TimeSpan Clamp(TimeSpan wait)
        => wait > Max429Wait ? Max429Wait : wait;
}
