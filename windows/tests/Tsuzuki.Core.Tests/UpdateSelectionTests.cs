using Xunit;
using Tsuzuki.Core.Updates;

namespace Tsuzuki.Core.Tests;

public class UpdateSelectionTests
{
    private const string MixedFeed = """
        [
          { "tag_name": "android-v2.1.0", "draft": false, "prerelease": false, "published_at": "2026-11-01T09:00:00Z",
            "assets": [ { "name": "Tsuzuki-2.1.0.apk", "url": "https://api.github.com/repos/Errnolink/tsuzuki/releases/assets/201",
                          "browser_download_url": "https://github.com/Errnolink/tsuzuki/releases/download/android-v2.1.0/Tsuzuki-2.1.0.apk" } ] },
          { "tag_name": "windows-v0.4.0", "draft": true, "prerelease": false, "published_at": "2026-11-01T08:00:00Z",
            "assets": [ { "name": "assets.win-x64.json", "url": "https://api.github.com/repos/Errnolink/tsuzuki/releases/assets/202",
                          "browser_download_url": "https://github.com/Errnolink/tsuzuki/releases/download/windows-v0.4.0/assets.win-x64.json" } ] },
          { "tag_name": "windows-v0.3.0", "draft": false, "prerelease": false, "published_at": "2026-10-30T12:00:00Z",
            "assets": [ { "name": "assets.win-x64.json", "url": "https://api.github.com/repos/Errnolink/tsuzuki/releases/assets/203",
                          "browser_download_url": "https://github.com/Errnolink/tsuzuki/releases/download/windows-v0.3.0/assets.win-x64.json" } ] },
          { "tag_name": "windows-v0.2.0", "draft": false, "prerelease": false, "published_at": "2026-10-20T12:00:00Z",
            "assets": [ { "name": "assets.win-x64.json", "url": "https://api.github.com/repos/Errnolink/tsuzuki/releases/assets/204",
                          "browser_download_url": "https://github.com/Errnolink/tsuzuki/releases/download/windows-v0.2.0/assets.win-x64.json" } ] }
        ]
        """;

    private const string PrereleaseFeed = """
        [
          { "tag_name": "windows-v0.5.0-rc1", "draft": false, "prerelease": true, "published_at": "2026-10-31T10:00:00Z",
            "assets": [ { "name": "assets.win-x64.json", "url": "u1", "browser_download_url": "b1" } ] },
          { "tag_name": "windows-v0.4.0", "draft": false, "prerelease": false, "published_at": "2026-10-25T10:00:00Z",
            "assets": [ { "name": "assets.win-x64.json", "url": "u2", "browser_download_url": "b2" } ] }
        ]
        """;

    private static IReadOnlyList<GithubReleaseInfo> Parse(string feed) => GithubReleaseList.Parse(feed);

    [Fact]
    public void AndroidReleasesAreIgnored()
    {
        var selected = GithubReleaseList.SelectWindows(Parse(MixedFeed), includePrereleases: false);

        Assert.Equal(["windows-v0.3.0", "windows-v0.2.0"], selected.Select(r => r.Tag));
    }

    [Fact]
    public void DraftWindowsReleasesAreIgnored()
    {
        var feed = Parse(MixedFeed);

        var tags = GithubReleaseList.SelectWindows(feed, includePrereleases: false).Select(r => r.Tag).ToList();

        Assert.DoesNotContain("windows-v0.4.0", tags);
    }

    [Fact]
    public void PrereleasesFollowTheRequestedPolicy()
    {
        var feed = Parse(PrereleaseFeed);

        var stable = GithubReleaseList.SelectWindows(feed, includePrereleases: false).Select(r => r.Tag).ToList();
        var withPre = GithubReleaseList.SelectWindows(feed, includePrereleases: true).Select(r => r.Tag).ToList();

        Assert.Equal(["windows-v0.4.0"], stable);
        Assert.Equal(["windows-v0.5.0-rc1", "windows-v0.4.0"], withPre);
    }

    [Fact]
    public void SelectionKeepsNewestFirst()
    {
        var oldestFirst = """
            [
              { "tag_name": "windows-v0.1.0", "draft": false, "prerelease": false, "published_at": "2026-09-01T00:00:00Z", "assets": [] },
              { "tag_name": "windows-v0.3.0", "draft": false, "prerelease": false, "published_at": "2026-10-01T00:00:00Z", "assets": [] },
              { "tag_name": "windows-v0.2.0", "draft": false, "prerelease": false, "published_at": "2026-09-15T00:00:00Z", "assets": [] }
            ]
            """;

        var selected = GithubReleaseList.SelectWindows(Parse(oldestFirst), includePrereleases: false);

        Assert.Equal(["windows-v0.3.0", "windows-v0.2.0", "windows-v0.1.0"], selected.Select(r => r.Tag));
    }

    [Fact]
    public void NearMissTagsAreIgnored()
    {
        var feed = """
            [
              { "tag_name": "v1.0.0", "draft": false, "prerelease": false, "published_at": "2026-10-01T00:00:00Z", "assets": [] },
              { "tag_name": "win-v1.0.0", "draft": false, "prerelease": false, "published_at": "2026-10-01T00:00:00Z", "assets": [] },
              { "tag_name": "windows-1.0.0", "draft": false, "prerelease": false, "published_at": "2026-10-01T00:00:00Z", "assets": [] },
              { "tag_name": "WINDOWS-V1.0.0", "draft": false, "prerelease": false, "published_at": "2026-10-01T00:00:00Z", "assets": [] }
            ]
            """;

        var selected = GithubReleaseList.SelectWindows(Parse(feed), includePrereleases: false);

        Assert.Equal(["WINDOWS-V1.0.0"], selected.Select(r => r.Tag));
    }

    [Fact]
    public void ParsedReleasesKeepTheirAssetsForTheUpdateFeed()
    {
        var selected = GithubReleaseList.SelectWindows(Parse(MixedFeed), includePrereleases: false);

        var release = selected.Single(r => r.Tag == "windows-v0.3.0");
        var asset = Assert.Single(release.Assets);
        Assert.Equal("assets.win-x64.json", asset.Name);
        Assert.Equal("https://github.com/Errnolink/tsuzuki/releases/download/windows-v0.3.0/assets.win-x64.json", asset.BrowserDownloadUrl);
        Assert.Equal(DateTimeOffset.Parse("2026-10-30T12:00:00Z"), release.PublishedAt);
    }

    [Fact]
    public void MalformedJsonYieldsNoReleases()
    {
        Assert.Empty(GithubReleaseList.Parse("<html>rate limited</html>"));
        Assert.Empty(GithubReleaseList.Parse("[]"));
    }
}
