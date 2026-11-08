using Google.Protobuf;
using Microsoft.Data.Sqlite;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;

namespace Tsuzuki.Core.Storage;

public sealed class SqliteMergeEditor(LibraryDatabase db, SqliteLibraryStore store) : IMergeEditor
{
    private const long MergedSource = LibraryRefresher.MergedSourceId;

    public async Task<long> MergeAsync(long mangaId, long candidateId, CancellationToken ct = default)
    {
        var result = await db.WriteAsync(conn =>
        {
            var original = Load(conn, mangaId);
            var candidate = Load(conn, candidateId);
            if (candidate.Manga.Source == MergedSource)
            {
                throw new InvalidOperationException("A merged title can't be merged into another title.");
            }
            if (candidateId == mangaId)
            {
                throw new InvalidOperationException("A title can't be merged with itself.");
            }
            var touched = new List<long> { mangaId, candidateId };
            if (original.Manga.Source == MergedSource)
            {
                return AddChild(conn, original, candidate, touched);
            }
            var existing = FindParent(conn, original.Manga.Url);
            if (existing is { Manga.Favorite: true })
            {
                touched.Add(existing.Id);
                if (!HasChild(existing.Manga, original.Manga))
                {
                    existing.Manga.MergedMangaReferences.Add(ChildRef(existing.Manga.Url, original.Manga, info: !existing.Manga.MergedMangaReferences.Any(r => r.IsInfoManga)));
                }
                return AddChild(conn, existing, candidate, touched);
            }
            return CreateParent(conn, existing, original, candidate, touched);
        }, result => Raise(result.Touched)).ConfigureAwait(false);
        return result.ParentId;
    }

    public Task SetInfoSourceAsync(long parentId, MergedRef child, CancellationToken ct = default) =>
        Edit(parentId, (conn, parent) =>
        {
            var target = Find(parent.Manga, child) ?? throw new InvalidOperationException("That title is not part of this merge.");
            foreach (var reference in parent.Manga.MergedMangaReferences)
            {
                reference.IsInfoManga = ReferenceEquals(reference, target);
            }
            CopyInfo(conn, parent.Manga, target);
        });

    public Task ReorderAsync(long parentId, IReadOnlyList<MergedRef> children, CancellationToken ct = default) =>
        Edit(parentId, (_, parent) =>
        {
            var ordered = children.Select(c => Find(parent.Manga, c)).OfType<BackupMergedMangaReference>().Distinct().ToList();
            ordered.AddRange(Children(parent.Manga).Where(r => !ordered.Contains(r)));
            for (var i = 0; i < ordered.Count; i++)
            {
                ordered[i].ChapterPriority = i;
            }
            foreach (var self in parent.Manga.MergedMangaReferences.Where(r => r.MangaSourceId == MergedSource))
            {
                self.ChapterPriority = -1;
            }
        });

    public Task RemoveAsync(long parentId, MergedRef child, CancellationToken ct = default) =>
        Edit(parentId, (conn, parent) =>
        {
            var target = Find(parent.Manga, child) ?? throw new InvalidOperationException("That title is not part of this merge.");
            if (Children(parent.Manga).Count() <= 2)
            {
                throw new InvalidOperationException("A merge needs at least two titles. Unmerge it instead.");
            }
            parent.Manga.MergedMangaReferences.Remove(target);
            if (target.IsInfoManga && Children(parent.Manga).FirstOrDefault() is { } next)
            {
                next.IsInfoManga = true;
                CopyInfo(conn, parent.Manga, next);
            }
        });

