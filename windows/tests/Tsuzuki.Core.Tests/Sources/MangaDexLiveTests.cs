using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class MangaDexLiveTests(ITestOutputHelper output)
{
    private void Report(string message)
    {
        output.WriteLine(message);
        Console.WriteLine(message);
    }

    [Fact]
    public async Task Searches_yotsuba_and_fetches_chapters_and_one_page_from_the_live_api()
    {
        if (Environment.GetEnvironmentVariable("TSUZUKI_LIVE") != "1")
        {
            return;
        }

        using var http = new HttpClient();
        http.DefaultRequestHeaders.UserAgent.ParseAdd(MangaDexClient.UserAgent);
        var source = MangaDexSources.Create(http, new MangaDexOptions())[0];

        var search = await source.SearchAsync("Yotsuba", 1, TestContext.Current.CancellationToken);
        Report($"search 'Yotsuba' page 1: {search.Mangas.Count} mangas, hasNextPage={search.HasNextPage}");
        Assert.NotEmpty(search.Mangas);

        var yotsuba = search.Mangas.Single(m => m.Url == "/manga/58be6aa6-06cb-4ca5-bd20-f1392ce451fb");
        var details = await source.GetMangaDetailsAsync(yotsuba.Url, TestContext.Current.CancellationToken);
        Report($"details: title='{details.Title}' status={details.Status} genres=[{string.Join(", ", details.Genres)}]");
        Assert.Equal("Yotsuba&!", details.Title);

        var chapters = await source.GetChapterListAsync(yotsuba.Url);
        Report($"chapters: {chapters.Count}, newest='{chapters[0].Name}' by {chapters[0].Scanlator}");
        Assert.NotEmpty(chapters);

        var readable = chapters.First(c => (c.PageCount ?? 0) > 0);
        var pages = await source.GetPageListAsync(readable.Url, TestContext.Current.CancellationToken);
        Report($"pages for '{readable.Name}': {pages.Count}");
        Assert.NotEmpty(pages);

        using var response = await http.GetAsync(pages[0].ImageUrl, TestContext.Current.CancellationToken);
        Report($"page fetch: {(int)response.StatusCode} {response.Content.Headers.ContentType} {response.Content.Headers.ContentLength} bytes");
        response.EnsureSuccessStatusCode();
    }
}
