using System.Text.Json;
using System.Text.Json.Serialization;
using Velopack.Sources;

namespace Tsuzuki.Core.Updates;

public sealed record GithubAsset(string? Name, string? Url, string? BrowserDownloadUrl);

public sealed record GithubReleaseInfo(
    string? Tag,
    bool Draft,
    bool Prerelease,
    DateTimeOffset? PublishedAt,
    IReadOnlyList<GithubAsset> Assets);

public static class GithubReleaseList
{
    public static IReadOnlyList<GithubReleaseInfo> Parse(string json)
    {
        List<Dto>? releases;
        try
        {
            releases = JsonSerializer.Deserialize<List<Dto>>(json);
        }
        catch (JsonException)
        {
            return [];
        }
        return releases?
            .Select(r => new GithubReleaseInfo(
                r.TagName,
                r.Draft,
                r.Prerelease,
                r.PublishedAt,
                (IReadOnlyList<GithubAsset>)[.. r.Assets.Select(a => new GithubAsset(a.Name, a.Url, a.BrowserDownloadUrl))]))
            .ToList() ?? [];
    }

    public static IReadOnlyList<GithubReleaseInfo> SelectWindows(IEnumerable<GithubReleaseInfo> releases, bool includePrereleases) =>
        releases
            .Where(r => r.Tag?.StartsWith(WindowsUpdateSource.TagPrefix, StringComparison.OrdinalIgnoreCase) == true && !r.Draft)
            .Where(r => includePrereleases || !r.Prerelease)
            .OrderByDescending(r => r.PublishedAt ?? DateTimeOffset.MinValue)
            .ToList();

    private sealed class Dto
    {
        [JsonPropertyName("tag_name")]
        public string? TagName { get; set; }

        [JsonPropertyName("draft")]
        public bool Draft { get; set; }

        [JsonPropertyName("prerelease")]
        public bool Prerelease { get; set; }

        [JsonPropertyName("published_at")]
        public DateTimeOffset? PublishedAt { get; set; }

        [JsonPropertyName("assets")]
        public List<AssetDto> Assets { get; set; } = [];

        public sealed class AssetDto
        {
            [JsonPropertyName("name")]
            public string? Name { get; set; }

            [JsonPropertyName("url")]
            public string? Url { get; set; }

            [JsonPropertyName("browser_download_url")]
            public string? BrowserDownloadUrl { get; set; }
        }
    }
}

public sealed class WindowsUpdateSource : GithubSource
{
    public const string RepoUrl = "https://github.com/Errnolink/tsuzuki";
    public const string TagPrefix = "windows-v";

    private const int PageSize = 100;
    private const int MaxPages = 10;
    private const int WantedReleases = 5;

    public WindowsUpdateSource()
        : base(RepoUrl, accessToken: null, prerelease: false)
    {
    }

    protected override async Task<GithubRelease[]> GetReleases(bool includePrereleases)
    {
        var selected = new List<GithubReleaseInfo>();
        for (var page = 1; page <= MaxPages && selected.Count < WantedReleases; page++)
        {
            var path = $"repos{RepoUri.AbsolutePath}/releases?per_page={PageSize}&page={page}";
            var json = await Downloader.DownloadString(
                new Uri(GetApiBaseUrl(RepoUri), path).ToString(),
                GetRequestHeaders("application/vnd.github+json")).ConfigureAwait(false);
            var batch = GithubReleaseList.Parse(json);
            if (batch.Count == 0)
            {
                break;
            }
            selected.AddRange(GithubReleaseList.SelectWindows(batch, includePrereleases));
            if (batch.Count < PageSize)
            {
                break;
            }
        }
        return [.. selected.Select(ToVelopackRelease)];
    }

    internal static GithubRelease ToVelopackRelease(GithubReleaseInfo info) => new()
    {
        Name = info.Tag,
        Prerelease = info.Prerelease,
        PublishedAt = info.PublishedAt?.UtcDateTime,
        Assets = [.. info.Assets.Select(a => new Velopack.Sources.GithubReleaseAsset
        {
            Name = a.Name,
            Url = a.Url,
            BrowserDownloadUrl = a.BrowserDownloadUrl,
        })],
    };
}
