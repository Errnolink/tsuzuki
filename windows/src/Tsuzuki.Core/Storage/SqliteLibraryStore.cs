using Google.Protobuf;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using static Tsuzuki.Core.Storage.SyncRows;

namespace Tsuzuki.Core.Storage;

public sealed class SqliteLibraryStore : ILibraryStore
{
    private readonly LibraryDatabase _db;
    private readonly ItemStore _items;
    private readonly Func<long, bool> _readable;

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

    public Task<IReadOnlyList<LibraryEntry>> GetLibraryAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            FillFavouriteOwners(db);
            var parents = db.Query(
                "SELECT id, source, url, title, thumbnail_url, date_added FROM manga WHERE favorite = 1 ORDER BY title");
            var hiddenChildren = HiddenChildrenOfFavourites(db);
            var stats = new Dictionary<long, (int Unread, int Total, long LastRead)>();
            foreach (var row in db.Query("""
                    SELECT o.parent_id AS manga_id,
                           MAX(ch.last_read_at) AS last_read,
                           SUM(CASE WHEN ch.chapter_number < 0 THEN 1 ELSE 0 END) AS unnumbered,
                           SUM(CASE WHEN ch.chapter_number < 0 AND ch.read = 0 THEN 1 ELSE 0 END) AS unnumbered_unread
                    FROM fav_owners o JOIN chapter ch ON ch.manga_id = o.manga_id
                    GROUP BY o.parent_id
                    """))
            {
                stats[row.Long("manga_id")] = ((int)row.Long("unnumbered_unread"), (int)row.Long("unnumbered"), row.Long("last_read"));
            }
            foreach (var row in db.Query("""
                    SELECT pid AS manga_id,
                           SUM(CASE WHEN anyread = 1 THEN 1 ELSE 0 END) AS read_numbers,
                           COUNT(*) AS distinct_numbers
                    FROM (
                        SELECT o.parent_id AS pid, ch.chapter_number AS num, MAX(ch.read) AS anyread
                        FROM fav_owners o JOIN chapter ch ON ch.manga_id = o.manga_id
                        WHERE ch.chapter_number >= 0
                        GROUP BY o.parent_id, ch.chapter_number
                    )
                    GROUP BY pid
                    """))
            {
                var (unread, total, lastRead) = stats.TryGetValue(row.Long("manga_id"), out var stat)
                    ? stat
                    : (0, 0, 0L);
                var distinctNumbers = (int)row.Long("distinct_numbers");
                stats[row.Long("manga_id")] = (
                    unread + distinctNumbers - (int)row.Long("read_numbers"),
                    total + distinctNumbers,
                    lastRead);
            }
            var categoryUids = new Dictionary<long, List<long>>();
            foreach (var row in db.Query(
                         "SELECT manga_id, category_uid FROM manga_category WHERE manga_id IN (SELECT id FROM manga WHERE favorite = 1)"))
            {
                if (!categoryUids.TryGetValue(row.Long("manga_id"), out var uids))
                {
                    categoryUids[row.Long("manga_id")] = uids = [];
                }
                uids.Add(row.Long("category_uid"));
            }
            var mergedChildren = new Dictionary<long, List<long>>();
            foreach (var row in db.Query("""
                    SELECT r.parent_id AS parent_id, c.id AS child_id FROM merged_ref r
                    LEFT JOIN manga c ON c.source = r.child_source AND c.url = r.child_url
                    WHERE r.parent_id IN (SELECT id FROM manga WHERE favorite = 1)
                    """))
            {
                if (!mergedChildren.TryGetValue(row.Long("parent_id"), out var children))
                {
                    mergedChildren[row.Long("parent_id")] = children = [];
                }
                if (row.LongOrNull("child_id") is { } childId)
                {
                    children.Add(childId);
                }
            }
            var entries = new List<LibraryEntry>();
            foreach (var row in parents)
            {
                var id = row.Long("id");
                if (hiddenChildren.Contains(id)) continue;
                stats.TryGetValue(id, out var stat);
                var hasRefs = mergedChildren.TryGetValue(id, out var children);
                entries.Add(new LibraryEntry(
                    id,
                    row.Long("source"),
                    row.Text("url"),
                    row.Text("title"),
                    row.TextOrNull("thumbnail_url"),
                    stat.Unread,
                    stat.Total,
                    stat.LastRead,
                    row.Long("date_added"),
                    categoryUids.TryGetValue(id, out var uids) ? uids : [],
                    row.Long("source").ToString() == MergedSourceId || hasRefs,
                    _readable(row.Long("source"))));
            }
            return (IReadOnlyList<LibraryEntry>)entries;
        });

    private HashSet<long> HiddenChildrenOfFavourites(Microsoft.Data.Sqlite.SqliteConnection db) =>
        db.Query("""
            SELECT m.id FROM manga m
            JOIN merged_ref r ON r.child_source = m.source AND r.child_url = m.url
            JOIN manga p ON p.id = r.parent_id
            WHERE p.favorite = 1 AND m.favorite = 1 AND m.id <> p.id
            """).Select(r => r.Long("id")).ToHashSet();

    public Task<IReadOnlySet<string>> GetFavouriteKeysAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => (IReadOnlySet<string>)db.Query("""
                SELECT source, url FROM manga WHERE favorite = 1 AND id NOT IN (
                    SELECT c.id FROM manga c
                    JOIN merged_ref r ON r.child_source = c.source AND r.child_url = c.url
                    JOIN manga p ON p.id = r.parent_id
                    WHERE p.favorite = 1)
                """).Select(r => r.Long("source") + "|" + r.Text("url")).ToHashSet());

    private List<ChapterRow> LoadChaptersOf(Microsoft.Data.Sqlite.SqliteConnection db, IReadOnlyList<long> mangaIds)
    {
        var rows = new List<(SqliteRow Row, long OwnerSource)>();
        foreach (var chunk in ChunkIds(mangaIds))
        {
            var (sql, parameters) = InFilterList("ch.manga_id", chunk);
            foreach (var row in db.Query(
                         "SELECT ch.*, m.source AS owner_source FROM chapter ch JOIN manga m ON m.id = ch.manga_id WHERE " + sql,
                         parameters))
            {
                rows.Add((row, row.Long("owner_source")));
            }
        }
        return rows.Select(tuple => ChapterRowOf(tuple.Row, tuple.OwnerSource, _readable(tuple.OwnerSource))).ToList();
    }

    public Task<IReadOnlyList<CategoryInfo>> GetCategoriesAsync(CancellationToken ct = default) =>
        _db.ReadAsync(db => (IReadOnlyList<CategoryInfo>)db.Query(
                "SELECT uid, name, sort FROM category WHERE deleted = 0 ORDER BY sort")
            .Select(r => new CategoryInfo(r.Long("uid"), r.Text("name"), (int)r.Long("sort"))).ToList());

    public Task<MangaDetails?> GetMangaAsync(long mangaId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var row = db.Query("SELECT * FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault();
            if (row == null) return null;
            var uids = db.Query("SELECT category_uid FROM manga_category WHERE manga_id = $id", ("$id", mangaId))
                .Select(r => r.Long("category_uid")).ToList();
            var refs = db.Query("SELECT * FROM merged_ref WHERE parent_id = $id ORDER BY priority", ("$id", mangaId))
                .Select(r => new MergedRef(r.Long("child_source"), r.Text("child_url"), r.Bool("is_info"), (int)r.Long("priority")))
                .ToList();
            var genres = row.Text("genres").Split('\u001f', StringSplitOptions.RemoveEmptyEntries).ToList();
            return (MangaDetails?)new MangaDetails(
                mangaId, row.Long("source"), row.Text("url"), row.Text("title"),
                row.TextOrNull("author"), row.TextOrNull("artist"), row.TextOrNull("description"),
                genres, (MangaStatus)(int)row.Long("status"), row.TextOrNull("thumbnail_url"),
                row.Bool("favorite"), row.Long("date_added"), uids, refs);
        });

    public Task<long?> FindMangaIdAsync(long source, string url, CancellationToken ct = default) =>
        _db.ReadAsync(db => db.Scalar<long?>(
            "SELECT id FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", url)));

    public Task<IReadOnlyList<ChapterRow>> GetChaptersAsync(long mangaId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var ids = MergedChapterOwnerIds(db, mangaId);
            return (IReadOnlyList<ChapterRow>)LoadChaptersOf(db, ids)
                .OrderByDescending(c => c.SourceOrder).ThenByDescending(c => c.Url, StringComparer.Ordinal)
                .ToList();
        });

    private List<long> MergedChapterOwnerIds(Microsoft.Data.Sqlite.SqliteConnection db, long mangaId)
    {
        var ids = new List<long> { mangaId };
        foreach (var reference in db.Query("SELECT child_source, child_url FROM merged_ref WHERE parent_id = $id", ("$id", mangaId)))
        {
            if (db.Scalar<long?>("SELECT id FROM manga WHERE source = $s AND url = $u",
                    ("$s", reference.Long("child_source")), ("$u", reference.Text("child_url"))) is { } childId)
            {
                ids.Add(childId);
            }
        }
        return ids;
    }

    public Task<ChapterRow?> GetChapterAsync(long chapterId, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var row = db.Query(
                "SELECT ch.*, m.source AS owner_source FROM chapter ch JOIN manga m ON m.id = ch.manga_id WHERE ch.id = $id",
                ("$id", chapterId)).FirstOrDefault();
            return row == null
                ? null
                : ChapterRowOf(row, row.Long("owner_source"), _readable(row.Long("owner_source")));
        });

    private static ChapterRow ChapterRowOf(SqliteRow row, long ownerSource, bool readable) => new(
        row.Long("id"),
        row.Long("manga_id"),
        ownerSource,
        row.Text("url"),
        row.Text("name"),
        row.TextOrNull("scanlator"),
        row.Double("chapter_number"),
        row.Long("date_upload"),
        (int)row.Long("source_order"),
        row.Bool("read"),
        row.Bool("bookmark"),
        (int)row.Long("last_page_read"),
        row.LongOrNull("page_count") is { } pages ? (int)pages : null,
        row.Long("last_read_at"),
        row.TextOrNull("external_url"),
        readable);

    public Task<IReadOnlyList<HistoryEntry>> GetHistoryAsync(int limit, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            var ids = VisibleMangaIds(db);
            var entries = new List<HistoryEntry>();
            foreach (var chunk in ChunkIds(ids))
            {
                var (sql, parameters) = InFilterList("ch.manga_id", chunk);
                var historySql = """
                        SELECT ch.id AS chapter_id, ch.name, ch.chapter_number, ch.last_page_read, ch.last_read_at,
                               m.id AS manga_id, m.title, m.thumbnail_url
                        FROM chapter ch JOIN manga m ON m.id = ch.manga_id
                        WHERE ch.last_read_at > 0 AND
                        """ + " " + sql + " ORDER BY ch.last_read_at DESC";
                foreach (var row in db.Query(historySql, parameters))
                {
                    entries.Add(new HistoryEntry(
                        row.Long("manga_id"), row.Text("title"), row.TextOrNull("thumbnail_url"),
                        row.Long("chapter_id"), row.Text("name"), row.Double("chapter_number"),
                        (int)row.Long("last_page_read"), row.Long("last_read_at")));
                }
            }
            return (IReadOnlyList<HistoryEntry>)entries
                .OrderByDescending(e => e.LastReadAt).ThenByDescending(e => e.ChapterId)
                .Take(limit).ToList();
        });

    public Task<IReadOnlyList<UpdateEntry>> GetUpdatesAsync(int limit, CancellationToken ct = default) =>
        _db.ReadAsync(db =>
        {
            FillFavouriteOwners(db);
            var entries = new List<UpdateEntry>();
            foreach (var row in db.Query(
                         """
                         SELECT ch.id AS chapter_id, ch.name, ch.scanlator, ch.chapter_number, ch.date_fetch,
                                ch.date_upload, ch.read, ch.last_page_read, m.id AS manga_id, m.title, m.thumbnail_url
                         FROM chapter ch INDEXED BY idx_chapter_fetch JOIN manga m ON m.id = ch.manga_id
                         WHERE ch.manga_id IN (SELECT manga_id FROM fav_owners)
                         ORDER BY ch.date_fetch DESC, ch.date_upload DESC, ch.id ASC LIMIT $lim
                         """, ("$lim", limit)))
            {
                entries.Add(new UpdateEntry(
                    row.Long("manga_id"), row.Text("title"), row.TextOrNull("thumbnail_url"),
                    row.Long("chapter_id"), row.Text("name"), row.Double("chapter_number"),
                    row.TextOrNull("scanlator"), row.Long("date_fetch"), row.Long("date_upload"),
                    row.Bool("read"), (int)row.Long("last_page_read")));
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
                FROM fav_owners o JOIN chapter ch ON ch.manga_id = o.manga_id
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
                JOIN fav_owners o ON o.parent_id = lr.parent_id
                JOIN chapter ch ON ch.manga_id = o.manga_id AND ch.read = 0 AND ch.chapter_number > lr.chapter_number
                WHERE lr.is_read = 1 AND lr.chapter_number >= 0
                GROUP BY lr.parent_id
                """);
            var entries = new List<ContinueEntry>();
            foreach (var row in db.Query("""
                    SELECT lr.parent_id AS manga_id, m.title AS title, m.thumbnail_url AS thumbnail_url,
                           lr.chapter_id AS chapter_id, lr.name AS name, lr.chapter_number AS chapter_number,
                           lr.last_page_read AS last_page_read, lr.page_count AS page_count,
                           lr.last_read_at AS last_read_at,
                           r.chapter_id AS resume_id, r.name AS resume_name,
                           r.chapter_number AS resume_number, r.page_count AS resume_pages,
                           r.last_read_at AS resume_last_read_at
                    FROM fav_lastread lr
                    JOIN manga m ON m.id = lr.parent_id
                    LEFT JOIN fav_resume r ON r.parent_id = lr.parent_id
                    ORDER BY CASE WHEN r.chapter_id IS NULL THEN lr.last_read_at ELSE r.last_read_at END DESC, lr.parent_id ASC
                    """))
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
                    chapterId, name, number, lastPageRead, pageCount, lastReadAt, null));
            }
            return (IReadOnlyList<ContinueEntry>)entries.Take(limit).ToList();
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

    private List<long> VisibleMangaIds(Microsoft.Data.Sqlite.SqliteConnection db)
    {
        var ids = db.Query("SELECT id FROM manga WHERE favorite = 1").Select(r => r.Long("id")).ToList();
        foreach (var id in ids.ToList())
        {
            ids.AddRange(MergedChapterOwnerIds(db, id).Skip(1));
        }
        return ids.Distinct().ToList();
    }

    public Task<long> UpsertFromSourceAsync(long source, SManga manga, CancellationToken ct = default) =>
        _db.WriteAsync(db =>
        {
            var existing = db.Query("SELECT * FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", manga.Url))
                .FirstOrDefault();
            long id;
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
                    ("$g", string.Join('\u001f', manga.Genres)), ("$st", (long)manga.Status), ("$th", manga.ThumbnailUrl),
                    ("$p", payload));
                id = db.Scalar<long>("SELECT id FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", manga.Url));
            }
            else
            {
                id = existing.Long("id");
                var payload = new MessageParser<BackupManga>(() => new BackupManga()).ParseFrom(existing.Blob("payload"));
                payload.Title = manga.Title;
                if (manga.Author != null) payload.Author = manga.Author; else payload.ClearAuthor();
                if (manga.Artist != null) payload.Artist = manga.Artist; else payload.ClearArtist();
                if (manga.Description != null) payload.Description = manga.Description; else payload.ClearDescription();
                payload.Genre.Clear();
                payload.Genre.AddRange(manga.Genres);
                payload.Status = (int)manga.Status;
                if (manga.ThumbnailUrl != null) payload.ThumbnailUrl = manga.ThumbnailUrl; else payload.ClearThumbnailUrl();
                payload.Initialized = manga.Initialized;
                db.ExecuteNonQuery("""
                    UPDATE manga SET title = $t, author = $a, artist = $ar, description = $d, genres = $g,
                        status = $st, thumbnail_url = $th, payload = $p, last_chapter_refresh = $now
                    WHERE id = $id
                    """,
                    ("$t", manga.Title), ("$a", manga.Author), ("$ar", manga.Artist), ("$d", manga.Description),
                    ("$g", string.Join('\u001f', manga.Genres)), ("$st", (long)manga.Status), ("$th", manga.ThumbnailUrl),
                    ("$p", payload.ToByteArray()), ("$now", NowMs()), ("$id", id));
            }
            _db.RenderCache.Invalidate();
            return id;
        });

    private static byte[] PayloadFromSource(long source, SManga manga, bool favourite)
    {
        var payload = new BackupManga
        {
            Source = source,
            Url = manga.Url,
            Title = manga.Title,
            Status = (int)manga.Status,
            Favorite = favourite,
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
            var manga = db.Query("SELECT source, payload FROM manga WHERE id = $id", ("$id", mangaId)).FirstOrDefault()
                ?? throw new InvalidOperationException("manga not found: " + mangaId);
            var source = manga.Long("source");
            var known = db.Query("""
                SELECT id, url, date_fetch, version, last_modified_at, read, bookmark, last_page_read,
                    name, scanlator, chapter_number, date_upload, page_count, external_url
                FROM chapter WHERE manga_id = $id
                """,
                ("$id", mangaId)).ToDictionary(r => r.Text("url"));
            var now = NowMs();
            var added = 0;
            var updated = 0;
            var seen = new HashSet<string>();
            long refreshSeq = 0;
            var refreshOrigin = "";
            void TakeSeq()
            {
                refreshSeq = _db.GetSeq(db) + 1;
                _db.SetSeq(db, refreshSeq);
                _db.RenderCache.Invalidate();
                refreshOrigin = _db.GetMeta(db, "local_device_id");
            }
            foreach (var chapter in chapters)
            {
                if (!known.TryGetValue(chapter.Url, out var existing))
                {
                    var payload = new BackupChapter
                    {
                        Url = chapter.Url,
                        Name = chapter.Name,
                        ChapterNumber = (float)chapter.ChapterNumber,
                        DateUpload = chapter.DateUpload,
                        DateFetch = now,
                        Read = false,
                        Bookmark = false,
                        LastPageRead = 0,
                    };
                    if (chapter.Scanlator != null) payload.Scanlator = chapter.Scanlator;
                    if (refreshSeq == 0) TakeSeq();
                    var version = 1;
                    var modified = NowSec();
                    payload.Version = version;
                    payload.LastModifiedAt = modified;
                    db.ExecuteNonQuery("""
                        INSERT INTO chapter(manga_id, url, name, scanlator, chapter_number, source_order, date_upload,
                            date_fetch, read, bookmark, last_page_read, last_read_at, page_count, external_url,
                            version, last_modified_at, seq, origin, payload)
                        VALUES ($m, $u, $n, $sc, $num, $ord, $up, $fetch, 0, 0, 0, 0, $pc, $ext, $v, $lm, $seq, $o, $p)
                        """,
                        ("$m", mangaId), ("$u", chapter.Url), ("$n", chapter.Name), ("$sc", chapter.Scanlator),
                        ("$num", chapter.ChapterNumber), ("$ord", 0), ("$up", chapter.DateUpload), ("$fetch", now),
                        ("$pc", chapter.PageCount), ("$ext", chapter.ExternalUrl),
                        ("$v", version), ("$lm", modified), ("$seq", refreshSeq), ("$o", refreshOrigin), ("$p", payload.ToByteArray()));
                    added++;
                }
                else
                {
                    seen.Add(chapter.Url);
                    if (existing.Long("version") > 0
                        && existing.Text("name") == chapter.Name
                        && existing.TextOrNull("scanlator") == chapter.Scanlator
                        && existing.Double("chapter_number") == chapter.ChapterNumber
                        && existing.Long("date_upload") == chapter.DateUpload
                        && existing.LongOrNull("page_count") == chapter.PageCount
                        && existing.TextOrNull("external_url") == chapter.ExternalUrl)
                    {
                        continue;
                    }
                    var payload = new BackupChapter
                    {
                        Url = chapter.Url,
                        Name = chapter.Name,
                        ChapterNumber = (float)chapter.ChapterNumber,
                        DateUpload = chapter.DateUpload,
                        DateFetch = existing.Long("date_fetch"),
                        Read = existing.Bool("read"),
                        Bookmark = existing.Bool("bookmark"),
                        LastPageRead = (int)existing.Long("last_page_read"),
                        Version = existing.Long("version"),
                        LastModifiedAt = existing.Long("last_modified_at"),
                    };
                    if (chapter.Scanlator != null) payload.Scanlator = chapter.Scanlator;
                    else payload.ClearScanlator();
                    if (refreshSeq == 0) TakeSeq();
                    var version = payload.Version + 1;
                    var modified = NowSec();
                    payload.Version = version;
                    payload.LastModifiedAt = modified;
                    db.ExecuteNonQuery("""
                        UPDATE chapter SET name = $n, scanlator = $sc, chapter_number = $num, date_upload = $up,
                            page_count = $pc, external_url = $ext, version = $v, last_modified_at = $lm,
                            seq = $seq, origin = $o, payload = $p WHERE id = $id
                        """,
                        ("$n", chapter.Name), ("$sc", chapter.Scanlator), ("$num", chapter.ChapterNumber),
                        ("$up", chapter.DateUpload), ("$pc", chapter.PageCount), ("$ext", chapter.ExternalUrl),
                        ("$v", version), ("$lm", modified), ("$seq", refreshSeq), ("$o", refreshOrigin),
                        ("$p", payload.ToByteArray()), ("$id", existing.Long("id")));
                    updated++;
                }
            }
            var removed = 0;
            foreach (var url in known.Keys.Where(u => !seen.Contains(u)).ToList())
            {
                db.ExecuteNonQuery("DELETE FROM chapter WHERE manga_id = $m AND url = $u", ("$m", mangaId), ("$u", url));
                removed++;
            }
            db.ExecuteNonQuery("UPDATE manga SET last_chapter_refresh = $now WHERE id = $id", ("$now", NowMs()), ("$id", mangaId));
            _db.RenderCache.Invalidate();
            return new ChapterSyncResult(added, updated, removed);
        }, result => RaiseChanged(new[] { mangaId }, ChangeOrigin.Source, null, Array.Empty<ProgressHandoff>()));

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

    internal static (string Sql, (string, object?)[] Parameters) InFilterList(string column, List<long> values)
    {
        var placeholders = new List<string>();
        var parameters = new List<(string, object?)>();
        for (var i = 0; i < values.Count; i++)
        {
            placeholders.Add($"$v{i}");
            parameters.Add(($"$v{i}", (object?)values[i]));
        }
        return (values.Count > 0 ? $"{column} IN ({string.Join(", ", placeholders)})" : "1 = 0", parameters.ToArray());
    }

    private static IEnumerable<List<long>> ChunkIds(IReadOnlyList<long> source)
    {
        for (var i = 0; i < source.Count; i += 400)
        {
            yield return source.Skip(i).Take(400).ToList();
        }
    }
    public Task FlushAsync(CancellationToken ct = default) => _db.FlushAsync();
}
