using System.Net;
using System.Text;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class RecordingHttp(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
{
    public List<HttpRequestMessage> Requests { get; } = [];

    public static HttpResponseMessage Html(string fixtureName)
        => new(HttpStatusCode.OK) { Content = new StringContent(FakeMangaDexServer.ReadFixture(fixtureName), Encoding.UTF8, "text/html") };

    public static HttpResponseMessage Bytes(byte[] body)
        => new(HttpStatusCode.OK) { Content = new ByteArrayContent(body) };

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        lock (Requests)
        {
            Requests.Add(request);
        }
        return Task.FromResult(respond(request));
    }
}
