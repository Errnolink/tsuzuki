using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Pages;

public sealed partial class LibraryPage : UserControl, ITzPage, ITileHost
{
    private static readonly (string Key, string Label)[] Sorts =
    [
        ("recent", "Recently read"),
        ("unread", "Most unread"),
        ("title", "Title"),
        ("added", "Recently added"),
    ];

    private static readonly DataTemplate TileTemplate = (DataTemplate)Application.Current.Resources["TzTileTemplate"];

    private readonly BulkObservableCollection<TileData> source = [];
    private readonly Dictionary<DependencyObject, MangaTile> tileByContainer = [];
    private readonly HashSet<MangaTile> realized = [];
    private readonly DispatcherQueueTimer searchDebounce;
    private readonly DispatcherQueueTimer refreshTimer;
    private IReadOnlyList<LibraryEntry> all = [];
    private IReadOnlyList<CategoryInfo> categories = [];
    private string filter = string.Empty;
    private string shape = string.Empty;
    private string appliedFilter = string.Empty;
    private IReadOnlyList<LibraryEntry>? lastEntries;
    private bool dirty = true;
    private bool shown;
    private bool firstLoad = true;
    private readonly MenuFlyout sortMenu = new() { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight };
    private ScrollViewer? gridScroller;
    private double collapseProgress = -1;
    private bool titleCollapsed;


    private double tileWidth = 168;

