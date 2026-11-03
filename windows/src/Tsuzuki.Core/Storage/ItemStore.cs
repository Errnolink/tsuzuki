using Google.Protobuf;
using Microsoft.Data.Sqlite;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sync;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Storage;

internal sealed record ApplyOutcome(long Seq, bool Wrote, IReadOnlyList<long> MangaIds,
    IReadOnlyList<ProgressHandoff> Handoffs);

internal sealed class ItemView
{
    public required Func<SyncKind, string, SyncItem?> Get { get; init; }
    public required Func<string, SyncItem?> CategoryByName { get; init; }
    public required Func<SyncItem, SyncItem, bool> SameContent { get; init; }
}

/// <summary>
/// Reads and writes sync items in the library tables. Column/payload derivation for sync
/// traffic goes through SyncRows; this class owns the SQL for each kind and builds the
/// stored-state view the merge rules run against.
/// </summary>
internal sealed class ItemStore
{
    private const int ChunkSize = 400;

    private readonly LibraryDatabase _db;

    public ItemStore(LibraryDatabase db) => _db = db;

    public LibraryDatabase Database => _db;

    public bool StoreExists(SqliteConnection db) =>
        db.Scalar<long>("SELECT COUNT(*) FROM meta WHERE key = 'hub_seq'") > 0;

    public void EnsureState(SqliteConnection db) =>
        db.ExecuteNonQuery("INSERT INTO meta(key, value) VALUES ('hub_seq','0') ON CONFLICT(key) DO NOTHING");

    public ItemView LoadView(SqliteConnection db, IReadOnlyList<SyncItem> incoming)
    {
        var byKey = new Dictionary<(SyncKind, string), SyncItem>();

        var mangaKeys = incoming.Where(i => i.Kind == SyncKind.Manga).Select(i => i.Key).Distinct().ToList();
        foreach (var item in LoadMangaItems(db, mangaKeys))
        {
            byKey[(SyncKind.Manga, item.Key)] = item;
        }

        var parentKeys = incoming.Where(i => i.Kind == SyncKind.Chapter).Select(i => i.ParentKey).Distinct().ToList();
        foreach (var item in LoadChapterItems(db, parentKeys))
        {
            byKey[(SyncKind.Chapter, item.Key)] = item;
        }

        var categories = LoadAllCategories(db);
        foreach (var category in categories)
        {
            byKey[(SyncKind.Category, category.Key)] = category;
        }

        foreach (var group in incoming
                     .Where(i => i.Kind is SyncKind.Field or SyncKind.Source or SyncKind.AppPref
                         or SyncKind.SourcePref or SyncKind.ExtStore or SyncKind.SavedSearch)
                     .GroupBy(SectionRowKind))
        {
            foreach (var item in LoadSections(db, group.Key, group.Select(i => i.Key).ToList()))
            {
                byKey[(item.Kind, item.Key)] = item;
            }
        }

        var categoriesByName = new Dictionary<string, SyncItem>();
        foreach (var category in categories) categoriesByName.TryAdd(category.Name, category);

        return new ItemView
        {
            Get = (kind, key) => byKey.TryGetValue((kind, key), out var item) ? item : null,
            CategoryByName = name => categoriesByName.GetValueOrDefault(name),
            SameContent = (a, b) =>
                SameRefs(a.Refs, b.Refs) && SyncRows.PayloadEquals(a.Kind, a.Payload, b.Payload),
        };
    }

    private static bool SameRefs(IReadOnlyList<string> a, IReadOnlyList<string> b)
    {
        if (a.Count != b.Count) return false;
        return a.OrderBy(x => x, StringComparer.Ordinal).SequenceEqual(b.OrderBy(x => x, StringComparer.Ordinal));
    }

    public List<SyncItem> LoadMangaItems(SqliteConnection db, List<string> keys)
    {
        var rows = new List<SqliteRow>();
        foreach (var chunk in Chunk(keys))
        {
            var (sql, parameters) = MangaKeyFilter(chunk);
            rows.AddRange(db.Query("SELECT * FROM manga WHERE " + sql, parameters));
        }
        return MangaItems(db, rows);
    }

