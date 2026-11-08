using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class HistoryPage : ListRowPage
{
    private readonly AutoSuggestBox search;
    private IReadOnlyList<HistoryEntry> history = [];

    public HistoryPage() : this(new AutoSuggestBox
    {
        PlaceholderText = "Search history",
        Width = 280,
        QueryIcon = new SymbolIcon(Symbol.Find),
    })
    {
    }

    private HistoryPage(AutoSuggestBox search) : base("History", null, search)
    {
        this.search = search;
        AutomationProperties.SetName(search, "Search history by title");
        search.TextChanged += (_, e) =>
        {
            if (e.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
            {
                ShowHistory();
            }
        };
        search.QuerySubmitted += (_, _) => ShowHistory();
    }


    protected override Tsuzuki.Presentation.IFeed CreateFeed(Tsuzuki.Core.Engine engine) =>
        Feed(engine, library => library.GetHistoryAsync(500), fetched =>
        {
            history = fetched;
            ShowHistory();
        });

    private void ShowHistory()
    {
        var query = search.Text.Trim();
        var visible = query.Length == 0
            ? history
            : history.Where(h => h.Title.Contains(query, StringComparison.CurrentCultureIgnoreCase)).ToList();
        var groups = RowFormat.Group(visible, h => RowFormat.Day(h.LastReadAt), Row);
        var titles = history.Select(h => h.MangaId).Distinct().Count();
        var searching = query.Length > 0 && history.Count > 0;
        Show(groups,
            history.Count == 0 ? string.Empty : $"{titles:N0} {(titles == 1 ? "title" : "titles")} read recently",
            searching ? "\uE721" : "\uE81C",
            searching ? "No matches" : "Nothing read yet",
            searching
                ? $"Nothing in your history matches \u201c{query}\u201d."
                : "Chapters you read here or on your phone show up here.");
    }

    private static ListRowItem Row(HistoryEntry entry)
    {
        var chapter = entry.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(entry.ChapterNumber) : entry.ChapterName;
        var page = entry.LastPageRead + 1;
        var subtitle = entry.LastPageRead <= 0
            ? chapter
            : entry.PageCount is int total and > 0 ? $"{chapter} · page {page}/{total}" : $"{chapter} · page {page}";
        return new ListRowItem(entry.MangaId, entry.ChapterId, entry.LastPageRead, entry.Title, subtitle,
            RowFormat.Time(entry.LastReadAt), entry.ThumbnailUrl, entry.Source, false, "Resume", []);
    }
}
