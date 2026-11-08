using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class LibraryBehaviourTests
{
    private const long ChildSource = 111;

    private static ChapterRow Row(string url, double number, string? scanlator = null,
        bool read = false, int page = 0, long lastReadAt = 0, int sourceOrder = 0,
        string? externalUrl = null, long dateUpload = 0)
    {
        var id = Interlocked.Increment(ref _ids);
        return new ChapterRow(id, 1, ChildSource, url, "Ch " + number, scanlator, number,
            dateUpload, sourceOrder, read, false, page, null, lastReadAt, externalUrl, Readable: true);
    }

    private static int _ids;

    [Fact]
    public void GroupPrefersInProgressThenLastReadScanlatorThenMostChapters()
    {
        var rows = new[]
        {
            Row("/w/1", 1, "West"),
            Row("/w/2", 2, "West"),
            Row("/d/4", 4, "Doe", read: true, lastReadAt: 900),
            Row("/h/5", 5, "Hachi", page: 4, sourceOrder: 1),
            Row("/d/5", 5, "Doe", sourceOrder: 2),
            Row("/h/6", 6, "Hachi"),
            Row("/d/6", 6, "Doe"),
            Row("/w/7", 7, "West"),
            Row("/e/7", 7, "East"),
        };

        var groups = ChapterGrouping.Group(rows);

        Assert.Equal(new[] { 7d, 6d, 5d, 4d, 2d, 1d }, groups.Select(g => g.Number));
        var five = groups.First(g => g.Number == 5);
        Assert.Equal("Hachi", five.Primary.Scanlator);
        Assert.Equal("Doe", Assert.Single(five.Alternates).Scanlator);
        Assert.True(five.InProgress);
        Assert.Equal("Doe", groups.First(g => g.Number == 6).Primary.Scanlator);
        Assert.Equal("West", groups.First(g => g.Number == 7).Primary.Scanlator);
    }

    [Fact]
    public void NextToReadResumesTheUnfinishedChapter()
    {
        var groups = ChapterGrouping.Group(new[]
        {
            Row("/c/5", 5, "A", read: true, lastReadAt: 900),
            Row("/c/4", 4, "A", page: 3, lastReadAt: 1000),
            Row("/c/3", 3, "A"),
            Row("/c/2", 2, "A", read: true),
        });

        Assert.Equal(4, ChapterGrouping.NextToRead(groups)!.Number);
    }

    [Fact]
    public void NextToReadPicksTheNextUnreadAfterTheLastReadChapter()
    {
        var groups = ChapterGrouping.Group(new[]
        {
            Row("/c/6", 6, "A"),
            Row("/c/5", 5, "A", read: true, lastReadAt: 2000),
            Row("/c/4", 4, "A", read: true),
        });

        Assert.Equal(6, ChapterGrouping.NextToRead(groups)!.Number);
    }

    [Fact]
    public void NextToReadStartsAtTheFirstChapterWhenNothingWasRead()
    {
        var groups = ChapterGrouping.Group(new[]
        {
            Row("/c/2", 2, "A"),
            Row("/c/1", 1, "A"),
        });

        Assert.Equal(1, ChapterGrouping.NextToRead(groups)!.Number);
    }

    [Fact]
    public async Task RefreshAsyncOnMergedMangaRefreshesChildrenAndAggregatesCounts()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var source = new FakeSource(new[]
            {
                new SChapter("/c/1", "1") { ChapterNumber = 1, Scanlator = "A" },
                new SChapter("/c/2", "2") { ChapterNumber = 2, Scanlator = "A" },
            });
            var refresher = new LibraryRefresher(store, new SourceRegistry([source], _ => null));
            var parent = await store.UpsertFromSourceAsync(6969, new SManga("/manga/agg", "Aggregate"));
            var child = await store.UpsertFromSourceAsync(ChildSource, new SManga("/manga/child", "Child"));
            await store.SetFavoriteAsync(parent, true);
            await db.WriteAsync(connection => connection.ExecuteNonQuery(
                "INSERT OR IGNORE INTO merged_ref(parent_id, child_source, child_url, is_info, priority) VALUES ($p, $s, $u, 0, 0)",
                ("$p", parent), ("$s", ChildSource), ("$u", "/manga/child")));

            var first = await refresher.RefreshAsync(parent, includeDetails: false);
            Assert.Equal(new ChapterSyncResult(2, 0, 0), first);

            source.Chapters =
            [
                new SChapter("/c/2", "2") { ChapterNumber = 2, Scanlator = "A" },
                new SChapter("/c/3", "3") { ChapterNumber = 3, Scanlator = "A" },
            ];
            var second = await refresher.RefreshAsync(parent, includeDetails: false);
        Assert.Equal(new ChapterSyncResult(1, 0, 1), second);

            var childChapters = await store.GetChaptersAsync(child);
            Assert.Equal(2, childChapters.Count);
            Assert.Contains(childChapters, c => c.Url == "/c/3");
            Assert.DoesNotContain(childChapters, c => c.Url == "/c/1");
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task OneTitleFailingWithAnUnexpectedErrorDoesNotStopTheLibraryUpdate()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var source = new FakeSource([new SChapter("/c/1", "1") { ChapterNumber = 1 }]) { FailingUrl = "/manga/bad" };
            var refresher = new LibraryRefresher(store, new SourceRegistry([source], _ => null));
            var ids = new List<long>();
            foreach (var url in new[] { "/manga/bad", "/manga/a", "/manga/b", "/manga/c" })
            {
                var id = await store.UpsertFromSourceAsync(ChildSource, new SManga(url, url));
                await store.SetFavoriteAsync(id, true);
                ids.Add(id);
            }

            var result = await refresher.RefreshLibraryAsync(ct: TestContext.Current.CancellationToken);

            Assert.Equal(new UpdateRunResult(4, 3, 1), result);
            foreach (var id in ids.Skip(1)) Assert.Single(await store.GetChaptersAsync(id));
        }
        finally
        {
            db.Dispose();
        }
    }

    private sealed class FakeSource(SChapter[] chapters) : ISource
    {
        public SChapter[] Chapters = chapters;

        public long Id => ChildSource;
        public string Name => "Fake";
        public string Lang => "en";
        public bool SupportsLatest => false;

        public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) =>
            throw new NotSupportedException();

        public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default) =>
            mangaUrl == FailingUrl
                ? throw new FormatException("unparseable publish date")
                : Task.FromResult<IReadOnlyList<SChapter>>(Chapters);

        public string? FailingUrl;

        public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);

        public Uri WebUrl(string mangaUrl) => throw new NotSupportedException();
    }
}
