using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Next;

public sealed partial class NavPane : Grid
{
    public const double ExpandedWidth = 280;
    public const double CompactWidth = 48;

    private const double ItemHeight = 36;
    private static readonly TimeSpan IndicatorSlide = TimeSpan.FromMilliseconds(167);

    private sealed record PaneItem(string Key, string Label, Button Button, TextBlock Text);

    private readonly Dictionary<string, PaneItem> items = [];
    private readonly List<(long Id, PaneItem Item)> sourceItems = [];
    private readonly StackPanel top = new();
    private readonly StackPanel footer = new();
    private readonly StackPanel sources = new() { Visibility = Visibility.Collapsed };
    private readonly ScrollViewer scroller;
    private readonly Border indicator;
    private readonly Button chevron;
    private readonly FontIcon chevronIcon;
    private readonly InfoBadge badge = new() { Visibility = Visibility.Collapsed, VerticalAlignment = VerticalAlignment.Center };
    private readonly InfoBadge downloadsBadge = new() { Visibility = Visibility.Collapsed, VerticalAlignment = VerticalAlignment.Center };
    private readonly Ellipse statusDot = new() { Width = 6, Height = 6, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
    private readonly TextBlock statusText = ShellTheme.Text(string.Empty, "CaptionTextBlockStyle");
    private readonly Grid statusRow;
    private string? selected;
    private long? selectedSource;
    private bool compact;
    private bool sourcesOpen;
    private int updates;
    private FrameworkElement? indicatorTarget;
    private Vector3 indicatorAt = new(float.NaN);

    public event EventHandler<string>? Navigate;
    public event EventHandler<long>? SourceInvoked;
    public event EventHandler? DevicesInvoked;

    public NavPane()
    {
        RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        Padding = new Thickness(0, 4, 0, 0);
        AutomationProperties.SetName(this, "Navigation");
        AutomationProperties.SetLandmarkType(this, Microsoft.UI.Xaml.Automation.Peers.AutomationLandmarkType.Navigation);

        top.XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled;
        footer.XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled;
        top.TabFocusNavigation = KeyboardNavigationMode.Once;
        footer.TabFocusNavigation = KeyboardNavigationMode.Once;

        AddItem(top, "home", "\uE80F", "Home");
        AddItem(top, "library", "\uE8F1", "Library");
        var updatesItem = AddItem(top, "updates", "\uEA8F", "Updates");
        Grid.SetColumn(badge, 2);
        badge.Margin = new Thickness(0, 0, 12, 0);
        ((Grid)updatesItem.Button.Content).Children.Add(badge);
        AddItem(top, "history", "\uE81C", "History");
        var downloadsItem = AddItem(top, "downloads", "\uE896", "Downloads");
        Grid.SetColumn(downloadsBadge, 2);
        downloadsBadge.Margin = new Thickness(0, 0, 12, 0);
        ((Grid)downloadsItem.Button.Content).Children.Add(downloadsBadge);

        var browse = MakeItem("browse", "\uE774", "Browse");
        chevronIcon = ShellTheme.Glyph("\uE70D", 12);
        chevron = ShellTheme.Subtle(new Button
        {
            Width = 32,
            Height = 28,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(4),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 8, 0),
            Content = chevronIcon,
        });
        chevron.Click += (_, _) => SetSourcesOpen(!sourcesOpen);
        var browseRow = new Grid();
        browseRow.Children.Add(browse.Button);
        browseRow.Children.Add(chevron);
        top.Children.Add(browseRow);
        top.Children.Add(sources);
        UpdateChevron();
        AddItem(top, "extensions", "\uEA86", "Extensions");

        var devices = MakeItem("devices", "\uE8EA", "Devices");
        devices.Button.Click -= OnItemClick;
        devices.Button.Click += (_, _) => DevicesInvoked?.Invoke(this, EventArgs.Empty);
        footer.Children.Add(devices.Button);
        AddItem(footer, "settings", "\uE713", "Settings");

        statusRow = new Grid { Margin = new Thickness(4, 6, 12, 12), MinHeight = 20 };
        statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
        statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        statusText.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");
        statusText.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(statusText, 1);
        statusRow.Children.Add(statusDot);
        statusRow.Children.Add(statusText);

        var footerHost = new StackPanel();
        footerHost.Children.Add(footer);
        footerHost.Children.Add(new Border { Height = 1, Margin = new Thickness(12, 4, 12, 0), Background = ShellTheme.Brush("DividerStrokeColorDefaultBrush") });
        footerHost.Children.Add(statusRow);
        Grid.SetRow(footerHost, 1);

        scroller = new ScrollViewer
        {
            Content = top,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
        };
        scroller.ViewChanged += (_, _) => PlaceIndicator(animate: false);

        indicator = new Border
        {
            Width = 3,
            Height = 16,
            CornerRadius = new CornerRadius(1.5),
            Background = ShellTheme.Brush("AccentFillColorDefaultBrush"),
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Top,
            IsHitTestVisible = false,
            Opacity = 0,
        };
        Grid.SetRowSpan(indicator, 2);

        Children.Add(scroller);
        Children.Add(footerHost);
        Children.Add(indicator);
        LayoutUpdated += (_, _) => PlaceIndicator(animate: false);
        SetStatus(SyncChipState.Starting, "Starting sync", "starting");
    }

