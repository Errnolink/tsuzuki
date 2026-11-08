using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class LibraryChangeTests : IDisposable
{
    private const long MangaDex = 2499283573021220255;
    private const long MergedSource = 6969;

    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;
    private readonly List<LibraryChangedEventArgs> _events = new();

    public LibraryChangeTests()
    {
        (_db, _store) = TestLib.NewLibrary();
        _store.Changed += (_, e) =>
        {
            lock (_events) _events.Add(e);
        };
    }

    public void Dispose() => _db.Dispose();

    private List<LibraryChangedEventArgs> Events()
    {
        lock (_events) return _events.ToList();
    }

    private void ClearEvents()
    {
        lock (_events) _events.Clear();
    }

    private async Task<long> AddManga(string url, params string[] chapterUrls)
    {
        var id = await _store.UpsertFromSourceAsync(MangaDex, new SManga(url, url));
        await _store.SyncChaptersFromSourceAsync(id, Feed(chapterUrls));
        return id;
    }

    private static SChapter[] Feed(params string[] urls) =>
        urls.Select((url, i) => new SChapter(url, url) { ChapterNumber = i + 1, DateUpload = 1000 + i }).ToArray();

    [Fact]
    public async Task RefreshThatFindsNothingNewRaisesNoChange()
    {
        var id = await AddManga("/manga/a", "/chapter/1", "/chapter/2");
        ClearEvents();

        var result = await _store.SyncChaptersFromSourceAsync(id, Feed("/chapter/1", "/chapter/2"));

        Assert.Equal((0, 0, 0), (result.Added, result.Updated, result.Removed));
        Assert.Empty(Events());
    }

    [Fact]
    public async Task RefreshWithNewChaptersRaisesSourceChange()
    {
        var id = await AddManga("/manga/a", "/chapter/1");
        ClearEvents();

        await _store.SyncChaptersFromSourceAsync(id, Feed("/chapter/1", "/chapter/2"));

        var change = Assert.Single(Events());
        Assert.Equal(ChangeOrigin.Source, change.Origin);
        Assert.Equal(new[] { id }, change.MangaIds);
    }

    [Fact]
    public async Task BatchedRefreshRaisesOneChangeWithEveryChangedTitle()
    {
        var a = await AddManga("/manga/a", "/chapter/a1");
        var b = await AddManga("/manga/b", "/chapter/b1");
        var c = await AddManga("/manga/c", "/chapter/c1");
        var quiet = await AddManga("/manga/quiet", "/chapter/q1");
        ClearEvents();

        using (_store.BeginBatch())
        {
            await Task.WhenAll(
                _store.SyncChaptersFromSourceAsync(a, Feed("/chapter/a1", "/chapter/a2")),
                _store.SyncChaptersFromSourceAsync(b, Feed("/chapter/b1", "/chapter/b2")),
                _store.SyncChaptersFromSourceAsync(c, Feed("/chapter/c1", "/chapter/c2")),
                _store.SyncChaptersFromSourceAsync(quiet, Feed("/chapter/q1")));
            Assert.Empty(Events());
        }

        var change = Assert.Single(Events());
        Assert.Equal(ChangeOrigin.Source, change.Origin);
        Assert.Equal(new[] { a, b, c }.Order(), change.MangaIds.Order());
    }

    [Fact]
    public async Task LongBatchedRefreshReportsProgressBeforeItEnds()
    {
        var a = await AddManga("/manga/a", "/chapter/a1");
        ClearEvents();

        using (_store.BeginBatch())
        {
            await _store.SyncChaptersFromSourceAsync(a, Feed("/chapter/a1", "/chapter/a2"));
            var deadline = DateTime.UtcNow.AddSeconds(10);
            while (Events().Count == 0 && DateTime.UtcNow < deadline)
            {
                await Task.Delay(50);
            }
            var change = Assert.Single(Events());
            Assert.Equal(new[] { a }, change.MangaIds);
        }

        Assert.Single(Events());
    }

    [Fact]
    public async Task LocalChangesAreNotHeldBackByARefreshBatch()
    {
        var a = await AddManga("/manga/a", "/chapter/a1");
        ClearEvents();

        using (_store.BeginBatch())
        {
            await _store.SetFavoriteAsync(a, true);
            var change = Assert.Single(Events());
            Assert.Equal(ChangeOrigin.Local, change.Origin);
        }
    }

    [Fact]
    public async Task SourceDetailsRaiseChangeOnlyWhenTheyDiffer()
    {
        var id = await _store.UpsertFromSourceAsync(MangaDex, new SManga("/manga/a", "Title") { Description = "d" });
        ClearEvents();

        await _store.UpsertFromSourceAsync(MangaDex, new SManga("/manga/a", "Title") { Description = "d" });
        Assert.Empty(Events());

        await _store.UpsertFromSourceAsync(MangaDex, new SManga("/manga/a", "New title") { Description = "d" });
        var change = Assert.Single(Events());
        Assert.Equal(ChangeOrigin.Source, change.Origin);
        Assert.Equal(new[] { id }, change.MangaIds);
    }

    [Fact]
    public async Task RefreshMissingAReadChapterKeepsItAndItsProgress()
    {
        var id = await AddManga("/manga/a", "/chapter/1", "/chapter/2", "/chapter/3", "/chapter/4");
        await _store.SetFavoriteAsync(id, true);
        var chapters = await _store.GetChaptersAsync(id);
        var read = chapters.Single(c => c.Url == "/chapter/1");
        var started = chapters.Single(c => c.Url == "/chapter/2");
        await _store.SetChapterProgressAsync(read.ChapterId, 17, read: true);
        await _store.SetChapterProgressAsync(started.ChapterId, 6, read: false);

        var result = await _store.SyncChaptersFromSourceAsync(id, Feed("/chapter/4"));

        Assert.Equal(1, result.Removed);
        var after = await _store.GetChaptersAsync(id);
        Assert.Equal(new[] { "/chapter/1", "/chapter/2", "/chapter/4" }, after.Select(c => c.Url).Order(StringComparer.Ordinal));
        var keptRead = after.Single(c => c.Url == "/chapter/1");
        Assert.True(keptRead.Read);
        Assert.Equal(17, keptRead.LastPageRead);
        var keptStarted = after.Single(c => c.Url == "/chapter/2");
        Assert.False(keptStarted.Read);
        Assert.Equal(6, keptStarted.LastPageRead);
        Assert.Contains(await _store.GetHistoryAsync(10), h => h.ChapterId == read.ChapterId);
    }

    [Fact]
    public async Task ChangeHandlersCanWriteToTheLibrary()
    {
        var id = await AddManga("/manga/a", "/chapter/1");
        var wrote = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        _store.Changed += (_, e) =>
        {
            if (e.Origin != ChangeOrigin.Source) return;
            wrote.TrySetResult(_store.SetFavoriteAsync(id, true).Wait(TimeSpan.FromSeconds(5)));
        };

        var refresh = _store.SyncChaptersFromSourceAsync(id, Feed("/chapter/1", "/chapter/2"));

        Assert.True(await wrote.Task.WaitAsync(TimeSpan.FromSeconds(20)));
        await refresh;
        Assert.True((await _store.GetMangaAsync(id))!.Favorite);
    }

    [Fact]
    public async Task HasFavouritesReflectsTheLibrary()
    {
        var id = await AddManga("/manga/a", "/chapter/1");
        Assert.False(await _store.HasFavouritesAsync());

        await _store.SetFavoriteAsync(id, true);
        Assert.True(await _store.HasFavouritesAsync());

        await _store.SetFavoriteAsync(id, false);
        Assert.False(await _store.HasFavouritesAsync());
    }

    [Fact]
    public async Task HistoryShowsNewestReadsOfLibraryTitlesAndMergedChildren()
    {
        var favourite = await AddManga("/manga/fav", "/f/1", "/f/2", "/f/3");
        await _store.SetFavoriteAsync(favourite, true);
        var stranger = await AddManga("/manga/stranger", "/s/1", "/s/2");
        var child = await AddManga("/manga/child", "/c/1", "/c/2");
        var favouriteChild = await AddManga("/manga/favchild", "/fc/1");
        await _store.SetFavoriteAsync(favouriteChild, true);
        var parent = await _store.UpsertFromSourceAsync(MergedSource, new SManga("/merge/p", "Merged"));
        await _store.SetFavoriteAsync(parent, true);
        await Link(parent, MangaDex, "/manga/child");
        await Link(parent, MangaDex, "/manga/favchild");
        var droppedParent = await _store.UpsertFromSourceAsync(MergedSource, new SManga("/merge/dropped", "Dropped"));
        var orphan = await AddManga("/manga/orphan", "/o/1");
        await Link(droppedParent, MangaDex, "/manga/orphan");

        var stamps = new Dictionary<(long Manga, string Url), long>
        {
            [(favourite, "/f/1")] = 5_000,
            [(favourite, "/f/2")] = 9_000,
            [(favourite, "/f/3")] = 7_000,
            [(stranger, "/s/1")] = 9_500,
            [(stranger, "/s/2")] = 8_500,
            [(child, "/c/1")] = 8_000,
            [(child, "/c/2")] = 7_000,
            [(favouriteChild, "/fc/1")] = 6_000,
            [(orphan, "/o/1")] = 9_900,
        };
        foreach (var ((manga, url), stamp) in stamps)
        {
            await _db.WriteAsync(db => db.ExecuteNonQuery(
                "UPDATE chapter SET last_read_at = $t, last_page_read = 3 WHERE manga_id = $m AND url = $u",
                ("$t", stamp), ("$m", manga), ("$u", url)));
        }
        var ids = new Dictionary<string, long>();
        foreach (var manga in new[] { favourite, child, favouriteChild })
        {
            foreach (var chapter in await _store.GetChaptersAsync(manga))
            {
                ids[chapter.Url] = chapter.ChapterId;
            }
        }
        var tiedFirst = ids["/f/3"] > ids["/c/2"] ? "/f/3" : "/c/2";
        var tiedSecond = tiedFirst == "/f/3" ? "/c/2" : "/f/3";

        var all = await _store.GetHistoryAsync(200);
        Assert.Equal(new[] { "/f/2", "/c/1", tiedFirst, tiedSecond, "/fc/1", "/f/1" },
            all.Select(h => Url(ids, h.ChapterId)));
        var top = all[1];
        Assert.Equal(child, top.MangaId);
        Assert.Equal("/manga/child", top.Title);
        Assert.Equal(3, top.LastPageRead);
        Assert.Equal(8_000, top.LastReadAt);

        var newest = await _store.GetHistoryAsync(3);
        Assert.Equal(all.Take(3).Select(h => h.ChapterId), newest.Select(h => h.ChapterId));
    }

    private static string Url(Dictionary<string, long> ids, long chapterId) => ids.Single(p => p.Value == chapterId).Key;

    private Task<int> Link(long parentId, long childSource, string childUrl) =>
        _db.WriteAsync(db => db.ExecuteNonQuery(
            "INSERT OR IGNORE INTO merged_ref(parent_id, child_source, child_url, is_info, priority) VALUES ($p, $s, $u, 0, 0)",
            ("$p", parentId), ("$s", childSource), ("$u", childUrl)));
}
