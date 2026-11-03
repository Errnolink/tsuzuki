using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Google.Protobuf;
using Microsoft.Data.Sqlite;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

/// <summary>
/// Chapter-delta capability (R1+R4): capped devices receive only the chapters that
/// moved since their cursor plus the parent manga payload, and the quiet category
/// floor is dropped; uncapped requests keep the whole-manga expansion byte-for-byte.
/// </summary>
public class ChapterDeltaTests : IDisposable
{
    private const long MdSource = 2499283573021220255;

    private readonly (LibraryDatabase Db, SqliteLibraryStore Store) _library;
    private readonly SyncHub _hub;
    private readonly HttpClient _client;
    private readonly string _key;

    public ChapterDeltaTests()
    {
        _library = TestLib.NewLibrary();
        _hub = new SyncHub(_library.Db, _library.Store, preferredPort: 0);
        _hub.StartAsync().GetAwaiter().GetResult();
        _key = _hub.GetPairing().ApiKey;
        _client = new HttpClient { BaseAddress = new Uri($"http://127.0.0.1:{_hub.Status.Port}") };
    }

    public void Dispose()
    {
        _client.Dispose();
        _hub.Dispose();
        _library.Db.Dispose();
    }

    private HttpRequestMessage Merge(object body, string device, long cursor, bool full = false, string? caps = null)
    {
        var bytes = body switch
        {
            BackupMessage backup => backup.ToByteArray(),
            byte[] raw => raw,
            _ => Array.Empty<byte>(),
        };
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge");
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        request.Headers.TryAddWithoutValidation("X-Device-ID", device);
        request.Headers.TryAddWithoutValidation("X-Device-Name", device + "-name");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", cursor.ToString());
        if (full) request.Headers.TryAddWithoutValidation("X-Sync-Full", "true");
        if (caps != null) request.Headers.TryAddWithoutValidation("X-Sync-Caps", caps);
        request.Content = new ByteArrayContent(bytes);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        return request;
    }

    private BackupManga ThreeChapterManga(int version = 5, long modified = 1000) =>
        TestLib.Manga(MdSource, "/manga/abc", "One", version: version, modified: modified,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
            TestLib.Chapter("/chapter/2", number: 2, version: 5, modified: 100),
            TestLib.Chapter("/chapter/3", number: 3, version: 5, modified: 100));