    public bool Compact
    {
        get => compact;
        set
        {
            if (compact == value)
            {
                return;
            }
            compact = value;
            ApplyCompact();
        }
    }

    public string Label(string key) => items.TryGetValue(key, out var item) ? item.Label : key;

    public void Select(string key)
    {
        selected = key;
        if (key == "browse" && Services.Settings.BrowseSource is { } source)
        {
            selectedSource = source;
        }
        ApplySelection(animate: true);
    }

    public void SelectSource(long sourceId)
    {
        selectedSource = sourceId;
        selected = "browse";
        ApplySelection(animate: true);
    }

    public void SetSources(IReadOnlyList<(long Id, string Label)> list)
    {
        sources.Children.Clear();
        sourceItems.Clear();
        foreach (var (id, label) in list)
        {
            var text = ShellTheme.Text(label, "BodyTextBlockStyle");
            text.VerticalAlignment = VerticalAlignment.Center;
            text.Margin = new Thickness(40, 0, 12, 0);
            var button = ItemButton(text, label);
            AutomationProperties.SetName(button, $"Browse {label}");
            button.Click += (_, _) => SourceInvoked?.Invoke(this, id);
            sources.Children.Add(button);
            sourceItems.Add((id, new PaneItem("source", label, button, text)));
        }
        chevron.Visibility = list.Count > 0 && !compact ? Visibility.Visible : Visibility.Collapsed;
        ApplySelection(animate: false);
    }

    public void SetDownloads(int count)
    {
        downloadsBadge.Value = count;
        downloadsBadge.Visibility = count > 0 ? Visibility.Visible : Visibility.Collapsed;
        var button = items["downloads"].Button;
        AutomationProperties.SetName(button, count > 0 ? $"Downloads, {count} in the queue" : "Downloads");
        if (compact)
        {
            ToolTipService.SetToolTip(button, count > 0 ? $"Downloads · {count} in the queue" : "Downloads");
        }
    }

    public void SetUpdates(int count)
    {
        updates = count;
        badge.Value = count;
        badge.Visibility = count > 0 ? Visibility.Visible : Visibility.Collapsed;
        var button = items["updates"].Button;
        AutomationProperties.SetName(button, count > 0 ? $"Updates, {count} unread" : "Updates");
        if (compact)
        {
            ToolTipService.SetToolTip(button, count > 0 ? $"Updates · {count} unread" : "Updates");
        }
    }

    public void SetStatus(SyncChipState state, string status, string description)
    {
        statusText.Text = status;
        statusDot.Fill = ShellTheme.Brush(state switch
        {
            SyncChipState.Synced => "SystemFillColorSuccessBrush",
            SyncChipState.Off => "SystemFillColorCautionBrush",
            SyncChipState.Error => "SystemFillColorCriticalBrush",
            SyncChipState.Unpaired => "SystemFillColorAttentionBrush",
            _ => "SystemFillColorNeutralBrush",
        });
        statusText.Foreground = ShellTheme.Brush(state switch
        {
            SyncChipState.Off => "SystemFillColorCautionBrush",
            SyncChipState.Error => "SystemFillColorCriticalBrush",
            _ => "TextFillColorSecondaryBrush",
        });
        ToolTipService.SetToolTip(statusRow, "Phone sync: " + description);
        AutomationProperties.SetName(statusRow, "Phone sync: " + description);
    }

    private PaneItem AddItem(Panel host, string key, string glyph, string label)
    {
        var item = MakeItem(key, glyph, label);
        host.Children.Add(item.Button);
        return item;
    }

    private PaneItem MakeItem(string key, string glyph, string label)
    {
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var icon = ShellTheme.Glyph(glyph);
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        icon.VerticalAlignment = VerticalAlignment.Center;
        var text = ShellTheme.Text(label, "BodyTextBlockStyle");
        text.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(text, 1);
        grid.Children.Add(icon);
        grid.Children.Add(text);
        var button = ItemButton(grid, label);
        button.Tag = key;
        button.Click += OnItemClick;
        var item = new PaneItem(key, label, button, text);
        items[key] = item;
        return item;
    }

