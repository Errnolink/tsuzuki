using Microsoft.Data.Sqlite;

namespace Tsuzuki.Core.Storage;

public sealed class LibraryDatabase : IDisposable
{
    private readonly SemaphoreSlim _writeGate = new(1, 1);
    private readonly string _connectionString;

    public LibraryDatabase(string path)
    {
        Path = path;
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = path,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Pooling = true,
        }.ToString();
    }

    public string Path { get; }

    internal long WriteEpoch => RenderCache.Epoch;

    internal RenderCache RenderCache { get; } = new();

    internal object SyncGate { get; } = new();

    internal Task? WarmTask;

    public void Initialise()
    {
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(System.IO.Path.GetFullPath(Path))!);
        using var db = Open();
        db.ExecuteNonQuery("PRAGMA journal_mode = WAL");
        db.ExecuteNonQuery("PRAGMA synchronous = NORMAL");
        db.ExecuteNonQuery("PRAGMA foreign_keys = ON");
        db.ExecuteNonQuery(Schema);
        db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ('schema_version','1') ON CONFLICT(key) DO NOTHING");
        if (db.Scalar<string?>("SELECT value FROM meta WHERE key = 'read_stamp_repair'") is null)
        {
            db.ExecuteNonQuery(
                "UPDATE chapter SET last_read_at = last_modified_at * 1000 WHERE read = 1 AND last_read_at = 0 AND last_modified_at > 0");
            db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ('read_stamp_repair','1')");
        }
        if (db.Scalar<string?>("SELECT value FROM meta WHERE key = 'planner_stats'") is null)
        {
            db.ExecuteNonQuery("PRAGMA optimize = 0x10002");
            db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ('planner_stats','1')");
        }
    }

    public SqliteConnection Open()
    {
        var db = new SqliteConnection(_connectionString);
        db.Open();
        db.ExecuteNonQuery("PRAGMA foreign_keys = ON; PRAGMA synchronous = NORMAL; PRAGMA mmap_size = 268435456; PRAGMA temp_store = MEMORY");
        return db;
    }

    public async Task<T> ReadAsync<T>(Func<SqliteConnection, T> fn)
    {
        return await Task.Run(() =>
        {
            using var db = Open();
            return fn(db);
        });
    }

    public async Task<T> WriteAsync<T>(Func<SqliteConnection, T> fn, Action<T>? afterCommit = null)
    {
        T result;
        await _writeGate.WaitAsync().ConfigureAwait(false);
        try
        {
            result = await Task.Run(() =>
            {
                var epoch = RenderCache.Epoch;
                using var db = Open();
                using var tx = db.BeginTransaction();
                var value = fn(db);
                tx.Commit();
                if (RenderCache.Epoch != epoch) RenderCache.Invalidate();
                return value;
            }).ConfigureAwait(false);
        }
        finally
        {
            _writeGate.Release();
        }
        afterCommit?.Invoke(result);
        return result;
    }

    public async Task FlushAsync()
    {
        await _writeGate.WaitAsync().ConfigureAwait(false);
        try
        {
            using var db = Open();
            db.ExecuteNonQuery("PRAGMA optimize");
            db.ExecuteNonQuery("PRAGMA wal_checkpoint(TRUNCATE)");
        }
        catch
        {
        }
        finally
        {
            _writeGate.Release();
        }
    }

    public long GetSeq(SqliteConnection db) =>
        db.Scalar<long?>("SELECT value FROM meta WHERE key = 'hub_seq'") ?? 0;

    public void SetSeq(SqliteConnection db, long seq) =>
        db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ('hub_seq', $v) ON CONFLICT(key) DO UPDATE SET value = $v",
            ("$v", seq.ToString()));

    public string GetMeta(SqliteConnection db, string key) =>
        db.Scalar<string?>("SELECT value FROM meta WHERE key = $k", ("$k", key)) ?? "";

    public void SetMeta(SqliteConnection db, string key, string value) =>
        db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ($k, $v) ON CONFLICT(key) DO UPDATE SET value = $v",
            ("$k", key), ("$v", value));

    public Task<string> GetMetaAsync(string key) => ReadAsync(db => GetMeta(db, key));

    public Task SetMetaAsync(string key, string value) => WriteAsync(db => { SetMeta(db, key, value); return 0; });

    public void Dispose()
    {
        _writeGate.Dispose();
    }

    private const string Schema = """
        CREATE TABLE IF NOT EXISTS meta(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS manga(
            id INTEGER PRIMARY KEY,
            source INTEGER NOT NULL,
            url TEXT NOT NULL,
            title TEXT NOT NULL,
            author TEXT,
            artist TEXT,
            description TEXT,
            genres TEXT NOT NULL DEFAULT '',
            status INTEGER NOT NULL DEFAULT 0,
            thumbnail_url TEXT,
            favorite INTEGER NOT NULL DEFAULT 0,
            date_added INTEGER NOT NULL DEFAULT 0,
            version INTEGER NOT NULL DEFAULT 0,
            last_modified_at INTEGER NOT NULL DEFAULT 0,
            seq INTEGER NOT NULL DEFAULT 0,
            origin TEXT,
            payload BLOB NOT NULL,
            last_chapter_refresh INTEGER NOT NULL DEFAULT 0,
            UNIQUE(source, url)
        );
        CREATE INDEX IF NOT EXISTS idx_manga_seq ON manga(seq);
        CREATE TABLE IF NOT EXISTS chapter(
            id INTEGER PRIMARY KEY,
            manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
            url TEXT NOT NULL,
            name TEXT NOT NULL,
            scanlator TEXT,
            chapter_number REAL NOT NULL DEFAULT -1,
            source_order INTEGER NOT NULL DEFAULT 0,
            date_upload INTEGER NOT NULL DEFAULT 0,
            date_fetch INTEGER NOT NULL DEFAULT 0,
            read INTEGER NOT NULL DEFAULT 0,
            bookmark INTEGER NOT NULL DEFAULT 0,
            last_page_read INTEGER NOT NULL DEFAULT 0,
            last_read_at INTEGER NOT NULL DEFAULT 0,
            page_count INTEGER,
            external_url TEXT,
            version INTEGER NOT NULL DEFAULT 0,
            last_modified_at INTEGER NOT NULL DEFAULT 0,
            seq INTEGER NOT NULL DEFAULT 0,
            origin TEXT,
            payload BLOB NOT NULL,
            UNIQUE(manga_id, url)
        );
        CREATE INDEX IF NOT EXISTS idx_chapter_manga ON chapter(manga_id);
        CREATE INDEX IF NOT EXISTS idx_chapter_seq ON chapter(seq);
        CREATE INDEX IF NOT EXISTS idx_chapter_read ON chapter(manga_id, read, chapter_number);
        CREATE INDEX IF NOT EXISTS idx_chapter_fetch ON chapter(date_fetch DESC, date_upload DESC, id);
        CREATE INDEX IF NOT EXISTS idx_chapter_stats ON chapter(manga_id, last_read_at, chapter_number, read);
        CREATE INDEX IF NOT EXISTS idx_chapter_history ON chapter(last_read_at) WHERE last_read_at > 0;
        CREATE INDEX IF NOT EXISTS idx_manga_favorite ON manga(favorite);
        CREATE TABLE IF NOT EXISTS category(
            id INTEGER PRIMARY KEY,
            uid INTEGER NOT NULL UNIQUE,
            name TEXT NOT NULL,
            sort INTEGER NOT NULL DEFAULT 0,
            flags INTEGER NOT NULL DEFAULT 0,
            version INTEGER NOT NULL DEFAULT 0,
            last_modified_at INTEGER NOT NULL DEFAULT 0,
            deleted INTEGER NOT NULL DEFAULT 0,
            seq INTEGER NOT NULL DEFAULT 0,
            origin TEXT,
            payload BLOB NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_category_name ON category(name);
        CREATE TABLE IF NOT EXISTS manga_category(
            manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
            category_uid INTEGER NOT NULL,
            PRIMARY KEY(manga_id, category_uid)
        );
        CREATE TABLE IF NOT EXISTS merged_ref(
            parent_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
            child_source INTEGER NOT NULL,
            child_url TEXT NOT NULL,
            is_info INTEGER NOT NULL DEFAULT 0,
            priority INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(parent_id, child_source, child_url)
        );
        CREATE INDEX IF NOT EXISTS idx_merged_child ON merged_ref(child_source, child_url);
        CREATE TABLE IF NOT EXISTS section(
            kind TEXT NOT NULL,
            key TEXT NOT NULL,
            payload BLOB NOT NULL,
            seq INTEGER NOT NULL DEFAULT 0,
            origin TEXT,
            PRIMARY KEY(kind, key)
        );
        CREATE INDEX IF NOT EXISTS idx_section_seq ON section(seq);
        CREATE TABLE IF NOT EXISTS device(
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            cursor INTEGER NOT NULL DEFAULT 0,
            last_seen INTEGER NOT NULL DEFAULT 0,
            protocol INTEGER NOT NULL DEFAULT 2
        );
        """;
}
