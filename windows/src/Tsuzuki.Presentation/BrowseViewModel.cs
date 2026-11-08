using Tsuzuki.Core.Sources;

namespace Tsuzuki.Presentation;

public sealed record BrowseRequest(ISource Source, ListingKind Kind, SearchQuery? Query);

public enum BrowsePageOutcome
{
    Added,
    Stale,
    Busy,
    Failed,
}

public sealed record BrowsePageResult(BrowsePageOutcome Outcome, IReadOnlyList<SManga> Added, Exception? Error = null);

public sealed class BrowseViewModel(ListingCache listings)
{
    private readonly HashSet<string> seen = [];
    private BrowseRequest? request;
    private int page;
    private int version;
    private bool refresh;

    public bool Loading { get; private set; }

    public bool HasNext { get; private set; } = true;

    public int Count => seen.Count;

    public void Reset(BrowseRequest next, bool forceRefresh)
    {
        version++;
        request = next;
        refresh = forceRefresh;
        page = 0;
        HasNext = true;
        Loading = false;
        seen.Clear();
    }

    public void AllowRetry() => HasNext = true;

    public async Task<BrowsePageResult> LoadMoreAsync(CancellationToken ct = default)
    {
        if (request is not { } current || Loading || !HasNext)
        {
            return new BrowsePageResult(BrowsePageOutcome.Busy, []);
        }
        Loading = true;
        var my = version;
        try
        {
            var next = page + 1;
            var result = await listings.GetAsync(current.Source, current.Kind, current.Query, next, refresh, ct);
            if (my != version)
            {
                return new BrowsePageResult(BrowsePageOutcome.Stale, []);
            }
            page = next;
            HasNext = result.HasNextPage;
            var added = result.Mangas.Where(m => seen.Add(m.Url)).ToList();
            return new BrowsePageResult(BrowsePageOutcome.Added, added);
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            if (my != version)
            {
                return new BrowsePageResult(BrowsePageOutcome.Stale, []);
            }
            if (seen.Count > 0)
            {
                HasNext = false;
            }
            return new BrowsePageResult(BrowsePageOutcome.Failed, [], e);
        }
        finally
        {
            if (my == version)
            {
                Loading = false;
            }
        }
    }
}
