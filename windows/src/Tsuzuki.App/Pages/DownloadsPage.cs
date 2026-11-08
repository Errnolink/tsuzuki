using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media.Animation;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Downloads;

namespace Tsuzuki.App.Pages;

public sealed partial class DownloadsPage : TzPage, ITzPage, ICoverHost
{
    private static readonly DataTemplate RowTemplate = (DataTemplate)XamlReader.Load(
        """<DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Grid MinHeight="60" /></DataTemplate>""");

    private readonly ListView list;
    private readonly TextBlock summary = new() { TextWrapping = TextWrapping.Wrap };
    private readonly TextBlock queueTitle = new() { Text = "Queue" };
    private readonly TextBlock emptyQueue = new() { Text = "Nothing is downloading. Choose Download on a title or a chapter to save it for offline reading.", TextWrapping = TextWrapping.Wrap };
    private readonly Button pause = new() { MinWidth = 96 };
    private readonly Button retry = new() { Content = "Retry failed" };
    private readonly Button cancelAll = new() { Content = "Cancel all" };
    private readonly StackPanel downloadedPanel = new() { Spacing = 4 };
    private readonly TextBlock downloadedTitle = new() { Text = "Downloaded" };
    private readonly StackPanel titleBlock = new();
    private readonly Dictionary<long, Row> bound = [];
    private List<DownloadItem> items = [];
    private bool dirty;
    private bool attached;
    private int version;