    public Task UnmergeAsync(long parentId, CancellationToken ct = default) =>
        db.WriteAsync(conn =>
        {
            var parent = Load(conn, parentId);
            if (parent.Manga.Source != MergedSource)
            {
                throw new InvalidOperationException("Only a merged title can be unmerged.");
            }
            var touched = new List<long> { parentId };
            var categories = conn.Query("SELECT category_uid FROM manga_category WHERE manga_id = $id", ("$id", parentId))
                .Select(r => r.Long("category_uid")).ToList();
            foreach (var reference in Children(parent.Manga).ToList())
            {
                if (FindId(conn, reference.MangaSourceId, reference.MangaUrl) is not { } childId)
                {
                    continue;
                }
                touched.Add(childId);
                var child = Load(conn, childId);
                if (child.Manga.Favorite)
                {
                    continue;
                }
                child.Manga.Favorite = true;
                child.Manga.FavoriteModifiedAt = SqliteLibraryStore.NowSec();
                child.Manga.DateAdded = SqliteLibraryStore.NowMs();
                foreach (var uid in categories)
                {
                    conn.ExecuteNonQuery("INSERT OR IGNORE INTO manga_category(manga_id, category_uid) VALUES ($m, $c)", ("$m", childId), ("$c", uid));
                }
                Save(conn, child);
            }
            parent.Manga.MergedMangaReferences.Clear();
            parent.Manga.Favorite = false;
            parent.Manga.FavoriteModifiedAt = SqliteLibraryStore.NowSec();
            Save(conn, parent);
            return touched;
        }, Raise);

    private Task Edit(long parentId, Action<SqliteConnection, Row> edit) =>
        db.WriteAsync(conn =>
        {
            var parent = Load(conn, parentId);
            if (parent.Manga.Source != MergedSource)
            {
                throw new InvalidOperationException("Only a merged title has merge settings.");
            }
            edit(conn, parent);
            Save(conn, parent);
            var touched = new List<long> { parentId };
            foreach (var reference in Children(parent.Manga))
            {
                if (FindId(conn, reference.MangaSourceId, reference.MangaUrl) is { } id) touched.Add(id);
            }
            return touched;
        }, Raise);

    private (long ParentId, List<long> Touched) AddChild(SqliteConnection conn, Row parent, Row candidate, List<long> touched)
    {
        if (!HasChild(parent.Manga, candidate.Manga))
        {
            parent.Manga.MergedMangaReferences.Add(ChildRef(parent.Manga.Url, candidate.Manga, info: false));
        }
        if (parent.Manga.MergedMangaReferences.All(r => r.MangaSourceId != MergedSource))
        {
            parent.Manga.MergedMangaReferences.Add(SelfRef(parent.Manga.Url));
        }
        var seq = Save(conn, parent);
        foreach (var id in touched.Where(id => id != parent.Id))
        {
            Share(conn, id, seq);
        }
        touched.Add(parent.Id);
        return (parent.Id, touched);
    }

    private (long ParentId, List<long> Touched) CreateParent(SqliteConnection conn, Row? archived, Row original, Row candidate, List<long> touched)
    {
        var source = original.Manga;
        var manga = new BackupManga
        {
            Source = MergedSource,
            Url = source.Url,
            Title = source.HasCustomTitle ? source.CustomTitle : source.Title,
            Status = source.Status,
            Viewer = source.Viewer,
            ChapterFlags = source.ChapterFlags,
            Favorite = true,
            FavoriteModifiedAt = SqliteLibraryStore.NowSec(),
            DateAdded = SqliteLibraryStore.NowMs(),
            Version = archived?.Manga.Version ?? 0,
            LastModifiedAt = archived?.Manga.LastModifiedAt ?? 0,
        };
        if (source.HasViewerFlags) manga.ViewerFlags = source.ViewerFlags;
        if (source.HasAuthor) manga.Author = source.Author;
        if (source.HasArtist) manga.Artist = source.Artist;
        if (source.HasDescription) manga.Description = source.Description;
        if (source.HasCustomThumbnailUrl) manga.ThumbnailUrl = source.CustomThumbnailUrl;
        else if (source.HasThumbnailUrl) manga.ThumbnailUrl = source.ThumbnailUrl;
        manga.Genre.AddRange(source.Genre);
        manga.MergedMangaReferences.Add(ChildRef(manga.Url, original.Manga, info: true));
        manga.MergedMangaReferences.Add(ChildRef(manga.Url, candidate.Manga, info: false));
        manga.MergedMangaReferences.Add(SelfRef(manga.Url));
        var parent = new Row(archived?.Id ?? 0, manga);
        var seq = Save(conn, parent);
        conn.ExecuteNonQuery("DELETE FROM manga_category WHERE manga_id = $p", ("$p", parent.Id));
        conn.ExecuteNonQuery("""
            INSERT OR IGNORE INTO manga_category(manga_id, category_uid)
            SELECT $p, category_uid FROM manga_category WHERE manga_id = $o
            """, ("$p", parent.Id), ("$o", original.Id));
        Share(conn, original.Id, seq);
        Share(conn, candidate.Id, seq);
        touched.Add(parent.Id);
        return (parent.Id, touched);
    }

