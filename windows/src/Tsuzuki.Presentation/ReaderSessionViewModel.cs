using Tsuzuki.Core.Library;

namespace Tsuzuki.Presentation;

public enum ReaderStepKind
{
    None,
    Scroll,
    Page,
    FinishChapter,
    NextChapter,
    PreviousChapter,
    HideEndCard,
}

public readonly record struct ReaderStep(ReaderStepKind Kind, int Page = 0, ChapterRow? Chapter = null);

public sealed class ReaderSessionViewModel
{
    public IReadOnlyList<ChapterGroup> Ascending { get; private set; } = [];

    public ChapterRow? Chapter { get; set; }

    public void SetChapters(IReadOnlyList<ChapterRow> rows, long mangaId) => Ascending = ChapterGrouping.Group(rows, mangaId).Reverse().ToList();

    public ChapterRow? Neighbour(int delta)
    {
        var position = CurrentIndex();
        if (position < 0)
        {
            return null;
        }
        for (var i = position + delta; i >= 0 && i < Ascending.Count; i += delta)
        {
            if (ReadableCopy(Ascending[i]) is { } copy)
            {
                return copy;
            }
        }
        return null;
    }

    public ReaderStep Next(bool strip, bool endCardShown, int pageCount, int lastShown)
    {
        if (strip)
        {
            return new ReaderStep(ReaderStepKind.Scroll);
        }
        if (endCardShown)
        {
            return Neighbour(1) is { } next ? new ReaderStep(ReaderStepKind.NextChapter, Chapter: next) : new ReaderStep(ReaderStepKind.FinishChapter);
        }
        if (pageCount == 0)
        {
            return new ReaderStep(ReaderStepKind.None);
        }
        return lastShown >= pageCount - 1 ? new ReaderStep(ReaderStepKind.FinishChapter) : new ReaderStep(ReaderStepKind.Page, lastShown + 1);
    }

    public ReaderStep Previous(bool strip, bool endCardShown, int pageCount, int firstShown)
    {
        if (strip)
        {
            return new ReaderStep(ReaderStepKind.Scroll);
        }
        if (endCardShown)
        {
            return new ReaderStep(ReaderStepKind.HideEndCard);
        }
        if (pageCount == 0)
        {
            return new ReaderStep(ReaderStepKind.None);
        }
        if (firstShown <= 0)
        {
            return Neighbour(-1) is { } previous ? new ReaderStep(ReaderStepKind.PreviousChapter, Chapter: previous) : new ReaderStep(ReaderStepKind.None);
        }
        return new ReaderStep(ReaderStepKind.Page, firstShown - 1);
    }

    private int CurrentIndex()
    {
        if (Chapter is not { } current)
        {
            return -1;
        }
        for (var i = 0; i < Ascending.Count; i++)
        {
            var group = Ascending[i];
            if (group.Primary.ChapterId == current.ChapterId || group.Alternates.Any(a => a.ChapterId == current.ChapterId))
            {
                return i;
            }
        }
        return -1;
    }

    private ChapterRow? ReadableCopy(ChapterGroup group)
    {
        var copies = group.Alternates.Prepend(group.Primary).Where(c => c.Readable && c.ExternalUrl is null);
        if (Chapter?.Scanlator is { } scan && copies.FirstOrDefault(c => c.Scanlator == scan) is { } same)
        {
            return same;
        }
        return copies.FirstOrDefault();
    }
}
