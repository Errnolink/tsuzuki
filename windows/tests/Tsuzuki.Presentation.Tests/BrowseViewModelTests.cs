using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class BrowseViewModelTests
{
    private static ListingCache Cache() => new(Path.Combine(Path.GetTempPath(), "tsuzuki-presentation-tests", Guid.NewGuid().ToString("N")));

    [Fact]
    public async Task A_page_from_a_source_left_behind_never_lands_in_the_new_list()
    {
        var slow = new PagedSource(1, "Slow") { Gate = new TaskCompletionSource() };
        var fast = new PagedSource(2, "Fast");
        var model = new BrowseViewModel(Cache());

        model.Reset(new BrowseRequest(slow, ListingKind.Popular, null), false);
        var stale = model.LoadMoreAsync();
        model.Reset(new BrowseRequest(fast, ListingKind.Popular, null), false);
        var fresh = await model.LoadMoreAsync();
        slow.Gate.SetResult();

        Assert.Equal(BrowsePageOutcome.Stale, (await stale).Outcome);
        Assert.All(fresh.Added, m => Assert.StartsWith("Fast", m.Title));
    }

    [Fact]
    public async Task Titles_a_source_repeats_on_the_next_page_are_shown_once()
    {
        var source = new PagedSource(3, "Repeat") { Overlap = true };
        var model = new BrowseViewModel(Cache());
        model.Reset(new BrowseRequest(source, ListingKind.Popular, null), false);

        var first = await model.LoadMoreAsync();
        var second = await model.LoadMoreAsync();

        Assert.Equal(2, first.Added.Count);
        Assert.Single(second.Added);
        Assert.Equal(3, model.Count);
    }

    [Fact]
    public async Task A_failed_next_page_stops_paging_until_retried_but_keeps_what_loaded()
    {
        var source = new PagedSource(4, "Flaky") { FailFromPage = 2 };
        var model = new BrowseViewModel(Cache());
        model.Reset(new BrowseRequest(source, ListingKind.Popular, null), false);

        await model.LoadMoreAsync();
        var failed = await model.LoadMoreAsync();

        Assert.Equal(BrowsePageOutcome.Failed, failed.Outcome);
        Assert.False(model.HasNext);
        Assert.Equal(BrowsePageOutcome.Busy, (await model.LoadMoreAsync()).Outcome);
        model.AllowRetry();
        Assert.True(model.HasNext);
    }

    private sealed class PagedSource(long id, string name) : ISource
    {
        public TaskCompletionSource? Gate { get; init; }
        public bool Overlap { get; init; }
        public int FailFromPage { get; init; } = int.MaxValue;
        public long Id => id;
        public string Name => name;
        public string Lang => "en";
        public bool SupportsLatest => false;

        public async Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default)
        {
            if (Gate is not null)
            {
                await Gate.Task;
            }
            if (page >= FailFromPage)
            {
                throw new HttpRequestException("offline");
            }
            var start = Overlap ? page : page * 2;
            return new MangasPage([new SManga($"/{start}", $"{name} {start}"), new SManga($"/{start + 1}", $"{name} {start + 1}")], true);
        }

        public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) => Task.FromResult<IReadOnlyList<FilterSpec>>([]);
        public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => GetPopularAsync(page, ct);
        public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) => GetPopularAsync(page, ct);
        public Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default) => throw new NotSupportedException();
        public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);
        public Uri WebUrl(string mangaUrl) => new("https://example.org" + mangaUrl);
    }
}
