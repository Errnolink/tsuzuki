using System.Collections.Concurrent;
using System.Net;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;

namespace Tsuzuki.Core;

public sealed record EngineOptions(bool DataSaver);

public static class EngineFactory
{
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

        var registry = new MutableRegistry(http, Options(options), id => SourceName(db, id));
        var store = new SqliteLibraryStore(db, id => registry.Get(id) is not null);
        var covers = new DiskImageCache(paths.CoverCache, http, 1L << 30);
        var pages = new DiskImageCache(paths.PageCache, http, 3L << 29, visibleConcurrency: 6, backgroundConcurrency: 4);
        MangaDexAtHomeReporter.Attach(pages, http);
        var hub = new SyncHub(db, store);
        var backup = new BackupService(db, store);
        _ = Task.Run(covers.Trim);
        _ = Task.Run(pages.Trim);
        var engine = new Engine(paths, store, registry, covers, pages, hub, backup, http);
        Registries[engine] = registry;
        return engine;
    }

    public static void ApplyOptions(Engine engine, EngineOptions options)
    {
        if (Registries.TryGetValue(engine, out var registry))
        {
            registry.Rebuild(Options(options));
        }
    }

    private static MangaDexOptions Options(EngineOptions options) => new() { DataSaver = options.DataSaver };

    private static readonly ConcurrentDictionary<long, string?> Names = new();

    private static string? SourceName(LibraryDatabase db, long id) => Names.GetOrAdd(id, key =>
    {
        using var connection = db.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT payload FROM section WHERE kind = 'source' AND key = $key";
        command.Parameters.AddWithValue("$key", key.ToString());
        return command.ExecuteScalar() is byte[] payload ? BackupSource.Parser.ParseFrom(payload).Name : null;
    });

    private sealed class MutableRegistry(HttpClient http, MangaDexOptions initial, Func<long, string?> names) : ISourceRegistry
    {
        private SourceRegistry inner = new(MangaDexSources.Create(http, initial), names);

        public IReadOnlyList<ISource> All => inner.All;
        public ISource? Get(long sourceId) => inner.Get(sourceId);
        public string DisplayName(long sourceId) => inner.DisplayName(sourceId);

        public void Rebuild(MangaDexOptions options) => inner = new SourceRegistry(MangaDexSources.Create(http, options), names);
    }
}
