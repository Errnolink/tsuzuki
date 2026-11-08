using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Controls;

public sealed partial class SettingsExpander : StackPanel
{
    private readonly SettingsCard card = new();
    private readonly FontIcon chevron = new() { Glyph = Icons.ChevronDown, FontSize = 16 };
    private readonly Button toggle;
    private readonly StackPanel trailing = new() { Orientation = Orientation.Horizontal, Spacing = 8 };
    private readonly StackPanel items = new();
    private readonly Border itemsHost;
    private FrameworkElement? content;
    private bool expanded;

    public SettingsExpander()
    {
        chevron.FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"];
        toggle = new Button
        {
            Style = (Style)Application.Current.Resources["TzIconButton"],
            Width = 32,
            Height = 32,
            CornerRadius = (CornerRadius)Application.Current.Resources["TzControlRadius"],
            Content = chevron,
        };
        toggle.Click += (_, _) => IsExpanded = !IsExpanded;
        trailing.Children.Add(toggle);
        card.Content = trailing;
        card.Tapped += OnHeaderTapped;
        card.PointerEntered += (_, _) => card.Background = Theme.Brush("TzCardBackgroundHover");
        card.PointerExited += (_, _) => card.Background = Theme.Brush("TzCardBackground");
        var radius = ((CornerRadius)Application.Current.Resources["TzCardRadius"]).TopLeft;
        itemsHost = new Border
        {
            Background = Theme.Brush("TzCardBackground"),
            BorderBrush = Theme.Brush("TzCardStroke"),
            BorderThickness = new Thickness(1, 0, 1, 1),
            CornerRadius = new CornerRadius(0, 0, radius, radius),
            Child = items,
            Visibility = Visibility.Collapsed,
        };
        Children.Add(card);
        Children.Add(itemsHost);
        Sync();
    }

    public string Header
    {
        get => card.Header;
        set
        {
            card.Header = value;
            AutomationProperties.SetName(this, value);
            AutomationProperties.SetName(toggle, value);
        }
    }

    public string Description
    {
        get => card.Description;
        set => card.Description = value;
    }

    public string? Icon
    {
        get => card.Icon;
        set => card.Icon = value;
    }

    public FrameworkElement? Content
    {
        get => content;
        set
        {
            if (content is not null)
            {
                trailing.Children.Remove(content);
            }
            content = value;
            if (content is null)
            {
                return;
            }
            content.VerticalAlignment = VerticalAlignment.Center;
            if (!string.IsNullOrEmpty(card.Header) && content is Control and not ButtonBase)
            {
                AutomationProperties.SetName(content, card.Header);
            }
            trailing.Children.Insert(0, content);
        }
    }

    public bool IsExpanded
    {
        get => expanded;
        set
        {
            expanded = value;
            Sync();
        }
    }

    public void Add(SettingsCard item)
    {
        if (items.Children.Count > 0)
        {
            items.Children.Add(new Border { Height = 1, Background = Theme.Brush("TzCardStroke") });
        }
        item.Nest();
        items.Children.Add(item);
    }

    private void Sync()
    {
        var radius = ((CornerRadius)Application.Current.Resources["TzCardRadius"]).TopLeft;
        card.CornerRadius = expanded ? new CornerRadius(radius, radius, 0, 0) : new CornerRadius(radius);
        itemsHost.Visibility = expanded ? Visibility.Visible : Visibility.Collapsed;
        chevron.Glyph = expanded ? Icons.ChevronUp : Icons.ChevronDown;
        ToolTipService.SetToolTip(toggle, expanded ? "Show less" : "Show more");
    }

    private void OnHeaderTapped(object sender, TappedRoutedEventArgs e)
    {
        for (var node = e.OriginalSource as DependencyObject; node is not null && node != card; node = VisualTreeHelper.GetParent(node))
        {
            if (node == toggle || node == content)
            {
                return;
            }
        }
        IsExpanded = !IsExpanded;
    }
}
