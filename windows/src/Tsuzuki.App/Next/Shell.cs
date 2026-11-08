using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core.Library;
using VirtualKey = Windows.System.VirtualKey;

namespace Tsuzuki.App.Next;

public sealed record SearchScope(string Label, long SourceId);

public sealed record SearchRequest(string Text, SearchScope? Scope)
{
    public override string ToString() => Scope is null ? $"Show “{Text}” in your library" : $"Search {Scope.Label} for “{Text}”";
}

public sealed class Shell
{
    private const double CompactBelow = 1008;

    private readonly MainWindow window;
    private readonly TitleBar titleBar;
    private readonly Grid searchField = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
    private readonly AutoSuggestBox search;
    private readonly Border hint;
    private readonly Button sync;
    private readonly FontIcon syncGlyph = ShellTheme.Glyph("\uE895", 14);
    private readonly Ellipse syncDot = new() { Width = 6, Height = 6, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom, Visibility = Visibility.Collapsed };
    private readonly TextBlock syncText = ShellTheme.Text("Starting", "CaptionTextBlockStyle");
    private bool narrow;
    private bool narrowExpanded;
    private Tsuzuki.Presentation.ShellViewModel? model;

    public Shell(MainWindow window, TitleBar titleBar)
    {
        this.window = window;
        this.titleBar = titleBar;

        titleBar.Title = "Tsuzuki";
        titleBar.IconSource = new ImageIconSource { ImageSource = new BitmapImage(new Uri("ms-appx:///Assets/Tsuzuki.png")) };
        titleBar.IsPaneToggleButtonVisible = true;
        titleBar.PaneToggleRequested += (_, _) => TogglePane();
        EventHandler? initialFocus = null;
        initialFocus = (_, _) =>
        {
            window.Nav.Changed -= initialFocus;
            var timer = titleBar.DispatcherQueue.CreateTimer();
            timer.Interval = TimeSpan.FromMilliseconds(600);
            timer.IsRepeating = false;
            timer.Tick += (_, _) =>
            {
                if (!window.Nav.CanGoBack && FocusManager.FindFirstFocusableElement(window.Nav.Top ?? window.Nav.Host) is Control first)
                {
                    first.Focus(FocusState.Programmatic);
                }
            };
            timer.Start();
        };
        window.Nav.Changed += initialFocus;

        search = new AutoSuggestBox
        {
            PlaceholderText = "Search library, titles, commands",
            QueryIcon = new SymbolIcon(Symbol.Find),
            VerticalAlignment = VerticalAlignment.Center,
        };
        AutomationProperties.SetName(search, "Search library, titles, commands");
        AutomationProperties.SetHelpText(search, "Type to filter your library. Press Enter to open the command palette.");
        AutomationProperties.SetAcceleratorKey(search, "Control+K");
        hint = new Border
        {
            CornerRadius = new CornerRadius(4),
            BorderThickness = new Thickness(1),
            BorderBrush = ShellTheme.Brush("ControlStrokeColorDefaultBrush"),
            Background = ShellTheme.Brush("SubtleFillColorSecondaryBrush"),
            Padding = new Thickness(6, 1, 6, 2),
            Margin = new Thickness(0, 0, 40, 0),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center,
            IsHitTestVisible = false,
            Child = new TextBlock { Text = "Ctrl+K", Style = ShellTheme.Style("CaptionTextBlockStyle"), Foreground = ShellTheme.Brush("TextFillColorTertiaryBrush") },
        };
        AutomationProperties.SetAccessibilityView(hint, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        searchField.Children.Add(search);
        searchField.Children.Add(hint);
        var searchHost = new Grid { HorizontalAlignment = HorizontalAlignment.Stretch };
        searchHost.Children.Add(searchField);
        searchField.Width = 440;
        titleBar.SizeChanged += (_, e) =>
        {
            searchField.Width = Math.Clamp(e.NewSize.Width - 760, 200, 480);
            UpdateHint();
        };
        search.TextChanged += (box, args) =>
        {
            UpdateHint();
            if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
            {
                window.RaiseSearchChanged(search.Text);
                if (search.Text.Trim().Length > 0 && (window.Nav.CurrentRoot != "library" || window.Nav.CanGoBack))
                {
                    _ = Log.Guard("next-search", () => window.NavigateRootAsync("library"));
                }
            }
        };
        search.GotFocus += (_, _) => UpdateHint();
        search.LostFocus += (_, _) => UpdateHint();
        search.QuerySubmitted += (_, args) => window.OpenPalette(args.QueryText.Trim());
        search.KeyDown += (_, e) =>
        {
            if (e.Key != VirtualKey.Escape)
            {
                return;
            }
            e.Handled = true;
            if (search.Text.Length > 0)
            {
                search.Text = string.Empty;
                window.RaiseSearchChanged(string.Empty);
            }
            window.Nav.Host.Focus(FocusState.Programmatic);
        };
        titleBar.Content = searchHost;

        var syncIcon = new Grid { Width = 16, Height = 16, VerticalAlignment = VerticalAlignment.Center };
        syncIcon.Children.Add(syncGlyph);
        syncIcon.Children.Add(syncDot);
        syncDot.Fill = ShellTheme.Brush("SystemFillColorSuccessBrush");
        var syncContent = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        syncContent.Children.Add(syncIcon);
        syncText.VerticalAlignment = VerticalAlignment.Center;
        syncContent.Children.Add(syncText);
        sync = ShellTheme.Subtle(new Button
        {
            Height = 32,
            Padding = new Thickness(10, 0, 10, 0),
            CornerRadius = new CornerRadius(4),
            VerticalAlignment = VerticalAlignment.Center,
            Content = syncContent,
        });
        sync.Resources["ButtonForeground"] = ShellTheme.Brush("TextFillColorPrimaryBrush");
        sync.Click += (_, _) => DevicesPanel.Show();
        var right = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = VerticalAlignment.Center };
        right.Children.Add(sync);
        titleBar.RightHeader = right;

        Pane.Navigate += (_, key) => _ = Log.Guard("next-nav", () => window.NavigateRootAsync(key));
        Pane.SourceInvoked += (_, id) => _ = Log.Guard("next-source", () => AppNav.OpenBrowseAsync(id));
        Pane.DevicesInvoked += (_, _) => DevicesPanel.Show();
        ApplyPane();

        AppHost.EngineReady += (_, _) => Ui.Enqueue(window.DispatcherQueue, OnEngineReady, "next-shell");
        Ui.ReaderClosed += (_, _) => model?.ReaderClosed();
        UpdateHint();
    }

