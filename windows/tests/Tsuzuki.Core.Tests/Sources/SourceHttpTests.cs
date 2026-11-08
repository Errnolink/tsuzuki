using System.Net;
using System.Text;
using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class SourceHttpTests
{
    private static Func<HttpRequestMessage> Get(string url) => () => new HttpRequestMessage(HttpMethod.Get, url);

    [Fact]
    public async Task A_cloudflare_challenge_is_reported_as_the_source_blocking_this_pc()
    {
        var server = new RecordingHttp(_ => new HttpResponseMessage(HttpStatusCode.Forbidden)
        {
            Content = new StringContent("<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>", Encoding.UTF8, "text/html"),
        });
        var http = new SourceHttp(new HttpClient(server), "Weeb Central", _ => TimeSpan.Zero);

        var error = await Assert.ThrowsAsync<SourceBlockedException>(() => http.GetStringAsync(Get("https://weebcentral.com/search/data"), null, TestContext.Current.CancellationToken));

        Assert.Equal("Weeb Central", error.SourceName);
        Assert.Single(server.Requests);
    }

    [Fact]
    public async Task A_challenge_header_is_detected_even_on_a_server_error()
    {
        var server = new RecordingHttp(_ =>
        {
            var response = new HttpResponseMessage(HttpStatusCode.ServiceUnavailable) { Content = new StringContent("") };
            response.Headers.Add("cf-mitigated", "challenge");
            return response;
        });
        var http = new SourceHttp(new HttpClient(server), "Weeb Central", _ => TimeSpan.Zero);

        await Assert.ThrowsAsync<SourceBlockedException>(() => http.GetStringAsync(Get("https://weebcentral.com/"), null, TestContext.Current.CancellationToken));
        Assert.Single(server.Requests);
    }

    [Fact]
    public async Task Plain_server_errors_are_retried_then_succeed()
    {
        var calls = 0;
        var server = new RecordingHttp(_ => ++calls < 3
            ? new HttpResponseMessage(HttpStatusCode.BadGateway) { Content = new StringContent("") }
            : new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("ok") });
        var http = new SourceHttp(new HttpClient(server), "Weeb Central", _ => TimeSpan.Zero);

        var body = await http.GetStringAsync(Get("https://weebcentral.com/"), null, TestContext.Current.CancellationToken);

        Assert.Equal("ok", body);
        Assert.Equal(3, calls);
    }
}
