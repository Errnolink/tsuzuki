using System.IO.Compression;
using Google.Protobuf;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Backup;

public sealed class BackupService : IBackupService
{
    private readonly LibraryDatabase _db;
    private readonly Sync.SyncEngine _engine;
    private readonly Func<IReadOnlyList<IBackupTarget>>? _targetProbe;
    private readonly Lock _discoveryLock = new();
    private Task<IReadOnlyList<IBackupTarget>>? _discovery;
    private long _discoveredAt;

    public BackupService(LibraryDatabase db, SqliteLibraryStore? store = null)
    {
        _db = db;
        _engine = new Sync.SyncEngine(db, store);
    }

    public BackupService(LibraryDatabase db, Func<IReadOnlyList<IBackupTarget>> targetProbe) : this(db)
    {
        _targetProbe = targetProbe;
    }

    public Task<IReadOnlyList<IBackupTarget>> DiscoverTargetsAsync()
    {
        lock (_discoveryLock)
        {
            var fresh = Environment.TickCount64 - _discoveredAt < (long)TimeSpan.FromMinutes(5).TotalMilliseconds;
            if (fresh && _discovery is { IsFaulted: false, IsCanceled: false } cached) return cached;
            _discoveredAt = Environment.TickCount64;
            return _discovery = Task.Run(Probe);
        }
    }

    private IReadOnlyList<IBackupTarget> Probe()
    {
        if (_targetProbe != null) return _targetProbe();
        var targets = new List<IBackupTarget> { new LocalFolderTarget(AppPaths.Default.Backups) };
        if (DriveForDesktopTarget.Locate() is { } drive) targets.Add(drive);
        return targets;
    }

    public async Task<BackupResult> CreateAsync(IBackupTarget target, CancellationToken ct = default)
    {
        var backup = await RenderLibrary().ConfigureAwait(false);
        var name = BackupFiles.Prefix + DateTimeOffset.Now.ToString("yyyy-MM-dd_HH-mm") + BackupFiles.Extension;
        using var buffer = new MemoryStream();
        using (var gzip = new GZipStream(buffer, CompressionLevel.Fastest, leaveOpen: true))
        {
            gzip.Write(backup.ToByteArray());
        }
        buffer.Seek(0, SeekOrigin.Begin);
        var location = await target.WriteAsync(name, buffer, ct).ConfigureAwait(false);
        return new BackupResult(location, backup.BackupManga.Count, buffer.Length);
    }

    public async Task<RestoreResult> RestoreAsync(Stream tachibk, CancellationToken ct = default)
    {
        using var input = new MemoryStream();
        await tachibk.CopyToAsync(input, ct).ConfigureAwait(false);
        var data = input.ToArray();
        if (data.Length >= 2 && data[0] == 0x1f && data[1] == 0x8b)
        {
            using var gzip = new GZipStream(new MemoryStream(data), CompressionMode.Decompress);
            using var output = new MemoryStream();
            await gzip.CopyToAsync(output, ct).ConfigureAwait(false);
            data = output.ToArray();
        }
        var backup = data.Length == 0 ? new BackupMessage() : BackupMessage.Parser.ParseFrom(data);
        return await _engine.Restore(backup, data).ConfigureAwait(false);
    }

    internal async Task<BackupMessage> RenderLibrary()
    {
        return await _db.ReadAsync(db =>
        {
            var store = new ItemStore(_db);
            var keys = new List<string>();
            var seen = new HashSet<string>();
            foreach (var row in db.Query("""
                    SELECT source, url FROM manga
                    WHERE favorite = 1 OR source = 6969
                       OR (source, url) IN (SELECT child_source, child_url FROM merged_ref)
                    """))
            {
                var key = SyncRows.MangaKey(row.Long("source"), row.Text("url"));
                if (seen.Add(key)) keys.Add(key);
            }

            var items = new List<SyncItem>();
            items.AddRange(store.LoadMangaItems(db, keys));
            items.AddRange(store.LoadChapterItems(db, keys));
            items.AddRange(store.LoadAllCategories(db));
            foreach (var kind in new[] { "source", "app_pref", "source_pref", "ext_store", "saved_search" })
            {
                items.AddRange(store.LoadSections(db, kind,
                    db.Query("SELECT key FROM section WHERE kind = $k", ("$k", kind))
                        .Select(r => r.Text("key")).ToList()));
            }
            foreach (var kind in db.Query("SELECT DISTINCT kind FROM section WHERE kind LIKE 'field:%'")
                .Select(r => r.Text("kind")).ToList())
            {
                items.AddRange(store.LoadSections(db, kind,
                    db.Query("SELECT key FROM section WHERE kind = $k", ("$k", kind))
                        .Select(r => r.Text("key")).ToList()));
            }

            return SyncRows.Render(items);
        });
    }
}