    private static Button ItemButton(UIElement content, string label)
    {
        var button = ShellTheme.Subtle(new Button
        {
            Height = ItemHeight,
            Margin = new Thickness(4, 2, 4, 2),
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(4),
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            VerticalContentAlignment = VerticalAlignment.Stretch,
            Content = content,
        });
        button.Resources["ButtonForeground"] = ShellTheme.Brush("TextFillColorPrimaryBrush");
        AutomationProperties.SetName(button, label);
        return button;
    }

    private void OnItemClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string key })
        {
            if (key == "browse" && !compact && sourceItems.Count > 0)
            {
                SetSourcesOpen(true);
            }
            Navigate?.Invoke(this, key);
        }
    }

    private void SetSourcesOpen(bool open)
    {
        sourcesOpen = open;
        sources.Visibility = open && !compact ? Visibility.Visible : Visibility.Collapsed;
        UpdateChevron();
        ApplySelection(animate: false);
    }

    private void UpdateChevron()
    {
        chevronIcon.Glyph = sourcesOpen ? "\uE70E" : "\uE70D";
        var label = sourcesOpen ? "Hide sources" : "Show sources";
        AutomationProperties.SetName(chevron, label);
        ToolTipService.SetToolTip(chevron, label);
    }

    private void ApplyCompact()
    {
        foreach (var item in items.Values)
        {
            item.Text.Visibility = compact ? Visibility.Collapsed : Visibility.Visible;
            ToolTipService.SetToolTip(item.Button, compact ? item.Label : null);
        }
        chevron.Visibility = !compact && sourceItems.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        sources.Visibility = sourcesOpen && !compact ? Visibility.Visible : Visibility.Collapsed;
        statusText.Visibility = compact ? Visibility.Collapsed : Visibility.Visible;
        Grid.SetColumn(badge, compact ? 0 : 2);
        badge.HorizontalAlignment = compact ? HorizontalAlignment.Right : HorizontalAlignment.Stretch;
        badge.VerticalAlignment = compact ? VerticalAlignment.Top : VerticalAlignment.Center;
        badge.Margin = compact ? new Thickness(0, 2, 2, 0) : new Thickness(0, 0, 12, 0);
        SetUpdates(updates);
        ApplySelection(animate: false);
    }

    private void ApplySelection(bool animate)
    {
        var fill = ShellTheme.Brush("SubtleFillColorSecondaryBrush");
        var none = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        var sourceTarget = selected == "browse" && sourcesOpen && !compact
            ? sourceItems.FirstOrDefault(s => s.Id == selectedSource).Item
            : null;
        FrameworkElement? target = null;
        foreach (var item in items.Values)
        {
            var on = item.Key == selected && sourceTarget is null;
            item.Button.Background = on ? fill : none;
            AutomationProperties.SetItemStatus(item.Button, item.Key == selected ? "Current page" : string.Empty);
            if (on)
            {
                target = item.Button;
            }
        }
        foreach (var (_, item) in sourceItems)
        {
            var on = item == sourceTarget;
            item.Button.Background = on ? fill : none;
            AutomationProperties.SetItemStatus(item.Button, on ? "Current source" : string.Empty);
            if (on)
            {
                target = item.Button;
            }
        }
        indicatorTarget = target;
        PlaceIndicator(animate);
    }

    private void PlaceIndicator(bool animate)
    {
        if (indicatorTarget is not { } target || target.XamlRoot is null || target.ActualHeight <= 0)
        {
            indicator.Opacity = 0;
            indicatorAt = new(float.NaN);
            return;
        }
        var origin = target.TransformToVisual(this).TransformPoint(default);
        var to = new Vector3((float)origin.X, (float)(origin.Y + (target.ActualHeight - indicator.Height) / 2), 0);
        if (to == indicatorAt)
        {
            return;
        }
        var visual = Motion.VisualOf(indicator);
        var first = float.IsNaN(indicatorAt.X);
        var from = indicatorAt;
        indicatorAt = to;
        indicator.Opacity = 1;
        if (!animate || first || Motion.Reduced)
        {
            visual.StopAnimation("Translation");
            visual.Properties.InsertVector3("Translation", to);
            return;
        }
        visual.StartAnimation("Translation", Motion.Vector3(visual.Compositor, from, to, IndicatorSlide, Curve.StandardDecelerate));
    }
}
