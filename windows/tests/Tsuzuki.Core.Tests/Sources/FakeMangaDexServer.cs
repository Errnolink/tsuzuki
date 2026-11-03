using System.Net;
using System.Text;
using System.Text.Json;
using Tsuzuki.Core.Sources.MangaDex;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class FakeMangaDexServer : HttpMessageHandler
{
    public const string YotsubaUuid = "58be6aa6-06cb-4ca5-bd20-f1392ce451fb";

    private readonly Func<string, string, HttpResponseMessage> resolve;

    public FakeMangaDexServer(Func<string, string, HttpResponseMessage> resolve)
    {
        this.resolve = resolve;
    }

    public List<string> Requests { get; } = [];

    public List<string> UserAgents { get; } = [];

    public static MangaDexSource Source(FakeMangaDexServer server, MangaDexOptions? options = null)
    {
        var resolved = options ?? new MangaDexOptions();
        return new MangaDexSource(new MangaDexClient(new HttpClient(server)), resolved, resolved.Languages[0]);
    }

    public static FakeMangaDexServer FromFixtures()
        => new((path, query) => Json(FixtureFor(path, query)));

    public static string FixtureFor(string path, string query) => (path, query) switch
    {
        (var p, _) when p == $"/manga/{YotsubaUuid}/aggregate" => "yotsuba-aggregate.json",
        (var p, _) when p == $"/manga/{YotsubaUuid}/feed" => "yotsuba-feed.json",
        (var p, _) when p == $"/manga/{YotsubaUuid}" => "yotsuba-details.json",
        ("/manga", var q) when q.Contains("order%5BfollowedCount%5D=desc") => "popular-en.json",
        ("/manga", var q) when q.Contains("ids%5B%5D=") => "latest-mangas.json",
        ("/manga", var q) when q.Contains("title=") => "search-yotsuba.json",
        ("/manga", _) => "search-yotsuba.json",
        ("/chapter", _) => "latest-en.json",
        (var p, _) when p.StartsWith("/at-home/server/") => "at-home.json",
        (var p, _) when p.StartsWith("/statistics/manga/") => "yotsuba-statistics.json",
        _ => throw new InvalidOperationException($"No fixture route for {path}?{query}"),
    };

    public static HttpResponseMessage Json(string fixtureName, HttpStatusCode status = HttpStatusCode.OK)
        => Raw(ReadFixture(fixtureName), status);

    public static string ReadFixture(string fixtureName)
        => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Sources", "Fixtures", fixtureName));

    public static HttpResponseMessage Raw(string body, HttpStatusCode status = HttpStatusCode.OK)
        => new(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };

    public static HttpResponseMessage StatusCode(HttpStatusCode status)
        => new(status) { Content = new StringContent("") };

    public static string FixtureWithTotal(string fixtureName, int total, int offset)
    {
        var document = JsonDocument.Parse(ReadFixture(fixtureName));
        var buffer = new MemoryStream();
        using (var writer = new Utf8JsonWriter(buffer))
        {
            writer.WriteStartObject();
            foreach (var property in document.RootElement.EnumerateObject())
            {
                if (property.NameEquals("total"))
                {
                    writer.WriteNumber("total", total);
                }
                else if (property.NameEquals("offset"))
                {
                    writer.WriteNumber("offset", offset);
                }
                else
                {
                    property.WriteTo(writer);
                }
            }
            writer.WriteEndObject();
        }
        return Encoding.UTF8.GetString(buffer.ToArray());
    }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        var uri = request.RequestUri!;
        var path = uri.AbsolutePath;
        var query = uri.Query.Length > 0 ? uri.Query[1..] : "";
        Requests.Add(path + (query.Length > 0 ? $"?{query}" : ""));
        UserAgents.Add(string.Join(" ", request.Headers.UserAgent.Select(u => u.ToString())));
        return Task.FromResult(resolve(path, query));
    }
}
