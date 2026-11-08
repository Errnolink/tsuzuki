using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;

namespace Tsuzuki.Core.Tracking;

public sealed class TrackService
{
    private const string Schema = """
        CREATE TABLE IF NOT EXISTS track_queue(
            manga_id INTEGER NOT NULL,
            sync_id INTEGER NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            next_at INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(manga_id, sync_id))
        """;

    private readonly LibraryDatabase db;
    private readonly SqliteLibraryStore store;
    private readonly TimeProvider time;
    private readonly SemaphoreSlim drainGate = new(1, 1);
    private bool ready;

    public TrackService(LibraryDatabase db, SqliteLibraryStore store, IReadOnlyList<ITracker> trackers, TimeProvider? clock = null)
    {
        this.db = db;
        this.store = store;
        Trackers = trackers;
        time = clock ?? TimeProvider.System;
    }

    public IReadOnlyList<ITracker> Trackers { get; }

    public bool UpdateAfterReading { get; set; } = true;

    public ITracker? Tracker(int syncId) => Trackers.FirstOrDefault(t => t.Id == syncId);

    public Task<IReadOnlyList<BackupTracking>> GetAsync(long mangaId, CancellationToken ct = default) => store.GetTrackingAsync(mangaId, ct);

    public async Task ChapterReadAsync(long mangaId, double chapterNumber, int totalChapters = 0, CancellationToken ct = default)
    {
        if (!UpdateAfterReading || chapterNumber <= 0)
        {
            return;
        }
        var touched = new List<int>();
        var now = time.GetUtcNow().ToUnixTimeMilliseconds();
        await store.EditTrackingAsync(mangaId, tracks =>
        {
            foreach (var track in tracks)
            {
                if (!TrackerIds.FollowsReading(track.SyncId) || chapterNumber <= track.LastChapterRead)
                {
                    continue;
                }
                track.LastChapterRead = (float)chapterNumber;
                var tracker = Tracker(track.SyncId);
                if (track.StartedReadingDate == 0)
                {
                    track.StartedReadingDate = now;
                }
                if (tracker is not null)
                {
                    var total = track.TotalChapters > 0 ? track.TotalChapters : totalChapters;
                    if (total > 0 && chapterNumber >= total)
                    {
                        track.Status = tracker.CompletedStatus;
                        if (track.FinishedReadingDate == 0)
                        {
                            track.FinishedReadingDate = now;
                        }
                    }
                    else if (track.Status == tracker.PlanStatus)
                    {
                        track.Status = tracker.ReadingStatus;
                    }
                }
                touched.Add(track.SyncId);
            }
            return touched.Count > 0;
        }, ct).ConfigureAwait(false);
        foreach (var syncId in touched)
        {
            await EnqueueAsync(mangaId, syncId).ConfigureAwait(false);
        }
        await DrainAsync(ct).ConfigureAwait(false);
    }

    public async Task<BackupTracking> BindAsync(long mangaId, ITracker tracker, TrackSearchResult result, double highestRead, CancellationToken ct = default)
    {
        var track = await tracker.BindAsync(result, ct).ConfigureAwait(false);
        var pushNeeded = false;
        if (highestRead > track.LastChapterRead)
        {
            track.LastChapterRead = (float)highestRead;
            if (track.Status == tracker.PlanStatus)
            {
                track.Status = tracker.ReadingStatus;
            }
            if (track.StartedReadingDate == 0)
            {
                track.StartedReadingDate = time.GetUtcNow().ToUnixTimeMilliseconds();
            }
            pushNeeded = true;
        }
        await store.EditTrackingAsync(mangaId, tracks =>
        {
            for (var i = tracks.Count - 1; i >= 0; i--)
            {
                if (tracks[i].SyncId == tracker.Id)
                {
                    tracks.RemoveAt(i);
                }
            }
            tracks.Add(track);
            return true;
        }, ct).ConfigureAwait(false);
        if (pushNeeded)
        {
            await EnqueueAsync(mangaId, tracker.Id).ConfigureAwait(false);
            await DrainAsync(ct).ConfigureAwait(false);
        }
        return track;
    }

    public Task UnbindAsync(long mangaId, int syncId, CancellationToken ct = default) =>
        store.EditTrackingAsync(mangaId, tracks =>
        {
            var removed = false;
            for (var i = tracks.Count - 1; i >= 0; i--)
            {
                if (tracks[i].SyncId == syncId)
                {
                    tracks.RemoveAt(i);
                    removed = true;
                }
            }
            return removed;
        }, ct);

    public async Task SaveAsync(long mangaId, BackupTracking edited, CancellationToken ct = default)
    {
        await store.EditTrackingAsync(mangaId, tracks =>
        {
            for (var i = 0; i < tracks.Count; i++)
            {
                if (tracks[i].SyncId == edited.SyncId)
                {
                    tracks[i] = edited.Clone();
                    return true;
                }
            }
            return false;
        }, ct).ConfigureAwait(false);
        await EnqueueAsync(mangaId, edited.SyncId).ConfigureAwait(false);
        await DrainAsync(ct).ConfigureAwait(false);
    }

