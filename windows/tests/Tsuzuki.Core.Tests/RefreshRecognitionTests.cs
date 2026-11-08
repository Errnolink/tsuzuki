using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests;

public sealed class RefreshRecognitionTests
{
    private const long SourceId = 2131019126180322627;

    [Fact]
    public async Task Refreshing_a_source_without_numbers_keeps_the_phone_numbers_and_versions()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var ct = TestContext.Current.CancellationToken;
            var mangaId = await store.UpsertFromSourceAsync(SourceId, new SManga("/series/01J76XYA1TQYKV6W4BCMR2WD5M/Wind-Breaker", "Wind Breaker"), ct);
            await store.SyncChaptersFromSourceAsync(mangaId,
            [
                new SChapter("/chapters/b", "Chapter 74") { ChapterNumber = 74, Scanlator = "Unknown" },
                new SChapter("/chapters/a", "Chapter 73.5") { ChapterNumber = 73.5, Scanlator = "Unknown" },
            ], ct);
            var before = await store.GetChaptersAsync(mangaId, ct);
            var source = new NumberlessSource(
            [
                new SChapter("/chapters/b", "Chapter 74") { Scanlator = "Unknown" },
                new SChapter("/chapters/a", "Chapter 73.5") { Scanlator = "Unknown" },
            ]);
            var refresher = new LibraryRefresher(store, new SourceRegistry([source], _ => null));

            var result = await refresher.RefreshAsync(mangaId, includeDetails: false, ct);

            Assert.Equal(new ChapterSyncResult(0, 0, 0), result);
            var after = await store.GetChaptersAsync(mangaId, ct);
            Assert.Equal(73.5, after.Single(c => c.Url == "/chapters/a").ChapterNumber);
            Assert.Equal(74, after.Single(c => c.Url == "/chapters/b").ChapterNumber);
            Assert.Equal(before.Select(c => (c.Url, c.ChapterNumber)).OrderBy(c => c.Url), after.Select(c => (c.Url, c.ChapterNumber)).OrderBy(c => c.Url));
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task New_chapters_from_a_source_without_numbers_get_recognised_numbers()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var ct = TestContext.Current.CancellationToken;
            var mangaId = await store.UpsertFromSourceAsync(SourceId, new SManga("/series/x/Solo", "Solo Leveling"), ct);
            var source = new NumberlessSource(
            [
                new SChapter("/chapters/2", "Solo Leveling - Chapter 2.5"),
                new SChapter("/chapters/1", "Chapter 1"),
            ]);
            var refresher = new LibraryRefresher(store, new SourceRegistry([source], _ => null));

            await refresher.RefreshAsync(mangaId, includeDetails: false, ct);

            var chapters = await store.GetChaptersAsync(mangaId, ct);
            var half = chapters.Single(c => c.Url == "/chapters/2");
            Assert.Equal(2.5, half.ChapterNumber);
            Assert.Equal("Chapter 2.5", half.Name);
            Assert.Equal(1, chapters.Single(c => c.Url == "/chapters/1").ChapterNumber);
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task Refreshing_keeps_phone_synced_decimal_chapters_unchanged()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var ct = TestContext.Current.CancellationToken;
            var mangaId = await store.UpsertFromSourceAsync(SourceId, new SManga("/series/x/Solo", "Solo Leveling"), ct);
            await store.SyncChaptersFromSourceAsync(mangaId,
            [
                new SChapter("/chapters/101", "Chapter 10.1") { ChapterNumber = (double)10.1f, Scanlator = "Unknown" },
            ], ct);
            var source = new NumberlessSource([new SChapter("/chapters/101", "Chapter 10.1") { Scanlator = "Unknown" }]);
            var refresher = new LibraryRefresher(store, new SourceRegistry([source], _ => null));

            var first = await refresher.RefreshAsync(mangaId, includeDetails: false, ct);
            var second = await refresher.RefreshAsync(mangaId, includeDetails: false, ct);

            Assert.Equal(new ChapterSyncResult(0, 0, 0), first);
            Assert.Equal(new ChapterSyncResult(0, 0, 0), second);
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task A_source_that_returns_no_chapters_does_not_wipe_the_library_copy()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            var ct = TestContext.Current.CancellationToken;
            var mangaId = await store.UpsertFromSourceAsync(SourceId, new SManga("/series/x/Solo", "Solo Leveling"), ct);
            await store.SyncChaptersFromSourceAsync(mangaId,
            [
                new SChapter("/chapters/1", "Chapter 1") { ChapterNumber = 1 },
                new SChapter("/chapters/2", "Chapter 2") { ChapterNumber = 2 },
            ], ct);
            var refresher = new LibraryRefresher(store, new SourceRegistry([new NumberlessSource([])], _ => null));

            await Assert.ThrowsAsync<InvalidOperationException>(() => refresher.RefreshAsync(mangaId, includeDetails: false, ct));

            Assert.Equal(2, (await store.GetChaptersAsync(mangaId, ct)).Count);
        }
        finally
        {
            db.Dispose();
        }
    }

    private sealed class NumberlessSource(SChapter[] chapters) : ISource
    {
        public long Id => SourceId;
        public string Name => "Weeb Central";
        public string Lang => "en";
        public bool SupportsLatest => true;

        public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) => throw new NotSupportedException();
        public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();

        public Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<SChapter>>(chapters);

        public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default) =>
            throw new NotSupportedException();

        public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);

        public Uri WebUrl(string mangaUrl) => throw new NotSupportedException();
    }
}
