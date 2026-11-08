using System.Text.Json;
using Microsoft.Data.Sqlite;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;

namespace Tsuzuki.Core.Downloads;

public sealed class DownloadQueue
{
    private const string PausedKey = "downloads_paused";
    private const string AutoMarkKey = "downloads_auto_mark";

    private readonly LibraryDatabase db;
    private readonly ILibraryStore library;
    private readonly ISourceRegistry sources;
    private readonly ChapterDownloader downloader;
    private readonly LocalChapterProvider local;
    private readonly DownloadEnvironment environment;
    private readonly object gate = new();
    private readonly List<Entry> queue = [];
    private readonly Dictionary<(long, string), Entry> queued = new();
    private readonly Dictionary<(long, string), DownloadedChapter> index = new();
    private readonly Dictionary<long, Task> workers = new();
    private readonly SemaphoreSlim policyGate = new(1, 1);
    private readonly SemaphoreSlim scanGate = new(1, 1);
    private readonly CancellationTokenSource shutdown = new();
    private DownloadOptions options;
    private bool paused;
    private bool lowSpace;
    private bool metered;
    private bool moving;
    private bool started;

    public DownloadQueue(LibraryDatabase db, ILibraryStore library, ISourceRegistry sources, IImageCache pages, IImageCache covers,
        string extractRoot, DownloadOptions options, DownloadEnvironment? environment = null)
    {
        this.db = db;
        this.library = library;
        this.sources = sources;
        this.options = options;
        this.environment = environment ?? new DownloadEnvironment();
        downloader = new ChapterDownloader(pages, covers, this.environment);
        local = new LocalChapterProvider(extractRoot);
    }

    public static string DefaultRoot { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "Tsuzuki", "downloads");

    public event EventHandler? Changed;

    public event EventHandler<Exception>? BackgroundError;

    public DownloadOptions Options => options;

    public DownloadBlock Block
    {
        get
        {
            lock (gate) return BlockLocked();
        }
    }

    public int Count
    {
        get
        {
            lock (gate) return queue.Count;
        }
    }

    public IReadOnlyList<DownloadItem> Items
    {
        get
        {
            lock (gate) return queue.Select(e => e.Snapshot()).ToList();
        }
    }

    public async Task StartAsync()
    {
        await db.WriteAsync(conn =>
        {
            conn.ExecuteNonQuery(Schema);
            if (conn.Scalar<string?>("SELECT value FROM meta WHERE key = $k", ("$k", AutoMarkKey)) is null)
            {
                db.SetMeta(conn, AutoMarkKey, conn.Scalar<long>("SELECT COALESCE(MAX(id), 0) FROM chapter").ToString());
            }
            return 0;
        }).ConfigureAwait(false);
        var (entries, chapters, pausedValue) = await db.ReadAsync(conn => (LoadQueue(conn), LoadIndex(conn), db.GetMeta(conn, PausedKey))).ConfigureAwait(false);
        lock (gate)
        {
            queue.Clear();
            queued.Clear();
            index.Clear();
            foreach (var entry in entries)
            {
                queue.Add(entry);
                queued[entry.Key] = entry;
            }
            foreach (var chapter in chapters)
            {
                index[(chapter.MangaId, chapter.ChapterUrl)] = chapter;
            }
            paused = pausedValue == "1";
            started = true;
        }
        library.Changed += OnLibraryChanged;
        Raise();
        await RescanAsync().ConfigureAwait(false);
        Kick();
    }

    public async Task StopAsync()
    {
        library.Changed -= OnLibraryChanged;
        Task[] running;
        lock (gate)
        {
            started = false;
            shutdown.Cancel();
            running = workers.Values.ToArray();
        }
        try
        {
            await Task.WhenAll(running).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
        }
    }

    public void Configure(DownloadOptions next)
    {
        DownloadOptions previous;
        lock (gate)
        {
            previous = options;
            options = next;
        }
        if (!started)
        {
            return;
        }
        if (!string.Equals(Path.GetFullPath(previous.Root), Path.GetFullPath(next.Root), StringComparison.OrdinalIgnoreCase))
        {
            _ = Guard(async () =>
            {
                await RescanAsync().ConfigureAwait(false);
                Kick();
            });
            return;
        }
        if (previous.DeleteAfterReadKeep != next.DeleteAfterReadKeep)
        {
            _ = RunPolicyAsync(checkNew: false);
        }
        Kick();
    }

    public void NetworkChanged() => Kick();

    public void Pause()
    {
        lock (gate)
        {
            paused = true;
            CancelActiveLocked();
        }
        _ = Guard(() => db.SetMetaAsync(PausedKey, "1"));
        Raise();
    }

    public void Resume()
    {
        lock (gate)
        {
            paused = false;
            lowSpace = false;
        }
        _ = Guard(() => db.SetMetaAsync(PausedKey, "0"));
        Kick();
    }

