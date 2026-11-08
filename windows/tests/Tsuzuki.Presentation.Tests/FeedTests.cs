using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class FeedTests
{
    private static readonly DateTimeOffset Today = new(2025, 11, 6, 18, 0, 0, TimeSpan.Zero);

    private static string Day(long ms) => DateTimeOffset.FromUnixTimeMilliseconds(ms).UtcDateTime.Date.ToString("yyyy-MM-dd");

    private static long At(int daysAgo, int hour) => new DateTimeOffset(Today.UtcDateTime.Date.AddDays(-daysAgo).AddHours(hour), TimeSpan.Zero).ToUnixTimeMilliseconds();

    private static UpdateEntry Update(long mangaId, long chapterId, double number, long fetched, bool read = false) =>
        new(mangaId, "Title " + mangaId, null, chapterId, "Ch. " + number, number, null, fetched, fetched, read, 0, 2499283573021220255);

    private static HistoryEntry Read(long mangaId, long chapterId, long at) =>
        new(mangaId, "Title " + mangaId, null, chapterId, "Ch", chapterId, 0, at, 2499283573021220255, null);

    [Fact]
    public void New_chapters_of_one_title_on_one_day_collapse_into_one_row()
    {
        var updates = new[]
        {
            Update(1, 13, 13, At(0, 12)),
            Update(2, 20, 5, At(0, 11)),
            Update(1, 12, 12, At(0, 10)),
            Update(1, 11, 11, At(1, 10)),
        };

        var clusters = UpdateClusters.Build(updates, unreadOnly: false, Day);

        Assert.Equal([2, 1, 1], clusters.Select(c => c.Count));
        Assert.Equal([13, 12], clusters[0].Select(u => u.ChapterId));
        Assert.Equal(11, clusters[2][0].ChapterId);
    }

    [Fact]
    public void Unread_only_drops_read_chapters_and_mark_all_read_targets_each_unread_chapter_once()
    {
        var updates = new[]
        {
            Update(1, 13, 13, At(0, 12)),
            Update(1, 12, 12, At(0, 10), read: true),
            Update(1, 13, 13, At(0, 9)),
        };

        var clusters = UpdateClusters.Build(updates, unreadOnly: true, Day);

        Assert.All(clusters.SelectMany(c => c), u => Assert.False(u.Read));
        Assert.Equal([13L], UpdateClusters.UnreadIds(updates));
    }

    [Fact]
    public void Reading_one_title_several_times_in_a_row_shows_as_one_streak_per_day()
    {
        var history = new[]
        {
            Read(1, 3, At(0, 12)),
            Read(1, 2, At(0, 11)),
            Read(2, 9, At(0, 10)),
            Read(1, 1, At(0, 9)),
            Read(1, 0, At(1, 9)),
        };

        var streaks = HistoryStreaks.Build(history, Day);

        Assert.Equal([2, 1, 1, 1], streaks.Select(s => s.Count));
    }
}
