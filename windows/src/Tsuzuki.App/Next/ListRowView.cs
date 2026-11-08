using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Navigation;

namespace Tsuzuki.App.Next;

public sealed class ListRowGroup(string key, IEnumerable<ListRowItem> items) : List<ListRowItem>(items)
{
    public string Key { get; } = key;

    public override string ToString() => Key;
}

public sealed partial class ListRowView : GridView
{
    private const string Presentation = "xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation'";

    private static readonly DataTemplate SlotTemplate = (DataTemplate)XamlReader.Load($"<DataTemplate {Presentation}><Grid/></DataTemplate>");

    private static readonly ItemsPanelTemplate PanelTemplate = (ItemsPanelTemplate)XamlReader.Load(
        $"<ItemsPanelTemplate {Presentation}><ItemsWrapGrid Orientation='Horizontal' AreStickyGroupHeadersEnabled='False'/></ItemsPanelTemplate>");

    private static readonly Style HeaderStyle = (Style)XamlReader.Load($"""
        <Style {Presentation} TargetType='GridViewHeaderItem'>
            <Setter Property='Template'>
                <Setter.Value>
                    <ControlTemplate TargetType='GridViewHeaderItem'>
                        <ContentPresenter Margin='{"{"}TemplateBinding Padding{"}"}' Content='{"{"}TemplateBinding Content{"}"}' HorizontalAlignment='Stretch' />
                    </ControlTemplate>
                </Setter.Value>
            </Setter>
        </Style>
        """);

    private static readonly Style ContainerStyle = (Style)XamlReader.Load($"""
        <Style {Presentation} TargetType='GridViewItem'>
            <Setter Property='Margin' Value='0'/>
            <Setter Property='Padding' Value='0'/>
            <Setter Property='MinWidth' Value='0'/>
            <Setter Property='MinHeight' Value='0'/>
            <Setter Property='HorizontalContentAlignment' Value='Stretch'/>
            <Setter Property='VerticalContentAlignment' Value='Stretch'/>
        </Style>
        """);

    private readonly CollectionViewSource source = new() { IsSourceGrouped = true };
    private readonly Dictionary<DependencyObject, ListRow> rowByContainer = [];
    private IReadOnlyList<string> signature = [];
    private int columns = 1;

    public ListRowView(string name, string? secondaryLabel)
    {
        SecondaryLabel = secondaryLabel;
        AutomationProperties.SetName(this, name);
        SelectionMode = ListViewSelectionMode.None;
        IsItemClickEnabled = true;
        ItemTemplate = SlotTemplate;
        ItemsPanel = PanelTemplate;
        ItemContainerStyle = ContainerStyle;
        GroupStyle.Add(new GroupStyle { HidesIfEmpty = true, HeaderContainerStyle = HeaderStyle });
        Padding = new Thickness(36, 0, 36, 28);
        TabNavigation = Microsoft.UI.Xaml.Input.KeyboardNavigationMode.Once;
        source.Source = new List<ListRowGroup>();
        ItemsSource = source.View;
        ContainerContentChanging += OnContainerChanging;
        ChoosingGroupHeaderContainer += OnChoosingHeader;
        ItemClick += (_, e) =>
        {
            if (e.ClickedItem is ListRowItem item)
            {
                Invoke(item);
            }
        };
        SizeChanged += (_, _) => ApplyLayout();
        Loaded += (_, _) => ApplyLayout();
    }

    public event EventHandler<(ListRowItem Item, FrameworkElement? Cover)>? Invoked;

    public event EventHandler<ListRowItem>? SecondaryInvoked;

    public string? SecondaryLabel { get; }