    private List<SyncItem> MangaItems(SqliteConnection db, List<SqliteRow> rows)
    {
        if (rows.Count == 0) return new List<SyncItem>();
        var refsByManga = CategoryRefsByManga(db, rows.Select(r => r.Long("id")).ToList());
        var nameKeys = NameKeyedCategories(db);
        var items = new List<SyncItem>(rows.Count);
        foreach (var row in rows)
        {
            var id = row.Long("id");
            var refs = new List<string>();
            if (refsByManga.TryGetValue(id, out var uids))
            {
                foreach (var uid in uids)
                {
                    refs.Add(uid != 0 ? "uid:" + uid : nameKeys.GetValueOrDefault(uid, "uid:0"));
                }
            }
            items.Add(new SyncItem
            {
                Kind = SyncKind.Manga,
                Key = SyncRows.MangaKey(row.Long("source"), row.Text("url")),
                Version = row.Long("version"),
                ModifiedAt = row.Long("last_modified_at"),
                Refs = refs,
                Payload = row.Blob("payload"),
                Seq = row.Long("seq"),
                Origin = row.TextOrNull("origin"),
            });
        }
        return items;
    }

    private static Dictionary<long, List<long>> CategoryRefsByManga(SqliteConnection db, List<long> ids)
    {
        var map = new Dictionary<long, List<long>>();
        foreach (var chunk in Chunk(ids))
        {
            var (sql, parameters) = InFilter("mc.manga_id", chunk.Cast<object>().ToList());
            foreach (var row in db.Query(
                         "SELECT mc.manga_id, mc.category_uid FROM manga_category mc JOIN category c ON c.uid = mc.category_uid WHERE " + sql +
                         " ORDER BY mc.manga_id, mc.category_uid", parameters))
            {
                var mangaId = row.Long("manga_id");
                if (!map.TryGetValue(mangaId, out var list)) map[mangaId] = list = new List<long>();
                list.Add(row.Long("category_uid"));
            }
        }
        return map;
    }

    private static Dictionary<long, string> NameKeyedCategories(SqliteConnection db)
    {
        var map = new Dictionary<long, string>();
        foreach (var row in db.Query("SELECT uid, payload FROM category WHERE uid = 0"))
        {
            var category = BackupCategory.Parser.ParseFrom(row.Blob("payload"));
            map[row.Long("uid")] = SyncRows.CategoryKey(category);
        }
        return map;
    }

    public List<SyncItem> LoadChapterItems(SqliteConnection db, List<string> parentKeys)
    {
        var ids = new List<long>();
        foreach (var chunk in Chunk(parentKeys))
        {
            var (sql, parameters) = MangaKeyFilter(chunk);
            foreach (var row in db.Query("SELECT id FROM manga WHERE " + sql, parameters))
            {
                ids.Add(row.Long("id"));
            }
        }
        var items = new List<SyncItem>();
        foreach (var chunk in Chunk(ids))
        {
            var (sql, parameters) = InFilter("ch.manga_id", chunk.Cast<object>().ToList());
            items.AddRange(ChapterItems(db, sql, parameters));
        }
        return items;
    }

    /// <summary>Loads chapter items by exact chapter key (<c>mangaKey␟url</c>).</summary>
    public List<SyncItem> LoadChapterItemsByKeys(SqliteConnection db, List<string> keys)
    {
        var items = new List<SyncItem>();
        foreach (var chunk in Chunk(keys))
        {
            var values = new List<string>();
            var parameters = new List<(string, object?)>();
            foreach (var key in chunk)
            {
                if (SyncRows.ChapterKeyParts(key) is not { } chapter) continue;
                if (SyncRows.MangaKeyParts(chapter.MangaKey) is not { } manga) continue;
                var i = values.Count;
                values.Add($"($s{i}, $u{i}, $c{i})");
                parameters.Add(($"$s{i}", (object?)manga.Source));
                parameters.Add(($"$u{i}", (object?)manga.Url));
                parameters.Add(($"$c{i}", (object?)chapter.Url));
            }
            if (values.Count == 0) continue;
            items.AddRange(ChapterItems(db,
                "(m.source, m.url, ch.url) IN (VALUES " + string.Join(", ", values) + ")",
                parameters.ToArray()));
        }
        return items;
    }

    /// <summary>Whether any category row (live or tombstoned) moved past the cursor.</summary>
    public bool CategoryActivitySince(SqliteConnection db, long cursor) =>
        db.Scalar<long>("SELECT EXISTS(SELECT 1 FROM category WHERE seq > $c)", ("$c", cursor)) > 0;

