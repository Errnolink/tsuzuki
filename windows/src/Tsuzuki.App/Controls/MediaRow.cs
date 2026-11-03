using System.Numerics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;

namespace Tsuzuki.App.Controls;

public sealed record MediaRowData(long MangaId, long ChapterId, string Title, string Subtitle, string Trailing, string? ThumbnailUrl, bool Unread, int Page, object? Tag = null, bool Group = false, bool Expanded = false, string? Tip = null);

public sealed partial class MediaRow : Button
{
    private readonly CoverImage cover = new() { DecodeWidth = 60 };
    private readonly TextBlock title = new() { FontSize = 14, FontWeight = FontWeights.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly TextBlock subtitle = new() { FontSize = 12, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1, Margin = new Thickness(0, 3, 0, 0) };
    private readonly TextBlock trailing = new() { FontSize = 12, VerticalAlignment = VerticalAlignment.Center };
    private readonly FontIcon chevron = new()
    {
        Glyph = Design.Icons.ChevronDown,
        FontFamily = (Microsoft.UI.Xaml.Media.FontFamily)Application.Current.Resources["TzIconFont"],
        FontSize = 15,
        Width = 16,
        Height = 16,
        Foreground = Design.Theme.Brush("TzTextTertiary"),
        VerticalAlignment = VerticalAlignment.Center,
        Margin = new Thickness(12, 0, 2, 0),
        Visibility = Visibility.Collapsed,
    };
    private readonly Ellipse dot = new() { Width = 8, Height = 8, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(14, 0, 4, 0) };
    private readonly Border actionPill = new()
    {
        CornerRadius = new CornerRadius(12),
        Padding = new Thickness(14, 0, 14, 0),
        Height = 28,
        VerticalAlignment = VerticalAlignment.Center,
        Margin = new Thickness(0, 0, 10, 0),
        Opacity = 0,
        IsHitTestVisible = false,
    };
    private readonly TextBlock actionText = new() { FontSize = 12, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center };

    public MediaRow()
    {
        Style = (Style)Application.Current.Resources["TzRowButton"];
        Height = 76;
        Padding = new Thickness(12, 0, 16, 0);
        subtitle.Foreground = Design.Theme.Brush("TzTextSecondary");
        trailing.Foreground = Design.Theme.Brush("TzTextTertiary");
        dot.Fill = Design.Theme.Brush("TzAccent");
        actionPill.Background = Design.Theme.Brush("TzAccentSoft");
        actionText.Foreground = Design.Theme.Brush("TzAccent");
        ActualThemeChanged += (_, _) => ApplyDim();
        actionPill.Child = actionText;
        var thumb = new Border { Width = 40, Height = 60, CornerRadius = new CornerRadius(6), Child = cover, VerticalAlignment = VerticalAlignment.Center };
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(14, 0, 12, 0) };
        text.Children.Add(title);
        text.Children.Add(subtitle);
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(thumb);
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);
        Grid.SetColumn(actionPill, 2);
        grid.Children.Add(actionPill);
        Grid.SetColumn(trailing, 3);
        grid.Children.Add(trailing);
        Grid.SetColumn(chevron, 4);
        grid.Children.Add(chevron);
        Grid.SetColumn(dot, 5);
        grid.Children.Add(dot);
        Content = grid;
        PointerEntered += (_, _) => SetPill(true);
        PointerExited += (_, _) => SetPill(false);
        actionPill.Tapped += (_, e) =>
        {
            e.Handled = true;
            new Microsoft.UI.Xaml.Automation.Peers.ButtonAutomationPeer(this).Invoke();
        };
    }

    private bool pillShown;

    private void SetPill(bool hover)
    {
        if (Data is null || pillShown == hover)
        {
            return;
        }
        pillShown = hover;
        actionPill.IsHitTestVisible = hover;
        var visual = Design.Motion.VisualOf(actionPill);
        var compositor = visual.Compositor;
        visual.StartAnimation("Opacity", Design.Motion.Scalar(compositor, (float)actionPill.Opacity, hover ? 1 : 0, Design.Motion.Short2, Design.Curve.StandardDecelerate));
        if (!Design.Motion.Reduced)
        {
            visual.StartAnimation("Translation", Design.Motion.Vector3(compositor, new(hover ? 6 : 0, 0, 0), new(hover ? 0 : 6, 0, 0), Design.Motion.Short2, Design.Curve.StandardDecelerate));
        }
    }

    public static readonly DependencyProperty DataProperty = DependencyProperty.Register(
        nameof(Data), typeof(MediaRowData), typeof(MediaRow), new PropertyMetadata(null, (d, _) => ((MediaRow)d).BindCore()));

    public MediaRowData? Data
    {
        get => (MediaRowData?)GetValue(DataProperty);
        set => SetValue(DataProperty, value);
    }

    private void BindCore()
    {
        if (Data is not { } data)
        {
            return;
        }
        title.Text = data.Title;
        subtitle.Text = data.Subtitle;
        trailing.Text = data.Trailing;
        if (cover.Url != data.ThumbnailUrl)
        {
            cover.Url = data.ThumbnailUrl;
        }
        else if (data.ThumbnailUrl is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        dot.Opacity = data.Unread ? 1 : 0;
        actionText.Text = data.Page > 0 ? "Continue" : "Read";
        chevron.Visibility = data.Group ? Visibility.Visible : Visibility.Collapsed;
        if (data.Group)
        {
            ApplyChevron(data.Expanded, animate: false);
        }
        ToolTipService.SetToolTip(this, data.Group && !data.Expanded && data.Tip is { Length: > 0 } ? data.Tip : null);
        ApplyDim();
        SetPill(false);
        AutomationProperties.SetName(this, data.Group
            ? $"{data.Title}, {data.Subtitle}, {(data.Expanded ? "expanded" : "collapsed")}"
            : $"{data.Title}, {data.Subtitle}");
    }

    private void ApplyChevron(bool expanded, bool animate)
    {
        var visual = Design.Motion.VisualOf(chevron);
        visual.CenterPoint = new Vector3(8, 8, 0);
        var target = expanded ? 0f : -MathF.PI / 2;
        if (animate && !Design.Motion.Reduced)
        {
            visual.StartAnimation("RotationAngle", Design.Motion.Scalar(visual.Compositor, visual.RotationAngle, target, Design.Motion.Short3, Design.Curve.StandardDecelerate));
        }
        else
        {
            visual.StopAnimation("RotationAngle");
            visual.RotationAngle = target;
        }
    }

    public void AnimateChevron(bool expanded) => ApplyChevron(expanded, animate: true);

    private Microsoft.UI.Xaml.Media.Animation.Storyboard? board;

    public Microsoft.UI.Xaml.Media.Animation.Storyboard? Board
    {
        get => board;
        set
        {
            board?.Stop();
            board = value;
        }
    }

    public void ResetPresentation()
    {
        Board = null;
        Height = 76;
        Opacity = 1;
    }

    private void ApplyDim()
    {
        if (Data is not { } data)
        {
            return;
        }
        var light = ActualTheme == ElementTheme.Light;
        title.Opacity = data.Unread ? 1 : light ? 0.70 : 0.62;
        subtitle.Opacity = data.Unread ? 1 : light ? 0.92 : 0.72;
    }

    public FrameworkElement Thumb => cover;

    public void Bind(MediaRowData data) => Data = data;
}

public sealed partial class SectionHeader : Grid
{
    public static readonly DependencyProperty TextProperty = DependencyProperty.Register(
        nameof(Text), typeof(string), typeof(SectionHeader), new PropertyMetadata(null, (d, _) => ((SectionHeader)d).label.Text = ((string)((SectionHeader)d).GetValue(TextProperty))?.ToUpperInvariant() ?? string.Empty));

    private readonly TextBlock label = new()
    {
        Style = (Style)Application.Current.Resources["TzKicker"],
        Margin = new Thickness(12, 22, 0, 8),
    };

    public SectionHeader() => Children.Add(label);

    public string Text
    {
        get => (string)GetValue(TextProperty);
        set => SetValue(TextProperty, value);
    }
}