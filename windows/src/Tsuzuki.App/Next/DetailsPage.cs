using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Numerics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Presentation;
using Windows.ApplicationModel.DataTransfer;
using Windows.Foundation;
using Windows.System;
using Windows.UI;

namespace Tsuzuki.App.Next;

internal static class DetailsTheme
{
    public static readonly FontFamily Symbols = (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    public static Brush Brush(string key) => (Brush)Application.Current.Resources[key];

    public static Style Style(string key) => (Style)Application.Current.Resources[key];

    public static Color Color(string key) => Application.Current.Resources[key] switch
    {
        Color color => color,
        SolidColorBrush brush => brush.Color,
        var other => throw new InvalidCastException(key + " is " + other?.GetType().Name),
    };

    public static FontIcon Icon(string glyph, double size = 16) => new() { Glyph = glyph, FontFamily = Symbols, FontSize = size };
}

public sealed partial class DetailsPage : TzPage, ITzPage, ITransitionPage, ITransitionSkip, ITitledPage
{
    private const double PadX = 36;
    private const double BodyTop = 28;
    private const double AmbientHeight = 520;
    private const double CardFade = 140;


    private readonly long mangaId;
    private readonly DetailsTransition transition;
    private readonly DetailsHeaderStyle headerStyle = Services.Settings.DetailsHeader;
    private double? coverRatio;
    private readonly Task<DetailsSnapshot?> snapshot;
    private readonly GroupCollection rows = [];
    private readonly HashSet<string> selectedKeys = [];
    private readonly HashSet<string> expandedKeys = [];
    private readonly DetailsViewModel model;