    public async Task RefreshAsync(long mangaId, CancellationToken ct = default)
    {
        var tracks = await store.GetTrackingAsync(mangaId, ct).ConfigureAwait(false);
        var remote = new Dictionary<int, BackupTracking>();
        foreach (var track in tracks)
        {
            if (Tracker(track.SyncId) is { SignedIn: true } tracker)
            {
                try
                {
                    remote[track.SyncId] = await tracker.RefreshAsync(track, ct).ConfigureAwait(false);
                }
                catch (HttpRequestException)
                {
                }
            }
        }
        if (remote.Count == 0)
        {
            return;
        }
        var behind = new List<int>();
        await store.EditTrackingAsync(mangaId, list =>
        {
            var changed = false;
            foreach (var track in list)
            {
                if (!remote.TryGetValue(track.SyncId, out var fresh))
                {
                    continue;
                }
                if (fresh.LastChapterRead < track.LastChapterRead)
                {
                    behind.Add(track.SyncId);
                }
                var progress = Math.Max(fresh.LastChapterRead, track.LastChapterRead);
                if (track.Status != fresh.Status || track.Score != fresh.Score || track.LastChapterRead != progress)
                {
                    track.Status = fresh.Status;
                    track.Score = fresh.Score;
                    track.LastChapterRead = progress;
                    changed = true;
                }
            }
            return changed;
        }, ct).ConfigureAwait(false);
        foreach (var syncId in behind)
        {
            await EnqueueAsync(mangaId, syncId).ConfigureAwait(false);
        }
        if (behind.Count > 0)
        {
            await DrainAsync(ct).ConfigureAwait(false);
        }
    }

    public async Task DrainAsync(CancellationToken ct = default)
    {
        await EnsureSchemaAsync().ConfigureAwait(false);
        if (!await drainGate.WaitAsync(0, ct).ConfigureAwait(false))
        {
            return;
        }
        try
        {
            var now = time.GetUtcNow().ToUnixTimeMilliseconds();
            var due = await db.ReadAsync(conn => conn.Query("SELECT manga_id, sync_id, attempts FROM track_queue WHERE next_at <= $now", ("$now", now))
                .Select(r => (MangaId: r.Long("manga_id"), SyncId: (int)r.Long("sync_id"), Attempts: r.Long("attempts"))).ToList()).ConfigureAwait(false);
            foreach (var (mangaId, syncId, attempts) in due)
            {
                var tracker = Tracker(syncId);
                var track = (await store.GetTrackingAsync(mangaId, ct).ConfigureAwait(false)).FirstOrDefault(t => t.SyncId == syncId);
                if (tracker is null || track is null)
                {
                    await RemoveAsync(mangaId, syncId).ConfigureAwait(false);
                    continue;
                }
                if (!tracker.SignedIn)
                {
                    continue;
                }
                try
                {
                    await tracker.UpdateAsync(track, ct).ConfigureAwait(false);
                    await RemoveAsync(mangaId, syncId).ConfigureAwait(false);
                }
                catch (Exception e) when (e is HttpRequestException or TaskCanceledException && !ct.IsCancellationRequested)
                {
                    var delay = TimeSpan.FromMinutes(Math.Min(360, Math.Pow(2, attempts)));
                    await db.WriteAsync(conn => conn.ExecuteNonQuery("UPDATE track_queue SET attempts = attempts + 1, next_at = $next WHERE manga_id = $m AND sync_id = $s",
                        ("$next", now + (long)delay.TotalMilliseconds), ("$m", mangaId), ("$s", syncId))).ConfigureAwait(false);
                }
            }
        }
        finally
        {
            drainGate.Release();
        }
    }

    public async Task<int> PendingAsync()
    {
        await EnsureSchemaAsync().ConfigureAwait(false);
        return (int)await db.ReadAsync(conn => conn.Scalar<long>("SELECT COUNT(*) FROM track_queue")).ConfigureAwait(false);
    }

    private async Task EnqueueAsync(long mangaId, int syncId)
    {
        await EnsureSchemaAsync().ConfigureAwait(false);
        await db.WriteAsync(conn => conn.ExecuteNonQuery("""
            INSERT INTO track_queue(manga_id, sync_id, attempts, next_at) VALUES ($m, $s, 0, 0)
            ON CONFLICT(manga_id, sync_id) DO UPDATE SET next_at = 0
            """, ("$m", mangaId), ("$s", syncId))).ConfigureAwait(false);
    }

    private Task RemoveAsync(long mangaId, int syncId) =>
        db.WriteAsync(conn => conn.ExecuteNonQuery("DELETE FROM track_queue WHERE manga_id = $m AND sync_id = $s", ("$m", mangaId), ("$s", syncId)));

    private async Task EnsureSchemaAsync()
    {
        if (ready)
        {
            return;
        }
        await db.WriteAsync(conn => conn.ExecuteNonQuery(Schema)).ConfigureAwait(false);
        ready = true;
    }
}
