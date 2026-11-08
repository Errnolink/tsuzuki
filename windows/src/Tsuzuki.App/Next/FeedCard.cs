using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Navigation;

namespace Tsuzuki.App.Next;

public sealed record FeedChipItem(string Label, long ChapterId, int Page, bool Clickable);

public sealed record FeedCardItem(
    long MangaId,
    string Title,
    string? ThumbnailUrl,
    long Source,
    bool Merged,
    int UnreadCount,
    IReadOnlyList<FeedChipItem> Chips,
    IReadOnlyList<long> UnreadChapterIds,
    bool Read)
{
    public string Signature =>
        $"{MangaId}|{Title}|{ThumbnailUrl}|{Merged}|{UnreadCount}|{Read}|{string.Join(";", Chips.Select(c => c.Label + (c.Clickable ? "!" : "")))}|{string.Join(";", UnreadChapterIds)}";

    public string AccessibleName => Read ? $"{Title}, read" : $"{Title}, {UnreadCount} unread chapters";
}

public sealed partial class FeedCard : Button
{
    private const long MangaDexSource = 2499283573021220255;

    private static readonly FontFamily Symbols = (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    private readonly CoverImage cover = new() { DecodeWidth = 96 };
    private readonly Border coverFrame;
    private readonly TextBlock title = new()
    {
        Style = StyleOf("BodyStrongTextBlockStyle"),
        FontSize = 14,
        TextTrimming = TextTrimming.CharacterEllipsis,
        MaxLines = 1,
    };
    private readonly WrapPanel chips = new() { HorizontalSpacing = 6, VerticalSpacing = 6, Margin = new Thickness(0, 6, 0, 0) };
    private readonly Border badge = new()
    {
        Background = Brush("AccentFillColorDefaultBrush"),
        CornerRadius = new CornerRadius(9),
        Padding = new Thickness(7, 2, 7, 3),
        HorizontalAlignment = HorizontalAlignment.Right,
    };
    private readonly TextBlock badgeText = new()
    {
        Style = StyleOf("CaptionTextBlockStyle"),
        FontSize = 11,
        FontWeight = Microsoft.UI.Text.FontWeights.Bold,
        Foreground = Brush("TextOnAccentFillColorPrimaryBrush"),
        TextLineBounds = Microsoft.UI.Xaml.TextLineBounds.Tight,
    };
    private readonly Border tag = new()
    {
        Background = Brush("TzChipBackground"),
        CornerRadius = new CornerRadius(4),
        Padding = new Thickness(6, 1, 6, 2),
        HorizontalAlignment = HorizontalAlignment.Right,
    };
    private readonly TextBlock tagText = new()
    {
        Style = StyleOf("CaptionTextBlockStyle"),
        FontSize = 10,
        FontWeight = Microsoft.UI.Text.FontWeights.Bold,
        Foreground = Brush("TextFillColorSecondaryBrush"),
    };
    private readonly List<Button> chipButtons = [];

    public FeedCard()
    {
        CornerRadius = new CornerRadius(12);
        Padding = new Thickness(12, 8, 12, 9);
        HorizontalContentAlignment = HorizontalAlignment.Stretch;
        HorizontalAlignment = HorizontalAlignment.Stretch;
        Resources["ButtonBackground"] = Brush("CardBackgroundFillColorDefaultBrush");
        Resources["ButtonBackgroundPointerOver"] = Brush("CardBackgroundFillColorSecondaryBrush");
        Resources["ButtonBackgroundPressed"] = Brush("CardBackgroundFillColorSecondaryBrush");
        Resources["ButtonBorderBrush"] = Brush("CardStrokeColorDefaultBrush");
        Resources["ButtonBorderBrushPointerOver"] = Brush("CardStrokeColorDefaultBrush");
        Resources["ButtonBorderBrushPressed"] = Brush("CardStrokeColorDefaultBrush");
        BorderThickness = new Thickness(1);

        badge.Child = badgeText;
        tag.Child = tagText;
        coverFrame = new Border
        {
            Width = 56,
            Height = 80,
            CornerRadius = new CornerRadius(6),
            BorderBrush = Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            Background = Brush("CardBackgroundFillColorDefaultBrush"),
            Child = cover,
        };
        cover.Stretch = Stretch.UniformToFill;

        var main = new StackPanel();
        main.Children.Add(title);
        main.Children.Add(chips);

        var right = new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 8,
        };
        right.Children.Add(badge);
        right.Children.Add(tag);

        var grid = new Grid { ColumnSpacing = 14 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(coverFrame);
        Grid.SetColumn(main, 1);
        grid.Children.Add(main);
        Grid.SetColumn(right, 2);
        grid.Children.Add(right);

        Content = grid;
        Click += OnClick;
        var menu = new MenuFlyout();
        menu.Opening += (_, _) => FillMenu(menu);
        ContextFlyout = menu;
    }

    public FeedCardItem? Item { get; private set; }

    public FrameworkElement CoverElement => coverFrame;

    public event EventHandler<(FeedCardItem Item, FeedChipItem Chip, FrameworkElement Origin)>? ChipInvoked;

    public event EventHandler<FeedCardItem>? OpenDetails;

    public event EventHandler<FeedCardItem>? MarkRead;

    public void Bind(FeedCardItem item)
    {
        Item = item;
        cover.SourceId = item.Source;
        if (cover.Url != item.ThumbnailUrl)
        {
            cover.Url = item.ThumbnailUrl;
        }
        else if (item.ThumbnailUrl is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        title.Text = item.Title;
        ToolTipService.SetToolTip(title, item.Title);
        Opacity = item.Read ? 0.5 : 1;
        badgeText.Text = item.UnreadCount > 999 ? "999+" : item.UnreadCount.ToString();
        badge.Visibility = item.UnreadCount > 0 ? Visibility.Visible : Visibility.Collapsed;
        tag.Visibility = item.Read || (!item.Merged && item.Source != MangaDexSource) ? Visibility.Collapsed : Visibility.Visible;
        tagText.Text = item.Merged ? "Merged" : "MD";
        AutomationProperties.SetName(this, item.AccessibleName);
        FillChips(item);
    }

    private void FillChips(FeedCardItem item)
    {
        foreach (var chip in chipButtons)
        {
            chips.Children.Remove(chip);
        }
        chipButtons.Clear();
        foreach (var chip in item.Chips)
        {
            var label = new TextBlock
            {
                Text = chip.Label,
                Style = StyleOf("CaptionTextBlockStyle"),
                FontSize = 12,
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                TextLineBounds = Microsoft.UI.Xaml.TextLineBounds.Tight,
            };
            var button = new Button
            {
                Content = label,
                Padding = new Thickness(8, 2, 8, 4),
                CornerRadius = new CornerRadius(8),
                MinWidth = 0,
                MinHeight = 0,
            };
            if (chip.Clickable)
            {
                button.Background = Brush("TzAccentSoft");
                button.BorderBrush = Brush("TzAccentSoft");
                button.Foreground = Brush("TzAccent");
                var captured = chip;
                button.Click += (_, _) => ChipInvoked?.Invoke(this, (item, captured, button));
                AutomationProperties.SetName(button, $"Read {chip.Label} of {item.Title}");
            }
            else
            {
                button.IsEnabled = false;
                button.Foreground = Brush("TextFillColorSecondaryBrush");
            }
            chipButtons.Add(button);
            chips.Children.Add(button);
        }
    }

    private void OnClick(object sender, RoutedEventArgs e)
    {
        if (chipButtons.Contains(e.OriginalSource) || Item is null)
        {
            return;
        }
        OpenDetails?.Invoke(this, Item);
    }

    private void FillMenu(MenuFlyout menu)
    {
        menu.Items.Clear();
        if (Item is not { } item)
        {
            return;
        }
        if (item.UnreadChapterIds.Count > 0)
        {
            var mark = new MenuFlyoutItem { Text = "Mark read", Icon = new FontIcon { Glyph = "\uE73E", FontFamily = Symbols } };
            mark.Click += (_, _) => MarkRead?.Invoke(this, item);
            menu.Items.Add(mark);
        }
        var details = new MenuFlyoutItem { Text = "Open details", Icon = new FontIcon { Glyph = "\uE946", FontFamily = Symbols } };
        details.Click += (_, _) => OpenDetails?.Invoke(this, item);
        menu.Items.Add(details);
    }

    private static Style StyleOf(string key) => (Style)Application.Current.Resources[key];

    private static Brush Brush(string key) => (Brush)Application.Current.Resources[key];
}
