using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Controls;

namespace Tsuzuki.App.Next;

public sealed record ListRowItem(
    long MangaId,
    long ChapterId,
    int Page,
    string Title,
    string Subtitle,
    string Time,
    string? ThumbnailUrl,
    long Source,
    bool Unread,
    string ActionLabel,
    IReadOnlyList<long> UnreadIds,
    bool First = false,
    bool Last = false)
{
    public string Signature => $"{MangaId}|{ChapterId}|{Page}|{Title}|{Subtitle}|{Time}|{ThumbnailUrl}|{Unread}|{ActionLabel}|{UnreadIds.Count}|{First}|{Last}";

    public string AccessibleName => Unread ? $"{Title}, {Subtitle}, {Time}, unread" : $"{Title}, {Subtitle}, {Time}";
}

public static class RowFormat
{
    public static string Day(long ms)
    {
        if (ms <= 0)
        {
            return "Earlier";
        }
        var date = DateTimeOffset.FromUnixTimeMilliseconds(ms).ToLocalTime().Date;
        if (date == DateTime.Today) return "Today";
        if (date == DateTime.Today.AddDays(-1)) return "Yesterday";
        if (date > DateTime.Today.AddDays(-7)) return date.ToString("dddd");
        return date.Year == DateTime.Today.Year ? date.ToString("d MMMM") : date.ToString("d MMMM yyyy");
    }

    public static string Time(long ms) =>
        ms <= 0 ? string.Empty : DateTimeOffset.FromUnixTimeMilliseconds(ms).ToLocalTime().ToString("t");

    public static List<ListRowGroup> Group<T>(IEnumerable<T> items, Func<T, string> dayOf, Func<T, ListRowItem> row) where T : notnull
    {
        var groups = new List<ListRowGroup>();
        string? day = null;
        List<T> pending = [];
        foreach (var entry in Tsuzuki.Core.Library.DayGrouping.WithHeaders(items, dayOf))
        {
            if (entry is string header)
            {
                if (day is not null)
                {
                    groups.Add(Seal(day, pending, row));
                }
                day = header;
                pending = [];
                continue;
            }
            pending.Add((T)entry);
        }
        if (day is not null)
        {
            groups.Add(Seal(day, pending, row));
        }
        return groups;
    }

    private static ListRowGroup Seal<T>(string day, List<T> entries, Func<T, ListRowItem> row) =>
        new(day, entries.Select((e, i) => row(e) with { First = i == 0, Last = i == entries.Count - 1 }));
}

public sealed partial class ListRow : Grid
{
    public const double RowHeight = 88;

