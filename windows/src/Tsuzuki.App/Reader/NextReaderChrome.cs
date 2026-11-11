using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Reader;
using DispatcherQueuePriority = Microsoft.UI.Dispatching.DispatcherQueuePriority;

namespace Tsuzuki.App.Reader;

public sealed partial class ReaderPage
{
    private static readonly Windows.UI.Color NextBarColor = Windows.UI.Color.FromArgb(235, 0x14, 0x15, 0x19);
    private static readonly Windows.UI.Color NextStrokeColor = Windows.UI.Color.FromArgb(28, 255, 255, 255);
    private static readonly Windows.UI.Color NextSecondaryColor = Windows.UI.Color.FromArgb(200, 255, 255, 255);
    private static readonly int[] AutoHideChoices = [2000, 4000, 8000, 0];
    private static readonly string[] BackgroundChoices = ["black", "gray", "white"];

    private readonly NextThumbnails thumbnails = new();
    private Grid? nextTop;
    private Grid? nextBottom;
    private Grid? nextDrag;
    private ColumnDefinition? nextCaption;
    private TextBlock? nextTitle;
    private TextBlock? nextScanlator;
    private DropDownButton? nextCopies;
    private FontIcon? nextFullIcon;
    private Button? nextPrev;
    private Button? nextNext;
    private Grid? navCluster;
    private ScrollView? filmScroll;
    private ItemsRepeater? filmRepeater;
    private ThumbFactory? filmFactory;
    private ToggleButton? filmToggle;
    private ToggleButton? spreadsPill;
    private TextBlock? spreadsText;
    private ToggleButton? offsetPill;
    private Slider? nextSlider;
    private TextBlock? nextLabel;
    private Grid? explorer;
    private ItemsRepeater? explorerRepeater;
    private ThumbFactory? explorerFactory;
    private TextBlock? explorerTitle;
    private bool syncingSettings;

    private FrameworkElement ChromeTop => nextTop!;
    private FrameworkElement ChromeBottom => nextBottom!;
    private UIElement ActiveDragArea => nextDrag!;
    private Slider ActiveSlider => nextSlider!;
    private float TopShift => 48;
    private float BottomShift => (float)Math.Max(64, nextBottom.ActualHeight);
    private double BottomRevealZone => Math.Max(110, nextBottom.ActualHeight + 24);
    private bool ExplorerOpen => explorer?.Visibility == Visibility.Visible;