    private List<SyncItem> ChapterItems(SqliteConnection db, string where, params (string Name, object? Value)[] parameters)
    {
        var items = new List<SyncItem>();
        int[] o = null;
        db.QueryEach(ChapterItemSelect + " WHERE " + where, reader =>
        {
            o ??= new[]
            {
                reader.GetOrdinal("m_source"), reader.GetOrdinal("m_url"), reader.GetOrdinal("url"),
                reader.GetOrdinal("version"), reader.GetOrdinal("last_modified_at"),
                reader.GetOrdinal("payload"), reader.GetOrdinal("seq"), reader.GetOrdinal("origin"),
            };
            var mangaKey = SyncRows.MangaKey(reader.GetInt64(o[0]), reader.GetString(o[1]));
            items.Add(new SyncItem
            {
                Kind = SyncKind.Chapter,
                Key = SyncRows.ChapterKey(mangaKey, reader.GetString(o[2])),
                ParentKey = mangaKey,
                Version = reader.GetInt64(o[3]),
                ModifiedAt = reader.GetInt64(o[4]),
                Payload = (byte[])reader.GetValue(o[5]),
                Seq = reader.GetInt64(o[6]),
                Origin = reader.IsDBNull(o[7]) ? null : reader.GetString(o[7]),
            });
        }, parameters);
        return items;
    }

    public List<SyncItem> LoadAllCategories(SqliteConnection db) =>
        db.Query("SELECT * FROM category").Select(CategoryItem).ToList();

    public List<SyncItem> LoadSections(SqliteConnection db, string rowKind, List<string> keys)
    {
        var items = new List<SyncItem>();
        foreach (var chunk in Chunk(keys))
        {
            var (sql, parameters) = InFilter("key", chunk.Cast<object>().ToList());
            var all = parameters.Append(("$kind", (object?)rowKind)).ToArray();
            foreach (var row in db.Query("SELECT * FROM section WHERE kind = $kind AND " + sql, all))
            {
                items.Add(SectionItem(row, KindFromName(rowKind)));
            }
        }
        return items;
    }

    public List<SyncItem> ItemsSince(SqliteConnection db, long cursor)
    {
        var items = MangaItems(db, db.Query("SELECT * FROM manga WHERE seq > $c", ("$c", cursor)));
        items.AddRange(ChapterItems(db, "ch.seq > $c", ("$c", cursor)));
        items.AddRange(LoadAllCategories(db));
        items.AddRange(db.Query("SELECT * FROM section WHERE seq > $c", ("$c", cursor))
            .Select(row => SectionItem(row, KindFromName(row.Text("kind")))));
        return items;
    }

    public List<SyncItem> AllItems(SqliteConnection db)
    {
        var items = MangaItems(db, db.Query("SELECT * FROM manga"));
        items.AddRange(ChapterItems(db, "1 = 1"));
        items.AddRange(LoadAllCategories(db));
        items.AddRange(db.Query("SELECT * FROM section")
            .Select(row => SectionItem(row, KindFromName(row.Text("kind")))));
        return items;
    }

