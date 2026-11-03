using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.App.Pages;

public sealed partial class BrowsePage : UserControl, ITzPage, ITileHost
{
    private enum Mode
    {
        Popular,
        Latest,
        Search,
    }

    private static readonly DataTemplate TileTemplate = (DataTemplate)Application.Current.Resources["TzTileTemplate"];

    private readonly ObservableCollection<TileData> items = [];
    private readonly Dictionary<DependencyObject, MangaTile> tileByContainer = [];
    private readonly HashSet<MangaTile> realized = [];
    private readonly Dictionary<long, long> idsByTile = [];
    private ScrollViewer? scroller;
    private Mode mode = Mode.Popular;
    private string query = string.Empty;
    private int page;
    private bool hasNext = true;
    private bool loading;
    private int version;
    private double tileWidth;
    private HashSet<string> inLibrary = [];
    private bool marksDirty = true;
    private ISource? source;

    public BrowsePage()
    {
        InitializeComponent();
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
            scroller ??= ItemsTree.FindDescendant<ScrollViewer>(TileGrid);
            if (scroller is not null)
            {
                scroller.ViewChanged += (_, _) => MaybeLoadMore();
            }
        };
        AppHost.TilesResized += (_, _) => { tileWidth = 0; Relayout(); };
        RetryButton.Click += (_, _) => _ = ReloadAsync();
        source = Services.Engine?.Sources.All.FirstOrDefault();
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += (_, _) => marksDirty = true;
        }
        AppHost.EngineReady += OnEngineReady;
        BuildModes();
    }

    private void OnEngineReady(object? sender, EventArgs e)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            if (source is null && Services.Engine?.Sources.All.FirstOrDefault() is { } first)
            {
                source = first;
                BuildModes();
                if (items.Count == 0 && !loading)
                {
                    _ = ReloadAsync();
                }
            }
        });
    }
    public void Search(string text)
    {
        if (text.Length == 0) return;
        query = text;
        mode = Mode.Search;
        BuildModes();
        _ = ReloadAsync();
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
            _ = OpenAsync(tile);
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

    private void BuildModes()
    {
        Modes.Children.Clear();
        AddMode("Popular", Mode.Popular);
        if (source?.SupportsLatest != false)
        {
            AddMode("Latest updates", Mode.Latest);
        }
        if (query.Length > 0)
        {
            AddMode($"“{query}”", Mode.Search);
        }
        Summary.Text = source is null ? "No sources available" : source.Name;
    }

    private void AddMode(string label, Mode target)
    {
        var chip = new ToggleButton { Style = (Style)Application.Current.Resources["TzChip"], Content = label, IsChecked = mode == target };
        chip.Click += (_, _) =>
        {
            mode = target;
            foreach (var other in Modes.Children.OfType<ToggleButton>())
            {
                other.IsChecked = other == chip;
            }
            _ = ReloadAsync();
        };
        Modes.Children.Add(chip);
    }

    public void OnShown()
    {
        ApplyTileTransitions();
        if (items.Count == 0 && !loading)
        {
            _ = ReloadAsync();
        }
        if (marksDirty)
        {
            _ = RefreshLibraryMarksAsync();
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

    private void ApplyLibraryMarks()
    {
        var updated = false;
        for (var i = 0; i < items.Count; i++)
        {
            if (items[i].Tag is SManga manga)
            {
                var marked = inLibrary.Contains((source?.Id ?? 0) + "|" + manga.Url);
                if (items[i].InLibrary != marked)
                {
                    items[i] = items[i] with { InLibrary = marked, Caption = marked ? "In library" : StatusText(manga.Status) };
                    updated = true;
                }
            }
        }
    }

    private async Task ReloadAsync()
    {
        version++;
        items.Clear();
        idsByTile.Clear();
        DispatcherQueue.TryEnqueue(Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
        {
            tileByContainer.Clear();
            realized.Clear();
        });
        page = 0;
        hasNext = true;
        loading = false;
        scroller?.ChangeView(null, 0, null, true);
        await LoadMoreAsync(animate: true);
    }

    private void MaybeLoadMore()
    {
        if (scroller is { } s && !loading && hasNext && s.VerticalOffset > s.ExtentHeight - s.ViewportHeight * 1.8)
        {
            _ = LoadMoreAsync(animate: false);
        }
    }

    private async Task LoadMoreAsync(bool animate)
    {
        if (source is null || loading || !hasNext)
        {
            return;
        }
        loading = true;
        var my = version;
        LoadingRing.IsActive = true;
        Message.Visibility = Visibility.Collapsed;
        try
        {
            var next = page + 1;
            var result = mode switch
            {
                Mode.Latest => await source.GetLatestAsync(next),
                Mode.Search => await source.SearchAsync(query, next),
                _ => await source.GetPopularAsync(next),
            };
            if (my != version)
            {
                return;
            }
            page = next;
            hasNext = result.HasNextPage;
            foreach (var manga in result.Mangas)
            {
                var key = source.Id + "|" + manga.Url;
                if (items.Any(i => i.Tag is SManga m && m.Url == manga.Url))
                {
                    continue;
                }
                var marked = inLibrary.Contains(key);
                items.Add(new TileData(0, manga.Title, manga.ThumbnailUrl, marked ? "In library" : StatusText(manga.Status), 0, false, false, manga, marked, AddToLibrary));
            }
            if (items.Count == 0)
            {
                ShowMessage(mode == Mode.Search ? "No results" : "Nothing here", mode == Mode.Search ? $"MangaDex has nothing matching “{query}”." : "MangaDex returned no titles.", retry: false);
            }
            if (animate && items.Count > 0)
            {
                StaggerRealized(24);
            }
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or InvalidOperationException)
        {
            if (my == version && items.Count == 0)
            {
                ShowMessage("Couldn't reach MangaDex", "Check your connection, then try again.", retry: true);
            }
        }
        finally
        {
            if (my == version)
            {
                loading = false;
                LoadingRing.IsActive = false;
            }
        }
    }

    private void StaggerRealized(int take)
    {
        DispatcherQueue.TryEnqueue(Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
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
        if (Services.Engine is not { } engine || source is null || tile.Data?.Tag is not SManga manga)
        {
            return;
        }
        var id = await engine.Library.UpsertFromSourceAsync(source.Id, manga);
        idsByTile[id] = id;
        tile.Bind(tile.Data with { MangaId = id });
        await AppNav.OpenDetailsAsync(id, tile);
    }

    private void AddToLibrary(MangaTile tile) => _ = AddToLibraryAsync(tile);

    private async Task AddToLibraryAsync(MangaTile tile)
    {
        if (Services.Engine is not { } engine || source is null || tile.Data?.Tag is not SManga manga)
        {
            return;
        }
        var key = source.Id + "|" + manga.Url;
        if (inLibrary.Contains(key))
        {
            return;
        }
        long id;
        try
        {
            id = await engine.Refresher.AddToLibraryAsync(source.Id, manga);
        }
        catch (Exception e) when (e is HttpRequestException or IOException)
        {
            OfflineToast(manga);
            return;
        }
        inLibrary.Add(key);
        tile.Bind(tile.Data with { MangaId = id, InLibrary = true, Caption = "In library" });
        for (var i = 0; i < items.Count; i++)
        {
            if (items[i].Tag is SManga m && m.Url == manga.Url)
            {
                items[i] = items[i] with { InLibrary = true, Caption = "In library" };
                break;
            }
        }
        Toasts.Show("Added to library", manga.Title, Icons.Check, "View", () => _ = AppNav.OpenDetailsAsync(id, null), coverUrl: manga.ThumbnailUrl);
        _ = RefreshChaptersInBackgroundAsync(id, manga);
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
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or InvalidOperationException)
        {
            OfflineToast(manga);
        }
    }

    private static void OfflineToast(SManga manga) =>
        Toasts.Show("Added to library (offline — chapters will sync when online)", manga.Title, Icons.Check, coverUrl: manga.ThumbnailUrl);

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

    private void ApplyTileTransitions() => TileGrid.ItemContainerTransitions = ListMotion.ItemTransitions();
}
