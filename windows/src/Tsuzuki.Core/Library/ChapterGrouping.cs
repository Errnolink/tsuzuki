namespace Tsuzuki.Core.Library;

public sealed record ChapterGroup(ChapterRow Primary, IReadOnlyList<ChapterRow> Alternates)
{
    public bool Read => Primary.Read || Alternates.Any(a => a.Read);
    public bool InProgress => !Read && (Primary.LastPageRead > 0 || Alternates.Any(a => a.LastPageRead > 0));
    public long LastReadAt => Math.Max(Primary.LastReadAt, Alternates.Count == 0 ? 0 : Alternates.Max(a => a.LastReadAt));
    public double Number => Primary.ChapterNumber;
}

public static class ChapterGrouping
{
    public static IReadOnlyList<ChapterGroup> Group(IReadOnlyList<ChapterRow> rows)
    {
        var scanlatorCounts = rows
            .Where(r => !string.IsNullOrEmpty(r.Scanlator))
            .GroupBy(r => r.Scanlator!)
            .ToDictionary(g => g.Key, g => g.Count());
        var lastReadScanlator = rows
            .Where(r => r.LastReadAt > 0 && !string.IsNullOrEmpty(r.Scanlator))
            .OrderByDescending(r => r.LastReadAt)
            .Select(r => r.Scanlator)
            .FirstOrDefault();

        var groups = new List<ChapterGroup>();
        foreach (var bucket in rows.GroupBy(r => r.ChapterNumber >= 0 ? Math.Round(r.ChapterNumber, 3).ToString(System.Globalization.CultureInfo.InvariantCulture) : "u:" + r.Url))
        {
            var copies = bucket.ToList();
            var primary = copies
                .OrderByDescending(c => !c.Read && c.LastPageRead > 0 ? 1 : 0)
                .ThenByDescending(c => c.Readable ? 1 : 0)
                .ThenByDescending(c => lastReadScanlator is not null && c.Scanlator == lastReadScanlator ? 1 : 0)
                .ThenByDescending(c => c.Scanlator is not null && scanlatorCounts.TryGetValue(c.Scanlator, out var n) ? n : 0)
                .ThenByDescending(c => c.ExternalUrl is null ? 1 : 0)
                .ThenByDescending(c => c.DateUpload)
                .First();
            groups.Add(new ChapterGroup(primary, copies.Where(c => c != primary).ToList()));
        }
        return groups
            .OrderByDescending(g => g.Number)
            .ThenBy(g => g.Primary.SourceOrder)
            .ToList();
    }

    public static ChapterGroup? NextToRead(IReadOnlyList<ChapterGroup> descending)
    {
        if (descending.Count == 0)
        {
            return null;
        }
        var lastRead = descending.Where(g => g.LastReadAt > 0).OrderByDescending(g => g.LastReadAt).FirstOrDefault();
        var ascending = descending.Reverse().ToList();
        if (lastRead is not null)
        {
            if (!lastRead.Read)
            {
                return lastRead;
            }
            var index = ascending.IndexOf(lastRead);
            var next = ascending.Skip(index + 1).FirstOrDefault(g => !g.Read);
            return next ?? lastRead;
        }
        return ascending.FirstOrDefault(g => !g.Read) ?? ascending[0];
    }
}