    private static BackupMergedMangaReference ChildRef(string mergeUrl, BackupManga child, bool info) => new()
    {
        IsInfoManga = info,
        GetChapterUpdates = true,
        ChapterSortMode = 0,
        ChapterPriority = 0,
        DownloadChapters = true,
        MergeUrl = mergeUrl,
        MangaUrl = child.Url,
        MangaSourceId = child.Source,
    };

    private static BackupMergedMangaReference SelfRef(string mergeUrl) => new()
    {
        IsInfoManga = false,
        GetChapterUpdates = false,
        ChapterSortMode = 0,
        ChapterPriority = -1,
        DownloadChapters = false,
        MergeUrl = mergeUrl,
        MangaUrl = mergeUrl,
        MangaSourceId = MergedSource,
    };

    private static IEnumerable<BackupMergedMangaReference> Children(BackupManga parent) =>
        parent.MergedMangaReferences
            .Where(r => r.MangaSourceId != MergedSource)
            .OrderBy(r => r.ChapterPriority)
            .ThenBy(r => r.MangaSourceId)
            .ThenBy(r => r.MangaUrl, StringComparer.Ordinal);

    private static bool HasChild(BackupManga parent, BackupManga child) =>
        parent.MergedMangaReferences.Any(r => r.MangaSourceId == child.Source && r.MangaUrl == child.Url);

    private static BackupMergedMangaReference? Find(BackupManga parent, MergedRef child) =>
        parent.MergedMangaReferences.FirstOrDefault(r =>
            r.MangaSourceId != MergedSource && r.MangaSourceId == child.Source && r.MangaUrl == child.Url);

    private static void CopyInfo(SqliteConnection conn, BackupManga parent, BackupMergedMangaReference info)
    {
        if (FindId(conn, info.MangaSourceId, info.MangaUrl) is not { } id)
        {
            return;
        }
        var child = Load(conn, id).Manga;
        var title = child.HasCustomTitle ? child.CustomTitle : child.Title;
        if (title.Length > 0) parent.Title = title;
        if (child.HasAuthor) parent.Author = child.Author;
        if (child.HasArtist) parent.Artist = child.Artist;
        if (child.HasDescription) parent.Description = child.Description;
        if (child.HasCustomThumbnailUrl) parent.ThumbnailUrl = child.CustomThumbnailUrl;
        else if (child.HasThumbnailUrl) parent.ThumbnailUrl = child.ThumbnailUrl;
        if (child.Genre.Count > 0)
        {
            parent.Genre.Clear();
            parent.Genre.AddRange(child.Genre);
        }
        if (child.Status != 0) parent.Status = child.Status;
    }

    private static Row? FindParent(SqliteConnection conn, string url) =>
        FindId(conn, MergedSource, url) is { } id ? Load(conn, id) : null;

    private static long? FindId(SqliteConnection conn, long source, string url) =>
        conn.Scalar<long?>("SELECT id FROM manga WHERE source = $s AND url = $u", ("$s", source), ("$u", url));

    private static Row Load(SqliteConnection conn, long id)
    {
        var row = conn.Query("SELECT payload FROM manga WHERE id = $id", ("$id", id)).FirstOrDefault()
            ?? throw new InvalidOperationException("manga not found: " + id);
        return new Row(id, BackupManga.Parser.ParseFrom(row.Blob("payload")));
    }