    public ChapterDownloadStatus StatusOf(long mangaId, string chapterUrl)
    {
        lock (gate)
        {
            if (index.ContainsKey((mangaId, chapterUrl)))
            {
                return new ChapterDownloadStatus(ChapterDownloadState.Downloaded, 0, 0);
            }
            if (queued.TryGetValue((mangaId, chapterUrl), out var entry))
            {
                var state = entry.State switch
                {
                    DownloadState.Downloading => ChapterDownloadState.Downloading,
                    DownloadState.Failed => ChapterDownloadState.Failed,
                    _ => ChapterDownloadState.Queued,
                };
                return new ChapterDownloadStatus(state, entry.PagesDone, entry.PagesTotal);
            }
            return ChapterDownloadStatus.None;
        }
    }

    public DownloadItem? ItemOf(long mangaId, string chapterUrl)
    {
        lock (gate) return queued.TryGetValue((mangaId, chapterUrl), out var entry) ? entry.Snapshot() : null;
    }

    public bool IsDownloaded(long mangaId, string chapterUrl)
    {
        lock (gate) return index.ContainsKey((mangaId, chapterUrl));
    }

    public int DownloadedCount(long mangaId)
    {
        lock (gate) return index.Keys.Count(k => k.Item1 == mangaId);
    }

    public IReadOnlySet<long> DownloadedMangaIds()
    {
        lock (gate) return index.Keys.Select(k => k.Item1).ToHashSet();
    }

    public async Task<int> EnqueueAsync(IReadOnlyList<long> chapterIds)
    {
        if (chapterIds.Count == 0)
        {
            return 0;
        }
        var ids = JsonSerializer.Serialize(chapterIds);
        var rows = await db.ReadAsync(conn => conn.Query(
            """
            SELECT ch.id, ch.manga_id, ch.url, ch.name, ch.chapter_number, m.source, m.title
            FROM chapter ch JOIN manga m ON m.id = ch.manga_id
            WHERE ch.id IN (SELECT value FROM json_each($ids))
            ORDER BY ch.manga_id, ch.chapter_number, ch.source_order DESC
            """, ("$ids", ids))).ConfigureAwait(false);
        List<Entry> fresh;
        lock (gate)
        {
            fresh = rows
                .Where(r => sources.Get(r.Long("source")) is not null)
                .Select(r => new Entry
                {
                    MangaId = r.Long("manga_id"),
                    ChapterUrl = r.Text("url"),
                    ChapterId = r.Long("id"),
                    Source = r.Long("source"),
                    MangaTitle = r.Text("title"),
                    ChapterName = r.Text("name"),
                    ChapterNumber = r.Double("chapter_number"),
                })
                .Where(e => !index.ContainsKey(e.Key) && !queued.ContainsKey(e.Key))
                .ToList();
        }
        if (fresh.Count == 0)
        {
            return 0;
        }
        var now = ChapterDownloader.NowMs();
        await db.WriteAsync(conn =>
        {
            var position = conn.Scalar<long>("SELECT COALESCE(MAX(position), 0) FROM download_queue");
            foreach (var entry in fresh)
            {
                entry.Position = ++position;
                entry.Id = conn.Scalar<long>(
                    """
                    INSERT INTO download_queue(manga_id, chapter_url, position, state, added_at) VALUES ($m, $u, $p, 0, $now)
                    ON CONFLICT(manga_id, chapter_url) DO UPDATE SET state = 0, error = NULL
                    RETURNING id
                    """, ("$m", entry.MangaId), ("$u", entry.ChapterUrl), ("$p", entry.Position), ("$now", now));
            }
            return 0;
        }).ConfigureAwait(false);
        lock (gate)
        {
            foreach (var entry in fresh)
            {
                if (queued.TryAdd(entry.Key, entry))
                {
                    queue.Add(entry);
                }
            }
        }
        Kick();
        return fresh.Count;
    }

    public async Task CancelAsync(IReadOnlyList<long> itemIds)
    {
        var removed = new List<Entry>();
        lock (gate)
        {
            foreach (var entry in queue.Where(e => itemIds.Contains(e.Id)).ToList())
            {
                queue.Remove(entry);
                queued.Remove(entry.Key);
                entry.Removed = true;
                if (entry.Cts is { } cts)
                {
                    cts.Cancel();
                }
                else
                {
                    removed.Add(entry);
                }
            }
        }
        await db.WriteAsync(conn => conn.ExecuteNonQuery("DELETE FROM download_queue WHERE id IN (SELECT value FROM json_each($ids))",
            ("$ids", JsonSerializer.Serialize(itemIds)))).ConfigureAwait(false);
        foreach (var entry in removed)
        {
            await DeleteStagingAsync(entry).ConfigureAwait(false);
        }
        Raise();
        Kick();
    }

