using Google.Protobuf;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class PhoneBackupRoundTripTests : IDisposable
{
    private static readonly string? PhoneBackupPath = Environment.GetEnvironmentVariable("TSUZUKI_PHONE_BACKUP");

    private readonly ITestOutputHelper _output;
    private readonly List<LibraryDatabase> _databases = new();
    private readonly string _tempDir;

    public PhoneBackupRoundTripTests(ITestOutputHelper output)
    {
        _output = output;
        _tempDir = Path.Combine(TestLib.TempRoot, Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        foreach (var db in _databases) db.Dispose();
    }

    private LibraryDatabase FreshDb()
    {
        var (db, _) = TestLib.NewLibrary();
        _databases.Add(db);
        return db;
    }

    private sealed class FileTarget(string directory) : IBackupTarget
    {
        public string Id => "memory";
        public string DisplayName => "Memory";
        public string Location => directory;

        public async Task<string> WriteAsync(string fileName, Stream data, CancellationToken ct = default)
        {
            Directory.CreateDirectory(directory);
            var full = Path.Combine(directory, fileName);
            await using var output = File.Create(full);
            await data.CopyToAsync(output, ct);
            return full;
        }

        public Task<IReadOnlyList<BackupFileInfo>> ListAsync(CancellationToken ct = default) =>
            Task.FromResult((IReadOnlyList<BackupFileInfo>)Array.Empty<BackupFileInfo>());

        public Task<Stream> OpenReadAsync(string fileName, CancellationToken ct = default) =>
            Task.FromResult<Stream>(File.OpenRead(Path.Combine(directory, fileName)));

        public Task PruneAsync(int keep, CancellationToken ct = default) => Task.CompletedTask;
    }

    [Fact]
    public async Task PhoneBackupSurvivesRestoreCreateRestore()
    {
        if (PhoneBackupPath is null || !File.Exists(PhoneBackupPath)) Assert.Skip("set TSUZUKI_PHONE_BACKUP to a phone .tachibk to run this test");

        var firstDb = FreshDb();
        var service1 = new BackupService(firstDb);
        RestoreResult firstRestore;
        await using (var phone = File.OpenRead(PhoneBackupPath))
        {
            firstRestore = await service1.RestoreAsync(phone);
        }
        _output.WriteLine($"restore 1: manga added {firstRestore.MangaAdded}, updated {firstRestore.MangaUpdated}, chapters {firstRestore.ChaptersUpdated}, categories {firstRestore.CategoriesUpdated}");
        Assert.True(firstRestore.MangaAdded > 0);
        Assert.True(firstRestore.ChaptersUpdated > 0);

        var created = await service1.CreateAsync(new FileTarget(_tempDir));
        _output.WriteLine($"created backup: {created.MangaCount} manga, {created.SizeBytes} bytes");

        var secondDb = FreshDb();
        var service2 = new BackupService(secondDb);
        RestoreResult secondRestore;
        await using (var stream = File.OpenRead(created.Location))
        {
            secondRestore = await service2.RestoreAsync(stream);
        }
        _output.WriteLine($"restore 2: manga added {secondRestore.MangaAdded}, updated {secondRestore.MangaUpdated}, chapters {secondRestore.ChaptersUpdated}, categories {secondRestore.CategoriesUpdated}");
        Assert.True(secondRestore.MangaAdded > 0);

        var firstManga = MangaRows(firstDb);
        var secondManga = MangaRows(secondDb);
        Assert.Equal(firstManga.Count, secondManga.Count);
        foreach (var (key, firstRow) in firstManga)
        {
            Assert.True(secondManga.TryGetValue(key, out var secondRow), key);
            Assert.Equal(firstRow.Favorite, secondRow.Favorite);
            Assert.Equal(firstRow.Title, secondRow.Title);
            Assert.Equal(firstRow.Version, secondRow.Version);
            Assert.Equal(firstRow.Genres, secondRow.Genres);
        }

        var firstChapters = ChapterRows(firstDb);
        var secondChapters = ChapterRows(secondDb);
        var favouriteKeys = firstManga.Keys.ToHashSet();
        firstChapters = firstChapters
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        secondChapters = secondChapters
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        Assert.Equal(firstChapters.Count, secondChapters.Count);
        Assert.True(firstChapters.Count > 0);
        foreach (var (key, firstRow) in firstChapters)
        {
            Assert.True(secondChapters.TryGetValue(key, out var secondRow), key);
            Assert.Equal(firstRow.Read, secondRow.Read);
            Assert.Equal(firstRow.LastPageRead, secondRow.LastPageRead);
            Assert.Equal(firstRow.Bookmark, secondRow.Bookmark);
            Assert.Equal(firstRow.ChapterNumber, secondRow.ChapterNumber);
            Assert.Equal(firstRow.Version, secondRow.Version);
            Assert.Equal(firstRow.DateUpload, secondRow.DateUpload);
        }

        var readCount = firstChapters.Values.Count(c => c.Read);
        _output.WriteLine($"chapters compared: {firstChapters.Count}, of which read: {readCount}");
        Assert.Equal(readCount, secondChapters.Values.Count(c => c.Read));

        var firstHistory = HistoryEntries(firstDb);
        var secondHistory = HistoryEntries(secondDb);
        firstHistory = firstHistory
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        secondHistory = secondHistory
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        Assert.Equal(firstHistory.Count, secondHistory.Count);
        Assert.True(firstHistory.Count > 0);
        foreach (var (key, lastRead) in firstHistory)
        {
            Assert.True(secondHistory.TryGetValue(key, out var secondLastRead), key);
            Assert.Equal(lastRead, secondLastRead);
        }

        var firstCategories = CategoryRows(firstDb);
        var secondCategories = CategoryRows(secondDb);
        Assert.Equal(firstCategories.Count, secondCategories.Count);
        foreach (var (key, firstName) in firstCategories)
        {
            Assert.True(secondCategories.TryGetValue(key, out var secondName));
            Assert.Equal(firstName, secondName);
        }

        var firstMerged = MergedRefs(firstDb);
        var secondMerged = MergedRefs(secondDb);
        firstMerged = firstMerged
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        secondMerged = secondMerged
            .Where(kv => favouriteKeys.Contains(kv.Key.Split('\u001f')[0]))
            .ToDictionary(kv => kv.Key, kv => kv.Value);
        Assert.Equal(firstMerged.Count, secondMerged.Count);
        foreach (var (key, priority) in firstMerged)
        {
            Assert.True(secondMerged.TryGetValue(key, out var secondPriority), key);
            Assert.Equal(priority, secondPriority);
        }
        _output.WriteLine($"categories compared: {firstCategories.Count}, merged refs compared: {firstMerged.Count}, history entries compared: {firstHistory.Count}");

        var firstLibrary = await new SqliteLibraryStore(firstDb, _ => true).GetLibraryAsync();
        var secondLibrary = await new SqliteLibraryStore(secondDb, _ => true).GetLibraryAsync();
        Assert.Equal(firstLibrary.Count, secondLibrary.Count);
        Assert.Equal(firstLibrary.Sum(e => e.UnreadCount), secondLibrary.Sum(e => e.UnreadCount));
        _output.WriteLine($"library entries: {firstLibrary.Count}, total unread: {firstLibrary.Sum(e => e.UnreadCount)}");
    }

    private static Dictionary<string, (bool Favorite, string Title, long Version, string Genres)> MangaRows(LibraryDatabase db) =>
        db.ReadAsync(connection => connection.Query(
                "SELECT source, url, title, favorite, version, genres FROM manga WHERE favorite = 1")
            .ToDictionary(
                row => row.Long("source") + "|" + row.Text("url"),
                row => (row.Bool("favorite"), row.Text("title"), row.Long("version"), row.Text("genres"))))
            .GetAwaiter().GetResult();

    private static Dictionary<string, (bool Read, int LastPageRead, bool Bookmark, double ChapterNumber, long Version, long DateUpload)> ChapterRows(LibraryDatabase db) =>
        db.ReadAsync(connection => connection.Query("""
                SELECT m.source AS m_source, m.url AS m_url, ch.url, ch.read, ch.last_page_read, ch.bookmark,
                       ch.chapter_number, ch.version, ch.date_upload
                FROM chapter ch JOIN manga m ON m.id = ch.manga_id
                """)
            .ToDictionary(
                row => row.Long("m_source") + "|" + row.Text("m_url") + "\u001f" + row.Text("url"),
                row => (row.Bool("read"), (int)row.Long("last_page_read"), row.Bool("bookmark"),
                    row.Double("chapter_number"), row.Long("version"), row.Long("date_upload"))))
            .GetAwaiter().GetResult();

    private static Dictionary<string, long> HistoryEntries(LibraryDatabase db) =>
        db.ReadAsync(connection =>
        {
            var entries = new Dictionary<string, long>();
            foreach (var row in connection.Query("SELECT id, source, url, payload FROM manga"))
            {
                var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(row.Blob("payload"));
                foreach (var history in payload.History)
                {
                    entries[row.Long("source") + "|" + row.Text("url") + "\u001f" + history.Url] = history.LastRead;
                }
            }
            return entries;
        }).GetAwaiter().GetResult();

    private static Dictionary<long, string> CategoryRows(LibraryDatabase db) =>
        db.ReadAsync(connection => connection.Query("SELECT uid, name FROM category WHERE deleted = 0")
            .ToDictionary(row => row.Long("uid"), row => row.Text("name"))).GetAwaiter().GetResult();

    private static Dictionary<string, long> MergedRefs(LibraryDatabase db) =>
        db.ReadAsync(connection =>
        {
            var entries = new Dictionary<string, long>();
            foreach (var row in connection.Query("""
                    SELECT p.source AS p_source, p.url AS p_url, r.child_source, r.child_url, r.priority
                    FROM merged_ref r JOIN manga p ON p.id = r.parent_id
                    """))
            {
                entries[row.Long("p_source") + "|" + row.Text("p_url") + "\u001f"
                    + row.Long("child_source") + "|" + row.Text("child_url")] = row.Long("priority");
            }
            return entries;
        }).GetAwaiter().GetResult();
}
