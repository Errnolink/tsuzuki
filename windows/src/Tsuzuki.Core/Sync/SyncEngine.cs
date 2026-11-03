using Google.Protobuf;
using Microsoft.Data.Sqlite;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using BackupMessage = Tsuzuki.Core.Proto.Backup;
using static Tsuzuki.Core.Storage.SyncRows;

namespace Tsuzuki.Core.Sync;

internal sealed record EngineResult(
    BackupMessage Backup,
    long Cursor,
    bool Changed,
    bool FullRequested,
    int ItemsReceived,
    IReadOnlyList<long> MangaIds,
    IReadOnlyList<ProgressHandoff> Handoffs,
    int Sent,
    byte[]? Wire = null,
    byte[]? WireGzip = null);

/// <summary>
/// Runs the server-side sync flow against the library database: split, merge, apply under
/// one seq bump, then render a full or delta response. Shared by the HTTP hub and backup
/// restore so both take exactly the same merge path.
/// </summary>
internal sealed class SyncEngine
{
    private readonly LibraryDatabase _db;
    private readonly SqliteLibraryStore? _store;

    public SyncEngine(LibraryDatabase db, SqliteLibraryStore? store = null)
    {
        _db = db;
        _store = store;
    }

    public async Task<EngineResult> Merge(string deviceId, string deviceName, BackupMessage incoming, long clientCursor,
        bool forceFull, IReadOnlyList<long> deletedCategoryUids, ReadOnlyMemory<byte>? rawIncoming = null, bool chapterDelta = false)
    {
        var previousLatency = System.Runtime.GCSettings.LatencyMode;
        System.Runtime.GCSettings.LatencyMode = System.Runtime.GCLatencyMode.SustainedLowLatency;
        try
        {
            return await MergeCore(deviceId, deviceName, incoming, clientCursor, forceFull, deletedCategoryUids, rawIncoming, chapterDelta).ConfigureAwait(false);
        }
        finally
        {
            System.Runtime.GCSettings.LatencyMode = previousLatency;
        }
    }

    private async Task<EngineResult> MergeCore(string deviceId, string deviceName, BackupMessage incoming, long clientCursor,
        bool forceFull, IReadOnlyList<long> deletedCategoryUids, ReadOnlyMemory<byte>? rawIncoming, bool chapterDelta)
    {
        var items = Split(incoming, rawIncoming);
        var deviceKey = DeviceKey(deviceId, deviceName);
        var deletedCategories = deletedCategoryUids.Select(uid => "uid:" + uid).ToList();

        var expectFull = forceFull || clientCursor == 0;
        if (expectFull)
        {
            var warm = _db.WarmTask;
            if (warm is { IsCompleted: false })
            {
                try
                {
                    await warm.ConfigureAwait(false);
                }
                catch
                {
                }
            }
        }

        var (appliedNewSeq, full, cursor, fullRequested, outcome, mangaIds, handoffs) = await _db.WriteAsync(db =>
        {
            var store = new ItemStore(_db);
            store.EnsureState(db);
            var startSeq = _db.GetSeq(db);
            var c = clientCursor;
            var reqFull = false;
            if (c > startSeq)
            {
                c = 0;
                reqFull = true;
            }
            var isFull = forceFull || c == 0;

            var view = store.LoadView(db, items);
            var mergeOutcome = SyncMerge.Merge(view, new MergeRequest(items, deletedCategories));

            var seq = startSeq;
            IReadOnlyList<long> touchedManga = Array.Empty<long>();
            IReadOnlyList<ProgressHandoff> touchedHandoffs = Array.Empty<ProgressHandoff>();
            if (mergeOutcome.Writes.Count > 0 || mergeOutcome.Tombstones.Count > 0)
            {
                seq = startSeq + 1;
                var applied = store.Apply(db, mergeOutcome, seq, deviceKey, deviceName);
                _db.SetSeq(db, seq);
                _db.RenderCache.Invalidate();
                touchedManga = applied.MangaIds;
                touchedHandoffs = applied.Handoffs;
            }

            store.UpsertDevice(db, deviceKey, deviceName, seq, 2, SqliteLibraryStore.NowMs());
            return (seq, isFull, c, reqFull, mergeOutcome, touchedManga, touchedHandoffs);
        });

        BackupMessage backup;
        bool changed;
        byte[]? wire = null;
        byte[]? wireGzip = null;
        var sent = 0;

        if (full)
        {
            if (_db.RenderCache.TryGet(appliedNewSeq, out var raw, out var gz, out var cachedSent))
            {
                backup = new BackupMessage();
                wire = raw;
                wireGzip = gz;
                sent = cachedSent;
            }
            else
            {
                var epoch = _db.WriteEpoch;
                using var readDb = _db.Open();
                var readStore = new ItemStore(_db);
                backup = readStore.RenderStore(readDb);
                (wire, wireGzip, sent) = Encode(backup);
                if (_db.WriteEpoch == epoch)
                {
                    _db.RenderCache.Fill(appliedNewSeq, wire, wireGzip, sent);
                }
            }
            changed = true;
        }
        else
        {
            using var readDb = _db.Open();
            var readStore = new ItemStore(_db);
            (backup, changed) = BuildDelta(readStore, readDb, cursor, deviceKey, outcome, chapterDelta);
            sent = SentCount(backup);
        }

        return new EngineResult(backup, appliedNewSeq, changed, fullRequested, items.Count, mangaIds, handoffs, sent, wire, wireGzip);
    }