    public async Task RetryAsync(IReadOnlyList<long> itemIds)
    {
        List<long> retried;
        lock (gate)
        {
            retried = queue.Where(e => e.State == DownloadState.Failed && (itemIds.Count == 0 || itemIds.Contains(e.Id))).Select(e =>
            {
                e.State = DownloadState.Queued;
                e.Error = null;
                return e.Id;
            }).ToList();
        }
        await db.WriteAsync(conn => conn.ExecuteNonQuery("UPDATE download_queue SET state = 0, error = NULL WHERE id IN (SELECT value FROM json_each($ids))",
            ("$ids", JsonSerializer.Serialize(retried)))).ConfigureAwait(false);
        Kick();
    }

    public async Task MoveAsync(long itemId, int toIndex)
    {
        List<(long Id, long Position)> positions;
        lock (gate)
        {
            var entry = queue.FirstOrDefault(e => e.Id == itemId);
            if (entry is null)
            {
                return;
            }
            queue.Remove(entry);
            queue.Insert(Math.Clamp(toIndex, 0, queue.Count), entry);
            for (var i = 0; i < queue.Count; i++)
            {
                queue[i].Position = i + 1;
            }
            positions = queue.Select(e => (e.Id, e.Position)).ToList();
        }
        await db.WriteAsync(conn =>
        {
            foreach (var (id, position) in positions)
            {
                conn.ExecuteNonQuery("UPDATE download_queue SET position = $p WHERE id = $id", ("$p", position), ("$id", id));
            }
            return 0;
        }).ConfigureAwait(false);
        Raise();
    }

    public Task<IReadOnlyList<DownloadedEntry>> GetDownloadedAsync() => db.ReadAsync(conn => (IReadOnlyList<DownloadedEntry>)conn.Query(
        """
        SELECT d.manga_id, d.chapter_url, d.path, d.pages, d.bytes, d.completed_at, m.title,
               COALESCE(ch.id, 0) AS chapter_id, COALESCE(ch.name, d.chapter_url) AS name, COALESCE(ch.chapter_number, -1) AS chapter_number
        FROM download d JOIN manga m ON m.id = d.manga_id
        LEFT JOIN chapter ch ON ch.manga_id = d.manga_id AND ch.url = d.chapter_url
        ORDER BY m.title COLLATE NOCASE, chapter_number
        """).Select(r => new DownloadedEntry(r.Long("manga_id"), r.Text("title"), r.Long("chapter_id"), r.Text("chapter_url"),
            r.Text("name"), r.Double("chapter_number"), r.Text("path"), (int)r.Long("pages"), r.Long("bytes"), r.Long("completed_at"))).ToList());

    public async Task<int> DeleteAsync(IReadOnlyList<(long MangaId, string ChapterUrl)> chapters)
    {
        var root = options.Root;
        var deleted = new List<(long, string)>();
        foreach (var key in chapters)
        {
            DownloadedChapter? chapter;
            lock (gate)
            {
                index.TryGetValue(key, out chapter);
            }
            if (chapter is null)
            {
                continue;
            }
            var path = Path.Combine(root, chapter.Path);
            try
            {
                if (File.Exists(path))
                {
                    File.Delete(path);
                }
                else if (Directory.Exists(path))
                {
                    Directory.Delete(path, recursive: true);
                }
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                continue;
            }
            RemoveEmptyParents(path, root);
            deleted.Add(key);
        }
        if (deleted.Count == 0)
        {
            return 0;
        }
        lock (gate)
        {
            foreach (var key in deleted)
            {
                index.Remove(key);
            }
        }
        await db.WriteAsync(conn =>
        {
            foreach (var (mangaId, url) in deleted)
            {
                conn.ExecuteNonQuery("DELETE FROM download WHERE manga_id = $m AND chapter_url = $u", ("$m", mangaId), ("$u", url));
            }
            return 0;
        }).ConfigureAwait(false);
        Raise();
        Kick();
        return deleted.Count;
    }

    public async Task<IReadOnlyList<PageRef>?> OpenLocalAsync(long mangaId, string chapterUrl, CancellationToken ct = default)
    {
        DownloadedChapter? chapter;
        lock (gate)
        {
            index.TryGetValue((mangaId, chapterUrl), out chapter);
        }
        if (chapter is null)
        {
            return null;
        }
        var path = Path.Combine(options.Root, chapter.Path);
        try
        {
            var pages = await local.OpenAsync(path, ct).ConfigureAwait(false);
            return pages.Count > 0 ? pages : null;
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException)
        {
            return null;
        }
    }

