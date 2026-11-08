using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using VirtualKey = Windows.System.VirtualKey;

namespace Tsuzuki.App.Next;

public sealed partial class LibraryPage : TzPage, ITzPage, ICoverHost
{
    private static readonly (string Key, string Label)[] Sorts =
    [
        ("recent", "Recently read"),
        ("unread", "Most unread"),
        ("title", "Title"),
        ("added", "Recently added"),
        ("source", "Source"),
        ("progress", "Progress"),
    ];

    private static readonly (MangaStatus Status, string Label)[] Statuses =
    [
        (MangaStatus.Ongoing, "Ongoing"),
        (MangaStatus.Completed, "Completed"),
        (MangaStatus.OnHiatus, "On hiatus"),
        (MangaStatus.Cancelled, "Cancelled"),
        (MangaStatus.PublishingFinished, "Publishing finished"),
        (MangaStatus.Licensed, "Licensed"),
        (MangaStatus.Unknown, "Unknown"),
    ];

    private const double TileSpacing = 16;

    private readonly BulkObservableCollection<TileData> source = [];
    private readonly Dictionary<DependencyObject, CoverTile> tiles = [];
    private readonly Dictionary<DependencyObject, LibraryListRow> rows = [];
    private readonly HashSet<DependencyObject> realized = [];
    private readonly DispatcherQueueTimer searchDebounce;

    private readonly TextBlock summary;
    private readonly AppBarButton sortButton;
    private readonly MenuFlyout sortMenu = new() { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight };
    private readonly AppBarButton filterButton;
    private readonly MenuFlyout filterMenu = new() { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight };
    private readonly AppBarButton viewButton;
    private readonly AppBarToggleButton selectButton;
    private readonly AppBarButton refreshButton;
    private readonly SelectorBar categoryBar;
    private readonly ScrollViewer categoryScroller;
    private readonly InfoBar refreshBar;
    private readonly ProgressBar refreshProgress;
    private readonly GridView grid;
    private readonly ListView list;
    private readonly Grid listPane;
    private readonly Grid listHeader;
    private readonly ProgressRing loadingRing;
    private readonly StackPanel emptyState;
    private readonly TextBlock emptyTitle;
    private readonly TextBlock emptyBody;
    private readonly StackPanel emptyActions;
    private readonly Border selectionBar;
    private readonly TextBlock selectionCount;
    private readonly AppBarButton[] selectionCommands;

    private IReadOnlyList<LibraryEntry> all = [];
    private IReadOnlyList<CategoryInfo> categories = [];
    private List<long?> categoryUids = [];
    private string filter = string.Empty;
    private string shape = string.Empty;
    private string appliedFilter = string.Empty;
    private Tsuzuki.Presentation.LibraryViewModel? model;
    private bool animateNext = true;
    private bool shown;
    private bool firstLoad = true;
    private bool loaded;
    private bool syncingCategories;
    private bool selecting;
    private bool refreshing;
    private double tileWidth = 168;

