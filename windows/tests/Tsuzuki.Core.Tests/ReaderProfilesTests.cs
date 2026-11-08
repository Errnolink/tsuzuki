using Tsuzuki.Core.Reader;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class ReaderProfilesTests
{
    private static string NewPath()
    {
        var dir = Path.Combine(TestLib.TempRoot, Guid.NewGuid().ToString("N"));
        return Path.Combine(dir, "reader-profiles.json");
    }

    [Fact]
    public void SeriesWithoutAProfileHasNone()
    {
        var profiles = ReaderProfiles.Load(NewPath());
        Assert.Null(profiles.Get(42));
    }

    [Fact]
    public void ProfileSavedForOneSeriesDoesNotLeakIntoAnother()
    {
        var path = NewPath();
        var profiles = ReaderProfiles.Load(path);
        profiles.Set(1, new ReaderProfile(ReaderLayout.Double, RightToLeft: false, PageFit.Width, SpreadOffset: true));
        profiles.Save();

        var reloaded = ReaderProfiles.Load(path);
        Assert.Null(reloaded.Get(2));
        Assert.Null(profiles.Get(2));
    }

    [Fact]
    public void ProfileSurvivesReload()
    {
        var path = NewPath();
        var profiles = ReaderProfiles.Load(path);
        profiles.Set(1, new ReaderProfile(ReaderLayout.Double, RightToLeft: false, PageFit.Width, SpreadOffset: true));
        profiles.Set(2, new ReaderProfile(ReaderLayout.Strip, RightToLeft: true, PageFit.Screen, SpreadOffset: false));
        profiles.Save();

        var reloaded = ReaderProfiles.Load(path);
        Assert.Equal(new ReaderProfile(ReaderLayout.Double, false, PageFit.Width, true), reloaded.Get(1));
        Assert.Equal(new ReaderProfile(ReaderLayout.Strip, true, PageFit.Screen, false), reloaded.Get(2));
    }

    [Fact]
    public void LaterChangeToASeriesReplacesItsEarlierProfile()
    {
        var path = NewPath();
        var profiles = ReaderProfiles.Load(path);
        profiles.Set(1, new ReaderProfile(ReaderLayout.Single, true, PageFit.Screen, false));
        profiles.Save();
        profiles.Set(1, new ReaderProfile(ReaderLayout.Double, true, PageFit.Screen, true));
        profiles.Save();

        Assert.Equal(new ReaderProfile(ReaderLayout.Double, true, PageFit.Screen, true), ReaderProfiles.Load(path).Get(1));
    }

    [Fact]
    public void CorruptFileStartsWithNoProfilesAndCanBeOverwritten()
    {
        var path = NewPath();
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, "{ not json");

        var profiles = ReaderProfiles.Load(path);
        Assert.Null(profiles.Get(1));
        profiles.Set(1, new ReaderProfile(ReaderLayout.Single, false, PageFit.Original, false));
        profiles.Save();

        Assert.Equal(PageFit.Original, ReaderProfiles.Load(path).Get(1)?.Fit);
    }
}
