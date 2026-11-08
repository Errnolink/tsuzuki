using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;

namespace Tsuzuki.App.Next;

public sealed class FeedGroup : List<FeedCardItem>
{
    public FeedGroup(string header, bool today, int titles, int chapters, IReadOnlyList<long> unreadIds, IEnumerable<FeedCardItem> items)
        : base(items)
    {
        Header = header;
        Today = today;
        Titles = titles;
        Chapters = chapters;
        UnreadIds = unreadIds;
    }

    public string Header { get; }

    public bool Today { get; }

    public int Titles { get; }

    public int Chapters { get; }

    public IReadOnlyList<long> UnreadIds { get; }

    public string CountLabel
    {
        get
        {
            var titles = Titles == 1 ? "1 title" : $"{Titles} titles";
            return $"{titles} · {(Chapters == 1 ? "1 ch." : $"{Chapters} ch.")}";
        }
    }

    public string Signature => $"{Header}|{Today}|{Titles}|{Chapters}|{UnreadIds.Count}|{string.Join(";", this.Select(i => i.Signature))}";

    public override string ToString() => Header;
}

public sealed partial class FeedTimelineView : Grid
{
    private const string Presentation = "xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation'";
    private const double RailCenter = 11;
    private const double CardIndent = 30;

    private static readonly DataTemplate SlotTemplate = (DataTemplate)XamlReader.Load($"<DataTemplate {Presentation}><Grid Margin='{CardIndent},0,0,10'/></DataTemplate>");

    private static readonly ItemsPanelTemplate PanelTemplate = (ItemsPanelTemplate)XamlReader.Load(
        $"<ItemsPanelTemplate {Presentation}><ItemsStackPanel Orientation='Vertical' AreStickyGroupHeadersEnabled='False'/></ItemsPanelTemplate>");

    private static readonly Style HeaderItemStyle = (Style)XamlReader.Load($"""
        <Style {Presentation} TargetType='ListViewHeaderItem'>
          <Setter Property='Margin' Value='0'/>
          <Setter Property='Padding' Value='0'/>
          <Setter Property='MinHeight' Value='0'/>
          <Setter Property='HorizontalContentAlignment' Value='Stretch'/>
          <Setter Property='Template'>
            <Setter.Value>
              <ControlTemplate TargetType='ListViewHeaderItem'>
                <ContentPresenter Content='{"{"}TemplateBinding Tag{"}"}' HorizontalAlignment='Stretch' HorizontalContentAlignment='Stretch' />
              </ControlTemplate>
            </Setter.Value>
          </Setter>
        </Style>
        """);

    private static readonly Style ContainerStyle = (Style)XamlReader.Load($"""
        <Style {Presentation} TargetType='ListViewItem'>
          <Setter Property='Margin' Value='0'/>
          <Setter Property='Padding' Value='0'/>
          <Setter Property='MinWidth' Value='0'/>
          <Setter Property='MinHeight' Value='0'/>
          <Setter Property='HorizontalContentAlignment' Value='Stretch'/>
          <Setter Property='VerticalContentAlignment' Value='Stretch'/>
        </Style>
        """);

    private readonly CollectionViewSource source = new() { IsSourceGrouped = true };
    private readonly ListView list;
    private readonly Dictionary<DependencyObject, FeedCard> cardByContainer = [];
    private readonly Dictionary<ListViewBaseHeaderItem, DayHeader> headers = [];
    private IReadOnlyList<string> signature = [];
    private ScrollViewer? scroller;

    public FeedTimelineView()
    {
        var rail = new Rectangle
        {
            Width = 2,
            Fill = Brush("TzStrokeStrong"),
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Stretch,
            Margin = new Thickness(RailCenter - 1, 0, 0, 0),
            IsHitTestVisible = false,
        };
        list = new ListView
        {
            SelectionMode = ListViewSelectionMode.None,
            ItemTemplate = SlotTemplate,
            ItemsPanel = PanelTemplate,
            ItemContainerStyle = ContainerStyle,
            Padding = new Thickness(0, 0, 20, 8),
            TabNavigation = KeyboardNavigationMode.Once,
        };
        list.GroupStyle.Add(new GroupStyle { HidesIfEmpty = true, HeaderContainerStyle = HeaderItemStyle });
        source.Source = new List<FeedGroup>();
        list.ItemsSource = source.View;
        list.ContainerContentChanging += OnContainerChanging;
        list.ChoosingGroupHeaderContainer += OnChoosingHeader;
        list.Loaded += (_, _) => scroller ??= FindDescendant<ScrollViewer>(list);
        AutomationProperties.SetName(list, "New chapters by day");
        Children.Add(rail);
        Children.Add(list);
    }

    public event EventHandler<(FeedCardItem Item, FeedChipItem Chip, FrameworkElement Origin)>? ChipInvoked;

    public event EventHandler<(FeedCardItem Item, FrameworkElement? Cover)>? DetailsInvoked;

    public event EventHandler<(FeedCardItem Item, IReadOnlyList<long> UnreadIds)>? CardMarkRead;

    public event EventHandler<FeedGroup>? DayMarkRead;

    public int Count { get; private set; }

    public bool Present(IReadOnlyList<FeedGroup> groups)
    {
        var next = groups.SelectMany(g => g.Select(i => i.Signature).Prepend(g.Signature)).ToList();
        Count = groups.Sum(g => g.Count);
        if (next.SequenceEqual(signature))
        {
            return false;
        }
        var scroll = scroller ?? FindDescendant<ScrollViewer>(list);
        var offset = scroll?.VerticalOffset ?? 0;
        signature = next;
        source.Source = groups;
        list.ItemsSource = source.View;
        if (scroll is not null && offset > 0)
        {
            list.UpdateLayout();
            scroll.ChangeView(null, offset, null, true);
        }
        return true;
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId) =>
        cardByContainer.Values.Where(c => c.Item?.MangaId == mangaId).Select(c => c.CoverElement);