    private static FontFamily SymbolFont => (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    private static Style TextStyle(string key) => (Style)Application.Current.Resources[key];

    private static FontIcon Icon(string glyph, double size = 16) => new() { Glyph = glyph, FontFamily = SymbolFont, FontSize = size };

    private static Button IconButton(string glyph, string name)
    {
        var button = new Button
        {
            Content = Icon(glyph),
            Width = 40,
            Height = 36,
            Padding = new Thickness(0),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            BorderThickness = new Thickness(0),
            CornerRadius = new CornerRadius(4),
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(button, name);
        AutomationProperties.SetName(button, name);
        return button;
    }

    private static ToggleButton ModePill(string glyph, TextBlock label, string name)
    {
        var pill = new ToggleButton
        {
            Content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, Children = { Icon(glyph, 12), label } },
            Height = 28,
            Padding = new Thickness(12, 0, 12, 0),
            CornerRadius = new CornerRadius(14),
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(pill, name);
        AutomationProperties.SetName(pill, name);
        return pill;
    }

    private void BuildNextChrome()
    {
        HintBar.Margin = new Thickness(0, 0, 0, 200);
        var bar = new SolidColorBrush(NextBarColor);
        var stroke = new SolidColorBrush(NextStrokeColor);
        var secondary = new SolidColorBrush(NextSecondaryColor);

        var back = IconButton("\uE72B", "Close reader (Esc)");
        back.Click += (_, _) => _ = CloseAsync();
        nextTitle = new TextBlock { Style = TextStyle("BodyStrongTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.NoWrap, TextTrimming = TextTrimming.CharacterEllipsis, MaxWidth = 520 };
        nextScanlator = new TextBlock { Style = TextStyle("BodyTextBlockStyle"), Foreground = secondary, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0, 0, 0), Visibility = Visibility.Collapsed };
        nextCopies = new DropDownButton
        {
            Foreground = secondary,
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(8, 4, 8, 4),
            VerticalAlignment = VerticalAlignment.Center,
            Visibility = Visibility.Collapsed,
            Flyout = new MenuFlyout { Placement = FlyoutPlacementMode.BottomEdgeAlignedLeft },
        };
        ToolTipService.SetToolTip(nextCopies, "Switch scanlation group");
        AutomationProperties.SetName(nextCopies, "Scanlation group");
        var heading = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6, 0, 0, 0), Children = { nextTitle, nextScanlator, nextCopies } };
        nextDrag = new Grid { Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent) };
        var thumbs = IconButton("\uF0E2", "Thumbnails (T)");
        thumbs.Click += (_, _) => ToggleExplorer();
        var gear = IconButton("\uE713", "Reader settings");
        gear.Flyout = BuildSettingsFlyout();
        var full = IconButton("\uE740", "Full screen (F11)");
        nextFullIcon = (FontIcon)full.Content;
        full.Click += (_, _) => ToggleFullscreen();
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, VerticalAlignment = VerticalAlignment.Center, Children = { thumbs, gear, full } };
        nextCaption = new ColumnDefinition { Width = new GridLength(140) };
        nextTop = new Grid
        {
            Height = 48,
            VerticalAlignment = VerticalAlignment.Top,
            Padding = new Thickness(8, 0, 0, 0),
            ColumnSpacing = 4,
            Background = bar,
            BorderBrush = stroke,
            BorderThickness = new Thickness(0, 0, 0, 1),
            RequestedTheme = ElementTheme.Dark,
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto },
                nextCaption,
            },
            Children = { back, heading, nextDrag, actions },
        };
        Grid.SetColumn(heading, 1);
        Grid.SetColumn(nextDrag, 2);
        Grid.SetColumn(actions, 3);

        filmFactory = new ThumbFactory(thumbnails, 60, 88, JumpToPage);
        filmRepeater = new ItemsRepeater
        {
            ItemTemplate = filmFactory,
            Layout = new StackLayout { Orientation = Orientation.Horizontal, Spacing = 6 },
            XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled,
            TabFocusNavigation = KeyboardNavigationMode.Once,
        };
        AutomationProperties.SetName(filmRepeater, "Filmstrip");
        filmScroll = new ScrollView
        {
            Content = filmRepeater,
            ContentOrientation = ScrollingContentOrientation.Horizontal,
            HorizontalScrollMode = ScrollingScrollMode.Enabled,
            VerticalScrollMode = ScrollingScrollMode.Disabled,
            HorizontalScrollBarVisibility = ScrollingScrollBarVisibility.Auto,
            VerticalScrollBarVisibility = ScrollingScrollBarVisibility.Hidden,
            Height = 100,
            Visibility = Services.Settings.ReaderFilmstrip ? Visibility.Visible : Visibility.Collapsed,
        };

        filmToggle = new ToggleButton
        {
            Content = Icon("\uE786"),
            Width = 40,
            Height = 32,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(4),
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(filmToggle, "Filmstrip (S)");
        AutomationProperties.SetName(filmToggle, "Filmstrip (S)");
        filmToggle.Click += (_, _) => ToggleFilmstrip();

        spreadsText = new TextBlock { FontSize = 12, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center };
        spreadsPill = ModePill("\uE89A", spreadsText, "Spreads (D)");
        spreadsPill.Click += (_, _) =>
        {
            SetLayout(layout == ReaderLayout.Double ? ReaderLayout.Single : ReaderLayout.Double);
            ApplyNextState();
        };
        offsetPill = ModePill("\uE8AB", new TextBlock { Text = "Offset +1", FontSize = 12, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center }, "Offset pages by one (O)");
        offsetPill.Click += (_, _) =>
        {
            ToggleOffset();
            ApplyNextState();
        };

        nextSlider = new Slider
        {
            Minimum = 1,
            Maximum = 1,
            StepFrequency = 1,
            TickPlacement = TickPlacement.Inline,
            IsThumbToolTipEnabled = true,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(6, 0, 6, 0),
        };
        AutomationProperties.SetName(nextSlider, "Page");
        nextSlider.ValueChanged += (_, e) => OnSliderChanged(e.NewValue, e.OldValue);

        nextLabel = new TextBlock { Style = TextStyle("CaptionTextBlockStyle"), Foreground = secondary, VerticalAlignment = VerticalAlignment.Center, MinWidth = 96, TextAlignment = TextAlignment.Right };
        var goTo = new Button
        {
            Content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 8,
                Children =
                {
                    new Border
                    {
                        BorderBrush = stroke,
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 0, 6, 1),
                        Child = new TextBlock { Text = "G", FontSize = 11, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold },
                    },
                    new TextBlock { Text = "Go to", FontSize = 12, VerticalAlignment = VerticalAlignment.Center },
                },
            },
            Height = 28,
            Padding = new Thickness(8, 0, 10, 0),
            CornerRadius = new CornerRadius(14),
            VerticalAlignment = VerticalAlignment.Center,
        };
        ToolTipService.SetToolTip(goTo, "Go to page (G)");
        AutomationProperties.SetName(goTo, "Go to page (G)");
        goTo.Click += (_, _) => ShowGoTo();

        nextPrev = IconButton("\uE892", "Previous chapter ([)");
        nextPrev.Click += (_, _) => _ = GoChapterAsync(-1);
        nextNext = IconButton("\uE893", "Next chapter (])");
        nextNext.Click += (_, _) => _ = NextChapterAsync();
        foreach (var button in new[] { nextPrev, nextNext })
        {
            if (button.Content is FontIcon glyph)
            {
                glyph.MirroredWhenRightToLeft = true;
            }
        }

        navCluster = new Grid
        {
            ColumnSpacing = 6,
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto },
            },
            Children = { nextPrev, nextSlider, nextNext },
        };
        Grid.SetColumn(nextSlider, 1);
        Grid.SetColumn(nextNext, 2);

        var controls = new Grid
        {
            ColumnSpacing = 6,
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
            },
            Children = { filmToggle, spreadsPill, offsetPill, navCluster, nextLabel, goTo },
        };
        UIElement[] order = [filmToggle, spreadsPill, offsetPill, navCluster, nextLabel, goTo];
        for (var i = 0; i < order.Length; i++)
        {
            Grid.SetColumn((FrameworkElement)order[i], i);
        }
        Grid.SetRow(controls, 1);
        nextBottom = new Grid
        {
            VerticalAlignment = VerticalAlignment.Bottom,
            Padding = new Thickness(12, 8, 12, 10),
            RowSpacing = 8,
            Background = bar,
            BorderBrush = stroke,
            BorderThickness = new Thickness(0, 1, 0, 0),
            RequestedTheme = ElementTheme.Dark,
            RowDefinitions = { new RowDefinition { Height = GridLength.Auto }, new RowDefinition { Height = GridLength.Auto } },
            Children = { filmScroll, controls },
        };

        Root.Children.Insert(Root.Children.IndexOf(HintBar), nextBottom);
        Root.Children.Insert(Root.Children.IndexOf(HintBar), nextTop);
        ApplyBackground();
        ApplyAutoHide();
        ApplyNextState();
    }

    private Flyout BuildSettingsFlyout()
    {
        var layoutChoice = new RadioButtons { Header = "Layout", Items = { "Single page", "Two pages", "Long strip" } };
        var directionChoice = new RadioButtons { Header = "Reading direction", Items = { "Right to left", "Left to right" } };
        var fitChoice = new RadioButtons { Header = "Fit", Items = { "Fit to screen", "Fit width", "Original size" } };
        var offsetSwitch = new ToggleSwitch { Header = "Offset page pairs by one" };
        var backgroundChoice = new RadioButtons { Header = "Background", Items = { "Black", "Dark gray", "White" } };
        var hideChoice = new ComboBox { Header = "Hide controls after", Items = { "2 seconds", "4 seconds", "8 seconds", "Never" }, MinWidth = 200 };
        var wheelChoice = new ComboBox { Header = "Wheel scroll (long strip)", Items = { "System", "Quarter page", "Half page", "Full page" }, MinWidth = 200 };
        AutomationProperties.SetName(layoutChoice, "Layout");
        AutomationProperties.SetName(directionChoice, "Reading direction");
        AutomationProperties.SetName(fitChoice, "Fit");
        AutomationProperties.SetName(offsetSwitch, "Offset page pairs by one");
        AutomationProperties.SetName(backgroundChoice, "Background");
        AutomationProperties.SetName(hideChoice, "Hide controls after");

        void Sync()
        {
            syncingSettings = true;
            layoutChoice.SelectedIndex = layout switch { ReaderLayout.Single => 0, ReaderLayout.Double => 1, _ => 2 };
            directionChoice.SelectedIndex = rightToLeft ? 0 : 1;
            fitChoice.SelectedIndex = fit switch { PageFit.Screen => 0, PageFit.Width => 1, _ => 2 };
            offsetSwitch.IsOn = spreadOffset;
            backgroundChoice.SelectedIndex = Math.Max(0, Array.IndexOf(BackgroundChoices, Services.Settings.ReaderBackground));
            hideChoice.SelectedIndex = Math.Max(0, Array.IndexOf(AutoHideChoices, Services.Settings.ReaderAutoHideMs));
            wheelChoice.SelectedIndex = Math.Min(3, Math.Max(0, Services.Settings.ReaderWheelScroll));
            var paged = layout != ReaderLayout.Strip;
            directionChoice.IsEnabled = paged;
            fitChoice.IsEnabled = paged;
            offsetSwitch.IsEnabled = layout == ReaderLayout.Double;
            syncingSettings = false;
        }

        layoutChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && layoutChoice.SelectedIndex >= 0)
            {
                SetLayout(layoutChoice.SelectedIndex switch { 0 => ReaderLayout.Single, 1 => ReaderLayout.Double, _ => ReaderLayout.Strip });
                Sync();
            }
        };
        directionChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && directionChoice.SelectedIndex >= 0 && (directionChoice.SelectedIndex == 0) != rightToLeft)
            {
                ToggleDirection();
                Sync();
            }
        };
        fitChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && fitChoice.SelectedIndex >= 0)
            {
                SetFit(fitChoice.SelectedIndex switch { 0 => PageFit.Screen, 1 => PageFit.Width, _ => PageFit.Original });
                Sync();
            }
        };
        offsetSwitch.Toggled += (_, _) =>
        {
            if (!syncingSettings && offsetSwitch.IsOn != spreadOffset)
            {
                ToggleOffset();
                Sync();
            }
        };
        backgroundChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && backgroundChoice.SelectedIndex >= 0)
            {
                Services.Settings.ReaderBackground = BackgroundChoices[backgroundChoice.SelectedIndex];
                Services.Settings.Save();
                ApplyBackground();
            }
        };
        hideChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && hideChoice.SelectedIndex >= 0)
            {
                Services.Settings.ReaderAutoHideMs = AutoHideChoices[hideChoice.SelectedIndex];
                Services.Settings.Save();
                ApplyAutoHide();
            }
        };
        wheelChoice.SelectionChanged += (_, _) =>
        {
            if (!syncingSettings && wheelChoice.SelectedIndex >= 0)
            {
                Services.Settings.ReaderWheelScroll = wheelChoice.SelectedIndex;
                Services.Settings.Save();
                UpdateStripWheelOwnership();
            }
        };

        var flyout = new Flyout
        {
            Placement = FlyoutPlacementMode.BottomEdgeAlignedRight,
            Content = new StackPanel
            {
                Spacing = 16,
                Width = 260,
                Children = { layoutChoice, directionChoice, fitChoice, offsetSwitch, backgroundChoice, hideChoice, wheelChoice },
            },
        };
        flyout.Opening += (_, _) => Sync();
        flyout.Closed += (_, _) => Focus(FocusState.Programmatic);
        return flyout;
    }

    private void ApplyBackground()
    {
        var choice = Services.Settings.ReaderBackground;
        var light = choice == "white";
        Root.Background = new SolidColorBrush(choice switch
        {
            "gray" => Windows.UI.Color.FromArgb(255, 0x14, 0x15, 0x19),
            "white" => Microsoft.UI.Colors.White,
            _ => Microsoft.UI.Colors.Black,
        });
        var ink = new SolidColorBrush(light ? Windows.UI.Color.FromArgb(255, 0x1A, 0x1A, 0x1A) : Microsoft.UI.Colors.White);
        MessageTitle.Foreground = ink;
        LoadingRing.Foreground = ink;
        MessageBody.Foreground = new SolidColorBrush(light ? Windows.UI.Color.FromArgb(178, 0, 0, 0) : Windows.UI.Color.FromArgb(176, 255, 255, 255));
    }

    private void ApplyAutoHide()
    {
        var ms = Services.Settings.ReaderAutoHideMs;
        chromeTimer.Interval = TimeSpan.FromMilliseconds(ms > 0 ? ms : 2400);
        if (ms > 0 && chromeVisible)
        {
            chromeTimer.Stop();
            chromeTimer.Start();
        }
    }

    private bool AutoHideEnabled => Services.Settings.ReaderAutoHideMs > 0;

    private void ApplyNextState()
    {
        if (spreadsPill is null || offsetPill is null || spreadsText is null || filmToggle is null)
        {
            return;
        }
        var spreads = layout == ReaderLayout.Double;
        spreadsPill.IsChecked = spreads;
        spreadsText.Text = spreads ? "Spreads on" : "Spreads off";
        offsetPill.IsChecked = spreadOffset;
        offsetPill.Visibility = spreads ? Visibility.Visible : Visibility.Collapsed;
        filmToggle.IsChecked = Services.Settings.ReaderFilmstrip;
        var flow = rightToLeft && layout != ReaderLayout.Strip ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        if (navCluster is not null)
        {
            navCluster.FlowDirection = flow;
        }
        if (filmScroll is not null)
        {
            filmScroll.FlowDirection = flow;
        }
        if (explorerRepeater is not null)
        {
            explorerRepeater.FlowDirection = flow;
        }
        if (pages.Count > 0)
        {
            var (first, second) = layout == ReaderLayout.Strip ? (index, null) : SpreadFor(index);
            UpdateThumbPosition(first, second);
        }
    }

    private void UpdateNextTitle()
    {
        if (nextTitle is null || nextScanlator is null || nextCopies is null || chapter is not { } current)
        {
            return;
        }
        var number = current.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(current.ChapterNumber) : current.Name;
        nextTitle.Text = manga?.Title is { Length: > 0 } title ? $"{number} · {title}" : number;
        var copies = CurrentCopies();
        if (copies.Count > 1)
        {
            var menu = (MenuFlyout)nextCopies.Flyout;
            menu.Items.Clear();
            foreach (var copy in copies)
            {
                var item = new RadioMenuFlyoutItem
                {
                    Text = copy.Scanlator ?? "Unknown group",
                    GroupName = "copies",
                    IsChecked = copy.ChapterId == current.ChapterId,
                };
                item.Click += (_, _) => _ = Log.Guard("reader switch copy", () => SwitchCopyAsync(copy));
                menu.Items.Add(item);
            }
            nextCopies.Content = current.Scanlator ?? "Unknown group";
            nextCopies.Visibility = Visibility.Visible;
            nextScanlator.Visibility = Visibility.Collapsed;
        }
        else
        {
            nextScanlator.Text = current.Scanlator ?? string.Empty;
            nextScanlator.Visibility = current.Scanlator is null ? Visibility.Collapsed : Visibility.Visible;
            nextCopies.Visibility = Visibility.Collapsed;
        }
    }

    private List<ChapterRow> CurrentCopies()
    {
        var position = CurrentGroupIndex();
        if (position < 0)
        {
            return [];
        }
        var group = session.Ascending[position];
        return new[] { group.Primary }.Concat(group.Alternates).Where(c => c.Readable && c.ExternalUrl is null).ToList();
    }

    private async Task SwitchCopyAsync(ChapterRow copy)
    {
        if (loadingChapter || chapter?.ChapterId == copy.ChapterId)
        {
            return;
        }
        Flash(copy.Scanlator ?? "Unknown group");
        await LoadChapterAsync(copy.ChapterId, index);
    }

    private void ToggleFilmstrip()
    {
        if (filmScroll is null)
        {
            return;
        }
        var show = !Services.Settings.ReaderFilmstrip;
        Services.Settings.ReaderFilmstrip = show;
        Services.Settings.Save();
        filmScroll.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
        Flash(show ? "Filmstrip on" : "Filmstrip off");
        SetChrome(true);
        ApplyNextState();
    }

    private void ResetThumbnails()
    {
        if (filmRepeater is not null)
        {
            filmRepeater.ItemsSource = null;
        }
        if (explorerRepeater is not null)
        {
            explorerRepeater.ItemsSource = null;
        }
        filmFactory?.Reset();
        explorerFactory?.Reset();
        if (pages.Count == 0 || chapter is null)
        {
            thumbnails.Clear();
            if (nextLabel is not null)
            {
                nextLabel.Text = string.Empty;
            }
            return;
        }
        thumbnails.Load(pages, chapter.Source);
        if (filmRepeater is not null)
        {
            filmRepeater.ItemsSource = Enumerable.Range(0, pages.Count).ToList();
        }
        if (explorerRepeater is not null)
        {
            explorerRepeater.ItemsSource = Enumerable.Range(0, pages.Count).ToList();
        }
        if (nextSlider is not null)
        {
            nextSlider.TickFrequency = Math.Max(1, pages.Count - 1);
        }
    }

    private void UpdateThumbPosition(int first, int? second)
    {
        var last = second ?? first;
        filmFactory?.SetCurrent(first, last);
        explorerFactory?.SetCurrent(first, last);
        if (nextLabel is not null)
        {
            nextLabel.Text = pages.Count == 0 ? string.Empty : PositionLabel(first, second);
        }
        if (filmScroll?.Visibility == Visibility.Visible && filmRepeater?.ItemsSource is not null && first < pages.Count)
        {
            var element = filmRepeater.GetOrCreateElement(first);
            element.StartBringIntoView(new BringIntoViewOptions { HorizontalAlignmentRatio = 0.5, AnimationDesired = !Motion.Reduced });
        }
    }

    private string PositionLabel(int first, int? second)
    {
        if (layout != ReaderLayout.Double)
        {
            return $"Page {first + 1} / {pages.Count}";
        }
        var (at, count) = Planner().Position(first);
        return second is { } s ? $"Spread {at} / {count} · pp. {first + 1}–{s + 1}" : $"Spread {at} / {count} · p. {first + 1}";
    }

    private void OnSliderChanged(double value, double previous)
    {
        var target = (int)value - 1;
        if (!suppressSlider && !loadingChapter && target != index && pages.Count > 0 && Math.Abs(value - previous) > 0.5)
        {
            JumpToPage(target);
        }
    }

    private void JumpToPage(int page)
    {
        if (loadingChapter || pages.Count == 0)
        {
            return;
        }
        page = Math.Clamp(page, 0, pages.Count - 1);
        if (layout == ReaderLayout.Strip)
        {
            index = page;
            ScrollStripTo(page);
            UpdatePageLabels(page, null);
            QueueProgress(page);
        }
        else
        {
            _ = GoToPageAsync(page, animate: false);
        }
    }

    private void ToggleExplorer()
    {
        if (ExplorerOpen)
        {
            CloseExplorer();
        }
        else
        {
            OpenExplorer();
        }
    }

    private void OpenExplorer()
    {
        if (pages.Count == 0 || loadingChapter || chapter is null)
        {
            return;
        }
        HideGoTo(refocus: false);
        if (explorer is null)
        {
            BuildExplorer();
        }
        explorerTitle!.Text = $"{ChapterTitle(chapter)} · {pages.Count} pages";
        explorerRepeater!.ItemsSource ??= Enumerable.Range(0, pages.Count).ToList();
        var (first, second) = layout == ReaderLayout.Strip ? (index, null) : SpreadFor(index);
        explorerFactory!.SetCurrent(first, second ?? first);
        explorer!.Visibility = Visibility.Visible;
        if (!Motion.Reduced)
        {
            _ = Motion.FadeIn(explorer, Motion.Short4);
        }
        DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, () =>
        {
            if (!ExplorerOpen || first >= pages.Count)
            {
                return;
            }
            var element = explorerRepeater.GetOrCreateElement(first);
            element.UpdateLayout();
            element.StartBringIntoView(new BringIntoViewOptions { VerticalAlignmentRatio = 0.4, AnimationDesired = false });
            if (element is Control control)
            {
                control.Focus(FocusState.Keyboard);
            }
        });
    }

    private void CloseExplorer()
    {
        if (explorer is null)
        {
            return;
        }
        explorer.Visibility = Visibility.Collapsed;
        Focus(FocusState.Programmatic);
    }

    private void BuildExplorer()
    {
        explorerFactory = new ThumbFactory(thumbnails, 120, 176, page =>
        {
            CloseExplorer();
            JumpToPage(page);
        });
        explorerRepeater = new ItemsRepeater
        {
            ItemTemplate = explorerFactory,
            Layout = new UniformGridLayout
            {
                MinItemWidth = 120,
                MinItemHeight = 176,
                MinRowSpacing = 12,
                MinColumnSpacing = 12,
                ItemsJustification = UniformGridLayoutItemsJustification.Center,
            },
            XYFocusKeyboardNavigation = XYFocusKeyboardNavigationMode.Enabled,
            TabFocusNavigation = KeyboardNavigationMode.Once,
            Margin = new Thickness(24, 4, 24, 24),
        };
        AutomationProperties.SetName(explorerRepeater, "Pages");
        explorerTitle = new TextBlock { Style = TextStyle("SubtitleTextBlockStyle"), TextTrimming = TextTrimming.CharacterEllipsis, VerticalAlignment = VerticalAlignment.Center };
        var close = IconButton("\uE711", "Close thumbnails (Esc)");
        close.Click += (_, _) => CloseExplorer();
        var header = new Grid
        {
            Padding = new Thickness(24, 16, 12, 12),
            ColumnDefinitions = { new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }, new ColumnDefinition { Width = GridLength.Auto } },
            Children = { explorerTitle, close },
        };
        Grid.SetColumn(close, 1);
        var scroll = new ScrollView { Content = explorerRepeater, VerticalScrollBarVisibility = ScrollingScrollBarVisibility.Auto };
        Grid.SetRow(scroll, 1);
        var panel = new Grid
        {
            MaxWidth = 1120,
            Margin = new Thickness(48, 40, 48, 40),
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(250, 0x14, 0x15, 0x19)),
            BorderBrush = new SolidColorBrush(NextStrokeColor),
            BorderThickness = new Thickness(1),
            RowDefinitions = { new RowDefinition { Height = GridLength.Auto }, new RowDefinition { Height = new GridLength(1, GridUnitType.Star) } },
            Children = { header, scroll },
        };
        AutomationProperties.SetName(panel, "Page thumbnails");
        explorer = new Grid
        {
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(153, 0, 0, 0)),
            RequestedTheme = ElementTheme.Dark,
            Visibility = Visibility.Collapsed,
            Children = { panel },
        };
        explorer.Tapped += (_, e) =>
        {
            if (e.OriginalSource == explorer)
            {
                CloseExplorer();
            }
            e.Handled = true;
        };
        explorer.DoubleTapped += (_, e) => e.Handled = true;
        Root.Children.Insert(Root.Children.IndexOf(ShortcutLayer), explorer);
    }
}
