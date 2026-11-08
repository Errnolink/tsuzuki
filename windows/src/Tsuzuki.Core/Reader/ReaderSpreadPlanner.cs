namespace Tsuzuki.Core.Reader;

public sealed class ReaderSpreadPlanner(int pageCount, bool firstPageAlone, bool offset, Func<int, bool> isLandscape)
{
    private readonly int leadingSingles = (firstPageAlone ? 1 : 0) + (offset ? 1 : 0);

    public static (int Left, int? Right) Arrange(int first, int? second, bool rightToLeft) =>
        second is { } s && rightToLeft ? (s, first) : (first, second);

    public (int First, int? Second) SpreadFor(int index)
    {
        index = Math.Clamp(index, 0, Math.Max(0, pageCount - 1));
        if (pageCount < 2 || index < leadingSingles)
        {
            return (index, null);
        }
        var first = leadingSingles + (index - leadingSingles) / 2 * 2;
        var second = first + 1;
        if (second >= pageCount || isLandscape(first) || isLandscape(second))
        {
            return (index, null);
        }
        return (first, second);
    }

    public int NextStart(int index)
    {
        var (first, second) = SpreadFor(index);
        return (second ?? first) + 1;
    }

    public int PreviousStart(int index) => SpreadFor(index).First - 1;

    public (int Ordinal, int Count) Position(int index)
    {
        var shown = SpreadFor(index).First;
        var ordinal = 0;
        var count = 0;
        for (var i = 0; i < pageCount; i = NextStart(i))
        {
            count++;
            if (SpreadFor(i).First <= shown)
            {
                ordinal = count;
            }
        }
        return (Math.Max(1, ordinal), Math.Max(1, count));
    }
}
