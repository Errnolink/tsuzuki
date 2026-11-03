using Tsuzuki.Core.Reader;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class ReaderSpreadTests
{
    private sealed class Reader
    {
        private readonly ReaderSpreadPlanner planner;
        private int current;
        private int pageCount;

        public Reader(int pageCount, bool firstPageAlone, params int[] landscapePages)
        {
            this.pageCount = pageCount;
            planner = new ReaderSpreadPlanner(pageCount, firstPageAlone, i => landscapePages.Contains(i));
        }

        public List<(int First, int? Second)> Shown { get; } = [];

        public void Open(int index)
        {
            current = index;
            Display(index);
        }

        public void Next()
        {
            var (first, second) = planner.SpreadFor(current);
            var last = second ?? first;
            if (last >= pageCount - 1)
            {
                return;
            }
            current = last + 1;
            Display(current);
        }

        public void Previous()
        {
            var (first, _) = planner.SpreadFor(current);
            if (first <= 0)
            {
                return;
            }
            current = first - 1;
            Display(current);
        }

        public void JumpTo(int index)
        {
            current = Math.Clamp(index, 0, pageCount - 1);
            Display(current);
        }

        private void Display(int index)
        {
            var spread = planner.SpreadFor(index);
            planner.OnDisplayed(spread.First, spread.Second is not null);
            Shown.Add(spread);
        }
    }

    [Fact]
    public void ChapterWithoutLandscapePagesPairsEverySpread()
    {
        var reader = new Reader(6, firstPageAlone: false);
        reader.Open(0);
        reader.Next();
        reader.Next();
        reader.Next();
        Assert.Equal(
            new List<(int, int?)> { (0, 1), (2, 3), (4, 5) },
            reader.Shown);
    }

    [Fact]
    public void LandscapeSecondPageShowsTheOrphanedPartnerAloneNext()
    {
        var reader = new Reader(6, firstPageAlone: false, 1);
        reader.Open(0);
        reader.Next();
        Assert.Equal((0, null), reader.Shown[0]);
        Assert.Equal((1, null), reader.Shown[1]);
        reader.Next();
        Assert.Equal((2, 3), reader.Shown[2]);
        reader.Next();
        Assert.Equal((4, 5), reader.Shown[3]);
    }

    [Fact]
    public void LandscapeFirstPageShowsTheOrphanedPartnerAloneNext()
    {
        var reader = new Reader(6, firstPageAlone: false, 2);
        reader.Open(2);
        Assert.Equal((2, null), reader.Shown[0]);
        reader.Next();
        Assert.Equal((3, null), reader.Shown[1]);
        reader.Next();
        Assert.Equal((4, 5), reader.Shown[2]);
    }

    [Fact]
    public void FirstPageAloneResumesPairingAfterALandscapePage()
    {
        var reader = new Reader(6, firstPageAlone: true, 1);
        reader.Open(0);
        Assert.Equal((0, null), reader.Shown[0]);
        reader.Next();
        Assert.Equal((1, null), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((2, null), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((3, 4), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((5, null), reader.Shown[^1]);
    }

    [Fact]
    public void BackwardsAfterACollapseReturnsToTheLandscapePage()
    {
        var reader = new Reader(6, firstPageAlone: false, 0);
        reader.Open(0);
        Assert.Equal((0, null), reader.Shown[0]);
        reader.Next();
        Assert.Equal((1, null), reader.Shown[^1]);
        reader.Previous();
        Assert.Equal((0, null), reader.Shown[^1]);
    }

    [Fact]
    public void SliderJumpAfterACollapsePairsFromTheResyncPoint()
    {
        var reader = new Reader(7, firstPageAlone: false, 0);
        reader.Open(0);
        reader.Next();
        Assert.Equal((1, null), reader.Shown[^1]);
        reader.JumpTo(4);
        Assert.Equal((4, 5), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((6, null), reader.Shown[^1]);
    }
}
