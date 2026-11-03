using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

internal static class TestLib
{
    public static string TempRoot { get; } = Path.Combine(Path.GetTempPath(), "tsuzuki-tests");

    public static BackupMessage BackupOf(Tsuzuki.Core.Sync.EngineResult result) =>
        result.Wire is { Length: > 0 } wire ? BackupMessage.Parser.ParseFrom(wire) : result.Backup;

    public static (LibraryDatabase Db, SqliteLibraryStore Store) NewLibrary()
    {
        var dir = Path.Combine(TempRoot, Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        var db = new LibraryDatabase(Path.Combine(dir, "library.db"));
        db.Initialise();
        db.SetMetaAsync("local_device_id", "pc-" + Guid.NewGuid().ToString("N")[..8]).GetAwaiter().GetResult();
        return (db, new SqliteLibraryStore(db, _ => true));
    }

    public static BackupMessage Backup(params BackupManga[] manga)
    {
        var backup = new BackupMessage();
        backup.BackupManga.AddRange(manga);
        return backup;
    }

    public static BackupMessage WithCategories(this BackupMessage backup, params BackupCategory[] categories)
    {
        backup.BackupCategories.AddRange(categories);
        return backup;
    }

    public static BackupManga Manga(long source, string url, string title = "Manga", int version = 0,
        long modified = 0, params BackupChapter[] chapters)
    {
        var manga = new BackupManga
        {
            Source = source,
            Url = url,
            Title = title,
            Version = version,
            LastModifiedAt = modified,
        };
        manga.Chapters.AddRange(chapters);
        return manga;
    }

    public static BackupManga WithCategories(this BackupManga manga, params long[] orders)
    {
        manga.Categories.AddRange(orders);
        return manga;
    }

    public static BackupChapter Chapter(string url, string name = "Chapter", double number = -1,
        bool read = false, int page = 0, int version = 0, long modified = 0, long order = 0)
    {
        return new BackupChapter
        {
            Url = url,
            Name = name,
            ChapterNumber = (float)number,
            Read = read,
            LastPageRead = page,
            Version = version,
            LastModifiedAt = modified,
            SourceOrder = order,
        };
    }

    public static BackupCategory Category(string name, long uid, long order, int version = 0, long modified = 0)
    {
        return new BackupCategory
        {
            Name = name,
            Uid = uid,
            Order = order,
            Version = version,
            LastModifiedAt = modified,
        };
    }

    public static BackupManga FindManga(BackupMessage backup, long source, string url) =>
        backup.BackupManga.First(m => m.Source == source && m.Url == url);

    public static bool HasManga(BackupMessage backup, long source, string url) =>
        backup.BackupManga.Any(m => m.Source == source && m.Url == url);

    public static byte[] Gzip(byte[] data)
    {
        using var buffer = new MemoryStream();
        using (var gzip = new System.IO.Compression.GZipStream(buffer, System.IO.Compression.CompressionLevel.Optimal))
        {
            gzip.Write(data);
        }
        return buffer.ToArray();
    }
}