    public int Count { get; private set; }

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
            foreach (var row in rowByContainer.Values)
            {
                row.Columns = value;
            }
            ApplyLayout();
        }
    }

    public bool Present(IReadOnlyList<ListRowGroup> groups)
    {
        var next = groups.SelectMany(g => g.Select(i => i.Signature).Prepend("#" + g.Key)).ToList();
        Count = groups.Sum(g => g.Count);
        if (next.SequenceEqual(signature))
        {
            return false;
        }
        signature = next;
        source.Source = groups;
        ItemsSource = source.View;
        return true;
    }

    private void ApplyLayout()
    {
        if (ItemsPanelRoot is not ItemsWrapGrid panel || ActualWidth <= 0)
        {
            return;
        }
        var width = Math.Max(0, ActualWidth - Padding.Left - Padding.Right - 1);
        panel.MaximumRowsOrColumns = columns;
        panel.ItemWidth = Math.Floor(width / columns);
        panel.ItemHeight = ListRow.RowHeight + (columns > 1 ? 8 : 0);
    }

    private void OnChoosingHeader(ListViewBase sender, ChoosingGroupHeaderContainerEventArgs args)
    {
        if (args.Group is not ListRowGroup group)
        {
            return;
        }
        if (args.GroupHeaderContainer is not GridViewHeaderItem header)
        {
            header = new GridViewHeaderItem
            {
                Style = HeaderStyle,
                Padding = new Thickness(2, 20, 0, 8),
                Margin = new Thickness(0),
                MinHeight = 0,
                HorizontalContentAlignment = HorizontalAlignment.Stretch,
                IsTabStop = false,
            };
            args.GroupHeaderContainer = header;
        }
        header.Content = new TextBlock { Text = group.Key, Style = (Style)Application.Current.Resources["BodyStrongTextBlockStyle"] };
        AutomationProperties.SetName(header, group.Key);
        AutomationProperties.SetHeadingLevel(header, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level2);
    }

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container || container.ContentTemplateRoot is not Grid slot)
        {
            return;
        }
        if (!rowByContainer.TryGetValue(container, out var row))
        {
            row = new ListRow { SecondaryLabel = SecondaryLabel, Columns = columns };
            slot.Children.Add(row);
            rowByContainer[container] = row;
            row.PrimaryInvoked += (_, item) => Invoke(item);
            row.SecondaryInvoked += (_, item) => SecondaryInvoked?.Invoke(this, item);
            container.GotFocus += (_, _) => row.Focused = true;
            container.LostFocus += (_, _) => row.Focused = false;
            var menu = new MenuFlyout();
            menu.Opening += (_, _) => FillMenu(menu, row.Item);
            container.ContextFlyout = menu;
        }
        if (args.InRecycleQueue || args.Item is not ListRowItem item)
        {
            return;
        }
        row.Bind(item);
        AutomationProperties.SetName(container, item.AccessibleName);
    }

    private void FillMenu(MenuFlyout menu, ListRowItem? item)
    {
        menu.Items.Clear();
        if (item is null)
        {
            return;
        }
        var symbols = (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];
        var open = new MenuFlyoutItem { Text = item.ActionLabel, Icon = new FontIcon { Glyph = "\uE768", FontFamily = symbols } };
        open.Click += (_, _) => Invoke(item);
        menu.Items.Add(open);
        if (SecondaryLabel is { } label && item.UnreadIds.Count > 0)
        {
            var secondary = new MenuFlyoutItem { Text = label, Icon = new FontIcon { Glyph = "\uE73E", FontFamily = symbols } };
            secondary.Click += (_, _) => SecondaryInvoked?.Invoke(this, item);
            menu.Items.Add(secondary);
        }
        var details = new MenuFlyoutItem { Text = "Open details", Icon = new FontIcon { Glyph = "\uE946", FontFamily = symbols } };
        details.Click += (_, _) => _ = Log.Guard("next-list-details", () => AppNav.OpenDetailsAsync(item.MangaId, CoverOf(item)));
        menu.Items.Add(details);
    }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId) =>
        rowByContainer.Values.Where(r => r.Item?.MangaId == mangaId).Select(r => r.Cover);

    private FrameworkElement? CoverOf(ListRowItem item) =>
        rowByContainer.Values.FirstOrDefault(r => ReferenceEquals(r.Item, item))?.Cover;

    private void Invoke(ListRowItem item) => Invoked?.Invoke(this, (item, CoverOf(item)));
}
