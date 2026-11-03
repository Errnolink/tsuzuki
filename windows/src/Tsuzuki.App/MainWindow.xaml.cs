using System.Runtime.InteropServices;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Windows.Graphics;
using Windows.System;

namespace Tsuzuki.App;

public sealed partial class MainWindow : Window
{
    private readonly Dictionary<string, (Button Button, FontIcon Icon, TextBlock Label)> rail = [];
    private readonly Dictionary<string, Func<FrameworkElement>> rootFactories = [];
    private readonly System.Runtime.CompilerServices.ConditionalWeakTable<FrameworkElement, Design.TopBarState> topBars = new();
    private string? selectedRail;
    private float pillTargetY = float.NaN;

    public NavHost Nav { get; }
    public Design.TopControls TopRow { get; }

    public Grid ReaderLayer => ReaderHost;
    public StackPanel Toasts => ToastHost;
    public UIElement ContentRoot => Root;

    public MainWindow()
    {
        InitializeComponent();
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        AppWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;
        var icon = Path.Combine(AppContext.BaseDirectory, "Assets", "Tsuzuki.ico");
        if (File.Exists(icon))
        {
            AppWindow.SetIcon(icon);
        }
        SizeWindow();

        Nav = new NavHost(PageHost);
        TopRow = new Design.TopControls(TopCapsule, TopCapsuleIcons, TopTitle, SearchBox);
        Nav.Changed += (_, _) =>
        {
            AppTitleBar.IsBackButtonVisible = Nav.CanGoBack;
            TopRow.Present(Nav.Top is { } top && topBars.TryGetValue(top, out var bar) ? bar : Design.TopBarState.Empty);
        };
        Root.KeyboardAccelerators.Add(Accelerator(VirtualKey.Left, VirtualKeyModifiers.Menu, () => _ = GoBackAsync()));
        Root.KeyboardAccelerators.Add(Accelerator(VirtualKey.F, VirtualKeyModifiers.Control, () =>
        {
            if (ReaderLayer.Visibility == Visibility.Visible) return;
            TopRow.RevealSearch();
        }));
        SearchBox.KeyDown += (s, e) =>
        {
            if (e.Key == VirtualKey.Escape)
            {
                SearchBox.Text = string.Empty;
                e.Handled = true;
                PageHost.Focus(FocusState.Programmatic);
            }
        };
        Root.Loaded += (_, _) => MoveRailPill(selectedRail, animate: false);
        RailItemsHost.LayoutUpdated += OnRailLayout;
        Rail.AddHandler(UIElement.PointerMovedEvent, new PointerEventHandler((_, e) => SyncRailHover(e)), true);
        Rail.AddHandler(UIElement.PointerExitedEvent, new PointerEventHandler((_, _) => SyncRailHover(null)), true);
    }