    internal static (byte[] Raw, byte[] Gzip, int Sent) Encode(BackupMessage backup)
    {
        var raw = backup.ToByteArray();
        using var compressed = new MemoryStream();
        using (var gzip = new System.IO.Compression.GZipStream(compressed, System.IO.Compression.CompressionLevel.Fastest, true))
        {
            gzip.Write(raw, 0, raw.Length);
        }
        return (raw, compressed.ToArray(), SentCount(backup));
    }

    internal static int SentCount(BackupMessage backup) =>
        backup.BackupManga.Count
        + backup.BackupManga.Sum(m => m.Chapters.Count)
        + backup.BackupCategories.Count
        + backup.BackupSources.Count
        + backup.BackupPreferences.Count
        + backup.BackupSourcePreferences.Count
        + backup.BackupExtensionStores.Count
        + backup.BackupSavedSearches.Count;

    /// <summary>Renders the whole store. Null when nothing was ever stored for the key.</summary>
    public (BackupMessage? Backup, byte[]? Wire, byte[]? WireGzip, long Seq) Snapshot()
    {
        var db = _db.Open();
        try
        {
            var store = new ItemStore(_db);
            if (!store.StoreExists(db)) return (null, null, null, 0);
            var seq = _db.GetSeq(db);
            if (_db.RenderCache.TryGet(seq, out var raw, out var gz, out _)) return (null, raw, gz, seq);
            return (store.RenderStore(db), null, null, seq);
        }
        finally
        {
            db.Dispose();
        }
    }

    public Task BeginWarmSnapshot()
    {
        lock (_db.SyncGate)
        {
            if (_db.WarmTask is { IsCompleted: false }) return _db.WarmTask;
            _db.WarmTask = Task.Run(() =>
            {
                try
                {
                    WarmSnapshot();
                }
                catch
                {
                }
            });
            return _db.WarmTask;
        }
    }

    public void WarmSnapshot()
    {
        var db = _db.Open();
        try
        {
            var store = new ItemStore(_db);
            if (!store.StoreExists(db)) return;
            var seq = _db.GetSeq(db);
            var epoch = _db.WriteEpoch;
            if (_db.RenderCache.TryGet(seq, out _, out _, out _)) return;
            var backup = store.RenderStore(db);
            if (_db.WriteEpoch != epoch) return;
            var (raw, gz, sent) = Encode(backup);
            if (_db.WriteEpoch != epoch) return;
            _db.RenderCache.Fill(seq, raw, gz, sent);
        }
        finally
        {
            db.Dispose();
        }
    }

    /// <summary>
    /// Applies a backup file through the same merge path the hub uses, attributed to the
    /// origin device "backup". Counts what the merge added or updated.
    /// </summary>
    public async Task<Backup.RestoreResult> Restore(BackupMessage incoming, ReadOnlyMemory<byte>? rawIncoming = null)
    {
        var previousLatency = System.Runtime.GCSettings.LatencyMode;
        System.Runtime.GCSettings.LatencyMode = System.Runtime.GCLatencyMode.SustainedLowLatency;
        try
        {
            return await RestoreCore(incoming, rawIncoming).ConfigureAwait(false);
        }
        finally
        {
            System.Runtime.GCSettings.LatencyMode = previousLatency;
        }
    }