    /// <summary>Renders the whole store straight from the tables, streaming chapters and
    /// parsing each payload once. Matches SyncRows.Render over AllItems exactly.</summary>
    public BackupMessage RenderStore(SqliteConnection db)
    {
        var backup = new BackupMessage();
        var keyToOrder = new Dictionary<string, long>();
        var categories = new List<BackupCategory>();
        foreach (var row in db.Query("SELECT deleted, payload FROM category"))
        {
            if (row.Bool("deleted")) continue;
            categories.Add(BackupCategory.Parser.ParseFrom(row.Blob("payload")));
        }
        backup.BackupCategories.AddRange(categories.OrderBy(c => c.Order));
        foreach (var category in backup.BackupCategories) keyToOrder[SyncRows.CategoryKey(category)] = category.Order;

        var chaptersByManga = new Dictionary<string, List<BackupChapter>>();
        int[] chapterCols = null;
        db.QueryEach(ChapterPayloadSelect, reader =>
        {
            chapterCols ??= new[] { reader.GetOrdinal("m_source"), reader.GetOrdinal("m_url"), reader.GetOrdinal("payload") };
            var parentKey = SyncRows.MangaKey(reader.GetInt64(chapterCols[0]), reader.GetString(chapterCols[1]));
            var chapter = BackupChapter.Parser.ParseFrom((byte[])reader.GetValue(chapterCols[2]));
            if (!chaptersByManga.TryGetValue(parentKey, out var list)) chaptersByManga[parentKey] = list = new List<BackupChapter>();
            list.Add(chapter);
        });

        var mangaRows = db.Query("SELECT * FROM manga");
        var refsByManga = CategoryRefsByManga(db, mangaRows.Select(r => r.Long("id")).ToList());
        var nameKeys = NameKeyedCategories(db);
        var sourceIds = new HashSet<long>();
        var mangaItems = new List<(string Key, BackupManga Manga, List<string> Refs)>();
        foreach (var row in mangaRows)
        {
            var id = row.Long("id");
            var refs = new List<string>();
            if (refsByManga.TryGetValue(id, out var uids))
            {
                foreach (var uid in uids)
                {
                    refs.Add(uid != 0 ? "uid:" + uid : nameKeys.GetValueOrDefault(uid, "uid:0"));
                }
            }
            var key = SyncRows.MangaKey(row.Long("source"), row.Text("url"));
            var manga = BackupManga.Parser.ParseFrom(row.Blob("payload"));
            if (SyncRows.MangaKeyParts(key) is { } parts) sourceIds.Add(parts.Source);
            mangaItems.Add((key, manga, refs));
        }
        foreach (var (key, manga, refs) in mangaItems.OrderBy(m => m.Key, StringComparer.Ordinal))
        {
            manga.Categories.Clear();
            foreach (var reference in refs)
            {
                if (keyToOrder.TryGetValue(reference, out var order)) manga.Categories.Add(order);
            }
            if (chaptersByManga.TryGetValue(key, out var chapters))
            {
                manga.Chapters.AddRange(chapters.OrderBy(c => c.SourceOrder).ThenBy(c => c.Url, StringComparer.Ordinal));
            }
            backup.BackupManga.Add(manga);
        }

        var sectionItems = db.Query("SELECT * FROM section")
            .Select(row => SectionItem(row, KindFromName(row.Text("kind")))).ToList();
        return SyncRows.RenderSections(backup, sectionItems, sourceIds);
    }

    private const string ChapterItemSelect =
        "SELECT ch.url, ch.version, ch.last_modified_at, ch.seq, ch.origin, ch.payload, m.source AS m_source, m.url AS m_url FROM chapter ch JOIN manga m ON m.id = ch.manga_id";

    private const string ChapterPayloadSelect =
        "SELECT ch.payload, m.source AS m_source, m.url AS m_url FROM chapter ch JOIN manga m ON m.id = ch.manga_id";

    /// <summary>Applies merge writes and tombstones. Caller bumps and stores the seq.</summary>
    public ApplyOutcome Apply(SqliteConnection db, MergeOutcome outcome, long newSeq, string origin, string deviceName)
    {
        var mangaIds = new HashSet<long>();
        var handoffs = new List<ProgressHandoff>();
        foreach (var item in outcome.Writes.Where(i => i.Kind == SyncKind.Category))
        {
            WriteCategory(db, item, newSeq, origin);
        }
        var writtenManga = new List<(SyncItem Item, BackupManga Manga, long Id)>();
        foreach (var item in outcome.Writes.Where(i => i.Kind == SyncKind.Manga))
        {
            var manga = BackupManga.Parser.ParseFrom(item.Payload);
            if (WriteManga(db, item, manga, newSeq, origin) is { } id)
            {
                mangaIds.Add(id);
                writtenManga.Add((item, manga, id));
            }
        }
        var visible = VisibleLibraryMangaIds(db);
        var chapterWrites = outcome.Writes.Where(i => i.Kind == SyncKind.Chapter).ToList();
        var mangaIdByKey = MangaIdsByKey(db, chapterWrites.Select(i => i.ParentKey).Distinct().ToList());
        var before = ChapterRowsBefore(db, mangaIdByKey.Values.Distinct().ToList());
        using var chapterCmd = ChapterUpsertCommand(db);
        foreach (var item in chapterWrites)
        {
            var url = SyncRows.ChapterKeyParts(item.Key)?.Url;
            if (url == null || !mangaIdByKey.TryGetValue(item.ParentKey, out var mangaId)) continue;
            var chapter = BackupChapter.Parser.ParseFrom(item.Payload);
            BindChapter(chapterCmd, item, mangaId, url, chapter, newSeq, origin);
            chapterCmd.ExecuteNonQuery();
            mangaIds.Add(mangaId);
            if (!visible.Contains(mangaId)) continue;
            if (!before.TryGetValue((mangaId, url), out var was)) continue;
            if (was.Read != chapter.Read || was.Page != chapter.LastPageRead)
            {
                handoffs.Add(new ProgressHandoff(
                    mangaId, was.Id, (int)chapter.LastPageRead, chapter.Read, deviceName));
            }
        }
        foreach (var (item, manga, mangaId) in writtenManga)
        {
            foreach (var history in manga.History.Where(h => h.LastRead > 0))
            {
                db.ExecuteNonQuery(
                    "UPDATE chapter SET last_read_at = $lr WHERE manga_id = $m AND url = $cu AND last_read_at < $lr",
                    ("$lr", history.LastRead), ("$m", mangaId), ("$cu", history.Url));
            }
        }
        foreach (var item in outcome.Writes.Where(i => i.Kind is SyncKind.Field or SyncKind.Source
                     or SyncKind.AppPref or SyncKind.SourcePref or SyncKind.ExtStore or SyncKind.SavedSearch))
        {
            WriteSection(db, item, newSeq, origin);
        }
        var tombstoned = false;
        foreach (var key in outcome.Tombstones)
        {
            tombstoned |= TombstoneCategory(db, key, newSeq, origin);
        }
        var visibleIds = mangaIds.Where(visible.Contains).ToList();
        return new ApplyOutcome(newSeq, outcome.Writes.Count > 0 || tombstoned, visibleIds, handoffs);
    }

