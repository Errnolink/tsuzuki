using Google.Protobuf;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using static Tsuzuki.Core.Storage.SyncRows;

namespace Tsuzuki.Core.Storage;

public sealed class SqliteLibraryStore : ILibraryStore
{
    private static readonly TimeSpan BatchFlushInterval = TimeSpan.FromSeconds(2);

    private readonly LibraryDatabase _db;
    private readonly ItemStore _items;
    private readonly Func<long, bool> _readable;
    private readonly object _batchGate = new();
    private readonly HashSet<long> _batchedIds = new();
    private int _batchDepth;
    private Timer? _batchTimer;

    public SqliteLibraryStore(LibraryDatabase db, Func<long, bool> readable)
    {
        _db = db;
        _items = new ItemStore(db);
        _readable = readable;
    }

    internal ItemStore Items => _items;

    public event EventHandler<LibraryChangedEventArgs>? Changed;

    internal void RaiseChanged(IReadOnlyList<long> mangaIds, ChangeOrigin origin, string? deviceName,
        IReadOnlyList<ProgressHandoff> handoffs, bool isProgressOnly = false)
    {
        Changed?.Invoke(this, new LibraryChangedEventArgs(mangaIds, origin, deviceName, handoffs, isProgressOnly));
    }

    public IDisposable BeginBatch()
    {
        lock (_batchGate)
        {
            if (_batchDepth++ == 0)
            {
                _batchTimer = new Timer(_ => FlushBatch(), null, BatchFlushInterval, BatchFlushInterval);
            }
        }
        return new SourceBatch(this);
    }

    private void EndBatch()
    {
        Timer? timer = null;
        lock (_batchGate)
        {
            if (--_batchDepth == 0)
            {
                timer = _batchTimer;
                _batchTimer = null;
            }
        }
        if (timer is null) return;
        timer.Dispose();
        FlushBatch();
    }

    private void FlushBatch()
    {
        long[] ids;
        lock (_batchGate)
        {
            if (_batchedIds.Count == 0) return;
            ids = _batchedIds.ToArray();
            _batchedIds.Clear();
        }
        RaiseChanged(ids, ChangeOrigin.Source, null, Array.Empty<ProgressHandoff>());
    }

    private void RaiseSourceChanged(long mangaId)
    {
        lock (_batchGate)
        {
            if (_batchDepth > 0)
            {
                _batchedIds.Add(mangaId);
                return;
            }
        }
        RaiseChanged(new[] { mangaId }, ChangeOrigin.Source, null, Array.Empty<ProgressHandoff>());
    }

    private sealed class SourceBatch(SqliteLibraryStore store) : IDisposable
    {
        private int _disposed;

        public void Dispose()
        {
            if (Interlocked.Exchange(ref _disposed, 1) == 0) store.EndBatch();
        }
    }