    public async Task<int> RescanAsync()
    {
        await scanGate.WaitAsync().ConfigureAwait(false);
        try
        {
            var root = options.Root;
            var since = ChapterDownloader.NowMs();
            Dictionary<(long, string), DownloadedChapter> previous;
            lock (gate)
            {
                previous = new Dictionary<(long, string), DownloadedChapter>(index);
            }
            var found = await db.ReadAsync(conn => Scan(conn, root, previous)).ConfigureAwait(false);
            List<DownloadedChapter> rows;
            lock (gate)
            {
                var merged = found.ToDictionary(c => (c.MangaId, c.ChapterUrl));
                foreach (var (key, value) in index)
                {
                    if (value.CompletedAt >= since && !merged.ContainsKey(key))
                    {
                        merged[key] = value;
                    }
                }
                index.Clear();
                foreach (var (key, value) in merged)
                {
                    index[key] = value;
                }
                rows = merged.Values.ToList();
            }
            await db.WriteAsync(conn =>
            {
                conn.ExecuteNonQuery("DELETE FROM download");
                foreach (var row in rows)
                {
                    Insert(conn, row);
                }
                return 0;
            }).ConfigureAwait(false);
            Raise();
            return rows.Count;
        }
        finally
        {
            scanGate.Release();
        }
    }