    private static Dictionary<string, long> MangaIdsByKey(SqliteConnection db, List<string> keys)
    {
        var map = new Dictionary<string, long>();
        foreach (var chunk in Chunk(keys))
        {
            var (sql, parameters) = MangaKeyFilter(chunk);
            foreach (var row in db.Query("SELECT id, source, url FROM manga WHERE " + sql, parameters))
            {
                map[SyncRows.MangaKey(row.Long("source"), row.Text("url"))] = row.Long("id");
            }
        }
        return map;
    }

    private static Dictionary<(long MangaId, string Url), (long Id, bool Read, long Page)> ChapterRowsBefore(
        SqliteConnection db, List<long> mangaIds)
    {
        var map = new Dictionary<(long MangaId, string Url), (long Id, bool Read, long Page)>();
        foreach (var chunk in Chunk(mangaIds))
        {
            var (sql, parameters) = InFilter("manga_id", chunk.Cast<object>().ToList());
            int[] o = null;
            db.QueryEach("SELECT id, manga_id, url, read, last_page_read FROM chapter WHERE " + sql, reader =>
            {
                o ??= new[]
                {
                    reader.GetOrdinal("id"), reader.GetOrdinal("manga_id"), reader.GetOrdinal("url"),
                    reader.GetOrdinal("read"), reader.GetOrdinal("last_page_read"),
                };
                map[(reader.GetInt64(o[1]), reader.GetString(o[2]))] =
                    (reader.GetInt64(o[0]), reader.GetInt64(o[3]) != 0, reader.GetInt64(o[4]));
            }, parameters);
        }
        return map;
    }

    private const string ChapterUpsertSql = """
        INSERT INTO chapter(manga_id, url, name, scanlator, chapter_number, source_order, date_upload,
            date_fetch, read, bookmark, last_page_read, last_read_at, page_count, external_url,
            version, last_modified_at, seq, origin, payload)
        VALUES ($manga_id, $url, $name, $scanlator, $number, $order, $upload, $fetch, $read, $bookmark,
            $page, $readat, NULL, NULL, $version, $modified, $seq, $origin, $payload)
        ON CONFLICT(manga_id, url) DO UPDATE SET
            name = $name, scanlator = $scanlator, chapter_number = $number, source_order = $order,
            date_upload = $upload, date_fetch = $fetch, read = $read, bookmark = $bookmark,
            last_page_read = $page,
            last_read_at = CASE WHEN chapter.last_read_at = 0 THEN $readat ELSE chapter.last_read_at END,
            version = $version, last_modified_at = $modified,
            seq = $seq, origin = $origin, payload = $payload
        """;

