using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class ReaderSessionViewModelTests
{
    private static ChapterRow Row(long id, double number, string? group = null, bool readable = true) =>
        new(id, 1, 2499283573021220255, $"/chapter/{id}", $"Ch. {number}", group, number, 0, (long)(100 - number), false, false, 0, 20, 0, null, readable);

    private static ReaderSessionViewModel Session(params ChapterRow[] rows)
    {
        var session = new ReaderSessionViewModel();
        session.SetChapters(rows.OrderByDescending(r => r.ChapterNumber).ToList(), 1);
        return session;
    }

    [Fact]
    public void Turning_past_the_last_page_shows_the_end_card_instead_of_opening_the_next_chapter()
    {
        var session = Session(Row(1, 1), Row(2, 2));
        session.Chapter = session.Ascending[0].Primary;

        var atEnd = session.Next(strip: false, endCardShown: false, pageCount: 20, lastShown: 19);
        var fromCard = session.Next(strip: false, endCardShown: true, pageCount: 20, lastShown: 19);

        Assert.Equal(ReaderStepKind.FinishChapter, atEnd.Kind);
        Assert.Equal(ReaderStepKind.NextChapter, fromCard.Kind);
        Assert.Equal(2, fromCard.Chapter!.ChapterId);
    }

    [Fact]
    public void The_next_chapter_keeps_the_same_group_and_skips_chapters_only_on_the_phone()
    {
        var session = Session(Row(1, 1, "A"), Row(2, 2, "B"), Row(3, 2, "A", readable: false), Row(4, 3, "B", readable: false), Row(5, 4, "A"));
        session.Chapter = session.Ascending[0].Primary;

        var next = session.Neighbour(1);
        session.Chapter = next;
        var after = session.Neighbour(1);

        Assert.Equal(2, next!.ChapterId);
        Assert.Equal(5, after!.ChapterId);
    }

    [Fact]
    public void Going_back_from_the_first_page_opens_the_previous_chapter_and_from_the_end_card_just_closes_it()
    {
        var session = Session(Row(1, 1), Row(2, 2));
        session.Chapter = session.Ascending[1].Primary;

        Assert.Equal(1, session.Previous(false, false, 20, 0).Chapter!.ChapterId);
        Assert.Equal(ReaderStepKind.HideEndCard, session.Previous(false, true, 20, 19).Kind);
        Assert.Equal(new ReaderStep(ReaderStepKind.Page, 4), session.Previous(false, false, 20, 5));
    }
}