    private readonly Grid root = new();
    private readonly Grid ambient = new() { Height = AmbientHeight, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false };
    private readonly Image ambientImage = new() { Stretch = Stretch.UniformToFill, HorizontalAlignment = HorizontalAlignment.Stretch, VerticalAlignment = VerticalAlignment.Stretch };
    private readonly Border ambientTint = new();
    private readonly Border ambientFade = new();
    private readonly ScrollView scroller = new() { HorizontalScrollMode = ScrollingScrollMode.Disabled, HorizontalScrollBarVisibility = ScrollingScrollBarVisibility.Hidden, VerticalScrollBarVisibility = ScrollingScrollBarVisibility.Auto };
    private readonly StackPanel body = new() { Padding = new Thickness(PadX, BodyTop, PadX, 96) };
    private readonly Grid header = new() { ColumnSpacing = 32 };
    private readonly Border coverFrame = new() { CornerRadius = new CornerRadius(8), VerticalAlignment = VerticalAlignment.Top, BorderThickness = new Thickness(1) };
    private readonly CoverImage headerCover = new() { DecodeWidth = 330 };
    private readonly StackPanel coverGhost = new() { Spacing = 10, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Visibility = Visibility.Collapsed };
    private readonly StackPanel info = new() { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Left, MaxWidth = 860 };
    private readonly TextBlock sourceLine = new();
    private readonly TextBlock titleText = new() { TextWrapping = TextWrapping.Wrap, MaxLines = 2, TextTrimming = TextTrimming.WordEllipsis, Margin = new Thickness(0, 4, 0, 0) };
    private readonly TextBlock authorText = new() { Margin = new Thickness(0, 6, 0, 0), TextWrapping = TextWrapping.Wrap };
    private readonly StackPanel pills = new() { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 16, 0, 0) };
    private readonly TextBlock chapterPillText = new();
    private readonly Border chapterPill;
    private readonly WrapPanel tags = new() { HorizontalSpacing = 6, VerticalSpacing = 6, Margin = new Thickness(0, 14, 0, 0) };
    private readonly Button continueButton = new() { Height = 36, Padding = new Thickness(16, 0, 18, 0) };
    private readonly TextBlock continueText = new() { Text = "Start reading", VerticalAlignment = VerticalAlignment.Center };
    private readonly ToggleButton libraryButton = new() { Height = 36, Padding = new Thickness(14, 0, 14, 0) };
    private readonly FontIcon libraryIcon = DetailsTheme.Icon("\uE710", 14);
    private readonly TextBlock libraryText = new() { Text = "Add to library", VerticalAlignment = VerticalAlignment.Center };
    private readonly Button categoryButton = new() { Height = 36, Padding = new Thickness(14, 0, 14, 0), Visibility = Visibility.Collapsed };
    private readonly Button overflowButton = new() { Width = 40, Height = 36, Padding = new Thickness(0) };
    private readonly SelectorBar tabs = new() { Margin = new Thickness(-12, 28, 0, 8) };
    private readonly SelectorBarItem chaptersTab = new() { Text = "Chapters", IsSelected = true };
    private readonly SelectorBarItem aboutTab = new() { Text = "About" };
    private readonly StackPanel chaptersPanel = new();
    private readonly StackPanel aboutPanel = new() { Spacing = 24, Visibility = Visibility.Collapsed, MaxWidth = 860, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 12, 0, 0) };
    private readonly TextBlock chapterSub = new() { VerticalAlignment = VerticalAlignment.Center };
    private readonly ToggleButton unreadOnly = new() { Content = "Unread only", Height = 32, Padding = new Thickness(12, 0, 12, 0) };
    private readonly ComboBox groupFilter = new() { MinWidth = 180, Height = 32, PlaceholderText = "All groups" };
    private readonly Button sortButton = new() { Height = 32, Padding = new Thickness(12, 0, 12, 0) };
    private readonly FontIcon sortIcon = DetailsTheme.Icon("\uE74B", 12);
    private readonly TextBlock sortText = new() { Text = "Newest first", VerticalAlignment = VerticalAlignment.Center };
    private readonly NumberBox jumpBox = new() { Width = 140, Height = 32, PlaceholderText = "Jump to Ch.", SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Hidden };
    private readonly Grid columnHeader = ChapterColumns.Header();
    private readonly ItemsRepeater chapterList = new() { Layout = new StackLayout { Spacing = 0 } };
    private readonly StackPanel skeleton = new() { Spacing = 6, Visibility = Visibility.Collapsed, Margin = new Thickness(0, 6, 0, 0) };
    private readonly TextBlock chapterMessage = new() { TextWrapping = TextWrapping.Wrap, Margin = new Thickness(12, 16, 0, 0), Visibility = Visibility.Collapsed };
    private readonly StackPanel stickyHost = new() { VerticalAlignment = VerticalAlignment.Top };
    private readonly Grid stickyBar = new() { Height = 60, ColumnSpacing = 14, Opacity = 0, IsHitTestVisible = false, Padding = new Thickness(PadX, 0, PadX, 0), BorderThickness = new Thickness(0, 0, 0, 1) };
    private readonly CoverImage stickyCover = new() { DecodeWidth = 64 };
    private readonly TextBlock stickyTitle = new() { VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly Button stickyContinue = new() { Height = 32, Padding = new Thickness(14, 0, 14, 0), VerticalAlignment = VerticalAlignment.Center };
    private readonly TextBlock stickyContinueText = new() { Text = "Start reading" };
    private readonly Border stickyColumns = new() { Visibility = Visibility.Collapsed, Padding = new Thickness(PadX, 0, PadX, 0), BorderThickness = new Thickness(0, 0, 0, 1) };
    private readonly Border selectionHost = new() { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 24), CornerRadius = new CornerRadius(8), BorderThickness = new Thickness(1), Padding = new Thickness(12, 0, 4, 0), Visibility = Visibility.Collapsed };
    private readonly TextBlock selectionCount = new() { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(4, 0, 12, 0) };
    private readonly CommandBar selectionBar = new() { DefaultLabelPosition = CommandBarDefaultLabelPosition.Right, OverflowButtonVisibility = CommandBarOverflowButtonVisibility.Collapsed, Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent), IsDynamicOverflowEnabled = false };
    private readonly AppBarButton selectionPrevious = new() { Label = "Mark previous as read", Icon = DetailsTheme.Icon("\uE762") };

    private MangaDetails? manga;
    private IReadOnlyList<ChapterRow> chapters = [];
    private IReadOnlyList<ChapterGroup> groups = [];
    private ChapterGroup? next;
    private string? groupFilterValue;
    private IReadOnlyList<string> scanlators = [];
    private string? anchorKey;
    private bool newestFirst = true;
    private bool stickyShown;
    private string? subBeforeRefresh;
    private int rowsSignature;
    private string? accentUrl;
    private string? blurUrl;
    private Color? ambientAccent;
    private CoverTint? cardTint;

    public DetailsPage(long mangaId, TransitionOrigin origin, Task<DetailsSnapshot?> snapshot)
    {
        this.mangaId = mangaId;
        this.snapshot = snapshot;
        var engine = Services.Engine!;
        model = new DetailsViewModel(mangaId, engine.Library, (id, details) => engine.Refresher.RefreshAsync(id, details), new DispatcherUi(DispatcherQueue), () => Ui.ReaderOpen);
        model.Changed += (_, change) => OnModelChanged(change);
        transition = new DetailsTransition(this, mangaId, origin);
        chapterPill = Pill(null, chapterPillText);
        PointerPressed += (_, _) => SkipTransition();
        BuildLayout();
        coverRatio = transition.KnownRatio;
        ApplyCoverShape();
        SizeChanged += (_, _) => ApplyCoverShape();
        headerCover.ImageLoaded += (_, _) =>
        {
            if (coverRatio is null && CoverImage.RatioOf(headerCover.Url) is { } decoded)
            {
                coverRatio = decoded;
                ApplyCoverShape();
            }
        };
        if (transition.Tile is { } data)
        {
            headerCover.SourceId = data.Source;
            SetTitle(data.Title);
        }
        if (transition.FromLibrary)
        {
            SetLibraryState(true);
        }
        if (transition.Cover is { Source: { } source } image)
        {
            headerCover.SourceId = image.SourceId;
            SetCover(image.Url, source);
        }
        else if (transition.Tile is { ThumbnailUrl: { } thumbnail })
        {
            SetCover(thumbnail);
        }
        if (CoverColors.TintOf(transition.Cover?.Bitmap, transition.Cover?.Url ?? transition.Tile?.ThumbnailUrl) is { } knownTint)
        {
            UseTint(knownTint);
        }
        header.SizeChanged += (_, _) => ApplyAmbient();
        DismissDrag.Attach(this, header, () => scroller.VerticalOffset < 1);
        BindSnapshotHeader();
        Loaded += OnLoadedWatch;
        Unloaded += OnUnloadedWatch;
    }

    public long MangaId => mangaId;

    public string Title => manga?.Title ?? titleText.Text;

    public static Task<DetailsSnapshot?> Fetch(long mangaId)
    {
        if (Services.Engine is not { } engine)
        {
            return Task.FromResult<DetailsSnapshot?>(null);
        }
        return Task.Run(async () =>
        {
            try
            {
                return await FetchCore(engine, mangaId);
            }
            catch (Exception e)
            {
                Log.Error("details fetch", e);
                return null;
            }
        });
    }

    private static async Task<DetailsSnapshot?> FetchCore(Tsuzuki.Core.Engine engine, long mangaId)
    {
        var m = await engine.Library.GetMangaAsync(mangaId);
        if (m is null)
        {
            return null;
        }
        if (string.IsNullOrWhiteSpace(m.ThumbnailUrl) && m.MergedRefs.Count > 0)
        {
            foreach (var reference in m.MergedRefs)
            {
                var childId = await engine.Library.FindMangaIdAsync(reference.Source, reference.Url);
                if (childId is not { } id)
                {
                    continue;
                }
                var child = await engine.Library.GetMangaAsync(id);
                if (!string.IsNullOrWhiteSpace(child?.ThumbnailUrl))
                {
                    m = m with { ThumbnailUrl = child.ThumbnailUrl };
                    break;
                }
            }
        }
        var chs = await engine.Library.GetChaptersAsync(mangaId);
        return new DetailsSnapshot(m, chs);
    }

    private void BuildLayout()
    {
        root.Background = DetailsTheme.Brush("SolidBackgroundFillColorBaseBrush");
        ambient.Children.Add(ambientImage);
        ambient.Children.Add(ambientTint);
        ambient.Children.Add(ambientFade);

        coverFrame.BorderBrush = DetailsTheme.Brush("CardStrokeColorDefaultBrush");
        coverFrame.Background = DetailsTheme.Brush("CardBackgroundFillColorDefaultBrush");
        coverGhost.Children.Add(new FontIcon { Glyph = "\uE8EA", FontFamily = DetailsTheme.Symbols, FontSize = 32, Foreground = DetailsTheme.Brush("TextFillColorTertiaryBrush") });
        coverGhost.Children.Add(new TextBlock { Text = "Cover on your phone", Style = DetailsTheme.Style("CaptionTextBlockStyle"), Foreground = DetailsTheme.Brush("TextFillColorTertiaryBrush"), HorizontalAlignment = HorizontalAlignment.Center });
        var coverGrid = new Grid();
        coverGrid.Children.Add(headerCover);
        coverGrid.Children.Add(coverGhost);
        coverFrame.Child = coverGrid;
        var shadow = new ThemeShadow();
        shadow.Receivers.Add(ambient);
        coverFrame.Shadow = shadow;
        coverFrame.Translation = new Vector3(0, 0, 32);
        AutomationProperties.SetAccessibilityView(coverFrame, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);

        sourceLine.Style = DetailsTheme.Style("CaptionTextBlockStyle");
        sourceLine.Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush");
        titleText.Style = DetailsTheme.Style("TitleLargeTextBlockStyle");
        titleText.MaxLines = 2;
        titleText.TextWrapping = TextWrapping.Wrap;
        titleText.TextTrimming = TextTrimming.WordEllipsis;
        AutomationProperties.SetHeadingLevel(titleText, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level1);
        authorText.Style = DetailsTheme.Style("BodyTextBlockStyle");
        authorText.Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush");
        authorText.Margin = new Thickness(0, 6, 0, 0);
        info.Children.Add(sourceLine);
        info.Children.Add(titleText);
        info.Children.Add(authorText);
        info.Children.Add(pills);
        info.Children.Add(tags);
        var actions = BuildActions();
        info.Children.Add(actions);

        header.Children.Add(coverFrame);
        header.Children.Add(info);
        if (headerStyle == DetailsHeaderStyle.Centred)
        {
            header.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            header.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            header.RowSpacing = 24;
            coverFrame.HorizontalAlignment = HorizontalAlignment.Center;
            Grid.SetRow(info, 1);
            info.HorizontalAlignment = HorizontalAlignment.Center;
            info.VerticalAlignment = VerticalAlignment.Top;
            info.MaxWidth = 760;
            foreach (var text in new[] { sourceLine, titleText, authorText })
            {
                text.TextAlignment = TextAlignment.Center;
                text.HorizontalAlignment = HorizontalAlignment.Center;
            }
            pills.HorizontalAlignment = HorizontalAlignment.Center;
            tags.HorizontalAlignment = HorizontalAlignment.Center;
            actions.HorizontalAlignment = HorizontalAlignment.Center;
        }
        else
        {
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            Grid.SetColumn(info, 1);
        }

        tabs.Items.Add(chaptersTab);
        tabs.Items.Add(aboutTab);
        AutomationProperties.SetName(tabs, "Details sections");
        tabs.SelectionChanged += (_, _) => ShowTab(tabs.SelectedItem == aboutTab);

        BuildChaptersPanel();

        body.Children.Add(header);
        body.Children.Add(tabs);
        body.Children.Add(chaptersPanel);
        body.Children.Add(aboutPanel);

        var content = new Grid();
        content.Children.Add(ambient);
        content.Children.Add(body);
        scroller.Content = content;
        scroller.ViewChanged += (_, _) => UpdateSticky();
        body.SizeChanged += (_, _) => UpdateSticky();
        root.Children.Add(scroller);

        BuildSticky();
        root.Children.Add(stickyHost);
        BuildSelectionBar();
        root.Children.Add(selectionHost);
        root.PreviewKeyDown += OnRootKeyDown;
        Content = root;

        ApplyAmbient();
    }

    private StackPanel BuildActions()
    {
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 24, 0, 0) };
        continueButton.Style = DetailsTheme.Style("AccentButtonStyle");
        continueButton.Content = IconText(DetailsTheme.Icon("\uE768", 14), continueText);
        continueText.FontWeight = FontWeights.SemiBold;
        AutomationProperties.SetName(continueButton, "Start reading");
        continueButton.Click += (_, _) => _ = Log.Guard("next-details-continue", () => ContinueAsync(continueButton));

        libraryButton.Content = IconText(libraryIcon, libraryText);
        AutomationProperties.SetName(libraryButton, "Add to library");
        libraryButton.Click += (_, _) => _ = Log.Guard("next-details-library", ToggleLibraryAsync);

        categoryButton.Content = IconText(DetailsTheme.Icon("\uE8EC", 14), new TextBlock { Text = "Categories", VerticalAlignment = VerticalAlignment.Center });
        AutomationProperties.SetName(categoryButton, "Categories");
        categoryButton.Click += (_, _) => _ = Log.Guard("next-details-categories", () => MangaActions.ShowCategoriesAsync(mangaId, categoryButton));

        overflowButton.Content = DetailsTheme.Icon("\uE712", 16);
        ToolTipService.SetToolTip(overflowButton, "More actions");
        AutomationProperties.SetName(overflowButton, "More actions");
        overflowButton.Click += (_, _) => ShowOverflow(overflowButton);

        actions.Children.Add(continueButton);
        actions.Children.Add(libraryButton);
        actions.Children.Add(categoryButton);
        actions.Children.Add(overflowButton);
        return actions;
    }

    private static StackPanel IconText(FontIcon icon, TextBlock text)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        icon.VerticalAlignment = VerticalAlignment.Center;
        panel.Children.Add(icon);
        panel.Children.Add(text);
        return panel;
    }

    private void BuildChaptersPanel()
    {
        chapterSub.Style = DetailsTheme.Style("CaptionTextBlockStyle");
        chapterSub.Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush");

        AutomationProperties.SetName(unreadOnly, "Unread only, off");
        unreadOnly.Click += (_, _) =>
        {
            A11y.NameToggle(unreadOnly, "Unread only");
            BindChapters(animate: false);
        };
        AutomationProperties.SetName(groupFilter, "Scanlation group");
        groupFilter.SelectionChanged += (_, _) =>
        {
            var value = groupFilter.SelectedIndex <= 0 ? null : groupFilter.SelectedItem as string;
            if (value == groupFilterValue)
            {
                return;
            }
            groupFilterValue = value;
            BindChapters(animate: false);
        };
        sortButton.Content = IconText(sortIcon, sortText);
        AutomationProperties.SetName(sortButton, "Sort: newest first");
        sortButton.Click += (_, _) =>
        {
            newestFirst = !newestFirst;
            sortText.Text = newestFirst ? "Newest first" : "Oldest first";
            sortIcon.Glyph = newestFirst ? "\uE74B" : "\uE74A";
            AutomationProperties.SetName(sortButton, "Sort: " + sortText.Text.ToLowerInvariant());
            BindChapters(animate: false);
        };
        AutomationProperties.SetName(jumpBox, "Jump to chapter number");
        jumpBox.ValueChanged += (_, e) =>
        {
            if (!double.IsNaN(e.NewValue))
            {
                JumpTo(e.NewValue);
            }
        };

        var filters = new Grid { Margin = new Thickness(0, 4, 0, 8), ColumnSpacing = 8 };
        filters.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        filters.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        filters.Children.Add(chapterSub);
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        controls.Children.Add(unreadOnly);
        controls.Children.Add(groupFilter);
        controls.Children.Add(sortButton);
        controls.Children.Add(jumpBox);
        Grid.SetColumn(controls, 1);
        filters.Children.Add(controls);

        columnHeader.BorderBrush = DetailsTheme.Brush("DividerStrokeColorDefaultBrush");
        columnHeader.BorderThickness = new Thickness(0, 0, 0, 1);
        columnHeader.Visibility = Visibility.Collapsed;

        chapterList.ItemTemplate = new ChapterTableFactory(g => selectedKeys.Contains(GroupKey(g)), () => selectedKeys.Count > 0, g => expandedKeys.Contains(GroupKey(g)), WireRow);
        chapterList.ItemsSource = rows;
        chapterList.XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled;
        chapterList.TabFocusNavigation = KeyboardNavigationMode.Once;
        AutomationProperties.SetName(chapterList, "Chapters");

        chapterMessage.Style = DetailsTheme.Style("BodyTextBlockStyle");
        chapterMessage.Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush");

        chaptersPanel.Children.Add(filters);
        chaptersPanel.Children.Add(columnHeader);
        chaptersPanel.Children.Add(chapterList);
        chaptersPanel.Children.Add(skeleton);
        chaptersPanel.Children.Add(chapterMessage);
    }

    private void BuildSticky()
    {
        stickyBar.Background = DetailsTheme.Brush("AcrylicBackgroundFillColorDefaultBrush");
        stickyBar.BorderBrush = DetailsTheme.Brush("DividerStrokeColorDefaultBrush");
        stickyBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        stickyBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        stickyBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var thumb = new Border { Width = 28, Height = 42, CornerRadius = new CornerRadius(4), VerticalAlignment = VerticalAlignment.Center, Child = stickyCover };
        stickyBar.Children.Add(thumb);
        stickyTitle.Style = DetailsTheme.Style("BodyStrongTextBlockStyle");
        Grid.SetColumn(stickyTitle, 1);
        stickyBar.Children.Add(stickyTitle);
        stickyContinue.Style = DetailsTheme.Style("AccentButtonStyle");
        stickyContinue.Content = IconText(DetailsTheme.Icon("\uE768", 12), stickyContinueText);
        AutomationProperties.SetName(stickyContinue, "Start reading");
        stickyContinue.Click += (_, _) => _ = Log.Guard("next-details-continue", () => ContinueAsync(stickyContinue));
        Grid.SetColumn(stickyContinue, 2);
        stickyBar.Children.Add(stickyContinue);
        stickyBar.Visibility = Visibility.Collapsed;

        stickyColumns.Background = DetailsTheme.Brush("AcrylicBackgroundFillColorDefaultBrush");
        stickyColumns.BorderBrush = DetailsTheme.Brush("DividerStrokeColorDefaultBrush");
        stickyColumns.Child = ChapterColumns.Header();
        stickyColumns.IsHitTestVisible = false;
        AutomationProperties.SetAccessibilityView(stickyColumns, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);

        stickyHost.Children.Add(stickyBar);
        stickyHost.Children.Add(stickyColumns);
    }

    private void BuildSelectionBar()
    {
        selectionHost.Background = DetailsTheme.Brush("AcrylicInAppFillColorDefaultBrush");
        selectionHost.BorderBrush = DetailsTheme.Brush("SurfaceStrokeColorFlyoutBrush");
        var shadow = new ThemeShadow();
        shadow.Receivers.Add(scroller);
        selectionHost.Shadow = shadow;
        selectionHost.Translation = new Vector3(0, 0, 32);
        selectionCount.Style = DetailsTheme.Style("BodyStrongTextBlockStyle");

        var markRead = new AppBarButton { Label = "Mark read", Icon = DetailsTheme.Icon("\uE8FB") };
        markRead.Click += (_, _) => _ = Log.Guard("next-details-select-read", () => MarkSelectedAsync(true));
        var markUnread = new AppBarButton { Label = "Mark unread", Icon = DetailsTheme.Icon("\uE7BA") };
        markUnread.Click += (_, _) => _ = Log.Guard("next-details-select-unread", () => MarkSelectedAsync(false));
        selectionPrevious.Click += (_, _) => _ = Log.Guard("next-details-select-previous", MarkPreviousOfSelectionAsync);
        var download = new AppBarButton { Label = "Download", Icon = DetailsTheme.Icon("\uE896") };
        download.Click += (_, _) => _ = Log.Guard("next-details-select-download", async () =>
        {
            await DownloadActions.DownloadAsync([.. SelectedGroups().Select(g => g.Primary)]);
            ClearSelection();
        });
        var selectAll = new AppBarButton { Label = "Select all", Icon = DetailsTheme.Icon("\uE8B3") };
        selectAll.Click += (_, _) => SelectAll();
        var clear = new AppBarButton { Label = "Cancel", Icon = DetailsTheme.Icon("\uE711") };
        clear.Click += (_, _) => ClearSelection();
        foreach (var button in new[] { markRead, markUnread, selectionPrevious, download, selectAll, clear })
        {
            AutomationProperties.SetName(button, button.Label);
            selectionBar.PrimaryCommands.Add(button);
        }
        selectionBar.Content = selectionCount;
        AutomationProperties.SetName(selectionBar, "Selected chapters");
        selectionHost.Child = selectionBar;
    }

    private void WireRow(ChapterTableRow row)
    {
        row.OpenRequested += (s, args) =>
        {
            var view = (ChapterTableRow)s!;
            if (view.Group is { } group)
            {
                _ = Log.Guard("next-details-open", () => MangaActions.OpenGroupAsync(mangaId, group, view));
            }
        };
        row.SelectRequested += (_, e) => Select(e);
        row.ExpandRequested += (s, _) =>
        {
            if (((ChapterTableRow)s!).Group is { } group && !expandedKeys.Remove(GroupKey(group)))
            {
                expandedKeys.Add(GroupKey(group));
            }
            BindChapters(animate: false);
        };
        row.ToggleReadRequested += (s, args) =>
        {
            if (((ChapterTableRow)s!).Group is { } group)
            {
                _ = Log.Guard("next-details-mark", () => MarkAsync([group], !group.Read));
            }
        };
        row.MarkPreviousRequested += (s, args) =>
        {
            if (((ChapterTableRow)s!).Group is { } group)
            {
                _ = Log.Guard("next-details-mark-previous", () => MarkPreviousAsync(group));
            }
        };
        row.MenuRequested += (s, anchor) => ShowChapterMenu((ChapterTableRow)s!, anchor);
    }

    public static string GroupKey(ChapterGroup group) =>
        group.Nested ? "c:" + group.Primary.ChapterId
        : group.Number >= 0 ? "n:" + Math.Round(group.Number, 3).ToString(System.Globalization.CultureInfo.InvariantCulture) : "u:" + group.Primary.Url;

    private void SetTitle(string title)
    {
        if (titleText.Text == title)
        {
            return;
        }
        titleText.Text = title;
        ToolTipService.SetToolTip(titleText, title);
        stickyTitle.Text = title;
        if (IsLoaded)
        {
            App.MainWindow.RefreshTitle();
        }
    }

    private void SetLibraryState(bool inLibrary)
    {
        libraryButton.IsChecked = inLibrary;
        libraryText.Text = inLibrary ? "In library" : "Add to library";
        libraryIcon.Glyph = inLibrary ? "\uE73E" : "\uE710";
        AutomationProperties.SetName(libraryButton, inLibrary ? "In library, select to remove" : "Add to library");
        categoryButton.Visibility = inLibrary ? Visibility.Visible : Visibility.Collapsed;
    }

    private void BindSnapshotHeader()
    {
        if (manga is null && snapshot.IsCompletedSuccessfully && snapshot.Result is { } data)
        {
            manga = data.Manga;
            BindHeader();
        }
    }

    private void SetCover(string? url, ImageSource? initialSource = null)
    {
        if (initialSource is not null)
        {
            headerCover.SetSource(initialSource, url);
        }
        else
        {
            headerCover.Url = url;
        }
        stickyCover.SourceId = headerCover.SourceId;
        stickyCover.Url = url;
        coverGhost.Visibility = string.IsNullOrWhiteSpace(url) ? Visibility.Visible : Visibility.Collapsed;
        if (blurUrl != url)
        {
            blurUrl = url;
            var sourceId = headerCover.SourceId;
            _ = Log.Guard("next-details-blur", async () =>
            {
                var blurred = await CoverBlur.LoadAsync(url, sourceId);
                if (blurUrl == url)
                {
                    ambientImage.Source = blurred;
                    if (blurred is not null)
                    {
                        _ = Motion.FadeIn(ambientImage, Motion.Medium1);
                    }
                }
            });
        }
    }

    private void ApplyAmbient()
    {
        var bg = DetailsTheme.Color("SolidBackgroundFillColorBase");
        var light = ActualTheme == ElementTheme.Light || (ActualTheme == ElementTheme.Default && Application.Current.RequestedTheme == ApplicationTheme.Light);
        if (cardTint is { } tint)
        {
            var card = tint.Card.ToColor();
            var headerBottom = BodyTop + header.ActualHeight;
            var height = headerBottom + CardFade;
            ambient.Height = height;
            ambientImage.Opacity = 0;
            ambientTint.Background = null;
            ambientFade.Background = Gradient([(card, 0), (card, (headerBottom - 24) / height), (bg, 1)]);
            return;
        }
        ambient.Height = AmbientHeight;
        ambientImage.Opacity = light ? 0.4 : 0.75;
        ambientFade.Background = Gradient(light
            ? [(Color.FromArgb(110, bg.R, bg.G, bg.B), 0), (Color.FromArgb(200, bg.R, bg.G, bg.B), 0.55), (Color.FromArgb(255, bg.R, bg.G, bg.B), 1)]
            : [(Color.FromArgb(90, 0, 0, 0), 0), (Color.FromArgb(190, bg.R, bg.G, bg.B), 0.55), (Color.FromArgb(255, bg.R, bg.G, bg.B), 1)]);
        if (ambientAccent is { } accent)
        {
            ambientTint.Background = Gradient([(Color.FromArgb(light ? (byte)50 : (byte)80, accent.R, accent.G, accent.B), 0), (Color.FromArgb(0, accent.R, accent.G, accent.B), 0.8)]);
        }
        else
        {
            ambientTint.Background = null;
        }
    }

    private void UseTint(CoverTint? tint)
    {
        cardTint = Services.Settings.DetailsCoverColour ? tint : null;
        ambientAccent = tint?.Accent.ToColor();
        tint = cardTint;
        header.RequestedTheme = tint is null ? ElementTheme.Default : ElementTheme.Dark;
        if (tint is not null)
        {
            var white = Microsoft.UI.Colors.White;
            var card = tint.Card.ToColor();
            var glass = tint.Glass.ToColor();
            Paint(continueButton, "AccentButton", "", white, Color.FromArgb(232, 255, 255, 255), Color.FromArgb(204, 255, 255, 255), card);
            foreach (var button in new Control[] { categoryButton, overflowButton, libraryButton })
            {
                Paint(button, button is ToggleButton ? "ToggleButton" : "Button", "", glass, tint.Glass.ToColor(224), card, white);
            }
            Paint(libraryButton, "ToggleButton", "Checked", white, Color.FromArgb(232, 255, 255, 255), Color.FromArgb(204, 255, 255, 255), card);
        }
        else
        {
            foreach (var button in new Control[] { continueButton, libraryButton, categoryButton, overflowButton })
            {
                button.Resources.Clear();
            }
        }
        foreach (var button in new Control[] { continueButton, libraryButton, categoryButton, overflowButton })
        {
            button.RequestedTheme = ElementTheme.Light;
            button.RequestedTheme = ElementTheme.Default;
        }
        RestyleHeader();
        ApplyAmbient();
    }

    private static void Paint(Control button, string stem, string state, Color rest, Color hover, Color pressed, Color text)
    {
        var phases = new[] { "", "PointerOver", "Pressed" };
        var fills = new[] { rest, hover, pressed };
        var rim = new SolidColorBrush(Color.FromArgb(56, 255, 255, 255));
        for (var i = 0; i < phases.Length; i++)
        {
            var suffix = state + phases[i];
            button.Resources[$"{stem}Background{suffix}"] = new SolidColorBrush(fills[i]);
            button.Resources[$"{stem}Foreground{suffix}"] = new SolidColorBrush(text);
            button.Resources[$"{stem}BorderBrush{suffix}"] = rim;
        }
    }

    private void RestyleHeader()
    {
        var onCard = cardTint is not null;
        Brush primary = onCard ? new SolidColorBrush(Microsoft.UI.Colors.White) : DetailsTheme.Brush("TextFillColorPrimaryBrush");
        Brush secondary = onCard ? new SolidColorBrush(Color.FromArgb(214, 255, 255, 255)) : DetailsTheme.Brush("TextFillColorSecondaryBrush");
        Brush chip = onCard ? new SolidColorBrush(Color.FromArgb(36, 255, 255, 255)) : DetailsTheme.Brush("SubtleFillColorSecondaryBrush");
        Brush pill = onCard ? new SolidColorBrush(Color.FromArgb(30, 255, 255, 255)) : DetailsTheme.Brush("ControlFillColorSecondaryBrush");
        Brush rim = onCard ? new SolidColorBrush(Color.FromArgb(46, 255, 255, 255)) : DetailsTheme.Brush("CardStrokeColorDefaultBrush");
        titleText.Foreground = primary;
        sourceLine.Foreground = secondary;
        authorText.Foreground = secondary;
        foreach (var item in pills.Children.OfType<Border>())
        {
            item.Background = pill;
            item.BorderBrush = rim;
            Recolour(item.Child, primary);
        }
        foreach (var item in tags.Children)
        {
            switch (item)
            {
                case Border plain:
                    plain.Background = chip;
                    Recolour(plain.Child, secondary);
                    break;
                case HyperlinkButton link:
                    link.Background = chip;
                    link.Foreground = primary;
                    Recolour(link.Content as UIElement, primary);
                    break;
            }
        }
    }

    private static void Recolour(UIElement? element, Brush brush)
    {
        switch (element)
        {
            case TextBlock text:
                text.Foreground = brush;
                break;
            case FontIcon icon:
                icon.Foreground = brush;
                break;
            case Panel panel:
                foreach (var child in panel.Children)
                {
                    if (child is not Microsoft.UI.Xaml.Shapes.Shape)
                    {
                        Recolour(child, brush);
                    }
                }
                break;
        }
    }

    private static LinearGradientBrush Gradient((Color Color, double Offset)[] stops)
    {
        var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(0, 1) };
        foreach (var (color, offset) in stops)
        {
            brush.GradientStops.Add(new GradientStop { Color = color, Offset = offset });
        }
        return brush;
    }

    private void BuildSkeleton()
    {
        skeleton.Children.Clear();
        List<UIElement> ghosts = [];
        for (var i = 0; i < 6; i++)
        {
            var ghost = new Border { Height = 36, CornerRadius = new CornerRadius(4), Background = DetailsTheme.Brush("ControlFillColorSecondaryBrush"), Opacity = 1 - i * 0.14 };
            skeleton.Children.Add(ghost);
            ghosts.Add(ghost);
        }
        skeleton.Visibility = Visibility.Visible;
        Design.Skeleton.Pulse(ghosts);
    }

    private void ShowMessage(string? text)
    {
        chapterMessage.Text = text ?? string.Empty;
        chapterMessage.Visibility = text is null ? Visibility.Collapsed : Visibility.Visible;
    }

    private async Task LoadAsync(Task chaptersAfter)
    {
        if (Services.Engine is null)
        {
            return;
        }
        if (!snapshot.IsCompleted && await Task.WhenAny(snapshot, Task.Delay(100)) != snapshot)
        {
            BuildSkeleton();
        }
        var data = await snapshot;
        if (data is null)
        {
            skeleton.Visibility = Visibility.Collapsed;
            ShowMessage("This title is no longer in your library.");
            return;
        }
        if (manga is null)
        {
            manga = data.Manga;
            BindHeader();
        }
        chapters = data.Chapters;
        await chaptersAfter;
        BindChapters(animate: true);
        await model.StartAsync(snapshot);
    }

    private void OnModelChanged(DetailsChange change)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        switch (change.Kind)
        {
            case DetailsChangeKind.RefreshStarted:
                subBeforeRefresh = chapterSub.Text;
                chapterSub.Text = "Checking for new chapters…";
                break;
            case DetailsChangeKind.Refreshed:
                manga = model.Manga ?? manga;
                BindHeader();
                chapters = model.Chapters;
                BindChapters(animate: chapters.Count > 0 && rows.Count == 0);
                if (change.Result is null && chapters.Count == 0)
                {
                    ShowMessage(manga is not null && !engine.Sources.IsReadable(manga.Source) && manga.Source != LibraryRefresher.MergedSourceId
                        ? $"{engine.Sources.DisplayName(manga.Source)} reads on your phone. Your progress still syncs here."
                        : "No chapters yet.");
                }
                UpdateChapterSub();
                break;
            case DetailsChangeKind.RefreshFailed:
                chapterSub.Text = subBeforeRefresh ?? chapterSub.Text;
                if (chapters.Count == 0)
                {
                    skeleton.Visibility = Visibility.Collapsed;
                    ShowMessage("Couldn't reach the source. Check your connection and try again.");
                }
                UpdateChapterSub();
                break;
            case DetailsChangeKind.LibraryChanged:
                manga = model.Manga ?? manga;
                chapters = model.Chapters;
                BindHeader();
                BindChapters(animate: false);
                break;
        }
    }

    private Task RefreshAsync(bool includeDetails) => model.RefreshAsync(includeDetails);

    private long BrowseSource()
    {
        if (manga is null || Services.Engine is not { } engine)
        {
            return 0;
        }
        var candidates = new[] { manga.Source }.Concat(manga.MergedRefs.Select(r => r.Source));
        return candidates.FirstOrDefault(id => id != LibraryRefresher.MergedSourceId && engine.Sources.Get(id) is not null && engine.Sources.IsReadable(id));
    }

    private void BindHeader()
    {
        if (manga is null || Services.Engine is not { } engine)
        {
            return;
        }
        var merged = manga.MergedRefs.Count > 0;
        var sourceName = merged
            ? $"Merged from {manga.MergedRefs.Count(r => !(r.Source == manga.Source && r.Url == manga.Url))} sources"
            : engine.Sources.DisplayName(manga.Source);
        sourceLine.Text = sourceName;
        SetTitle(manga.Title);
        var people = People(manga);
        authorText.Text = string.Join(", ", people);
        headerCover.SourceId = merged ? manga.MergedRefs.FirstOrDefault(r => r.IsInfoManga)?.Source ?? manga.Source : manga.Source;
        if (headerCover.Url != manga.ThumbnailUrl)
        {
            SetCover(manga.ThumbnailUrl);
        }

        var (rating, description) = SplitDescription(manga.Description);
        pills.Children.Clear();
        pills.Children.Add(Pill(StatusDot(manga.Status), Caption(StatusLabel(manga.Status))));
        pills.Children.Add(Pill(DetailsTheme.Icon(merged ? "\uE71B" : "\uE774", 12), Caption(merged ? "Merged" : sourceName)));
        pills.Children.Add(chapterPill);
        if (rating is not null)
        {
            pills.Children.Add(Pill(DetailsTheme.Icon("\uE735", 12), Caption(rating)));
        }
        var phoneOnly = manga.Source != LibraryRefresher.MergedSourceId && !engine.Sources.IsReadable(manga.Source);
        if (phoneOnly)
        {
            pills.Children.Add(Pill(DetailsTheme.Icon("\uE8EA", 12), Caption("Reads on your phone")));
        }

        tags.Children.Clear();
        var browseSource = BrowseSource();
        var browseName = browseSource != 0 ? engine.Sources.DisplayName(browseSource) : null;
        foreach (var genre in Genres(manga).Take(14))
        {
            tags.Children.Add(Tag(genre, browseSource, browseName));
        }
        tags.Visibility = tags.Children.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        RestyleHeader();

        SetLibraryState(manga.Favorite);
        BindAbout(engine, description, people, phoneOnly);
        if (accentUrl != manga.ThumbnailUrl)
        {
            accentUrl = manga.ThumbnailUrl;
            _ = Log.Guard("next-details-accent", () => ApplyAccentAsync(manga.ThumbnailUrl, manga.Source));
        }
    }

    private static List<string> People(MangaDetails m) =>
        new[] { m.Author, m.Artist }
            .Where(p => !string.IsNullOrWhiteSpace(p))
            .SelectMany(p => p!.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

    private static IEnumerable<string> Genres(MangaDetails m) =>
        m.Genres
            .Where(g => !g.StartsWith("Content Rating:", StringComparison.OrdinalIgnoreCase) && !g.StartsWith("Original Language:", StringComparison.OrdinalIgnoreCase))
            .Select(g => g.IndexOf(": ", StringComparison.Ordinal) is var i and >= 0 ? g[(i + 2)..] : g)
            .Distinct(StringComparer.OrdinalIgnoreCase);

    private static (string? Rating, string Description) SplitDescription(string? raw)
    {
        var lines = (raw ?? string.Empty).Trim().Split('\n');
        string? rating = null;
        var noise = 0;
        while (noise < lines.Length)
        {
            var line = lines[noise].Trim();
            if (line.StartsWith("Rating:", StringComparison.OrdinalIgnoreCase))
            {
                var value = line["Rating:".Length..].Trim();
                rating = value.Length > 0 ? value : null;
            }
            else if (!line.StartsWith("Bookmarks:", StringComparison.OrdinalIgnoreCase))
            {
                break;
            }
            noise++;
        }
        return (rating, string.Join('\n', lines.Skip(noise)).Trim());
    }

    private UIElement Tag(string genre, long browseSource, string? browseName)
    {
        if (browseSource == 0)
        {
            var plain = new Border
            {
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(8, 3, 8, 4),
                Background = DetailsTheme.Brush("SubtleFillColorSecondaryBrush"),
                Child = new TextBlock { Text = genre, Style = DetailsTheme.Style("CaptionTextBlockStyle"), Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush") },
            };
            return plain;
        }
        var button = new HyperlinkButton
        {
            Content = new TextBlock { Text = genre, Style = DetailsTheme.Style("CaptionTextBlockStyle") },
            Padding = new Thickness(8, 3, 8, 4),
            MinHeight = 0,
            CornerRadius = new CornerRadius(4),
            Background = DetailsTheme.Brush("SubtleFillColorSecondaryBrush"),
        };
        ToolTipService.SetToolTip(button, $"Search {browseName} for {genre}");
        AutomationProperties.SetName(button, $"Search {browseName} for {genre}");
        button.Click += (_, _) => _ = Log.Guard("next-details-genre", () => AppNav.OpenBrowseAsync(browseSource, genre));
        return button;
    }

    private void BindAbout(Tsuzuki.Core.Engine engine, string description, IReadOnlyList<string> people, bool phoneOnly)
    {
        aboutPanel.Children.Clear();
        if (description.Length > 0)
        {
            aboutPanel.Children.Add(Section("Description", new TextBlock
            {
                Text = description,
                Style = DetailsTheme.Style("BodyTextBlockStyle"),
                TextWrapping = TextWrapping.Wrap,
                IsTextSelectionEnabled = true,
            }));
        }
        var facts = new Grid { ColumnSpacing = 24, RowSpacing = 10 };
        facts.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(140) });
        facts.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        void Fact(string label, string value)
        {
            var row = facts.RowDefinitions.Count;
            facts.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var key = new TextBlock { Text = label, Style = DetailsTheme.Style("BodyTextBlockStyle"), Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush") };
            var text = new TextBlock { Text = value, Style = DetailsTheme.Style("BodyTextBlockStyle"), TextWrapping = TextWrapping.Wrap, IsTextSelectionEnabled = true };
            Grid.SetRow(key, row);
            Grid.SetRow(text, row);
            Grid.SetColumn(text, 1);
            facts.Children.Add(key);
            facts.Children.Add(text);
        }
        if (manga!.Author is { Length: > 0 } author)
        {
            Fact("Author", author);
        }
        if (manga.Artist is { Length: > 0 } artist && !string.Equals(artist, manga.Author, StringComparison.OrdinalIgnoreCase))
        {
            Fact("Artist", artist);
        }
        else if (people.Count > 0 && manga.Author is not { Length: > 0 })
        {
            Fact("People", string.Join(", ", people));
        }
        Fact("Status", StatusLabel(manga.Status));
        Fact("Source", manga.Source == LibraryRefresher.MergedSourceId ? "Merged title" : engine.Sources.DisplayName(manga.Source));
        if (manga.MergedRefs.Count > 0)
        {
            var sources = manga.MergedRefs.Select(r => engine.Sources.DisplayName(r.Source)).Distinct().ToList();
            Fact("Merged from", string.Join(", ", sources));
        }
        if (manga.Favorite && manga.DateAdded > 0)
        {
            Fact("Added", Format.Date(manga.DateAdded));
        }
        var genres = Genres(manga).ToList();
        if (genres.Count > 0)
        {
            Fact("Genres", string.Join(", ", genres));
        }
        aboutPanel.Children.Add(Section("Information", facts));
        if (phoneOnly)
        {
            aboutPanel.Children.Add(new InfoBar
            {
                IsOpen = true,
                IsClosable = false,
                Severity = InfoBarSeverity.Informational,
                Title = "Reads on your phone",
                Message = $"{engine.Sources.DisplayName(manga.Source)} isn't available on Windows yet. Your progress still syncs here.",
            });
        }
    }

    private static StackPanel Section(string title, UIElement content)
    {
        var panel = new StackPanel { Spacing = 10 };
        var heading = new TextBlock { Text = title, Style = DetailsTheme.Style("SubtitleTextBlockStyle") };
        AutomationProperties.SetHeadingLevel(heading, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level2);
        panel.Children.Add(heading);
        panel.Children.Add(content);
        return panel;
    }

    private void ShowTab(bool about)
    {
        chaptersPanel.Visibility = about ? Visibility.Collapsed : Visibility.Visible;
        aboutPanel.Visibility = about ? Visibility.Visible : Visibility.Collapsed;
        if (about)
        {
            ClearSelection();
        }
        _ = Motion.FadeIn(about ? aboutPanel : chaptersPanel, Motion.Short4, Motion.Reduced ? 0 : 12);
        UpdateSticky();
    }

    private async Task ApplyAccentAsync(string? url, long sourceId)
    {
        var tint = await CoverColors.TintAsync(url, sourceId);
        if (url != accentUrl)
        {
            return;
        }
        UseTint(tint);
    }

    private static TextBlock Caption(string text) =>
        new() { Text = text, Style = DetailsTheme.Style("CaptionTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center };

    private static Border Pill(UIElement? lead, TextBlock text)
    {
        text.Style ??= DetailsTheme.Style("CaptionTextBlockStyle");
        text.VerticalAlignment = VerticalAlignment.Center;
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        if (lead is not null)
        {
            if (lead is FrameworkElement fe)
            {
                fe.VerticalAlignment = VerticalAlignment.Center;
            }
            panel.Children.Add(lead);
        }
        panel.Children.Add(text);
        return new Border
        {
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(10, 4, 10, 5),
            Background = DetailsTheme.Brush("ControlFillColorSecondaryBrush"),
            BorderBrush = DetailsTheme.Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            Child = panel,
        };
    }

    private static Ellipse StatusDot(MangaStatus status) => new()
    {
        Width = 8,
        Height = 8,
        Fill = DetailsTheme.Brush(status switch
        {
            MangaStatus.Ongoing => "SystemFillColorSuccessBrush",
            MangaStatus.Completed or MangaStatus.PublishingFinished => "AccentFillColorDefaultBrush",
            MangaStatus.Cancelled => "SystemFillColorCriticalBrush",
            MangaStatus.OnHiatus => "SystemFillColorCautionBrush",
            _ => "TextFillColorTertiaryBrush",
        }),
    };

    private static string StatusLabel(MangaStatus status) => status switch
    {
        MangaStatus.Ongoing => "Ongoing",
        MangaStatus.Completed => "Completed",
        MangaStatus.Licensed => "Licensed",
        MangaStatus.PublishingFinished => "Publishing finished",
        MangaStatus.Cancelled => "Cancelled",
        MangaStatus.OnHiatus => "On hiatus",
        _ => "Status unknown",
    };

    private void BindChapters(bool animate)
    {
        groups = ChapterGrouping.Group(chapters, mangaId);
        next = ChapterGrouping.NextToRead(groups);
        BindGroupFilter();
        IEnumerable<ChapterGroup> visible = groups;
        if (unreadOnly.IsChecked == true)
        {
            visible = visible.Where(g => !g.Read && (g.Primary.Readable || g.Alternates.Any(a => a.Readable)));
        }
        if (groupFilterValue is { } scan)
        {
            visible = visible.Where(g => g.Primary.Scanlator?.Trim() == scan || g.Alternates.Any(a => a.Scanlator?.Trim() == scan));
        }
        if (!newestFirst)
        {
            visible = visible.Reverse();
        }
        var list = visible
            .SelectMany(g => expandedKeys.Contains(GroupKey(g)) && g.Alternates.Count > 0
                ? g.Alternates.Select(a => new ChapterGroup(a, [], Nested: true)).Prepend(g)
                : new[] { g })
            .ToList();
        var known = groups.SelectMany(g => g.Alternates.Select(a => "c:" + a.ChapterId).Prepend(GroupKey(g))).ToHashSet();
        expandedKeys.IntersectWith(known);
        if (selectedKeys.RemoveWhere(k => !known.Contains(k)) > 0)
        {
            UpdateSelectionBar();
        }
        var signature = Signature(list);
        var changed = signature != rowsSignature || rows.Count != list.Count;
        if (changed)
        {
            rowsSignature = signature;
            ApplyRows(list);
        }
        columnHeader.Visibility = groups.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        chapterPillText.Text = groups.Count == 1 ? "1 chapter" : $"{groups.Count} chapters";
        chapterPill.Visibility = groups.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        UpdateChapterSub();
        if (chapters.Count > 0)
        {
            skeleton.Visibility = Visibility.Collapsed;
            ShowMessage(list.Count == 0 ? "No chapters match these filters." : null);
            if (animate && changed)
            {
                _ = Motion.FadeIn(chapterList, Motion.Medium2, Motion.Reduced ? 0 : 12);
            }
        }
        else if (!model.Refreshing)
        {
            skeleton.Visibility = Visibility.Collapsed;
        }
        UpdateContinue();
    }

    private void BindGroupFilter()
    {
        var names = chapters
            .Select(c => c.Scanlator?.Trim())
            .Where(s => !string.IsNullOrEmpty(s))
            .Select(s => s!)
            .Distinct(StringComparer.Ordinal)
            .OrderBy(s => s, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
        groupFilter.Visibility = names.Count > 1 ? Visibility.Visible : Visibility.Collapsed;
        if (names.SequenceEqual(scanlators))
        {
            return;
        }
        scanlators = names;
        if (groupFilterValue is not null && !names.Contains(groupFilterValue))
        {
            groupFilterValue = null;
        }
        groupFilter.Items.Clear();
        groupFilter.Items.Add("All groups");
        foreach (var name in names)
        {
            groupFilter.Items.Add(name);
        }
        groupFilter.SelectedIndex = groupFilterValue is null ? 0 : names.IndexOf(groupFilterValue) + 1;
    }

    private int GroupSignature(ChapterGroup group)
    {
        var c = group.Primary;
        var hash = new HashCode();
        hash.Add(expandedKeys.Contains(GroupKey(group)));
        hash.Add(c.ChapterId);
        hash.Add(c.Name);
        hash.Add(c.Scanlator);
        hash.Add(c.DateUpload);
        hash.Add(c.PageCount);
        hash.Add(c.Readable);
        hash.Add(c.ExternalUrl);
        hash.Add(group.Read);
        foreach (var copy in group.Alternates)
        {
            hash.Add(copy.ChapterId);
            hash.Add(copy.LastPageRead);
        }
        hash.Add(c.LastPageRead);
        return hash.ToHashCode();
    }

    private int Signature(List<ChapterGroup> list)
    {
        var hash = new HashCode();
        hash.Add(list.Count);
        foreach (var group in list)
        {
            hash.Add(GroupKey(group));
            hash.Add(GroupSignature(group));
        }
        return hash.ToHashCode();
    }

    private void ApplyRows(List<ChapterGroup> target)
    {
        if (rows.Count == 0)
        {
            rows.ReplaceAll(target);
            return;
        }
        var targetKeys = new HashSet<string>(target.Select(GroupKey));
        var currentKeys = new HashSet<string>(rows.Select(GroupKey));
        var keptCurrent = rows.Select(GroupKey).Where(targetKeys.Contains);
        var keptTarget = target.Select(GroupKey).Where(currentKeys.Contains);
        if (targetKeys.Count != target.Count || currentKeys.Count != rows.Count || !keptCurrent.SequenceEqual(keptTarget))
        {
            rows.ReplaceAll(target);
            return;
        }
        for (var i = rows.Count - 1; i >= 0; i--)
        {
            if (!targetKeys.Contains(GroupKey(rows[i])))
            {
                rows.RemoveAt(i);
            }
        }
        for (var i = 0; i < target.Count; i++)
        {
            if (i < rows.Count && GroupKey(rows[i]) == GroupKey(target[i]))
            {
                if (GroupSignature(rows[i]) != GroupSignature(target[i]))
                {
                    rows[i] = target[i];
                }
            }
            else
            {
                rows.Insert(i, target[i]);
            }
        }
    }

    private void UpdateChapterSub()
    {
        if (model.Refreshing)
        {
            return;
        }
        var unread = groups.Count(g => !g.Read);
        var count = groups.Count == 1 ? "1 chapter" : $"{groups.Count} chapters";
        chapterSub.Text = groups.Count == 0 ? string.Empty : unread == 0 ? count + " · all read" : $"{count} · {unread} unread";
    }

    private void UpdateContinue()
    {
        string label;
        string shortLabel;
        string glyph = "\uE768";
        if (next is null)
        {
            label = "No chapters";
            shortLabel = label;
            continueButton.IsEnabled = false;
            stickyContinue.IsEnabled = false;
        }
        else
        {
            continueButton.IsEnabled = true;
            stickyContinue.IsEnabled = true;
            var number = next.Number >= 0 ? "Ch. " + Format.ChapterNumber(next.Number) : next.Primary.Name;
            var anyRead = groups.Any(g => g.LastReadAt > 0 || g.Read);
            if (next.InProgress)
            {
                var page = Math.Max(next.Primary.LastPageRead, next.Alternates.Select(a => a.LastPageRead).DefaultIfEmpty(0).Max());
                label = $"Continue {number} · p. {page + 1}";
                shortLabel = "Continue";
            }
            else if (groups.All(g => g.Read))
            {
                label = "Read again · " + number;
                shortLabel = "Read again";
                glyph = "\uE72C";
            }
            else if (anyRead)
            {
                label = "Continue " + number;
                shortLabel = "Continue";
            }
            else
            {
                label = "Start reading · " + number;
                shortLabel = "Start reading";
            }
        }
        continueText.Text = label;
        ((FontIcon)((StackPanel)continueButton.Content).Children[0]).Glyph = glyph;
        AutomationProperties.SetName(continueButton, label);
        stickyContinueText.Text = shortLabel;
        AutomationProperties.SetName(stickyContinue, label);
    }

    private async Task ContinueAsync(FrameworkElement from)
    {
        if (next is not null)
        {
            await MangaActions.OpenGroupAsync(mangaId, next, from);
        }
    }

    private void ShowChapterMenu(ChapterTableRow row, FrameworkElement anchor)
    {
        if (row.Group is not { } group)
        {
            return;
        }
        var menu = new MenuFlyout();
        var primary = group.Primary;
        var resume = !primary.Read && primary.LastPageRead > 0;
        var open = new MenuFlyoutItem { Text = resume ? "Continue reading from here" : "Read", Icon = DetailsTheme.Icon("\uE768") };
        open.IsEnabled = primary.Readable && primary.ExternalUrl is null;
        open.Click += (_, _) => _ = AppNav.OpenReaderAsync(mangaId, primary.ChapterId, primary.Read ? 0 : primary.LastPageRead, row);
        menu.Items.Add(open);
        menu.Items.Add(new MenuFlyoutSeparator());
        var toggle = new MenuFlyoutItem { Text = group.Read ? "Mark as unread" : "Mark as read", Icon = DetailsTheme.Icon(group.Read ? "\uE7BA" : "\uE8FB") };
        toggle.Click += (_, _) => _ = Log.Guard("next-details-mark", () => MarkAsync([group], !group.Read));
        menu.Items.Add(toggle);
        var before = PreviousUnread(group).Count;
        var previous = new MenuFlyoutItem { Text = before > 0 ? $"Mark previous {before} as read" : "Mark previous as read", Icon = DetailsTheme.Icon("\uE762"), IsEnabled = before > 0 };
        previous.Click += (_, _) => _ = Log.Guard("next-details-mark-previous", () => MarkPreviousAsync(group));
        menu.Items.Add(previous);
        var select = new MenuFlyoutItem { Text = selectedKeys.Contains(GroupKey(group)) ? "Deselect" : "Select", Icon = DetailsTheme.Icon("\uE762") };
        select.Click += (_, _) => Select(new ChapterRowClick(row, Toggle: true, Range: false));
        menu.Items.Add(select);
        menu.Items.Add(DownloadActions.ChapterItem(group));
        if (group.Alternates.Count > 0)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
            foreach (var copy in new[] { group.Primary }.Concat(group.Alternates))
            {
                var item = new MenuFlyoutItem { Text = "Read " + (copy.Scanlator ?? "unknown group") + (copy.Readable ? string.Empty : " (phone)") };
                item.IsEnabled = copy.Readable && copy.ExternalUrl is null;
                item.Click += (_, _) => _ = AppNav.OpenReaderAsync(mangaId, copy.ChapterId, copy.Read ? 0 : copy.LastPageRead, row);
                menu.Items.Add(item);
            }
        }
        if (group.Primary.Url.StartsWith("/chapter/", StringComparison.Ordinal))
        {
            menu.Items.Add(new MenuFlyoutSeparator());
            var web = new MenuFlyoutItem { Text = "Open chapter on MangaDex", Icon = DetailsTheme.Icon("\uE8A7") };
            web.Click += (_, _) => _ = Log.Guard("next-details-web", async () => await Launcher.LaunchUriAsync(new Uri("https://mangadex.org" + group.Primary.Url)));
            menu.Items.Add(web);
        }
        menu.ShowAt(anchor);
    }

    private List<ChapterGroup> PreviousUnread(ChapterGroup group) =>
        group.Number < 0 ? [] : groups.Where(g => g.Number >= 0 && g.Number < group.Number && !g.Read).ToList();

    private async Task MarkPreviousAsync(ChapterGroup group)
    {
        var targets = PreviousUnread(group);
        if (targets.Count == 0)
        {
            Toasts.Show("Nothing to mark", "Every earlier chapter is already read.", Icons.CheckCheck, seconds: 3);
            return;
        }
        var title = targets.Count == 1 ? "Mark previous 1 chapter as read?" : $"Mark previous {targets.Count} chapters as read?";
        if (!await Dialogs.ConfirmAsync(title, "Everything before this chapter is marked read here and on your phone at the next sync.", "Mark as read"))
        {
            return;
        }
        await MarkAsync(targets, true);
    }

    private async Task MarkAsync(IReadOnlyList<ChapterGroup> targets, bool read)
    {
        if (Services.Engine is not { } engine || targets.Count == 0)
        {
            return;
        }
        var ids = targets.SelectMany(g => new[] { g.Primary }.Concat(g.Alternates)).Select(c => c.ChapterId).ToList();
        await engine.Library.SetChaptersReadAsync(ids, read);
    }

    private List<ChapterGroup> SelectedGroups() => groups.Where(g => selectedKeys.Contains(GroupKey(g))).ToList();

    private async Task MarkSelectedAsync(bool read)
    {
        var targets = SelectedGroups();
        ClearSelection();
        await MarkAsync(targets, read);
    }

    private async Task MarkPreviousOfSelectionAsync()
    {
        var latest = SelectedGroups().Where(g => g.Number >= 0).MaxBy(g => g.Number);
        if (latest is null)
        {
            return;
        }
        ClearSelection();
        await MarkPreviousAsync(latest);
    }

    private void Select(ChapterRowClick click)
    {
        if (click.Row.Group is not { } group)
        {
            return;
        }
        var key = GroupKey(group);
        if (click.Range && anchorKey is not null)
        {
            var keys = rows.Select(GroupKey).ToList();
            var from = keys.IndexOf(anchorKey);
            var to = keys.IndexOf(key);
            if (from >= 0 && to >= 0)
            {
                for (var i = Math.Min(from, to); i <= Math.Max(from, to); i++)
                {
                    selectedKeys.Add(keys[i]);
                }
            }
            else
            {
                selectedKeys.Add(key);
                anchorKey = key;
            }
        }
        else
        {
            if (!selectedKeys.Remove(key))
            {
                selectedKeys.Add(key);
            }
            anchorKey = key;
        }
        RefreshSelection();
    }

    private void SelectAll()
    {
        foreach (var group in rows)
        {
            selectedKeys.Add(GroupKey(group));
        }
        RefreshSelection();
    }

    private void ClearSelection()
    {
        if (selectedKeys.Count == 0)
        {
            return;
        }
        selectedKeys.Clear();
        anchorKey = null;
        RefreshSelection();
    }

    private void RefreshSelection()
    {
        var mode = selectedKeys.Count > 0;
        for (var i = 0; i < rows.Count; i++)
        {
            if (chapterList.TryGetElement(i) is ChapterTableRow row && row.Group is { } group)
            {
                row.SetSelection(selectedKeys.Contains(GroupKey(group)), mode);
            }
        }
        UpdateSelectionBar();
    }

    private void UpdateSelectionBar()
    {
        var count = selectedKeys.Count;
        var show = count > 0;
        selectionCount.Text = count == 1 ? "1 selected" : $"{count} selected";
        selectionPrevious.IsEnabled = SelectedGroups().Any(g => g.Number >= 0 && PreviousUnread(g).Count > 0);
        if (show == (selectionHost.Visibility == Visibility.Visible))
        {
            return;
        }
        selectionHost.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
        if (show)
        {
            _ = Motion.FadeIn(selectionHost, Motion.Short4, Motion.Reduced ? 0 : 12);
        }
    }

    private void OnRootKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Escape && selectedKeys.Count > 0)
        {
            e.Handled = true;
            ClearSelection();
        }
        else if (e.Key == VirtualKey.A && chaptersPanel.Visibility == Visibility.Visible
                 && Microsoft.UI.Input.InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down)
                 && FocusManager.GetFocusedElement(XamlRoot) is ChapterTableRow)
        {
            e.Handled = true;
            SelectAll();
        }
    }

    private void JumpTo(double number)
    {
        if (groups.Count == 0)
        {
            return;
        }
        var target = groups.Where(g => g.Number >= 0).MinBy(g => Math.Abs(g.Number - number));
        if (target is null)
        {
            Toasts.Show("No numbered chapters", null, Icons.Search, seconds: 3);
            return;
        }
        var key = GroupKey(target);
        if (!rows.Any(g => GroupKey(g) == key))
        {
            unreadOnly.IsChecked = false;
            A11y.NameToggle(unreadOnly, "Unread only");
            groupFilterValue = null;
            groupFilter.SelectedIndex = 0;
            BindChapters(animate: false);
        }
        var index = rows.Select(GroupKey).ToList().IndexOf(key);
        if (index < 0)
        {
            return;
        }
        if (chapterList.GetOrCreateElement(index) is ChapterTableRow row)
        {
            row.UpdateLayout();
            row.StartBringIntoView(new BringIntoViewOptions { VerticalAlignmentRatio = 0.35, AnimationDesired = !Motion.Reduced });
            row.Focus(FocusState.Keyboard);
        }
    }

    private async Task ToggleLibraryAsync()
    {
        if (Services.Engine is not { } engine || manga is null)
        {
            SetLibraryState(manga?.Favorite ?? false);
            return;
        }
        if (manga.Favorite)
        {
            await MangaActions.RemoveFromLibraryAsync(mangaId, manga.Title);
        }
        else
        {
            await engine.Library.SetFavoriteAsync(mangaId, true);
            Toasts.Show("Added to your library", "It will sync to your phone.", Icons.Library);
        }
        manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
        BindHeader();
    }

    private void SetPreferredGroup(string? name)
    {
        if (name is null)
        {
            Services.Settings.PreferredGroups.Remove(mangaId);
        }
        else
        {
            Services.Settings.PreferredGroups[mangaId] = name;
        }
        Services.Settings.Save();
        BindChapters(animate: false);
    }

    private void ShowOverflow(FrameworkElement anchor)
    {
        var menu = new MenuFlyout { Placement = FlyoutPlacementMode.BottomEdgeAlignedLeft };
        var allRead = new MenuFlyoutItem { Text = "Mark all as read", Icon = DetailsTheme.Icon("\uE8FB"), IsEnabled = groups.Any(g => !g.Read) };
        allRead.Click += (_, _) => _ = Log.Guard("next-details-mark", () => MangaActions.MarkAllReadAsync(mangaId));
        menu.Items.Add(allRead);
        var allUnread = new MenuFlyoutItem { Text = "Mark all as unread", Icon = DetailsTheme.Icon("\uE7BA"), IsEnabled = groups.Any(g => g.Read || g.InProgress) };
        allUnread.Click += (_, _) => _ = Log.Guard("next-details-mark", () => MarkAsync(groups, false));
        menu.Items.Add(allUnread);
        menu.Items.Add(new MenuFlyoutSeparator());
        var refresh = new MenuFlyoutItem { Text = "Refresh from source", Icon = DetailsTheme.Icon("\uE72C"), IsEnabled = !model.Refreshing };
        refresh.Click += (_, _) => _ = Log.Guard("next-details-refresh", () => RefreshAsync(includeDetails: true));
        menu.Items.Add(refresh);
        menu.Items.Add(DownloadActions.SeriesMenu(groups));
        if (groups.Any(g => g.Alternates.Count > 0) && scanlators.Count > 1)
        {
            Services.Settings.PreferredGroups.TryGetValue(mangaId, out var current);
            var preferred = new MenuFlyoutSubItem { Text = "Preferred group", Icon = DetailsTheme.Icon("\uE716") };
            var automatic = new RadioMenuFlyoutItem { Text = "Automatic", GroupName = "preferred-group", IsChecked = current is null };
            automatic.Click += (_, _) => SetPreferredGroup(null);
            preferred.Items.Add(automatic);
            preferred.Items.Add(new MenuFlyoutSeparator());
            foreach (var name in scanlators)
            {
                var count = chapters.Count(c => string.Equals(c.Scanlator?.Trim(), name, StringComparison.Ordinal));
                var item = new RadioMenuFlyoutItem
                {
                    Text = $"{name} · {count} {(count == 1 ? "chapter" : "chapters")}",
                    GroupName = "preferred-group",
                    IsChecked = string.Equals(current, name, StringComparison.OrdinalIgnoreCase),
                };
                item.Click += (_, _) => SetPreferredGroup(name);
                preferred.Items.Add(item);
            }
            menu.Items.Add(preferred);
        }
        var tracking = new MenuFlyoutItem { Text = "Tracking…", Icon = new FontIcon { Glyph = "\uE9D2" } };
        tracking.Click += (_, _) => _ = Log.Guard("next-details-tracking", () => TrackingDialog.ShowAsync(XamlRoot, mangaId, manga?.Title ?? string.Empty,
            groups.Where(g => g.Read && g.Number > 0).Select(g => g.Number).DefaultIfEmpty(0).Max()));
        menu.Items.Add(tracking);
        var merged = manga is { Source: LibraryRefresher.MergedSourceId, MergedRefs.Count: > 0 };
        var merge = new MenuFlyoutItem { Text = merged ? "Merged sources…" : "Merge with another title…", Icon = new FontIcon { Glyph = Icons.Merge, FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"] } };
        merge.Click += (_, _) => _ = Log.Guard("next-details-merge", () => MergeSheet.ShowAsync(mangaId, coverFrame));
        menu.Items.Add(merge);
        if (WebTarget() is { } web)
        {
            var open = new MenuFlyoutItem { Text = "Open on " + web.Name, Icon = DetailsTheme.Icon("\uE8A7") };
            open.Click += (_, _) => _ = Log.Guard("next-details-web", async () => await Launcher.LaunchUriAsync(web.Url));
            menu.Items.Add(open);
        }
        var copy = new MenuFlyoutItem { Text = "Copy title", Icon = DetailsTheme.Icon("\uE8C8") };
        copy.Click += (_, _) =>
        {
            var package = new DataPackage();
            package.SetText(manga?.Title ?? titleText.Text);
            Clipboard.SetContent(package);
            Toasts.Show("Copied", "Title", Icons.Copy, seconds: 2);
        };
        menu.Items.Add(copy);
        if (manga?.Favorite == true)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
            var remove = new MenuFlyoutItem { Text = "Remove from library", Icon = DetailsTheme.Icon("\uE74D"), Foreground = DetailsTheme.Brush("SystemFillColorCriticalBrush") };
            remove.Click += (_, _) => _ = Log.Guard("next-details-remove", async () =>
            {
                if (Services.Engine is not { } engine || manga is null)
                {
                    return;
                }
                await MangaActions.RemoveFromLibraryAsync(mangaId, manga.Title);
                manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
                BindHeader();
            });
            menu.Items.Add(remove);
        }
        menu.ShowAt(anchor);
    }

    private (Uri Url, string Name)? WebTarget()
    {
        if (manga is null || Services.Engine is not { } engine)
        {
            return null;
        }
        var candidates = new[] { (manga.Source, manga.Url) }.Concat(manga.MergedRefs.Select(r => (r.Source, r.Url)));
        foreach (var (sourceId, url) in candidates)
        {
            if (engine.Sources.Get(sourceId) is not { } source)
            {
                continue;
            }
            try
            {
                return (source.WebUrl(url), source.Name);
            }
            catch (ArgumentException)
            {
            }
        }
        return null;
    }

    private void UpdateSticky()
    {
        if (header.ActualHeight <= 0)
        {
            return;
        }
        var offset = scroller.VerticalOffset;
        SetSticky(offset >= body.Padding.Top + header.ActualHeight);
        var columnsTop = chaptersPanel.Visibility == Visibility.Visible && columnHeader.Visibility == Visibility.Visible
            ? columnHeader.TransformToVisual(body).TransformPoint(new Point(0, 0)).Y
            : double.MaxValue;
        var columnsShown = offset + stickyBar.Height > columnsTop;
        stickyColumns.Visibility = columnsShown ? Visibility.Visible : Visibility.Collapsed;
    }

    private void SetSticky(bool show)
    {
        if (show == stickyShown)
        {
            return;
        }
        stickyShown = show;
        stickyBar.Visibility = Visibility.Visible;
        stickyBar.IsHitTestVisible = show;
        stickyBar.Opacity = 1;
        var v = Motion.VisualOf(stickyBar);
        var c = v.Compositor;
        v.StartAnimation("Opacity", Motion.Scalar(c, show ? 0 : 1, show ? 1 : 0, Motion.Short4, show ? Curve.StandardDecelerate : Curve.StandardAccelerate));
        if (!Motion.Reduced)
        {
            v.StartAnimation("Translation", Motion.Vector3(c, new(0, show ? -8 : 0, 0), new(0, show ? 0 : -8, 0), Motion.Short4, Curve.StandardDecelerate));
        }
        AutomationProperties.SetAccessibilityView(stickyBar, show ? Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Content : Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        stickyContinue.IsTabStop = show;
    }

    private void OnLoadedWatch(object sender, RoutedEventArgs e)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        model.Activate();
        engine.Downloads.Changed -= OnDownloadsChanged;
        engine.Downloads.Changed += OnDownloadsChanged;
        Ui.ReaderClosed -= OnReaderClosed;
        Ui.ReaderClosed += OnReaderClosed;
    }

    private void OnUnloadedWatch(object sender, RoutedEventArgs e)
    {
        if (Services.Engine is { } engine)
        {
            engine.Downloads.Changed -= OnDownloadsChanged;
        }
        model.Deactivate();
        Ui.ReaderClosed -= OnReaderClosed;
    }

    private bool downloadsDirty;

    private void OnDownloadsChanged(object? sender, EventArgs e)
    {
        if (downloadsDirty)
        {
            return;
        }
        downloadsDirty = true;
        Ui.Enqueue(DispatcherQueue, Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
        {
            downloadsDirty = false;
            for (var i = 0; i < rows.Count; i++)
            {
                if (chapterList.TryGetElement(i) is ChapterTableRow row)
                {
                    row.BindDownload();
                }
            }
        }, "next-details-downloads");
    }

    private void OnReaderClosed(object? sender, EventArgs e) => model.ReaderClosed();

    private HeroParts Parts(bool landsOnCover) => landsOnCover
        ? new([info], [tabs, chaptersPanel, aboutPanel, ambient, stickyHost, selectionHost])
        : new([], [header, tabs, chaptersPanel, aboutPanel, ambient, stickyHost, selectionHost]);

    private void ApplyCoverShape()
    {
        var page = ActualWidth > 1 ? ActualWidth : App.MainWindow.Nav.Host.ActualWidth;
        var (width, height) = DetailsHeaderLayout.CoverSize(headerStyle, page - 2 * PadX, coverRatio);
        coverFrame.Width = width;
        coverFrame.Height = height;
    }

    private Rect LandingCover() =>
        ContainerTransform.ToRect(DetailsHeaderLayout.Cover(headerStyle, ActualWidth, PadX, PadX, BodyTop, coverRatio));

    public async Task EnterAsync(FrameworkElement? below)
    {
        DevHarness.Mark("next details enter");
        await transition.EnterAsync(below, snapshot, BindSnapshotHeader, LandingCover, Parts, flight => _ = Log.Guard("next-details", () => LoadAsync(flight)));
        headerCover.RefreshQuietly();
    }

    public Task ExitAsync(FrameworkElement? below)
    {
        DevHarness.Mark("next details exit");
        return transition.ExitAsync(below, ContainerTransform.BoundsIn(coverFrame, this), Parts);
    }

    public void SkipTransition() => transition.Skip();

    private sealed partial class GroupCollection : ObservableCollection<ChapterGroup>
    {
        public void ReplaceAll(IEnumerable<ChapterGroup> items)
        {
            Items.Clear();
            foreach (var item in items)
            {
                Items.Add(item);
            }
            OnPropertyChanged(new PropertyChangedEventArgs(nameof(Count)));
            OnPropertyChanged(new PropertyChangedEventArgs("Item[]"));
            OnCollectionChanged(new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
        }
    }
}
