using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests;

public sealed class ListingCacheTests
{
    private static string NewRoot() => Path.Combine(TestLib.TempRoot, "listings-" + Guid.NewGuid().ToString("N"));

    [Fact]
    public async Task Coming_back_to_a_browse_page_shows_the_saved_list_without_asking_the_source_again()
    {
        var ct = TestContext.Current.CancellationToken;
        var clock = new Clock();
        var source = new CountingSource();
        var root = NewRoot();

        await new ListingCache(root, clock).GetAsync(source, ListingKind.Popular, null, 1, refresh: false, ct);
        clock.Advance(TimeSpan.FromMinutes(20));
        var again = await new ListingCache(root, clock).GetAsync(source, ListingKind.Popular, null, 1, refresh: false, ct);

        Assert.Equal(1, source.Calls);
        Assert.Equal("Popular 1", again.Mangas[0].Title);
    }

    [Fact]
    public async Task An_old_list_or_an_explicit_reload_asks_the_source_again()
    {
        var ct = TestContext.Current.CancellationToken;
        var clock = new Clock();
        var source = new CountingSource();
        var cache = new ListingCache(NewRoot(), clock);

        await cache.GetAsync(source, ListingKind.Popular, null, 1, refresh: false, ct);
        await cache.GetAsync(source, ListingKind.Popular, null, 1, refresh: true, ct);
        clock.Advance(ListingCache.BrowseLifetime + TimeSpan.FromMinutes(1));
        await cache.GetAsync(source, ListingKind.Popular, null, 1, refresh: false, ct);

        Assert.Equal(3, source.Calls);
    }

    [Fact]
    public async Task Different_searches_are_kept_apart()
    {
        var ct = TestContext.Current.CancellationToken;
        var source = new CountingSource();
        var cache = new ListingCache(NewRoot(), new Clock());

        var one = await cache.GetAsync(source, ListingKind.Search, new SearchQuery("one", new Dictionary<string, IReadOnlyList<string>>()), 1, false, ct);
        var two = await cache.GetAsync(source, ListingKind.Search, new SearchQuery("two", new Dictionary<string, IReadOnlyList<string>>()), 1, false, ct);

        Assert.Equal("one 1", one.Mangas[0].Title);
        Assert.Equal("two 1", two.Mangas[0].Title);
    }

    [Fact]
    public async Task When_the_source_is_unreachable_the_last_list_is_shown()
    {
        var ct = TestContext.Current.CancellationToken;
        var clock = new Clock();
        var source = new CountingSource();
        var cache = new ListingCache(NewRoot(), clock);
        await cache.GetAsync(source, ListingKind.Latest, null, 1, false, ct);

        clock.Advance(TimeSpan.FromHours(2));
        source.Offline = true;
        var page = await cache.GetAsync(source, ListingKind.Latest, null, 1, false, ct);

        Assert.Equal("Latest 1", page.Mangas[0].Title);
    }

    private sealed class Clock : TimeProvider
    {
        private DateTimeOffset now = new(2025, 11, 6, 12, 0, 0, TimeSpan.Zero);

        public override DateTimeOffset GetUtcNow() => now;

        public void Advance(TimeSpan by) => now += by;
    }

    private sealed class CountingSource : ISource
    {
        public int Calls { get; private set; }
        public bool Offline { get; set; }
        public long Id => 42;
        public string Name => "Counting";
        public string Lang => "en";
        public bool SupportsLatest => true;

        private Task<MangasPage> Page(string label, int page)
        {
            Calls++;
            if (Offline)
            {
                throw new HttpRequestException("offline");
            }
            return Task.FromResult(new MangasPage([new SManga($"/{label}/{page}", $"{label} {page}")], true));
        }

        public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) => Task.FromResult<IReadOnlyList<FilterSpec>>([]);
        public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) => Page("Popular", page);
        public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => Page("Latest", page);
        public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) => Page(query.Text, page);
        public Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default) => throw new NotSupportedException();
        public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);
        public Uri WebUrl(string mangaUrl) => new("https://example.org" + mangaUrl);
    }
}
