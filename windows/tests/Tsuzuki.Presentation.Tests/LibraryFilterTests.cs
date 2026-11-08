using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class LibraryFilterTests
{
    private static LibraryEntry Entry(long id, bool merged = false) =>
        new(id, 1, $"/manga/{id}", $"Title {id}", null, 1, 3, id, id, [], merged, true);

    private static readonly IReadOnlyList<LibraryEntry> Entries = [Entry(1), Entry(2, merged: true), Entry(3), Entry(4, merged: true)];

    [Fact]
    public void Downloaded_shows_only_titles_with_a_downloaded_chapter()
    {
        var visible = LibraryViewModel.Filter(Entries, new LibraryQuery(DownloadedOnly: true), null, null, new HashSet<long> { 2, 3 });
        Assert.Equal([2L, 3L], visible.Select(e => e.MangaId).Order());
    }

    [Fact]
    public void Downloaded_with_nothing_downloaded_shows_nothing()
    {
        Assert.Empty(LibraryViewModel.Filter(Entries, new LibraryQuery(DownloadedOnly: true), null, null, new HashSet<long>()));
    }

    [Fact]
    public void Merged_shows_only_merged_titles_and_combines_with_downloaded()
    {
        Assert.Equal([2L, 4L], LibraryViewModel.Filter(Entries, new LibraryQuery(MergedOnly: true), null, null).Select(e => e.MangaId).Order());
        Assert.Equal([2L], LibraryViewModel.Filter(Entries, new LibraryQuery(DownloadedOnly: true, MergedOnly: true), null, null, new HashSet<long> { 1, 2 }).Select(e => e.MangaId));
    }
}
