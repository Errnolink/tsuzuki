namespace Tsuzuki.Core.Library;

public sealed record FeedChips(IReadOnlyList<UpdateEntry> Shown, int Overflow);

public sealed record FeedDayCounts(string Day, int Titles, int Chapters);

public static class FeedTimeline
{
    public const int MaxChips = 3;

    public static FeedChips Chips(IReadOnlyList<UpdateEntry> cluster)
    {
        var unread = cluster
            .Where(u => !u.Read)
            .OrderByDescending(u => u.ChapterNumber >= 0 ? u.ChapterNumber : double.NegativeInfinity)
            .ThenByDescending(u => u.DateFetch)
            .ToList();
        var shown = unread.Take(MaxChips).ToList();
        return new FeedChips(shown, unread.Count - shown.Count);
    }

    public static IReadOnlyList<FeedDayCounts> Counts(IReadOnlyList<IReadOnlyList<UpdateEntry>> clusters, Func<long, string> dayOf)
    {
        var result = new List<FeedDayCounts>();
        var index = new Dictionary<string, (List<long> Titles, int Chapters)>();
        foreach (var cluster in clusters)
        {
            if (cluster.Count == 0)
            {
                continue;
            }
            var day = dayOf(cluster[0].DateFetch);
            if (!index.TryGetValue(day, out var entry))
            {
                index[day] = entry = ([], 0);
                result.Add(new FeedDayCounts(day, 0, 0));
            }
            entry.Titles.Add(cluster[0].MangaId);
            index[day] = (entry.Titles, entry.Chapters + cluster.Count);
        }
        return [.. result.Select(r =>
        {
            var (titles, chapters) = index[r.Day];
            return new FeedDayCounts(r.Day, titles.Distinct().Count(), chapters);
        })];
    }
}
