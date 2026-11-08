using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public class MergeEditingTests : IDisposable
{
    private const long MangaDex = 2499283573021220255;
    private const long WeebCentral = 2131019126180322627;
    private const long Merged = 6969;

    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;
    private readonly SqliteMergeEditor _merges;

    public MergeEditingTests()
    {
        (_db, _store) = TestLib.NewLibrary();
        _merges = new SqliteMergeEditor(_db, _store);
    }

    public void Dispose() => _db.Dispose();

    private async Task<long> Title(long source, string url, string title, bool favourite, params string[] chapters)
    {
        var id = await _store.UpsertFromSourceAsync(source, new SManga(url, title)
        {
            Description = title + " blurb",
            ThumbnailUrl = "https://covers.test/" + title,
            Initialized = true,
        });
        if (favourite)
        {
            await _store.SetFavoriteAsync(id, true);
        }
        await _store.SyncChaptersFromSourceAsync(id,
            chapters.Select((url, i) => new SChapter(url, url) { ChapterNumber = i + 1, DateUpload = 1000 + i }).ToArray());
        return id;
    }

    private async Task<long> PhoneFullSync() =>
        (await new SyncEngine(_db, _store).Merge("phone", "Phone", new BackupMessage(), 0, true, Array.Empty<long>())).Cursor;

    private async Task<BackupMessage> PhoneDelta(long cursor) =>
        TestLib.BackupOf(await new SyncEngine(_db, _store).Merge("phone", "Phone", new BackupMessage(), cursor, false, Array.Empty<long>()));

    private static List<(long Source, string Url, bool Info, bool Updates, int Sort, int Priority, bool Download, string MergeUrl)> Refs(BackupManga parent) =>
        parent.MergedMangaReferences
            .Select(r => (r.MangaSourceId, r.MangaUrl, r.IsInfoManga, r.GetChapterUpdates, r.ChapterSortMode, r.ChapterPriority, r.DownloadChapters, r.MergeUrl))
            .OrderBy(r => r.ChapterPriority).ThenBy(r => r.MangaSourceId).ToList();

    [Fact]
    public async Task MergeMadeOnPcReachesThePhoneAsTheStructureAndroidBuilds()
    {
        var reading = await _store.CreateCategoryAsync("Reading");
        var original = await Title(MangaDex, "/manga/x", "Original", favourite: true, "/chapter/x1", "/chapter/x2");
        await _store.SetMangaCategoriesAsync(original, [reading.Uid]);
        var cursor = await PhoneFullSync();
        var candidate = await Title(WeebCentral, "/series/y", "Candidate", favourite: false, "/series/y/1");

        var parentId = await _merges.MergeAsync(original, candidate);
        var delta = await PhoneDelta(cursor);

        var parent = TestLib.FindManga(delta, Merged, "/manga/x");
        Assert.True(parent.Favorite);
        Assert.Equal("Original", parent.Title);
        Assert.Equal("Original blurb", parent.Description);
        Assert.Empty(parent.Chapters);
        Assert.Equal(new[] { (long)reading.Order }, parent.Categories);
        Assert.Equal(
            new[]
            {
                (Merged, "/manga/x", false, false, 0, -1, false, "/manga/x"),
                (WeebCentral, "/series/y", false, true, 0, 0, true, "/manga/x"),
                (MangaDex, "/manga/x", true, true, 0, 0, true, "/manga/x"),
            },
            Refs(parent));
        var child = TestLib.FindManga(delta, WeebCentral, "/series/y");
        Assert.False(child.Favorite);
        Assert.Equal(new[] { "/series/y/1" }, child.Chapters.Select(c => c.Url));
        var entry = Assert.Single(await _store.GetLibraryAsync());
        Assert.Equal(parentId, entry.MangaId);
        Assert.True(entry.IsMerged);
    }

    [Fact]
    public async Task MergedChaptersStayOwnedByTheirChildren()
    {
        var original = await Title(MangaDex, "/manga/x", "Original", favourite: true, "/chapter/x1");
        var candidate = await Title(WeebCentral, "/series/y", "Candidate", favourite: false, "/series/y/1");

        var parentId = await _merges.MergeAsync(original, candidate);

        var chapters = await _store.GetChaptersAsync(parentId);
        Assert.Equal(
            new[] { (original, "/chapter/x1"), (candidate, "/series/y/1") },
            chapters.Select(c => (c.OwnerMangaId, c.Url)).OrderBy(c => c.Url));
        var snapshot = TestLib.BackupOf(await new SyncEngine(_db, _store).Merge("phone", "Phone", new BackupMessage(), 0, true, Array.Empty<long>()));
        Assert.Empty(TestLib.FindManga(snapshot, Merged, "/manga/x").Chapters);
        Assert.Equal(new[] { "/chapter/x1" }, TestLib.FindManga(snapshot, MangaDex, "/manga/x").Chapters.Select(c => c.Url));
        Assert.Equal(new[] { "/series/y/1" }, TestLib.FindManga(snapshot, WeebCentral, "/series/y").Chapters.Select(c => c.Url));
    }

    [Fact]
    public async Task ReadingAMergedChapterSyncsTheReadMarkOntoTheChildChapter()
    {
        var original = await Title(MangaDex, "/manga/x", "Original", favourite: true, "/chapter/x1");
        var candidate = await Title(WeebCentral, "/series/y", "Candidate", favourite: false, "/series/y/1", "/series/y/2");
        var parentId = await _merges.MergeAsync(original, candidate);
        var cursor = await PhoneFullSync();

        var chapter = (await _store.GetChaptersAsync(parentId)).Single(c => c.Url == "/series/y/2");
        await _store.SetChaptersReadAsync([chapter.ChapterId], true);
        var delta = await PhoneDelta(cursor);

        var child = TestLib.FindManga(delta, WeebCentral, "/series/y");
        Assert.True(child.Chapters.Single(c => c.Url == "/series/y/2").Read);
        Assert.False(child.Chapters.Single(c => c.Url == "/series/y/1").Read);
        Assert.DoesNotContain(delta.BackupManga, m => m.Source == Merged && m.Chapters.Count > 0);
    }

    [Fact]
    public async Task AddingAndRemovingChildrenKeepsOneInfoSource()
    {
        var original = await Title(MangaDex, "/manga/x", "Original", favourite: true, "/chapter/x1");
        var second = await Title(WeebCentral, "/series/y", "Second", favourite: false, "/series/y/1");
        var third = await Title(WeebCentral, "/series/z", "Third", favourite: false, "/series/z/1");
        var parentId = await _merges.MergeAsync(original, second);

        Assert.Equal(parentId, await _merges.MergeAsync(parentId, third));
        await _merges.RemoveAsync(parentId, new MergedRef(MangaDex, "/manga/x", true, 0));

        var parent = await _store.GetMangaAsync(parentId);
        var children = parent!.MergedRefs.Where(r => r.Source != Merged).ToList();
        Assert.Equal(new[] { "/series/y", "/series/z" }, children.Select(r => r.Url));
        Assert.Equal("/series/y", Assert.Single(children, r => r.IsInfoManga).Url);
        Assert.Equal("Second", parent.Title);
        Assert.Equal(new[] { "/series/y/1", "/series/z/1" }, (await _store.GetChaptersAsync(parentId)).Select(c => c.Url).OrderBy(u => u));
        await Assert.ThrowsAsync<InvalidOperationException>(() => _merges.RemoveAsync(parentId, children[0]));
    }

    [Fact]
    public async Task UnmergeRestoresTheChildrenAsLibraryTitles()
    {
        var reading = await _store.CreateCategoryAsync("Reading");
        var original = await Title(MangaDex, "/manga/x", "Original", favourite: true, "/chapter/x1");
        await _store.SetMangaCategoriesAsync(original, [reading.Uid]);
        var candidate = await Title(WeebCentral, "/series/y", "Candidate", favourite: false, "/series/y/1");
        var parentId = await _merges.MergeAsync(original, candidate);
        var cursor = await PhoneFullSync();
        var mergedVersion = TestLib.FindManga(await PhoneDelta(0), Merged, "/manga/x").Version;

        await _merges.UnmergeAsync(parentId);

        var library = await _store.GetLibraryAsync();
        Assert.Equal(new[] { "Candidate", "Original" }, library.Select(e => e.Title).OrderBy(t => t));
        Assert.All(library, e => Assert.False(e.IsMerged));
        Assert.All(library, e => Assert.Equal(new[] { reading.Uid }, e.CategoryUids));
        Assert.Equal(2, (await _store.GetChaptersAsync(library.Single(e => e.Title == "Original").MangaId)).Count
            + (await _store.GetChaptersAsync(library.Single(e => e.Title == "Candidate").MangaId)).Count);
        var delta = await PhoneDelta(cursor);
        var parent = TestLib.FindManga(delta, Merged, "/manga/x");
        Assert.False(parent.Favorite);
        Assert.Empty(parent.MergedMangaReferences);
        Assert.True(parent.Version > mergedVersion);
        Assert.True(TestLib.FindManga(delta, WeebCentral, "/series/y").Favorite);
    }

    [Fact]
    public async Task EditingAnAndroidMergeKeepsAndroidsReferenceSettings()
    {
        static BackupMergedMangaReference Ref(long source, string url, bool info, bool updates, int sort, int priority, bool download) => new()
        {
            MangaSourceId = source,
            MangaUrl = url,
            IsInfoManga = info,
            GetChapterUpdates = updates,
            ChapterSortMode = sort,
            ChapterPriority = priority,
            DownloadChapters = download,
            MergeUrl = "/manga/a",
        };
        var parent = TestLib.Manga(Merged, "/manga/a", "Phone Title", version: 3, modified: 1000);
        parent.Favorite = true;
        parent.MergedMangaReferences.Add(Ref(MangaDex, "/manga/a", true, true, 0, 0, true));
        parent.MergedMangaReferences.Add(Ref(WeebCentral, "/series/b", false, false, 0, 1, false));
        parent.MergedMangaReferences.Add(Ref(Merged, "/manga/a", false, false, 2, -1, false));
        var first = TestLib.Manga(MangaDex, "/manga/a", "First", version: 2, modified: 900, TestLib.Chapter("/chapter/a1", number: 1));
        first.Favorite = true;
        var second = TestLib.Manga(WeebCentral, "/series/b", "Second", version: 1, modified: 900, TestLib.Chapter("/series/b/1", number: 1));
        second.Favorite = false;
        second.Description = "From the second source";
        var sync = await new SyncEngine(_db, _store).Merge("phone", "Phone", TestLib.Backup(parent, first, second), 0, true, Array.Empty<long>());
        var parentId = (await _store.FindMangaIdAsync(Merged, "/manga/a"))!.Value;

        await _merges.ReorderAsync(parentId, [new MergedRef(WeebCentral, "/series/b", false, 1), new MergedRef(MangaDex, "/manga/a", true, 0)]);
        await _merges.SetInfoSourceAsync(parentId, new MergedRef(WeebCentral, "/series/b", false, 0));
        var delta = await PhoneDelta(sync.Cursor);

        var edited = TestLib.FindManga(delta, Merged, "/manga/a");
        Assert.True(edited.Version > 3);
        Assert.Equal("Second", edited.Title);
        Assert.Equal("From the second source", edited.Description);
        Assert.Equal(
            new[]
            {
                (Merged, "/manga/a", false, false, 2, -1, false, "/manga/a"),
                (WeebCentral, "/series/b", true, false, 0, 0, false, "/manga/a"),
                (MangaDex, "/manga/a", false, true, 0, 1, true, "/manga/a"),
            },
            Refs(edited));
    }
}