    private long Save(SqliteConnection conn, Row row)
    {
        var manga = row.Manga;
        var (version, modified, seq, origin) = store.BumpVersion(conn, manga.Version, manga.LastModifiedAt);
        manga.Version = version;
        manga.LastModifiedAt = modified;
        (string, object?)[] values =
        [
            ("$source", manga.Source), ("$url", manga.Url), ("$title", manga.Title),
            ("$author", manga.HasAuthor ? manga.Author : null), ("$artist", manga.HasArtist ? manga.Artist : null),
            ("$description", manga.HasDescription ? manga.Description : null),
            ("$genres", string.Join('\u001f', manga.Genre)), ("$status", (long)manga.Status),
            ("$thumb", manga.HasThumbnailUrl ? manga.ThumbnailUrl : null), ("$favorite", manga.Favorite),
            ("$date_added", manga.DateAdded), ("$version", version), ("$modified", modified),
            ("$seq", seq), ("$origin", origin), ("$payload", manga.ToByteArray()), ("$id", row.Id),
        ];
        if (row.Id == 0)
        {
            conn.ExecuteNonQuery("""
                INSERT INTO manga(source, url, title, author, artist, description, genres, status, thumbnail_url,
                    favorite, date_added, version, last_modified_at, seq, origin, payload, last_chapter_refresh)
                VALUES ($source, $url, $title, $author, $artist, $description, $genres, $status, $thumb,
                    $favorite, $date_added, $version, $modified, $seq, $origin, $payload, 0)
                """, values);
            row.Id = conn.Scalar<long>("SELECT id FROM manga WHERE source = $source AND url = $url", ("$source", manga.Source), ("$url", manga.Url));
        }
        else
        {
            conn.ExecuteNonQuery("""
                UPDATE manga SET title = $title, author = $author, artist = $artist, description = $description,
                    genres = $genres, status = $status, thumbnail_url = $thumb, favorite = $favorite,
                    date_added = $date_added, version = $version, last_modified_at = $modified,
                    seq = $seq, origin = $origin, payload = $payload
                WHERE id = $id
                """, values);
        }
        conn.ExecuteNonQuery("DELETE FROM merged_ref WHERE parent_id = $id", ("$id", row.Id));
        foreach (var reference in manga.MergedMangaReferences)
        {
            conn.ExecuteNonQuery("""
                INSERT OR IGNORE INTO merged_ref(parent_id, child_source, child_url, is_info, priority)
                VALUES ($m, $cs, $cu, $info, $prio)
                """,
                ("$m", row.Id), ("$cs", reference.MangaSourceId), ("$cu", reference.MangaUrl),
                ("$info", reference.IsInfoManga), ("$prio", reference.ChapterPriority));
        }
        conn.ExecuteNonQuery("UPDATE chapter SET seq = $seq, origin = $o WHERE manga_id = $id AND seq = 0",
            ("$seq", seq), ("$o", origin), ("$id", row.Id));
        return seq;
    }

    private void Share(SqliteConnection conn, long mangaId, long seq)
    {
        var origin = db.GetMeta(conn, "local_device_id");
        conn.ExecuteNonQuery("UPDATE manga SET seq = $seq, origin = $o WHERE id = $id AND seq = 0",
            ("$seq", seq), ("$o", origin), ("$id", mangaId));
        conn.ExecuteNonQuery("UPDATE chapter SET seq = $seq, origin = $o WHERE manga_id = $id AND seq = 0",
            ("$seq", seq), ("$o", origin), ("$id", mangaId));
    }

    private void Raise(List<long> touched) =>
        store.RaiseChanged(touched.Distinct().ToList(), ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>());

    private sealed class Row(long id, BackupManga manga)
    {
        public long Id { get; set; } = id;
        public BackupManga Manga { get; } = manga;
    }
}
