using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Automation.Peers;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Tsuzuki.App.Design;
using Windows.System;

namespace Tsuzuki.App.Next;

public sealed partial class HomeShelf : StackPanel
{
    private readonly ScrollViewer scroller;
    private readonly Button previous;
    private readonly Button forward;
    private readonly TextBlock count;
    private bool hovered;

    public HomeShelf(string title, string seeAllLabel, Func<Task> seeAll)
    {
        Spacing = 8;
        var header = new Grid();
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var heading = ShellTheme.Text(title, "SubtitleTextBlockStyle");
        heading.VerticalAlignment = VerticalAlignment.Center;
        AutomationProperties.SetHeadingLevel(heading, AutomationHeadingLevel.Level2);
        count = ShellTheme.Text(string.Empty, "CaptionTextBlockStyle");
        count.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");
        count.VerticalAlignment = VerticalAlignment.Center;
        count.Margin = new Thickness(10, 4, 0, 0);
        Grid.SetColumn(count, 1);
        var link = new HyperlinkButton { Content = "See all", VerticalAlignment = VerticalAlignment.Center, Padding = new Thickness(8, 4, 8, 4) };
        AutomationProperties.SetName(link, seeAllLabel);
        link.Click += (_, _) => _ = Log.Guard("home-see-all", seeAll);
        Grid.SetColumn(link, 2);
        header.Children.Add(heading);
        header.Children.Add(count);
        header.Children.Add(link);
        Children.Add(header);

        Row = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 16,
            Padding = new Thickness(0, 6, 0, 10),
            XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled,
            TabFocusNavigation = KeyboardNavigationMode.Once,
        };
        AutomationProperties.SetName(Row, title);
        scroller = new ScrollViewer
        {
            Content = Row,
            HorizontalScrollMode = ScrollMode.Enabled,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden,
            VerticalScrollMode = ScrollMode.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
            IsTabStop = false,
        };
        scroller.ViewChanged += (_, _) => UpdateArrows();
        scroller.SizeChanged += (_, _) => UpdateArrows();
        Row.SizeChanged += (_, _) => UpdateArrows();

        previous = Arrow("\uE76B", $"Scroll {title} back", HorizontalAlignment.Left, -1);
        forward = Arrow("\uE76C", $"Scroll {title} forward", HorizontalAlignment.Right, 1);
        var body = new Grid();
        body.Children.Add(scroller);
        body.Children.Add(previous);
        body.Children.Add(forward);
        body.PointerEntered += (_, _) => { hovered = true; UpdateArrows(); };
        body.PointerExited += (_, _) => { hovered = false; UpdateArrows(); };
        body.AddHandler(PointerWheelChangedEvent, new PointerEventHandler(OnWheel), true);
        Children.Add(body);
    }

    public StackPanel Row { get; }

    public void SetCount(string text) => count.Text = text;

    private Button Arrow(string glyph, string label, HorizontalAlignment side, int direction)
    {
        var button = new Button
        {
            Width = 32,
            Height = 32,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(16),
            HorizontalAlignment = side,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = side == HorizontalAlignment.Left ? new Thickness(-12, 0, 0, 24) : new Thickness(0, 0, -12, 24),
            Background = ShellTheme.Brush("AcrylicInAppFillColorDefaultBrush"),
            BorderBrush = ShellTheme.Brush("ControlStrokeColorDefaultBrush"),
            Content = ShellTheme.Glyph(glyph, 12),
            IsTabStop = false,
            Visibility = Visibility.Collapsed,
        };
        AutomationProperties.SetName(button, label);
        ToolTipService.SetToolTip(button, label);
        button.Click += (_, _) => Page(direction);
        return button;
    }

    private void Page(int direction)
    {
        var target = Math.Clamp(scroller.HorizontalOffset + direction * scroller.ViewportWidth * 0.85, 0, scroller.ScrollableWidth);
        scroller.ChangeView(target, null, null, Motion.Reduced);
    }

    private void OnWheel(object sender, PointerRoutedEventArgs e)
    {
        var point = e.GetCurrentPoint(scroller);
        var shift = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Shift).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        if (point.Properties.IsHorizontalMouseWheel || !shift || scroller.ScrollableWidth <= 0)
        {
            return;
        }
        var delta = point.Properties.MouseWheelDelta;
        scroller.ChangeView(Math.Clamp(scroller.HorizontalOffset - delta, 0, scroller.ScrollableWidth), null, null, Motion.Reduced);
        e.Handled = true;
    }

    private void UpdateArrows()
    {
        previous.Visibility = hovered && scroller.HorizontalOffset > 1 ? Visibility.Visible : Visibility.Collapsed;
        forward.Visibility = hovered && scroller.HorizontalOffset < scroller.ScrollableWidth - 1 ? Visibility.Visible : Visibility.Collapsed;
    }
}
