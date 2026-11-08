using System.Net;

namespace Tsuzuki.Core.Sources;

public sealed class SourceBlockedException(string sourceName, Uri url, HttpStatusCode status)
    : HttpRequestException($"{sourceName} is blocking requests from this PC right now ({(int)status} challenge for {url})", null, status)
{
    public string SourceName { get; } = sourceName;
}

public sealed class SourceHttp
{
    public const int MaxRetries = 3;

    private static readonly TimeSpan MaxRetryWait = TimeSpan.FromSeconds(60);

    private readonly HttpClient http;
    private readonly string sourceName;
    private readonly Func<int, TimeSpan> retryDelay;

    public SourceHttp(HttpClient http, string sourceName, Func<int, TimeSpan>? retryDelay = null)
    {
        this.http = http;
        this.sourceName = sourceName;
        this.retryDelay = retryDelay ?? (attempt => TimeSpan.FromSeconds(1 << attempt));
    }

    public async Task<HttpResponseMessage> SendAsync(Func<HttpRequestMessage> create, TokenBucket? bucket, CancellationToken ct = default)
    {
        for (var attempt = 0; ; attempt++)
        {
            if (bucket is not null)
            {
                await bucket.AcquireAsync(ct).ConfigureAwait(false);
            }
            using var request = create();
            var url = request.RequestUri!;
            var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            if (response.IsSuccessStatusCode)
            {
                return response;
            }
            TimeSpan wait;
            using (response)
            {
                var status = response.StatusCode;
                if (await IsChallengeAsync(response, ct).ConfigureAwait(false))
                {
                    throw new SourceBlockedException(sourceName, url, status);
                }
                var retryable = status == HttpStatusCode.TooManyRequests || (int)status >= 500;
                if (!retryable || attempt >= MaxRetries)
                {
                    throw new HttpRequestException($"{sourceName} returned {(int)status} for {url}", null, status);
                }
                wait = status == HttpStatusCode.TooManyRequests
                    ? RetryAfterFrom(response) ?? retryDelay(attempt)
                    : retryDelay(attempt);
            }
            await Task.Delay(wait, ct).ConfigureAwait(false);
        }
    }

    public async Task<string> GetStringAsync(Func<HttpRequestMessage> create, TokenBucket? bucket, CancellationToken ct = default)
    {
        using var response = await SendAsync(create, bucket, ct).ConfigureAwait(false);
        return await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
    }

    private static async Task<bool> IsChallengeAsync(HttpResponseMessage response, CancellationToken ct)
    {
        if (response.Headers.NonValidated.TryGetValues("cf-mitigated", out var mitigated)
            && mitigated.Any(v => v.Equals("challenge", StringComparison.OrdinalIgnoreCase)))
        {
            return true;
        }
        if (response.StatusCode is not (HttpStatusCode.Forbidden or HttpStatusCode.ServiceUnavailable))
        {
            return false;
        }
        var body = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        return body.Contains("<title>Just a moment", StringComparison.OrdinalIgnoreCase)
            || body.Contains("challenge-platform/h/", StringComparison.Ordinal);
    }

    private static TimeSpan? RetryAfterFrom(HttpResponseMessage response)
    {
        if (response.Headers.NonValidated.TryGetValues("X-RateLimit-Retry-After", out var unixValues)
            && long.TryParse(unixValues.FirstOrDefault(), out var unixSeconds))
        {
            var unix = DateTimeOffset.FromUnixTimeSeconds(unixSeconds) - DateTimeOffset.UtcNow;
            if (unix > TimeSpan.Zero)
            {
                return Clamp(unix + TimeSpan.FromMilliseconds(250));
            }
        }
        if (response.Headers.RetryAfter is { } retry)
        {
            var wait = retry.Delta ?? (retry.Date is { } date ? date - DateTimeOffset.UtcNow : null);
            if (wait > TimeSpan.Zero)
            {
                return Clamp(wait.Value + TimeSpan.FromMilliseconds(250));
            }
        }
        return null;
    }

    private static TimeSpan Clamp(TimeSpan wait) => wait > MaxRetryWait ? MaxRetryWait : wait;
}
