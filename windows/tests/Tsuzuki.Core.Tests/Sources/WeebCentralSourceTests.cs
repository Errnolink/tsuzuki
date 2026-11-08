using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.WeebCentral;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class WeebCentralSourceTests
{
    private const long WeebCentralId = 2131019126180322627;
    private const string WindBreaker = "/series/01J76XYA1TQYKV6W4BCMR2WD5M/Wind-Breaker";

    private static (WeebCentralSource Source, RecordingHttp Http) Source(string fixture)
    {
        var http = new RecordingHttp(_ => RecordingHttp.Html(fixture));
        return (new WeebCentralSource(new HttpClient(http)), http);
    }

    [Fact]
    public async Task Search_finds_the_phone_favourite_under_its_stored_url()
    {
        var (source, http) = Source("weebcentral-search.html");

        var page = await source.SearchAsync(SearchQuery.ForText("Wind Breaker: (2013)"), 1, TestContext.Current.CancellationToken);

        var request = Assert.Single(http.Requests).RequestUri!;
        Assert.Equal("/search/data", request.AbsolutePath);
        Assert.Contains("text=Wind%20Breaker%20%20%202013&", request.Query);
        Assert.Contains("sort=Best%20Match", request.Query);
        Assert.Contains("offset=0", request.Query);
        Assert.Equal(WindBreaker, page.Mangas[0].Url);
        Assert.Equal("Wind Breaker", page.Mangas[0].Title);
        Assert.Equal("https://temp.compsci88.com/cover/normal/01J76XYA1TQYKV6W4BCMR2WD5M.webp", page.Mangas[0].ThumbnailUrl);
        Assert.Equal("/series/01J76XYF5ZDZX41X5S64WQHFXZ/Wind-Breaker-NII-Satoru", page.Mangas[1].Url);
        Assert.False(page.HasNextPage);
    }

    [Fact]
    public async Task Popular_pages_by_thirty_two_and_offers_the_next_page()
    {
        var (source, http) = Source("weebcentral-popular.html");

        var page = await source.GetPopularAsync(2, TestContext.Current.CancellationToken);

        var query = Assert.Single(http.Requests).RequestUri!.Query;
        Assert.Contains("sort=Popularity", query);
        Assert.Contains("offset=32", query);
        Assert.Equal(32, page.Mangas.Count);
        Assert.True(page.HasNextPage);
        Assert.All(page.Mangas, m => Assert.Matches("^/series/[0-9A-Z]{26}/[^/]+$", m.Url));
        Assert.All(page.Mangas, m => Assert.False(string.IsNullOrWhiteSpace(m.Title)));
    }

    [Fact]
    public async Task Latest_asks_for_latest_updates()
    {
        var (source, http) = Source("weebcentral-popular.html");

        await source.GetLatestAsync(1, TestContext.Current.CancellationToken);

        Assert.Contains("sort=Latest%20Updates", Assert.Single(http.Requests).RequestUri!.Query);
    }

    [Fact]
    public async Task Default_search_sends_the_extension_default_filters()
    {
        var (source, http) = Source("weebcentral-search.html");

        await source.SearchAsync(SearchQuery.ForText("wind breaker"), 1, TestContext.Current.CancellationToken);

        Assert.Equal(
            "?text=wind%20breaker&sort=Best%20Match&order=Descending&official=Any&anime=Any&adult=Any&limit=32&offset=0&display_mode=Full%20Display",
            Assert.Single(http.Requests).RequestUri!.Query);
    }

    [Fact]
    public async Task Search_carries_the_chosen_filters_the_way_weeb_central_expects()
    {
        var (source, http) = Source("weebcentral-search.html");
        var query = new SearchQuery("", new Dictionary<string, IReadOnlyList<string>>
        {
            ["sort"] = ["Subscribers"],
            ["order"] = ["Ascending"],
            ["official"] = ["True"],
            ["anime"] = ["False"],
            ["adult"] = ["False"],
            ["author"] = ["NII Satoru"],
            ["included_status"] = ["Ongoing", "Hiatus"],
            ["included_type"] = ["Manhwa"],
            ["included_tag"] = ["Martial Arts", "Action"],
            ["included_tag!"] = ["Sci-fi"],
        });

        await source.SearchAsync(query, 3, TestContext.Current.CancellationToken);

        Assert.Equal(
            "?text=&sort=Subscribers&order=Ascending&official=True&anime=False&adult=False&author=NII%20Satoru"
            + "&included_status=Ongoing&included_status=Hiatus&included_type=Manhwa"
            + "&included_tag=Action&included_tag=Martial%20Arts&excluded_tag=Sci-fi"
            + "&limit=32&offset=64&display_mode=Full%20Display",
            Assert.Single(http.Requests).RequestUri!.Query);
    }

    [Fact]
    public async Task Filters_list_every_weeb_central_option()
    {
        var (source, _) = Source("weebcentral-search.html");

        var filters = await source.GetFiltersAsync(TestContext.Current.CancellationToken);

        Assert.Equal(["sort", "order", "official", "anime", "adult", "author", "included_status", "included_type", "included_tag"], filters.Select(f => f.Key));
        Assert.Equal(38, filters.Single(f => f.Kind == FilterKind.TriState).Options.Count);
        Assert.Equal(["Best Match"], filters[0].Default);
    }

    [Fact]
    public void Web_url_is_the_series_page_on_the_site()
    {
        var (source, _) = Source("weebcentral-search.html");

        Assert.Equal(new Uri("https://weebcentral.com" + WindBreaker), source.WebUrl(WindBreaker));
    }

    [Fact]
    public async Task Details_match_what_the_phone_stored_for_the_favourite()
    {
        var (source, http) = Source("weebcentral-details.html");

        var manga = await source.GetMangaDetailsAsync(WindBreaker, TestContext.Current.CancellationToken);

        Assert.Equal("https://weebcentral.com" + WindBreaker, Assert.Single(http.Requests).RequestUri!.AbsoluteUri);
        Assert.Equal(WindBreaker, manga.Url);
        Assert.Equal("Wind Breaker", manga.Title);
        Assert.Equal("Jo YongSeuk", manga.Author);
        Assert.Equal(["Action", "Comedy", "Drama", "School Life", "Shounen", "Sports", "Manhwa"], manga.Genres);
        Assert.Equal(MangaStatus.Cancelled, manga.Status);
        Assert.Equal("https://temp.compsci88.com/cover/normal/01J76XYA1TQYKV6W4BCMR2WD5M.webp", manga.ThumbnailUrl);
        Assert.Equal(
            "Jay's the perfect student. He's got straight As and he's the student body president. But after being coerced into joining the school's biking team, the Hummingbird Crew, he discovers a whole new world outside of studying. What new adventures will he face outside the comfort of his textbooks?"
            + "\n\nTracker(s):\n- [AniList](https://anilist.co/manga/86099)\n- [MangaUpdates](https://www.mangaupdates.com/series/8jn71ik)",
            manga.Description);
    }

    [Fact]
    public async Task Chapters_keep_the_phone_urls_numbers_dates_and_scanlators()
    {
        var (source, http) = Source("weebcentral-chapters.html");

        var chapters = await source.GetChapterListAsync(WindBreaker, TestContext.Current.CancellationToken);

        Assert.Equal("https://weebcentral.com/series/01J76XYA1TQYKV6W4BCMR2WD5M/full-chapter-list", Assert.Single(http.Requests).RequestUri!.AbsoluteUri);
        Assert.Equal(556, chapters.Count);
        var newest = chapters[0];
        Assert.Equal("/chapters/01K0NNB0VE6NYE0D7012KG5TWC", newest.Url);
        Assert.Equal("S4 - Episode 178", newest.Name);
        Assert.Equal(556, newest.ChapterNumber);
        Assert.Equal(1753073681336, newest.DateUpload);
        Assert.Equal("Unknown", newest.Scanlator);
        Assert.Equal(555, chapters[1].ChapterNumber);
        Assert.Equal("/chapters/01J7W74HK06HFW526BJGN5DHEX", chapters[^1].Url);
        Assert.Equal("Official", chapters[^1].Scanlator);
        Assert.All(chapters, c => Assert.Matches("^/chapters/[0-9A-Z]{26}$", c.Url));
    }

    [Fact]
    public async Task Pages_come_from_the_long_strip_image_list()
    {
        var (source, http) = Source("weebcentral-images.html");

        var pages = await source.GetPageListAsync("/chapters/01K0NNB0VE6NYE0D7012KG5TWC", ct: TestContext.Current.CancellationToken);

        Assert.Equal(
            "https://weebcentral.com/chapters/01K0NNB0VE6NYE0D7012KG5TWC/images?is_prev=False&reading_style=long_strip",
            Assert.Single(http.Requests).RequestUri!.AbsoluteUri);
        Assert.Equal(19, pages.Count);
        Assert.Equal("https://scans.lastation.us/manga/Wind-Breaker/0178-001.png", pages[0].ImageUrl.AbsoluteUri);
        Assert.Equal(18, pages[18].Index);
        Assert.Equal(pages.Count, pages.Select(p => p.CacheKey).Distinct().Count());
    }

    [Fact]
    public void Image_requests_carry_the_site_referer()
    {
        var source = new WeebCentralSource(new HttpClient());

        using var request = source.CreateImageRequest(new Uri("https://scans.lastation.us/manga/Wind-Breaker/0178-001.png"));

        Assert.Equal(new Uri("https://weebcentral.com/"), request.Headers.Referrer);
    }

    [Fact]
    public void Registers_under_the_android_id_so_existing_favourites_read_here()
    {
        var registry = new SourceRegistry([new WeebCentralSource(new HttpClient())], _ => null);

        Assert.True(registry.IsReadable(WeebCentralId));
        Assert.Equal("Weeb Central", registry.DisplayName(WeebCentralId));
    }

    [Fact]
    public async Task Existing_favourites_become_readable_without_migration()
    {
        var registry = new SourceRegistry([new WeebCentralSource(new HttpClient())], _ => null);
        var (db, _) = TestLib.NewLibrary();
        try
        {
            var store = new SqliteLibraryStore(db, registry.IsReadable);
            var id = await store.UpsertFromSourceAsync(WeebCentralId, new SManga(WindBreaker, "Wind Breaker"), TestContext.Current.CancellationToken);
            await store.SetFavoriteAsync(id, true, TestContext.Current.CancellationToken);

            var library = await store.GetLibraryAsync(TestContext.Current.CancellationToken);

            Assert.True(Assert.Single(library).Readable);
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task Rate_limits_the_site_to_one_request_every_two_seconds()
    {
        var (source, http) = Source("weebcentral-images.html");
        var started = Environment.TickCount64;

        await source.GetPageListAsync("/chapters/01K0NNB0VE6NYE0D7012KG5TWC", ct: TestContext.Current.CancellationToken);
        await source.GetPageListAsync("/chapters/01K0NNB0VEEEGG6BBH5MPTCVTX", ct: TestContext.Current.CancellationToken);

        Assert.Equal(2, http.Requests.Count);
        Assert.True(Environment.TickCount64 - started >= 1900, "the second request went out before the site's two second spacing");
    }
}
