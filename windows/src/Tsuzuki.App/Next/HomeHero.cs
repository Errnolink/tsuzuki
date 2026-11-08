using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;
using Windows.UI;

namespace Tsuzuki.App.Next;

public sealed partial class HomeHero : Grid
{
    private const int Cycle = 5;

    private readonly Image backdrop = new() { Stretch = Stretch.UniformToFill, Opacity = 0 };
    private readonly CoverImage cover = new() { DecodeWidth = 200 };
    private readonly Border coverFrame;
    private readonly Grid details;
    private readonly TextBlock title = ShellTheme.Text(string.Empty, "TitleLargeTextBlockStyle");
    private readonly TextBlock chapter = ShellTheme.Text(string.Empty, "BodyTextBlockStyle");
    private readonly ProgressBar progress = new() { Maximum = 100, Width = 320, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 14, 0, 0) };
    private readonly Button continueButton;
    private readonly Button detailsButton;
    private readonly PipsPager pips;
    private IReadOnlyList<ContinueEntry> entries = [];
    private CancellationTokenSource? blurCts;
    private int swapVersion;
    private bool syncing;

    public HomeHero()
    {
        Height = 320;

        var backdropLayer = new Grid { Opacity = 0.55, IsHitTestVisible = false };
        backdropLayer.Children.Add(backdrop);
        var scrim = new Border { IsHitTestVisible = false, Background = Scrim() };

        cover.Stretch = Stretch.UniformToFill;
        coverFrame = new Border
        {
            Width = 180,
            Height = 270,
            CornerRadius = new CornerRadius(6),
            Child = cover,
            Margin = new Thickness(24, 0, 0, 0),
            VerticalAlignment = VerticalAlignment.Center,
            Shadow = new ThemeShadow(),
            Translation = new Vector3(0, 0, 32),
        };

        var upNext = ShellTheme.Text("Up next", "CaptionTextBlockStyle");
        upNext.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");
        title.TextWrapping = TextWrapping.WrapWholeWords;
        title.MaxLines = 2;
        title.Margin = new Thickness(0, 4, 0, 6);
        chapter.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");

        var play = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        play.Children.Add(ShellTheme.Glyph("\uE768", 14));
        play.Children.Add(new TextBlock { Text = "Continue" });
        continueButton = new Button { Style = ShellTheme.Style("AccentButtonStyle"), Content = play, Padding = new Thickness(16, 6, 16, 7), MinWidth = 120 };
        continueButton.Click += (_, _) => _ = Log.Guard("home-hero-continue", ContinueAsync);
        detailsButton = new Button { Content = "Details", Padding = new Thickness(16, 6, 16, 7) };
        detailsButton.Click += (_, _) => _ = Log.Guard("home-hero-details", () => Current is { } e ? AppNav.OpenDetailsAsync(e.MangaId, (FrameworkElement?)coverFrame) : Task.CompletedTask);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 22, 0, 0) };
        buttons.Children.Add(continueButton);
        buttons.Children.Add(detailsButton);

        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(36, 0, 36, 0), MaxWidth = 720, HorizontalAlignment = HorizontalAlignment.Left };
        text.Children.Add(upNext);
        text.Children.Add(title);
        text.Children.Add(chapter);
        text.Children.Add(progress);
        text.Children.Add(buttons);

        details = new Grid();
        details.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        details.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        Grid.SetColumn(text, 1);
        details.Children.Add(coverFrame);
        details.Children.Add(text);

        pips = new PipsPager
        {
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(0, 0, 16, 10),
            PreviousButtonVisibility = PipsPagerButtonVisibility.VisibleOnPointerOver,
            NextButtonVisibility = PipsPagerButtonVisibility.VisibleOnPointerOver,
            Visibility = Visibility.Collapsed,
        };
        AutomationProperties.SetName(pips, "Choose a series to jump back into");
        pips.SelectedIndexChanged += (_, _) =>
        {
            var index = pips.SelectedPageIndex;
            if (!syncing && index >= 0 && index < entries.Count && entries[index].MangaId != Current?.MangaId)
            {
                _ = Log.Guard("home-hero-swap", () => ShowAsync(index, animate: true));
            }
        };

        var layers = new Grid();
        layers.Children.Add(backdropLayer);
        layers.Children.Add(scrim);
        layers.Children.Add(details);
        layers.Children.Add(pips);
        Children.Add(new Border
        {
            CornerRadius = new CornerRadius(8),
            BorderThickness = new Thickness(1),
            Background = ShellTheme.Brush("CardBackgroundFillColorDefaultBrush"),
            BorderBrush = ShellTheme.Brush("CardStrokeColorDefaultBrush"),
            Child = layers,
        });
        AutomationProperties.SetName(this, "Jump back in");
        AutomationProperties.SetLandmarkType(this, Microsoft.UI.Xaml.Automation.Peers.AutomationLandmarkType.Custom);
        AutomationProperties.SetLocalizedLandmarkType(this, "Jump back in");
    }

    public ContinueEntry? Current { get; private set; }

    public FrameworkElement CoverElement => coverFrame;

    public static string ChapterLine(ContinueEntry e)
    {
        var number = e.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(e.ChapterNumber) : e.ChapterName;
        if (e.LastPageRead <= 0)
        {
            return "Next: " + number;
        }
        return e.PageCount is > 0 ? $"{number} · Page {e.LastPageRead + 1} of {e.PageCount}" : $"{number} · Page {e.LastPageRead + 1}";
    }

    public static double? Fraction(ContinueEntry e) =>
        e.PageCount is int pages && pages > 0 && e.LastPageRead > 0 ? Math.Clamp((e.LastPageRead + 1) / (double)pages, 0, 1) : null;

    public void SetEntries(IReadOnlyList<ContinueEntry> list)
    {
        var keep = Current is { } current ? list.Take(Cycle).ToList().FindIndex(e => e.MangaId == current.MangaId) : -1;
        entries = [.. list.Take(Cycle)];
        syncing = true;
        pips.NumberOfPages = entries.Count;
        pips.Visibility = entries.Count > 1 ? Visibility.Visible : Visibility.Collapsed;
        if (entries.Count == 0)
        {
            syncing = false;
            Current = null;
            return;
        }
        var index = keep >= 0 ? keep : 0;
        pips.SelectedPageIndex = index;
        syncing = false;
        _ = Log.Guard("home-hero", () => ShowAsync(index, animate: false));
    }

    private async Task ShowAsync(int index, bool animate)
    {
        var my = ++swapVersion;
        var entry = entries[index];
        if (animate && !Motion.Reduced)
        {
            var visual = Motion.VisualOf(details);
            var c = visual.Compositor;
            await Motion.Run(c, _ => visual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short3, Curve.StandardAccelerate)));
            if (my != swapVersion)
            {
                return;
            }
        }
        var sameTitle = Current?.MangaId == entry.MangaId;
        Bind(entry);
        if (animate)
        {
            _ = Motion.FadeIn(details, Motion.Medium1, rise: 12);
        }
        if (!sameTitle || backdrop.Source is null)
        {
            await LoadBackdropAsync(entry, my);
        }
    }

    private void Bind(ContinueEntry entry)
    {
        Current = entry;
        cover.SourceId = entry.Source;
        cover.Url = entry.ThumbnailUrl;
        title.Text = entry.Title;
        ToolTipService.SetToolTip(title, entry.Title);
        var line = ChapterLine(entry);
        chapter.Text = line;
        var fraction = Fraction(entry);
        progress.Visibility = fraction is null ? Visibility.Collapsed : Visibility.Visible;
        progress.Value = (fraction ?? 0) * 100;
        AutomationProperties.SetName(progress, fraction is null ? string.Empty : $"{Math.Round(fraction.Value * 100)} percent read");
        AutomationProperties.SetName(continueButton, $"Continue {entry.Title}, {line}");
        AutomationProperties.SetName(detailsButton, $"Details for {entry.Title}");
    }

    private async Task LoadBackdropAsync(ContinueEntry entry, int my)
    {
        blurCts?.Cancel();
        var cts = blurCts = new CancellationTokenSource();
        var visual = Motion.VisualOf(backdrop);
        var c = visual.Compositor;
        if (backdrop.Source is not null && !Motion.Reduced)
        {
            await Motion.Run(c, _ => visual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short3, Curve.StandardAccelerate)));
        }
        var source = await CoverBlur.LoadAsync(entry.ThumbnailUrl, entry.Source, cts.Token);
        if (my != swapVersion || cts.IsCancellationRequested)
        {
            return;
        }
        backdrop.Source = source;
        backdrop.Opacity = 1;
        if (source is not null)
        {
            await Motion.FadeIn(backdrop, Motion.Medium2);
        }
    }

    private Task ContinueAsync() =>
        Current is { } e ? AppNav.OpenReaderAsync(e.MangaId, e.ChapterId, Math.Max(e.LastPageRead, 0), coverFrame) : Task.CompletedTask;

    private Brush Scrim()
    {
        var dark = Application.Current.RequestedTheme == ApplicationTheme.Dark;
        var tint = dark ? Color.FromArgb(255, 0x20, 0x20, 0x20) : Color.FromArgb(255, 0xF3, 0xF3, 0xF3);
        return new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0, 1),
            EndPoint = new Windows.Foundation.Point(1, 0),
            GradientStops =
            {
                new GradientStop { Color = WithAlpha(tint, dark ? 0.82 : 0.78), Offset = 0 },
                new GradientStop { Color = WithAlpha(tint, dark ? 0.45 : 0.38), Offset = 0.55 },
                new GradientStop { Color = WithAlpha(tint, 0.05), Offset = 1 },
            },
        };
    }

    private static Color WithAlpha(Color color, double alpha) => Color.FromArgb((byte)Math.Round(alpha * 255), color.R, color.G, color.B);
}
