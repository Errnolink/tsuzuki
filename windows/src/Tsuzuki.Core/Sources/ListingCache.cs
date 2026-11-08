using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Sources;

public enum ListingKind
{
    Popular,
    Latest,
    Search,
}

public sealed class ListingCache
{
    public static readonly TimeSpan BrowseLifetime = TimeSpan.FromMinutes(30);
    public static readonly TimeSpan SearchLifetime = TimeSpan.FromMinutes(10);
    private static readonly TimeSpan KeepOnDisk = TimeSpan.FromDays(3);

    private readonly string root;
    private readonly TimeProvider time;
    private readonly ConcurrentDictionary<string, Entry> memory = new();

    public ListingCache(string root, TimeProvider? clock = null)
    {
        this.root = root;
        time = clock ?? TimeProvider.System;
        _ = Task.Run(Trim);
    }

    public async Task<MangasPage> GetAsync(ISource source, ListingKind kind, SearchQuery? query, int page, bool refresh, CancellationToken ct = default)
    {
        var key = Key(source.Id, kind, query, page);
        var now = time.GetUtcNow();
        var lifetime = kind == ListingKind.Search ? SearchLifetime : BrowseLifetime;
        var cached = Lookup(key);
        if (!refresh && cached is not null && now - cached.FetchedAt < lifetime)
        {
            return cached.Page;
        }
        MangasPage fresh;
        try
        {
            fresh = kind switch
            {
                ListingKind.Latest => await source.GetLatestAsync(page, ct).ConfigureAwait(false),
                ListingKind.Search => await source.SearchAsync(query ?? SearchQuery.Empty, page, ct).ConfigureAwait(false),
                _ => await source.GetPopularAsync(page, ct).ConfigureAwait(false),
            };
        }
        catch (HttpRequestException) when (cached is not null && !refresh)
        {
            return cached.Page;
        }
        var entry = new Entry(now, fresh);
        memory[key] = entry;
        Save(key, entry);
        return fresh;
    }

    private Entry? Lookup(string key)
    {
        if (memory.TryGetValue(key, out var hit))
        {
            return hit;
        }
        var path = Path.Combine(root, key + ".json");
        try
        {
            if (!File.Exists(path))
            {
                return null;
            }
            var stored = JsonSerializer.Deserialize(File.ReadAllText(path), ListingJson.Default.StoredListing);
            if (stored is null)
            {
                return null;
            }
            var entry = new Entry(DateTimeOffset.FromUnixTimeMilliseconds(stored.FetchedAt), new MangasPage(stored.Mangas, stored.HasNextPage));
            memory[key] = entry;
            return entry;
        }
        catch (Exception e) when (e is JsonException or IOException or UnauthorizedAccessException)
        {
            return null;
        }
    }

    private void Save(string key, Entry entry)
    {
        try
        {
            Directory.CreateDirectory(root);
            var path = Path.Combine(root, key + ".json");
            var temp = path + "." + Environment.CurrentManagedThreadId + ".tmp";
            File.WriteAllText(temp, JsonSerializer.Serialize(
                new StoredListing(entry.FetchedAt.ToUnixTimeMilliseconds(), [.. entry.Page.Mangas], entry.Page.HasNextPage), ListingJson.Default.StoredListing));
            File.Move(temp, path, overwrite: true);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    private void Trim()
    {
        try
        {
            if (!Directory.Exists(root))
            {
                return;
            }
            var cutoff = time.GetUtcNow() - KeepOnDisk;
            foreach (var file in Directory.EnumerateFiles(root))
            {
                if (File.GetLastWriteTimeUtc(file) < cutoff.UtcDateTime)
                {
                    File.Delete(file);
                }
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    private static string Key(long sourceId, ListingKind kind, SearchQuery? query, int page)
    {
        var text = new StringBuilder().Append(sourceId).Append('|').Append(kind).Append('|').Append(page);
        if (query is not null)
        {
            text.Append('|').Append(query.Text);
            foreach (var (filter, values) in query.Values.OrderBy(v => v.Key, StringComparer.Ordinal))
            {
                text.Append('|').Append(filter).Append('=').Append(string.Join(',', values));
            }
        }
        return Convert.ToHexStringLower(SHA1.HashData(Encoding.UTF8.GetBytes(text.ToString())));
    }

    private sealed record Entry(DateTimeOffset FetchedAt, MangasPage Page);
}

internal sealed record StoredListing(long FetchedAt, List<SManga> Mangas, bool HasNextPage);

[JsonSerializable(typeof(StoredListing))]
internal sealed partial class ListingJson : JsonSerializerContext;
