using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class UpdatesPage : TzPage, ITzPage, ICoverHost
{
    private sealed record FeedData(IReadOnlyList<UpdateEntry> Updates, IReadOnlyList<LibraryEntry> Entries);

    private const int UpdatesLimit = 300;
    private const int ShelfSize = 20;
    private const long MangaDexSource = 2499283573021220255;

    private readonly TextBlock summary = new() { Style = Resource<Style>("CaptionTextBlockStyle") };
    private readonly HomeShelf shelf = new("Continue reading", "See all reading history", () => App.MainWindow.NavigateRootAsync("history"));
    private readonly TextBlock unreadCount = new() { Style = Resource<Style>("CaptionTextBlockStyle") };
    private readonly FeedTimelineView timeline = new();
    private readonly StackPanel empty = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Spacing = 8, MaxWidth = 420, Visibility = Visibility.Collapsed };
    private readonly FontIcon emptyIcon = new() { FontSize = 48, Margin = new Thickness(0, 0, 0, 8) };
    private readonly TextBlock emptyTitle = new() { Style = Resource<Style>("SubtitleTextBlockStyle"), HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center };
    private readonly TextBlock emptyBody = new() { Style = Resource<Style>("BodyTextBlockStyle"), HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap };
    private Tsuzuki.Presentation.FeedViewModel<FeedData>? feed;
    private Tsuzuki.Presentation.FeedViewModel<ContinueEntry>? shelfFeed;
    private readonly HashSet<long> pinned = [];
    private bool unreadOnly;
    private bool shown;
    private bool firstLoad = true;
    private bool checking;

    public UpdatesPage() : this(new CommandBar
    {
        DefaultLabelPosition = CommandBarDefaultLabelPosition.Right,
        OverflowButtonVisibility = CommandBarOverflowButtonVisibility.Collapsed,
        Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Transparent),
        IsDynamicOverflowEnabled = false,
    })
    {
    }

    private UpdatesPage(CommandBar commands)
    {
        AutomationProperties.SetName(this, "Updates");
        unreadCount.Foreground = Resource<Brush>("TextFillColorSecondaryBrush");
        unreadCount.VerticalAlignment = VerticalAlignment.Center;
        emptyIcon.FontFamily = Resource<FontFamily>("SymbolThemeFontFamily");
        emptyIcon.Foreground = Resource<Brush>("TextFillColorTertiaryBrush");
        emptyBody.Foreground = Resource<Brush>("TextFillColorSecondaryBrush");
        empty.Children.Add(emptyIcon);
        empty.Children.Add(emptyTitle);
        empty.Children.Add(emptyBody);

        var unreadToggle = new AppBarToggleButton { Label = "Unread only", Icon = Glyph("\uE71C") };
        unreadToggle.Click += (_, _) =>
        {
            unreadOnly = unreadToggle.IsChecked == true;
            ShowFeed();
        };
        var markAll = new AppBarButton { Label = "Mark all read", Icon = Glyph("\uE8FB") };
        markAll.Click += (_, _) => _ = Log.Guard("next-updates-mark-all", MarkAllReadAsync);
        var check = new AppBarButton { Label = "Check for updates", Icon = Glyph("\uE72C") };
        check.Click += (_, _) => _ = Log.Guard("next-updates-check", () => CheckNowAsync(check));
        commands.PrimaryCommands.Add(unreadToggle);
        commands.PrimaryCommands.Add(markAll);
        commands.PrimaryCommands.Add(check);

        timeline.ChipInvoked += (_, e) => _ = Log.Guard("feed-chip-open", () => AppNav.OpenReaderAsync(e.Item.MangaId, e.Chip.ChapterId, e.Chip.Page, e.Origin));
        timeline.DetailsInvoked += (_, e) => _ = Log.Guard("feed-card-open", () => AppNav.OpenDetailsAsync(e.Item.MangaId, e.Cover));
        timeline.CardMarkRead += (_, e) => _ = Log.Guard("feed-card-mark", () => MarkReadAsync(e.Item.MangaId, e.UnreadIds));
        timeline.DayMarkRead += (_, group) => _ = Log.Guard("feed-day-mark", () => MarkDayReadAsync(group));

        var title = new TextBlock { Text = "Updates", Style = Resource<Style>("TitleTextBlockStyle") };
        AutomationProperties.SetHeadingLevel(title, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level1);
        summary.Foreground = Resource<Brush>("TextFillColorSecondaryBrush");
        var titles = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Bottom };
        titles.Children.Add(title);
        titles.Children.Add(summary);
        commands.VerticalAlignment = VerticalAlignment.Bottom;

        var header = new Grid { Padding = new Thickness(36, 28, 12, 4) };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.Children.Add(titles);
        Grid.SetColumn(commands, 1);
        header.Children.Add(commands);

        var section = new Grid { Margin = new Thickness(0, 20, 0, 8) };
        section.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        section.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var sectionTitle = new TextBlock { Text = "New chapters", Style = Resource<Style>("SubtitleTextBlockStyle") };
        AutomationProperties.SetHeadingLevel(sectionTitle, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level2);
        unreadCount.Margin = new Thickness(10, 4, 0, 0);
        section.Children.Add(sectionTitle);
        Grid.SetColumn(unreadCount, 1);
        section.Children.Add(unreadCount);

        var body = new Grid { Margin = new Thickness(36, 0, 36, 24) };
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        shelf.Visibility = Visibility.Collapsed;
        Grid.SetRow(shelf, 0);
        body.Children.Add(shelf);
        Grid.SetRow(section, 1);
        body.Children.Add(section);
        Grid.SetRow(timeline, 2);
        body.Children.Add(timeline);
        Grid.SetRow(empty, 2);
        body.Children.Add(empty);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.Children.Add(header);
        Grid.SetRow(body, 1);
        root.Children.Add(body);
        Content = root;

        if (Services.Engine is { } engine)
        {
            EnsureFeeds(engine);
        }
        else
        {
            AppHost.EngineReady += (_, _) => Ui.Enqueue(DispatcherQueue, () =>
            {
                if (Services.Engine is { } ready && feed is null)
                {
                    EnsureFeeds(ready);
                    if (shown)
                    {
                        feed.Activate();
                        shelfFeed.Activate();
                    }
                }
            }, "feed-ready");
        }
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId)
    {
        foreach (var cover in timeline.CoversOf(mangaId))
        {
            yield return cover;
        }
        if (shelf.Visibility != Visibility.Visible)
        {
            yield break;
        }
        foreach (var child in shelf.Row.Children)
        {
            if (child is ContinueTile { Entry.MangaId: var id } tile && id == mangaId)
            {
                yield return tile.CoverElement;
            }
        }
    }

    private void EnsureFeeds(Tsuzuki.Core.Engine engine)
    {
        if (feed is not null)
        {
            return;
        }
        var ui = new DispatcherUi(DispatcherQueue);
        feed = new Tsuzuki.Presentation.FeedViewModel<FeedData>(
            engine.Library,
            async library => new FeedData[] { new(await library.GetUpdatesAsync(UpdatesLimit), await library.GetLibraryAsync()) },
            ui,
            () => Ui.ReaderOpen);
        feed.Changed += (_, _) => ShowFeed();
        shelfFeed = new Tsuzuki.Presentation.FeedViewModel<ContinueEntry>(
            engine.Library,
            library => library.GetContinueReadingAsync(ShelfSize),
            ui,
            () => Ui.ReaderOpen);
        shelfFeed.Changed += (_, _) => SyncShelf(shelfFeed.Items);
        Ui.ReaderClosed += OnReaderClosed;
    }

    private void OnReaderClosed(object? sender, EventArgs e)
    {
        feed?.ReaderClosed();
        shelfFeed?.ReaderClosed();
    }

    public void OnShown()
    {
        shown = true;
        feed?.Activate();
        shelfFeed?.Activate();
    }

    public void OnHidden()
    {
        shown = false;
        feed?.Deactivate();
        shelfFeed?.Deactivate();
        pinned.Clear();
    }

    private void ShowFeed()
    {
        if (feed is not { } model || !model.Loaded)
        {
            return;
        }
        var data = model.Items is [var loaded] ? loaded : new FeedData([], []);
        var visible = unreadOnly ? data.Updates.Where(u => !u.Read || pinned.Contains(u.MangaId)).ToList() : [.. data.Updates];
        var clusters = Tsuzuki.Presentation.UpdateClusters.Build(visible, unreadOnly: false, RowFormat.Day);
        var groups = BuildGroups(clusters, data.Entries);
        var unread = data.Updates.Count(u => !u.Read);
        unreadCount.Text = data.Updates.Count == 0
            ? string.Empty
            : unread == 0 ? "All caught up" : unread == 1 ? "1 unread chapter" : $"{unread:N0} unread";
        var changed = timeline.Present(groups);
        summary.Text = data.Updates.Count == 0
            ? string.Empty
            : unread == 0 ? "All caught up" : $"{unread:N0} unread {(unread == 1 ? "chapter" : "chapters")}";
        summary.Visibility = summary.Text.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        var caughtUp = unreadOnly && data.Updates.Count > 0;
        emptyIcon.Glyph = caughtUp ? "\uE930" : "\uE895";
        emptyTitle.Text = caughtUp ? "You're all caught up" : "No new chapters yet";
        emptyBody.Text = caughtUp
            ? "Every chapter in Updates is read. Turn off Unread only to see them all."
            : "New chapters for titles in your library show up here after a check.";
        var none = timeline.Count == 0;
        empty.Visibility = none ? Visibility.Visible : Visibility.Collapsed;
        timeline.Visibility = none ? Visibility.Collapsed : Visibility.Visible;
        if (firstLoad && changed && !none && !Design.Motion.Reduced)
        {
            _ = Design.Motion.FadeIn(timeline, Design.Motion.Medium1, 12);
        }
        if (!none)
        {
            firstLoad = false;
        }
    }

    private List<FeedGroup> BuildGroups(IReadOnlyList<IReadOnlyList<UpdateEntry>> clusters, IReadOnlyList<LibraryEntry> entries)
    {
        var merged = entries.DistinctBy(e => e.MangaId).ToDictionary(e => e.MangaId, e => e.IsMerged);
        var groups = new List<FeedGroup>();
        foreach (var day in clusters.GroupBy(c => RowFormat.Day(c[0].DateFetch)))
        {
            var dayEntries = day.SelectMany(c => c).ToList();
            groups.Add(new FeedGroup(
                day.Key,
                day.Key == "Today",
                dayEntries.Select(u => u.MangaId).Distinct().Count(),
                dayEntries.Count,
                Tsuzuki.Presentation.UpdateClusters.UnreadIds(dayEntries),
                day.Select(cluster => Card(cluster, merged))));
        }
        return groups;
    }

    private static FeedCardItem Card(IReadOnlyList<UpdateEntry> cluster, IReadOnlyDictionary<long, bool> merged)
    {
        var newest = cluster[0];
        var chips = FeedTimeline.Chips(cluster);
        var unread = cluster.Count(u => !u.Read);
        var items = new List<FeedChipItem>();
        for (var i = 0; i < chips.Shown.Count; i++)
        {
            var number = Format.ChapterNumber(chips.Shown[i].ChapterNumber);
            items.Add(new FeedChipItem(i == 0 ? "Ch. " + number : number, chips.Shown[i].ChapterId, Math.Max(chips.Shown[i].LastPageRead, 0), true));
        }
        if (chips.Overflow > 0)
        {
            items.Add(new FeedChipItem($"+{chips.Overflow}", 0, 0, false));
        }
        if (unread == 0)
        {
            var chapter = newest.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(newest.ChapterNumber) : newest.ChapterName;
            items = [new FeedChipItem($"{chapter} · read", newest.ChapterId, 0, false)];
        }
        merged.TryGetValue(newest.MangaId, out var isMerged);
        return new FeedCardItem(
            newest.MangaId,
            newest.Title,
            newest.ThumbnailUrl,
            newest.Source,
            isMerged,
            unread,
            items,
            [.. cluster.Where(u => !u.Read).Select(u => u.ChapterId).Distinct()],
            unread == 0);
    }

    private void SyncShelf(IReadOnlyList<ContinueEntry> items)
    {
        var row = shelf.Row.Children;
        while (row.Count > items.Count)
        {
            row.RemoveAt(row.Count - 1);
        }
        for (var i = 0; i < items.Count; i++)
        {
            if (i >= row.Count)
            {
                row.Add(new ContinueTile(lead: i == 0));
            }
            if (row[i] is ContinueTile tile)
            {
                tile.Bind(items[i]);
            }
        }
        shelf.Visibility = items.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private async Task MarkDayReadAsync(FeedGroup group)
    {
        if (group.UnreadIds.Count == 0)
        {
            return;
        }
        var noun = group.UnreadIds.Count == 1 ? "chapter" : "chapters";
        if (!await Dialogs.ConfirmAsync($"Mark {group.UnreadIds.Count:N0} {noun} as read?",
                $"Every unread chapter from {group.Header} ({group.UnreadIds.Count:N0} {noun}) is marked as read.",
                "Mark as read"))
        {
            return;
        }
        foreach (var mangaId in group.Select(i => i.MangaId).Distinct())
        {
            pinned.Add(mangaId);
        }
        await MarkReadIdsAsync(group.UnreadIds);
    }

    private async Task MarkReadAsync(long mangaId, IReadOnlyList<long> ids)
    {
        if (ids.Count == 0)
        {
            return;
        }
        pinned.Add(mangaId);
        await MarkReadIdsAsync(ids);
    }

    private async Task MarkAllReadAsync()
    {
        var data = feed?.Items is [var loaded] ? loaded : null;
        var ids = Tsuzuki.Presentation.UpdateClusters.UnreadIds(data?.Updates ?? []);
        if (ids.Count == 0)
        {
            Toasts.Show("Nothing to mark", "Every chapter in Updates is already read.", Icons.CircleCheck);
            return;
        }
        var noun = ids.Count == 1 ? "chapter" : "chapters";
        if (!await Dialogs.ConfirmAsync($"Mark {ids.Count:N0} {noun} as read?",
                $"Every unread chapter listed in Updates ({ids.Count:N0} {noun}) is marked as read.",
                "Mark as read"))
        {
            return;
        }
        foreach (var mangaId in (data?.Updates ?? []).Where(u => !u.Read).Select(u => u.MangaId))
        {
            pinned.Add(mangaId);
        }
        await MarkReadIdsAsync(ids);
    }

    private async Task MarkReadIdsAsync(IReadOnlyList<long> ids)
    {
        if (ids.Count > 0 && Services.Engine is { } engine)
        {
            await engine.Library.SetChaptersReadAsync(ids, true);
        }
    }

    private static FontIcon Glyph(string glyph) =>
        new() { Glyph = glyph, FontFamily = (Microsoft.UI.Xaml.Media.FontFamily)Application.Current.Resources["SymbolThemeFontFamily"] };

    private async Task CheckNowAsync(FrameworkElement anchor)
    {
        if (checking)
        {
            return;
        }
        checking = true;
        var control = anchor as Control;
        if (control is not null)
        {
            control.IsEnabled = false;
        }
        try
        {
            var result = await LibraryUpdater.RunAsync();
            Toasts.Show(result.NewChapters > 0 ? $"{result.NewChapters} new chapters" : "You're up to date",
                result.Failed > 0 ? $"{result.Failed} titles could not be checked" : $"Checked {result.Checked} titles", Icons.Sparkles);
        }
        finally
        {
            checking = false;
            if (control is not null)
            {
                control.IsEnabled = true;
            }
        }
    }

    private static T Resource<T>(string key) => (T)Application.Current.Resources[key];
}