    public DownloadsPage()
    {
        list = new ListView
        {
            SelectionMode = ListViewSelectionMode.None,
            ItemTemplate = RowTemplate,
            ItemContainerTransitions = new TransitionCollection(),
            Padding = new Thickness(36, 28, 36, 56),
            Header = BuildHeader(),
            Footer = BuildFooter(),
        };
        var itemStyle = new Style(typeof(ListViewItem));
        itemStyle.Setters.Add(new Setter(ListViewItem.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        itemStyle.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(0)));
        itemStyle.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(0, 0, 0, 4)));
        itemStyle.Setters.Add(new Setter(Control.IsTabStopProperty, false));
        list.ItemContainerStyle = itemStyle;
        list.ContainerContentChanging += OnContainerChanging;
        AutomationProperties.SetName(list, "Download queue");
        Content = list;
        pause.Click += (_, _) =>
        {
            if (Services.Engine is not { } engine)
            {
                return;
            }
            if (engine.Downloads.Block == DownloadBlock.Paused)
            {
                engine.Downloads.Resume();
            }
            else
            {
                engine.Downloads.Pause();
            }
        };
        retry.Click += (_, _) => _ = Log.Guard("downloads-retry", async () =>
        {
            if (Services.Engine is { } engine)
            {
                await engine.Downloads.RetryAsync([.. items.Where(i => i.State == DownloadState.Failed).Select(i => i.Id)]);
            }
        });
        cancelAll.Click += (_, _) => _ = Log.Guard("downloads-cancel-all", async () =>
        {
            if (Services.Engine is { } engine && await Dialogs.ConfirmAsync($"Cancel {items.Count} downloads?", "Chapters already downloaded are kept.", "Cancel downloads"))
            {
                await engine.Downloads.CancelAsync([.. items.Select(i => i.Id)]);
            }
        });
        Loaded += (_, _) => Attach();
        Unloaded += (_, _) => Detach();
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private UIElement BuildHeader()
    {
        var header = new StackPanel { Spacing = 16, Margin = new Thickness(0, 0, 0, 12), MaxWidth = 1080, HorizontalAlignment = HorizontalAlignment.Stretch };
        titleBlock.Children.Add(new TextBlock { Text = "Downloads", Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 2, 0, 0) });
        header.Children.Add(titleBlock);
        summary.Style = Res<Style>("TzSecondary");
        AutomationProperties.SetLiveSetting(summary, Microsoft.UI.Xaml.Automation.Peers.AutomationLiveSetting.Polite);
        header.Children.Add(summary);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        actions.Children.Add(pause);
        actions.Children.Add(retry);
        actions.Children.Add(cancelAll);
        var folder = new Button { Content = "Open folder" };
        folder.Click += (_, _) => _ = Log.Guard("downloads-folder", async () =>
        {
            if (Services.Engine is { } engine)
            {
                Directory.CreateDirectory(engine.Downloads.Options.Root);
                await Windows.System.Launcher.LaunchFolderPathAsync(engine.Downloads.Options.Root);
            }
        });
        actions.Children.Add(folder);
        header.Children.Add(actions);
        queueTitle.Style = Res<Style>("TzBodyStrong");
        header.Children.Add(queueTitle);
        emptyQueue.Style = Res<Style>("TzCaption");
        header.Children.Add(emptyQueue);
        return header;
    }

    private UIElement BuildFooter()
    {
        var footer = new StackPanel { Spacing = 8, Margin = new Thickness(0, 24, 0, 0), MaxWidth = 1080, HorizontalAlignment = HorizontalAlignment.Stretch };
        downloadedTitle.Style = Res<Style>("TzBodyStrong");
        footer.Children.Add(downloadedTitle);
        footer.Children.Add(downloadedPanel);
        return footer;
    }

    public void OnShown()
    {
        Attach();
        Refresh();
        _ = Log.Guard("downloads-downloaded", LoadDownloadedAsync);
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId) =>
        downloadedPanel.Children.OfType<Grid>().Where(row => row.Tag is long id && id == mangaId).ToList();

    private void Attach()
    {
        if (attached || Services.Engine is not { } engine)
        {
            return;
        }
        attached = true;
        engine.Downloads.Changed += OnChanged;
        Refresh();
        _ = Log.Guard("downloads-downloaded", LoadDownloadedAsync);
    }

    private void Detach()
    {
        if (attached && Services.Engine is { } engine)
        {
            engine.Downloads.Changed -= OnChanged;
        }
        attached = false;
    }

    private void OnChanged(object? sender, EventArgs e)
    {
        if (dirty)
        {
            return;
        }
        dirty = true;
        Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
        {
            dirty = false;
            var before = items.Count;
            Refresh();
            if (items.Count < before)
            {
                _ = Log.Guard("downloads-downloaded", LoadDownloadedAsync);
            }
        }, "downloads-changed");
    }

    private void Refresh()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var next = engine.Downloads.Items.ToList();
        var sameOrder = next.Count == items.Count && next.Select(i => i.Id).SequenceEqual(items.Select(i => i.Id));
        items = next;
        if (sameOrder && list.ItemsSource is not null)
        {
            foreach (var item in next)
            {
                if (bound.TryGetValue(item.Id, out var row) && row.Item?.Id == item.Id)
                {
                    row.Bind(item);
                }
            }
        }
        else
        {
            bound.Clear();
            list.ItemsSource = next;
        }
        var block = engine.Downloads.Block;
        var failed = next.Count(i => i.State == DownloadState.Failed);
        summary.Text = (next.Count, block) switch
        {
            (0, _) => "Downloaded chapters open instantly and read with no connection.",
            (_, DownloadBlock.Paused) => $"{next.Count} waiting · paused",
            (_, DownloadBlock.Metered) => $"{next.Count} waiting for an unmetered connection (Settings › Downloads)",
            (_, DownloadBlock.LowSpace) => $"{next.Count} waiting · less than 1 GB free on the download drive",
            _ => $"{next.Count} in the queue" + (failed > 0 ? $" · {failed} failed" : string.Empty),
        };
        pause.Content = block == DownloadBlock.Paused ? "Resume" : "Pause";
        AutomationProperties.SetName(pause, block == DownloadBlock.Paused ? "Resume downloads" : "Pause downloads");
        retry.Visibility = failed > 0 ? Visibility.Visible : Visibility.Collapsed;
        cancelAll.IsEnabled = next.Count > 0;
        emptyQueue.Visibility = next.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private async Task LoadDownloadedAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var my = ++version;
        var entries = await engine.Downloads.GetDownloadedAsync();
        if (my != version)
        {
            return;
        }
        downloadedPanel.Children.Clear();
        var series = entries.GroupBy(e => (e.MangaId, e.MangaTitle)).OrderBy(g => g.Key.MangaTitle, StringComparer.CurrentCultureIgnoreCase).ToList();
        downloadedTitle.Text = series.Count == 0
            ? "Downloaded"
            : $"Downloaded · {entries.Count} chapters · {Size(entries.Sum(e => e.Bytes))}";
        if (series.Count == 0)
        {
            downloadedPanel.Children.Add(new TextBlock { Text = "No downloaded chapters yet.", Style = Res<Style>("TzCaption") });
            return;
        }
        foreach (var group in series)
        {
            var row = new Grid { ColumnSpacing = 12, Padding = new Thickness(16, 10, 12, 10), CornerRadius = new CornerRadius(6), Background = Theme.Brush("TzCardBackground") };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var text = new StackPanel();
            text.Children.Add(new TextBlock { Text = group.Key.MangaTitle, Style = Res<Style>("TzBodyStrong"), TextTrimming = TextTrimming.CharacterEllipsis });
            text.Children.Add(new TextBlock { Text = $"{group.Count()} chapters · {Size(group.Sum(e => e.Bytes))}", Style = Res<Style>("TzCaption") });
            row.Children.Add(text);
            var open = new Button { Content = "Open" };
            AutomationProperties.SetName(open, $"Open {group.Key.MangaTitle}");
            var mangaId = group.Key.MangaId;
            row.Tag = mangaId;
            open.Click += (_, _) => _ = Log.Guard("downloads-open", () => AppNav.OpenDetailsAsync(mangaId, row));
            Grid.SetColumn(open, 1);
            row.Children.Add(open);
            var delete = new Button { Content = "Delete" };
            AutomationProperties.SetName(delete, $"Delete downloads of {group.Key.MangaTitle}");
            var chapters = group.Select(e => (e.MangaId, e.ChapterUrl)).ToList();
            delete.Click += (_, _) => _ = Log.Guard("downloads-delete", async () =>
            {
                if (await Dialogs.ConfirmAsync($"Delete {chapters.Count} downloaded chapters of {group.Key.MangaTitle}?", "The files are removed from this PC. Reading progress is kept.", "Delete"))
                {
                    await engine.Downloads.DeleteAsync(chapters);
                    await LoadDownloadedAsync();
                }
            });
            Grid.SetColumn(delete, 2);
            row.Children.Add(delete);
            downloadedPanel.Children.Add(row);
        }
    }

    private static string Size(long bytes) => bytes switch
    {
        >= 1L << 30 => $"{bytes / (double)(1L << 30):0.0} GB",
        >= 1L << 20 => $"{bytes / (double)(1L << 20):0} MB",
        _ => $"{Math.Max(1, bytes / 1024)} KB",
    };

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.InRecycleQueue || args.Item is not DownloadItem item || args.ItemContainer.ContentTemplateRoot is not Grid root)
        {
            return;
        }
        if (root.Tag is not Row row)
        {
            row = new Row(root, this);
            root.Tag = row;
        }
        foreach (var stale in bound.Where(b => b.Value == row).Select(b => b.Key).ToList())
        {
            bound.Remove(stale);
        }
        bound[item.Id] = row;
        row.Bind(item);
        AutomationProperties.SetName(args.ItemContainer, $"{item.MangaTitle}, {item.ChapterName}");
        args.Handled = true;
    }

    private async Task MoveAsync(DownloadItem item, int delta)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var index = items.FindIndex(i => i.Id == item.Id);
        if (index < 0)
        {
            return;
        }
        await engine.Downloads.MoveAsync(item.Id, Math.Clamp(index + delta, 0, items.Count - 1));
    }

    private sealed class Row
    {
        private readonly TextBlock title = new() { TextTrimming = TextTrimming.CharacterEllipsis };
        private readonly TextBlock detail = new() { TextTrimming = TextTrimming.CharacterEllipsis };
        private readonly ProgressBar progress = new() { Minimum = 0, Maximum = 1, MinHeight = 3, Margin = new Thickness(0, 6, 0, 0) };
        private readonly Button up = new() { Content = new FontIcon { Glyph = "\uE70E", FontSize = 12 } };
        private readonly Button down = new() { Content = new FontIcon { Glyph = "\uE70D", FontSize = 12 } };
        private readonly Button action = new() { MinWidth = 88 };

        public Row(Grid root, DownloadsPage page)
        {
            root.ColumnSpacing = 8;
            root.Padding = new Thickness(16, 10, 12, 10);
            root.CornerRadius = new CornerRadius(6);
            root.Background = Theme.Brush("TzCardBackground");
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            title.Style = Res<Style>("TzBodyStrong");
            detail.Style = Res<Style>("TzCaption");
            var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(title);
            text.Children.Add(detail);
            text.Children.Add(progress);
            root.Children.Add(text);
            ToolTipService.SetToolTip(up, "Move up");
            ToolTipService.SetToolTip(down, "Move down");
            AutomationProperties.SetName(up, "Move up");
            AutomationProperties.SetName(down, "Move down");
            up.Click += (_, _) => Run(page, item => page.MoveAsync(item, -1));
            down.Click += (_, _) => Run(page, item => page.MoveAsync(item, 1));
            action.Click += (_, _) => Run(page, async item =>
            {
                if (Services.Engine is not { } engine)
                {
                    return;
                }
                if (item.State == DownloadState.Failed)
                {
                    await engine.Downloads.RetryAsync([item.Id]);
                }
                else
                {
                    await engine.Downloads.CancelAsync([item.Id]);
                }
            });
            foreach (var (button, column) in new[] { (up, 1), (down, 2), (action, 3) })
            {
                button.VerticalAlignment = VerticalAlignment.Center;
                Grid.SetColumn(button, column);
                root.Children.Add(button);
            }
        }

        public DownloadItem? Item { get; private set; }

        private void Run(DownloadsPage page, Func<DownloadItem, Task> work)
        {
            if (Item is { } item)
            {
                _ = Log.Guard("downloads-row", () => work(item));
            }
        }

        public void Bind(DownloadItem item)
        {
            Item = item;
            title.Text = item.MangaTitle;
            var chapter = item.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(item.ChapterNumber) : item.ChapterName;
            detail.Text = item.State switch
            {
                DownloadState.Failed => $"{chapter} · failed: {item.Error ?? "unknown error"}",
                DownloadState.Downloading when item.PagesTotal > 0 => $"{chapter} · page {item.PagesDone} of {item.PagesTotal}",
                DownloadState.Downloading => $"{chapter} · starting",
                _ => $"{chapter} · waiting",
            };
            detail.Foreground = item.State == DownloadState.Failed ? Theme.Brush("TzDanger") : Theme.Brush("TzTextSecondary");
            progress.Visibility = item.State == DownloadState.Downloading ? Visibility.Visible : Visibility.Collapsed;
            progress.IsIndeterminate = item.PagesTotal == 0;
            progress.Value = item.PagesTotal > 0 ? (double)item.PagesDone / item.PagesTotal : 0;
            action.Content = item.State == DownloadState.Failed ? "Retry" : "Cancel";
            AutomationProperties.SetName(action, $"{action.Content} {item.MangaTitle} {chapter}");
        }
    }
}