    private static SqliteCommand ChapterUpsertCommand(SqliteConnection db)
    {
        var cmd = db.CreateCommand();
        cmd.CommandText = ChapterUpsertSql;
        foreach (var name in new[]
                 {
                     "manga_id", "url", "name", "scanlator", "number", "order", "upload", "fetch",
                     "read", "bookmark", "page", "readat", "version", "modified", "seq", "origin", "payload"
                 })
        {
            var parameter = cmd.CreateParameter();
            parameter.ParameterName = "$" + name;
            cmd.Parameters.Add(parameter);
        }
        cmd.Prepare();
        return cmd;
    }

    private static void BindChapter(SqliteCommand cmd, SyncItem item, long mangaId, string url, BackupChapter chapter,
        long seq, string? origin)
    {
        cmd.Parameters["$manga_id"].Value = mangaId;
        cmd.Parameters["$url"].Value = url;
        cmd.Parameters["$name"].Value = chapter.Name;
        cmd.Parameters["$scanlator"].Value = (object?)(chapter.HasScanlator ? chapter.Scanlator : null) ?? DBNull.Value;
        cmd.Parameters["$number"].Value = chapter.ChapterNumber;
        cmd.Parameters["$order"].Value = chapter.SourceOrder;
        cmd.Parameters["$upload"].Value = chapter.DateUpload;
        cmd.Parameters["$fetch"].Value = chapter.DateFetch;
        cmd.Parameters["$read"].Value = chapter.Read;
        cmd.Parameters["$bookmark"].Value = chapter.Bookmark;
        cmd.Parameters["$page"].Value = chapter.LastPageRead;
        cmd.Parameters["$readat"].Value = chapter.Read && chapter.LastModifiedAt > 0 ? chapter.LastModifiedAt * 1000 : 0;
        cmd.Parameters["$version"].Value = chapter.Version;
        cmd.Parameters["$modified"].Value = chapter.LastModifiedAt;
        cmd.Parameters["$seq"].Value = seq;
        cmd.Parameters["$origin"].Value = (object?)origin ?? DBNull.Value;
        cmd.Parameters["$payload"].Value = item.Payload;
    }

    private static HashSet<long> VisibleLibraryMangaIds(SqliteConnection db) =>
        db.Query("""
            SELECT id FROM manga WHERE favorite = 1
            UNION
            SELECT m.id FROM manga m
            JOIN merged_ref r ON r.child_source = m.source AND r.child_url = m.url
            JOIN manga p ON p.id = r.parent_id
            WHERE p.favorite = 1
            """).Select(r => r.Long("id")).ToHashSet();

    public long? WriteManga(SqliteConnection db, SyncItem item, BackupManga manga, long seq, string? origin)
    {
        if (SyncRows.MangaKeyParts(item.Key) is not { } keyParts) return null;
        var favourite = !manga.HasFavorite || manga.Favorite;
        db.ExecuteNonQuery("""
            INSERT INTO manga(source, url, title, author, artist, description, genres, status, thumbnail_url,
                favorite, date_added, version, last_modified_at, seq, origin, payload, last_chapter_refresh)
            VALUES ($source, $url, $title, $author, $artist, $description, $genres, $status, $thumb,
                $favorite, $date_added, $version, $modified, $seq, $origin, $payload, 0)
            ON CONFLICT(source, url) DO UPDATE SET
                title = $title, author = $author, artist = $artist, description = $description,
                genres = $genres, status = $status, thumbnail_url = $thumb, favorite = $favorite,
                date_added = $date_added, version = $version, last_modified_at = $modified,
                seq = $seq, origin = $origin, payload = $payload
            """,
            ("$source", keyParts.Source), ("$url", keyParts.Url), ("$title", manga.Title),
            ("$author", manga.HasAuthor ? manga.Author : null), ("$artist", manga.HasArtist ? manga.Artist : null),
            ("$description", manga.HasDescription ? manga.Description : null),
            ("$genres", string.Join('\u001f', manga.Genre)), ("$status", (long)manga.Status),
            ("$thumb", manga.HasThumbnailUrl ? manga.ThumbnailUrl : null),
            ("$favorite", favourite),
            ("$date_added", manga.DateAdded), ("$version", manga.Version), ("$modified", manga.LastModifiedAt),
            ("$seq", seq), ("$origin", origin), ("$payload", item.Payload));
        var id = db.Scalar<long>("SELECT id FROM manga WHERE source = $source AND url = $url",
            ("$source", keyParts.Source), ("$url", keyParts.Url));

        db.ExecuteNonQuery("DELETE FROM manga_category WHERE manga_id = $id", ("$id", id));
        foreach (var reference in item.Refs)
        {
            if (ResolveCategoryUid(db, reference) is { } uid)
            {
                db.ExecuteNonQuery("INSERT OR IGNORE INTO manga_category(manga_id, category_uid) VALUES ($m, $c)",
                    ("$m", id), ("$c", uid));
            }
        }

        db.ExecuteNonQuery("DELETE FROM merged_ref WHERE parent_id = $id", ("$id", id));
        foreach (var reference in manga.MergedMangaReferences)
        {
            db.ExecuteNonQuery("""
                INSERT OR IGNORE INTO merged_ref(parent_id, child_source, child_url, is_info, priority)
                VALUES ($m, $cs, $cu, $info, $prio)
                """,
                ("$m", id), ("$cs", reference.MangaSourceId), ("$cu", reference.MangaUrl),
                ("$info", reference.IsInfoManga), ("$prio", reference.ChapterPriority));
        }
        return id;
    }

