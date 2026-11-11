using Google.Protobuf;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class StorageBehaviourTests : IDisposable
{
    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;

    public StorageBehaviourTests()
    {
        (_db, _store) = TestLib.NewLibrary();
    }

    public void Dispose() => _db.Dispose();

    [Fact]
    public async Task StoreReadsLeaveTheCallingThread()
    {
        var done = new TaskCompletionSource();
        var callerThread = 0;
        var queryThread = 0;
        var caller = new Thread(() =>
        {
            callerThread = Environment.CurrentManagedThreadId;
            _db.ReadAsync<object?>(_ =>
            {
                queryThread = Environment.CurrentManagedThreadId;
                return null;
            }).Wait();
            done.SetResult();
        });
        caller.Start();
        Assert.True(done.Task.Wait(TimeSpan.FromSeconds(10)));
        Assert.NotEqual(callerThread, queryThread);
    }

    [Fact]
    public async Task SourceUpsertDoesNotTouchFavouriteVersionOrSeq()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        var before = await GetRow(mangaId);
        await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title v2")
        {
            Description = "new",
        });

        var after = await GetRow(mangaId);
        Assert.Equal(before.Long("version"), after.Long("version"));
        Assert.Equal(before.Long("seq"), after.Long("seq"));
        Assert.Equal(before.Text("origin"), after.Text("origin"));
        Assert.Equal(1, after.Long("favorite"));
        Assert.Equal("Title v2", after.Text("title"));
    }

    [Fact]
    public async Task FavouriteWriteBumpsVersionSeqAndOrigin()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        var row = await GetRow(mangaId);
        Assert.Equal(1, row.Long("version"));
        Assert.Equal(1, row.Long("seq"));
        Assert.Equal(await _db.GetMetaAsync("local_device_id"), row.Text("origin"));
        Assert.True(row.Long("last_modified_at") > 1_000_000_000);
        Assert.True(row.Long("date_added") > 1_000_000_000_000);
        var payload = PayloadOf(row);
        Assert.True(payload.Favorite);
        Assert.True(payload.HasFavoriteModifiedAt);
    }

    [Fact]
    public async Task ChapterSyncAddsUpdatesAndRemovesWithoutTouchingReadState()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        var first = await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1") { ChapterNumber = 1, DateUpload = 111 },
            new SChapter("/chapter/2", "2") { ChapterNumber = 2, DateUpload = 222 },
        });
        Assert.Equal((2, 0, 0), (first.Added, first.Updated, first.Removed));
        var chapters = await _store.GetChaptersAsync(mangaId);
        var chapter1 = chapters.First(c => c.Url == "/chapter/1");
        await _store.SetChapterProgressAsync(chapter1.ChapterId, 5, read: true);

        var second = await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1 updated") { ChapterNumber = 1, DateUpload = 111, PageCount = 18 },
            new SChapter("/chapter/3", "3") { ChapterNumber = 3, DateUpload = 333 },
        });
        Assert.Equal((1, 1, 1), (second.Added, second.Updated, second.Removed));

        var after = await _store.GetChaptersAsync(mangaId);
        Assert.Equal(2, after.Count);
        var kept = after.First(c => c.Url == "/chapter/1");
        Assert.True(kept.Read);
        Assert.Equal(5, kept.LastPageRead);
        Assert.Equal(18, kept.PageCount);
        Assert.Equal("1 updated", kept.Name);
    }

    [Fact]
    public async Task ReadingProgressUpdatesMangaHistory()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1") { ChapterNumber = 1 },
        });
        var chapter = (await _store.GetChaptersAsync(mangaId)).Single();
        var before = await GetRow(mangaId);

        await _store.SetChapterProgressAsync(chapter.ChapterId, 3, read: false);
        var after = await GetRow(mangaId);
        Assert.Equal(before.Long("version") + 1, after.Long("version"));
        var payload = PayloadOf(after);
        var history = Assert.Single(payload.History);
        Assert.Equal("/chapter/1", history.Url);
        Assert.True(history.LastRead > 1_000_000_000_000);

        var chapterRow = (await _store.GetChaptersAsync(mangaId)).Single();
        Assert.True(chapterRow.LastReadAt > 0);
        Assert.Equal(3, chapterRow.LastPageRead);
    }

    [Fact]
    public async Task UnreadCountsUseDistinctChapterNumbers()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/a", "a") { ChapterNumber = 1 },
            new SChapter("/b", "b") { ChapterNumber = 1 },
            new SChapter("/c", "c") { ChapterNumber = 2 },
            new SChapter("/d", "d") { ChapterNumber = -1 },
        });
        var entry = (await _store.GetLibraryAsync()).Single();
        Assert.Equal(3, entry.UnreadCount);
        Assert.Equal(3, entry.ChapterCount);

        var chapters = await _store.GetChaptersAsync(mangaId);
        await _store.SetChaptersReadAsync(chapters.Where(c => c.Url == "/b").Select(c => c.ChapterId).ToList(), true);
        entry = (await _store.GetLibraryAsync()).Single();
        Assert.Equal(2, entry.UnreadCount);
    }

    [Fact]
    public async Task MergedParentFoldsChildrenAndHidesThemFromLibrary()
    {
        const long mergedSource = 6969;
        var parentId = await _store.UpsertFromSourceAsync(mergedSource, new SManga("/merge/one", "Merged"));
        await _store.SetFavoriteAsync(parentId, true);
        var childId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Child"));
        await _store.SyncChaptersFromSourceAsync(childId, new[]
        {
            new SChapter("/child/1", "1") { ChapterNumber = 1 },
            new SChapter("/child/2", "2") { ChapterNumber = 2 },
        });
        SetMergedRefs(parentId, 2499283573021220255, "/manga/abc");
        var childRow = await GetRow(childId);
        RewritePayloadRefs(parentId, childRow.Long("source"), childRow.Text("url"));

        var library = await _store.GetLibraryAsync();
        var entry = Assert.Single(library);
        Assert.Equal(parentId, entry.MangaId);
        Assert.True(entry.IsMerged);
        Assert.Equal(2, entry.ChapterCount);
        Assert.Equal(2, entry.UnreadCount);
    }

    [Fact]
    public async Task MergedStatsSurviveASelfReferencingMergedRef()
    {
        const long mergedSource = 6969;
        var parentId = await _store.UpsertFromSourceAsync(mergedSource, new SManga("/merge/self", "Self"));
        await _store.SetFavoriteAsync(parentId, true);
        var childId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Child"));
        await _store.SyncChaptersFromSourceAsync(childId, new[]
        {
            new SChapter("/child/1", "1") { ChapterNumber = 1 },
            new SChapter("/child/2", "2") { ChapterNumber = 2 },
        });
        SetMergedRefs(parentId, 2499283573021220255, "/manga/abc");
        SetMergedRefs(parentId, mergedSource, "/merge/self");

        var entry = Assert.Single(await _store.GetLibraryAsync());
        Assert.Equal(parentId, entry.MangaId);
        Assert.Equal(2, entry.ChapterCount);
        Assert.Equal(2, entry.UnreadCount);
    }

    [Fact]
    public async Task FavouriteKeysMatchVisibleLibraryEntries()
    {
        const long mergedSource = 6969;
        var hiddenParent = await _store.UpsertFromSourceAsync(mergedSource, new SManga("/merge/one", "Merged"));
        await _store.SetFavoriteAsync(hiddenParent, true);
        var hiddenChild = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Child"));
        await _store.SetFavoriteAsync(hiddenChild, true);
        SetMergedRefs(hiddenParent, 2499283573021220255, "/manga/abc");
        var solo = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/solo", "Solo"));
        await _store.SetFavoriteAsync(solo, true);
        await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/unfaved", "Unfaved"));

        var keys = await _store.GetFavouriteKeysAsync();
        Assert.Equal(2, keys.Count);
        Assert.Contains(mergedSource + "|/merge/one", keys);
        Assert.Contains("2499283573021220255|/manga/solo", keys);
        Assert.DoesNotContain("2499283573021220255|/manga/abc", keys);
        Assert.DoesNotContain("2499283573021220255|/manga/unfaved", keys);
    }

    [Fact]
    public async Task ContinueReadingResumesNextUnreadChapterAtPageZero()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1") { ChapterNumber = 1 },
            new SChapter("/chapter/2", "2") { ChapterNumber = 2 },
            new SChapter("/chapter/3", "3") { ChapterNumber = 3 },
        });
        var chapters = (await _store.GetChaptersAsync(mangaId)).OrderBy(c => c.Url, StringComparer.Ordinal).ToList();
        await _store.SetChapterProgressAsync(chapters[0].ChapterId, 12, read: true);

        var entry = Assert.Single(await _store.GetContinueReadingAsync(10));
        Assert.Equal(chapters[1].ChapterId, entry.ChapterId);
        Assert.Equal(0, entry.LastPageRead);
        Assert.Equal(2, entry.ChapterNumber);
        Assert.Equal(2499283573021220255, entry.Source);

        await _store.SetChapterProgressAsync(chapters[1].ChapterId, 4, read: false);
        entry = Assert.Single(await _store.GetContinueReadingAsync(10));
        Assert.Equal(chapters[1].ChapterId, entry.ChapterId);
        Assert.Equal(4, entry.LastPageRead);
    }

    [Fact]
    public async Task HistoryAndUpdatesOrderMostRecentFirst()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1") { ChapterNumber = 1, DateUpload = 1000 },
            new SChapter("/chapter/2", "2") { ChapterNumber = 2, DateUpload = 2000 },
        });
        var chapters = (await _store.GetChaptersAsync(mangaId)).OrderBy(c => c.ChapterNumber).ToList();
        await _store.SetChapterProgressAsync(chapters[1].ChapterId, 4, read: false);
        await Task.Delay(1100);
        await _store.SetChapterProgressAsync(chapters[0].ChapterId, 0, read: true);

        var history = await _store.GetHistoryAsync(10);
        Assert.Equal(2, history.Count);
        Assert.Equal(chapters[0].ChapterId, history[0].ChapterId);
        Assert.Equal("1", history[0].ChapterName);

        var updates = await _store.GetUpdatesAsync(10);
        Assert.Equal(2, updates.Count);
        Assert.Equal(chapters[1].ChapterId, updates[0].ChapterId);
        Assert.Equal("2", updates[0].ChapterName);
        Assert.Equal(4, updates.Single(u => u.ChapterId == chapters[1].ChapterId).LastPageRead);
    }

    [Fact]
    public async Task HistoryAndUpdatesCarryTheSourceOfTheirTitle()
    {
        const long otherSource = 4242;
        var mangaDex = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Dex"));
        var other = await _store.UpsertFromSourceAsync(otherSource, new SManga("/series/xyz", "Other"));
        foreach (var id in new[] { mangaDex, other })
        {
            await _store.SetFavoriteAsync(id, true);
            await _store.SyncChaptersFromSourceAsync(id, new[] { new SChapter("/chapter/1", "1") { ChapterNumber = 1, DateUpload = 1000 } });
            await _store.SetChapterProgressAsync(Assert.Single(await _store.GetChaptersAsync(id)).ChapterId, 2, read: false);
        }

        var history = await _store.GetHistoryAsync(10);
        Assert.Equal(2499283573021220255, history.Single(h => h.MangaId == mangaDex).Source);
        Assert.Equal(otherSource, history.Single(h => h.MangaId == other).Source);

        var updates = await _store.GetUpdatesAsync(10);
        Assert.Equal(2499283573021220255, updates.Single(u => u.MangaId == mangaDex).Source);
        Assert.Equal(otherSource, updates.Single(u => u.MangaId == other).Source);
    }

    [Fact]
    public async Task HistoryCarriesThePageCountOfTheChapter()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        await _store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "1") { ChapterNumber = 1, DateUpload = 1000, PageCount = 31 },
            new SChapter("/chapter/2", "2") { ChapterNumber = 2, DateUpload = 2000 },
        });
        foreach (var chapter in await _store.GetChaptersAsync(mangaId))
        {
            await _store.SetChapterProgressAsync(chapter.ChapterId, 6, read: false);
        }

        var history = await _store.GetHistoryAsync(10);
        Assert.Equal(31, history.Single(h => h.ChapterName == "1").PageCount);
        Assert.Null(history.Single(h => h.ChapterName == "2").PageCount);
    }

    [Fact]
    public async Task CategoriesRoundTripThroughMangaMembership()
    {
        var mangaId = await _store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Title"));
        await _store.SetFavoriteAsync(mangaId, true);
        var created = await _store.CreateCategoryAsync("Reading");
        await _store.SetMangaCategoriesAsync(mangaId, new[] { created.Uid });

        var details = await _store.GetMangaAsync(mangaId);
        Assert.Contains(created.Uid, details!.CategoryUids);
        var entry = (await _store.GetLibraryAsync()).Single();
        Assert.Contains(created.Uid, entry.CategoryUids);
        var categories = await _store.GetCategoriesAsync();
        Assert.Equal("Reading", Assert.Single(categories).Name);
    }

    private async Task<SqliteRow> GetRow(long mangaId) =>
        (await _db.ReadAsync(db => db.Query("SELECT * FROM manga WHERE id = $id", ("$id", mangaId)).ToList())).Single();

    private BackupManga PayloadOf(SqliteRow row) =>
        new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(row.Blob("payload"));

    private void SetMergedRefs(long parentId, long childSource, string childUrl)
    {
        _db.WriteAsync(db => db.ExecuteNonQuery(
            "INSERT OR IGNORE INTO merged_ref(parent_id, child_source, child_url, is_info, priority) VALUES ($p, $s, $u, 0, 0)",
            ("$p", parentId), ("$s", childSource), ("$u", childUrl))).GetAwaiter().GetResult();
    }

    private void RewritePayloadRefs(long mangaId, long childSource, string childUrl)
    {
        _db.WriteAsync(db =>
        {
            var row = db.Query("SELECT payload FROM manga WHERE id = $id", ("$id", mangaId)).Single();
            var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(row.Blob("payload"));
            payload.MergedMangaReferences.Add(new BackupMergedMangaReference
            {
                MangaSourceId = childSource,
                MangaUrl = childUrl,
            });
            db.ExecuteNonQuery("UPDATE manga SET payload = $p WHERE id = $id",
                ("$p", payload.ToByteArray()), ("$id", mangaId));
            return 0;
        }).GetAwaiter().GetResult();
    }

    [Fact]
    public void ANewerSchemaVersionRefusesToOpen()
    {
        _db.WriteAsync(db => db.ExecuteNonQuery("UPDATE meta SET value = '99' WHERE key = 'schema_version'"))
            .GetAwaiter().GetResult();

        using var newer = new LibraryDatabase(_db.Path);
        var ex = Assert.Throws<InvalidOperationException>(() => newer.Initialise());
        Assert.Contains("newer app version", ex.Message);
    }
}