    public Task<IReadOnlyList<LibraryEntry>> GetLibraryAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            FillFavouriteOwners(db);
            var parents = new List<(long Id, long Source, string Url, string Title, string? Thumbnail, long DateAdded)>();
            db.QueryEach("SELECT id, source, url, title, thumbnail_url, date_added FROM manga WHERE favorite = 1 ORDER BY title",
                r => parents.Add((r.GetInt64(0), r.GetInt64(1), r.GetString(2), r.GetString(3), r.IsDBNull(4) ? null : r.GetString(4), r.GetInt64(5))));
            var hiddenChildren = HiddenChildrenOfFavourites(db);
            var stats = new Dictionary<long, (int Unread, int Total, long LastRead)>();
            db.QueryEach("""
                    SELECT o.parent_id,
                           MAX(ch.last_read_at),
                           SUM(CASE WHEN ch.chapter_number < 0 THEN 1 ELSE 0 END),
                           SUM(CASE WHEN ch.chapter_number < 0 AND ch.read = 0 THEN 1 ELSE 0 END),
                           COUNT(DISTINCT CASE WHEN ch.chapter_number >= 0 THEN ch.chapter_number END),
                           COUNT(DISTINCT CASE WHEN ch.chapter_number >= 0 AND ch.read = 1 THEN ch.chapter_number END)
                    FROM fav_owners o CROSS JOIN chapter ch INDEXED BY idx_chapter_stats ON ch.manga_id = o.manga_id
                    GROUP BY o.parent_id
                    """,
                r =>
                {
                    var distinctNumbers = (int)r.GetInt64(4);
                    stats[r.GetInt64(0)] = (
                        (int)r.GetInt64(3) + distinctNumbers - (int)r.GetInt64(5),
                        (int)r.GetInt64(2) + distinctNumbers,
                        r.GetInt64(1));
                });
            var categoryUids = new Dictionary<long, List<long>>();
            db.QueryEach("SELECT manga_id, category_uid FROM manga_category WHERE manga_id IN (SELECT id FROM manga WHERE favorite = 1)",
                r =>
                {
                    var id = r.GetInt64(0);
                    if (!categoryUids.TryGetValue(id, out var uids))
                    {
                        categoryUids[id] = uids = [];
                    }
                    uids.Add(r.GetInt64(1));
                });
            var mergedParents = new HashSet<long>();
            db.QueryEach("SELECT DISTINCT parent_id FROM merged_ref WHERE parent_id IN (SELECT id FROM manga WHERE favorite = 1)",
                r => mergedParents.Add(r.GetInt64(0)));
            var entries = new List<LibraryEntry>(parents.Count);
            foreach (var (id, source, url, title, thumbnail, dateAdded) in parents)
            {
                if (hiddenChildren.Contains(id)) continue;
                stats.TryGetValue(id, out var stat);
                entries.Add(new LibraryEntry(
                    id,
                    source,
                    url,
                    title,
                    thumbnail,
                    stat.Unread,
                    stat.Total,
                    stat.LastRead,
                    dateAdded,
                    categoryUids.TryGetValue(id, out var uids) ? uids : [],
                    source.ToString() == MergedSourceId || mergedParents.Contains(id),
                    _readable(source)));
            }
            return (IReadOnlyList<LibraryEntry>)entries;
        });

    private static HashSet<long> HiddenChildrenOfFavourites(Microsoft.Data.Sqlite.SqliteConnection db)
    {
        var ids = new HashSet<long>();
        db.QueryEach("""
            SELECT m.id FROM manga m
            JOIN merged_ref r ON r.child_source = m.source AND r.child_url = m.url
            JOIN manga p ON p.id = r.parent_id
            WHERE p.favorite = 1 AND m.favorite = 1 AND m.id <> p.id
            """, r => ids.Add(r.GetInt64(0)));
        return ids;
    }

    public Task<bool> HasFavouritesAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => db.Scalar<long>("SELECT EXISTS(SELECT 1 FROM manga WHERE favorite = 1)") != 0);

    public Task<IReadOnlySet<string>> GetFavouriteKeysAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => (IReadOnlySet<string>)db.Query("""
                SELECT source, url FROM manga WHERE favorite = 1 AND id NOT IN (
                    SELECT c.id FROM manga c
                    JOIN merged_ref r ON r.child_source = c.source AND r.child_url = c.url
                    JOIN manga p ON p.id = r.parent_id
                    WHERE p.favorite = 1)
                """).Select(r => r.Long("source") + "|" + r.Text("url")).ToHashSet());

    public Task<IReadOnlyList<CategoryInfo>> GetCategoriesAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => (IReadOnlyList<CategoryInfo>)db.Query(
                "SELECT uid, name, sort FROM category WHERE deleted = 0 ORDER BY sort")
            .Select(r => new CategoryInfo(r.Long("uid"), r.Text("name"), (int)r.Long("sort"))).ToList());

    public Task<MangaDetails?> GetMangaAsync(long mangaId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var row = db.Query("""
                SELECT source, url, title, author, artist, description, genres, status, thumbnail_url, favorite, date_added, last_chapter_refresh
                FROM manga WHERE id = $id
                """, ("$id", mangaId)).FirstOrDefault();
            if (row == null) return null;
            var uids = db.Query("SELECT category_uid FROM manga_category WHERE manga_id = $id", ("$id", mangaId))
                .Select(r => r.Long("category_uid")).ToList();
            var refs = db.Query("SELECT child_source, child_url, is_info, priority FROM merged_ref WHERE parent_id = $id ORDER BY priority, child_source, child_url", ("$id", mangaId))
                .Select(r => new MergedRef(r.Long("child_source"), r.Text("child_url"), r.Bool("is_info"), (int)r.Long("priority")))
                .ToList();
            var genres = row.Text("genres").Split('\u001f', StringSplitOptions.RemoveEmptyEntries).ToList();
            return (MangaDetails?)new MangaDetails(
                mangaId, row.Long("source"), row.Text("url"), row.Text("title"),
                row.TextOrNull("author"), row.TextOrNull("artist"), row.TextOrNull("description"),
                genres, (MangaStatus)(int)row.Long("status"), row.TextOrNull("thumbnail_url"),
                row.Bool("favorite"), row.Long("date_added"), uids, refs, row.Long("last_chapter_refresh"));
        });

    public Task<long?> FindMangaIdAsync(long source, string url, CancellationToken ct = default) =>
        _db.ReadAsync(db => db.Scalar<long?>(
            "SELECT id FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", url)));

    private const string ChapterSelect = """
        SELECT ch.id, ch.manga_id, m.source, ch.url, ch.name, ch.scanlator, ch.chapter_number, ch.date_upload,
               ch.source_order, ch.read, ch.bookmark, ch.last_page_read, ch.page_count, ch.last_read_at, ch.external_url
        FROM chapter ch JOIN manga m ON m.id = ch.manga_id
        """;

    public Task<IReadOnlyList<ChapterRow>> GetChaptersAsync(long mangaId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var rows = new List<ChapterRow>();
            db.QueryEach(ChapterSelect + """
                 WHERE ch.manga_id IN (
                    SELECT $id UNION
                    SELECT c.id FROM merged_ref r JOIN manga c ON c.source = r.child_source AND c.url = r.child_url
                    WHERE r.parent_id = $id)
                """, r => rows.Add(ChapterRowOf(r)), ("$id", mangaId));
            rows.Sort((a, b) =>
            {
                var order = b.SourceOrder.CompareTo(a.SourceOrder);
                return order != 0 ? order : string.CompareOrdinal(b.Url, a.Url);
            });
            return (IReadOnlyList<ChapterRow>)rows;
        });

    public Task<ChapterRow?> GetChapterAsync(long chapterId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            ChapterRow? row = null;
            db.QueryEach(ChapterSelect + " WHERE ch.id = $id", r => row = ChapterRowOf(r), ("$id", chapterId));
            return row;
        });

    private ChapterRow ChapterRowOf(Microsoft.Data.Sqlite.SqliteDataReader r)
    {
        var source = r.GetInt64(2);
        return new ChapterRow(
            r.GetInt64(0),
            r.GetInt64(1),
            source,
            r.GetString(3),
            r.GetString(4),
            r.IsDBNull(5) ? null : r.GetString(5),
            r.GetDouble(6),
            r.GetInt64(7),
            (int)r.GetInt64(8),
            r.GetInt64(9) != 0,
            r.GetInt64(10) != 0,
            (int)r.GetInt64(11),
            r.IsDBNull(12) ? null : (int)r.GetInt64(12),
            r.GetInt64(13),
            r.IsDBNull(14) ? null : r.GetString(14),
            _readable(source));
    }

    public Task<IReadOnlyList<HistoryEntry>> GetHistoryAsync(int limit, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var entries = new List<HistoryEntry>();
            db.QueryEach("""
                SELECT m.id, m.title, m.thumbnail_url, ch.id, ch.name, ch.chapter_number, ch.last_page_read, ch.last_read_at, m.source, ch.page_count
                FROM chapter ch INDEXED BY idx_chapter_history JOIN manga m ON m.id = ch.manga_id
                WHERE ch.last_read_at > 0 AND (m.favorite = 1 OR m.id IN (
                    SELECT c.id FROM merged_ref r
                    JOIN manga p ON p.id = r.parent_id
                    JOIN manga c ON c.source = r.child_source AND c.url = r.child_url
                    WHERE p.favorite = 1))
                ORDER BY ch.last_read_at DESC, ch.id DESC
                LIMIT $lim
                """,
                r => entries.Add(new HistoryEntry(
                    r.GetInt64(0), r.GetString(1), r.IsDBNull(2) ? null : r.GetString(2),
                    r.GetInt64(3), r.GetString(4), r.GetDouble(5), (int)r.GetInt64(6), r.GetInt64(7), r.GetInt64(8),
                    r.IsDBNull(9) ? null : (int)r.GetInt64(9))),
                ("$lim", Math.Max(limit, 0)));
            return (IReadOnlyList<HistoryEntry>)entries;
        });

    public Task<IReadOnlyList<UpdateEntry>> GetUpdatesAsync(int limit, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            FillFavouriteOwners(db);
            var entries = new List<UpdateEntry>();
            foreach (var row in db.Query(
                         """
                         SELECT ch.id AS chapter_id, ch.name, ch.scanlator, ch.chapter_number, ch.date_fetch,
                                ch.date_upload, ch.read, ch.last_page_read, m.id AS manga_id, m.title, m.thumbnail_url, m.source
                         FROM chapter ch INDEXED BY idx_chapter_fetch JOIN manga m ON m.id = ch.manga_id
                         WHERE ch.manga_id IN (SELECT manga_id FROM fav_owners)
                         ORDER BY ch.date_fetch DESC, ch.date_upload DESC, ch.id ASC LIMIT $lim
                         """, ("$lim", limit)))
            {
                entries.Add(new UpdateEntry(
                    row.Long("manga_id"), row.Text("title"), row.TextOrNull("thumbnail_url"),
                    row.Long("chapter_id"), row.Text("name"), row.Double("chapter_number"),
                    row.TextOrNull("scanlator"), row.Long("date_fetch"), row.Long("date_upload"),
                    row.Bool("read"), (int)row.Long("last_page_read"), row.Long("source")));
            }
            return (IReadOnlyList<UpdateEntry>)entries;
        });

    public Task<IReadOnlyList<ContinueEntry>> GetContinueReadingAsync(int limit, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            FillFavouriteOwners(db);
            db.ExecuteNonQuery("""
                DROP TABLE IF EXISTS temp.fav_lastread;
                CREATE TEMP TABLE fav_lastread(
                    parent_id INTEGER NOT NULL PRIMARY KEY, chapter_id INTEGER NOT NULL, name TEXT NOT NULL,
                    chapter_number REAL NOT NULL, last_page_read INTEGER NOT NULL, page_count INTEGER,
                    last_read_at INTEGER NOT NULL, is_read INTEGER NOT NULL);
                INSERT INTO fav_lastread(parent_id, chapter_id, name, chapter_number, last_page_read, page_count, last_read_at, is_read)
                SELECT o.parent_id, ch.id, ch.name, ch.chapter_number, ch.last_page_read, ch.page_count, MAX(ch.last_read_at), ch.read
                FROM fav_owners o CROSS JOIN chapter ch ON ch.manga_id = o.manga_id
                WHERE ch.last_read_at > 0
                GROUP BY o.parent_id
                """);
            db.ExecuteNonQuery("""
                DROP TABLE IF EXISTS temp.fav_resume;
                CREATE TEMP TABLE fav_resume(
                    parent_id INTEGER NOT NULL PRIMARY KEY, chapter_id INTEGER NOT NULL, name TEXT NOT NULL,
                    chapter_number REAL NOT NULL, page_count INTEGER, last_read_at INTEGER NOT NULL);
                INSERT INTO fav_resume(parent_id, chapter_id, name, chapter_number, page_count, last_read_at)
                SELECT lr.parent_id, ch.id, ch.name, MIN(ch.chapter_number), ch.page_count, ch.last_read_at
                FROM fav_lastread lr
                CROSS JOIN fav_owners o ON o.parent_id = lr.parent_id
                CROSS JOIN chapter ch ON ch.manga_id = o.manga_id AND ch.read = 0 AND ch.chapter_number > lr.chapter_number
                WHERE lr.is_read = 1 AND lr.chapter_number >= 0
                GROUP BY lr.parent_id
                """);
            var entries = new List<ContinueEntry>();
            foreach (var row in db.Query("""
                    SELECT lr.parent_id AS manga_id, m.source AS source, m.title AS title, m.thumbnail_url AS thumbnail_url,
                           lr.chapter_id AS chapter_id, lr.name AS name, lr.chapter_number AS chapter_number,
                           lr.last_page_read AS last_page_read, lr.page_count AS page_count,
                           lr.last_read_at AS last_read_at,
                           r.chapter_id AS resume_id, r.name AS resume_name,
                           r.chapter_number AS resume_number, r.page_count AS resume_pages,
                           r.last_read_at AS resume_last_read_at
                    FROM fav_lastread lr
                    CROSS JOIN manga m ON m.id = lr.parent_id
                    LEFT JOIN fav_resume r ON r.parent_id = lr.parent_id
                    ORDER BY CASE WHEN r.chapter_id IS NULL THEN lr.last_read_at ELSE r.last_read_at END DESC, lr.parent_id ASC
                    LIMIT $lim
                    """, ("$lim", Math.Max(limit, 0))))
            {
                long chapterId;
                string name;
                double number;
                int lastPageRead;
                int? pageCount;
                long lastReadAt;
                if (row.LongOrNull("resume_id") is { } resumeId)
                {
                    chapterId = resumeId;
                    name = row.Text("resume_name");
                    number = row.Double("resume_number");
                    lastPageRead = 0;
                    pageCount = row.LongOrNull("resume_pages") is { } pages ? (int)pages : null;
                    lastReadAt = row.Long("resume_last_read_at");
                }
                else
                {
                    chapterId = row.Long("chapter_id");
                    name = row.Text("name");
                    number = row.Double("chapter_number");
                    lastPageRead = (int)row.Long("last_page_read");
                    pageCount = row.LongOrNull("page_count") is { } pages ? (int)pages : null;
                    lastReadAt = row.Long("last_read_at");
                }
                entries.Add(new ContinueEntry(
                    row.Long("manga_id"), row.Text("title"), row.TextOrNull("thumbnail_url"),
                    chapterId, name, number, lastPageRead, pageCount, lastReadAt, null, row.Long("source")));
            }
            return (IReadOnlyList<ContinueEntry>)entries;
        });

    private static void FillFavouriteOwners(Microsoft.Data.Sqlite.SqliteConnection db)
    {
        db.ExecuteNonQuery("DROP TABLE IF EXISTS temp.fav_owners");
        db.ExecuteNonQuery("""
            CREATE TEMP TABLE fav_owners(
                parent_id INTEGER NOT NULL, manga_id INTEGER NOT NULL,
                PRIMARY KEY(parent_id, manga_id)) WITHOUT ROWID;
            INSERT INTO fav_owners(parent_id, manga_id) SELECT id, id FROM manga WHERE favorite = 1;
            INSERT OR IGNORE INTO fav_owners(parent_id, manga_id)
            SELECT r.parent_id, c.id FROM merged_ref r
            JOIN manga c ON c.source = r.child_source AND c.url = r.child_url
            WHERE r.parent_id IN (SELECT id FROM manga WHERE favorite = 1);
            DELETE FROM fav_owners WHERE parent_id IN (
                SELECT c.id FROM manga c
                JOIN merged_ref r ON r.child_source = c.source AND r.child_url = c.url
                JOIN manga p ON p.id = r.parent_id
                WHERE p.favorite = 1 AND c.favorite = 1 AND c.id <> p.id)
            """);
    }

    public async Task<long> UpsertFromSourceAsync(long source, SManga manga, CancellationToken ct = default)
    {
        var (id, changed) = await _db.WriteAsync(db =>
        {
            var existing = db.Query("SELECT * FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", manga.Url))
                .FirstOrDefault();
            long id;
            var changed = false;
            var genres = string.Join('\u001f', manga.Genres);
            if (existing == null)
            {
                var payload = PayloadFromSource(source, manga, favourite: false);
                db.ExecuteNonQuery("""
                    INSERT INTO manga(source, url, title, author, artist, description, genres, status, thumbnail_url,
                        favorite, date_added, version, last_modified_at, seq, origin, payload, last_chapter_refresh)
                    VALUES ($s, $u, $t, $a, $ar, $d, $g, $st, $th, 0, 0, 0, 0, 0, NULL, $p, 0)
                    """,
                    ("$s", source), ("$u", manga.Url), ("$t", manga.Title),
                    ("$a", manga.Author), ("$ar", manga.Artist), ("$d", manga.Description),
                    ("$g", genres), ("$st", (long)manga.Status), ("$th", manga.ThumbnailUrl),
                    ("$p", payload));
                id = db.Scalar<long>("SELECT id FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", manga.Url));
            }
            else
            {
                id = existing.Long("id");
                var title = manga.Title.Length > 0 ? manga.Title : existing.Text("title");
                var thumbnail = manga.ThumbnailUrl ?? existing.TextOrNull("thumbnail_url");
                var author = existing.TextOrNull("author");
                var artist = existing.TextOrNull("artist");
                var description = existing.TextOrNull("description");
                var storedGenres = existing.Text("genres");
                var status = existing.Long("status");
                if (manga.Initialized)
                {
                    author = manga.Author ?? author;
                    artist = manga.Artist ?? artist;
                    description = manga.Description ?? description;
                    if (manga.Genres.Count > 0) storedGenres = genres;
                    if (manga.Status != MangaStatus.Unknown) status = (long)manga.Status;
                }
                changed = existing.Text("title") != title
                    || existing.TextOrNull("author") != author
                    || existing.TextOrNull("artist") != artist
                    || existing.TextOrNull("description") != description
                    || existing.Text("genres") != storedGenres
                    || existing.Long("status") != status
                    || existing.TextOrNull("thumbnail_url") != thumbnail;
                var stored = existing.Blob("payload");
                var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(stored);
                payload.Title = title;
                if (thumbnail != null) payload.ThumbnailUrl = thumbnail;
                if (manga.Initialized)
                {
                    if (author != null) payload.Author = author;
                    if (artist != null) payload.Artist = artist;
                    if (description != null) payload.Description = description;
                    if (manga.Genres.Count > 0 && !payload.Genre.SequenceEqual(manga.Genres))
                    {
                        payload.Genre.Clear();
                        payload.Genre.AddRange(manga.Genres);
                    }
                    payload.Status = (int)status;
                    payload.Initialized = true;
                }
                var bytes = payload.ToByteArray();
                db.ExecuteNonQuery("""
                    UPDATE manga SET title = $t, author = $a, artist = $ar, description = $d, genres = $g,
                        status = $st, thumbnail_url = $th, payload = $p, last_chapter_refresh = $now
                    WHERE id = $id
                    """,
                    ("$t", title), ("$a", author), ("$ar", artist), ("$d", description),
                    ("$g", storedGenres), ("$st", status), ("$th", thumbnail),
                    ("$p", changed || manga.Initialized ? bytes : stored), ("$now", NowMs()), ("$id", id));
            }
            if (changed || existing == null) _db.RenderCache.Invalidate();
            return (id, changed);
        }).ConfigureAwait(false);
        if (changed) RaiseSourceChanged(id);
        return id;
    }

    private static byte[] PayloadFromSource(long source, SManga manga, bool favourite)
    {
        var payload = new BackupManga
        {
            Source = source,
            Url = manga.Url,
            Title = manga.Title,
            Status = (int)manga.Status,
            Favorite = favourite,
            Initialized = manga.Initialized,
        };
        if (manga.Author != null) payload.Author = manga.Author;
        if (manga.Artist != null) payload.Artist = manga.Artist;
        if (manga.Description != null) payload.Description = manga.Description;
        if (manga.ThumbnailUrl != null) payload.ThumbnailUrl = manga.ThumbnailUrl;
        payload.Genre.AddRange(manga.Genres);
        return payload.ToByteArray();
    }

    public Task<ChapterSyncResult> SyncChaptersFromSourceAsync(long mangaId, IReadOnlyList<SChapter> chapters, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var manga = db.Query("SELECT source, url, favorite, version, origin FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault()
                ?? throw new InvalidOperationException("manga not found: " + mangaId);
            var syncable = IsSyncable(db, manga);
            var known = db.Query("""
                SELECT id, url, version, seq, origin, page_count, external_url, payload
                FROM chapter WHERE manga_id = $id
                """,
                ("$id", mangaId)).ToDictionary(r => r.Text("url"));
            var now = NowMs();
            var added = 0;
            var updated = 0;
            var seen = new HashSet<string>();
            long refreshSeq = 0;
            string? refreshOrigin = null;
            var taken = false;
            var quietChange = false;
            void TakeSeq()
            {
                if (taken) return;
                taken = true;
                if (!syncable) return;
                refreshSeq = _db.GetSeq(db) + 1;
                _db.SetSeq(db, refreshSeq);
                refreshOrigin = _db.GetMeta(db, "local_device_id");
            }
            var parser = new MessageParser<BackupChapter>(() => new BackupChapter());
            for (var index = 0; index < chapters.Count; index++)
            {
                var chapter = chapters[index];
                if (!seen.Add(chapter.Url)) continue;
                var number = (double)(float)chapter.ChapterNumber;
                if (!known.TryGetValue(chapter.Url, out var existing))
                {
                    var payload = new BackupChapter
                    {
                        Url = chapter.Url,
                        Name = chapter.Name,
                        ChapterNumber = (float)chapter.ChapterNumber,
                        DateUpload = chapter.DateUpload,
                        DateFetch = now,
                        SourceOrder = index,
                        Version = 1,
                        LastModifiedAt = NowSec(),
                    };
                    if (chapter.Scanlator != null) payload.Scanlator = chapter.Scanlator;
                    TakeSeq();
                    db.ExecuteNonQuery("""
                        INSERT INTO chapter(manga_id, url, name, scanlator, chapter_number, source_order, date_upload,
                            date_fetch, read, bookmark, last_page_read, last_read_at, page_count, external_url,
                            version, last_modified_at, seq, origin, payload)
                        VALUES ($m, $u, $n, $sc, $num, $ord, $up, $fetch, 0, 0, 0, 0, $pc, $ext, $v, $lm, $seq, $o, $p)
                        """,
                        ("$m", mangaId), ("$u", chapter.Url), ("$n", chapter.Name), ("$sc", chapter.Scanlator),
                        ("$num", number), ("$ord", (long)index), ("$up", chapter.DateUpload), ("$fetch", now),
                        ("$pc", chapter.PageCount), ("$ext", chapter.ExternalUrl),
                        ("$v", payload.Version), ("$lm", payload.LastModifiedAt), ("$seq", refreshSeq), ("$o", refreshOrigin),
                        ("$p", payload.ToByteArray()));
                    added++;
                    continue;
                }
                var stored = parser.ParseFrom(existing.Blob("payload"));
                var pageCount = chapter.PageCount is { } count ? count : existing.LongOrNull("page_count");
                var externalUrl = chapter.ExternalUrl ?? existing.TextOrNull("external_url");
                var sourceChanged = existing.Long("version") <= 0
                    || stored.Name != chapter.Name
                    || (stored.HasScanlator ? stored.Scanlator : null) != chapter.Scanlator
                    || stored.ChapterNumber != (float)chapter.ChapterNumber
                    || stored.DateUpload != chapter.DateUpload;
                if (!sourceChanged)
                {
                    var reordered = stored.SourceOrder != index;
                    if (reordered) stored.SourceOrder = index;
                    if (reordered || existing.LongOrNull("page_count") != pageCount || existing.TextOrNull("external_url") != externalUrl)
                    {
                        db.ExecuteNonQuery("UPDATE chapter SET source_order = $ord, page_count = $pc, external_url = $ext, payload = $p WHERE id = $id",
                            ("$ord", (long)index), ("$pc", pageCount), ("$ext", externalUrl),
                            ("$p", reordered ? stored.ToByteArray() : existing.Blob("payload")), ("$id", existing.Long("id")));
                        quietChange |= reordered;
                    }
                    continue;
                }
                stored.Name = chapter.Name;
                if (chapter.Scanlator != null) stored.Scanlator = chapter.Scanlator; else stored.ClearScanlator();
                stored.ChapterNumber = (float)chapter.ChapterNumber;
                stored.DateUpload = chapter.DateUpload;
                stored.SourceOrder = index;
                stored.Version = Math.Max(stored.Version, existing.Long("version")) + 1;
                stored.LastModifiedAt = NowSec();
                TakeSeq();
                db.ExecuteNonQuery("""
                    UPDATE chapter SET name = $n, scanlator = $sc, chapter_number = $num, source_order = $ord,
                        date_upload = $up, page_count = $pc, external_url = $ext, version = $v, last_modified_at = $lm,
                        seq = $seq, origin = $o, payload = $p WHERE id = $id
                    """,
                    ("$n", chapter.Name), ("$sc", chapter.Scanlator), ("$num", number), ("$ord", (long)index),
                    ("$up", chapter.DateUpload), ("$pc", pageCount), ("$ext", externalUrl),
                    ("$v", stored.Version), ("$lm", stored.LastModifiedAt),
                    ("$seq", syncable ? refreshSeq : existing.Long("seq")),
                    ("$o", syncable ? refreshOrigin : existing.TextOrNull("origin")),
                    ("$p", stored.ToByteArray()), ("$id", existing.Long("id")));
                updated++;
            }
            var removed = 0;
            foreach (var (url, row) in known)
            {
                if (seen.Contains(url)) continue;
                var payload = parser.ParseFrom(row.Blob("payload"));
                if (payload.Read || payload.LastPageRead > 0 || payload.Bookmark) continue;
                db.ExecuteNonQuery("DELETE FROM chapter WHERE id = $id", ("$id", row.Long("id")));
                removed++;
            }
            db.ExecuteNonQuery("UPDATE manga SET last_chapter_refresh = $now WHERE id = $id", ("$now", NowMs()), ("$id", mangaId));
            if (quietChange || added + updated + removed > 0) _db.RenderCache.Invalidate();
            return new ChapterSyncResult(added, updated, removed);
        }, result =>
        {
            if (result.Added + result.Updated + result.Removed > 0) RaiseSourceChanged(mangaId);
        });

    private static bool IsSyncable(Microsoft.Data.Sqlite.SqliteConnection db, SqliteRow manga) =>
        manga.Bool("favorite") || manga.Long("version") > 0 || manga.TextOrNull("origin") != null
        || db.Scalar<long>("SELECT EXISTS(SELECT 1 FROM merged_ref WHERE child_source = $s AND child_url = $u)",
            ("$s", manga.Long("source")), ("$u", manga.Text("url"))) > 0;

    public Task SetFavoriteAsync(long mangaId, bool favorite, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var row = db.Query("SELECT payload FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault()
                ?? throw new InvalidOperationException("manga not found: " + mangaId);
            var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(row.Blob("payload"));
            payload.Favorite = favorite;
            payload.FavoriteModifiedAt = NowSec();
            if (favorite) payload.DateAdded = NowMs();
            var (version, modified, seq, origin) = BumpVersion(db, payload.Version, payload.LastModifiedAt);
            payload.Version = version;
            payload.LastModifiedAt = modified;
            db.ExecuteNonQuery("""
                UPDATE manga SET favorite = $f, date_added = $da, version = $v, last_modified_at = $lm,
                    seq = $seq, origin = $o, payload = $p WHERE id = $id
                """,
                ("$f", favorite), ("$da", payload.DateAdded), ("$v", version), ("$lm", modified),
                ("$seq", seq), ("$o", origin), ("$p", payload.ToByteArray()), ("$id", mangaId));
            db.ExecuteNonQuery("UPDATE chapter SET seq = $seq, origin = $o WHERE manga_id = $id AND seq = 0",
                ("$seq", seq), ("$o", origin), ("$id", mangaId));
            return mangaId;
        }, id => RaiseChanged(new[] { id }, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>()));

    public Task SetChapterProgressAsync(long chapterId, int lastPageRead, bool read, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var chapter = db.Query("SELECT id, manga_id, url, read FROM chapter WHERE id = $id", ("$id", chapterId)).FirstOrDefault()
                ?? throw new InvalidOperationException("chapter not found: " + chapterId);
            var readChanged = (chapter.Long("read") == 1) != read;
            ApplyProgress(db, new[] { (chapter.Long("id"), chapter.Long("manga_id"), chapter.Text("url"), lastPageRead, read) });
            return (MangaId: chapter.Long("manga_id"), ReadChanged: readChanged);
        }, r => RaiseChanged(new[] { r.MangaId }, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>(), isProgressOnly: !r.ReadChanged));

    public Task SetChaptersReadAsync(IReadOnlyList<long> chapterIds, bool read, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var touched = new List<long>();
            var progress = new List<(long ChapterId, long MangaId, string Url, int LastPageRead, bool Read)>();
            foreach (var chapterId in chapterIds)
            {
                var chapter = db.Query("SELECT id, manga_id, url, last_page_read FROM chapter WHERE id = $id", ("$id", chapterId)).FirstOrDefault()
                    ?? throw new InvalidOperationException("chapter not found: " + chapterId);
                touched.Add(chapter.Long("manga_id"));
                progress.Add((chapter.Long("id"), chapter.Long("manga_id"), chapter.Text("url"), (int)chapter.Long("last_page_read"), read));
            }
            ApplyProgress(db, progress, stampHistory: read);
            return touched.Distinct().ToList();
        }, ids => RaiseChanged(ids, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>()));

    private void ApplyProgress(Microsoft.Data.Sqlite.SqliteConnection db,
        IEnumerable<(long ChapterId, long MangaId, string Url, int LastPageRead, bool Read)> updates, bool stampHistory = true)
    {
        var mangaPayloads = new Dictionary<long, BackupManga>();
        foreach (var (chapterId, mangaId, url, lastPageRead, read) in updates)
        {
            var row = db.Query("SELECT payload, version, last_modified_at, last_read_at FROM chapter WHERE id = $id", ("$id", chapterId)).First();
            var payload = new MessageParser<BackupChapter>(() => new BackupChapter()).ParseFrom(row.Blob("payload"));
            payload.LastPageRead = lastPageRead;
            payload.Read = read;
            var (version, modified, seq, origin) = BumpVersion(db, payload.Version, payload.LastModifiedAt);
            payload.Version = version;
            payload.LastModifiedAt = modified;
            db.ExecuteNonQuery("""
                UPDATE chapter SET read = $r, last_page_read = $p, last_read_at = $lr, version = $v,
                    last_modified_at = $lm, seq = $seq, origin = $o, payload = $pl WHERE id = $id
                """,
                ("$r", read), ("$p", lastPageRead), ("$lr", stampHistory ? NowMs() : row.Long("last_read_at")), ("$v", version), ("$lm", modified),
                ("$seq", seq), ("$o", origin), ("$pl", payload.ToByteArray()), ("$id", chapterId));
            if (!mangaPayloads.TryGetValue(mangaId, out var mangaPayload))
            {
                var mangaRow = db.Query("SELECT payload FROM manga WHERE id = $id", ("$id", mangaId)).First();
                mangaPayload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(mangaRow.Blob("payload"));
                mangaPayloads[mangaId] = mangaPayload;
            }
            if (stampHistory)
            {
                var now = NowMs();
                var history = mangaPayload.History.FirstOrDefault(h => h.Url == url);
                if (history == null)
                {
                    mangaPayload.History.Add(new BackupHistory { Url = url, LastRead = now });
                }
                else
                {
                    history.LastRead = now;
                }
            }
        }

        foreach (var (mangaId, mangaPayload) in mangaPayloads)
        {
            var (mVersion, mModified, mSeq, mOrigin) = BumpVersion(db, mangaPayload.Version, mangaPayload.LastModifiedAt);
            mangaPayload.Version = mVersion;
            mangaPayload.LastModifiedAt = mModified;
            db.ExecuteNonQuery("""
                UPDATE manga SET version = $v, last_modified_at = $lm, seq = $seq, origin = $o, payload = $p WHERE id = $id
                """,
                ("$v", mVersion), ("$lm", mModified), ("$seq", mSeq), ("$o", mOrigin),
                ("$p", mangaPayload.ToByteArray()), ("$id", mangaId));
        }
    }

    public Task SetMangaCategoriesAsync(long mangaId, IReadOnlyList<long> categoryUids, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            db.ExecuteNonQuery("DELETE FROM manga_category WHERE manga_id = $id", ("$id", mangaId));
            foreach (var uid in categoryUids)
            {
                db.ExecuteNonQuery("INSERT OR IGNORE INTO manga_category(manga_id, category_uid) VALUES ($m, $c)",
                    ("$m", mangaId), ("$c", uid));
            }
            BumpManga(db, mangaId);
            return mangaId;
        }, id => RaiseChanged(new[] { id }, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>()));

    public Task<CategoryInfo> CreateCategoryAsync(string name, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var uid = db.Scalar<long>("SELECT COALESCE(MAX(uid), 0) FROM category") + 1;
            var order = db.Scalar<long>("SELECT COALESCE(MAX(sort), -1) FROM category WHERE deleted = 0") + 1;
            var (version, modified, seq, origin) = BumpVersion(db, 0, 0);
            var payload = new BackupCategory
            {
                Name = name,
                Order = order,
                Uid = uid,
                Version = version,
                LastModifiedAt = modified,
            };
            db.ExecuteNonQuery("""
                INSERT INTO category(uid, name, sort, flags, version, last_modified_at, deleted, seq, origin, payload)
                VALUES ($uid, $name, $sort, 0, $v, $lm, 0, $seq, $o, $p)
                """,
                ("$uid", uid), ("$name", name), ("$sort", order), ("$v", version), ("$lm", modified),
                ("$seq", seq), ("$o", origin), ("$p", payload.ToByteArray()));
            return new CategoryInfo(uid, name, (int)order);
        }, _ => RaiseChanged(Array.Empty<long>(), ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>()));

    internal (long Version, long ModifiedAt, long Seq, string Origin) BumpVersion(
        Microsoft.Data.Sqlite.SqliteConnection db, long currentVersion, long currentModifiedAt)
    {
        var seq = _db.GetSeq(db) + 1;
        _db.SetSeq(db, seq);
        _db.RenderCache.Invalidate();
        return (currentVersion + 1, NowSec(), seq, _db.GetMeta(db, "local_device_id"));
    }

    public Task<IReadOnlyList<BackupTracking>> GetTrackingAsync(long mangaId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var row = db.Query("SELECT payload FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault();
            if (row == null) return (IReadOnlyList<BackupTracking>)[];
            return BackupManga.Parser.ParseFrom(row.Blob("payload")).Tracking.Select(t => t.Clone()).ToList();
        });

    public Task<bool> EditTrackingAsync(long mangaId, Func<Google.Protobuf.Collections.RepeatedField<BackupTracking>, bool> edit, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var row = db.Query("SELECT payload FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault()
                ?? throw new InvalidOperationException("manga not found: " + mangaId);
            var payload = BackupManga.Parser.ParseFrom(row.Blob("payload"));
            if (!edit(payload.Tracking)) return false;
            var (version, modified, seq, origin) = BumpVersion(db, payload.Version, payload.LastModifiedAt);
            payload.Version = version;
            payload.LastModifiedAt = modified;
            db.ExecuteNonQuery("UPDATE manga SET version = $v, last_modified_at = $lm, seq = $seq, origin = $o, payload = $p WHERE id = $id",
                ("$v", version), ("$lm", modified), ("$seq", seq), ("$o", origin), ("$p", payload.ToByteArray()), ("$id", mangaId));
            return true;
        }, changed =>
        {
            if (changed) RaiseChanged(new[] { mangaId }, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>());
        });

    internal void BumpManga(Microsoft.Data.Sqlite.SqliteConnection db, long mangaId)
    {
        var row = db.Query("SELECT payload, version, last_modified_at FROM manga WHERE id = $id", ("$id", mangaId)).First();
        var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(row.Blob("payload"));
        var (version, modified, seq, origin) = BumpVersion(db, payload.Version, payload.LastModifiedAt);
        payload.Version = version;
        payload.LastModifiedAt = modified;
        db.ExecuteNonQuery("UPDATE manga SET version = $v, last_modified_at = $lm, seq = $seq, origin = $o, payload = $p WHERE id = $id",
            ("$v", version), ("$lm", modified), ("$seq", seq), ("$o", origin), ("$p", payload.ToByteArray()), ("$id", mangaId));
    }

    internal static long NowSec() => DateTimeOffset.UtcNow.ToUnixTimeSeconds();

    internal static long NowMs() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    public Task FlushAsync(CancellationToken ct = default) => _db.FlushAsync();
}
