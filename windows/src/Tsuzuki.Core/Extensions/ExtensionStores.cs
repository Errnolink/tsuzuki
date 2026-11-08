using System.Text.Json;
using System.Text.RegularExpressions;

namespace Tsuzuki.Core.Extensions;

public sealed record ExtensionStore(string IndexUrl, string Name, string? BadgeLabel, string? SigningKey);

public static partial class ExtensionStores
{
    private static readonly string[] Candidates = ["index.pb", "index.min.json", "repo.json"];

    public static Uri? DirectIndex(string input)
    {
        var text = input.Trim();
        if (text.StartsWith("mihon://", StringComparison.OrdinalIgnoreCase) || text.StartsWith("tachiyomi://", StringComparison.OrdinalIgnoreCase))
        {
            var query = text.Contains('?') ? text[(text.IndexOf('?') + 1)..] : string.Empty;
            foreach (var pair in query.Split('&'))
            {
                var parts = pair.Split('=', 2);
                if (parts.Length == 2 && parts[0] == "url")
                {
                    text = Uri.UnescapeDataString(parts[1]);
                }
            }
        }
        if (!text.Contains("://"))
        {
            text = "https://" + text;
        }
        if (!Uri.TryCreate(text, UriKind.Absolute, out var url) || url.Scheme is not ("http" or "https"))
        {
            return null;
        }
        return Candidates.Any(c => url.AbsolutePath.EndsWith(c, StringComparison.OrdinalIgnoreCase)) ? url : null;
    }

    public static async Task<(Uri IndexUrl, StoreIndex Index)> ResolveAsync(HttpClient http, string input, CancellationToken ct = default)
    {
        if (DirectIndex(input) is { } direct)
        {
            return (direct, await FetchAsync(http, direct, ct).ConfigureAwait(false));
        }
        var text = input.Trim();
        if (!text.Contains("://"))
        {
            text = "https://" + text;
        }
        if (!Uri.TryCreate(text, UriKind.Absolute, out var page) || page.Scheme is not ("http" or "https"))
        {
            throw new FormatException("That doesn't look like a web address.");
        }
        var visited = new HashSet<string>();
        var queue = new Queue<Uri>([page]);
        while (queue.Count > 0 && visited.Count < 6)
        {
            var current = queue.Dequeue();
            if (!visited.Add(current.ToString()))
            {
                continue;
            }
            string html;
            try
            {
                html = await http.GetStringAsync(current, ct).ConfigureAwait(false);
            }
            catch (HttpRequestException)
            {
                continue;
            }
            foreach (Match match in StoreLink().Matches(html))
            {
                var link = System.Net.WebUtility.HtmlDecode(match.Value);
                if (DirectIndex(link) is { } found)
                {
                    return (found, await FetchAsync(http, found, ct).ConfigureAwait(false));
                }
            }
            foreach (Match match in GuideLink().Matches(html))
            {
                if (Uri.TryCreate(current, System.Net.WebUtility.HtmlDecode(match.Groups[1].Value), out var next) && next.Host == page.Host)
                {
                    queue.Enqueue(next);
                }
            }
        }
        foreach (var candidate in Candidates)
        {
            var guess = new Uri(page.AbsolutePath.EndsWith('/') ? page : new Uri(page + "/"), candidate);
            try
            {
                return (guess, await FetchAsync(http, guess, ct).ConfigureAwait(false));
            }
            catch (Exception e) when (e is HttpRequestException or InvalidDataException or JsonException or Google.Protobuf.InvalidProtocolBufferException)
            {
            }
        }
        throw new InvalidDataException("No extension store was found at that address. Paste the store's index link (it usually ends in index.pb or index.min.json).");
    }

    public static readonly TimeSpan IndexLifetime = TimeSpan.FromHours(6);

    public static async Task<StoreIndex> FetchAsync(HttpClient http, Uri indexUrl, CancellationToken ct = default, string? cacheDir = null, bool refresh = true)
    {
        var cacheFile = cacheDir is null ? null : Path.Combine(cacheDir, Convert.ToHexStringLower(System.Security.Cryptography.SHA1.HashData(System.Text.Encoding.UTF8.GetBytes(indexUrl.ToString()))) + ".idx");
        if (!refresh && cacheFile is not null && File.Exists(cacheFile) && DateTime.UtcNow - File.GetLastWriteTimeUtc(cacheFile) < IndexLifetime)
        {
            try
            {
                return ExtensionIndex.Parse(await File.ReadAllBytesAsync(cacheFile, ct).ConfigureAwait(false), indexUrl);
            }
            catch (Exception e) when (e is IOException or JsonException or Google.Protobuf.InvalidProtocolBufferException or InvalidDataException)
            {
            }
        }
        byte[] bytes;
        using (var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct))
        {
            timeout.CancelAfter(TimeSpan.FromSeconds(30));
            try
            {
                bytes = await http.GetByteArrayAsync(indexUrl, timeout.Token).ConfigureAwait(false);
            }
            catch (HttpRequestException) when (cacheFile is not null && File.Exists(cacheFile))
            {
                bytes = await File.ReadAllBytesAsync(cacheFile, ct).ConfigureAwait(false);
            }
        }
        if (cacheFile is not null)
        {
            try
            {
                Directory.CreateDirectory(cacheDir!);
                await File.WriteAllBytesAsync(cacheFile, bytes, ct).ConfigureAwait(false);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
            }
        }
        try
        {
            return ExtensionIndex.Parse(bytes, indexUrl);
        }
        catch (Exception e) when (e is JsonException or Google.Protobuf.InvalidProtocolBufferException or InvalidDataException)
        {
            throw new InvalidDataException("The store's index couldn't be read.", e);
        }
    }

    public static IReadOnlyList<ExtensionStore> Load(string path)
    {
        try
        {
            return File.Exists(path) ? JsonSerializer.Deserialize(File.ReadAllText(path), ExtensionJson.Default.ListExtensionStore) ?? [] : [];
        }
        catch (Exception e) when (e is JsonException or IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    public static void Save(string path, IReadOnlyList<ExtensionStore> stores)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temp = path + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(stores.ToList(), ExtensionJson.Default.ListExtensionStore));
        File.Move(temp, path, overwrite: true);
    }

    [GeneratedRegex(@"(?:mihon|tachiyomi)://[^""'<>\s]+|https?://[^""'<>\s]+?(?:index\.pb|index\.min\.json|repo\.json)", RegexOptions.IgnoreCase)]
    private static partial Regex StoreLink();

    [GeneratedRegex(@"href=""([^""#]*(?:getting-started|guide|install|setup)[^""#]*)""", RegexOptions.IgnoreCase)]
    private static partial Regex GuideLink();
}
