using System.Collections.Concurrent;
using System.Text.Json;
using System.Text.Json.Nodes;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;

namespace Tsuzuki.Core.Extensions;

public interface IExtensionSourceSink
{
    bool IsNative(long sourceId);
    void Register(ISource source);
    void Unregister(long sourceId);
}

public sealed record CatalogEntry(ExtensionStore Store, StoreExtension Extension, InstalledExtension? Installed)
{
    public bool HasUpdate => Installed is { } installed && Extension.VersionCode > installed.VersionCode;
}

public sealed record SyncedStore(string IndexUrl, string Name);

public sealed class ExtensionManager : IAsyncDisposable
{
    private readonly string root;
    private readonly HttpClient http;
    private readonly LibraryDatabase db;
    private readonly IExtensionSourceSink sink;
    private readonly ExtensionHost host;
    private readonly SemaphoreSlim installGate = new(1, 1);
    private readonly ConcurrentDictionary<string, int> loadedIn = new();
    private readonly ConcurrentDictionary<string, SemaphoreSlim> loading = new();
    private readonly object gate = new();
    private List<ExtensionStore> stores;
    private List<InstalledExtension> installed;
    private IReadOnlyDictionary<string, StoreIndex> indexes = new Dictionary<string, StoreIndex>();

    public ExtensionManager(string dataRoot, HttpClient http, LibraryDatabase db, IExtensionSourceSink sink, string hostJar)
    {
        root = Path.Combine(dataRoot, "extensions");
        this.http = http;
        this.db = db;
        this.sink = sink;
        Runtime = new ExtensionRuntime(Path.Combine(root, "runtime"), hostJar);
        host = new ExtensionHost(Runtime, root);
        TempRoot = Path.Combine(root, "tmp");
        stores = [.. ExtensionStores.Load(StoresPath)];
        installed = [.. LoadInstalled()];
        foreach (var extension in installed)
        {
            RegisterSources(extension);
        }
    }

    public event EventHandler? Changed;

    public ExtensionRuntime Runtime { get; }

    public string TempRoot { get; }

    public IReadOnlyList<ExtensionStore> Stores
    {
        get
        {
            lock (gate)
            {
                return [.. stores];
            }
        }
    }

    public IReadOnlyList<InstalledExtension> Installed
    {
        get
        {
            lock (gate)
            {
                return [.. installed];
            }
        }
    }

    private string StoresPath => Path.Combine(root, "stores.json");
    private string InstalledPath => Path.Combine(root, "installed.json");

    public async Task<ExtensionStore> AddStoreAsync(string input, CancellationToken ct = default)
    {
        var (indexUrl, index) = await ExtensionStores.ResolveAsync(http, input, ct).ConfigureAwait(false);
        var store = new ExtensionStore(indexUrl.ToString(), string.IsNullOrWhiteSpace(index.Name) ? indexUrl.Host : index.Name, index.BadgeLabel, index.SigningKey);
        lock (gate)
        {
            stores.RemoveAll(s => s.IndexUrl == store.IndexUrl);
            stores.Add(store);
            ExtensionStores.Save(StoresPath, stores);
            var next = new Dictionary<string, StoreIndex>(indexes) { [store.IndexUrl] = index };
            indexes = next;
        }
        Changed?.Invoke(this, EventArgs.Empty);
        return store;
    }

