using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Controls;

public sealed partial class SettingsCard : Grid
{
    private readonly FontIcon icon = new() { FontSize = 20, Width = 20, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 16, 0), Visibility = Visibility.Collapsed };
    private readonly TextBlock header = new() { TextWrapping = TextWrapping.Wrap };
    private readonly TextBlock description = new() { TextWrapping = TextWrapping.Wrap, Visibility = Visibility.Collapsed };
    private FrameworkElement? content;

    public SettingsCard()
    {
        MinHeight = 68;
        Padding = new Thickness(16, 12, 16, 12);
        CornerRadius = Res<CornerRadius>("TzCardRadius");
        Background = Theme.Brush("TzCardBackground");
        BorderBrush = Theme.Brush("TzCardStroke");
        BorderThickness = new Thickness(1);
        ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        icon.FontFamily = Res<FontFamily>("TzIconFont");
        icon.Foreground = Theme.Brush("TzText");
        header.Style = Res<Style>("TzBody");
        description.Style = Res<Style>("TzCaption");
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 2, Children = { header, description } };
        SetColumn(text, 1);
        Children.Add(icon);
        Children.Add(text);
    }

    public string Header
    {
        get => header.Text;
        set
        {
            header.Text = value;
            AutomationProperties.SetName(this, value);
            NameContent();
        }
    }

    public string Description
    {
        get => description.Text;
        set
        {
            description.Text = value;
            description.Visibility = string.IsNullOrEmpty(value) ? Visibility.Collapsed : Visibility.Visible;
            NameContent();
        }
    }

    public bool DescriptionSelectable
    {
        get => description.IsTextSelectionEnabled;
        set => description.IsTextSelectionEnabled = value;
    }

    public string? Icon
    {
        get => icon.Glyph;
        set
        {
            icon.Glyph = value ?? string.Empty;
            icon.Visibility = string.IsNullOrEmpty(value) ? Visibility.Collapsed : Visibility.Visible;
        }
    }

    public FrameworkElement? Content
    {
        get => content;
        set
        {
            if (content is not null)
            {
                Children.Remove(content);
            }
            content = value;
            if (content is null)
            {
                return;
            }
            content.VerticalAlignment = VerticalAlignment.Center;
            content.HorizontalAlignment = HorizontalAlignment.Right;
            content.Margin = new Thickness(16, 0, 0, 0);
            SetColumn(content, 2);
            Children.Add(content);
            NameContent();
        }
    }

    internal void Nest()
    {
        MinHeight = 52;
        Padding = new Thickness(52, 10, 16, 10);
        CornerRadius = new CornerRadius(0);
        Background = null;
        BorderThickness = new Thickness(0);
    }

    private void NameContent()
    {
        if (content is not Control control || control is ButtonBase || string.IsNullOrEmpty(header.Text))
        {
            return;
        }
        AutomationProperties.SetName(control, header.Text);
        AutomationProperties.SetHelpText(control, description.Text);
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];
}