    private void OnChoosingHeader(ListViewBase sender, ChoosingGroupHeaderContainerEventArgs args)
    {
        var container = args.GroupHeaderContainer ?? new ListViewHeaderItem { Style = HeaderItemStyle };
        if (!headers.TryGetValue(container, out var header))
        {
            header = new DayHeader();
            header.MarkRead += (_, group) => DayMarkRead?.Invoke(this, group);
            headers[container] = header;
            container.Tag = header.Root;
        }
        if (args.Group is FeedGroup group)
        {
            header.Bind(group);
        }
        args.GroupHeaderContainer = container;
    }

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container || container.ContentTemplateRoot is not Grid slot)
        {
            return;
        }
        if (!cardByContainer.TryGetValue(container, out var card))
        {
            card = new FeedCard();
            slot.Children.Add(card);
            cardByContainer[container] = card;
            card.ChipInvoked += (_, e) => ChipInvoked?.Invoke(this, e);
            card.OpenDetails += (_, item) => DetailsInvoked?.Invoke(this, (item, card.CoverElement));
            card.MarkRead += (_, item) => CardMarkRead?.Invoke(this, (item, item.UnreadChapterIds));
            slot.Children.Add(new Rectangle
            {
                Width = CardIndent - RailCenter,
                Height = 2,
                Fill = Brush("TzStrokeStrong"),
                HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(RailCenter - CardIndent, 0, 0, 0),
                IsHitTestVisible = false,
            });
        }
        if (args.InRecycleQueue || args.Item is not FeedCardItem item)
        {
            return;
        }
        card.Bind(item);
        AutomationProperties.SetName(container, item.AccessibleName);
    }

    private sealed class DayHeader
    {
        private readonly Ellipse ring = new() { Width = 22, Height = 22, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        private readonly Ellipse dot = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        private readonly TextBlock day = new() { Style = StyleOf("SubtitleTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(CardIndent - 2 * RailCenter + 2, 0, 0, 0) };
        private readonly TextBlock counts = new()
        {
            Style = StyleOf("CaptionTextBlockStyle"),
            Foreground = Brush("TextFillColorSecondaryBrush"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(10, 3, 12, 0),
        };
        private readonly HyperlinkButton mark = new()
        {
            Content = "Mark read",
            FontSize = 13,
            Padding = new Thickness(8, 4, 8, 5),
            VerticalAlignment = VerticalAlignment.Center,
        };
        private FeedGroup? group;

        public DayHeader()
        {
            var dotHost = new Grid { Width = 2 * RailCenter, VerticalAlignment = VerticalAlignment.Stretch };
            dotHost.Children.Add(new Rectangle
            {
                Width = 2,
                Fill = Brush("TzStrokeStrong"),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Stretch,
                IsHitTestVisible = false,
            });
            dotHost.Children.Add(ring);
            dotHost.Children.Add(dot);
            Root = new Grid
            {
                Padding = new Thickness(0, 14, 0, 10),
                Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
                ColumnDefinitions =
                {
                    new ColumnDefinition { Width = GridLength.Auto },
                    new ColumnDefinition { Width = GridLength.Auto },
                    new ColumnDefinition { Width = GridLength.Auto },
                    new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                    new ColumnDefinition { Width = GridLength.Auto },
                },
            };
            Grid.SetColumn(dotHost, 0);
            Root.Children.Add(dotHost);
            Grid.SetColumn(day, 1);
            Root.Children.Add(day);
            Grid.SetColumn(counts, 2);
            Root.Children.Add(counts);
            Grid.SetColumn(mark, 4);
            Root.Children.Add(mark);
            mark.Click += (_, _) =>
            {
                if (group is { UnreadIds.Count: > 0 } g)
                {
                    MarkRead?.Invoke(this, g);
                }
            };
            AutomationProperties.SetHeadingLevel(day, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level2);
        }

        public event EventHandler<FeedGroup>? MarkRead;

        public Grid Root { get; }

        public void Bind(FeedGroup value)
        {
            group = value;
            day.Text = value.Header;
            counts.Text = value.CountLabel;
            dot.Width = value.Today ? 12 : 8;
            dot.Height = value.Today ? 12 : 8;
            dot.Fill = Brush(value.Today ? "AccentFillColorDefaultBrush" : "TextFillColorTertiaryBrush");
            ring.Fill = Brush("TzAccentSoft");
            ring.Visibility = value.Today ? Visibility.Visible : Visibility.Collapsed;
            mark.Visibility = value.UnreadIds.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
            AutomationProperties.SetName(Root, $"{value.Header}, {value.CountLabel}");
        }
    }

    private static T? FindDescendant<T>(DependencyObject start) where T : DependencyObject
    {
        var count = VisualTreeHelper.GetChildrenCount(start);
        for (var i = 0; i < count; i++)
        {
            var child = VisualTreeHelper.GetChild(start, i);
            if (child is T found)
            {
                return found;
            }
            var nested = FindDescendant<T>(child);
            if (nested is not null)
            {
                return nested;
            }
        }
        return default;
    }

    private static Brush Brush(string key) => (Brush)Application.Current.Resources[key];

    private static Style StyleOf(string key) => (Style)Application.Current.Resources[key];
}
