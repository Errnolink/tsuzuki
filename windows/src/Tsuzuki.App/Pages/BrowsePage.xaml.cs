using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.App.Pages;

public sealed partial class BrowsePage : TzPage, ITzPage, ICoverHost
{
    private enum Mode
    {
        Popular,
        Latest,
        Search,
    }

    private sealed record BrowseItem(ISource Source, SManga Manga);

    private static readonly DataTemplate TileTemplate = (DataTemplate)Application.Current.Resources["TzTileTemplate"];
    private static readonly Dictionary<long, IReadOnlyDictionary<string, IReadOnlyList<string>>> SessionFilters = [];
    private static readonly Dictionary<long, IReadOnlyList<FilterSpec>> SessionSpecs = [];

    private readonly ObservableCollection<TileData> items = [];
    private readonly Dictionary<string, long> resolved = [];
    private readonly List<(long Id, SManga Manga)> recentlyAdded = [];
    private Microsoft.UI.Dispatching.DispatcherQueueTimer? addedToast;
    private readonly Dictionary<DependencyObject, MangaTile> tileByContainer = [];
    private readonly HashSet<MangaTile> realized = [];
    private ScrollViewer? scroller;
    private Mode mode = Mode.Popular;
    private string query = string.Empty;
    private bool refreshRequested;
    private Tsuzuki.Presentation.BrowseViewModel? paging;
    private double tileWidth;
    private HashSet<string> inLibrary = [];
    private readonly HashSet<string> adding = [];
    private bool marksDirty = true;
    private ISource? source;
    private readonly Flyout filterFlyout = CreateFilterFlyout();
    private Dictionary<string, IReadOnlyList<string>> draft = [];
    private int filterLoad;

