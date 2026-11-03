namespace Tsuzuki.Core.Reader;

public sealed class ReaderSpreadPlanner(int pageCount, bool firstPageAlone, Func<int, bool> isLandscape)
{
    private int pairAnchor = firstPageAlone ? 1 : 0;
    private int? pendingSingleton;

    public (int First, int? Second) SpreadFor(int index)
    {
        if (pageCount < 2 || (firstPageAlone && index == 0))
        {
            return (index, null);
        }
        if (index == pendingSingleton)
        {
            return (index, null);
        }
        var first = (index - pairAnchor) % 2 == 0 ? index : index - 1;
        if (first < 0)
        {
            first = 0;
        }
        var second = first + 1 < pageCount ? first + 1 : (int?)null;
        if (isLandscape(first))
        {
            return (first, null);
        }
        if (second is { } s && isLandscape(s))
        {
            return (first, null);
        }
        return (first, second);
    }

    public void OnDisplayed(int first, bool paired)
    {
        if (first == 0 && firstPageAlone)
        {
            return;
        }
        if (paired)
        {
            pendingSingleton = null;
            return;
        }
        if (pendingSingleton != first)
        {
            pendingSingleton = first + 1 < pageCount ? first + 1 : null;
            pairAnchor = first + 2;
            return;
        }
        pairAnchor = first + 1;
    }
}