    public void WriteCategory(SqliteConnection db, SyncItem item, long seq, string? origin)
    {
        var category = BackupCategory.Parser.ParseFrom(item.Payload);
        var uid = item.Key.StartsWith("uid:", StringComparison.Ordinal)
            ? long.Parse(item.Key["uid:".Length..])
            : CategoryUidForNameKey(db, item.Key["name:".Length..]);
        db.ExecuteNonQuery("""
            INSERT INTO category(uid, name, sort, flags, version, last_modified_at, deleted, seq, origin, payload)
            VALUES ($uid, $name, $sort, $flags, $version, $modified, 0, $seq, $origin, $payload)
            ON CONFLICT(uid) DO UPDATE SET
                name = $name, sort = $sort, flags = $flags, version = $version, last_modified_at = $modified,
                deleted = 0, seq = $seq, origin = $origin, payload = $payload
            """,
            ("$uid", uid), ("$name", category.Name), ("$sort", category.Order), ("$flags", category.Flags),
            ("$version", category.Version), ("$modified", category.LastModifiedAt),
            ("$seq", seq), ("$origin", origin), ("$payload", item.Payload));
    }

    private long CategoryUidForNameKey(SqliteConnection db, string name)
    {
        var existing = db.Scalar<long?>("SELECT uid FROM category WHERE name = $n", ("$n", name));
        if (existing.HasValue) return existing.Value;
        var zeroTaken = db.Scalar<long>("SELECT COUNT(*) FROM category WHERE uid = 0") > 0;
        return zeroTaken ? NextUid(db) : 0;
    }

    private long NextUid(SqliteConnection db) => db.Scalar<long>("SELECT COALESCE(MAX(uid), 0) FROM category") + 1;

    public bool TombstoneCategory(SqliteConnection db, string key, long seq, string? origin)
    {
        if (key.StartsWith("uid:", StringComparison.Ordinal))
        {
            return db.ExecuteNonQuery(
                "UPDATE category SET deleted = 1, seq = $seq, origin = $origin WHERE uid = $uid AND deleted = 0",
                ("$seq", seq), ("$origin", origin),
                ("$uid", long.Parse(key["uid:".Length..]))) > 0;
        }
        if (key.StartsWith("name:", StringComparison.Ordinal))
        {
            return db.ExecuteNonQuery(
                "UPDATE category SET deleted = 1, seq = $seq, origin = $origin WHERE name = $name AND uid = 0 AND deleted = 0",
                ("$seq", seq), ("$origin", origin), ("$name", key["name:".Length..])) > 0;
        }
        return false;
    }

    public void WriteSection(SqliteConnection db, SyncItem item, long seq, string? origin) =>
        db.ExecuteNonQuery("""
            INSERT INTO section(kind, key, payload, seq, origin) VALUES ($kind, $key, $payload, $seq, $origin)
            ON CONFLICT(kind, key) DO UPDATE SET payload = $payload, seq = $seq, origin = $origin
            """,
            ("$kind", SectionRowKind(item)), ("$key", item.Key), ("$payload", item.Payload),
            ("$seq", seq), ("$origin", origin));

