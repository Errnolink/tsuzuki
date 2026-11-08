using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Automation.Peers;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Next;
using Tsuzuki.App.Pages;
using Windows.System;

namespace Tsuzuki.App.Design;

public sealed record PaletteItem(string Title, string Kind, string Glyph, Action<FrameworkElement?> Run, string? CoverUrl = null, long Source = 0);

public static class PaletteMatch
{
    public static int Score(string query, string candidate)
    {
        if (query.Length == 0)
        {
            return 0;
        }
        var index = candidate.IndexOf(query, StringComparison.OrdinalIgnoreCase);
        if (index == 0)
        {
            return 10_000 - (candidate.Length - query.Length);
        }
        if (index > 0)
        {
            var wordStart = WordStart(candidate, index);
            return (wordStart ? 8_000 : 6_000) - index * 4 - (candidate.Length - query.Length);
        }
        var score = 0;
        var q = 0;
        var previous = -2;
        var firstMatch = -1;
        for (var i = 0; i < candidate.Length && q < query.Length; i++)
        {
            if (char.ToLowerInvariant(candidate[i]) != char.ToLowerInvariant(query[q]))
            {
                continue;
            }
            if (firstMatch < 0)
            {
                firstMatch = i;
            }
            score += 10;
            if (WordStart(candidate, i))
            {
                score += 25;
            }
            if (i == previous + 1)
            {
                score += 15;
            }
            else if (previous >= 0)
            {
                score -= Math.Min(i - previous - 1, 6);
            }
            previous = i;
            q++;
        }
        if (q < query.Length)
        {
            return -1;
        }
        if (firstMatch == 0)
        {
            score += 200;
        }
        return Math.Max(1, 1_000 + score - firstMatch * 2 - candidate.Length / 4);
    }

    private static bool WordStart(string text, int index) =>
        index == 0 || !char.IsLetterOrDigit(text[index - 1]) || (char.IsUpper(text[index]) && char.IsLower(text[index - 1]));
}

public sealed class CommandPalette
{
    private const int MaxTitles = 40;
    private const int MaxRows = 60;

    private static readonly DataTemplate RowTemplate = (DataTemplate)XamlReader.Load(
        """<DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Grid Height="44" /></DataTemplate>""");

    private readonly MainWindow window;
    private readonly Grid host;
    private readonly Border scrim = new();
    private readonly Border card;
    private readonly TextBox input;
    private readonly ListView list;
    private readonly TextBlock count;
    private readonly List<PaletteItem> fixedItems;
    private IReadOnlyList<PaletteItem> titles = [];
    private int openVersion;
    private bool open;
    private DependencyObject? restoreFocus;

