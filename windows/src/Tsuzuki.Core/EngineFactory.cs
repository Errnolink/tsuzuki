using System.Collections.Concurrent;
using System.Net;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Downloads;
using Tsuzuki.Core.Extensions;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Tsuzuki.Core.Sources.WeebCentral;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;

namespace Tsuzuki.Core;

public sealed record EngineOptions(bool DataSaver, DownloadOptions? Downloads = null, Func<bool>? IsMetered = null, Tsuzuki.Core.Tracking.ITrackerCredentials? TrackerCredentials = null);

public static class EngineFactory
{
    private static readonly TimeSpan TrimDelay = TimeSpan.FromSeconds(60);
    private static readonly ConcurrentDictionary<Engine, MutableRegistry> Registries = new();

    public static async Task<Engine> OpenAsync(AppPaths paths, EngineOptions options)
    {
        paths.EnsureCreated();
        var http = new HttpClient(new SocketsHttpHandler
        {
            AutomaticDecompression = DecompressionMethods.All,
            PooledConnectionLifetime = TimeSpan.FromMinutes(5),
            MaxConnectionsPerServer = 8,
        })
        {
            Timeout = TimeSpan.FromSeconds(40),
        };
        http.DefaultRequestHeaders.UserAgent.ParseAdd(MangaDexClient.UserAgent);

        var db = new LibraryDatabase(paths.Database);
        await Task.Run(db.Initialise).ConfigureAwait(false);

        var registry = new MutableRegistry(new MangaDexClient(http), new WeebCentralSource(http), Options(options), id => SourceName(db, id));
        var store = new SqliteLibraryStore(db, registry.IsReadable);
        var covers = new DiskImageCache(paths.CoverCache, http, registry.Get, 1L << 30, gate: new HostGate(4, TimeSpan.FromMilliseconds(120)));
        var pages = new DiskImageCache(paths.PageCache, http, registry.Get, 3L << 29, visibleConcurrency: 4, backgroundConcurrency: 2);
        MangaDexAtHomeReporter.Attach(pages, http);
        var hub = new SyncHub(db, store, AppIdentity.HubPort);
        var backup = new BackupService(db, store);
        _ = Task.Run(async () =>
        {
            await Task.Delay(TrimDelay).ConfigureAwait(false);
            covers.Trim();
            pages.Trim();
        });
        var downloads = new DownloadQueue(db, store, registry, pages, covers, Path.Combine(paths.Root, "cache", "local"),
            options.Downloads ?? new DownloadOptions { Root = DownloadQueue.DefaultRoot },
            new DownloadEnvironment { IsMetered = options.IsMetered ?? (() => false) });
        var extensions = new ExtensionManager(paths.Root, http, db, registry, Path.Combine(AppContext.BaseDirectory, "extension-host", "tsuzuki-extension-host.jar"));
        var credentials = options.TrackerCredentials ?? throw new ArgumentException("Tracker credentials are required.", nameof(options));
        var tracking = new Tsuzuki.Core.Tracking.TrackService(db, store,
            [new Tsuzuki.Core.Tracking.MangaUpdatesTracker(http, credentials), new Tsuzuki.Core.Tracking.KitsuTracker(http, credentials)]);
        var engine = new Engine(paths, store, new SqliteMergeEditor(db, store), registry, covers, pages, hub, backup, http, downloads, extensions, tracking);
        Registries[engine] = registry;
        return engine;
    }

    public static void ApplyOptions(Engine engine, EngineOptions options)
    {
        if (Registries.TryGetValue(engine, out var registry))
        {
            registry.Rebuild(Options(options));
        }
        if (options.Downloads is { } downloads)
        {
            engine.Downloads.Configure(downloads);
        }
    }

    private static MangaDexOptions Options(EngineOptions options) => new() { DataSaver = options.DataSaver };

    private static readonly ConcurrentDictionary<long, string> Names = new();

    private static string? SourceName(LibraryDatabase db, long id)
    {
        if (Names.TryGetValue(id, out var cached)) return cached;
        try
        {
            using var connection = db.Open();
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT payload FROM section WHERE kind = 'source' AND key = $key";
            command.Parameters.AddWithValue("$key", id.ToString());
            if (command.ExecuteScalar() is not byte[] payload) return null;
            var name = BackupSource.Parser.ParseFrom(payload).Name;
            if (name.Length > 0) Names[id] = name;
            return name.Length > 0 ? name : null;
        }
        catch (Microsoft.Data.Sqlite.SqliteException)
        {
            return null;
        }
    }

    private sealed class MutableRegistry : ISourceRegistry, IExtensionSourceSink
    {
        private readonly MangaDexClient mangaDex;
        private readonly WeebCentralSource weebCentral;
        private readonly Func<long, string?> names;
        private readonly ConcurrentDictionary<long, ISource> extras = new();
        private readonly object gate = new();
        private IReadOnlyList<ISource> natives;
        private HashSet<long> nativeIds;
        private SourceRegistry inner;

        public MutableRegistry(MangaDexClient mangaDex, WeebCentralSource weebCentral, MangaDexOptions options, Func<long, string?> names)
        {
            this.mangaDex = mangaDex;
            this.weebCentral = weebCentral;
            this.names = names;
            natives = [.. MangaDexSources.Create(mangaDex, options), weebCentral];
            nativeIds = [.. natives.Select(s => s.Id)];
            inner = Compose();
        }

        public IReadOnlyList<ISource> All => inner.All;
        public ISource? Get(long sourceId) => inner.Get(sourceId);
        public bool IsReadable(long sourceId) => inner.IsReadable(sourceId);
        public string DisplayName(long sourceId) => inner.DisplayName(sourceId);

        public bool IsNative(long sourceId) => nativeIds.Contains(sourceId);

        public void Register(ISource source)
        {
            extras[source.Id] = source;
            Recompose();
        }

        public void Unregister(long sourceId)
        {
            extras.TryRemove(sourceId, out _);
            Recompose();
        }

        public void Rebuild(MangaDexOptions options)
        {
            lock (gate)
            {
                natives = [.. MangaDexSources.Create(mangaDex, options), weebCentral];
                nativeIds = [.. natives.Select(s => s.Id)];
                inner = Compose();
            }
        }

        private void Recompose()
        {
            lock (gate)
            {
                inner = Compose();
            }
        }

        private SourceRegistry Compose() =>
            new([.. natives, .. extras.Values.Where(s => !nativeIds.Contains(s.Id)).OrderBy(s => s.Name, StringComparer.CurrentCultureIgnoreCase).ThenBy(s => s.Lang)], names);
    }
}