    public BrowsePage()
    {
        InitializeComponent();
        TileGrid.Padding = new Thickness(36, 28, 36, 48);
        TileGrid.ItemTemplate = TileTemplate;
        TileGrid.ItemsPanel = (ItemsPanelTemplate)XamlReader.Load(
            """<ItemsPanelTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><ItemsWrapGrid Orientation="Horizontal"/></ItemsPanelTemplate>""");
        TileGrid.ItemContainerStyle = MakeContainerStyle();
        TileGrid.ItemContainerTransitions = ListMotion.ItemTransitions();
        TileGrid.ItemsSource = items;
        TileGrid.ContainerContentChanging += OnContainerChanging;
        TileGrid.SizeChanged += (_, _) => Relayout();
        TileGrid.Loaded += (_, _) =>
        {
            Relayout();
            if (scroller is null)
            {
                scroller = ItemsTree.FindDescendant<ScrollViewer>(TileGrid);
                if (scroller is not null)
                {
                    scroller.ViewChanged += (_, _) => MaybeLoadMore();
                }
            }
        };
        AppHost.TilesResized += (_, _) => { tileWidth = 0; Relayout(); };
        RetryButton.Click += (_, _) => ForceReload();
        FiltersButton.Flyout = filterFlyout;
        filterFlyout.Opening += (_, _) => _ = Log.Guard("browse filters", OpenFiltersAsync);
        SourceSearch.QuerySubmitted += (_, args) => SubmitSearch(args.QueryText.Trim());
        SourceSearch.TextChanged += (_, args) =>
        {
            if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput && SourceSearch.Text.Length == 0 && query.Length > 0)
            {
                SubmitSearch(string.Empty);
            }
        };
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += (_, _) => marksDirty = true;
        }
        AppHost.EngineReady += OnEngineReady;
        source = PickSource();
        BuildSources();
        BuildModes();
    }

    private void OnEngineReady(object? sender, EventArgs e)
    {
        Ui.Enqueue(DispatcherQueue, () =>
        {
            if (source is not null || PickSource() is not { } picked)
            {
                return;
            }
            source = picked;
            Services.Engine!.Library.Changed += (_, _) => marksDirty = true;
            Services.Engine.Extensions.Changed += (_, _) => Ui.Enqueue(DispatcherQueue, OnSourcesChanged, "browse-sources");
            BuildSources();
            BuildModes();
            if (items.Count == 0 && paging?.Loading != true)
            {
                Reload();
            }
        }, "browse");
    }

    private void OnSourcesChanged()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        if (source is not null && engine.Sources.Get(source.Id) is null && PickSource() is { } fallback)
        {
            ChangeSource(fallback);
            BuildModes();
            Reload();
            return;
        }
        BuildSources();
    }

    private static ISource? PickSource()
    {
        if (Services.Engine is not { } engine)
        {
            return null;
        }
        return Services.Settings.BrowseSource is { } remembered && engine.Sources.Get(remembered) is { } chosen
            ? chosen
            : engine.Sources.All.FirstOrDefault();
    }

    public static string SourceLabel(ISource candidate, IReadOnlyList<ISource> all) =>
        all.Count(s => s.Name == candidate.Name) > 1 ? $"{candidate.Name} ({candidate.Lang})" : candidate.Name;

    public void Search(string text, long sourceId)
    {
        if (text.Length == 0)
        {
            return;
        }
        if (Services.Engine?.Sources.Get(sourceId) is { } target && target != source)
        {
            ChangeSource(target);
        }
        SourceSearch.Text = text;
        SubmitSearch(text);
    }

    public void ShowSource(long sourceId)
    {
        if (Services.Engine?.Sources.Get(sourceId) is { } target)
        {
            SelectSource(target);
        }
    }

    private void SubmitSearch(string text)
    {
        query = text;
        if (HasSearch)
        {
            mode = Mode.Search;
        }
        else if (mode == Mode.Search)
        {
            mode = Mode.Popular;
        }
        BuildModes();
        Reload();
    }

    private IReadOnlyDictionary<string, IReadOnlyList<string>> Applied =>
        source is { } current && SessionFilters.TryGetValue(current.Id, out var values) ? values : SearchQuery.Empty.Values;

    private int ActiveFilters =>
        source is { } current && SessionSpecs.TryGetValue(current.Id, out var specs) ? new SearchQuery(query, Applied).ActiveCount(specs) : 0;

    private bool HasSearch => query.Length > 0 || ActiveFilters > 0;

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container)
        {
            return;
        }
        if (!tileByContainer.TryGetValue(container, out var tile))
        {
            tile = ItemsTree.FindDescendant<MangaTile>(container);
            if (tile is null)
            {
                return;
            }
            tileByContainer[container] = tile;
            tile.Click += OnTileClick;
        }
        if (args.InRecycleQueue)
        {
            realized.Remove(tile);
            return;
        }
        realized.Add(tile);
    }

    private void OnTileClick(object sender, RoutedEventArgs e)
    {
        var tile = (MangaTile)sender;
        if (!tile.IsAddOrigin(e))
        {
            _ = Log.Guard("browse open", () => OpenAsync(tile));
        }
    }

    private static Style MakeContainerStyle()
    {
        var style = new Style(typeof(GridViewItem));
        style.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(0, 0, 20, 28)));
        style.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(0)));
        style.Setters.Add(new Setter(Control.IsTabStopProperty, false));
        style.Setters.Add(new Setter(GridViewItem.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        style.Setters.Add(new Setter(GridViewItem.VerticalContentAlignmentProperty, VerticalAlignment.Top));
        return style;
    }

    private void BuildSources()
    {
        var all = Services.Engine?.Sources.All ?? [];
        var menu = new MenuFlyout { Placement = FlyoutPlacementMode.BottomEdgeAlignedLeft };
        foreach (var candidate in all)
        {
            var item = new RadioMenuFlyoutItem { Text = SourceLabel(candidate, all), GroupName = "BrowseSource", IsChecked = candidate == source };
            item.Click += (_, _) => SelectSource(candidate);
            menu.Items.Add(item);
        }
        SourcePicker.Flyout = menu;
        SourcePicker.IsEnabled = all.Count > 0;
        var label = source is null ? "No sources" : SourceLabel(source, all);
        SourcePicker.Content = label;
        ToolTipService.SetToolTip(SourcePicker, "Choose a source to browse");
        A11y.Name(SourcePicker, $"Source: {label}");
        SourceSearch.PlaceholderText = source is null ? "Search" : $"Search {label}";
        A11y.Name(SourceSearch, SourceSearch.PlaceholderText);
        SourceSearch.IsEnabled = source is not null;
        FiltersButton.IsEnabled = source is not null;
        UpdateFilterBadge();
    }

    private void SelectSource(ISource picked)
    {
        if (picked == source)
        {
            BuildSources();
            return;
        }
        ChangeSource(picked);
        BuildModes();
        Reload();
    }

    private void ChangeSource(ISource picked)
    {
        source = picked;
        Services.Settings.BrowseSource = picked.Id;
        Services.Settings.Save();
        filterLoad++;
        filterFlyout.Hide();
        if (mode == Mode.Latest && !picked.SupportsLatest)
        {
            mode = Mode.Popular;
        }
        if (mode == Mode.Search && !HasSearch)
        {
            mode = Mode.Popular;
        }
        BuildSources();
    }

    private void BuildModes()
    {
        Modes.Children.Clear();
        AddMode("Popular", Mode.Popular);
        if (source?.SupportsLatest == true)
        {
            AddMode("Latest updates", Mode.Latest);
        }
        if (HasSearch)
        {
            AddMode(query.Length > 0 ? $"“{query}”" : "Filtered", Mode.Search);
        }
        var refresh = new Button
        {
            Style = (Style)Application.Current.Resources["TzIconButton"],
            Content = new FontIcon { FontFamily = (Microsoft.UI.Xaml.Media.FontFamily)Application.Current.Resources["TzIconFont"], Glyph = Icons.Refresh, FontSize = 14 },
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(refresh, "Reload from the source (F5)");
        A11y.Name(refresh, "Reload from the source");
        refresh.Click += (_, _) => ForceReload();
        var accelerator = new Microsoft.UI.Xaml.Input.KeyboardAccelerator { Key = Windows.System.VirtualKey.F5 };
        accelerator.Invoked += (_, e) =>
        {
            e.Handled = true;
            ForceReload();
        };
        refresh.KeyboardAccelerators.Add(accelerator);
        Modes.Children.Add(refresh);
        UpdateSummary();
    }

    private void UpdateSummary()
    {
        if (source is not { } current)
        {
            Summary.Text = "No sources available";
            return;
        }
        var filters = ActiveFilters;
        var suffix = filters switch
        {
            0 => string.Empty,
            1 => " · 1 filter",
            _ => $" · {filters} filters",
        };
        Summary.Text = mode switch
        {
            Mode.Latest => $"Latest updates on {current.Name}",
            Mode.Search when query.Length > 0 => $"Results for “{query}” on {current.Name}{suffix}",
            Mode.Search => $"Filtered titles on {current.Name}{suffix}",
            _ => $"Popular on {current.Name}",
        };
    }

    private static ToggleButton Chip(string label, bool isChecked)
    {
        var chip = new ToggleButton { Style = (Style)Application.Current.Resources["TzChip"], Content = label, IsChecked = isChecked };
        A11y.NameToggle(chip, label);
        return chip;
    }

    private void AddMode(string label, Mode target)
    {
        var chip = Chip(label, mode == target);
        chip.Click += (_, _) =>
        {
            if (mode == target)
            {
                chip.IsChecked = true;
                ForceReload();
                return;
            }
            mode = target;
            foreach (var other in Modes.Children.OfType<ToggleButton>())
            {
                other.IsChecked = other == chip;
                A11y.NameToggle(other, (string)other.Content);
            }
            UpdateSummary();
            Reload();
        };
        Modes.Children.Add(chip);
    }

    public void OnShown()
    {
        ApplyTileTransitions();
        if (items.Count == 0 && paging?.Loading != true)
        {
            Reload();
        }
        if (marksDirty)
        {
            _ = Log.Guard("browse marks", RefreshLibraryMarksAsync);
        }
    }

    private async Task RefreshLibraryMarksAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        marksDirty = false;
        inLibrary = [.. await engine.Library.GetFavouriteKeysAsync()];
        ApplyLibraryMarks();
    }

    private static string Key(BrowseItem item) => item.Source.Id + "|" + item.Manga.Url;

    private void ApplyLibraryMarks()
    {
        for (var i = 0; i < items.Count; i++)
        {
            if (items[i].Tag is BrowseItem item)
            {
                var marked = inLibrary.Contains(Key(item));
                if (items[i].InLibrary != marked)
                {
                    ReplaceQuietly(item, d => d with { InLibrary = marked, Caption = marked ? "In library" : StatusText(item.Manga.Status) });
                }
            }
        }
    }

    private void Reload() => _ = Log.Guard("browse", ReloadAsync);

    private void ForceReload()
    {
        refreshRequested = true;
        Reload();
    }

    private async Task ReloadAsync()
    {
        if (source is not { } current || Services.Engine is not { } engine)
        {
            return;
        }
        paging ??= new Tsuzuki.Presentation.BrowseViewModel(engine.Listings);
        paging.Reset(new Tsuzuki.Presentation.BrowseRequest(current, mode switch
        {
            Mode.Latest => ListingKind.Latest,
            Mode.Search => ListingKind.Search,
            _ => ListingKind.Popular,
        }, mode == Mode.Search ? new SearchQuery(query, Applied) : null), refreshRequested);
        refreshRequested = false;
        items.Clear();
        realized.Clear();
        LoadingRing.IsActive = false;
        scroller?.ChangeView(null, 0, null, true);
        await LoadMoreAsync(animate: true);
    }

    private void MaybeLoadMore()
    {
        if (scroller is { } s && paging is { Loading: false, HasNext: true } && s.VerticalOffset > s.ExtentHeight - s.ViewportHeight * 1.8)
        {
            _ = Log.Guard("browse", () => LoadMoreAsync(animate: false));
        }
    }

    private async Task LoadMoreAsync(bool animate)
    {
        if (source is not { } current || paging is not { Loading: false, HasNext: true } model)
        {
            return;
        }
        LoadingRing.IsActive = true;
        Message.Visibility = Visibility.Collapsed;
        var result = await model.LoadMoreAsync();
        if (result.Outcome is Tsuzuki.Presentation.BrowsePageOutcome.Stale or Tsuzuki.Presentation.BrowsePageOutcome.Busy)
        {
            return;
        }
        LoadingRing.IsActive = false;
        if (result.Outcome == Tsuzuki.Presentation.BrowsePageOutcome.Failed)
        {
            if (items.Count == 0)
            {
                if (result.Error is SourceBlockedException)
                {
                    ShowMessage($"{current.Name} is blocking this PC right now", "The site is asking for a browser check. Try again later.", retry: true);
                }
                else
                {
                    ShowMessage($"Couldn't reach {current.Name}", "Check your connection, then try again.", retry: true);
                }
            }
            else if (result.Error is not SourceBlockedException)
            {
                Log.Error("browse more", result.Error!);
                Toasts.Show($"Couldn't load more from {current.Name}", "Check your connection.", Design.Icons.Alert, "Try again", lead =>
                {
                    model.AllowRetry();
                    _ = Log.Guard("browse", () => LoadMoreAsync(animate: false));
                });
            }
            return;
        }
        foreach (var manga in result.Added)
        {
            var item = new BrowseItem(current, manga);
            var marked = inLibrary.Contains(Key(item));
            items.Add(new TileData(0, manga.Title, manga.ThumbnailUrl, marked ? "In library" : StatusText(manga.Status), 0, false, false, item, marked, AddToLibrary, current.Id));
        }
        if (items.Count == 0)
        {
            ShowMessage(mode == Mode.Search ? "No results" : "Nothing here", (mode, query.Length > 0) switch
            {
                (Mode.Search, true) => $"{current.Name} has nothing matching “{query}”.",
                (Mode.Search, false) => $"{current.Name} has nothing matching these filters.",
                _ => $"{current.Name} returned no titles.",
            }, retry: false);
        }
        if (animate && items.Count > 0)
        {
            StaggerRealized(24);
        }
        if (model.HasNext)
        {
            Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, MaybeLoadMore, "browse-fill");
        }
    }

    private void StaggerRealized(int take)
    {
        Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
        {
            var visible = realized
                .Select(t => (Tile: t, Container: ItemsTree.FindAncestor<GridViewItem>(t)))
                .Where(x => x.Container is not null)
                .Select(x => (Tile: x.Tile, Index: TileGrid.IndexFromContainer(x.Container!)))
                .Where(x => x.Index >= 0)
                .OrderBy(x => x.Index)
                .Take(take)
                .Select(x => (UIElement)x.Tile)
                .ToList();
            _ = Motion.Stagger(visible, TimeSpan.FromMilliseconds(18));
        }, "browse");
    }

    private void ShowMessage(string title, string body, bool retry)
    {
        MessageTitle.Text = title;
        MessageBody.Text = body;
        RetryButton.Visibility = retry ? Visibility.Visible : Visibility.Collapsed;
        Message.Visibility = Visibility.Visible;
    }

    private static string StatusText(MangaStatus status) => status switch
    {
        MangaStatus.Ongoing => "Ongoing",
        MangaStatus.Completed => "Completed",
        MangaStatus.OnHiatus => "On hiatus",
        MangaStatus.Cancelled => "Cancelled",
        _ => string.Empty,
    };

    private async Task OpenAsync(MangaTile tile)
    {
        if (Services.Engine is not { } engine || tile.Data is not { Tag: BrowseItem item } data)
        {
            return;
        }
        var id = await engine.Library.UpsertFromSourceAsync(item.Source.Id, item.Manga);
        resolved[Key(item)] = id;
        await AppNav.OpenDetailsAsync(id, tile.CoverElement);
    }

    private void AddToLibrary(MangaTile tile) => _ = Log.Guard("browse add", () => AddToLibraryAsync(tile));

    private async Task AddToLibraryAsync(MangaTile tile)
    {
        if (Services.Engine is not { } engine || tile.Data is not { Tag: BrowseItem item } data)
        {
            return;
        }
        var key = Key(item);
        if (inLibrary.Contains(key) || !adding.Add(key))
        {
            return;
        }
        var manga = item.Manga;
        long id;
        try
        {
            id = await engine.Refresher.AddToLibraryAsync(item.Source.Id, manga);
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            OfflineToast(manga);
            return;
        }
        finally
        {
            adding.Remove(key);
        }
        inLibrary.Add(key);
        resolved[key] = id;
        ReplaceQuietly(item, d => d with { MangaId = id, InLibrary = true, Caption = "In library" });
        AnnounceAdded(id, manga);
        _ = RefreshChaptersInBackgroundAsync(id, manga);
    }

    private void AnnounceAdded(long id, SManga manga)
    {
        recentlyAdded.Add((id, manga));
        if (addedToast is null)
        {
            addedToast = DispatcherQueue.CreateTimer();
            addedToast.Interval = TimeSpan.FromSeconds(6);
            addedToast.IsRepeating = false;
            addedToast.Tick += (_, _) => recentlyAdded.Clear();
        }
        addedToast.Stop();
        addedToast.Start();
        var added = recentlyAdded;
        if (added.Count == 1)
        {
            Toasts.Show("Added to library", manga.Title, Icons.Check, "View", lead => _ = AppNav.OpenDetailsAsync(id, lead), coverUrl: manga.ThumbnailUrl, key: "browse-added");
            return;
        }
        var names = string.Join(", ", added.TakeLast(2).Select(a => a.Manga.Title).Reverse());
        var more = added.Count > 2 ? $" and {added.Count - 2} more" : string.Empty;
        Toasts.Show($"Added {added.Count} titles to library", names + more, Icons.Check, "Open library", lead => _ = App.MainWindow.NavigateRootAsync("library"), coverUrl: manga.ThumbnailUrl, key: "browse-added");
    }

    private async Task RefreshChaptersInBackgroundAsync(long mangaId, SManga manga)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        try
        {
            await engine.Refresher.RefreshQueuedAsync(mangaId);
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            OfflineToast(manga);
        }
    }

    private static void OfflineToast(SManga manga) =>
        Toasts.Show("Added to library (offline — chapters will sync when online)", manga.Title, Icons.Check, coverUrl: manga.ThumbnailUrl);

    private static Style Res(string key) => (Style)Application.Current.Resources[key];

    private static Flyout CreateFilterFlyout()
    {
        var style = new Style(typeof(FlyoutPresenter));
        style.Setters.Add(new Setter(FrameworkElement.MaxWidthProperty, 560d));
        style.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(0)));
        style.Setters.Add(new Setter(ScrollViewer.VerticalScrollModeProperty, ScrollMode.Disabled));
        style.Setters.Add(new Setter(ScrollViewer.VerticalScrollBarVisibilityProperty, ScrollBarVisibility.Disabled));
        return new Flyout { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight, FlyoutPresenterStyle = style };
    }

    private void UpdateFilterBadge()
    {
        var count = ActiveFilters;
        FilterBadge.Value = count;
        FilterBadge.Visibility = count > 0 ? Visibility.Visible : Visibility.Collapsed;
        A11y.Name(FiltersButton, count switch
        {
            0 => "Filters",
            1 => "Filters, 1 active",
            _ => $"Filters, {count} active",
        });
        ToolTipService.SetToolTip(FiltersButton, source is null ? "Filters" : $"Filter {source.Name} titles");
    }

    private async Task OpenFiltersAsync()
    {
        if (source is not { } current)
        {
            return;
        }
        var my = ++filterLoad;
        if (SessionSpecs.TryGetValue(current.Id, out var known))
        {
            ShowFilterPanel(current, known);
            return;
        }
        filterFlyout.Content = FilterStatus(current, failed: false);
        try
        {
            var specs = await current.GetFiltersAsync();
            if (my != filterLoad || source != current)
            {
                return;
            }
            SessionSpecs[current.Id] = specs;
            ShowFilterPanel(current, specs);
            UpdateFilterBadge();
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            if (my == filterLoad && source == current)
            {
                filterFlyout.Content = FilterStatus(current, failed: true);
            }
        }
    }

    private FrameworkElement FilterStatus(ISource current, bool failed)
    {
        var panel = new StackPanel { Width = 360, Padding = new Thickness(20), Spacing = 10 };
        if (!failed)
        {
            var ring = new ProgressRing { IsActive = true, Width = 24, Height = 24, HorizontalAlignment = HorizontalAlignment.Center };
            A11y.Name(ring, $"Loading {current.Name} filters");
            panel.Children.Add(ring);
            panel.Children.Add(new TextBlock { Text = $"Loading {current.Name} filters…", Style = Res("TzSecondary"), HorizontalAlignment = HorizontalAlignment.Center });
            return panel;
        }
        panel.Children.Add(new TextBlock { Text = $"Couldn't load {current.Name} filters", Style = Res("TzBodyStrong"), TextWrapping = TextWrapping.Wrap });
        panel.Children.Add(new TextBlock { Text = "Check your connection, then try again.", Style = Res("TzSecondary"), TextWrapping = TextWrapping.Wrap });
        var retry = new Button { Content = "Try again", HorizontalAlignment = HorizontalAlignment.Left };
        A11y.Name(retry, $"Try loading {current.Name} filters again");
        retry.Click += (_, _) => _ = Log.Guard("browse filters", OpenFiltersAsync);
        panel.Children.Add(retry);
        return panel;
    }

    private void ShowFilterPanel(ISource current, IReadOnlyList<FilterSpec> specs)
    {
        draft = new Dictionary<string, IReadOnlyList<string>>(Applied);
        filterFlyout.Content = BuildFilterPanel(current, specs);
    }

    private FrameworkElement BuildFilterPanel(ISource current, IReadOnlyList<FilterSpec> specs)
    {
        var count = new TextBlock { Style = Res("TzCaption"), VerticalAlignment = VerticalAlignment.Center };
        void Changed()
        {
            var active = new SearchQuery(query, draft).ActiveCount(specs);
            count.Text = active switch
            {
                0 => "No filters selected",
                1 => "1 filter selected",
                _ => $"{active} filters selected",
            };
        }

        var body = new StackPanel { Spacing = 22 };
        var fields = new Grid { ColumnSpacing = 12, RowSpacing = 14 };
        fields.ColumnDefinitions.Add(new ColumnDefinition());
        fields.ColumnDefinitions.Add(new ColumnDefinition());
        foreach (var spec in specs.Where(s => s.Kind is FilterKind.Select or FilterKind.Text))
        {
            var field = spec.Kind == FilterKind.Select ? SelectField(spec, Changed) : TextField(spec, Changed);
            var index = fields.Children.Count;
            if (index % 2 == 0)
            {
                fields.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            }
            Grid.SetRow(field, index / 2);
            Grid.SetColumn(field, index % 2);
            fields.Children.Add(field);
        }
        if (fields.Children.Count > 0)
        {
            body.Children.Add(fields);
        }
        foreach (var spec in specs.Where(s => s.Kind == FilterKind.Multi))
        {
            body.Children.Add(Section(spec.Label, null, MultiChips(spec, Changed)));
        }
        foreach (var spec in specs.Where(s => s.Kind == FilterKind.TriState))
        {
            body.Children.Add(Section(spec.Label, "Click a tag once to include it, again to exclude it.", TriStateGroups(spec, Changed)));
        }
        Changed();

        var header = new StackPanel { Padding = new Thickness(20, 16, 20, 10), Spacing = 2 };
        header.Children.Add(new TextBlock { Text = "Filters", Style = Res("TzSubtitle") });
        header.Children.Add(new TextBlock { Text = current.Name, Style = Res("TzCaption") });

        var scroll = new ScrollViewer
        {
            Content = body,
            MaxHeight = 460,
            Padding = new Thickness(20, 4, 20, 20),
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };
        Grid.SetRow(scroll, 1);

        var reset = new Button { Content = "Reset" };
        A11y.Name(reset, "Reset filters");
        ToolTipService.SetToolTip(reset, "Clear every filter in this panel");
        reset.Click += (_, _) =>
        {
            draft = [];
            filterFlyout.Content = BuildFilterPanel(current, specs);
        };
        var apply = new Button { Content = "Apply", Style = Res("AccentButtonStyle") };
        A11y.Name(apply, "Apply filters");
        apply.Click += (_, _) => ApplyFilters(current, specs);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        buttons.Children.Add(reset);
        buttons.Children.Add(apply);
        Grid.SetColumn(buttons, 1);
        var footerRow = new Grid { ColumnSpacing = 12 };
        footerRow.ColumnDefinitions.Add(new ColumnDefinition());
        footerRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footerRow.Children.Add(count);
        footerRow.Children.Add(buttons);
        var footer = new Border
        {
            Child = footerRow,
            BorderBrush = Theme.Brush("TzStroke"),
            BorderThickness = new Thickness(0, 1, 0, 0),
            Padding = new Thickness(20, 12, 20, 12),
        };
        Grid.SetRow(footer, 2);

        var root = new Grid { Width = 500 };
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.Children.Add(header);
        root.Children.Add(scroll);
        root.Children.Add(footer);
        return root;
    }

    private FrameworkElement SelectField(FilterSpec spec, Action changed)
    {
        var box = new ComboBox
        {
            Header = spec.Label,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            ItemsSource = spec.Options.Select(o => o.Label).ToList(),
        };
        var chosen = new SearchQuery(query, draft).Single(spec);
        box.SelectedIndex = Math.Max(0, spec.Options.ToList().FindIndex(o => o.Value == chosen));
        A11y.Name(box, spec.Label);
        box.SelectionChanged += (_, _) =>
        {
            if (box.SelectedIndex >= 0)
            {
                draft[spec.Key] = [spec.Options[box.SelectedIndex].Value];
                changed();
            }
        };
        return box;
    }

    private FrameworkElement TextField(FilterSpec spec, Action changed)
    {
        var box = new TextBox
        {
            Header = spec.Label,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            Text = new SearchQuery(query, draft).Get(spec).FirstOrDefault() ?? string.Empty,
        };
        A11y.Name(box, spec.Label);
        box.TextChanged += (_, _) =>
        {
            draft[spec.Key] = [box.Text];
            changed();
        };
        return box;
    }

    private static StackPanel Section(string label, string? hint, UIElement content)
    {
        var section = new StackPanel { Spacing = 10 };
        var head = new StackPanel { Spacing = 2 };
        head.Children.Add(new TextBlock { Text = label, Style = Res("TzBodyStrong") });
        if (hint is not null)
        {
            head.Children.Add(new TextBlock { Text = hint, Style = Res("TzCaption"), TextWrapping = TextWrapping.Wrap });
        }
        section.Children.Add(head);
        section.Children.Add(content);
        return section;
    }

    private static ToggleButton FilterChip(string label, bool isChecked) => new()
    {
        Style = Res("TzChip"),
        Content = label,
        IsChecked = isChecked,
        MinHeight = 30,
        Padding = new Thickness(12, 0, 12, 0),
        FontSize = 12,
    };

    private static IReadOnlyList<string> Ordered(FilterSpec spec, ICollection<string> values) =>
        [.. spec.Options.Select(o => o.Value).Where(values.Contains)];

    private WrapPanel MultiChips(FilterSpec spec, Action changed)
    {
        var wrap = new WrapPanel { HorizontalSpacing = 8, VerticalSpacing = 8 };
        var selected = new SearchQuery(query, draft).Get(spec);
        foreach (var option in spec.Options)
        {
            var chip = FilterChip(option.Label, selected.Contains(option.Value));
            A11y.NameToggle(chip, option.Label);
            chip.Click += (_, _) =>
            {
                var now = new SearchQuery(query, draft).Get(spec).ToHashSet();
                if (chip.IsChecked == true)
                {
                    now.Add(option.Value);
                }
                else
                {
                    now.Remove(option.Value);
                }
                draft[spec.Key] = Ordered(spec, now);
                A11y.NameToggle(chip, option.Label);
                changed();
            };
            wrap.Children.Add(chip);
        }
        return wrap;
    }

    private StackPanel TriStateGroups(FilterSpec spec, Action changed)
    {
        var groups = new StackPanel { Spacing = 14 };
        foreach (var group in spec.Options.GroupBy(o => o.Group))
        {
            var wrap = new WrapPanel { HorizontalSpacing = 8, VerticalSpacing = 8 };
            foreach (var option in group)
            {
                wrap.Children.Add(TriStateChip(spec, option, changed));
            }
            if (group.Key is not { } name)
            {
                groups.Children.Add(wrap);
                continue;
            }
            var labelled = new StackPanel { Spacing = 8 };
            labelled.Children.Add(new TextBlock { Text = name.ToUpperInvariant(), Style = Res("TzKicker") });
            labelled.Children.Add(wrap);
            groups.Children.Add(labelled);
        }
        return groups;
    }

    private ToggleButton TriStateChip(FilterSpec spec, FilterOption option, Action changed)
    {
        var chip = FilterChip(option.Label, false);
        void Render()
        {
            var current = new SearchQuery(query, draft);
            var state = current.Get(spec).Contains(option.Value) ? 1 : current.Excluded(spec).Contains(option.Value) ? 2 : 0;
            chip.IsChecked = state == 1;
            chip.Content = state == 0 ? option.Label : TriStateContent(option.Label, excluded: state == 2);
            if (state == 2)
            {
                chip.Foreground = Theme.Brush("TzDanger");
                chip.BorderBrush = Theme.Brush("TzDanger");
            }
            else
            {
                chip.ClearValue(Control.ForegroundProperty);
                chip.ClearValue(Control.BorderBrushProperty);
            }
            AutomationProperties.SetName(chip, option.Label + state switch
            {
                1 => ", included",
                2 => ", excluded",
                _ => ", not used",
            });
        }

        chip.Click += (_, _) =>
        {
            var current = new SearchQuery(query, draft);
            var included = current.Get(spec).ToHashSet();
            var excluded = current.Excluded(spec).ToHashSet();
            if (included.Remove(option.Value))
            {
                excluded.Add(option.Value);
            }
            else if (!excluded.Remove(option.Value))
            {
                included.Add(option.Value);
            }
            draft[spec.Key] = Ordered(spec, included);
            draft[spec.ExcludeKey] = Ordered(spec, excluded);
            Render();
            changed();
        };
        Render();
        return chip;
    }

    private static StackPanel TriStateContent(string label, bool excluded)
    {
        var content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        content.Children.Add(new FontIcon
        {
            Glyph = excluded ? Icons.Close : Icons.Check,
            FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"],
            FontSize = 12,
        });
        content.Children.Add(new TextBlock
        {
            Text = label,
            VerticalAlignment = VerticalAlignment.Center,
            TextDecorations = excluded ? Windows.UI.Text.TextDecorations.Strikethrough : Windows.UI.Text.TextDecorations.None,
        });
        return content;
    }

    private void ApplyFilters(ISource current, IReadOnlyList<FilterSpec> specs)
    {
        var chosen = new SearchQuery(query, draft);
        var kept = new Dictionary<string, IReadOnlyList<string>>();
        foreach (var spec in specs)
        {
            if (chosen.ActiveCount([spec]) == 0)
            {
                continue;
            }
            kept[spec.Key] = chosen.Get(spec);
            if (spec.Kind == FilterKind.TriState)
            {
                kept[spec.ExcludeKey] = chosen.Excluded(spec);
            }
        }
        if (kept.Count == 0)
        {
            SessionFilters.Remove(current.Id);
        }
        else
        {
            SessionFilters[current.Id] = kept;
        }
        filterFlyout.Hide();
        if (source != current)
        {
            return;
        }
        UpdateFilterBadge();
        if (HasSearch)
        {
            mode = Mode.Search;
        }
        else if (mode == Mode.Search)
        {
            mode = Mode.Popular;
        }
        BuildModes();
        Reload();
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId) =>
        realized
            .Where(tile => (tile.Data is { Tag: BrowseItem b } && resolved.TryGetValue(Key(b), out var known) ? known : tile.Data?.MangaId) == mangaId)
            .OrderBy(tile => tileWidth > 0 && Math.Abs(tile.ActualWidth - tileWidth) <= 1 ? 0 : 1)
            .Select(tile => tile.CoverElement)
            .ToList();

    private void Relayout()
    {
        if (!EnsureTileMetrics())
        {
            Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () => EnsureTileMetrics());
        }
    }

    private bool EnsureTileMetrics()
    {
        var panel = TileGrid.ItemsPanelRoot as ItemsWrapGrid ?? ItemsTree.FindDescendant<ItemsWrapGrid>(TileGrid);
        if (panel is null)
        {
            return false;
        }
        var available = TileGrid.ActualWidth - TileGrid.Padding.Left - TileGrid.Padding.Right;
        if (available <= 0 || double.IsNaN(available))
        {
            return false;
        }
        const double spacing = 20;
        var columns = Math.Max(2, (int)Math.Floor((available + spacing) / (Services.Settings.TileWidth + spacing)));
        var width = Math.Floor((available - 1) / columns) - spacing;
        if (Math.Abs(width - tileWidth) < 0.5 && !double.IsNaN(panel.ItemWidth))
        {
            return true;
        }
        tileWidth = width;
        panel.ItemWidth = width + spacing;
        panel.ItemHeight = width * 1.5 + 65 + 28;
        return true;
    }

    private void ApplyTileTransitions() => TileGrid.ItemContainerTransitions = ListMotion.ItemTransitions();

    private void ReplaceQuietly(BrowseItem item, Func<TileData, TileData> change)
    {
        var index = -1;
        for (var i = 0; i < items.Count; i++)
        {
            if (items[i].Tag is BrowseItem b && b == item)
            {
                index = i;
                break;
            }
        }
        if (index < 0)
        {
            return;
        }
        var transitions = TileGrid.ItemContainerTransitions;
        TileGrid.ItemContainerTransitions = null;
        items[index] = change(items[index]);
        Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () => TileGrid.ItemContainerTransitions = transitions, "browse-transitions");
    }
}