    public LibraryPage()
    {
        summary = new TextBlock { Style = LibraryActions.Style("CaptionTextBlockStyle"), Foreground = LibraryActions.Brush("TextFillColorSecondaryBrush"), Margin = new Thickness(0, 4, 0, 0) };
        var titleStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        titleStack.Children.Add(new TextBlock { Text = "Library", Style = LibraryActions.Style("TitleTextBlockStyle") });
        titleStack.Children.Add(summary);

        sortButton = Command("\uE8CB", "Sort");
        sortButton.Flyout = sortMenu;
        filterButton = Command("\uE71C", "Filter");
        filterButton.Flyout = filterMenu;
        filterMenu.Opening += (_, _) => BuildFilterMenu();
        viewButton = Command("\uE8FD", "List view");
        viewButton.Click += (_, _) => SetView(Services.Settings.LibraryView == "list" ? "grid" : "list");
        selectButton = new AppBarToggleButton { Label = "Select", Icon = LibraryActions.Glyph("\uE762") };
        AutomationProperties.SetName(selectButton, "Select titles");
        ToolTipService.SetToolTip(selectButton, "Select titles");
        selectButton.Click += (_, _) => SetSelecting(selectButton.IsChecked == true);
        refreshButton = Command("\uE72C", "Refresh");
        AutomationProperties.SetName(refreshButton, "Check for new chapters");
        ToolTipService.SetToolTip(refreshButton, "Check for new chapters");
        refreshButton.Click += (_, _) => _ = Log.Guard("next-library-refresh", RefreshFromSourcesAsync);

        var commands = new CommandBar
        {
            DefaultLabelPosition = CommandBarDefaultLabelPosition.Right,
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            OverflowButtonVisibility = CommandBarOverflowButtonVisibility.Auto,
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Right,
        };
        commands.PrimaryCommands.Add(sortButton);
        commands.PrimaryCommands.Add(filterButton);
        commands.PrimaryCommands.Add(viewButton);
        commands.PrimaryCommands.Add(selectButton);
        commands.PrimaryCommands.Add(refreshButton);

        var header = new Grid { Margin = new Thickness(36, 28, 36, 0), ColumnSpacing = 16 };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.ColumnDefinitions.Add(new ColumnDefinition());
        header.Children.Add(titleStack);
        Grid.SetColumn(commands, 1);
        header.Children.Add(commands);

        categoryBar = new SelectorBar();
        AutomationProperties.SetName(categoryBar, "Categories");
        categoryBar.SelectionChanged += OnCategoryChanged;
        categoryScroller = new ScrollViewer
        {
            Content = categoryBar,
            HorizontalScrollMode = ScrollMode.Enabled,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden,
            VerticalScrollMode = ScrollMode.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Margin = new Thickness(24, 12, 36, 0),
            Visibility = Visibility.Collapsed,
        };

        refreshProgress = new ProgressBar { IsIndeterminate = true, Margin = new Thickness(0, 0, 0, 12) };
        refreshBar = new InfoBar
        {
            Title = "Checking for new chapters",
            Severity = InfoBarSeverity.Informational,
            IsClosable = false,
            IsOpen = false,
            Content = refreshProgress,
            Margin = new Thickness(36, 12, 36, 0),
        };

        grid = new GridView
        {
            SelectionMode = ListViewSelectionMode.None,
            Padding = new Thickness(36, 16, 36, 96),
            ItemTemplate = EmptyTemplate(),
            ItemsPanel = (ItemsPanelTemplate)XamlReader.Load(
                """<ItemsPanelTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><ItemsWrapGrid Orientation="Horizontal"/></ItemsPanelTemplate>"""),
            ItemContainerStyle = ContainerStyle(typeof(GridViewItem), new Thickness(0, 0, TileSpacing, 24)),
            ItemsSource = source,
        };
        AutomationProperties.SetName(grid, "Library titles");
        grid.ContainerContentChanging += OnGridContainerChanging;
        grid.SelectionChanged += (_, _) => UpdateSelectionBar();
        grid.SizeChanged += (_, _) => Relayout();
        grid.Loaded += (_, _) => Relayout();

        listHeader = new Grid { Padding = new Thickness(8, 0, 8, 6), Margin = new Thickness(36, 16, 36, 0) };
        LibraryListRow.Columns(listHeader);
        list = new ListView
        {
            SelectionMode = ListViewSelectionMode.None,
            Padding = new Thickness(36, 0, 36, 96),
            ItemTemplate = EmptyTemplate(),
            ItemContainerStyle = ContainerStyle(typeof(ListViewItem), new Thickness(0)),
            ItemsSource = source,
        };
        AutomationProperties.SetName(list, "Library titles");
        list.ContainerContentChanging += OnListContainerChanging;
        list.SelectionChanged += (_, _) => UpdateSelectionBar();
        listPane = new Grid { RowDefinitions = { new RowDefinition { Height = GridLength.Auto }, new RowDefinition() } };
        listPane.Children.Add(listHeader);
        Grid.SetRow(list, 1);
        listPane.Children.Add(list);

        loadingRing = new ProgressRing { IsActive = true, Width = 40, Height = 40, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        AutomationProperties.SetName(loadingRing, "Loading library");

        emptyTitle = new TextBlock { Style = LibraryActions.Style("SubtitleTextBlockStyle"), HorizontalAlignment = HorizontalAlignment.Center };
        emptyBody = new TextBlock { Style = LibraryActions.Style("BodyTextBlockStyle"), Foreground = LibraryActions.Brush("TextFillColorSecondaryBrush"), TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
        var pair = new Button { Content = "Pair my phone", Style = LibraryActions.Style("AccentButtonStyle") };
        pair.Click += (_, _) => DevicesPanel.Show();
        var explore = new Button { Content = "Explore MangaDex" };
        explore.Click += (_, _) => _ = App.MainWindow.NavigateRootAsync("browse");
        emptyActions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 0) };
        emptyActions.Children.Add(pair);
        emptyActions.Children.Add(explore);
        emptyState = new StackPanel { Spacing = 8, MaxWidth = 420, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Visibility = Visibility.Collapsed };
        emptyState.Children.Add(new FontIcon { Glyph = "\uE8F1", FontFamily = LibraryActions.Symbols, FontSize = 48, Foreground = LibraryActions.Brush("TextFillColorTertiaryBrush"), Margin = new Thickness(0, 0, 0, 8) });
        emptyState.Children.Add(emptyTitle);
        emptyState.Children.Add(emptyBody);
        emptyState.Children.Add(emptyActions);

        selectionCount = new TextBlock { Style = LibraryActions.Style("BodyStrongTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 8, 0), MinWidth = 96 };
        AutomationProperties.SetLiveSetting(selectionCount, Microsoft.UI.Xaml.Automation.Peers.AutomationLiveSetting.Polite);
        var markRead = Command("\uE8FB", "Mark read");
        markRead.Click += (_, _) => _ = Log.Guard("next-library-bulk", () => SetSelectedReadAsync(true));
        var markUnread = Command("\uE7B3", "Mark unread");
        markUnread.Click += (_, _) => _ = Log.Guard("next-library-bulk", () => SetSelectedReadAsync(false));
        var setCategories = Command("\uE8EC", "Set categories");
        setCategories.Click += (_, _) => _ = Log.Guard("next-library-bulk", () => ShowBulkCategoriesAsync(setCategories));
        var remove = Command("\uE74D", "Remove");
        remove.Click += (_, _) => _ = Log.Guard("next-library-bulk", RemoveSelectedAsync);
        var cancel = Command("\uE711", "Cancel");
        cancel.Click += (_, _) => SetSelecting(false);
        selectionCommands = [markRead, markUnread, setCategories, remove];
        var bulk = new CommandBar
        {
            DefaultLabelPosition = CommandBarDefaultLabelPosition.Right,
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            OverflowButtonVisibility = CommandBarOverflowButtonVisibility.Collapsed,
        };
        foreach (var command in selectionCommands)
        {
            bulk.PrimaryCommands.Add(command);
        }
        bulk.PrimaryCommands.Add(new AppBarSeparator());
        bulk.PrimaryCommands.Add(cancel);
        var bulkRow = new StackPanel { Orientation = Orientation.Horizontal };
        bulkRow.Children.Add(selectionCount);
        bulkRow.Children.Add(bulk);
        selectionBar = new Border
        {
            Child = bulkRow,
            Background = LibraryActions.Brush("SolidBackgroundFillColorBaseBrush"),
            BorderBrush = LibraryActions.Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(4),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(0, 0, 0, 24),
            Shadow = new ThemeShadow(),
            Translation = new System.Numerics.Vector3(0, 0, 32),
            Visibility = Visibility.Collapsed,
        };
        AutomationProperties.SetName(selectionBar, "Selection commands");

        var content = new Grid();
        content.Children.Add(grid);
        content.Children.Add(listPane);
        content.Children.Add(emptyState);
        content.Children.Add(loadingRing);
        content.Children.Add(selectionBar);

        var root = new Grid
        {
            RowDefinitions =
            {
                new RowDefinition { Height = GridLength.Auto },
                new RowDefinition { Height = GridLength.Auto },
                new RowDefinition { Height = GridLength.Auto },
                new RowDefinition(),
            },
        };
        root.Children.Add(header);
        Grid.SetRow(categoryScroller, 1);
        root.Children.Add(categoryScroller);
        Grid.SetRow(refreshBar, 2);
        root.Children.Add(refreshBar);
        Grid.SetRow(content, 3);
        root.Children.Add(content);
        Content = root;

        BuildSortMenu();
        BuildListHeader();
        ApplyView();
        UpdateFilterLabel();
        KeyDown += OnKeyDown;

        AppHost.TilesResized += (_, _) => { tileWidth = 0; Relayout(); };
        searchDebounce = DispatcherQueue.CreateTimer();
        searchDebounce.Interval = TimeSpan.FromMilliseconds(150);
        searchDebounce.IsRepeating = false;
        searchDebounce.Tick += (_, _) =>
        {
            if (shown)
            {
                _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: false));
            }
        };

        EnsureModel();
        AppHost.EngineReady += OnEngineReady;
        Ui.ReaderClosed += (_, _) => model?.ReaderClosed();
        filter = App.MainWindow.SearchText.Trim();
        App.MainWindow.SearchChanged += (_, text) =>
        {
            filter = text.Trim();
            searchDebounce.Stop();
            searchDebounce.Start();
        };
    }