    private static readonly FontFamily Symbols = (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    private readonly CoverImage cover = new() { DecodeWidth = 96 };
    private readonly Border card = new();
    private readonly TextBlock title = new() { Style = StyleOf("BodyStrongTextBlockStyle"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly TextBlock subtitle = new() { Style = StyleOf("CaptionTextBlockStyle"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1, Margin = new Thickness(0, 4, 0, 0) };
    private readonly TextBlock time = new() { Style = StyleOf("CaptionTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(12, 0, 0, 0) };
    private readonly Ellipse dot = new() { Width = 8, Height = 8, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(12, 0, 0, 0) };
    private readonly StackPanel actions = new()
    {
        Orientation = Orientation.Horizontal,
        Spacing = 8,
        VerticalAlignment = VerticalAlignment.Center,
        Opacity = 0,
        IsHitTestVisible = false,
        OpacityTransition = Design.Motion.Reduced ? null : new ScalarTransition { Duration = TimeSpan.FromMilliseconds(150) },
    };
    private readonly Button primary;
    private readonly TextBlock primaryText = new();
    private readonly Button secondary;
    private readonly TextBlock secondaryText = new();
    private readonly Brush restFill = Brush("CardBackgroundFillColorDefaultBrush");
    private readonly Brush hoverFill = Brush("CardBackgroundFillColorSecondaryBrush");
    private bool hovered;
    private bool focused;
    private bool actionsShown;
    private int columns = 1;

    public ListRow()
    {
        card.Background = restFill;
        card.BorderBrush = Brush("CardStrokeColorDefaultBrush");
        card.Padding = new Thickness(12, 8, 16, 8);
        subtitle.Foreground = Brush("TextFillColorSecondaryBrush");
        time.Foreground = Brush("TextFillColorSecondaryBrush");
        dot.Fill = Brush("AccentFillColorDefaultBrush");
        primary = ActionButton("\uE768", primaryText, accent: true);
        secondary = ActionButton("\uE73E", secondaryText, accent: false);
        actions.Children.Add(secondary);
        actions.Children.Add(primary);
        primary.Click += (_, _) => { if (Item is { } item) PrimaryInvoked?.Invoke(this, item); };
        secondary.Click += (_, _) => { if (Item is { } item) SecondaryInvoked?.Invoke(this, item); };

        var thumb = new Border
        {
            Width = 48,
            Height = 72,
            CornerRadius = new CornerRadius(4),
            Child = cover,
            VerticalAlignment = VerticalAlignment.Center,
        };
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 12, 0) };
        text.Children.Add(title);
        text.Children.Add(subtitle);
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(thumb);
        SetColumn(text, 1);
        grid.Children.Add(text);
        SetColumn(actions, 2);
        grid.Children.Add(actions);
        SetColumn(time, 3);
        grid.Children.Add(time);
        SetColumn(dot, 4);
        grid.Children.Add(dot);
        card.Child = grid;
        Children.Add(card);
        Height = RowHeight;
        PointerEntered += (_, _) => { hovered = true; Refresh(); };
        PointerExited += (_, _) => { hovered = false; Refresh(); };
        PointerCanceled += (_, _) => { hovered = false; Refresh(); };
        ApplyShape();
    }

    public event EventHandler<ListRowItem>? PrimaryInvoked;

    public event EventHandler<ListRowItem>? SecondaryInvoked;

    public ListRowItem? Item { get; private set; }

    public FrameworkElement Cover => cover;

    public string? SecondaryLabel { get; set; }

    public int Columns
    {
        get => columns;
        set
        {
            if (columns == value)
            {
                return;
            }
            columns = value;
            ApplyShape();
        }
    }

    public bool Focused
    {
        get => focused;
        set
        {
            focused = value;
            Refresh();
        }
    }

    public void Bind(ListRowItem item)
    {
        Item = item;
        title.Text = item.Title;
        subtitle.Text = item.Subtitle;
        time.Text = item.Time;
        cover.SourceId = item.Source;
        if (cover.Url != item.ThumbnailUrl)
        {
            cover.Url = item.ThumbnailUrl;
        }
        else if (item.ThumbnailUrl is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        dot.Opacity = item.Unread ? 1 : 0;
        title.Foreground = Brush(item.Unread ? "TextFillColorPrimaryBrush" : "TextFillColorSecondaryBrush");
        primaryText.Text = item.ActionLabel;
        AutomationProperties.SetName(primary, $"{item.ActionLabel} {item.Title}");
        var label = item.UnreadIds.Count > 0 ? SecondaryLabel : null;
        secondary.Visibility = label is null ? Visibility.Collapsed : Visibility.Visible;
        secondaryText.Text = label ?? string.Empty;
        AutomationProperties.SetName(secondary, label is null ? string.Empty : $"{label}, {item.Title}");
        hovered = false;
        focused = false;
        ApplyShape();
        Refresh();
    }

    private void ApplyShape()
    {
        if (columns > 1 || Item is null)
        {
            card.CornerRadius = new CornerRadius(8);
            card.BorderThickness = new Thickness(1);
            Margin = columns > 1 ? new Thickness(4) : new Thickness(0);
            return;
        }
        Margin = new Thickness(0);
        card.CornerRadius = new CornerRadius(Item.First ? 8 : 0, Item.First ? 8 : 0, Item.Last ? 8 : 0, Item.Last ? 8 : 0);
        card.BorderThickness = new Thickness(1, Item.First ? 1 : 0, 1, 1);
    }

    private void Refresh()
    {
        var active = hovered || focused;
        card.Background = active ? hoverFill : restFill;
        if (actionsShown == active)
        {
            return;
        }
        actionsShown = active;
        actions.IsHitTestVisible = active;
        time.Visibility = active ? Visibility.Collapsed : Visibility.Visible;
        actions.Opacity = active ? 1 : 0;
    }

    private static Button ActionButton(string glyph, TextBlock label, bool accent)
    {
        var content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        content.Children.Add(new FontIcon { Glyph = glyph, FontFamily = Symbols, FontSize = 14 });
        content.Children.Add(label);
        var button = new Button
        {
            Content = content,
            IsTabStop = false,
            Padding = new Thickness(12, 5, 12, 6),
            CornerRadius = new CornerRadius(4),
        };
        if (accent)
        {
            button.Style = StyleOf("AccentButtonStyle");
        }
        return button;
    }

    private static Style StyleOf(string key) => (Style)Application.Current.Resources[key];

    private static Brush Brush(string key) => (Brush)Application.Current.Resources[key];
}