    private static KeyboardAccelerator Accelerator(VirtualKey key, VirtualKeyModifiers modifiers, Action action)
    {
        var a = new KeyboardAccelerator { Key = key, Modifiers = modifiers };
        a.Invoked += (_, e) =>
        {
            e.Handled = true;
            action();
        };
        return a;
    }

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    private void SizeWindow()
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        var scale = GetDpiForWindow(hwnd) / 96.0;
        var area = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        var width = Math.Min((int)((DevHarness.Current?.WindowWidth ?? 1380) * scale), (int)(area.Width * 0.92));
        var height = Math.Min((int)((DevHarness.Current?.WindowHeight ?? 900) * scale), (int)(area.Height * 0.92));
        AppWindow.MoveAndResize(new RectInt32(area.X + (area.Width - width) / 2, area.Y + (area.Height - height) / 2, width, height));
        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.PreferredMinimumWidth = (int)(940 * scale);
            presenter.PreferredMinimumHeight = (int)(600 * scale);
        }
    }

    public void RestoreWindowBounds()
    {
        if (Services.Settings.WindowWidth < 940 || Services.Settings.WindowHeight < 600)
        {
            return;
        }
        var saved = new RectInt32(Services.Settings.WindowX, Services.Settings.WindowY, Services.Settings.WindowWidth, Services.Settings.WindowHeight);
        var area = DisplayArea.GetFromRect(new RectInt32(saved.X, saved.Y, 1, 1), DisplayAreaFallback.Primary).WorkArea;
        if (saved.X < area.X - 16 || saved.Y < area.Y - 16 || saved.X + saved.Width > area.X + area.Width + 16 || saved.Y + saved.Height > area.Y + area.Height + 16)
        {
            return;
        }
        AppWindow.MoveAndResize(saved);
        if (Services.Settings.WindowMaximized && AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.Maximize();
        }
    }

    public void SaveWindowBounds()
    {
        if (AppWindow.Presenter is OverlappedPresenter { State: OverlappedPresenterState.Maximized })
        {
            Services.Settings.WindowMaximized = true;
        }
        else
        {
            var position = AppWindow.Position;
            var size = AppWindow.Size;
            Services.Settings.WindowX = position.X;
            Services.Settings.WindowY = position.Y;
            Services.Settings.WindowWidth = size.Width;
            Services.Settings.WindowHeight = size.Height;
            Services.Settings.WindowMaximized = false;
        }
        Services.Settings.Save();
    }

    public void AddRootPage(string key, Func<FrameworkElement> factory) => rootFactories[key] = factory;

    public void AddRailItem(string key, string glyph, string label, Func<FrameworkElement> factory, bool footer = false)
    {
        rootFactories[key] = factory;
        var icon = new FontIcon { Glyph = glyph, FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"], FontSize = 20 };
        var text = new TextBlock
        {
            Text = label,
            FontSize = 11,
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
            HorizontalAlignment = HorizontalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis,
        };
        var stack = new StackPanel { Spacing = 4, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(icon);
        stack.Children.Add(text);
        var button = new Button
        {
            Style = (Style)Application.Current.Resources["TzRowButton"],
            Width = 60,
            Height = 56,
            HorizontalAlignment = HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(14),
            Content = stack,
        };
        ToolTipService.SetToolTip(button, label);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(button, label);
        button.Click += (_, _) => _ = NavigateRootAsync(key);
        rail[key] = (button, icon, text);
        (footer ? RailFooter : RailItems).Children.Add(button);
        ApplyRailColors();
    }

    public async Task NavigateRootAsync(string key)
    {
        selectedRail = key;
        ApplyRailColors();
        MoveRailPill(key, animate: true);
        await Nav.ShowRootAsync(key, rootFactories[key]);
    }

    private void ApplyRailColors()
    {
        foreach (var (key, item) in rail)
        {
            var selected = key == selectedRail;
            item.Icon.Foreground = Design.Theme.Brush(selected ? "TzAccent" : "TzTextSecondary");
            item.Label.Foreground = Design.Theme.Brush(selected ? "TzAccent" : "TzTextSecondary");
            VisualStateManager.GoToState(item.Button, "Normal", false);
        }
    }

    private void SyncRailHover(PointerRoutedEventArgs? e)
    {
        foreach (var (_, item) in rail)
        {
            var over = false;
            if (e != null)
            {
                var p = e.GetCurrentPoint(item.Button).Position;
                over = p.X >= 0 && p.Y >= 0 && p.X < item.Button.ActualWidth && p.Y < item.Button.ActualHeight;
            }
            VisualStateManager.GoToState(item.Button, over ? "PointerOver" : "Normal", false);
        }
    }

    private void MoveRailPill(string? key, bool animate)
    {
        if (key is null || !rail.TryGetValue(key, out var item) || item.Button.Parent != RailItems)
        {
            RailPill.Opacity = 0;
            return;
        }
        var y = (float)item.Button.TransformToVisual(RailItemsHost).TransformPoint(default).Y;
        pillTargetY = y;
        var visual = Motion.VisualOf(RailPill);
        RailPill.Opacity = 1;
        if (!animate)
        {
            visual.Properties.InsertVector3("Translation", new(0, y, 0));
            return;
        }
        Motion.Spring(RailPill, "Translation", new(0, y, 0), damping: 0.78f, periodMs: 55);
    }

    private void OnRailLayout(object? sender, object e)
    {
        if (selectedRail is null || !rail.TryGetValue(selectedRail, out var item) || float.IsNaN(pillTargetY) || item.Button.Parent != RailItems)
        {
            return;
        }
        var y = (float)item.Button.TransformToVisual(RailItemsHost).TransformPoint(default).Y;
        if (Math.Abs(y - pillTargetY) <= 0.5f)
        {
            return;
        }
        pillTargetY = y;
        Motion.Spring(RailPill, "Translation", new(0, y, 0), damping: 0.78f, periodMs: 55);
    }

    public Task GoBackAsync() => Nav.PopAsync();

    private void OnTitleBarBack(TitleBar sender, object args) => _ = GoBackAsync();

    private void OnRootPointerPressed(object sender, PointerRoutedEventArgs e)
    {
        var props = e.GetCurrentPoint(Root).Properties;
        if (props.IsXButton1Pressed)
        {
            e.Handled = true;
            _ = GoBackAsync();
        }
    }

    public event EventHandler<string>? SearchSubmitted;
    public event EventHandler<string>? SearchChanged;
    public event EventHandler? SyncChipClicked;

    private void OnSearchSubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args) =>
        SearchSubmitted?.Invoke(this, args.QueryText.Trim());

    private void OnSearchTextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
        {
            SearchChanged?.Invoke(this, sender.Text);
        }
    }

    public void ClearSearch() => SearchBox.Text = string.Empty;

    private void OnSyncChipClick(object sender, RoutedEventArgs e) => SyncChipClicked?.Invoke(this, EventArgs.Empty);

    public void SetTopBar(FrameworkElement page, Design.TopBarState state)
    {
        topBars.AddOrUpdate(page, state);
        if (Nav.Top == page)
        {
            TopRow.Present(state);
        }
    }


    public void SetSyncState(string text, Brush dot, bool pulse)
    {
        SyncText.Text = text;
        SyncDot.Fill = dot;
        SyncHalo.Fill = dot;
        if (pulse)
        {
            var v = Motion.VisualOf(SyncHalo);
            var c = v.Compositor;
            v.CenterPoint = new(5, 5, 0);
            v.StartAnimation("Scale", Motion.Vector3(c, new(1, 1, 1), new(2.6f, 2.6f, 1), TimeSpan.FromMilliseconds(900), Curve.StandardDecelerate));
            v.StartAnimation("Opacity", Motion.Scalar(c, 0.55f, 0, TimeSpan.FromMilliseconds(900), Curve.StandardDecelerate));
        }
    }

    public void SetTheme(ElementTheme theme)
    {
        Root.RequestedTheme = theme;
        var dark = theme == ElementTheme.Dark || (theme == ElementTheme.Default && Application.Current.RequestedTheme == ApplicationTheme.Dark);
        AppWindow.TitleBar.ButtonForegroundColor = dark ? Colors.White : Colors.Black;
        AppWindow.TitleBar.ButtonHoverBackgroundColor = dark ? Windows.UI.Color.FromArgb(24, 255, 255, 255) : Windows.UI.Color.FromArgb(18, 0, 0, 0);
        ApplyRailColors();
        TopRow.Retint();
    }

    public void RestoreTitleBar() => SetTitleBar(AppTitleBar);

    public void BringToFront()
    {
        AppWindow.Show();
        if (AppWindow.Presenter is OverlappedPresenter presenter && presenter.State == OverlappedPresenterState.Minimized)
        {
            presenter.Restore();
        }
        Activate();
    }

    public bool SheetOpen => SheetHost.Visibility == Visibility.Visible;

    public void ShowSheet(FrameworkElement content)
    {
        SheetCard.Child = content;
        SheetHost.Visibility = Visibility.Visible;
        SheetScrim.Tapped -= OnScrimTapped;
        SheetScrim.Tapped += OnScrimTapped;
        AttachSheetDrag();
        var card = Motion.VisualOf(SheetCard);
        var scrim = Motion.VisualOf(SheetScrim);
        var c = card.Compositor;
        AppTitleBar.Opacity = 0.25;
        Motion.VisualOf(AppTitleBar).StartAnimation("Opacity", Motion.Scalar(c, 1f, 0.25f, Motion.Medium1, Curve.StandardDecelerate));
        SheetCard.UpdateLayout();
        card.CenterPoint = new System.Numerics.Vector3((float)SheetCard.ActualWidth / 2, (float)SheetCard.ActualHeight / 2, 0);
        card.Properties.InsertVector3("Translation", System.Numerics.Vector3.Zero);
        scrim.StartAnimation("Opacity", Motion.Scalar(c, 0, 1, Motion.Medium1, Curve.StandardDecelerate));
        card.StartAnimation("Opacity", Motion.Scalar(c, 0, 1, Motion.Short4, Curve.StandardDecelerate));
        if (!Motion.Reduced)
        {
            card.StartAnimation("Scale", Motion.Vector3(c, new(0.94f, 0.94f, 1), System.Numerics.Vector3.One, Motion.Medium4, Curve.EmphasizedDecelerate));
        }
        SheetCard.KeyDown -= OnSheetKey;
        SheetCard.KeyDown += OnSheetKey;
        content.Loaded += (_, _) => content.Focus(FocusState.Programmatic);
    }

    private bool sheetDragging;
    private double sheetDragStartY;
    private double sheetCurrentOffset;
    private long sheetLastTimeMs;
    private double sheetVelocityY;

    private void AttachSheetDrag()
    {
        SheetCard.PointerPressed -= OnSheetPointerPressed;
        SheetCard.PointerPressed += OnSheetPointerPressed;
        SheetCard.PointerMoved -= OnSheetPointerMoved;
        SheetCard.PointerMoved += OnSheetPointerMoved;
        SheetCard.PointerReleased -= OnSheetPointerReleased;
        SheetCard.PointerReleased += OnSheetPointerReleased;
        SheetCard.PointerCanceled -= OnSheetPointerReleased;
        SheetCard.PointerCanceled += OnSheetPointerReleased;
    }

    private void OnSheetPointerPressed(object sender, PointerRoutedEventArgs e)
    {
        if (Motion.Reduced) return;
        var p = e.GetCurrentPoint(SheetCard);
        if (p.Position.Y <= 64)
        {
            sheetDragging = true;
            sheetDragStartY = e.GetCurrentPoint(SheetHost).Position.Y;
            sheetCurrentOffset = 0;
            sheetVelocityY = 0;
            sheetLastTimeMs = Environment.TickCount64;
            SheetCard.CapturePointer(e.Pointer);
            e.Handled = true;
        }
    }

    private void OnSheetPointerMoved(object sender, PointerRoutedEventArgs e)
    {
        if (!sheetDragging) return;
        var currentY = e.GetCurrentPoint(SheetHost).Position.Y;
        var delta = currentY - sheetDragStartY;
        var now = Environment.TickCount64;
        var dt = Math.Max(1, now - sheetLastTimeMs);
        sheetVelocityY = (delta - sheetCurrentOffset) / (dt / 1000.0);
        sheetLastTimeMs = now;
        sheetCurrentOffset = Math.Max(0, delta);

        var card = Motion.VisualOf(SheetCard);
        card.Properties.InsertVector3("Translation", new System.Numerics.Vector3(0, (float)sheetCurrentOffset, 0));
        var cardHeight = Math.Max(100.0, SheetCard.ActualHeight);
        var progress = Math.Clamp(sheetCurrentOffset / cardHeight, 0.0, 1.0);
        var scrim = Motion.VisualOf(SheetScrim);
        scrim.Opacity = (float)(1.0 - progress * 0.75);
        e.Handled = true;
    }

    private void OnSheetPointerReleased(object sender, PointerRoutedEventArgs e)
    {
        if (!sheetDragging) return;
        sheetDragging = false;
        SheetCard.ReleasePointerCapture(e.Pointer);
        e.Handled = true;

        var cardHeight = Math.Max(100.0, SheetCard.ActualHeight);
        var projected = sheetCurrentOffset + sheetVelocityY * 0.16;
        var flingThreshold = 800.0;
        var shouldDismiss = (sheetVelocityY > flingThreshold && sheetCurrentOffset >= cardHeight * 0.45) ||
                            (projected > cardHeight * 0.5);

        if (shouldDismiss)
        {
            _ = CloseSheetAsync();
        }
        else
        {
            var card = Motion.VisualOf(SheetCard);
            Motion.Spring(SheetCard, "Translation", System.Numerics.Vector3.Zero, damping: 0.78f, periodMs: 55);
            var scrim = Motion.VisualOf(SheetScrim);
            Motion.SpringScalar(SheetScrim, "Opacity", 1f, damping: 0.78f, periodMs: 55);
        }
    }

    private void OnScrimTapped(object sender, TappedRoutedEventArgs e) => _ = CloseSheetAsync();

    private void OnSheetKey(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Escape)
        {
            e.Handled = true;
            _ = CloseSheetAsync();
        }
    }

    public async Task CloseSheetAsync()
    {
        if (!SheetOpen)
        {
            return;
        }
        var card = Motion.VisualOf(SheetCard);
        var scrim = Motion.VisualOf(SheetScrim);
        var c = card.Compositor;
        await Motion.Run(c, _ =>
        {
            scrim.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short4, Curve.StandardAccelerate));
            card.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short3, Curve.StandardAccelerate));
            Motion.VisualOf(AppTitleBar).StartAnimation("Opacity", Motion.Scalar(c, 0.25f, 1f, Motion.Short4, Curve.StandardAccelerate));
            if (!Motion.Reduced)
            {
                card.StartAnimation("Scale", Motion.Vector3(c, System.Numerics.Vector3.One, new(0.97f, 0.97f, 1), Motion.Short4, Curve.EmphasizedAccelerate));
            }
        });
        SheetHost.Visibility = Visibility.Collapsed;
        card.Properties.InsertVector3("Translation", System.Numerics.Vector3.Zero);
        AppTitleBar.Opacity = 1;
        SheetCard.Child = null;
        SheetClosed?.Invoke(this, EventArgs.Empty);
    }

    public event EventHandler? SheetClosed;
}