    public void OnShown()
    {
        shown = true;
        EnsureModel();
        model?.Activate();
        if (model is { Loaded: true } && filter != appliedFilter)
        {
            _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: false));
        }
    }

    public void OnHidden()
    {
        shown = false;
        model?.Deactivate();
    }

    private void EnsureModel()
    {
        if (model is not null || Services.Engine is not { } engine)
        {
            return;
        }
        model = new Tsuzuki.Presentation.LibraryViewModel(engine.Library, engine.Sources.DisplayName, new DispatcherUi(DispatcherQueue), () => Ui.ReaderOpen, downloaded: engine.Downloads.DownloadedMangaIds);
        _ = model.SetQueryAsync(CurrentQuery());
        model.Changed += (_, change) => OnModelChanged(change);
        engine.Downloads.Changed += (_, _) =>
        {
            if (FilterTokens.Contains("downloaded"))
            {
                Ui.Enqueue(DispatcherQueue, () => _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: false)), "next-library-downloads");
            }
        };
        if (shown)
        {
            model.Activate();
        }
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId)
    {
        var listMode = Services.Settings.LibraryView == "list";
        foreach (var container in realized)
        {
            if (listMode && rows.TryGetValue(container, out var row) && row.Data?.MangaId == mangaId)
            {
                yield return row.CoverElement;
            }
            if (!listMode && tiles.TryGetValue(container, out var tile) && tile.Data?.MangaId == mangaId)
            {
                yield return tile.CoverElement;
            }
        }
    }

    private void OnEngineReady(object? sender, EventArgs e)
    {
        EnsureModel();
        model?.Invalidate();
    }

    private void UpdateSummary()
    {
        var unread = all.Sum(e => (long)e.UnreadCount);
        summary.Text = all.Count == 0 ? string.Empty : $"{all.Count:N0} {(all.Count == 1 ? "title" : "titles")} · {unread:N0} unread";
    }

    private void BuildCategories()
    {
        if (categories.Count == 0)
        {
            categoryScroller.Visibility = Visibility.Collapsed;
            categoryUids = [];
            categoryBar.Items.Clear();
            if (Services.Settings.LibraryCategory is not null)
            {
                Services.Settings.LibraryCategory = null;
                Services.Settings.Save();
            }
            return;
        }
        categoryScroller.Visibility = Visibility.Visible;
        var selected = Services.Settings.LibraryCategory;
        if (selected is { } s && s != 0 && categories.All(c => c.Uid != s))
        {
            selected = null;
            Services.Settings.LibraryCategory = null;
            Services.Settings.Save();
        }
        List<(long? Uid, string Name, int Count)> specs = [(null, "All", all.Count)];
        foreach (var category in categories.OrderBy(c => c.Order))
        {
            specs.Add((category.Uid, category.Name, all.Count(e => e.CategoryUids.Contains(category.Uid))));
        }
        var uncategorized = all.Count(e => e.CategoryUids.Count == 0);
        if (uncategorized > 0)
        {
            specs.Add((0, "Uncategorized", uncategorized));
        }
        if (selected == 0 && uncategorized == 0)
        {
            selected = null;
        }
        syncingCategories = true;
        try
        {
            if (!specs.Select(x => x.Uid).SequenceEqual(categoryUids))
            {
                categoryBar.Items.Clear();
                categoryUids = specs.Select(x => x.Uid).ToList();
                foreach (var _ in specs)
                {
                    var name = new TextBlock { VerticalAlignment = VerticalAlignment.Center };
                    var count = new TextBlock { Style = LibraryActions.Style("CaptionTextBlockStyle"), Foreground = LibraryActions.Brush("TextFillColorSecondaryBrush"), VerticalAlignment = VerticalAlignment.Center };
                    var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, Margin = new Thickness(4, 0, 10, 0) };
                    panel.Children.Add(name);
                    panel.Children.Add(count);
                    categoryBar.Items.Add(new SelectorBarItem { Child = panel });
                }
            }
            for (var i = 0; i < specs.Count; i++)
            {
                var (_, name, count) = specs[i];
                var item = categoryBar.Items[i];
                var panel = (StackPanel)item.Child;
                ((TextBlock)panel.Children[0]).Text = name;
                ((TextBlock)panel.Children[1]).Text = count.ToString("N0");
                AutomationProperties.SetName(item, $"{name}, {count:N0} {(count == 1 ? "title" : "titles")}");
            }
            var index = categoryUids.IndexOf(selected);
            categoryBar.SelectedItem = categoryBar.Items[Math.Max(index, 0)];
        }
        finally
        {
            syncingCategories = false;
        }
    }

    private void OnCategoryChanged(SelectorBar sender, SelectorBarSelectionChangedEventArgs args)
    {
        if (syncingCategories || sender.SelectedItem is not { } item)
        {
            return;
        }
        var index = sender.Items.IndexOf(item);
        if (index < 0 || index >= categoryUids.Count || categoryUids[index] == Services.Settings.LibraryCategory)
        {
            return;
        }
        Services.Settings.LibraryCategory = categoryUids[index];
        Services.Settings.Save();
        _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: true));
    }

    private void BuildSortMenu()
    {
        sortMenu.Items.Clear();
        foreach (var (key, label) in Sorts)
        {
            var item = new ToggleMenuFlyoutItem { Text = label, IsChecked = Services.Settings.LibrarySort == key };
            item.Click += (_, _) => SetSort(key);
            sortMenu.Items.Add(item);
        }
        var current = Sorts.FirstOrDefault(s => s.Key == Services.Settings.LibrarySort).Label ?? Sorts[0].Label;
        AutomationProperties.SetName(sortButton, $"Sort, {current}");
        ToolTipService.SetToolTip(sortButton, $"Sort: {current}");
    }

    private void SetSort(string key)
    {
        Services.Settings.LibrarySort = key;
        Services.Settings.Save();
        BuildSortMenu();
        BuildListHeader();
        _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: true));
    }

    private void BuildListHeader()
    {
        listHeader.Children.Clear();
        (string Label, string? Sort)[] columns = [("Title", "title"), ("Source", "source"), ("Unread", "unread"), ("Last read", "recent"), ("Progress", "progress"), ("Next chapter", null)];
        for (var i = 0; i < columns.Length; i++)
        {
            var (label, sort) = columns[i];
            FrameworkElement cell;
            if (sort is null)
            {
                cell = new TextBlock { Text = label, Style = LibraryActions.Style("CaptionTextBlockStyle"), Foreground = LibraryActions.Brush("TextFillColorSecondaryBrush"), VerticalAlignment = VerticalAlignment.Center };
            }
            else
            {
                var active = Services.Settings.LibrarySort == sort;
                var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
                panel.Children.Add(new TextBlock { Text = label, Style = LibraryActions.Style("CaptionTextBlockStyle"), Foreground = LibraryActions.Brush(active ? "TextFillColorPrimaryBrush" : "TextFillColorSecondaryBrush") });
                if (active)
                {
                    panel.Children.Add(new FontIcon { Glyph = sort == "title" ? "\uE70E" : "\uE70D", FontFamily = LibraryActions.Symbols, FontSize = 10 });
                }
                var button = new Button
                {
                    Content = panel,
                    Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
                    BorderThickness = new Thickness(0),
                    Padding = new Thickness(4, 2, 4, 2),
                    Margin = new Thickness(-4, 0, 0, 0),
                };
                AutomationProperties.SetName(button, active ? $"{label}, sorted" : $"Sort by {label}");
                button.Click += (_, _) => SetSort(sort);
                cell = button;
            }
            Grid.SetColumn(cell, i + 1);
            listHeader.Children.Add(cell);
        }
    }

    private void SetView(string view)
    {
        Services.Settings.LibraryView = view;
        Services.Settings.Save();
        ApplyView();
        if (source.Count > 0)
        {
            StaggerRealized(view == "list" ? 16 : 28);
        }
    }

    private void ApplyView()
    {
        var listMode = Services.Settings.LibraryView == "list";
        grid.Visibility = listMode ? Visibility.Collapsed : Visibility.Visible;
        listPane.Visibility = listMode ? Visibility.Visible : Visibility.Collapsed;
        viewButton.Label = listMode ? "Grid view" : "List view";
        viewButton.Icon = LibraryActions.Glyph(listMode ? "\uF0E2" : "\uE8FD");
        AutomationProperties.SetName(viewButton, listMode ? "Show as grid" : "Show as list");
        ToolTipService.SetToolTip(viewButton, listMode ? "Show as grid" : "Show as list");
        if (selecting)
        {
            (listMode ? (ListViewBase)grid : list).SelectedItems.Clear();
            ApplySelectionMode();
        }
    }

    private HashSet<string> FilterTokens => Services.Settings.LibraryFilter.Split(';', StringSplitOptions.RemoveEmptyEntries).ToHashSet();

    private void SetFilterToken(string prefix, string? token)
    {
        var tokens = FilterTokens;
        tokens.RemoveWhere(t => t == prefix || t.StartsWith(prefix + "=", StringComparison.Ordinal));
        if (token is not null)
        {
            tokens.Add(token);
        }
        Services.Settings.LibraryFilter = string.Join(';', tokens.Order(StringComparer.Ordinal));
        Services.Settings.Save();
        UpdateFilterLabel();
        _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: true));
    }

    private static long? TokenValue(HashSet<string> tokens, string prefix) =>
        tokens.FirstOrDefault(t => t.StartsWith(prefix + "=", StringComparison.Ordinal)) is { } t && long.TryParse(t[(prefix.Length + 1)..], out var value) ? value : null;

    private void UpdateFilterLabel()
    {
        var active = FilterTokens.Count;
        filterButton.Label = active == 0 ? "Filter" : $"Filter ({active})";
        AutomationProperties.SetName(filterButton, active == 0 ? "Filter" : $"Filter, {active} active");
        ToolTipService.SetToolTip(filterButton, "Filter titles");
    }

    private void BuildFilterMenu()
    {
        filterMenu.Items.Clear();
        var tokens = FilterTokens;
        var unread = new ToggleMenuFlyoutItem { Text = "Unread only", IsChecked = tokens.Contains("unread") };
        unread.Click += (_, _) => SetFilterToken("unread", unread.IsChecked ? "unread" : null);
        var readable = new ToggleMenuFlyoutItem { Text = "Readable on this PC", IsChecked = tokens.Contains("readable") };
        readable.Click += (_, _) => SetFilterToken("readable", readable.IsChecked ? "readable" : null);
        var downloadedOnly = new ToggleMenuFlyoutItem { Text = "Downloaded", IsChecked = tokens.Contains("downloaded") };
        downloadedOnly.Click += (_, _) => SetFilterToken("downloaded", downloadedOnly.IsChecked ? "downloaded" : null);
        var merged = new ToggleMenuFlyoutItem { Text = "Merged", IsChecked = tokens.Contains("merged") };
        merged.Click += (_, _) => SetFilterToken("merged", merged.IsChecked ? "merged" : null);
        filterMenu.Items.Add(unread);
        filterMenu.Items.Add(readable);
        filterMenu.Items.Add(downloadedOnly);
        filterMenu.Items.Add(merged);
        filterMenu.Items.Add(new MenuFlyoutSeparator());

        var status = TokenValue(tokens, "status");
        var statusMenu = new MenuFlyoutSubItem { Text = status is { } st ? $"Status: {Statuses.FirstOrDefault(x => (long)x.Status == st).Label}" : "Status" };
        var anyStatus = new ToggleMenuFlyoutItem { Text = "Any status", IsChecked = status is null };
        anyStatus.Click += (_, _) => SetFilterToken("status", null);
        statusMenu.Items.Add(anyStatus);
        foreach (var (value, label) in Statuses)
        {
            var item = new ToggleMenuFlyoutItem { Text = label, IsChecked = status == (long)value };
            item.Click += (_, _) => SetFilterToken("status", $"status={(long)value}");
            statusMenu.Items.Add(item);
        }
        filterMenu.Items.Add(statusMenu);

        var sourceId = TokenValue(tokens, "source");
        var names = all.Select(e => e.Source).Distinct()
            .Select(id => (Id: id, Name: Services.Engine?.Sources.DisplayName(id) ?? id.ToString()))
            .OrderBy(x => x.Name, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
        var sourceMenu = new MenuFlyoutSubItem { Text = sourceId is { } sid && names.FirstOrDefault(n => n.Id == sid).Name is { } chosen ? $"Source: {chosen}" : "Source" };
        var anySource = new ToggleMenuFlyoutItem { Text = "Any source", IsChecked = sourceId is null };
        anySource.Click += (_, _) => SetFilterToken("source", null);
        sourceMenu.Items.Add(anySource);
        foreach (var (id, name) in names)
        {
            var item = new ToggleMenuFlyoutItem { Text = name, IsChecked = sourceId == id };
            item.Click += (_, _) => SetFilterToken("source", $"source={id}");
            sourceMenu.Items.Add(item);
        }
        filterMenu.Items.Add(sourceMenu);

        if (tokens.Count > 0)
        {
            filterMenu.Items.Add(new MenuFlyoutSeparator());
            var clear = new MenuFlyoutItem { Text = "Clear filters", Icon = LibraryActions.Glyph("\uE894") };
            clear.Click += (_, _) =>
            {
                Services.Settings.LibraryFilter = string.Empty;
                Services.Settings.Save();
                UpdateFilterLabel();
                _ = Log.Guard("next-library", () => ApplyFilterAsync(animate: true));
            };
            filterMenu.Items.Add(clear);
        }
    }

    private Tsuzuki.Presentation.LibraryQuery CurrentQuery()
    {
        var tokens = FilterTokens;
        return new Tsuzuki.Presentation.LibraryQuery(
            Services.Settings.LibraryCategory,
            filter,
            Services.Settings.LibrarySort,
            tokens.Contains("unread"),
            tokens.Contains("readable"),
            TokenValue(tokens, "status"),
            TokenValue(tokens, "source"),
            tokens.Contains("downloaded"),
            tokens.Contains("merged"));
    }

    private Task ApplyFilterAsync(bool animate)
    {
        if (model is null)
        {
            return Task.CompletedTask;
        }
        animateNext |= animate;
        return model.SetQueryAsync(CurrentQuery());
    }

    private void OnModelChanged(Tsuzuki.Presentation.LibraryViewChange change)
    {
        if (!DispatcherQueue.HasThreadAccess)
        {
            Ui.Enqueue(DispatcherQueue, () => OnModelChanged(change), "next-library-model");
            return;
        }
        if (model is not { } current)
        {
            return;
        }
        all = current.All;
        categories = current.Categories;
        loaded = current.Loaded;
        if (change.Kind is Tsuzuki.Presentation.LibraryViewChangeKind.Loaded or Tsuzuki.Presentation.LibraryViewChangeKind.Updated)
        {
            BuildCategories();
            UpdateSummary();
        }
        if (loaded)
        {
            loadingRing.IsActive = false;
            loadingRing.Visibility = Visibility.Collapsed;
        }
        var animate = loaded && (animateNext || firstLoad);
        if (loaded)
        {
            animateNext = false;
            firstLoad = false;
        }
        var items = current.Visible.Select(ToTile).ToList();
        var text = current.Query.Search;
        var tokens = FilterTokens;
        var nextShape = $"{current.Query.Sort}|{current.Query.Category?.ToString() ?? "-"}|{Services.Settings.LibraryFilter}";
        if (shape.Length > 0 && nextShape == shape)
        {
            SyncIncremental(source, items);
        }
        else
        {
            shape = nextShape;
            source.ReplaceAll(items);
        }
        appliedFilter = text;
        var empty = all.Count == 0;
        emptyState.Visibility = items.Count == 0 && loaded ? Visibility.Visible : Visibility.Collapsed;
        listHeader.Visibility = items.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
        emptyTitle.Text = empty ? "Your library is empty" : "Nothing here";
        emptyBody.Text = empty
            ? "Pair your phone to bring your library over, or find something new on MangaDex."
            : text.Length > 0 ? $"No titles match “{text}”." : tokens.Count > 0 ? "No titles match these filters." : "No titles in this category yet.";
        emptyActions.Visibility = empty ? Visibility.Visible : Visibility.Collapsed;
        UpdateSelectionBar();
        if (animate && items.Count > 0)
        {
            StaggerRealized(Services.Settings.LibraryView == "list" ? 16 : 28);
        }
    }

    private static TileData ToTile(LibraryEntry e)
    {
        var (read, total) = LibraryActions.Progress(e);
        var caption = total == 0 ? "No chapters yet"
            : read == 0 ? $"{total:N0} chapters"
            : e.UnreadCount == 0 ? $"Caught up · {total:N0} ch."
            : $"Ch. {read:N0} of {total:N0}";
        return new TileData(e.MangaId, e.Title, e.ThumbnailUrl, caption, e.UnreadCount, !e.Readable, e.IsMerged, Tag: e, InLibrary: true, Source: e.Source);
    }

    private static bool SameTile(TileData a, TileData b) =>
        a.MangaId == b.MangaId && a.Title == b.Title && a.Caption == b.Caption && a.Badge == b.Badge
        && a.ThumbnailUrl == b.ThumbnailUrl && a.PhoneOnly == b.PhoneOnly && a.Merged == b.Merged && Equals(a.Tag, b.Tag);

    private static void SyncIncremental(BulkObservableCollection<TileData> current, IReadOnlyList<TileData> targets)
    {
        var targetMap = new Dictionary<long, (int Index, TileData Tile)>(targets.Count);
        for (var i = 0; i < targets.Count; i++)
        {
            targetMap[targets[i].MangaId] = (i, targets[i]);
        }
        var kept = 0;
        for (var i = 0; i < current.Count; i++)
        {
            if (targetMap.ContainsKey(current[i].MangaId))
            {
                kept++;
            }
        }
        var churn = current.Count - kept + targets.Count - kept;
        if (churn * 4 > Math.Max(current.Count, targets.Count))
        {
            current.ReplaceAll(targets);
            return;
        }
        for (var i = current.Count - 1; i >= 0; i--)
        {
            if (!targetMap.ContainsKey(current[i].MangaId))
            {
                current.RemoveAt(i);
            }
        }
        for (var i = 0; i < targets.Count; i++)
        {
            var target = targets[i];
            if (i < current.Count && current[i].MangaId == target.MangaId)
            {
                if (!SameTile(current[i], target))
                {
                    current[i] = target;
                }
                continue;
            }
            var existingIndex = -1;
            for (var j = i + 1; j < current.Count; j++)
            {
                if (current[j].MangaId == target.MangaId)
                {
                    existingIndex = j;
                    break;
                }
            }
            if (existingIndex >= 0)
            {
                current.Move(existingIndex, i);
                if (!SameTile(current[i], target))
                {
                    current[i] = target;
                }
            }
            else
            {
                current.Insert(i, target);
            }
        }
    }

    private void StaggerRealized(int take)
    {
        Ui.Enqueue(DispatcherQueue, DispatcherQueuePriority.Low, () =>
        {
            var listMode = Services.Settings.LibraryView == "list";
            ListViewBase view = listMode ? list : grid;
            var visible = realized
                .Where(c => listMode ? rows.ContainsKey(c) : tiles.ContainsKey(c))
                .Select(c => (Element: listMode ? (UIElement)rows[c] : tiles[c], Index: view.IndexFromContainer(c)))
                .Where(x => x.Index >= 0)
                .OrderBy(x => x.Index)
                .Take(take)
                .Select(x => x.Element)
                .ToList();
            _ = Motion.Stagger(visible, TimeSpan.FromMilliseconds(18), rise: 12);
        }, "next-library-stagger");
    }

    private void OnGridContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container || container.ContentTemplateRoot is not Grid host)
        {
            return;
        }
        if (!tiles.TryGetValue(container, out var tile))
        {
            tile = new CoverTile();
            tile.Clicked += (_, t) => OpenDetails(t.Data, t.CoverElement);
            host.Children.Add(tile);
            tiles[container] = tile;
        }
        args.Handled = true;
        if (args.InRecycleQueue)
        {
            realized.Remove(container);
            return;
        }
        tile.Data = (TileData)args.Item;
        tile.SelectionActive = selecting;
        container.IsTabStop = selecting;
        realized.Add(container);
    }

    private void OnListContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container || container.ContentTemplateRoot is not Grid host)
        {
            return;
        }
        if (!rows.TryGetValue(container, out var row))
        {
            row = new LibraryListRow();
            row.Clicked += (_, r) => OpenDetails(r.Data, r.CoverElement);
            host.Children.Add(row);
            rows[container] = row;
        }
        args.Handled = true;
        if (args.InRecycleQueue)
        {
            realized.Remove(container);
            return;
        }
        row.Data = (TileData)args.Item;
        row.SelectionActive = selecting;
        container.IsTabStop = selecting;
        realized.Add(container);
    }

    private static void OpenDetails(TileData? data, FrameworkElement cover)
    {
        if (data is not null)
        {
            _ = Log.Guard("next-library-open", () => AppNav.OpenDetailsAsync(data.MangaId, cover));
        }
    }

    private void SetSelecting(bool on)
    {
        if (selecting == on)
        {
            selectButton.IsChecked = on;
            return;
        }
        selecting = on;
        selectButton.IsChecked = on;
        ApplySelectionMode();
        UpdateSelectionBar();
        if (on)
        {
            selectionBar.Visibility = Visibility.Visible;
            if (!Motion.Reduced)
            {
                _ = Motion.FadeIn(selectionBar, Motion.Short4, 12);
            }
        }
        else
        {
            selectionBar.Visibility = Visibility.Collapsed;
        }
    }

    private void ApplySelectionMode()
    {
        var mode = selecting ? ListViewSelectionMode.Multiple : ListViewSelectionMode.None;
        var listMode = Services.Settings.LibraryView == "list";
        grid.SelectionMode = selecting && !listMode ? mode : ListViewSelectionMode.None;
        list.SelectionMode = selecting && listMode ? mode : ListViewSelectionMode.None;
        foreach (var (container, tile) in tiles)
        {
            tile.SelectionActive = selecting;
            ((Control)container).IsTabStop = selecting;
        }
        foreach (var (container, row) in rows)
        {
            row.SelectionActive = selecting;
            ((Control)container).IsTabStop = selecting;
        }
    }

    private List<TileData> Selected =>
        (Services.Settings.LibraryView == "list" ? list.SelectedItems : grid.SelectedItems).OfType<TileData>().ToList();

    private void UpdateSelectionBar()
    {
        if (!selecting)
        {
            return;
        }
        var count = Selected.Count;
        selectionCount.Text = count == 0 ? "None selected" : $"{count:N0} selected";
        foreach (var command in selectionCommands)
        {
            command.IsEnabled = count > 0;
        }
    }

    private void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Escape && selecting)
        {
            e.Handled = true;
            SetSelecting(false);
        }
    }

    private async Task SetSelectedReadAsync(bool read)
    {
        if (Services.Engine is not { } engine || Selected is not { Count: > 0 } selected)
        {
            return;
        }
        List<long> ids = [];
        foreach (var data in selected)
        {
            ids.AddRange((await engine.Library.GetChaptersAsync(data.MangaId)).Where(c => c.Read != read).Select(c => c.ChapterId));
        }
        if (ids.Count > 0)
        {
            await engine.Library.SetChaptersReadAsync(ids, read);
        }
        Toasts.Show(read ? $"Marked {selected.Count:N0} {Titles(selected.Count)} as read" : $"Marked {selected.Count:N0} {Titles(selected.Count)} as unread", null, read ? Icons.CheckCheck : Icons.Check, seconds: 3);
        SetSelecting(false);
    }

    private async Task ShowBulkCategoriesAsync(FrameworkElement anchor)
    {
        if (Services.Engine is not { } engine || Selected is not { Count: > 0 } selected)
        {
            return;
        }
        var current = await engine.Library.GetCategoriesAsync();
        var members = selected.ToDictionary(d => d.MangaId, d => (d.Tag as LibraryEntry)?.CategoryUids.ToHashSet() ?? []);
        var menu = new MenuFlyout { Placement = FlyoutPlacementMode.Top };
        async Task ApplyAsync(long uid, bool add)
        {
            foreach (var (mangaId, set) in members)
            {
                if (add ? set.Add(uid) : set.Remove(uid))
                {
                    await engine.Library.SetMangaCategoriesAsync(mangaId, set.ToList());
                }
            }
        }
        foreach (var category in current.OrderBy(c => c.Order))
        {
            var item = new ToggleMenuFlyoutItem { Text = category.Name, IsChecked = members.Values.All(s => s.Contains(category.Uid)) };
            item.Click += (_, _) => _ = Log.Guard("next-library-categories", () => ApplyAsync(category.Uid, item.IsChecked));
            menu.Items.Add(item);
        }
        if (current.Count > 0)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
        }
        var create = new MenuFlyoutItem { Text = "New category…", Icon = LibraryActions.Glyph("\uE710") };
        create.Click += (_, _) => _ = Log.Guard("next-library-categories", async () =>
        {
            var name = await Dialogs.PromptAsync("New category", "Name", "Create");
            if (!string.IsNullOrWhiteSpace(name))
            {
                var category = await engine.Library.CreateCategoryAsync(name.Trim());
                await ApplyAsync(category.Uid, true);
            }
        });
        menu.Items.Add(create);
        menu.ShowAt(anchor);
    }

    private async Task RemoveSelectedAsync()
    {
        if (Services.Engine is not { } engine || Selected is not { Count: > 0 } selected)
        {
            return;
        }
        var title = selected.Count == 1 ? selected[0].Title : $"{selected.Count:N0} titles";
        if (!await Dialogs.ConfirmDestructiveAsync($"Remove {title} from library?", "Your reading progress is kept. Your phone drops them from its library at the next sync.", "Remove", "Keep"))
        {
            return;
        }
        foreach (var data in selected)
        {
            await engine.Library.SetFavoriteAsync(data.MangaId, false);
        }
        Toasts.Show($"Removed {title} from your library", null, Icons.Close);
        SetSelecting(false);
    }

    private static string Titles(int count) => count == 1 ? "title" : "titles";

    private async Task RefreshFromSourcesAsync()
    {
        if (Services.Engine is not { } engine || refreshing)
        {
            return;
        }
        refreshing = true;
        refreshButton.IsEnabled = false;
        refreshProgress.IsIndeterminate = true;
        refreshBar.Message = string.Empty;
        refreshBar.IsOpen = true;
        var progress = new Progress<(int Done, int Total)>(p =>
        {
            if (p.Total > 0)
            {
                refreshProgress.IsIndeterminate = false;
                refreshProgress.Maximum = p.Total;
                refreshProgress.Value = p.Done;
                refreshBar.Message = $"{p.Done:N0} of {p.Total:N0}";
            }
        });
        try
        {
            var result = await engine.Refresher.RefreshLibraryAsync(progress);
            Toasts.Show(result.NewChapters > 0 ? $"{result.NewChapters:N0} new chapters" : "You're up to date",
                result.Failed > 0 ? $"{result.Failed:N0} titles could not be checked" : null, Icons.Sparkles);
        }
        finally
        {
            refreshing = false;
            refreshButton.IsEnabled = true;
            refreshBar.IsOpen = false;
        }
    }

    private void Relayout()
    {
        if (!EnsureTileMetrics())
        {
            Ui.Enqueue(DispatcherQueue, DispatcherQueuePriority.Low, () => EnsureTileMetrics());
        }
    }

    private bool EnsureTileMetrics()
    {
        var panel = grid.ItemsPanelRoot as ItemsWrapGrid ?? ItemsTree.FindDescendant<ItemsWrapGrid>(grid);
        if (panel is null)
        {
            return false;
        }
        var available = grid.ActualWidth - grid.Padding.Left - grid.Padding.Right;
        if (available <= 0 || double.IsNaN(available))
        {
            return false;
        }
        var columns = Math.Max(2, (int)Math.Floor((available + TileSpacing) / (Services.Settings.TileWidth + TileSpacing)));
        var width = Math.Floor((available - 1) / columns) - TileSpacing;
        if (Math.Abs(width - tileWidth) < 0.5 && !double.IsNaN(panel.ItemWidth))
        {
            return true;
        }
        tileWidth = width;
        panel.ItemWidth = width + TileSpacing;
        panel.ItemHeight = width * 1.5 + 62 + 24;
        return true;
    }

    private static AppBarButton Command(string glyph, string label)
    {
        var button = new AppBarButton { Label = label, Icon = LibraryActions.Glyph(glyph) };
        AutomationProperties.SetName(button, label);
        ToolTipService.SetToolTip(button, label);
        return button;
    }

    private static DataTemplate EmptyTemplate() => (DataTemplate)XamlReader.Load(
        """<DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Grid /></DataTemplate>""");

    private static Style ContainerStyle(Type type, Thickness margin)
    {
        var style = new Style(type);
        style.Setters.Add(new Setter(FrameworkElement.MarginProperty, margin));
        style.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(0)));
        style.Setters.Add(new Setter(Control.IsTabStopProperty, false));
        style.Setters.Add(new Setter(Control.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        style.Setters.Add(new Setter(Control.VerticalContentAlignmentProperty, VerticalAlignment.Top));
        return style;
    }
}
