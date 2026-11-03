using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Tsuzuki.Core.Tests.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class MangaDexSourceTests
{
    private const string YotsubaUrl = "/manga/58be6aa6-06cb-4ca5-bd20-f1392ce451fb";

    [Fact]
    public async Task Search_returns_the_browse_page_with_extension_defaults()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var page = await source.SearchAsync("Yotsuba", 1, TestContext.Current.CancellationToken);

        Assert.Equal(20, page.Mangas.Count);
        Assert.True(page.HasNextPage);
        var request = server.Requests.Single();
        Assert.StartsWith("/manga?", request);
        Assert.Contains("title=Yotsuba", request);
        Assert.Contains("contentRating%5B%5D=safe", request);
        Assert.Contains("contentRating%5B%5D=suggestive", request);
        Assert.Contains("order%5Brelevance%5D=desc", request);
        Assert.Contains("includedTagsMode=AND", request);
        Assert.Contains("excludedTagsMode=AND", request);
        Assert.Contains("limit=20", request);
        Assert.Contains("offset=0", request);
    }

    [Fact]
    public async Task Search_page_two_uses_the_manga_offset()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        await source.SearchAsync("Yotsuba", 2, TestContext.Current.CancellationToken);

        Assert.Contains("offset=20", server.Requests.Single());
    }

    [Fact]
    public async Task Popular_filters_to_the_language_and_recent_creations()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var page = await source.GetPopularAsync(1, TestContext.Current.CancellationToken);

        Assert.Equal(20, page.Mangas.Count);
        Assert.True(page.HasNextPage);
        var request = server.Requests.Single();
        Assert.Contains("order%5BfollowedCount%5D=desc", request);
        Assert.Contains("availableTranslatedLanguage%5B%5D=en", request);
        Assert.Contains("includes%5B%5D=cover_art", request);
        Assert.Matches(@"createdAtSince=\d{4}-\d{2}-\d{2}T00%3A00%3A00", request);
    }

    [Fact]
    public async Task Latest_resolves_mangas_behind_the_chapter_feed()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var page = await source.GetLatestAsync(1, TestContext.Current.CancellationToken);

        Assert.Equal(53, page.Mangas.Count);
        Assert.True(page.HasNextPage);
        Assert.Equal(2, server.Requests.Count);
        var chapterRequest = server.Requests[0];
        Assert.StartsWith("/chapter?", chapterRequest);
        Assert.Contains("translatedLanguage%5B%5D=en", chapterRequest);
        Assert.Contains("order%5BpublishAt%5D=desc", chapterRequest);
        Assert.Contains("includeFutureUpdates=0", chapterRequest);
        Assert.Contains("includeFuturePublishAt=0", chapterRequest);
        Assert.Contains("includeEmptyPages=0", chapterRequest);
        Assert.Contains("excludedGroups%5B%5D=4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb", chapterRequest);
        var mangaRequest = server.Requests[1];
        Assert.Contains("ids%5B%5D=", mangaRequest);
        Assert.Contains("limit=53", mangaRequest);
    }

    [Fact]
    public async Task Details_combine_manga_aggregate_and_cover()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var manga = await source.GetMangaDetailsAsync(YotsubaUrl, TestContext.Current.CancellationToken);

        Assert.Equal("Yotsuba&!", manga.Title);
        Assert.Equal(MangaStatus.OnHiatus, manga.Status);
        Assert.Equal("Azuma Kiyohiko", manga.Author);
        Assert.Equal(2, server.Requests.Count);
        Assert.Contains("/aggregate", server.Requests[1]);
    }

    [Fact]
    public async Task Chapter_list_walks_the_feed_and_drops_invalid_externals()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var chapters = await source.GetChapterListAsync(YotsubaUrl, TestContext.Current.CancellationToken);

        Assert.Equal(135, chapters.Count);
        Assert.Equal("Vol.16 Ch.114 - Yotsuba & The Teacher", chapters[0].Name);
        Assert.DoesNotContain(chapters, c => c.ExternalUrl != null);
    }

    [Fact]
    public async Task Chapter_list_pages_through_feeds_longer_than_500()
    {
        var calls = 0;
        var server = new FakeMangaDexServer((_, _) => FakeMangaDexServer.Raw(
            FakeMangaDexServer.FixtureWithTotal("yotsuba-feed.json", 600, calls++ == 0 ? 0 : 500)));
        var source = FakeMangaDexServer.Source(server);

        var chapters = await source.GetChapterListAsync(YotsubaUrl, TestContext.Current.CancellationToken);

        Assert.Equal(270, chapters.Count);
        Assert.Equal(2, calls);
        Assert.Contains("offset=0", server.Requests[0]);
        Assert.Contains("limit=500", server.Requests[0]);
        Assert.Contains("offset=500", server.Requests[1]);
    }

    [Fact]
    public async Task Onepiece_chapter_list_keeps_only_the_hosted_chapter()
    {
        var server = new FakeMangaDexServer((path, query) =>
            path == "/manga/a1c7c817-4e59-43b7-9365-09675a149a6f/feed"
                ? FakeMangaDexServer.Json("onepiece-feed.json")
                : throw new InvalidOperationException($"unexpected {path}"));
        var source = FakeMangaDexServer.Source(server);

        var chapters = await source.GetChapterListAsync("/manga/a1c7c817-4e59-43b7-9365-09675a149a6f", TestContext.Current.CancellationToken);

        var chapter = Assert.Single(chapters);
        Assert.Equal("Ch.1191 - Loki Is Here", chapter.Name);
        Assert.Null(chapter.ExternalUrl);
    }

    [Fact]
    public async Task Page_list_builds_full_quality_at_home_urls()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server);

        var pages = await source.GetPageListAsync("/chapter/b82f842f-d581-49cf-bee1-6f9d732e57ad", TestContext.Current.CancellationToken);

        Assert.Equal(47, pages.Count);
        Assert.Equal(0, pages[0].Index);
        Assert.Equal(46, pages[46].Index);
        Assert.StartsWith("https://cmdxd98sb0x3yprd.mangadex.network/data/35c5c3a99affd9f4dc46f623d29c7935/", pages[0].ImageUrl.ToString());
        Assert.EndsWith("1-a4b720782936009cb13b7b622e3da460e65c6fa6f00bda26b782f3753e064827.jpg", pages[0].ImageUrl.ToString());
        Assert.StartsWith("/at-home/server/b82f842f-d581-49cf-bee1-6f9d732e57ad", server.Requests.Single());
    }

    [Fact]
    public async Task Data_saver_option_builds_data_saver_urls()
    {
        var server = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(server, new MangaDexOptions { DataSaver = true });

        var pages = await source.GetPageListAsync("/chapter/b82f842f-d581-49cf-bee1-6f9d732e57ad", TestContext.Current.CancellationToken);

        Assert.Equal(47, pages.Count);
        Assert.Contains("/data-saver/35c5c3a99affd9f4dc46f623d29c7935/", pages[0].ImageUrl.ToString());
        Assert.EndsWith("1-dfd30d145ea983650ac996f654a54ee8ee2a00389fffaf3a9b2ee76aec6742ed.jpg", pages[0].ImageUrl.ToString());
    }

    [Fact]
    public async Task Other_languages_use_their_dex_language_code()
    {
        var server = new FakeMangaDexServer((path, query) => (path, query) switch
        {
            ("/chapter", _) => FakeMangaDexServer.Json("latest-en.json"),
            ("/manga", var q) when q.Contains("ids%5B%5D=") => FakeMangaDexServer.Json("latest-mangas.json"),
            _ => throw new InvalidOperationException($"unexpected {path}?{query}"),
        });
        var source = FakeMangaDexServer.Source(server, new MangaDexOptions { Languages = ["pt-BR"] });

        Assert.Equal("pt-BR", source.Lang);
        await source.GetLatestAsync(1, TestContext.Current.CancellationToken);

        Assert.Contains("translatedLanguage%5B%5D=pt-br", server.Requests[0]);
    }
}
