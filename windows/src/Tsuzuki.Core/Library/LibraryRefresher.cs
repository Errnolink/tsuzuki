using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Library;

public sealed record UpdateRunResult(int Checked, int NewChapters, int Failed);

public sealed class LibraryRefresher(ILibraryStore store, ISourceRegistry sources)
{
    public const long MergedSourceId = 6969;

    private readonly SemaphoreSlim gate = new(1, 1);
    private readonly SemaphoreSlim refreshGate = new(1, 1);

    public async Task<ChapterSyncResult?> RefreshAsync(long mangaId, bool includeDetails, CancellationToken ct = default)
    {
        var manga = await store.GetMangaAsync(mangaId, ct).ConfigureAwait(false);
        if (manga is null)
        {
            return null;
        }
        if (manga.Source == MergedSourceId)
        {
            var added = 0;
            var updated = 0;
            var removed = 0;
            var any = false;
            foreach (var child in manga.MergedRefs)
            {
                var childId = await store.FindMangaIdAsync(child.Source, child.Url, ct).ConfigureAwait(false);
                if (childId is null || sources.Get(child.Source) is null)
                {
                    continue;
                }
                var result = await RefreshSingleAsync(childId.Value, child.Source, child.Url, includeDetails && child.IsInfoManga, ct).ConfigureAwait(false);
                if (result is not null)
                {
                    any = true;
                    added += result.Added;
                    updated += result.Updated;
                    removed += result.Removed;
                }
            }
            return any ? new ChapterSyncResult(added, updated, removed) : null;
        }
        return await RefreshSingleAsync(manga.MangaId, manga.Source, manga.Url, includeDetails, ct).ConfigureAwait(false);
    }

    private async Task<ChapterSyncResult?> RefreshSingleAsync(long mangaId, long sourceId, string url, bool includeDetails, CancellationToken ct)
    {
        var source = sources.Get(sourceId);
        if (source is null)
        {
            return null;
        }
        if (includeDetails)
        {
            var details = await source.GetMangaDetailsAsync(url, ct).ConfigureAwait(false);
            await store.UpsertFromSourceAsync(sourceId, details with { Url = url }, ct).ConfigureAwait(false);
        }
        var chapters = await source.GetChapterListAsync(url, ct).ConfigureAwait(false);
        return await store.SyncChaptersFromSourceAsync(mangaId, chapters, ct).ConfigureAwait(false);
    }

    public async Task<long> AddToLibraryAsync(long sourceId, SManga manga, CancellationToken ct = default)
    {
        var id = await store.UpsertFromSourceAsync(sourceId, manga, ct).ConfigureAwait(false);
        await store.SetFavoriteAsync(id, true, ct).ConfigureAwait(false);
        return id;
    }

    public async Task<ChapterSyncResult?> RefreshQueuedAsync(long mangaId, CancellationToken ct = default)
    {
        await refreshGate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            return await RefreshAsync(mangaId, includeDetails: false, ct).ConfigureAwait(false);
        }
        finally
        {
            refreshGate.Release();
        }
    }

    public async Task<UpdateRunResult> RefreshLibraryAsync(IProgress<(int Done, int Total)>? progress = null, CancellationToken ct = default)
    {
        if (!await gate.WaitAsync(0, ct).ConfigureAwait(false))
        {
            return new UpdateRunResult(0, 0, 0);
        }
        try
        {
            var library = await store.GetLibraryAsync(ct).ConfigureAwait(false);
            var targets = library.Where(e => e.Readable || e.IsMerged).ToList();
            var done = 0;
            var added = 0;
            var failed = 0;
            await Parallel.ForEachAsync(targets, new ParallelOptions { MaxDegreeOfParallelism = 3, CancellationToken = ct }, async (entry, token) =>
            {
                try
                {
                    var result = await RefreshAsync(entry.MangaId, includeDetails: false, token).ConfigureAwait(false);
                    if (result is not null)
                    {
                        Interlocked.Add(ref added, result.Added);
                    }
                }
                catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or InvalidOperationException)
                {
                    Interlocked.Increment(ref failed);
                }
                progress?.Report((Interlocked.Increment(ref done), targets.Count));
            }).ConfigureAwait(false);
            return new UpdateRunResult(targets.Count, added, failed);
        }
        finally
        {
            gate.Release();
        }
    }
}
