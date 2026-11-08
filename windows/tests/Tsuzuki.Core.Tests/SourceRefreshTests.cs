using Tsuzuki.Core.Library;
using Google.Protobuf;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public class SourceRefreshTests : IDisposable
{
    private const long MdSource = 2499283573021220255;

    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;

    public SourceRefreshTests()
    {
        (_db, _store) = TestLib.NewLibrary();
    }

    public void Dispose() => _db.Dispose();

    private BackupMessage Rendered()
    {
        var (backup, wire, _, _) = new SyncEngine(_db, _store).Snapshot();
        return wire != null ? BackupMessage.Parser.ParseFrom(wire) : backup ?? new BackupMessage();
    }

    private Task<long> Seq() => _db.ReadAsync(db => _db.GetSeq(db));

    [Fact]
    public async Task OpeningALibraryTitleFromBrowseKeepsItsDetails()
    {
        var detailed = new SManga("/manga/abc", "Title")
        {
            Author = "Author",
            Artist = "Artist",
            Description = "A long description",
            Genres = ["Action", "Drama"],
            Status = MangaStatus.Ongoing,
            ThumbnailUrl = "https://example.org/old.jpg",
            Initialized = true,
        };
        var id = await _store.UpsertFromSourceAsync(MdSource, detailed);
        await _store.SetFavoriteAsync(id, true);

        await _store.UpsertFromSourceAsync(MdSource, new SManga("/manga/abc", "Title (new)") { ThumbnailUrl = "https://example.org/new.jpg" });
        await _store.SetFavoriteAsync(id, true);

        var details = (await _store.GetMangaAsync(id))!;
        Assert.Equal("Title (new)", details.Title);
        Assert.Equal("https://example.org/new.jpg", details.ThumbnailUrl);
        Assert.Equal("Author", details.Author);
        Assert.Equal("A long description", details.Description);
        Assert.Equal(["Action", "Drama"], details.Genres);
        Assert.Equal(MangaStatus.Ongoing, details.Status);
        var synced = TestLib.FindManga(Rendered(), MdSource, "/manga/abc");
        Assert.Equal("Author", synced.Author);
        Assert.Equal("Artist", synced.Artist);
        Assert.Equal("A long description", synced.Description);
        Assert.Equal(["Action", "Drama"], synced.Genre);
        Assert.Equal((int)MangaStatus.Ongoing, synced.Status);
        Assert.True(synced.Initialized);
    }

    [Fact]
    public async Task RefreshingAPhoneSyncedChapterWithUnchangedDataLeavesItAlone()
    {
        var chapter = TestLib.Chapter("/chapter/1", name: "Ch. 1", number: 1, version: 5, modified: 100, order: 0);
        chapter.Memo = ByteString.CopyFromUtf8("phone memo");
        chapter.DateUpload = 1_700_000_000_000;
        var manga = TestLib.Manga(MdSource, "/manga/abc", "Title", version: 3, modified: 50, chapter);
        manga.Favorite = true;
        await new SyncEngine(_db, _store).Merge("phone", "Phone", TestLib.Backup(manga), 0, false, Array.Empty<long>());
        var seq = await Seq();
        var id = (await _store.GetLibraryAsync()).Single().MangaId;

        var result = await _store.SyncChaptersFromSourceAsync(id,
        [
            new SChapter("/chapter/1", "Ch. 1") { ChapterNumber = 1, DateUpload = 1_700_000_000_000, PageCount = 20 },
        ]);

        Assert.Equal(new ChapterSyncResult(0, 0, 0), result);
        Assert.Equal(seq, await Seq());
        var stored = Assert.Single(TestLib.FindManga(Rendered(), MdSource, "/manga/abc").Chapters);
        Assert.Equal(5, stored.Version);
        Assert.Equal("phone memo", stored.Memo.ToStringUtf8());
    }

    [Fact]
    public async Task ARenamedChapterKeepsPhoneOnlyFieldsAndTakesItsSourcePosition()
    {
        var chapter = TestLib.Chapter("/chapter/1", name: "Ch. 1", number: 1, read: true, page: 7, version: 5, modified: 100, order: 9);
        chapter.Memo = ByteString.CopyFromUtf8("phone memo");
        var manga = TestLib.Manga(MdSource, "/manga/abc", "Title", version: 3, modified: 50, chapter);
        manga.Favorite = true;
        await new SyncEngine(_db, _store).Merge("phone", "Phone", TestLib.Backup(manga), 0, false, Array.Empty<long>());
        var id = (await _store.GetLibraryAsync()).Single().MangaId;

        await _store.SyncChaptersFromSourceAsync(id,
        [
            new SChapter("/chapter/2", "Ch. 2") { ChapterNumber = 2 },
            new SChapter("/chapter/1", "Chapter 1") { ChapterNumber = 1 },
        ]);

        var chapters = TestLib.FindManga(Rendered(), MdSource, "/manga/abc").Chapters.ToDictionary(c => c.Url);
        Assert.Equal("Chapter 1", chapters["/chapter/1"].Name);
        Assert.Equal(6, chapters["/chapter/1"].Version);
        Assert.Equal("phone memo", chapters["/chapter/1"].Memo.ToStringUtf8());
        Assert.True(chapters["/chapter/1"].Read);
        Assert.Equal(7, chapters["/chapter/1"].LastPageRead);
        Assert.Equal(1, chapters["/chapter/1"].SourceOrder);
        Assert.Equal(0, chapters["/chapter/2"].SourceOrder);
    }

    [Fact]
    public async Task BrowsingATitleOutsideTheLibraryDoesNotSyncIt()
    {
        await _store.SetFavoriteAsync(await _store.UpsertFromSourceAsync(MdSource, new SManga("/manga/kept", "Kept")), true);
        var seq = await Seq();
        var id = await _store.UpsertFromSourceAsync(MdSource, new SManga("/manga/browse", "Browsed"));
        await _store.SyncChaptersFromSourceAsync(id, [new SChapter("/chapter/b1", "1") { ChapterNumber = 1 }]);

        Assert.Equal(seq, await Seq());
        Assert.False(TestLib.HasManga(Rendered(), MdSource, "/manga/browse"));

        await _store.SetFavoriteAsync(id, true);
        var delta = TestLib.BackupOf(await new SyncEngine(_db, _store).Merge("phone", "Phone", new BackupMessage(), seq, false, Array.Empty<long>(), chapterDelta: true));

        var added = TestLib.FindManga(delta, MdSource, "/manga/browse");
        Assert.Equal("/chapter/b1", Assert.Single(added.Chapters).Url);
    }
}
