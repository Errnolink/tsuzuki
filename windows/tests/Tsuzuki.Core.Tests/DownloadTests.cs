using System.Collections.Concurrent;
using System.IO.Compression;
using System.Net.Http.Headers;
using System.Text;
using System.Xml.Linq;
using Google.Protobuf;
using Tsuzuki.Core.Downloads;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class DownloadTests : IDisposable
{
    private const long MangaDex = 2499283573021220255;

    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore _store;
    private readonly string _dir;
    private readonly string _root;
    private readonly FakeSource _source = new();
    private readonly List<DownloadQueue> _queues = new();

    public DownloadTests()
    {
        (_db, _store) = TestLib.NewLibrary();
        _dir = Path.GetDirectoryName(_db.Path)!;
        _root = Path.Combine(_dir, "downloads");
    }

    public void Dispose()
    {
        foreach (var queue in _queues)
        {
            queue.StopAsync().GetAwaiter().GetResult();
        }
        _db.Dispose();
    }

    private DownloadQueue NewQueue(FakeImages images, DownloadOptions? options = null, DownloadEnvironment? environment = null)
    {
        var queue = new DownloadQueue(_db, _store, new FakeRegistry(_source), images, images, Path.Combine(_dir, "extracted"),
            options ?? new DownloadOptions { Root = _root },
            environment ?? new DownloadEnvironment { PageRetryDelay = TimeSpan.FromMilliseconds(10) });
        _queues.Add(queue);
        return queue;
    }

    private async Task<(long MangaId, IReadOnlyList<ChapterRow> Chapters)> AddMangaAsync(string title, params SChapter[] chapters)
    {
        var id = await _store.UpsertFromSourceAsync(MangaDex, new SManga("/manga/" + Guid.NewGuid().ToString("N"), title)
        {
            ThumbnailUrl = "https://covers.test/cover.jpg",
            Author = "Author",
        });
        await _store.SyncChaptersFromSourceAsync(id, chapters);
        var rows = await _store.GetChaptersAsync(id);
        return (id, rows.OrderBy(c => c.ChapterNumber).ToList());
    }

    private static SChapter Chapter(string url, string name, double number, string? scanlator = "Group") =>
        new(url, name) { ChapterNumber = number, Scanlator = scanlator, DateUpload = 1_700_000_000_000 };

    private static async Task Until(Func<bool> condition, string because, int timeoutMs = 10_000)
    {
        var watch = System.Diagnostics.Stopwatch.StartNew();
        while (!condition())
        {
            if (watch.ElapsedMilliseconds > timeoutMs)
            {
                Assert.Fail("Timed out waiting: " + because);
            }
            await Task.Delay(20);
        }
    }

    [Fact]
    public async Task DownloadedChapterIsAStoredMihonCbzWithComicInfoAndCover()
    {
        _source.PageCount = 12;
        var (mangaId, chapters) = await AddMangaAsync("Re:Zero / Starting Life?", Chapter("/chapter/1", "Ch. 1: Start", 1));
        var queue = NewQueue(new FakeImages(_dir));
        await queue.StartAsync();

        await queue.EnqueueAsync([chapters[0].ChapterId]);
        await Until(() => queue.IsDownloaded(mangaId, "/chapter/1"), "chapter downloads");

        var mangaDir = Path.Combine(_root, "MangaDex (EN)", "Re_Zero _ Starting Life_");
        var cbz = Path.Combine(mangaDir, "Group_Ch. 1_ Start.cbz");
        Assert.True(File.Exists(cbz), "the CBZ uses Mihon's folder and file names");
        Assert.True(File.Exists(Path.Combine(mangaDir, "cover.jpg")));
        Assert.Empty(Directory.GetDirectories(mangaDir));
        using var zip = ZipFile.OpenRead(cbz);
        var pages = zip.Entries.Where(e => e.Name != "ComicInfo.xml").Select(e => e.Name).ToList();
        Assert.Equal(Enumerable.Range(1, 12).Select(i => $"{i:000}.jpg"), pages);
        Assert.All(zip.Entries, e => Assert.Equal(e.Length, e.CompressedLength));
        Assert.Equal("http://pages.test/1/7.jpg", PageText(zip.GetEntry("007.jpg")!));
        var info = XDocument.Load(zip.GetEntry("ComicInfo.xml")!.Open()).Root!;
        Assert.Equal("Re:Zero / Starting Life?", info.Element("Series")!.Value);
        Assert.Equal("1", info.Element("Number")!.Value);
        Assert.Equal("Group", info.Element("Translator")!.Value);
        Assert.Equal("MangaDex", info.Elements().Single(e => e.Name.LocalName == "SourceMihon").Value);
    }

    [Fact]
    public async Task InterruptedChapterResumesAtThePageItReachedAfterRestart()
    {
        _source.PageCount = 10;
        var (mangaId, chapters) = await AddMangaAsync("Resume", Chapter("/chapter/1", "Ch. 1", 1));
        var stalled = new FakeImages(_dir) { Stall = url => url.EndsWith("/6.jpg") || url.EndsWith("/7.jpg") || url.EndsWith("/8.jpg") || url.EndsWith("/9.jpg") || url.EndsWith("/10.jpg") };
        var first = NewQueue(stalled);
        await first.StartAsync();
        await first.EnqueueAsync([chapters[0].ChapterId]);
        var staging = Path.Combine(_root, "MangaDex (EN)", "Resume", "Group_Ch. 1_tmp");
        await Until(() => Directory.Exists(staging) && Directory.GetFiles(staging, "*.jpg").Length == 5, "five pages land in the staging folder");
        await first.StopAsync();
        File.WriteAllBytes(Path.Combine(staging, "006.tmp"), [0xFF, 0xD8]);

        var fresh = new FakeImages(_dir);
        var second = NewQueue(fresh);
        await second.StartAsync();
        await Until(() => second.IsDownloaded(mangaId, "/chapter/1"), "the queued chapter resumes after the restart");

        Assert.Equal([6, 7, 8, 9, 10], fresh.Fetched.Where(u => u.Contains("pages.test")).Select(u => int.Parse(u.Split('/')[^1].Replace(".jpg", ""))).Order());
        using var zip = ZipFile.OpenRead(Path.Combine(_root, "MangaDex (EN)", "Resume", "Group_Ch. 1.cbz"));
        Assert.Equal(Enumerable.Range(1, 10).Select(i => $"http://pages.test/1/{i}.jpg"),
            zip.Entries.Where(e => e.Name.EndsWith(".jpg")).OrderBy(e => e.Name).Select(PageText));
        Assert.False(Directory.Exists(staging));
        Assert.Empty(second.Items);
    }

    [Fact]
    public async Task FailedChapterNeverAppearsAsAHalfWrittenChapter()
    {
        _source.PageCount = 6;
        var (mangaId, chapters) = await AddMangaAsync("Broken", Chapter("/chapter/1", "Ch. 1", 1));
        var queue = NewQueue(new FakeImages(_dir) { Fail = url => url.EndsWith("/4.jpg") });
        await queue.StartAsync();
        await queue.EnqueueAsync([chapters[0].ChapterId]);
        await Until(() => queue.StatusOf(mangaId, "/chapter/1").State == ChapterDownloadState.Failed, "the chapter fails");

        var mangaDir = Path.Combine(_root, "MangaDex (EN)", "Broken");
        Assert.Empty(Directory.GetFiles(mangaDir, "*.cbz"));
        File.WriteAllBytes(Path.Combine(mangaDir, "Group_Ch. 1.cbz_tmp"), [0x50, 0x4B, 0x03, 0x04]);
        await queue.RescanAsync();

        Assert.False(queue.IsDownloaded(mangaId, "/chapter/1"));
        Assert.Null(await queue.OpenLocalAsync(mangaId, "/chapter/1"));
        Assert.Contains("4.jpg", queue.Items.Single().Error);
    }

    [Fact]
    public async Task DiskScanAdoptsChaptersCopiedFromMihon()
    {
        var (mangaId, _) = await AddMangaAsync("Copied Title",
            Chapter("/chapter/1", "Ch. 1", 1), Chapter("/chapter/2", "Ch. 2", 2, scanlator: null), Chapter("/chapter/3", "Ch. 3", 3));
        var mangaDir = Path.Combine(_root, "MangaDex (EN)", "Copied Title");
        var folder = Directory.CreateDirectory(Path.Combine(mangaDir, "Ch. 2")).FullName;
        foreach (var page in new[] { "1", "2", "10" })
        {
            File.WriteAllBytes(Path.Combine(folder, $"{page}.jpg"), Jpeg("folder " + page));
        }
        var queue = NewQueue(new FakeImages(_dir));
        await queue.StartAsync();

        Assert.True(queue.IsDownloaded(mangaId, "/chapter/2"));
        Assert.False(queue.IsDownloaded(mangaId, "/chapter/1"));
        var pages = await queue.OpenLocalAsync(mangaId, "/chapter/2");
        Assert.NotNull(pages);
        Assert.Equal(["folder 1", "folder 2", "folder 10"], pages!.Select(p => Encoding.UTF8.GetString(File.ReadAllBytes(p.ImageUrl.LocalPath)[3..])));
        Assert.Equal(1, (await queue.GetDownloadedAsync()).Count(d => d.ChapterUrl == "/chapter/2"));
    }

    [Fact]
    public async Task DiskScanAdoptsHashedArchiveNames()
    {
        var (mangaId, _) = await AddMangaAsync("Hashed", Chapter("/chapter/1", "Ch. 1", 1));
        var mangaDir = Directory.CreateDirectory(Path.Combine(_root, "MangaDex (EN)", "Hashed")).FullName;
        using (var zip = ZipFile.Open(Path.Combine(mangaDir, "Group_Ch. 1_690668.cbz"), ZipArchiveMode.Create))
        {
            zip.CreateEntry("001.jpg").Open().Using(s => s.Write(Jpeg("archive 1")));
        }
        var queue = NewQueue(new FakeImages(_dir));
        await queue.StartAsync();

        var pages = await queue.OpenLocalAsync(mangaId, "/chapter/1");
        Assert.Equal(["archive 1"], pages!.Select(p => Encoding.UTF8.GetString(File.ReadAllBytes(p.ImageUrl.LocalPath)[3..])));
    }

    [Fact]
    public async Task ReadStateArrivingBySyncDeletesTheDownload()
    {
        _source.PageCount = 3;
        var (mangaId, chapters) = await AddMangaAsync("Synced", Chapter("/chapter/1", "Ch. 1", 1), Chapter("/chapter/2", "Ch. 2", 2));
        await _store.SetFavoriteAsync(mangaId, true);
        var manga = (await _store.GetMangaAsync(mangaId))!;
        var queue = NewQueue(new FakeImages(_dir), new DownloadOptions { Root = _root, DeleteAfterReadKeep = 0 });
        await queue.StartAsync();
        await queue.EnqueueAsync(chapters.Select(c => c.ChapterId).ToList());
        await Until(() => queue.IsDownloaded(mangaId, "/chapter/1") && queue.IsDownloaded(mangaId, "/chapter/2"), "both chapters download");
        var first = Path.Combine(_root, "MangaDex (EN)", "Synced", "Group_Ch. 1.cbz");
        Assert.True(File.Exists(first));

        using var hub = new SyncHub(_db, _store, preferredPort: 0);
        await hub.StartAsync();
        using var client = new HttpClient { BaseAddress = new Uri($"http://127.0.0.1:{hub.Status.Port}") };
        var phone = TestLib.Backup(TestLib.Manga(MangaDex, manga.Url, "Synced", version: 50, modified: 4_000_000_000_000,
            TestLib.Chapter("/chapter/1", "Ch. 1", 1, read: true, page: 2, version: 50, modified: 4_000_000_000_000),
            TestLib.Chapter("/chapter/2", "Ch. 2", 2, version: 0, modified: 0)));
        phone.BackupManga[0].Favorite = true;
        using var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge") { Content = new ByteArrayContent(phone.ToByteArray()) };
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        request.Headers.TryAddWithoutValidation("X-API-Token", hub.GetPairing().ApiKey);
        request.Headers.TryAddWithoutValidation("X-Device-ID", "phone");
        request.Headers.TryAddWithoutValidation("X-Device-Name", "Phone");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", "0");
        (await client.SendAsync(request)).EnsureSuccessStatusCode();

        await Until(() => !queue.IsDownloaded(mangaId, "/chapter/1"), "the chapter read on the phone is deleted");
        Assert.False(File.Exists(first));
        Assert.True(queue.IsDownloaded(mangaId, "/chapter/2"));
        Assert.True(File.Exists(Path.Combine(_root, "MangaDex (EN)", "Synced", "Group_Ch. 2.cbz")));
    }

    [Fact]
    public async Task MeteredConnectionHoldsTheQueueUntilItIsUnmetered()
    {
        _source.PageCount = 2;
        var (mangaId, chapters) = await AddMangaAsync("Metered", Chapter("/chapter/1", "Ch. 1", 1));
        var metered = true;
        var images = new FakeImages(_dir);
        var queue = NewQueue(images, new DownloadOptions { Root = _root, UnmeteredOnly = true },
            new DownloadEnvironment { IsMetered = () => metered });
        await queue.StartAsync();
        await queue.EnqueueAsync([chapters[0].ChapterId]);
        await Task.Delay(300);

        Assert.Equal(DownloadBlock.Metered, queue.Block);
        Assert.Empty(images.Fetched);
        Assert.Equal(ChapterDownloadState.Queued, queue.StatusOf(mangaId, "/chapter/1").State);

        metered = false;
        queue.NetworkChanged();
        await Until(() => queue.IsDownloaded(mangaId, "/chapter/1"), "the queue runs once the connection is unmetered");
        Assert.Equal(DownloadBlock.None, queue.Block);
    }

    [Fact]
    public async Task LowDiskSpaceStopsTheQueueWithoutFailingItems()
    {
        _source.PageCount = 2;
        var (mangaId, chapters) = await AddMangaAsync("Space", Chapter("/chapter/1", "Ch. 1", 1));
        long free = 512L << 20;
        var queue = NewQueue(new FakeImages(_dir), environment: new DownloadEnvironment { FreeBytes = _ => free });
        await queue.StartAsync();
        await queue.EnqueueAsync([chapters[0].ChapterId]);

        await Until(() => queue.Block == DownloadBlock.LowSpace, "the queue stops below 1 GB free");
        Assert.Equal(ChapterDownloadState.Queued, queue.StatusOf(mangaId, "/chapter/1").State);

        free = 5L << 30;
        queue.Resume();
        await Until(() => queue.IsDownloaded(mangaId, "/chapter/1"), "the queue resumes with space");
    }

    [Fact]
    public async Task NewChaptersArrivingBySyncAreDownloadedForChosenCategories()
    {
        _source.PageCount = 2;
        var (mangaId, _) = await AddMangaAsync("Auto", Chapter("/chapter/1", "Ch. 1", 1));
        var (otherId, _) = await AddMangaAsync("Elsewhere", Chapter("/chapter/1", "Ch. 1", 1));
        await _store.SetFavoriteAsync(mangaId, true);
        await _store.SetFavoriteAsync(otherId, true);
        var category = await _store.CreateCategoryAsync("Weekly");
        await _store.SetMangaCategoriesAsync(mangaId, [category.Uid]);
        var queue = NewQueue(new FakeImages(_dir), new DownloadOptions { Root = _root, AutoDownloadCategories = new HashSet<long> { category.Uid } });
        await queue.StartAsync();

        await _store.SyncChaptersFromSourceAsync(mangaId, [Chapter("/chapter/1", "Ch. 1", 1), Chapter("/chapter/2", "Ch. 2", 2)]);
        await _store.SyncChaptersFromSourceAsync(otherId, [Chapter("/chapter/1", "Ch. 1", 1), Chapter("/chapter/2", "Ch. 2", 2)]);

        await Until(() => queue.IsDownloaded(mangaId, "/chapter/2"), "the new chapter in the chosen category downloads");
        Assert.False(queue.IsDownloaded(mangaId, "/chapter/1"));
        Assert.Equal(ChapterDownloadStatus.None, queue.StatusOf(otherId, "/chapter/2"));
    }

    [Fact]
    public async Task CancelRemovesTheItemAndItsPartialPages()
    {
        _source.PageCount = 4;
        var (mangaId, chapters) = await AddMangaAsync("Cancel", Chapter("/chapter/1", "Ch. 1", 1));
        var queue = NewQueue(new FakeImages(_dir) { Stall = url => url.EndsWith("/4.jpg") });
        await queue.StartAsync();
        await queue.EnqueueAsync([chapters[0].ChapterId]);
        var staging = Path.Combine(_root, "MangaDex (EN)", "Cancel", "Group_Ch. 1_tmp");
        await Until(() => Directory.Exists(staging) && Directory.GetFiles(staging, "*.jpg").Length == 3, "three pages land");

        await queue.CancelAsync([queue.Items.Single().Id]);

        await Until(() => !Directory.Exists(staging), "the staging folder is removed");
        Assert.Equal(ChapterDownloadStatus.None, queue.StatusOf(mangaId, "/chapter/1"));
        Assert.Empty(queue.Items);
    }

    [Theory]
    [InlineData("Re:Zero / Starting Life in Another World?", "Re_Zero _ Starting Life in Another World_")]
    [InlineData("...Hello World. ", "Hello World")]
    [InlineData("<Oshi no Ko>", "_Oshi no Ko_")]
    [InlineData(" . ", "(invalid)")]
    [InlineData("Tab\there|now*", "Tab_here_now_")]
    public void MangaFoldersAreNamedLikeMihon(string title, string expected)
    {
        Assert.Equal(Path.Combine("MangaDex (EN)", expected, "Ch. 1.cbz"),
            DownloadQueue.ChapterPath("MangaDex", "en", title, "Ch. 1", null, "/chapter/1", []));
    }

    [Fact]
    public void LongTitlesAreCutAtA240ByteCharacterBoundary()
    {
        var path = DownloadQueue.ChapterPath("Weeb Central", "en", new string('あ', 100), "Ch. 1", null, "/chapter/1", []);
        Assert.Equal(Path.Combine("Weeb Central (EN)", new string('あ', 80), "Ch. 1.cbz"), path);
    }

    [Theory]
    [InlineData("Vol.1 Ch.2: \"End\"", "Group/A", "Group_A_Vol.1 Ch.2_ _End_.cbz")]
    [InlineData(" Chapter 1", null, "Chapter 1.cbz")]
    [InlineData("  ", null, "Chapter.cbz")]
    [InlineData("Chapter 1", "  ", "Chapter 1.cbz")]
    public void ChapterFilesAreNamedLikeMihon(string name, string? scanlator, string expected)
    {
        Assert.Equal(Path.Combine("MangaDex (EN)", "T", expected),
            DownloadQueue.ChapterPath("MangaDex", "en", "T", name, scanlator, "/chapter/1", []));
    }

    [Fact]
    public void ChaptersSharingANameGetMihonsUrlHashSuffix()
    {
        var path = DownloadQueue.ChapterPath("MangaDex", "en", "T", "Ch. 1", "Group", "/chapter/1",
            [("Ch. 1", "Group", "/chapter/1"), ("Ch. 1", "Group", "/chapter/b")]);
        Assert.Equal(Path.Combine("MangaDex (EN)", "T", "Group_Ch. 1_690668.cbz"), path);
    }

    private static string PageText(ZipArchiveEntry entry)
    {
        using var stream = entry.Open();
        using var memory = new MemoryStream();
        stream.CopyTo(memory);
        return Encoding.UTF8.GetString(memory.ToArray()[3..]);
    }

    internal static byte[] Jpeg(string text) => [0xFF, 0xD8, 0xFF, .. Encoding.UTF8.GetBytes(text)];

    private sealed class FakeSource : ISource
    {
        public int PageCount { get; set; } = 3;
        public long Id => MangaDex;
        public string Name => "MangaDex";
        public string Lang => "en";
        public bool SupportsLatest => false;

        public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) => Task.FromResult<IReadOnlyList<FilterSpec>>([]);
        public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();
        public Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default) => throw new NotSupportedException();

        public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default)
        {
            var chapter = chapterUrl.Split('/')[^1];
            return Task.FromResult<IReadOnlyList<PageRef>>(Enumerable.Range(1, PageCount)
                .Select(i => new PageRef(i - 1, new Uri($"http://pages.test/{chapter}/{i}.jpg"), $"{chapter}/{i}"))
                .ToList());
        }

        public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);
        public Uri WebUrl(string mangaUrl) => new("https://mangadex.org/title" + mangaUrl[6..]);
    }

    private sealed class FakeRegistry(ISource source) : ISourceRegistry
    {
        public IReadOnlyList<ISource> All => [source];
        public ISource? Get(long sourceId) => sourceId == source.Id ? source : null;
        public bool IsReadable(long sourceId) => sourceId == source.Id;
        public string DisplayName(long sourceId) => sourceId == source.Id ? source.Name : sourceId.ToString();
    }

    private sealed class FakeImages(string dir) : IImageCache
    {
        private readonly ConcurrentQueue<string> fetched = new();

        public Func<string, bool> Stall { get; init; } = _ => false;
        public Func<string, bool> Fail { get; init; } = _ => false;
        public IReadOnlyList<string> Fetched => fetched.ToList();

        public async Task<string> GetFileAsync(ImageRequest image, ImagePriority priority, CancellationToken ct = default)
        {
            var url = image.Url.AbsoluteUri;
            if (Stall(url))
            {
                await Task.Delay(Timeout.Infinite, ct);
            }
            if (Fail(url))
            {
                throw new HttpRequestException($"{url} returned 404");
            }
            fetched.Enqueue(url);
            var path = Path.Combine(dir, "cache", Guid.NewGuid().ToString("N") + ".img");
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            await File.WriteAllBytesAsync(path, Jpeg(url), ct);
            return path;
        }

        public void Prefetch(IEnumerable<ImageRequest> images)
        {
        }

        public void Invalidate(ImageRequest request)
        {
        }
    }
}

internal static class StreamExtensions
{
    public static void Using(this Stream stream, Action<Stream> write)
    {
        using (stream)
        {
            write(stream);
        }
    }
}