    public NavPane Pane { get; } = new();

    public string SearchText => search.Text;

    public IReadOnlyList<SearchScope> SearchScopes => Services.Engine?.Sources.All is { Count: > 0 } all
        ? [.. all.Select(s => new SearchScope(BrowsePage.SourceLabel(s, all), s.Id))]
        : [];

    public void SubmitSearch(SearchRequest request)
    {
        if (request.Text.Length == 0)
        {
            return;
        }
        if (request.Scope is not { } scope)
        {
            search.Text = request.Text;
            window.RaiseSearchChanged(request.Text);
            _ = Log.Guard("search", () => window.NavigateRootAsync("library"));
            return;
        }
        _ = Log.Guard("search", async () =>
        {
            await window.NavigateRootAsync("browse");
            if (window.Nav.Root("browse") is BrowsePage browse)
            {
                browse.Search(request.Text, scope.SourceId);
            }
        });
    }

    public void ClearSearch() => search.Text = string.Empty;

    public void FocusSearch() => search.Focus(FocusState.Keyboard);

    public void Select(string key) => Pane.Select(key);

    public void WindowWidthChanged(double width)
    {
        var next = width < CompactBelow;
        if (next == narrow)
        {
            return;
        }
        narrow = next;
        narrowExpanded = false;
        ApplyPane();
    }

    public void UpdateBreadcrumb(NavHost nav)
    {
        if (!nav.CanGoBack || nav.CurrentRoot is not { } root)
        {
            titleBar.Subtitle = string.Empty;
            return;
        }
        var parent = Pane.Label(root);
        titleBar.Subtitle = nav.Top is ITitledPage { Title: { Length: > 0 } title }
            ? $"{parent} \u203A {(title.Length > 48 ? title[..47].TrimEnd() + "\u2026" : title)}"
            : parent;
    }

    public void SetSync(SyncChipState state, string text, string description, string status)
    {
        syncGlyph.Glyph = state switch
        {
            SyncChipState.Off => "\uE7BA",
            SyncChipState.Error => "\uE783",
            SyncChipState.Starting => "\uE895",
            _ => "\uE8EA",
        };
        syncGlyph.Foreground = ShellTheme.Brush(state switch
        {
            SyncChipState.Off => "SystemFillColorCautionBrush",
            SyncChipState.Error => "SystemFillColorCriticalBrush",
            SyncChipState.Starting => "TextFillColorTertiaryBrush",
            _ => "TextFillColorPrimaryBrush",
        });
        syncDot.Visibility = state == SyncChipState.Synced ? Visibility.Visible : Visibility.Collapsed;
        syncText.Text = text;
        syncText.Foreground = ShellTheme.Brush(state == SyncChipState.Error ? "SystemFillColorCriticalBrush" : "TextFillColorSecondaryBrush");
        AutomationProperties.SetName(sync, "Phone sync: " + description);
        ToolTipService.SetToolTip(sync, description);
        Pane.SetStatus(state, status, description);
    }

    private void UpdateHint() =>
        hint.Visibility = search.Text.Length == 0 && search.FocusState == FocusState.Unfocused && searchField.Width >= 360 ? Visibility.Visible : Visibility.Collapsed;

    private void TogglePane()
    {
        if (Ui.ReaderOpen)
        {
            _ = Reader.ReaderPage.CloseCurrentAsync();
            return;
        }
        if (narrow)
        {
            narrowExpanded = !narrowExpanded;
        }
        else
        {
            Services.Settings.NextPaneCompact = !Services.Settings.NextPaneCompact;
            Services.Settings.Save();
        }
        ApplyPane();
    }

    private void ApplyPane()
    {
        var compact = narrow ? !narrowExpanded : Services.Settings.NextPaneCompact;
        Pane.Compact = compact;
        Pane.Width = compact ? NavPane.CompactWidth : NavPane.ExpandedWidth;
    }

    private void OnEngineReady()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        ShowSources(engine);
        engine.Extensions.Changed += (_, _) => Ui.Enqueue(window.DispatcherQueue, () => ShowSources(engine), "next-sources");
        engine.Downloads.Changed += (_, _) => Ui.Enqueue(window.DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () => Pane.SetDownloads(engine.Downloads.Count), "next-downloads");
        Pane.SetDownloads(engine.Downloads.Count);
        model = new Tsuzuki.Presentation.ShellViewModel(engine.Library, new DispatcherUi(window.DispatcherQueue), () => Ui.ReaderOpen);
        model.Changed += (_, _) => Pane.SetUpdates(model.UnreadUpdates);
    }

    private void ShowSources(Tsuzuki.Core.Engine engine)
    {
        var all = engine.Sources.All;
        Pane.SetSources([.. all.Select(s => (s.Id, BrowsePage.SourceLabel(s, all)))]);
    }
}
