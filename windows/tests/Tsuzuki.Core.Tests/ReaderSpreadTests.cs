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
            : this(pageCount, firstPageAlone, false, landscapePages)
        {
        }

        public Reader(int pageCount, bool firstPageAlone, bool offset, int[] landscapePages)
        {
            this.pageCount = pageCount;
            planner = new ReaderSpreadPlanner(pageCount, firstPageAlone, offset, i => landscapePages.Contains(i));
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

    [Fact]
    public void OffsetShiftsPairingByOnePage()
    {
        var reader = new Reader(6, firstPageAlone: false, offset: true, []);
        reader.Open(0);
        reader.Next();
        reader.Next();
        reader.Next();
        Assert.Equal(
            new List<(int, int?)> { (0, null), (1, 2), (3, 4), (5, null) },
            reader.Shown);
    }

    [Fact]
    public void OffsetKeepsTheCoverAloneAndShiftsThePairsAfterIt()
    {
        var reader = new Reader(7, firstPageAlone: true, offset: true, []);
        reader.Open(0);
        reader.Next();
        reader.Next();
        reader.Next();
        reader.Next();
        Assert.Equal(
            new List<(int, int?)> { (0, null), (1, null), (2, 3), (4, 5), (6, null) },
            reader.Shown);
    }

    [Fact]
    public void OffsetAppliesToTheSpreadAlreadyOnScreen()
    {
        var plain = new Reader(8, firstPageAlone: true, offset: false, []);
        plain.JumpTo(4);
        var shifted = new Reader(8, firstPageAlone: true, offset: true, []);
        shifted.JumpTo(4);
        Assert.Equal((3, 4), plain.Shown[^1]);
        Assert.Equal((4, 5), shifted.Shown[^1]);
    }

    [Fact]
    public void OffsetStillShowsLandscapePagesAlone()
    {
        var reader = new Reader(8, firstPageAlone: false, offset: true, [3]);
        reader.Open(1);
        Assert.Equal((1, 2), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((3, null), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((4, null), reader.Shown[^1]);
        reader.Next();
        Assert.Equal((5, 6), reader.Shown[^1]);
    }

    [Fact]
    public void RightToLeftPutsTheEarlierPageOnTheRight()
    {
        var reader = new Reader(6, firstPageAlone: false, offset: true, []);
        reader.Open(1);
        var (first, second) = reader.Shown[^1];
        Assert.Equal((2, (int?)1), ReaderSpreadPlanner.Arrange(first, second, rightToLeft: true));
        Assert.Equal((1, (int?)2), ReaderSpreadPlanner.Arrange(first, second, rightToLeft: false));
        Assert.Equal((0, (int?)null), ReaderSpreadPlanner.Arrange(0, null, rightToLeft: true));
    }

    [Fact]
    public void SpreadPositionCountsEveryScreenOfTheChapter()
    {
        var planner = new ReaderSpreadPlanner(30, firstPageAlone: true, offset: true, _ => false);
        Assert.Equal((2, 16), planner.Position(1));
        Assert.Equal((3, 16), planner.Position(3));
        Assert.Equal((16, 16), planner.Position(29));
    }

    [Fact]
    public void SpreadPositionAccountsForLandscapePagesShownAlone()
    {
        var planner = new ReaderSpreadPlanner(10, firstPageAlone: true, offset: false, i => i == 5);
        Assert.Equal((1, 7), planner.Position(0));
        Assert.Equal((4, 7), planner.Position(5));
        Assert.Equal((5, 7), planner.Position(6));
        Assert.Equal((7, 7), planner.Position(9));
    }

    [Fact]
    public void PositionDoesNotDependOnWhatWasShownBefore()
    {
        var planner = new ReaderSpreadPlanner(12, firstPageAlone: true, offset: false, i => i == 2);
        var cold = planner.Position(7);
        planner.SpreadFor(1);
        planner.SpreadFor(2);
        planner.SpreadFor(3);
        Assert.Equal(cold, planner.Position(7));
        Assert.Equal((7, (int?)8), planner.SpreadFor(8));
    }
}