    public async Task MoveRootAsync(string newRoot, IProgress<double>? progress = null, CancellationToken ct = default)
    {
        var oldRoot = Path.GetFullPath(options.Root);
        var target = Path.GetFullPath(newRoot);
        if (string.Equals(oldRoot.TrimEnd('\\', '/'), target.TrimEnd('\\', '/'), StringComparison.OrdinalIgnoreCase))
        {
            return;
        }
        if (target.StartsWith(oldRoot.TrimEnd('\\', '/') + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
        {
            throw new ArgumentException("The new folder can't be inside the current downloads folder.", nameof(newRoot));
        }
        lock (gate)
        {
            moving = true;
            CancelActiveLocked();
        }
        Raise();
        try
        {
            await WaitForWorkersAsync().ConfigureAwait(false);
            await Task.Run(() =>
            {
                var files = Directory.Exists(oldRoot)
                    ? Directory.EnumerateFiles(oldRoot, "*", SearchOption.AllDirectories).ToList()
                    : [];
                long total = Math.Max(1, files.Sum(f => new FileInfo(f).Length));
                long copied = 0;
                foreach (var file in files)
                {
                    ct.ThrowIfCancellationRequested();
                    var destination = Path.Combine(target, Path.GetRelativePath(oldRoot, file));
                    Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
                    File.Copy(file, destination, overwrite: true);
                    var length = new FileInfo(file).Length;
                    if (new FileInfo(destination).Length != length)
                    {
                        throw new IOException($"Copying {file} did not verify.");
                    }
                    copied += length;
                    progress?.Report((double)copied / total);
                }
                Directory.CreateDirectory(target);
                lock (gate)
                {
                    options = options with { Root = target };
                }
                foreach (var file in files)
                {
                    try
                    {
                        File.Delete(file);
                    }
                    catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                    {
                    }
                }
                foreach (var dir in Directory.Exists(oldRoot) ? Directory.EnumerateDirectories(oldRoot, "*", SearchOption.AllDirectories).OrderByDescending(d => d.Length).ToList() : [])
                {
                    try
                    {
                        Directory.Delete(dir);
                    }
                    catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                    {
                    }
                }
            }, ct).ConfigureAwait(false);
        }
        finally
        {
            lock (gate)
            {
                moving = false;
            }
        }
        await RescanAsync().ConfigureAwait(false);
        Kick();
    }

    private async Task WaitForWorkersAsync()
    {
        Task[] running;
        lock (gate)
        {
            running = workers.Values.ToArray();
        }
        try
        {
            await Task.WhenAll(running).ConfigureAwait(false);
        }
        catch (Exception e) when (e is OperationCanceledException)
        {
        }
    }

    private DownloadBlock BlockLocked() =>
        paused || moving ? DownloadBlock.Paused
        : metered ? DownloadBlock.Metered
        : lowSpace ? DownloadBlock.LowSpace
        : DownloadBlock.None;

    private void CancelActiveLocked()
    {
        foreach (var entry in queue)
        {
            entry.Cts?.Cancel();
        }
    }

    private void Kick()
    {
        bool isStarted;
        DownloadOptions current;
        lock (gate)
        {
            isStarted = started;
            current = options;
        }
        if (!isStarted)
        {
            return;
        }
        var nowMetered = current.UnmeteredOnly && environment.IsMetered();
        var hasSpace = environment.FreeBytes(current.Root) >= environment.MinimumFreeBytes;
        lock (gate)
        {
            metered = nowMetered;
            if (lowSpace && hasSpace)
            {
                lowSpace = false;
            }
            if (!hasSpace && queue.Count > 0)
            {
                lowSpace = true;
            }
            if (BlockLocked() != DownloadBlock.None)
            {
                CancelActiveLocked();
            }
            else
            {
                foreach (var source in queue.Where(e => e.State == DownloadState.Queued).Select(e => e.Source).Distinct().ToList())
                {
                    if (!workers.ContainsKey(source))
                    {
                        workers[source] = Task.Run(() => RunSourceAsync(source));
                    }
                }
            }
        }
        Raise();
    }

    private async Task RunSourceAsync(long source)
    {
        while (true)
        {
            Entry? entry;
            CancellationTokenSource cts;
            string root;
            lock (gate)
            {
                entry = BlockLocked() == DownloadBlock.None && !shutdown.IsCancellationRequested
                    ? queue.FirstOrDefault(e => e.Source == source && e.State == DownloadState.Queued)
                    : null;
                if (entry is null)
                {
                    workers.Remove(source);
                    return;
                }
                entry.State = DownloadState.Downloading;
                entry.Error = null;
                entry.Cts = cts = CancellationTokenSource.CreateLinkedTokenSource(shutdown.Token);
                root = options.Root;
            }
            Raise();
            try
            {
                var result = await DownloadEntryAsync(entry, root, cts.Token).ConfigureAwait(false);
                await db.WriteAsync(conn =>
                {
                    conn.ExecuteNonQuery("DELETE FROM download_queue WHERE id = $id", ("$id", entry.Id));
                    Insert(conn, result);
                    return 0;
                }).ConfigureAwait(false);
                lock (gate)
                {
                    queue.Remove(entry);
                    queued.Remove(entry.Key);
                    index[entry.Key] = result;
                }
            }
            catch (OperationCanceledException) when (cts.IsCancellationRequested)
            {
                bool removed;
                lock (gate)
                {
                    removed = entry.Removed;
                    entry.State = DownloadState.Queued;
                }
                if (removed)
                {
                    await DeleteStagingAsync(entry).ConfigureAwait(false);
                }
            }
            catch (LowSpaceException)
            {
                lock (gate)
                {
                    entry.State = DownloadState.Queued;
                    lowSpace = true;
                }
            }
            catch (Exception e)
            {
                lock (gate)
                {
                    entry.State = DownloadState.Failed;
                    entry.Error = e.Message;
                }
                await Guard(() => db.WriteAsync(conn => conn.ExecuteNonQuery(
                    "UPDATE download_queue SET state = 2, error = $e, pages_done = $d, pages_total = $t WHERE id = $id",
                    ("$e", e.Message), ("$d", entry.PagesDone), ("$t", entry.PagesTotal), ("$id", entry.Id)))).ConfigureAwait(false);
            }
            finally
            {
                lock (gate)
                {
                    entry.Cts = null;
                }
                cts.Dispose();
            }
            Raise();
        }
    }

    private async Task<DownloadedChapter> DownloadEntryAsync(Entry entry, string root, CancellationToken ct)
    {
        var source = sources.Get(entry.Source)
            ?? throw new InvalidOperationException($"{sources.DisplayName(entry.Source)} isn't available on Windows.");
        var manga = await library.GetMangaAsync(entry.MangaId, ct).ConfigureAwait(false)
            ?? throw new InvalidOperationException("This title is no longer in the library.");
        var chapter = await library.GetChapterAsync(entry.ChapterId, ct).ConfigureAwait(false);
        if (chapter is null || chapter.Url != entry.ChapterUrl)
        {
            throw new InvalidOperationException("The source no longer lists this chapter.");
        }
        var relative = await RelativePathAsync(entry.MangaId, manga.Title, chapter.Name, chapter.Scanlator, chapter.Url, source).ConfigureAwait(false);
        var categories = manga.CategoryUids.Count == 0
            ? []
            : (await library.GetCategoriesAsync(ct).ConfigureAwait(false)).Where(c => manga.CategoryUids.Contains(c.Uid)).Select(c => c.Name.Trim()).ToList();
        var job = new ChapterJob(manga, chapter, source, relative, categories);
        return await downloader.DownloadAsync(job, root, new EntryProgress(this, entry), ct).ConfigureAwait(false);
    }

    private async Task<string> RelativePathAsync(long mangaId, string mangaTitle, string name, string? scanlator, string url, ISource source)
    {
        var siblings = await db.ReadAsync(conn => conn.Query("SELECT url, name, scanlator FROM chapter WHERE manga_id = $m", ("$m", mangaId))).ConfigureAwait(false);
        return ChapterPath(source.Name, source.Lang, mangaTitle, name, scanlator, url,
            siblings.Select(s => (s.Text("name"), s.TextOrNull("scanlator"), s.Text("url"))));
    }

    internal static string ChapterPath(string sourceName, string lang, string mangaTitle, string name, string? scanlator, string url,
        IEnumerable<(string Name, string? Scanlator, string Url)> siblings)
    {
        var plain = DownloadNames.ChapterName(name, scanlator, url);
        var clash = siblings.Any(s => s.Url != url
            && string.Equals(DownloadNames.ChapterName(s.Name, s.Scanlator, s.Url), plain, StringComparison.OrdinalIgnoreCase));
        var file = (clash ? DownloadNames.ChapterName(name, scanlator, url, withHash: true) : plain) + ".cbz";
        return Path.Combine(DownloadNames.SourceFolder(sourceName, lang), DownloadNames.MangaFolder(mangaTitle), file);
    }

    private async Task DeleteStagingAsync(Entry entry)
    {
        if (sources.Get(entry.Source) is not { } source || await library.GetMangaAsync(entry.MangaId).ConfigureAwait(false) is not { } manga
            || await library.GetChapterAsync(entry.ChapterId).ConfigureAwait(false) is not { } chapter)
        {
            return;
        }
        var relative = await RelativePathAsync(entry.MangaId, manga.Title, chapter.Name, chapter.Scanlator, chapter.Url, source).ConfigureAwait(false);
        var staging = Path.Combine(options.Root, relative)[..^4] + DownloadNames.TempSuffix;
        ChapterDownloader.DeleteDirectory(staging);
        RemoveEmptyParents(staging, options.Root);
    }

    private static void RemoveEmptyParents(string path, string root)
    {
        var fullRoot = Path.GetFullPath(root).TrimEnd('\\', '/');
        var dir = Path.GetDirectoryName(Path.GetFullPath(path));
        while (dir is not null && dir.Length > fullRoot.Length && dir.StartsWith(fullRoot, StringComparison.OrdinalIgnoreCase))
        {
            try
            {
                var rest = Directory.EnumerateFileSystemEntries(dir).ToList();
                if (rest.Any(e => !string.Equals(Path.GetFileName(e), DownloadNames.CoverFile, StringComparison.OrdinalIgnoreCase)))
                {
                    return;
                }
                Directory.Delete(dir, recursive: true);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                return;
            }
            dir = Path.GetDirectoryName(dir);
        }
    }

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e)
    {
        if (e.IsProgressOnly)
        {
            return;
        }
        _ = RunPolicyAsync(checkNew: e.Origin is ChangeOrigin.Source or ChangeOrigin.Remote);
    }

    private async Task RunPolicyAsync(bool checkNew)
    {
        try
        {
            await policyGate.WaitAsync(shutdown.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            return;
        }
        try
        {
            if (checkNew)
            {
                await CheckNewChaptersAsync().ConfigureAwait(false);
            }
            await ApplyDeleteAfterReadAsync().ConfigureAwait(false);
        }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            BackgroundError?.Invoke(this, e);
        }
        finally
        {
            policyGate.Release();
        }
    }

    private async Task CheckNewChaptersAsync()
    {
        var categories = options.AutoDownloadCategories;
        var ids = await db.WriteAsync(conn =>
        {
            var mark = long.TryParse(db.GetMeta(conn, AutoMarkKey), out var stored) ? stored : 0;
            var max = conn.Scalar<long>("SELECT COALESCE(MAX(id), 0) FROM chapter");
            if (max == mark)
            {
                return new List<long>();
            }
            db.SetMeta(conn, AutoMarkKey, max.ToString());
            if (categories.Count == 0 || max < mark)
            {
                return new List<long>();
            }
            return conn.Query(
                """
                WITH owners(manga_id, parent_id) AS (
                    SELECT id, id FROM manga WHERE favorite = 1
                    UNION
                    SELECT c.id, r.parent_id FROM merged_ref r
                    JOIN manga c ON c.source = r.child_source AND c.url = r.child_url
                    JOIN manga p ON p.id = r.parent_id
                    WHERE p.favorite = 1
                )
                SELECT ch.id FROM chapter ch JOIN owners o ON o.manga_id = ch.manga_id
                WHERE ch.id > $mark AND ch.id <= $max AND ch.read = 0
                  AND EXISTS (SELECT 1 FROM chapter old WHERE old.manga_id = ch.manga_id AND old.id <= $mark)
                  AND (EXISTS (SELECT 1 FROM manga_category mc WHERE mc.manga_id = o.parent_id AND mc.category_uid IN (SELECT value FROM json_each($cats)))
                       OR ($default = 1 AND NOT EXISTS (SELECT 1 FROM manga_category mc WHERE mc.manga_id = o.parent_id)))
                ORDER BY ch.manga_id, ch.chapter_number
                """,
                ("$mark", mark), ("$max", max), ("$cats", JsonSerializer.Serialize(categories)), ("$default", categories.Contains(0) ? 1 : 0))
                .Select(r => r.Long("id")).ToList();
        }).ConfigureAwait(false);
        if (ids.Count > 0)
        {
            await EnqueueAsync(ids).ConfigureAwait(false);
        }
    }

    private async Task ApplyDeleteAfterReadAsync()
    {
        if (options.DeleteAfterReadKeep is not { } keep)
        {
            return;
        }
        var read = await db.ReadAsync(conn => conn.Query(
            """
            SELECT d.manga_id, d.chapter_url, ch.last_read_at, ch.chapter_number FROM download d
            JOIN chapter ch ON ch.manga_id = d.manga_id AND ch.url = d.chapter_url
            WHERE ch.read = 1
            """)).ConfigureAwait(false);
        var doomed = read
            .GroupBy(r => r.Long("manga_id"))
            .SelectMany(g => g.OrderByDescending(r => r.Long("last_read_at")).ThenByDescending(r => r.Double("chapter_number")).Skip(keep))
            .Select(r => (r.Long("manga_id"), r.Text("chapter_url")))
            .ToList();
        if (doomed.Count > 0)
        {
            await DeleteAsync(doomed).ConfigureAwait(false);
        }
    }

    private List<DownloadedChapter> Scan(SqliteConnection conn, string root, Dictionary<(long, string), DownloadedChapter> previous)
    {
        var disk = DownloadScanner.Enumerate(root).ToList();
        if (disk.Count == 0)
        {
            return [];
        }
        var folders = disk.SelectMany(d => new[]
        {
            (d.SourceFolder.ToUpperInvariant(), d.MangaFolder.ToUpperInvariant()),
            (StripLang(d.SourceFolder.ToUpperInvariant()), d.MangaFolder.ToUpperInvariant()),
        }).ToHashSet();
        var sourceNames = new Dictionary<long, string>();
        var owners = new Dictionary<(string, string), List<long>>();
        foreach (var row in conn.Query("SELECT id, source, title FROM manga"))
        {
            var sourceId = row.Long("source");
            if (!sourceNames.TryGetValue(sourceId, out var sourceFolder))
            {
                sourceFolder = sourceNames[sourceId] = (sources.Get(sourceId) is { } native
                    ? DownloadNames.SourceFolder(native.Name, native.Lang)
                    : DownloadNames.BuildValidFilename(sources.DisplayName(sourceId))).ToUpperInvariant();
            }
            var key = (sourceFolder, DownloadNames.MangaFolder(row.Text("title")).ToUpperInvariant());
            if (folders.Contains(key))
            {
                if (!owners.TryGetValue(key, out var list))
                {
                    owners[key] = list = [];
                }
                list.Add(row.Long("id"));
            }
        }
        var found = new List<DownloadedChapter>();
        var claimed = new HashSet<(long, string)>();
        var names = new Dictionary<long, Dictionary<string, string>>();
        foreach (var chapter in disk)
        {
            var sourceKey = chapter.SourceFolder.ToUpperInvariant();
            var mangaKey = chapter.MangaFolder.ToUpperInvariant();
            var candidates = (owners.GetValueOrDefault((sourceKey, mangaKey)) ?? [])
                .Concat(owners.GetValueOrDefault((StripLang(sourceKey), mangaKey)) ?? [])
                .Distinct();
            var name = chapter.IsArchive ? Path.GetFileNameWithoutExtension(chapter.Name) : chapter.Name;
            foreach (var mangaId in candidates)
            {
                if (!names.TryGetValue(mangaId, out var byName))
                {
                    byName = names[mangaId] = ChapterNames(conn, mangaId);
                }
                if (byName.TryGetValue(name.ToUpperInvariant(), out var url) && claimed.Add((mangaId, url)))
                {
                    var completedAt = previous.TryGetValue((mangaId, url), out var known)
                        ? known.CompletedAt
                        : new DateTimeOffset(File.GetLastWriteTimeUtc(chapter.FullPath)).ToUnixTimeMilliseconds();
                    found.Add(DownloadScanner.Describe(mangaId, url, root, chapter.FullPath, completedAt));
                    break;
                }
            }
        }
        return found;
    }

    private static Dictionary<string, string> ChapterNames(SqliteConnection conn, long mangaId)
    {
        var byName = new Dictionary<string, string>();
        foreach (var row in conn.Query("SELECT url, name, scanlator FROM chapter WHERE manga_id = $m ORDER BY source_order DESC", ("$m", mangaId)))
        {
            var url = row.Text("url");
            foreach (var candidate in DownloadNames.ChapterCandidates(row.Text("name"), row.TextOrNull("scanlator"), url))
            {
                var key = candidate.EndsWith(".cbz", StringComparison.Ordinal) ? candidate[..^4] : candidate;
                byName.TryAdd(key.ToUpperInvariant(), url);
            }
        }
        return byName;
    }

    private static string StripLang(string folder)
    {
        var open = folder.LastIndexOf(" (", StringComparison.Ordinal);
        return open > 0 && folder.EndsWith(')') ? folder[..open] : folder;
    }

    private static List<Entry> LoadQueue(SqliteConnection conn) => conn.Query(
        """
        SELECT q.id, q.manga_id, q.chapter_url, q.position, q.state, q.error, q.pages_done, q.pages_total,
               ch.id AS chapter_id, ch.name, ch.chapter_number, m.source, m.title
        FROM download_queue q JOIN manga m ON m.id = q.manga_id
        JOIN chapter ch ON ch.manga_id = q.manga_id AND ch.url = q.chapter_url
        ORDER BY q.position
        """).Select(r => new Entry
    {
        Id = r.Long("id"),
        MangaId = r.Long("manga_id"),
        ChapterUrl = r.Text("chapter_url"),
        ChapterId = r.Long("chapter_id"),
        Source = r.Long("source"),
        MangaTitle = r.Text("title"),
        ChapterName = r.Text("name"),
        ChapterNumber = r.Double("chapter_number"),
        Position = r.Long("position"),
        State = r.Long("state") == (long)DownloadState.Failed ? DownloadState.Failed : DownloadState.Queued,
        Error = r.TextOrNull("error"),
        PagesDone = (int)r.Long("pages_done"),
        PagesTotal = (int)r.Long("pages_total"),
    }).ToList();

    private static List<DownloadedChapter> LoadIndex(SqliteConnection conn) => conn.Query(
        "SELECT manga_id, chapter_url, path, pages, bytes, completed_at FROM download")
        .Select(r => new DownloadedChapter(r.Long("manga_id"), r.Text("chapter_url"), r.Text("path"), (int)r.Long("pages"), r.Long("bytes"), r.Long("completed_at")))
        .ToList();

    private static void Insert(SqliteConnection conn, DownloadedChapter chapter) => conn.ExecuteNonQuery(
        """
        INSERT INTO download(manga_id, chapter_url, path, pages, bytes, completed_at) VALUES ($m, $u, $p, $n, $b, $c)
        ON CONFLICT(manga_id, chapter_url) DO UPDATE SET path = $p, pages = $n, bytes = $b, completed_at = $c
        """,
        ("$m", chapter.MangaId), ("$u", chapter.ChapterUrl), ("$p", chapter.Path), ("$n", chapter.Pages), ("$b", chapter.Bytes), ("$c", chapter.CompletedAt));

    private void Raise() => Changed?.Invoke(this, EventArgs.Empty);

    private async Task Guard(Func<Task> work)
    {
        try
        {
            await work().ConfigureAwait(false);
        }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            BackgroundError?.Invoke(this, e);
        }
    }