    public void UpsertDevice(SqliteConnection db, string id, string name, long cursor, int protocol, long lastSeenMs)
    {
        db.ExecuteNonQuery("""
            INSERT INTO device(id, name, cursor, last_seen, protocol) VALUES ($id, $name, $cursor, $seen, $protocol)
            ON CONFLICT(id) DO UPDATE SET
                name = $name, cursor = $cursor, last_seen = $seen, protocol = $protocol
            """,
            ("$id", id), ("$name", name), ("$cursor", cursor), ("$seen", lastSeenMs), ("$protocol", protocol));
        db.ExecuteNonQuery("""
            DELETE FROM device WHERE id NOT IN (SELECT id FROM device ORDER BY last_seen DESC, rowid DESC LIMIT 10)
            """);
    }

    public static string SectionRowKind(SyncItem item) =>
        item.Kind == SyncKind.Field ? item.Key : KindName(item.Kind);

    private static SyncItem CategoryItem(SqliteRow row)
    {
        var payload = row.Blob("payload");
        var category = BackupCategory.Parser.ParseFrom(payload);
        return new SyncItem
        {
            Kind = SyncKind.Category,
            Key = SyncRows.CategoryKey(category),
            Name = category.Name,
            Version = category.Version,
            ModifiedAt = category.LastModifiedAt,
            Deleted = row.Bool("deleted"),
            Payload = payload,
            Seq = row.Long("seq"),
            Origin = row.TextOrNull("origin"),
        };
    }

    private static SyncItem SectionItem(SqliteRow row, SyncKind kind) => new()
    {
        Kind = kind,
        Key = row.Text("key"),
        Payload = row.Blob("payload"),
        Seq = row.Long("seq"),
        Origin = row.TextOrNull("origin"),
    };

    private long? ResolveCategoryUid(SqliteConnection db, string key)
    {
        if (key.StartsWith("uid:", StringComparison.Ordinal))
        {
            return long.Parse(key["uid:".Length..]);
        }
        if (key.StartsWith("name:", StringComparison.Ordinal))
        {
            return db.Scalar<long?>("SELECT uid FROM category WHERE name = $n", ("$n", key["name:".Length..]));
        }
        return null;
    }

    private static string KindName(SyncKind kind) => kind switch
    {
        SyncKind.Source => "source",
        SyncKind.AppPref => "app_pref",
        SyncKind.SourcePref => "source_pref",
        SyncKind.ExtStore => "ext_store",
        SyncKind.SavedSearch => "saved_search",
        _ => throw new ArgumentException("unsupported section kind"),
    };

    private static SyncKind KindFromName(string name) => name switch
    {
        "source" => SyncKind.Source,
        "app_pref" => SyncKind.AppPref,
        "source_pref" => SyncKind.SourcePref,
        "ext_store" => SyncKind.ExtStore,
        "saved_search" => SyncKind.SavedSearch,
        _ when name.StartsWith("field:", StringComparison.Ordinal) => SyncKind.Field,
        _ => throw new ArgumentException("unknown section kind " + name),
    };

    private static IEnumerable<List<T>> Chunk<T>(List<T> source)
    {
        for (var i = 0; i < source.Count; i += ChunkSize)
        {
            yield return source.GetRange(i, Math.Min(ChunkSize, source.Count - i));
        }
    }

    private static (string Sql, (string, object?)[] Parameters) MangaKeyFilter(List<string> keys)
    {
        var values = new List<string>();
        var parameters = new List<(string, object?)>();
        for (var i = 0; i < keys.Count; i++)
        {
            var parts = SyncRows.MangaKeyParts(keys[i]) ?? (0, "");
            values.Add($"($s{i}, $u{i})");
            parameters.Add(($"$s{i}", (object?)parts.Item1));
            parameters.Add(($"$u{i}", (object?)parts.Item2));
        }
        return ($"(source, url) IN (VALUES {string.Join(", ", values)})", parameters.ToArray());
    }

    private static (string Sql, (string, object?)[] Parameters) InFilter(string column, List<object> values)
    {
        var placeholders = new List<string>();
        var parameters = new List<(string, object?)>();
        for (var i = 0; i < values.Count; i++)
        {
            placeholders.Add($"$v{i}");
            parameters.Add(($"$v{i}", values[i]));
        }
        return (values.Count > 0
            ? $"{column} IN ({string.Join(", ", placeholders)})"
            : "1 = 0", parameters.ToArray());
    }
}
