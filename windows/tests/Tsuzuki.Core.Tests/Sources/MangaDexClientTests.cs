using System.Net;
using Tsuzuki.Core.Sources.MangaDex;
using Tsuzuki.Core.Tests.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class MangaDexClientTests
{
    private static MangaDexClient Client(HttpMessageHandler handler, Func<int, TimeSpan>? retryDelay = null)
    {
        var httpClient = new HttpClient(handler);
        return new MangaDexClient(httpClient, retryDelay);
    }

    [Fact]
    public async Task Retries_after_the_requested_429_delay_and_succeeds()
    {
        var calls = 0;
        var server = new FakeMangaDexServer((_, _) => ++calls < 2
            ? Response(HttpStatusCode.TooManyRequests, retryAfterSeconds: "1")
            : FakeMangaDexServer.Json("yotsuba-statistics.json"));
        var client = Client(server);

        var statistics = await client.GetStatisticsAsync(FakeMangaDexServer.YotsubaUuid, TestContext.Current.CancellationToken);

        Assert.Equal(2, calls);
        Assert.Equal(50462, statistics.Follows);
    }

    [Fact]
    public async Task Honours_the_rate_limit_retry_after_header_over_the_default_backoff()
    {
        var retryAt = DateTimeOffset.UtcNow.AddSeconds(2).ToUnixTimeSeconds().ToString();
        var calls = 0;
        var server = new FakeMangaDexServer((_, _) => ++calls < 2
            ? Response(HttpStatusCode.TooManyRequests, rateLimitRetryAfter: retryAt)
            : FakeMangaDexServer.Json("search-yotsuba.json"));
        var client = Client(server, _ => TimeSpan.Zero);
        var started = Environment.TickCount64;

        await client.GetAsync<MangaListResponse>("/manga?limit=1", TestContext.Current.CancellationToken);

        var elapsed = Environment.TickCount64 - started;
        Assert.True(elapsed >= 1100, $"expected the X-RateLimit-Retry-After wait, took {elapsed}ms");
        Assert.Equal(2, calls);
    }

    [Fact]
    public async Task Retries_server_errors_with_backoff_and_succeeds()
    {
        var calls = 0;
        var server = new FakeMangaDexServer((_, _) => ++calls < 3
            ? FakeMangaDexServer.StatusCode(HttpStatusCode.BadGateway)
            : FakeMangaDexServer.Json("at-home.json"));
        var client = Client(server, _ => TimeSpan.FromMilliseconds(1));

        var atHome = await client.GetAsync<AtHomeResponse>("/at-home/server/abc", TestContext.Current.CancellationToken);

        Assert.Equal(3, calls);
        Assert.Equal("https://cmdxd98sb0x3yprd.mangadex.network", atHome!.BaseUrl);
    }

    [Fact]
    public async Task Does_not_retry_client_errors()
    {
        var server = new FakeMangaDexServer((_, _) => FakeMangaDexServer.StatusCode(HttpStatusCode.NotFound));
        var client = Client(server);

        await Assert.ThrowsAsync<HttpRequestException>(() => client.GetAsync<MangaListResponse>("/manga?limit=1", TestContext.Current.CancellationToken));

        Assert.Equal(1, server.Requests.Count);
    }

    [Fact]
    public async Task Gives_up_after_three_429_retries()
    {
        var server = new FakeMangaDexServer((_, _) => Response(HttpStatusCode.TooManyRequests, retryAfterSeconds: "0"));
        var client = Client(server, _ => TimeSpan.Zero);

        await Assert.ThrowsAsync<HttpRequestException>(() => client.GetAsync<MangaListResponse>("/manga?limit=1", TestContext.Current.CancellationToken));

        Assert.Equal(4, server.Requests.Count);
    }

    [Fact]
    public async Task Statistics_map_rating_follows_and_comments()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var client = Client(server);

        var statistics = await client.GetStatisticsAsync(FakeMangaDexServer.YotsubaUuid, TestContext.Current.CancellationToken);

        Assert.True(Math.Abs(statistics.Rating - 9.598666667454799) < 1e-9);
        Assert.Equal(50462, statistics.Follows);
        Assert.Equal(170, statistics.Comments);
    }

    [Fact]
    public async Task Sends_the_tsuzuki_user_agent()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var client = Client(server);

        await client.GetAsync<MangaListResponse>("/manga?limit=1", TestContext.Current.CancellationToken);

        Assert.Equal("Tsuzuki/0.1 (Windows)", server.UserAgents.Single());
    }

    private static HttpResponseMessage Response(HttpStatusCode status, string? retryAfterSeconds = null, string? rateLimitRetryAfter = null)
    {
        var response = FakeMangaDexServer.StatusCode(status);
        if (retryAfterSeconds != null)
        {
            response.Headers.RetryAfter = new System.Net.Http.Headers.RetryConditionHeaderValue(TimeSpan.FromSeconds(int.Parse(retryAfterSeconds)));
        }
        if (rateLimitRetryAfter != null)
        {
            response.Headers.Add("X-RateLimit-Retry-After", rateLimitRetryAfter);
        }
        return response;
    }
}