    private async Task<Backup.RestoreResult> RestoreCore(BackupMessage incoming, ReadOnlyMemory<byte>? rawIncoming)
    {
        var items = Split(incoming, rawIncoming);
        var changed = false;
        var result = await _db.WriteAsync(db =>
        {
            var store = new ItemStore(_db);
            store.EnsureState(db);
            var startSeq = _db.GetSeq(db);
            var view = store.LoadView(db, items);
            var outcome = SyncMerge.Merge(view, new MergeRequest(items, Array.Empty<string>()));
            if (outcome.Writes.Count > 0 || outcome.Tombstones.Count > 0)
            {
                var newSeq = startSeq + 1;
                store.Apply(db, outcome, newSeq, "backup", "backup");
                _db.SetSeq(db, newSeq);
                _db.RenderCache.Invalidate();
                changed = true;
            }
            var mangaAdded = outcome.Writes.Count(w => w.Kind == SyncKind.Manga && view.Get(SyncKind.Manga, w.Key) == null);
            var mangaUpdated = outcome.Writes.Count(w => w.Kind == SyncKind.Manga) - mangaAdded;
            var chapters = outcome.Writes.Count(w => w.Kind == SyncKind.Chapter);
            var categories = outcome.Writes.Count(w => w.Kind == SyncKind.Category) + outcome.Tombstones.Count;
            return new Backup.RestoreResult(mangaAdded, mangaUpdated, chapters, categories);
        });
        if (changed && _store is not null)
        {
            var touched = await _db.ReadAsync(db =>
                (IReadOnlyList<long>)db.Query("SELECT id FROM manga").Select(r => r.Long("id")).ToList());
            _store.RaiseChanged(touched, ChangeOrigin.Local, null, Array.Empty<ProgressHandoff>());
        }
        return result;
    }

    public bool StoreExists()
    {
        using var db = _db.Open();
        return new ItemStore(_db).StoreExists(db);
    }

    internal static (BackupMessage Backup, bool Changed) BuildDelta(ItemStore store, SqliteConnection db,
        long cursor, string deviceKey, MergeOutcome outcome, bool chapterDelta = false)
    {
        var changed = outcome.ChangedForClient;
        var want = new Dictionary<(SyncKind, string), SyncItem>();

        void Add(SyncItem item) => want[(item.Kind, item.Key)] = item;

        foreach (var item in store.ItemsSince(db, cursor))
        {
            if (item.Kind == SyncKind.Category)
            {
                Add(item);
                if (item.Seq > cursor && item.Origin != deviceKey) changed = true;
            }
            else if (item.Origin == deviceKey)
            {
                continue;
            }
            else
            {
                Add(item);
                changed = true;
            }
        }

        foreach (var group in outcome.ReturnKeys)
        {
            switch (group.Key)
            {
                case "Manga":
                    foreach (var item in store.LoadMangaItems(db, group.Value)) Add(item);
                    break;
                case "Chapter":
                    foreach (var item in store.LoadChapterItemsByKeys(db, group.Value)) Add(item);
                    break;
                case "Category":
                    foreach (var item in store.LoadAllCategories(db))
                    {
                        if (group.Value.Contains(item.Key)) Add(item);
                    }
                    break;
                default:
                    foreach (var item in store.LoadSections(db, group.Key, group.Value)) Add(item);
                    break;
            }
        }

        var parents = want.Values
            .Where(i => i.Kind == SyncKind.Chapter && !want.ContainsKey((SyncKind.Manga, i.ParentKey)))
            .Select(i => i.ParentKey)
            .Distinct()
            .ToList();
        foreach (var item in store.LoadMangaItems(db, parents)) Add(item);

        var mangaKeys = want.Values.Where(i => i.Kind == SyncKind.Manga).Select(i => i.Key).ToList();
        if (chapterDelta)
        {
            // Capped devices merge chapters by URL, so only chapters that moved since
            // the cursor travel; unchanged ones stay as the device has them. Category
            // rows still ride when anything category-shaped moved past the cursor (the
            // echoed list drives the client delete path); a quiet category floor is
            // dropped.
            if (!store.CategoryActivitySince(db, cursor))
            {
                foreach (var key in want.Keys.Where(k => k.Item1 == SyncKind.Category).ToList())
                {
                    want.Remove(key);
                }
            }
        }
        else
        {
            foreach (var chapter in store.LoadChapterItems(db, mangaKeys)) Add(chapter);
        }

        return (Render(want.Values), changed);
    }

    internal static string DeviceKey(string deviceId, string deviceName) =>
        deviceId.Length > 0 ? deviceId : deviceName;
}