    private const string Schema = """
        CREATE TABLE IF NOT EXISTS download(
            manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
            chapter_url TEXT NOT NULL,
            path TEXT NOT NULL,
            pages INTEGER NOT NULL DEFAULT 0,
            bytes INTEGER NOT NULL DEFAULT 0,
            completed_at INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(manga_id, chapter_url)
        );
        CREATE TABLE IF NOT EXISTS download_queue(
            id INTEGER PRIMARY KEY,
            manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
            chapter_url TEXT NOT NULL,
            position INTEGER NOT NULL,
            state INTEGER NOT NULL DEFAULT 0,
            error TEXT,
            pages_done INTEGER NOT NULL DEFAULT 0,
            pages_total INTEGER NOT NULL DEFAULT 0,
            added_at INTEGER NOT NULL DEFAULT 0,
            UNIQUE(manga_id, chapter_url)
        );
        """;

    private sealed class EntryProgress(DownloadQueue owner, Entry entry) : IProgress<(int Done, int Total)>
    {
        public void Report((int Done, int Total) value)
        {
            lock (owner.gate)
            {
                entry.PagesDone = value.Done;
                entry.PagesTotal = value.Total;
            }
            owner.Raise();
        }
    }

    private sealed class Entry
    {
        public long Id { get; set; }
        public long MangaId { get; init; }
        public required string ChapterUrl { get; init; }
        public long ChapterId { get; init; }
        public long Source { get; init; }
        public required string MangaTitle { get; init; }
        public required string ChapterName { get; init; }
        public double ChapterNumber { get; init; }
        public long Position { get; set; }
        public DownloadState State { get; set; }
        public string? Error { get; set; }
        public int PagesDone { get; set; }
        public int PagesTotal { get; set; }
        public CancellationTokenSource? Cts { get; set; }
        public bool Removed { get; set; }

        public (long, string) Key => (MangaId, ChapterUrl);

        public DownloadItem Snapshot() => new(Id, MangaId, ChapterUrl, ChapterId, Source, MangaTitle, ChapterName, ChapterNumber,
            State, PagesDone, PagesTotal, Error);
    }
}