    private async Task SeedAsync()
    {
        var response = await _client.SendAsync(Merge(TestLib.Backup(ThreeChapterManga())
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900)), "A", 0, full: true));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task CapabilitiesAdvertiseChapterDelta()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/capabilities");
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        using var response = await _client.SendAsync(request);
        Assert.Contains("chapter-delta", response.Headers.GetValues("X-Sync-Caps").Single().Split(','));
        var body = await response.Content.ReadFromJsonAsync<Dictionary<string, System.Text.Json.JsonElement>>();
        Assert.Contains(body!["caps"].EnumerateArray(), cap => cap.GetString() == "chapter-delta");
    }

    private async Task<long> VersionAsync(string query)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/version" + query);
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        using var response = await _client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<Dictionary<string, long>>();
        return body!["seq"];
    }

    [Fact]
    public async Task VersionWaitAnswersAsSoonAsAMergeLands()
    {
        await SeedAsync();
        var seq = await VersionAsync("");
        var watch = System.Diagnostics.Stopwatch.StartNew();
        var waiting = VersionAsync($"?since={seq}&wait=20");
        await Task.Delay(300);
        Assert.False(waiting.IsCompleted);

        var changed = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(changed, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000));
        await _client.SendAsync(Merge(changed, "A", seq));

        Assert.True(await waiting > seq);
        Assert.True(watch.Elapsed < TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task VersionWaitReturnsTheSameSeqWhenNothingChanges()
    {
        await SeedAsync();
        var seq = await VersionAsync("");
        var watch = System.Diagnostics.Stopwatch.StartNew();
        Assert.Equal(seq, await VersionAsync($"?since={seq}&wait=1"));
        Assert.True(watch.Elapsed >= TimeSpan.FromMilliseconds(900));
    }

    [Fact]
    public async Task VersionWaitWakesImmediatelyWhenAReadTransitionLands()
    {
        await SeedAsync();
        var mangaId = (await _library.Store.GetLibraryAsync()).Single().MangaId;
        var chapter = (await _library.Store.GetChaptersAsync(mangaId)).OrderBy(c => c.ChapterNumber).First();
        var seq = await VersionAsync("");
        var waiting = VersionAsync($"?since={seq}&wait=25");
        await Task.Delay(300);
        Assert.False(waiting.IsCompleted);

        var watch = System.Diagnostics.Stopwatch.StartNew();
        await _library.Store.SetChapterProgressAsync(chapter.ChapterId, 5, read: true);
        await Task.Delay(200);
        await _library.Store.SetChapterProgressAsync(chapter.ChapterId, 6, read: true);

        Assert.True(await waiting > seq);
        Assert.True(watch.Elapsed < TimeSpan.FromSeconds(2.5),
            $"waiter woke after {watch.Elapsed.TotalSeconds:F1}s; a debounced read flag would hold it past 3s");
    }

    [Fact]
    public async Task CappedPullCarriesOnlyTheChangedChapter()
    {
        await SeedAsync();
        await _client.SendAsync(Merge(new BackupMessage(), "B", 0));

        var changed = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(changed, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000));
        await _client.SendAsync(Merge(changed, "A", 1));

        var capped = await _client.SendAsync(Merge(new BackupMessage(), "B", 1, caps: "chapter-delta"));
        Assert.Equal("true", capped.Headers.GetValues("X-Sync-Changed").Single());
        var body = BackupMessage.Parser.ParseFrom(await capped.Content.ReadAsByteArrayAsync());
        var pulled = TestLib.FindManga(body, MdSource, "/manga/abc");
        Assert.Single(pulled.Chapters);
        Assert.Equal(6, pulled.Chapters.Single(c => c.Url == "/chapter/2").Version);
    }

    [Fact]
    public async Task UncappedPullKeepsTheWholeMangaExpansion()
    {
        await SeedAsync();
        await _client.SendAsync(Merge(new BackupMessage(), "B", 0));

        var changed = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(changed, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000));
        await _client.SendAsync(Merge(changed, "A", 1));

        var uncapped = await _client.SendAsync(Merge(new BackupMessage(), "B", 1));
        var body = BackupMessage.Parser.ParseFrom(await uncapped.Content.ReadAsByteArrayAsync());
        Assert.Equal(3, TestLib.FindManga(body, MdSource, "/manga/abc").Chapters.Count);
    }

    [Fact]
    public async Task CappedMangaOnlyChangeShipsAnEmptyChapterList()
    {
        await SeedAsync();
        await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        await _client.SendAsync(Merge(TestLib.Backup(ThreeChapterManga(version: 6, modified: 5000)), "A", 1));

        var capped = await _client.SendAsync(Merge(new BackupMessage(), "B", 1, caps: "chapter-delta"));
        var body = BackupMessage.Parser.ParseFrom(await capped.Content.ReadAsByteArrayAsync());
        var pulled = TestLib.FindManga(body, MdSource, "/manga/abc");
        Assert.Empty(pulled.Chapters);
        Assert.Equal(6, pulled.Version);
    }

    [Fact]
    public async Task CappedDeltaDropsQuietCategoriesButShipsThemOnActivity()
    {
        await SeedAsync();
        var register = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        var cursorB = long.Parse(register.Headers.GetValues("X-Sync-Cursor").Single());

        var changed = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(changed, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000));
        await _client.SendAsync(Merge(changed, "A", 1));

        var quiet = await _client.SendAsync(Merge(new BackupMessage(), "B", cursorB, caps: "chapter-delta"));
        Assert.Equal("true", quiet.Headers.GetValues("X-Sync-Changed").Single());
        var quietBody = BackupMessage.Parser.ParseFrom(await quiet.Content.ReadAsByteArrayAsync());
        Assert.Empty(quietBody.BackupCategories);
        Assert.Equal("2", quiet.Headers.GetValues("X-Sync-Cursor").Single());
        cursorB = 2;

        var renamed = TestLib.Category("Reading renamed", 3, 0, version: 3, modified: 6000);
        await _client.SendAsync(Merge(TestLib.Backup().WithCategories(renamed), "A", 2));

        var active = await _client.SendAsync(Merge(new BackupMessage(), "B", cursorB, caps: "chapter-delta"));
        Assert.Equal("true", active.Headers.GetValues("X-Sync-Changed").Single());
        var activeBody = BackupMessage.Parser.ParseFrom(await active.Content.ReadAsByteArrayAsync());
        Assert.Single(activeBody.BackupCategories);
        Assert.Equal("Reading renamed", activeBody.BackupCategories.Single().Name);
    }

    [Fact]
    public async Task CappedConflictReturnsTheServerWonChapter()
    {
        await SeedAsync();
        var register = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        var cursor = long.Parse(register.Headers.GetValues("X-Sync-Cursor").Single());

        var stale = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(stale, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 3, modified: 9000));
        var conflict = await _client.SendAsync(Merge(stale, "B", cursor, caps: "chapter-delta"));

        // the server keeps its newer chapter and returns it to the stale uploader
        Assert.Equal("true", conflict.Headers.GetValues("X-Sync-Changed").Single());
        var body = BackupMessage.Parser.ParseFrom(await conflict.Content.ReadAsByteArrayAsync());
        Assert.True(TestLib.HasManga(body, MdSource, "/manga/abc"));
        var pulled = TestLib.FindManga(body, MdSource, "/manga/abc");
        Assert.Equal(5, pulled.Chapters.Single(c => c.Url == "/chapter/2").Version);
    }

    [Fact]
    public async Task PcSourceRefreshInsertReachesTheNextPhoneDelta()
    {
        await SeedAsync();
        var register = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        var cursor = long.Parse(register.Headers.GetValues("X-Sync-Cursor").Single());

        var mangaId = await _library.Db.ReadAsync(
            db => db.Scalar<long>("SELECT id FROM manga WHERE source = $s AND url = $u",
                ("$s", MdSource), ("$u", "/manga/abc")));
        await _library.Store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "Chapter") { ChapterNumber = 1 },
            new SChapter("/chapter/4", "Chapter") { ChapterNumber = 4 },
        });

        var capped = await _client.SendAsync(Merge(new BackupMessage(), "B", cursor, caps: "chapter-delta"));
        Assert.Equal("true", capped.Headers.GetValues("X-Sync-Changed").Single());
        var body = BackupMessage.Parser.ParseFrom(await capped.Content.ReadAsByteArrayAsync());
        var pulled = TestLib.FindManga(body, MdSource, "/manga/abc");
        var chapter = Assert.Single(pulled.Chapters);
        Assert.Equal("/chapter/4", chapter.Url);
        Assert.True(chapter.Version > 0);
    }

    [Fact]
    public async Task PcSourceRefreshMetadataChangeReachesTheNextPhoneDelta()
    {
        await SeedAsync();
        var register = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        var cursor = long.Parse(register.Headers.GetValues("X-Sync-Cursor").Single());

        var mangaId = await _library.Db.ReadAsync(
            db => db.Scalar<long>("SELECT id FROM manga WHERE source = $s AND url = $u",
                ("$s", MdSource), ("$u", "/manga/abc")));
        await _library.Store.SyncChaptersFromSourceAsync(mangaId, new[]
        {
            new SChapter("/chapter/1", "Chapter") { ChapterNumber = 1 },
            new SChapter("/chapter/2", "Renamed by scanlator") { ChapterNumber = 2 },
        });

        var capped = await _client.SendAsync(Merge(new BackupMessage(), "B", cursor, caps: "chapter-delta"));
        Assert.Equal("true", capped.Headers.GetValues("X-Sync-Changed").Single());
        var body = BackupMessage.Parser.ParseFrom(await capped.Content.ReadAsByteArrayAsync());
        var pulled = TestLib.FindManga(body, MdSource, "/manga/abc");
        var chapter = pulled.Chapters.Single(c => c.Url == "/chapter/2");
        Assert.Equal("Renamed by scanlator", chapter.Name);
        Assert.True(chapter.Version > 5);
    }

    [Fact]
    public async Task CappedAndUncappedAppliesConvergeToTheSameStoreState()
    {
        await SeedAsync();
        await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        await _client.SendAsync(Merge(new BackupMessage(), "C", 0));

        var changed = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var manga = TestLib.FindManga(changed, MdSource, "/manga/abc");
        manga.Chapters.Clear();
        manga.Chapters.Add(TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000, read: true, page: 4));
        await _client.SendAsync(Merge(changed, "A", 1));

        var capped = BackupMessage.Parser.ParseFrom(
            await (await _client.SendAsync(Merge(new BackupMessage(), "B", 1, caps: "chapter-delta"))).Content.ReadAsByteArrayAsync());
        var uncapped = BackupMessage.Parser.ParseFrom(
            await (await _client.SendAsync(Merge(new BackupMessage(), "C", 1))).Content.ReadAsByteArrayAsync());

        // two fresh phone stores pull the same change, one capped, one uncapped
        var (phoneCappedDb, _) = TestLib.NewLibrary();
        var (phoneUncappedDb, _) = TestLib.NewLibrary();
        var cappedEngine = new SyncEngine(phoneCappedDb);
        var uncappedEngine = new SyncEngine(phoneUncappedDb);
        await cappedEngine.Restore(uncappedSnapshotSeed());
        await uncappedEngine.Restore(uncappedSnapshotSeed());
        await cappedEngine.Restore(capped);
        await uncappedEngine.Restore(uncapped);

        // the capped phone also keeps a local-only chapter the hub never saw
        var localOnly = TestLib.Backup(ThreeChapterManga(version: 5, modified: 100));
        var localManga = TestLib.FindManga(localOnly, MdSource, "/manga/abc");
        localManga.Chapters.Clear();
        localManga.Chapters.Add(TestLib.Chapter("/chapter/local", number: 99, version: 1, modified: 7));
        await cappedEngine.Restore(localOnly);

        using var cappedConn = phoneCappedDb.Open();
        using var uncappedConn = phoneUncappedDb.Open();
        static List<string> StoreItems(LibraryDatabase db, SqliteConnection conn)
        {
            return new ItemStore(db).AllItems(conn)
                .Where(i => i.Kind != SyncKind.Field)
                .Select(i => $"{i.Kind}|{i.Key}|{Convert.ToHexString(i.Payload.ToArray())}|{i.Version}|{i.ModifiedAt}")
                .OrderBy(x => x, StringComparer.Ordinal)
                .ToList();
        }
        var cappedItems = StoreItems(phoneCappedDb, cappedConn);
        var uncappedItems = StoreItems(phoneUncappedDb, uncappedConn);

        // both phones carry the same synced state; the capped phone additionally
        // keeps its local-only chapter that the hub never saw
        Assert.Equal(uncappedItems.Count + 1, cappedItems.Count);
        foreach (var item in uncappedItems)
        {
            Assert.Contains(item, cappedItems);
        }
        Assert.Contains(cappedItems, x => x.Contains("/chapter/local|", StringComparison.Ordinal));
        phoneCappedDb.Dispose();
        phoneUncappedDb.Dispose();
    }

    private BackupMessage uncappedSnapshotSeed()
    {
        // a fresh phone that already received the full store BEFORE the change: manga
        // with all three chapters at version 5, the category, no progress
        return TestLib.Backup(ThreeChapterManga())
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900));
    }
}