    public CommandPalette(MainWindow window, Grid host)
    {
        this.window = window;
        this.host = host;
        fixedItems = BuildFixedItems();
        var res = Application.Current.Resources;

        scrim.Background = Theme.Brush("TzScrim");
        scrim.Tapped += (_, _) => Close(restore: true);
        host.Children.Add(scrim);

        var glyph = new FontIcon
        {
            Glyph = Icons.Search,
            FontFamily = (FontFamily)res["TzIconFont"],
            FontSize = 18,
            Foreground = Theme.Brush("TzTextTertiary"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(18, 0, 0, 0),
        };
        input = new TextBox
        {
            PlaceholderText = "Search your library, pages and commands",
            FontSize = 16,
            BorderThickness = new Thickness(0),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            VerticalAlignment = VerticalAlignment.Center,
            Padding = new Thickness(10, 14, 16, 14),
            IsSpellCheckEnabled = false,
        };
        var clear = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        foreach (var key in new[]
                 {
                     "TextControlBackground", "TextControlBackgroundPointerOver", "TextControlBackgroundFocused",
                     "TextControlBorderBrush", "TextControlBorderBrushPointerOver", "TextControlBorderBrushFocused",
                 })
        {
            input.Resources[key] = clear;
        }
        AutomationProperties.SetName(input, "Command palette");
        input.TextChanged += (_, _) => Filter();
        input.PreviewKeyDown += OnInputKey;

        var inputRow = new Grid();
        inputRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        inputRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        inputRow.Children.Add(glyph);
        Grid.SetColumn(input, 1);
        inputRow.Children.Add(input);

        list = new ListView
        {
            SelectionMode = ListViewSelectionMode.Single,
            IsItemClickEnabled = true,
            ItemTemplate = RowTemplate,
            ItemContainerTransitions = new TransitionCollection(),
            Padding = new Thickness(0, 6, 0, 6),
            MaxHeight = 440,
            IsTabStop = false,
        };
        AutomationProperties.SetName(list, "Command palette results");
        list.ContainerContentChanging += OnContainerChanging;
        list.ItemClick += (_, e) =>
        {
            if (e.ClickedItem is PaletteItem item)
            {
                Invoke(item);
            }
        };

        var hint = new TextBlock { Text = "Up and Down to move · Enter to open · Esc to close", Style = (Style)res["TzCaption"], Foreground = Theme.Brush("TzTextTertiary") };
        count = new TextBlock { Style = (Style)res["TzCaption"], Foreground = Theme.Brush("TzTextTertiary"), HorizontalAlignment = HorizontalAlignment.Right };
        AutomationProperties.SetLiveSetting(count, AutomationLiveSetting.Polite);
        var footer = new Grid { Padding = new Thickness(18, 8, 18, 10), BorderBrush = Theme.Brush("TzStroke"), BorderThickness = new Thickness(0, 1, 0, 0) };
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.Children.Add(hint);
        Grid.SetColumn(count, 1);
        footer.Children.Add(count);

        var body = new Grid();
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.Children.Add(inputRow);
        var listFrame = new Border { BorderBrush = Theme.Brush("TzStroke"), BorderThickness = new Thickness(0, 1, 0, 0), Child = list };
        Grid.SetRow(listFrame, 1);
        body.Children.Add(listFrame);
        Grid.SetRow(footer, 2);
        body.Children.Add(footer);

        card = new Border
        {
            Width = 640,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(76, 60, 0, 24),
            CornerRadius = (CornerRadius)res["TzCardRadius"],
            Background = Theme.Brush("TzElevated"),
            BorderBrush = Theme.Brush("TzStrokeStrong"),
            BorderThickness = new Thickness(1),
            Shadow = new ThemeShadow(),
            Translation = new Vector3(0, 0, 32),
            Child = body,
        };
        card.KeyDown += (_, e) =>
        {
            if (e.Key == VirtualKey.Escape)
            {
                e.Handled = true;
                Close(restore: true);
            }
        };
        host.Children.Add(card);
        window.ReaderLayer.RegisterPropertyChangedCallback(UIElement.VisibilityProperty, (_, _) =>
        {
            if (open && Ui.ReaderOpen)
            {
                Close(restore: false);
            }
        });
    }

    public bool IsOpen => open;

    public void Toggle()
    {
        if (open)
        {
            Close(restore: true);
        }
        else
        {
            Open();
        }
    }

    public void Open(string text = "")
    {
        if (open || Ui.ReaderOpen || window.SheetOpen)
        {
            return;
        }
        open = true;
        var my = ++openVersion;
        restoreFocus = FocusManager.GetFocusedElement(host.XamlRoot) as DependencyObject;
        titles = [];
        input.Text = text;
        input.SelectionStart = text.Length;
        Filter();
        host.Visibility = Visibility.Visible;
        var cardVisual = Motion.VisualOf(card);
        var scrimVisual = Motion.VisualOf(scrim);
        var c = cardVisual.Compositor;
        scrimVisual.StartAnimation("Opacity", Motion.Scalar(c, 0, 1, Motion.Short4, Curve.StandardDecelerate));
        cardVisual.StartAnimation("Opacity", Motion.Scalar(c, 0, 1, Motion.Short3, Curve.StandardDecelerate));
        if (!Motion.Reduced)
        {
            cardVisual.StartAnimation("Translation", Motion.Vector3(c, new(0, -10, 32), new(0, 0, 32), Motion.Medium1, Curve.EmphasizedDecelerate));
        }
        if (!input.Focus(FocusState.Programmatic))
        {
            Ui.Enqueue(host.DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
            {
                if (open)
                {
                    input.Focus(FocusState.Programmatic);
                }
            }, "palette-focus");
        }
        _ = Log.Guard("palette-library", () => LoadTitlesAsync(my));
    }

    public void Close(bool restore)
    {
        if (!open)
        {
            return;
        }
        open = false;
        var my = ++openVersion;
        var cardVisual = Motion.VisualOf(card);
        var scrimVisual = Motion.VisualOf(scrim);
        var c = cardVisual.Compositor;
        _ = Motion.Run(c, _ =>
        {
            scrimVisual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short3, Curve.StandardAccelerate));
            cardVisual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short2, Curve.StandardAccelerate));
        }).ContinueWith(_ => Ui.Enqueue(host.DispatcherQueue, () =>
        {
            if (my == openVersion && !open)
            {
                host.Visibility = Visibility.Collapsed;
                list.ItemsSource = null;
                titles = [];
            }
        }, "palette-close"), TaskScheduler.Default);
        if (restore && restoreFocus is not null)
        {
            _ = FocusManager.TryFocusAsync(restoreFocus, FocusState.Programmatic);
        }
        restoreFocus = null;
    }

    private async Task LoadTitlesAsync(int my)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var library = await engine.Library.GetLibraryAsync();
        if (my != openVersion || !open)
        {
            return;
        }
        titles = library
            .Select(e => new PaletteItem(e.Title, Services.Engine?.Sources.DisplayName(e.Source) ?? "Title", Icons.BookOpen, origin => _ = AppNav.OpenDetailsAsync(e.MangaId, origin), e.ThumbnailUrl, e.Source))
            .ToList();
        if (input.Text.Trim().Length > 0)
        {
            Filter();
        }
    }

    private List<PaletteItem> BuildFixedItems() =>
    [
        Navigate("Home", Icons.Sparkles, "home"),
        Navigate("Library", Icons.Library, "library"),
        Navigate("Updates", Icons.Bell, "updates"),
        Navigate("History", Icons.History, "history"),
        Navigate("Browse", Icons.Compass, "browse"),
        Navigate("Settings", Icons.Settings, "settings"),
        new("Check for new chapters", "Command", Icons.Refresh, origin => _ = Log.Guard("palette-check", CheckForChaptersAsync)),
        new("Pair a phone", "Command", Icons.Smartphone, _ => DevicesPanel.Show()),
        new("Back up now", "Command", Icons.HardDrive, origin => _ = Log.Guard("palette-backup", SettingsPage.BackupAsync)),
        new("Restore backup…", "Command", Icons.RotateCcw, origin => _ = Log.Guard("palette-restore", SettingsPage.RestoreAsync)),
    ];

    private PaletteItem Navigate(string label, string glyph, string key) =>
        new(label, "Go to", glyph, origin => _ = Log.Guard("palette-nav", () => window.NavigateRootAsync(key)));

    private static async Task CheckForChaptersAsync()
    {
        var result = await LibraryUpdater.RunAsync();
        Toasts.Show(result.NewChapters > 0 ? $"{result.NewChapters} new chapters" : "You're up to date",
            result.Failed > 0 ? $"{result.Failed} titles could not be checked" : $"Checked {result.Checked} titles", Icons.Sparkles);
    }

    private void Filter()
    {
        var query = input.Text.Trim();
        List<PaletteItem> results;
        if (query.Length == 0)
        {
            results = [.. fixedItems];
        }
        else
        {
            var matchedTitles = titles
                .Select(t => (Item: t, Score: PaletteMatch.Score(query, t.Title)))
                .Where(m => m.Score > 0)
                .OrderByDescending(m => m.Score)
                .ThenBy(m => m.Item.Title, StringComparer.CurrentCultureIgnoreCase)
                .Take(MaxTitles);
            var matchedFixed = fixedItems
                .Select(t => (Item: t, Score: PaletteMatch.Score(query, t.Title) + 50))
                .Where(m => m.Score > 50);
            var scopes = window.Shell.SearchScopes;
            results = matchedFixed.Concat(matchedTitles)
                .OrderByDescending(m => m.Score)
                .Take(MaxRows - scopes.Count)
                .Select(m => m.Item)
                .ToList();
            foreach (var scope in scopes)
            {
                var request = new SearchRequest(query, scope);
                results.Add(new PaletteItem(request.ToString(), "Search", Icons.Search, _ => window.Shell.SubmitSearch(request)));
            }
        }
        list.ItemsSource = results;
        list.SelectedIndex = results.Count > 0 ? 0 : -1;
        count.Text = results.Count == 1 ? "1 result" : $"{results.Count} results";
        if (FrameworkElementAutomationPeer.CreatePeerForElement(count) is { } countPeer)
        {
            countPeer.RaiseAutomationEvent(AutomationEvents.LiveRegionChanged);
        }
    }

    private void OnInputKey(object sender, KeyRoutedEventArgs e)
    {
        switch (e.Key)
        {
            case VirtualKey.Down:
                Move(1);
                e.Handled = true;
                break;
            case VirtualKey.Up:
                Move(-1);
                e.Handled = true;
                break;
            case VirtualKey.PageDown:
                Move(8);
                e.Handled = true;
                break;
            case VirtualKey.PageUp:
                Move(-8);
                e.Handled = true;
                break;
            case VirtualKey.Enter:
                if (list.SelectedItem is PaletteItem item)
                {
                    Invoke(item);
                }
                e.Handled = true;
                break;
            case VirtualKey.Escape:
                Close(restore: true);
                e.Handled = true;
                break;
        }
    }

    private void Move(int delta)
    {
        var total = list.Items.Count;
        if (total == 0)
        {
            return;
        }
        var current = list.SelectedIndex < 0 ? 0 : list.SelectedIndex;
        var next = Math.Clamp(current + delta, 0, total - 1);
        if (Math.Abs(delta) == 1 && current + delta >= total)
        {
            next = 0;
        }
        else if (Math.Abs(delta) == 1 && current + delta < 0)
        {
            next = total - 1;
        }
        list.SelectedIndex = next;
        list.ScrollIntoView(list.Items[next]);
        if (list.ContainerFromIndex(next) is ListViewItem container
            && FrameworkElementAutomationPeer.FromElement(container) is { } peer)
        {
            peer.RaiseAutomationEvent(AutomationEvents.SelectionItemPatternOnElementSelected);
        }
    }

    private void Invoke(PaletteItem item)
    {
        var origin = list.ContainerFromItem(item) is ListViewItem { ContentTemplateRoot: Grid { Tag: PaletteRow row } } ? row.Origin : null;
        Close(restore: false);
        item.Run(origin);
    }

    private static void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.InRecycleQueue || args.Item is not PaletteItem item || args.ItemContainer.ContentTemplateRoot is not Grid root)
        {
            return;
        }
        if (root.Tag is not PaletteRow row)
        {
            row = new PaletteRow(root);
            root.Tag = row;
        }
        row.Bind(item);
        AutomationProperties.SetName(args.ItemContainer, $"{item.Title}, {item.Kind}");
        args.Handled = true;
    }

    private sealed class PaletteRow
    {
        private readonly CoverImage cover = new() { DecodeWidth = 28 };
        private readonly Border coverFrame;
        private readonly Grid root;
        private readonly FontIcon icon;
        private readonly TextBlock title;
        private readonly TextBlock kind;

        public PaletteRow(Grid root)
        {
            this.root = root;
            var res = Application.Current.Resources;
            root.ColumnSpacing = 12;
            root.Padding = new Thickness(6, 0, 6, 0);
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            coverFrame = new Border { Width = 24, Height = 36, CornerRadius = new CornerRadius(4), Child = cover, VerticalAlignment = VerticalAlignment.Center };
            icon = new FontIcon { FontFamily = (FontFamily)res["TzIconFont"], FontSize = 18, Foreground = Theme.Brush("TzTextSecondary"), VerticalAlignment = VerticalAlignment.Center };
            title = new TextBlock { Style = (Style)res["TzBody"], VerticalAlignment = VerticalAlignment.Center, MaxLines = 1 };
            kind = new TextBlock { Style = (Style)res["TzCaption"], Foreground = Theme.Brush("TzTextTertiary"), VerticalAlignment = VerticalAlignment.Center };
            root.Children.Add(coverFrame);
            root.Children.Add(icon);
            Grid.SetColumn(title, 1);
            root.Children.Add(title);
            Grid.SetColumn(kind, 2);
            root.Children.Add(kind);
        }

        public FrameworkElement Origin => coverFrame.Visibility == Visibility.Visible ? coverFrame : root;

        public void Bind(PaletteItem item)
        {
            title.Text = item.Title;
            kind.Text = item.Kind;
            var hasCover = item.CoverUrl is not null || item.Glyph == Icons.BookOpen;
            coverFrame.Visibility = hasCover ? Visibility.Visible : Visibility.Collapsed;
            icon.Visibility = hasCover ? Visibility.Collapsed : Visibility.Visible;
            icon.Glyph = item.Glyph;
            if (hasCover)
            {
                cover.SourceId = item.Source;
                if (cover.Url != item.CoverUrl)
                {
                    cover.Url = item.CoverUrl;
                }
            }
        }
    }
}