    public LibraryPage()
    {
        InitializeComponent();
        TileGrid.ItemTemplate = TileTemplate;
        TileGrid.ItemsPanel = (ItemsPanelTemplate)XamlReader.Load(
            """<ItemsPanelTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><ItemsWrapGrid Orientation="Horizontal"/></ItemsPanelTemplate>""");
        TileGrid.ItemContainerStyle = MakeContainerStyle();
        ApplyTileTransitions();
        TileGrid.ItemsSource = source;
        TileGrid.ContainerContentChanging += OnContainerChanging;
        TileGrid.SizeChanged += (_, _) => Relayout();
        TileGrid.Loaded += (_, _) => { Relayout(); HookGridScroller(); };
        AppHost.TilesResized += (_, _) => { tileWidth = 0; Relayout(); };
        refreshTimer = DispatcherQueue.CreateTimer();
        refreshTimer.Interval = TimeSpan.FromMilliseconds(250);
        refreshTimer.IsRepeating = false;
        refreshTimer.Tick += (_, _) => _ = Log.Guard("library", LoadAsync);

        foreach (var (key, label) in Sorts)
        {
            var item = new MenuFlyoutItem { Text = label };
            item.Click += (_, _) =>
            {
                Services.Settings.LibrarySort = key;
                Services.Settings.Save();
                _ = ApplyFilterAsync(animate: true);
            };
            sortMenu.Items.Add(item);
        }
        EmptyPair.Click += (_, _) => DevicesPanel.Show();
        EmptyBrowse.Click += (_, _) => _ = App.MainWindow.NavigateRootAsync("browse");
        BuildSkeleton();
        DeclareTopBar();

        searchDebounce = DispatcherQueue.CreateTimer();
        searchDebounce.Interval = TimeSpan.FromMilliseconds(150);
        searchDebounce.IsRepeating = false;
        searchDebounce.Tick += (_, _) =>
        {
            if (shown)
            {
                _ = ApplyFilterAsync(animate: false);
            }
        };

        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += OnLibraryChanged;
        }
        AppHost.EngineReady += OnEngineReady;
        App.MainWindow.SearchChanged += (_, text) =>
        {
            filter = text.Trim();
            searchDebounce.Stop();
            searchDebounce.Start();
        };
    }

    private void OnEngineReady(object? sender, EventArgs e)
    {
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed -= OnLibraryChanged;
            engine.Library.Changed += OnLibraryChanged;
        }
        dirty = true;
        if (shown)
        {
            _ = Log.Guard("library", LoadAsync);
        }
    }

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            dirty = true;
            if (shown)
            {
                refreshTimer.Start();
            }
        });
    }

    public void OnShown()
    {
        shown = true;
        ApplyTileTransitions();
        if (dirty)
        {
            _ = Log.Guard("library", LoadAsync);
        }
        else if (filter != appliedFilter)
        {
            _ = ApplyFilterAsync(animate: false);
        }
    }

    public void OnHidden() => shown = false;

    private async Task LoadAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        dirty = false;
        var library = engine.Library;
        var entriesTask = library.GetLibraryAsync();
        var continueTask = library.GetContinueReadingAsync(10);
        var categoriesTask = library.GetCategoriesAsync();
        all = await entriesTask;
        var continueEntries = await continueTask;
        categories = await categoriesTask;
        BuildChips();
        BuildContinue(continueEntries);
        await ApplyFilterAsync(animate: firstLoad);
        firstLoad = false;
        Skeleton.Visibility = Visibility.Collapsed;
        var unread = all.Sum(e => e.UnreadCount);
        Summary.Text = all.Count == 0 ? string.Empty : $"{all.Count} titles · {unread} unread chapters";
        DeclareTopBar();
        DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, UpdateTitleCollapse);
    }

    private void HookGridScroller()
    {
        if (gridScroller is not null)
        {
            return;
        }
        gridScroller = ItemsTree.FindDescendant<ScrollViewer>(TileGrid);
        if (gridScroller is not null)
        {
            gridScroller.ViewChanged += (_, _) => UpdateTitleCollapse();
        }
    }

    private void UpdateTitleCollapse()
    {
        if (TitleBlock.ActualHeight == 0)
        {
            return;
        }
        var top = TitleBlock.TransformToVisual(TileGrid).TransformPoint(default).Y;
        var progress = Math.Clamp((104 - (top + TitleBlock.ActualHeight)) / 48d, 0d, 1d);
        if (Math.Abs(progress - collapseProgress) < 0.01)
        {
            return;
        }
        collapseProgress = progress;
        var visual = Motion.VisualOf(TitleBlock);
        visual.StopAnimation("Opacity");
        visual.StopAnimation("Scale");
        visual.CenterPoint = new((float)TitleBlock.ActualWidth / 2, (float)TitleBlock.ActualHeight / 2, 0);
        visual.Opacity = 1 - (float)progress;
        if (!Motion.Reduced)
        {
            var shrink = 1f - 0.04f * (float)progress;
            visual.Scale = new(shrink, shrink, 1);
        }
        var collapsed = progress > 0.5;
        if (collapsed != titleCollapsed)
        {
            titleCollapsed = collapsed;
            DeclareTopBar();
        }
    }

    private void DeclareTopBar()
    {
        App.MainWindow.SetTopBar(this, new TopBarState(titleCollapsed ? "Library" : null, all.Count > 0
            ? [
                new TopBarAction(Icons.Sort, "Sort library", anchor => sortMenu.ShowAt(anchor)),
                new TopBarAction(Icons.Refresh, "Check for new chapters", anchor => _ = RefreshFromSourcesAsync(anchor)),
            ]
            : []));
    }

    private void BuildSkeleton()
    {
        List<UIElement> ghosts = [];
        for (var i = 0; i < 3; i++)
        {
            var ghost = new Border { Width = 340, Height = 132, CornerRadius = new CornerRadius(16), Background = Design.Theme.Brush("TzSkeleton") };
            GhostContinue.Children.Add(ghost);
            ghosts.Add(ghost);
        }
        var tiles = new WrapPanel { HorizontalSpacing = 20, VerticalSpacing = 28 };
        for (var i = 0; i < 6; i++)
        {
            var tile = new StackPanel { Width = tileWidth };
            var cover = new Border { Height = tileWidth / 1.5, CornerRadius = new CornerRadius(10), Background = Design.Theme.Brush("TzSkeleton") };
            tile.Children.Add(cover);
            var title = new Border { Height = 36, CornerRadius = new CornerRadius(6), Background = Design.Theme.Brush("TzSkeleton"), Margin = new Thickness(2, 10, 2, 0) };
            tile.Children.Add(title);
            var caption = new Border { Height = 14, CornerRadius = new CornerRadius(4), Background = Design.Theme.Brush("TzSkeleton"), Margin = new Thickness(2, 3, 2, 0) };
            tile.Children.Add(caption);
            tiles.Children.Add(tile);
            ghosts.Add(cover);
            ghosts.Add(title);
            ghosts.Add(caption);
        }
        GhostTiles.Children.Add(tiles);
        Design.Skeleton.Pulse(ghosts);
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private void BuildChips()
    {
        Chips.Children.Clear();
        if (categories.Count == 0)
        {
            ChipScroller.Visibility = Visibility.Collapsed;
            return;
        }
        ChipScroller.Visibility = Visibility.Visible;
        var selected = Services.Settings.LibraryCategory;
        if (selected is { } s && categories.All(c => c.Uid != s))
        {
            selected = null;
            Services.Settings.LibraryCategory = null;
        }
        AddChip(null, "All", all.Count, selected is null);
        foreach (var category in categories.OrderBy(c => c.Order))
        {
            var count = all.Count(e => e.CategoryUids.Contains(category.Uid));
            AddChip(category.Uid, category.Name, count, selected == category.Uid);
        }
        var uncategorized = all.Count(e => e.CategoryUids.Count == 0);
        if (uncategorized > 0)
        {
            AddChip(0, "Uncategorized", uncategorized, selected == 0);
        }
    }

    private void AddChip(long? uid, string name, int count, bool isChecked)
    {
        var content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        content.Children.Add(new TextBlock { Text = name, VerticalAlignment = VerticalAlignment.Center });
        var pill = new Border
        {
            Background = Design.Theme.Brush("TzChipCount"),
            CornerRadius = new CornerRadius(9),
            Height = 18,
            Padding = new Thickness(7, 0, 7, 0),
            VerticalAlignment = VerticalAlignment.Center,
        };
        pill.Child = new TextBlock
        {
            Text = count.ToString(),
            FontSize = 11,
            Foreground = Design.Theme.Brush("TzTextTertiary"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        content.Children.Add(pill);
        var chip = new ToggleButton { Style = (Style)Application.Current.Resources["TzChip"], Content = content, IsChecked = isChecked, Tag = uid };
        chip.Click += (_, _) =>
        {
            Services.Settings.LibraryCategory = uid;
            Services.Settings.Save();
            foreach (var other in Chips.Children.OfType<ToggleButton>())
            {
                other.IsChecked = Equals(other.Tag, uid);
            }
            _ = ApplyFilterAsync(animate: true);
        };
        Chips.Children.Add(chip);
    }

    private IReadOnlyList<ContinueEntry> lastContinue = Array.Empty<ContinueEntry>();

    private void BuildContinue(IReadOnlyList<ContinueEntry> entries)
    {
        if (!firstLoad && entries.SequenceEqual(lastContinue))
        {
            return;
        }
        lastContinue = entries;
        ContinueRow.Children.Clear();
        ContinueSection.Visibility = entries.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        foreach (var entry in entries)
        {
            var card = new ContinueCard(entry, null);
            card.Click += (_, _) => _ = AppNav.OpenReaderAsync(entry.MangaId, entry.ChapterId, entry.LastPageRead, card.CoverElement);
            var open = new MenuFlyoutItem { Text = "Open details" };
            open.Click += (_, _) => _ = AppNav.OpenDetailsAsync(entry.MangaId, null);
            card.ContextFlyout = new MenuFlyout { Items = { open } };
            ContinueRow.Children.Add(card);
        }
        if (entries.Count > 0 && firstLoad)
        {
            _ = Motion.Stagger(ContinueRow.Children.Take(6).ToList(), TimeSpan.FromMilliseconds(45), rise: 10);
        }
    }

    private async Task ApplyFilterAsync(bool animate)
    {
        var entries = all;
        var category = Services.Settings.LibraryCategory;
        var text = filter;
        var sort = Services.Settings.LibrarySort;
        var tiles = await Task.Run(() =>
        {
            IEnumerable<LibraryEntry> items = entries;
            if (category is { } uid)
            {
                items = uid == 0 ? items.Where(e => e.CategoryUids.Count == 0) : items.Where(e => e.CategoryUids.Contains(uid));
            }
            if (text.Length > 0)
            {
                items = items.Where(e => e.Title.Contains(text, StringComparison.CurrentCultureIgnoreCase));
            }
            items = sort switch
            {
                "title" => items.OrderBy(e => e.Title, StringComparer.CurrentCultureIgnoreCase),
                "unread" => items.OrderByDescending(e => e.UnreadCount).ThenByDescending(e => e.LastReadAt),
                "added" => items.OrderByDescending(e => e.DateAdded),
                _ => items.OrderByDescending(e => e.LastReadAt).ThenByDescending(e => e.DateAdded),
            };
            return items.Select(ToTile).ToList();
        });
        var nextShape = $"{sort}|{category?.ToString() ?? "-"}";
        if (shape.Length > 0 && nextShape == shape)
        {
            SyncIncremental(source, tiles);
        }
        else
        {
            shape = nextShape;
            source.ReplaceAll(tiles);
        }
        lastEntries = entries;
        appliedFilter = text;
        var empty = all.Count == 0;
        EmptyState.Visibility = empty || tiles.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        EmptyTitle.Text = empty ? "Your shelf is empty" : "Nothing here";
        EmptyBody.Text = empty
            ? "Pair your phone to bring your library over, or find something on MangaDex."
            : filter.Length > 0 ? $"No titles match “{filter}”. Press Enter to search MangaDex." : "No titles in this category yet.";
        EmptyActions.Visibility = empty ? Visibility.Visible : Visibility.Collapsed;
        if (animate && tiles.Count > 0)
        {
            StaggerRealized(28);
        }
    }
    private static void SyncIncremental(BulkObservableCollection<TileData> current, IReadOnlyList<TileData> targets)
    {
        var targetMap = new Dictionary<long, (int Index, TileData Tile)>(targets.Count);
        for (var i = 0; i < targets.Count; i++)
        {
            targetMap[targets[i].MangaId] = (i, targets[i]);
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
                if (current[i] with { Tag = null } != target with { Tag = null })
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
                if (current[i] with { Tag = null } != target with { Tag = null })
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
        DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, () =>
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
        });
    }

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        EnsureTileMetrics();
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
            _ = AppNav.OpenDetailsAsync(tile.Data!.MangaId, tile);
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

    private static TileData ToTile(LibraryEntry e)
    {
        var caption = e.LastReadAt > 0 ? "Read " + Format.Relative(e.LastReadAt) : e.ChapterCount > 0 ? $"{e.ChapterCount} chapters" : "Not started";
        return new TileData(e.MangaId, e.Title, e.ThumbnailUrl, caption, e.UnreadCount, !e.Readable, e.IsMerged);
    }

    private void Relayout() => EnsureTileMetrics();

    private void EnsureTileMetrics()
    {
        var panel = TileGrid.ItemsPanelRoot as ItemsWrapGrid ?? ItemsTree.FindDescendant<ItemsWrapGrid>(TileGrid);
        if (panel is null)
        {
            return;
        }
        var available = TileGrid.ActualWidth - TileGrid.Padding.Left - TileGrid.Padding.Right;
        if (available <= 0 || double.IsNaN(available))
        {
            return;
        }
        const double spacing = 20;
        var columns = Math.Max(2, (int)Math.Floor((available + spacing) / (Services.Settings.TileWidth + spacing)));
        var width = Math.Floor((available - spacing * (columns - 1)) / columns);
        if (Math.Abs(width - tileWidth) < 0.5 && !double.IsNaN(panel.ItemWidth))
        {
            return;
        }
        tileWidth = width;
        panel.ItemWidth = width + spacing;
        panel.ItemHeight = width * 1.5 + 65 + 28;
    }

    public MangaTile? FindTile(long mangaId)
    {
        MangaTile? fallback = null;
        foreach (var tile in realized)
        {
            if (tile.Data?.MangaId != mangaId)
            {
                continue;
            }
            fallback ??= tile;
            if (tileWidth > 0 && Math.Abs(tile.ActualWidth - tileWidth) <= 1)
            {
                return tile;
            }
        }
        return fallback;
    }

    private async Task RefreshFromSourcesAsync(FrameworkElement? anchor)
    {
        if (Services.Engine is null)
        {
            return;
        }
        var button = anchor as Button;
        var icon = button?.Content as FontIcon;
        var spin = icon is null ? null : Motion.VisualOf(icon);
        if (spin is not null)
        {
            spin.CenterPoint = new System.Numerics.Vector3((float)icon!.ActualWidth / 2, (float)icon.ActualHeight / 2, 0);
            var anim = spin.Compositor.CreateScalarKeyFrameAnimation();
            anim.InsertKeyFrame(1f, 360f, spin.Compositor.CreateLinearEasingFunction());
            anim.Duration = TimeSpan.FromMilliseconds(900);
            anim.IterationBehavior = Microsoft.UI.Composition.AnimationIterationBehavior.Forever;
            spin.StartAnimation("RotationAngleInDegrees", anim);
        }
        if (button is not null)
        {
            button.IsEnabled = false;
        }
        try
        {
            var result = await LibraryUpdater.RunAsync();
            Toasts.Show(result.NewChapters > 0 ? $"{result.NewChapters} new chapters" : "You're up to date",
                result.Failed > 0 ? $"{result.Failed} titles could not be checked" : null, Icons.Sparkles);
        }
        finally
        {
            if (spin is not null)
            {
                spin.StopAnimation("RotationAngleInDegrees");
                spin.RotationAngleInDegrees = 0;
            }
            if (button is not null)
            {
                button.IsEnabled = true;
            }
        }
    }

    private void ApplyTileTransitions() => TileGrid.ItemContainerTransitions = ListMotion.ItemTransitions();
}