    public void RemoveStore(string indexUrl)
    {
        lock (gate)
        {
            stores.RemoveAll(s => s.IndexUrl == indexUrl);
            ExtensionStores.Save(StoresPath, stores);
            var next = new Dictionary<string, StoreIndex>(indexes);
            next.Remove(indexUrl);
            indexes = next;
        }
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public async Task<IReadOnlyList<SyncedStore>> SyncedStoresAsync(CancellationToken ct = default)
    {
        var known = Stores.Select(s => s.IndexUrl).ToHashSet(StringComparer.OrdinalIgnoreCase);
        return await Task.Run(() =>
        {
            var found = new List<SyncedStore>();
            using var connection = db.Open();
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT payload FROM section WHERE kind = 'ext_store'";
            using var reader = command.ExecuteReader();
            while (reader.Read())
            {
                if (reader.GetValue(0) is not byte[] payload)
                {
                    continue;
                }
                var store = BackupExtensionStore.Parser.ParseFrom(payload);
                if (store.IndexUrl.Length > 0 && !known.Contains(store.IndexUrl))
                {
                    found.Add(new SyncedStore(store.IndexUrl, store.Name.Length > 0 ? store.Name : new Uri(store.IndexUrl).Host));
                }
            }
            return found;
        }, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<CatalogEntry>> CatalogAsync(bool refresh, CancellationToken ct = default)
    {
        var current = Stores;
        var known = indexes;
        var next = new Dictionary<string, StoreIndex>();
        var failures = new List<string>();
        foreach (var store in current)
        {
            if (!refresh && known.TryGetValue(store.IndexUrl, out var cached))
            {
                next[store.IndexUrl] = cached;
                continue;
            }
            try
            {
                next[store.IndexUrl] = await ExtensionStores.FetchAsync(http, new Uri(store.IndexUrl), ct, Path.Combine(root, "indexes"), refresh).ConfigureAwait(false);
            }
            catch (Exception e) when (e is HttpRequestException or InvalidDataException or TaskCanceledException && !ct.IsCancellationRequested)
            {
                failures.Add(store.Name);
                if (known.TryGetValue(store.IndexUrl, out var stale))
                {
                    next[store.IndexUrl] = stale;
                }
            }
        }
        indexes = next;
        var byPackage = Installed.ToDictionary(i => i.Package);
        var entries = new List<CatalogEntry>();
        foreach (var store in current)
        {
            if (!next.TryGetValue(store.IndexUrl, out var index))
            {
                continue;
            }
            foreach (var extension in index.Extensions)
            {
                entries.Add(new CatalogEntry(store, extension, byPackage.GetValueOrDefault(extension.Package)));
            }
        }
        if (entries.Count == 0 && failures.Count > 0)
        {
            throw new HttpRequestException("Couldn't reach " + string.Join(", ", failures) + ".");
        }
        return entries;
    }

    public async Task InstallAsync(ExtensionStore store, StoreExtension extension, IProgress<RuntimeProgress>? progress, CancellationToken ct = default)
    {
        if (!IsSupportedLib(extension.Lib))
        {
            throw new NotSupportedException($"{extension.Name} uses extension library {extension.Lib}, which the PC can't run yet.");
        }
        await installGate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (!Runtime.Installed)
            {
                await Runtime.InstallAsync(http, progress, ct).ConfigureAwait(false);
            }
            progress?.Report(new RuntimeProgress("Downloading " + extension.Name, 0, 0));
            var apks = Path.Combine(root, "apks");
            Directory.CreateDirectory(apks);
            var apk = Path.Combine(apks, $"{extension.Package}-{extension.VersionCode}.apk");
            var temp = apk + ".part";
            await File.WriteAllBytesAsync(temp, await http.GetByteArrayAsync(new Uri(new Uri(store.IndexUrl), extension.ApkUrl), ct).ConfigureAwait(false), ct).ConfigureAwait(false);
            File.Move(temp, apk, overwrite: true);
            progress?.Report(new RuntimeProgress("Starting " + extension.Name, 0, 0));
            var generation = await host.StartAsync(ct).ConfigureAwait(false);
            var loaded = await host.CallAsync("load", new JsonObject { ["apk"] = apk }, ct).ConfigureAwait(false) ?? throw new InvalidDataException("The extension didn't load.");
            if (store.SigningKey is { Length: > 0 } key && loaded["signature"]?.GetValue<string>() is { Length: > 0 } signature
                && !signature.Equals(key, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(apk);
                throw new InvalidDataException($"{extension.Name} isn't signed by {store.Name}, so it wasn't installed.");
            }
            var sources = new List<InstalledSource>();
            foreach (var node in loaded["sources"]?.AsArray() ?? [])
            {
                var source = node!.AsObject();
                sources.Add(new InstalledSource(
                    long.Parse(source["id"]!.GetValue<string>()),
                    source["name"]?.GetValue<string>() ?? extension.Name,
                    source["lang"]?.GetValue<string>() ?? "all",
                    source["supportsLatest"]?.GetValue<bool>() ?? false,
                    source["baseUrl"]?.GetValue<string>() ?? string.Empty));
            }
            var record = new InstalledExtension(extension.Package, extension.Name, extension.VersionName, extension.VersionCode, store.IndexUrl, Path.GetFileName(apk),
                extension.IconUrl is { Length: > 0 } icon ? new Uri(new Uri(store.IndexUrl), icon).ToString() : null, extension.Warning, sources);
            InstalledExtension? previous;
            lock (gate)
            {
                previous = installed.FirstOrDefault(i => i.Package == extension.Package);
                installed.RemoveAll(i => i.Package == extension.Package);
                installed.Add(record);
                SaveInstalled();
            }
            if (previous is not null)
            {
                UnregisterSources(previous);
                if (ApkPath(previous) != apk)
                {
                    TryDelete(ApkPath(previous));
                }
            }
            loadedIn[extension.Package] = generation;
            RegisterSources(record);
        }
        finally
        {
            installGate.Release();
        }
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public void Uninstall(string package)
    {
        InstalledExtension? removed;
        lock (gate)
        {
            removed = installed.FirstOrDefault(i => i.Package == package);
            installed.RemoveAll(i => i.Package == package);
            SaveInstalled();
        }
        if (removed is not null)
        {
            UnregisterSources(removed);
            loadedIn.TryRemove(package, out _);
            TryDelete(ApkPath(removed));
        }
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public async Task<JsonNode?> CallSourceAsync(string package, string op, JsonObject args, CancellationToken ct)
    {
        if (!Runtime.Installed)
        {
            throw new ExtensionHostException("Extension support isn't installed on this PC yet.", "runtime");
        }
        Directory.CreateDirectory(TempRoot);
        var generation = await host.StartAsync(ct).ConfigureAwait(false);
        if (!loadedIn.TryGetValue(package, out var loadedGeneration) || loadedGeneration != generation)
        {
            var lockFor = loading.GetOrAdd(package, _ => new SemaphoreSlim(1, 1));
            await lockFor.WaitAsync(ct).ConfigureAwait(false);
            try
            {
                generation = await host.StartAsync(ct).ConfigureAwait(false);
                if (!loadedIn.TryGetValue(package, out loadedGeneration) || loadedGeneration != generation)
                {
                    var record = Installed.FirstOrDefault(i => i.Package == package) ?? throw new ExtensionHostException("That extension was removed.", "source");
                    await host.CallAsync("load", new JsonObject { ["apk"] = ApkPath(record) }, ct).ConfigureAwait(false);
                    loadedIn[package] = generation;
                }
            }
            finally
            {
                lockFor.Release();
            }
        }
        return await host.CallAsync(op, args, ct).ConfigureAwait(false);
    }

    private string ApkPath(InstalledExtension extension) => Path.Combine(root, "apks", extension.ApkFile);

    public static bool IsSupportedLib(string lib) =>
        double.TryParse(lib, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var version) && version >= 1.4 && version < 1.7;

    private void RegisterSources(InstalledExtension extension)
    {
        var memos = new MemoStore(Path.Combine(root, "memo", extension.Package + ".json"));
        foreach (var source in extension.Sources)
        {
            if (sink.IsNative(source.Id))
            {
                continue;
            }
            sink.Register(new JvmSource(this, extension.Package, source.Id, source.Name, source.Lang, source.SupportsLatest, source.BaseUrl, memos));
        }
    }

    private void UnregisterSources(InstalledExtension extension)
    {
        foreach (var source in extension.Sources)
        {
            if (!sink.IsNative(source.Id))
            {
                sink.Unregister(source.Id);
            }
        }
    }

    private IReadOnlyList<InstalledExtension> LoadInstalled()
    {
        try
        {
            return File.Exists(InstalledPath) ? JsonSerializer.Deserialize(File.ReadAllText(InstalledPath), ExtensionJson.Default.ListInstalledExtension) ?? [] : [];
        }
        catch (Exception e) when (e is JsonException or IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    private void SaveInstalled()
    {
        Directory.CreateDirectory(root);
        var temp = InstalledPath + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(installed, ExtensionJson.Default.ListInstalledExtension));
        File.Move(temp, InstalledPath, overwrite: true);
    }

    private static void TryDelete(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    public ValueTask DisposeAsync() => host.DisposeAsync();
}
