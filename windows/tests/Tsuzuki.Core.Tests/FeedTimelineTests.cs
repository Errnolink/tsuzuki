using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class FeedTimelineTests
{
    private static UpdateEntry Entry(long manga, double number, long fetched, bool read = false) =>
        new(manga, "Title", null, manga * 100 + (long)number, "Chapter", number, null, fetched, fetched, read, 0, 1);

    private static readonly long Now = DateTimeOffset.Parse("2026-11-08T12:00:00Z").ToUnixTimeMilliseconds();
    private static readonly long Yesterday = Now - 86_400_000;

    private static string Day(long fetched) => fetched >= Now - 12_000_000 ? "Today" : "Yesterday";

    [Fact]
    public void ChipsShowNewestUnreadFirstWithOverflow()
    {
        var cluster = new List<UpdateEntry>
        {
            Entry(1, 187, Now - 300),
            Entry(1, 189, Now - 100),
            Entry(1, 188, Now - 200),
            Entry(1, 186, Now - 400),
        };

        var chips = FeedTimeline.Chips(cluster);

        Assert.Equal([189d, 188, 187], chips.Shown.Select(c => c.ChapterNumber));
        Assert.Equal(1, chips.Overflow);
    }

    [Fact]
    public void ChipsSkipReadEntries()
    {
        var cluster = new List<UpdateEntry>
        {
            Entry(1, 190, Now - 50, read: true),
            Entry(1, 189, Now - 100),
        };

        var chips = FeedTimeline.Chips(cluster);

        Assert.Equal([189d], chips.Shown.Select(c => c.ChapterNumber));
        Assert.Equal(0, chips.Overflow);
    }

    [Fact]
    public void FullyReadClusterShowsNoChips()
    {
        var chips = FeedTimeline.Chips([Entry(1, 214, Now, read: true)]);

        Assert.Empty(chips.Shown);
        Assert.Equal(0, chips.Overflow);
    }

    [Fact]
    public void CountsMergeClustersPerDay()
    {
        List<IReadOnlyList<UpdateEntry>> clusters =
        [
            [Entry(1, 189, Now - 100), Entry(1, 188, Now - 200)],
            [Entry(2, 612, Now - 300)],
            [Entry(3, 241, Yesterday - 100)],
            [Entry(3, 240, Yesterday - 200)],
            [Entry(4, 88, Yesterday - 300)],
        ];

        var counts = FeedTimeline.Counts(clusters, Day);

        Assert.Equal(2, counts.Count);
        Assert.Equal("Today", counts[0].Day);
        Assert.Equal(2, counts[0].Titles);
        Assert.Equal(3, counts[0].Chapters);
        Assert.Equal("Yesterday", counts[1].Day);
        Assert.Equal(2, counts[1].Titles);
        Assert.Equal(3, counts[1].Chapters);
    }

    [Fact]
    public void CountsTreatUnknownChapterFetchesInsideOneCluster()
    {
        var cluster = new List<UpdateEntry>
        {
            Entry(1, -1, Now - 100),
            Entry(1, 12, Now - 200),
        };

        var chips = FeedTimeline.Chips(cluster);

        Assert.Equal([12d, -1d], chips.Shown.Select(c => c.ChapterNumber));
    }
}
