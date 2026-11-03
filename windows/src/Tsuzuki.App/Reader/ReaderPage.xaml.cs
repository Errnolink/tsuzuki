using System.Numerics;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Input;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Windows.Foundation;
using Windows.System;
using DispatcherQueueTimer = Microsoft.UI.Dispatching.DispatcherQueueTimer;
using DispatcherQueuePriority = Microsoft.UI.Dispatching.DispatcherQueuePriority;

namespace Tsuzuki.App.Reader;

public sealed partial class ReaderPage : UserControl
{
    private static ReaderPage? current;

    private readonly long mangaId;
    private readonly Dictionary<int, (DecodedPage Page, List<SoftwareBitmapSource> Sources)> decoded = [];
    private readonly Dictionary<int, Task<(DecodedPage Page, List<SoftwareBitmapSource> Sources)>> decoding = [];
    private readonly DispatcherQueueTimer chromeTimer;
    private readonly DispatcherQueueTimer saveTimer;
    private readonly StripFactory stripFactory;
    private MangaDetails? manga;
    private IReadOnlyList<ChapterGroup> ascending = [];
    private ChapterRow? chapter;
    private IReadOnlyList<PageRef> pages = [];
    private Tsuzuki.Core.Reader.ReaderSpreadPlanner? spreadPlanner;
    private bool? spreadFirstPageAlone;
    private int index;
    private int loadVersion;
    private int chapterVersion;
    private bool chromeVisible = true;
    private bool pointerOverChrome;
    private double wheelAccumulator;
    private DateTime lastWheelTurn;
    private long lastTurnMs;
    private int pendingSaveIndex = -1;
    private long pendingSaveChapter = -1;
    private FrameworkElement? origin;
    private ReaderMode mode;
    private bool closing;

    private ReaderPage(long mangaId)
    {
        InitializeComponent();
        A11y.NameFromTooltips(BackButton, ModeSingle, ModeDouble, ModeStrip, DirectionButton, FitButton, FullscreenButton, PrevChapter, NextChapter);
        this.mangaId = mangaId;
        mode = Services.Settings.ReaderMode;
        chromeTimer = DispatcherQueue.CreateTimer();
        chromeTimer.Interval = TimeSpan.FromMilliseconds(2400);
        chromeTimer.IsRepeating = false;
        chromeTimer.Tick += (_, _) =>
        {
            if (!DevHarness.Active && !pointerOverChrome && !PageSlider.FocusState.HasFlag(FocusState.Pointer))
            {
                SetChrome(false);
            }
        };
        saveTimer = DispatcherQueue.CreateTimer();
        saveTimer.Interval = TimeSpan.FromMilliseconds(600);
        saveTimer.IsRepeating = false;
        saveTimer.Tick += (_, _) => _ = FlushProgressAsync();

        stripFactory = new StripFactory(this);
        Strip.ItemTemplate = stripFactory;

        BackButton.Click += (_, _) => _ = CloseAsync();
        ModeSingle.Click += (_, _) => SetMode(ReaderMode.Single);
        ModeDouble.Click += (_, _) => SetMode(ReaderMode.Double);
        ModeStrip.Click += (_, _) => SetMode(ReaderMode.Webtoon);
        DirectionButton.Click += (_, _) => ToggleDirection();
        FitButton.Click += (_, _) => CycleFit();
        FullscreenButton.Click += (_, _) => ToggleFullscreen();
        PrevChapter.Click += (_, _) => _ = GoChapterAsync(-1);
        NextChapter.Click += (_, _) => _ = GoChapterAsync(1);
        EndNext.Click += (_, _) => _ = GoChapterAsync(1);
        EndClose.Click += (_, _) => _ = CloseAsync();
        MessageAction.Click += (_, _) => { if (chapter is not null) _ = LoadChapterAsync(chapter.ChapterId, index); };
        PageSlider.ValueChanged += (_, e) =>
        {
            var target = (int)e.NewValue - 1;
            if (target != index && pages.Count > 0 && Math.Abs(e.NewValue - e.OldValue) > 0.5)
            {
                if (mode == ReaderMode.Webtoon)
                {
                    index = Math.Clamp(target, 0, pages.Count - 1);
                    ScrollStripTo(index);
                    UpdatePageLabels(index, null);
                    QueueProgress(index);
                }
                else
                {
                    _ = GoToPageAsync(target, animate: false);
                }
            }
        };

        foreach (var bar in new FrameworkElement[] { TopBar, BottomBar })
        {
            bar.PointerEntered += (_, _) => pointerOverChrome = true;
            bar.PointerExited += (_, _) => { pointerOverChrome = false; chromeTimer.Start(); };
        }
        Root.PointerMoved += OnPointerMoved;
        Root.Tapped += OnTapped;
        Root.DoubleTapped += OnDoubleTapped;
        PagedScroll.AddHandler(PointerWheelChangedEvent, new PointerEventHandler(OnPagedWheel), true);
        PagedScroll.ViewChanged += (_, _) => UpdatePagedEdgeState();
        StripScroll.ViewChanged += (_, _) => OnStripScrolled();
        Root.SizeChanged += (_, _) => LayoutPaged();
        KeyDown += OnKeyDown;
        Root.PointerPressed += (_, e) =>
        {
            transition?.Cancel();
            if (e.GetCurrentPoint(Root).Properties.IsXButton1Pressed)
            {
                e.Handled = true;
                _ = CloseAsync();
            }
        };
        ApplyModeButtons();
        DirectionText.Text = Services.Settings.RightToLeft ? "RTL" : "LTR";
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static Task CloseCurrentAsync() => current?.CloseAsync(animate: false) ?? Task.CompletedTask;

    public static async Task OpenAsync(long mangaId, long chapterId, int page, FrameworkElement? from)
    {
        if (current is not null)
        {
            await current.CloseAsync(animate: false);
        }
        var window = App.MainWindow;
        var reader = new ReaderPage(mangaId) { origin = from };
        current = reader;
        window.ReaderLayer.Children.Clear();
        window.ReaderLayer.Children.Add(reader);
        window.ReaderLayer.Visibility = Visibility.Visible;
        reader.UpdateLayout();
        window.SetTitleBar(reader.DragArea);
        reader.Focus(FocusState.Programmatic);
        var reveal = reader.RevealAsync(from);
        var load = reader.InitializeAsync(chapterId, page);
        await Task.WhenAll(reveal, load);
        reader.ShowHintOnce();
        reader.chromeTimer.Start();
    }

    private DispatcherQueueTimer? hintTimer;
    private bool hintHidden;

    private void ShowHintOnce()
    {
        if (Services.Settings.ReaderHintsShown || hintHidden)
        {
            return;
        }
        Services.Settings.ReaderHintsShown = true;
        Services.Settings.Save();
        HintText.Text = mode == ReaderMode.Webtoon
            ? "Scroll to read · ← → switch chapters · chrome hides as you read"
            : "← → or click the page edges to turn · scroll wheel pages · chrome hides as you read";
        HintBar.Visibility = Visibility.Visible;
        var visual = Motion.VisualOf(HintBar);
        var compositor = visual.Compositor;
        visual.StartAnimation("Opacity", Motion.Scalar(compositor, 0, 1, Motion.Medium2, Curve.StandardDecelerate));
        hintTimer = DispatcherQueue.CreateTimer();
        hintTimer.Interval = TimeSpan.FromSeconds(7);
        hintTimer.IsRepeating = false;
        hintTimer.Tick += (_, _) => HideHint();
        hintTimer.Start();
    }

    private async void HideHint()
    {
        if (hintHidden || HintBar.Visibility != Visibility.Visible)
        {
            return;
        }
        hintHidden = true;
        hintTimer?.Stop();
        var visual = Motion.VisualOf(HintBar);
        var compositor = visual.Compositor;
        await Motion.Run(compositor, _ =>
        {
            visual.StartAnimation("Opacity", Motion.Scalar(compositor, 1, 0, Motion.Short3, Curve.StandardAccelerate));
        });
        HintBar.Visibility = Visibility.Collapsed;
    }

    private async Task RevealAsync(FrameworkElement? from)
    {
        var layer = App.MainWindow.ReaderLayer;
        if (from is not null && from.XamlRoot is not null && !Motion.Reduced)
        {
            var rect = ContainerTransform.BoundsIn(from, layer);
            if (ContainerTransform.IsOnScreen(rect, layer))
            {
                await ContainerTransform.Expand(this, rect, new Rect(0, 0, ActualWidth, ActualHeight), 18, Motion.Medium4, [TopBar, BottomBar], null, BeginTransition());
                return;
            }
        }
        await Motion.FadeIn(this, Motion.Medium1);
    }

    private async Task InitializeAsync(long chapterId, int page)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        manga = await engine.Library.GetMangaAsync(mangaId);
        var rows = await engine.Library.GetChaptersAsync(mangaId);
        ascending = ChapterGrouping.Group(rows).Reverse().ToList();
        if (manga is not null && manga.Genres.Any(g => g.Equals("Long Strip", StringComparison.OrdinalIgnoreCase) || g.Equals("Web Comic", StringComparison.OrdinalIgnoreCase)) && mode != ReaderMode.Webtoon && !Services.Settings.ReaderModeChosen)
        {
            mode = ReaderMode.Webtoon;
            ApplyModeButtons();
        }
        TitleLine.Text = manga?.Title ?? string.Empty;
        await LoadChapterAsync(chapterId, page);
    }

    private async Task LoadChapterAsync(long chapterId, int page)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        await FlushProgressAsync();
        var my = ++chapterVersion;
        ReleaseAll();
        Message.Visibility = Visibility.Collapsed;
        EndCard.Visibility = Visibility.Collapsed;
        PageLeft.Source = null;
        PageRight.Source = null;
        LoadingRing.IsActive = true;
        chapter = await engine.Library.GetChapterAsync(chapterId);
        if (chapter is null || my != chapterVersion)
        {
            return;
        }
        ChapterLine.Text = ChapterTitle(chapter);
        var source = engine.Sources.Get(chapter.Source);
        if (source is null)
        {
            ShowMessage("This chapter reads on your phone", $"{engine.Sources.DisplayName(chapter.Source)} isn't available on Windows yet.", null);
            return;
        }
        try
        {
            pages = await source.GetPageListAsync(chapter.Url);
            spreadPlanner = null;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or InvalidOperationException)
        {
            if (my == chapterVersion)
            {
                ShowMessage("Couldn't load this chapter", "Check your connection, then try again.", "Try again");
            }
            return;
        }
        if (my != chapterVersion)
        {
            return;
        }
        if (pages.Count == 0)
        {
            ShowMessage("No pages here", chapter.ExternalUrl is null ? "This chapter has no pages on the source." : "This chapter is hosted on the publisher's site.", null);
            return;
        }
        index = Math.Clamp(page, 0, pages.Count - 1);
        PageSlider.Minimum = 1;
        PageSlider.Maximum = Math.Max(1, pages.Count);
        PageTotal.Text = pages.Count.ToString();
        UpdateChapterButtons();
        engine.Pages.Prefetch(pages.Skip(index).Take(6).Select(p => p.ImageUrl));
        if (mode == ReaderMode.Webtoon)
        {
            ShowStrip();
        }
        else
        {
            ShowPaged();
            await GoToPageAsync(index, animate: false, force: true);
        }
        QueueProgress(index);
    }

    private void ShowMessage(string title, string body, string? action)
    {
        LoadingRing.IsActive = false;
        MessageTitle.Text = title;
        MessageBody.Text = body;
        MessageAction.Visibility = action is null ? Visibility.Collapsed : Visibility.Visible;
        Message.Visibility = Visibility.Visible;
        SetChrome(true);
    }

    private static string ChapterTitle(ChapterRow c)
    {
        var number = c.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(c.ChapterNumber) : c.Name;
        return c.Scanlator is null ? number : $"{number} · {c.Scanlator}";
    }

    private int ViewportPixelHeight => (int)Math.Max(800, Root.ActualHeight * (XamlRoot?.RasterizationScale ?? 1));

    private Task<(DecodedPage Page, List<SoftwareBitmapSource> Sources)> GetPageAsync(int i, ImagePriority priority)
    {
        if (decoded.TryGetValue(i, out var ready))
        {
            return Task.FromResult(ready);
        }
        if (decoding.TryGetValue(i, out var pending))
        {
            return pending;
        }
        var task = LoadPageAsync(i, priority, chapterVersion);
        decoding[i] = task;
        return task;
    }

    private async Task<(DecodedPage Page, List<SoftwareBitmapSource> Sources)> LoadPageAsync(int i, ImagePriority priority, int version)
    {
        try
        {
            var engine = Services.Engine!;
            var path = await engine.Pages.GetFileAsync(pages[i].ImageUrl, priority);
            var scale = XamlRoot?.RasterizationScale ?? 1;
            DecodedPage page;
            if (mode == ReaderMode.Webtoon)
            {
                var width = (int)Math.Min(2000, StripWidth * scale);
                page = await PageDecoder.DecodeAsync(path, width, int.MaxValue, CancellationToken.None);
            }
            else
            {
                var maxHeight = (int)Math.Min(3400, ViewportPixelHeight * 1.6);
                page = await PageDecoder.DecodeAsync(path, 4000, Math.Max(1600, maxHeight), CancellationToken.None);
            }
            var sources = new List<SoftwareBitmapSource>(page.Slices.Count);
            foreach (var slice in page.Slices)
            {
                sources.Add(await PageDecoder.ToSourceAsync(slice.Bitmap));
            }
            var result = (page, sources);
            if (version == chapterVersion)
            {
                decoded[i] = result;
            }
            return result;
        }
        finally
        {
            if (version == chapterVersion)
            {
                decoding.Remove(i);
            }
        }
    }

    private void Evict(Func<int, bool> keep)
    {
        foreach (var key in decoded.Keys.Where(k => !keep(k)).ToList())
        {
            var (page, sources) = decoded[key];
            decoded.Remove(key);
            foreach (var s in sources)
            {
                s.Dispose();
            }
            foreach (var slice in page.Slices)
            {
                slice.Bitmap.Dispose();
            }
        }
    }

    private void ReleaseAll()
    {
        Evict(_ => false);
        decoding.Clear();
        stripFactory.Reset();
        Strip.ItemsSource = null;
    }

    private void ShowPaged()
    {
        StripScroll.Visibility = Visibility.Collapsed;
        PagedScroll.Visibility = Visibility.Visible;
    }

    private (int First, int? Second) SpreadFor(int i)
    {
        if (mode != ReaderMode.Double || pages.Count < 2)
        {
            return (i, null);
        }
        if (spreadPlanner is null || spreadFirstPageAlone != Services.Settings.FirstPageAlone)
        {
            spreadFirstPageAlone = Services.Settings.FirstPageAlone;
            spreadPlanner = new Tsuzuki.Core.Reader.ReaderSpreadPlanner(
                pages.Count, spreadFirstPageAlone.Value,
                i => decoded.TryGetValue(i, out var d) && d.Page.SourceWidth > d.Page.SourceHeight);
        }
        return spreadPlanner.SpreadFor(i);
    }

    private async Task GoToPageAsync(int target, bool animate, bool force = false, int direction = 0)
    {
        if (pages.Count == 0 || mode == ReaderMode.Webtoon)
        {
            return;
        }
        target = Math.Clamp(target, 0, pages.Count - 1);
        if (!force && target == index && PageLeft.Source is not null)
        {
            return;
        }
        var my = ++loadVersion;
        var engine = Services.Engine!;
        var spinner = Task.Delay(140).ContinueWith(_ => DispatcherQueue.TryEnqueue(() => { if (my == loadVersion) LoadingRing.IsActive = true; }), TaskScheduler.Default);
        var (first, second) = SpreadFor(target);
        var firstPage = await GetPageAsync(first, ImagePriority.Visible);
        (DecodedPage Page, List<SoftwareBitmapSource> Sources)? secondPage = null;
        if (second is { } s)
        {
            secondPage = await GetPageAsync(s, ImagePriority.Visible);
            if (secondPage.Value.Page.SourceWidth > secondPage.Value.Page.SourceHeight || firstPage.Page.SourceWidth > firstPage.Page.SourceHeight)
            {
                secondPage = null;
                second = null;
            }
        }
        if (my != loadVersion)
        {
            return;
        }
        LoadingRing.IsActive = false;
        Message.Visibility = Visibility.Collapsed;
        index = target;
        spreadPlanner?.OnDisplayed(first, secondPage is not null);
        var rtl = Services.Settings.RightToLeft;
        if (secondPage is { } sp)
        {
            PageLeft.Source = rtl ? sp.Sources[0] : firstPage.Sources[0];
            PageRight.Source = rtl ? firstPage.Sources[0] : sp.Sources[0];
            PageLeft.Tag = rtl ? sp.Page : firstPage.Page;
            PageRight.Tag = rtl ? firstPage.Page : sp.Page;
            PageRight.Visibility = Visibility.Visible;
            Spine.Visibility = Visibility.Visible;
        }
        else
        {
            PageLeft.Source = firstPage.Sources[0];
            PageLeft.Tag = firstPage.Page;
            PageRight.Source = null;
            PageRight.Visibility = Visibility.Collapsed;
            Spine.Visibility = Visibility.Collapsed;
        }
        LayoutPaged();
        PagedScroll.ZoomTo(1, null, new ScrollingZoomOptions(ScrollingAnimationMode.Disabled));
        PagedScroll.ScrollTo(0, 0, new ScrollingScrollOptions(ScrollingAnimationMode.Disabled));
        if (animate && !Motion.Reduced)
        {
            var now = Environment.TickCount64;
            var pace = lastTurnMs == 0 ? 1d : Math.Clamp((now - lastTurnMs - 220d) / 780d, 0d, 1d);
            lastTurnMs = now;
            var v = Motion.VisualOf(Spread);
            var c = v.Compositor;
            var dx = direction == 0 ? 0 : (direction > 0 ^ rtl ? 1f : -1f) * (float)(20 + 24 * pace);
            var slide = TimeSpan.FromMilliseconds(140 + 80 * pace);
            v.StartAnimation("Translation", Motion.Vector3(c, new Vector3(dx, 0, 0), Vector3.Zero, slide, Curve.EmphasizedDecelerate));
            v.StartAnimation("Opacity", Motion.Scalar(c, 0.35f, 1, TimeSpan.FromMilliseconds(110 + 60 * pace), Curve.StandardDecelerate));
        }
        var shownLast = second ?? first;
        UpdatePageLabels(first, second);
        QueueProgress(shownLast);
        EndCard.Visibility = Visibility.Collapsed;

        var keepFrom = first - 2;
        var keepTo = shownLast + 4;
        Evict(k => k >= keepFrom && k <= keepTo);
        engine.Pages.Prefetch(pages.Skip(shownLast + 1).Take(6).Select(p => p.ImageUrl));
        for (var ahead = shownLast + 1; ahead <= Math.Min(pages.Count - 1, shownLast + 3); ahead++)
        {
            _ = GetPageAsync(ahead, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
        if (first > 0)
        {
            _ = GetPageAsync(first - 1, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
        if (shownLast >= pages.Count - 2)
        {
            PrefetchNextChapter();
        }
    }

    private void LayoutPaged()
    {
        if (mode == ReaderMode.Webtoon || PageLeft.Tag is not DecodedPage left)
        {
            return;
        }
        var vw = Math.Max(1, Root.ActualWidth);
        var vh = Math.Max(1, Root.ActualHeight);
        var right = PageRight.Visibility == Visibility.Visible ? PageRight.Tag as DecodedPage : null;
        var totalAspectWidth = 1 / left.Aspect + (right is null ? 0 : 1 / right.Aspect);
        double height;
        switch (Services.Settings.ReaderFit)
        {
            case ReaderFit.Width:
                height = vw / totalAspectWidth;
                break;
            case ReaderFit.Original:
                height = left.SourceHeight / (XamlRoot?.RasterizationScale ?? 1);
                break;
            default:
                height = Math.Min(vh, vw / totalAspectWidth);
                break;
        }
        PageLeft.Height = height;
        PageLeft.Width = height / left.Aspect;
        if (right is not null)
        {
            PageRight.Height = height;
            PageRight.Width = height / right.Aspect;
        }
        var contentWidth = PageLeft.Width + (right is null ? 0 : PageRight.Width);
        PagedContent.Width = Math.Max(vw, contentWidth);
        PagedContent.Height = Math.Max(vh, height);
    }

    private void UpdatePageLabels(int first, int? second)
    {
        PageLabel.Text = second is { } s ? $"{first + 1}–{s + 1}" : (first + 1).ToString();
        PageSlider.Value = first + 1;
    }

    private void UpdateChapterButtons()
    {
        var position = CurrentGroupIndex();
        PrevChapter.IsEnabled = position > 0;
        NextChapter.IsEnabled = position >= 0 && position < ascending.Count - 1;
    }

    private int CurrentGroupIndex()
    {
        if (chapter is null)
        {
            return -1;
        }
        for (var i = 0; i < ascending.Count; i++)
        {
            var g = ascending[i];
            if (g.Primary.ChapterId == chapter.ChapterId || g.Alternates.Any(a => a.ChapterId == chapter.ChapterId))
            {
                return i;
            }
        }
        return -1;
    }

    private ChapterRow? ReadableCopy(ChapterGroup group)
    {
        if (chapter?.Scanlator is { } scan)
        {
            var same = new[] { group.Primary }.Concat(group.Alternates).FirstOrDefault(c => c.Scanlator == scan && c.Readable && c.ExternalUrl is null);
            if (same is not null)
            {
                return same;
            }
        }
        return new[] { group.Primary }.Concat(group.Alternates).FirstOrDefault(c => c.Readable && c.ExternalUrl is null);
    }

    private ChapterRow? NeighbourChapter(int delta)
    {
        var position = CurrentGroupIndex();
        if (position < 0)
        {
            return null;
        }
        for (var i = position + delta; i >= 0 && i < ascending.Count; i += delta)
        {
            var copy = ReadableCopy(ascending[i]);
            if (copy is not null)
            {
                return copy;
            }
        }
        return null;
    }

    private void PrefetchNextChapter()
    {
        if (NeighbourChapter(1) is not { } next || Services.Engine is not { } engine || engine.Sources.Get(next.Source) is not { } source)
        {
            return;
        }
        _ = Task.Run(async () =>
        {
            try
            {
                var list = await source.GetPageListAsync(next.Url);
                engine.Pages.Prefetch(list.Take(3).Select(p => p.ImageUrl));
            }
            catch (Exception)
            {
            }
        });
    }

    private async Task GoChapterAsync(int delta)
    {
        var target = NeighbourChapter(delta);
        if (target is null)
        {
            if (delta > 0)
            {
                ShowEndCard(null);
            }
            return;
        }
        if (delta > 0 && chapter is not null)
        {
            await MarkCurrentReadAsync();
        }
        await LoadChapterAsync(target.ChapterId, delta > 0 ? 0 : (target.Read ? 0 : target.LastPageRead));
    }

    private async Task MarkCurrentReadAsync()
    {
        if (chapter is null || Services.Engine is not { } engine || pages.Count == 0)
        {
            return;
        }
        saveTimer.Stop();
        pendingSaveIndex = -1;
        await engine.Library.SetChapterProgressAsync(chapter.ChapterId, pages.Count - 1, true);
    }

    private void ShowEndCard(ChapterRow? next)
    {
        next ??= NeighbourChapter(1);
        EndTitle.Text = next is null ? "You're all caught up" : "Up next: " + (next.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(next.ChapterNumber) : next.Name);
        EndBody.Text = next is null ? "No newer chapters are available to read here yet." : next.Scanlator ?? string.Empty;
        EndNext.Visibility = next is null ? Visibility.Collapsed : Visibility.Visible;
        EndCard.Visibility = Visibility.Visible;
        _ = Motion.FadeIn(EndCard, Motion.Medium1, 12);
        if (next is null)
        {
            _ = MarkCurrentReadAsync();
        }
    }

    private async Task NextAsync()
    {
        if (mode == ReaderMode.Webtoon)
        {
            ScrollStrip(0.85);
            return;
        }
        if (EndCard.Visibility == Visibility.Visible)
        {
            await GoChapterAsync(1);
            return;
        }
        var (first, second) = SpreadFor(index);
        var last = second ?? first;
        if (last >= pages.Count - 1)
        {
            await MarkCurrentReadAsync();
            ShowEndCard(null);
            return;
        }
        await GoToPageAsync(last + 1, animate: true, direction: 1);
    }

    private async Task PreviousAsync()
    {
        if (mode == ReaderMode.Webtoon)
        {
            ScrollStrip(-0.85);
            return;
        }
        if (EndCard.Visibility == Visibility.Visible)
        {
            EndCard.Visibility = Visibility.Collapsed;
            return;
        }
        var (first, _) = SpreadFor(index);
        if (first <= 0)
        {
            var previous = NeighbourChapter(-1);
            if (previous is not null)
            {
                await LoadChapterAsync(previous.ChapterId, int.MaxValue);
            }
            return;
        }
        var target = first - 1;
        if (mode == ReaderMode.Double)
        {
            var (pf, _) = SpreadFor(target);
            target = pf;
        }
        await GoToPageAsync(target, animate: true, direction: -1);
    }

    private void QueueProgress(int pageIndex)
    {
        if (chapter is null)
        {
            return;
        }
        pendingSaveIndex = pageIndex;
        pendingSaveChapter = chapter.ChapterId;
        saveTimer.Stop();
        saveTimer.Start();
    }

    private async Task FlushProgressAsync()
    {
        saveTimer.Stop();
        if (pendingSaveIndex < 0 || pendingSaveChapter < 0 || Services.Engine is not { } engine)
        {
            return;
        }
        var chapterId = pendingSaveChapter;
        var page = pendingSaveIndex;
        var read = chapter?.ChapterId == chapterId && pages.Count > 0 && page >= pages.Count - 1;
        pendingSaveIndex = -1;
        pendingSaveChapter = -1;
        await engine.Library.SetChapterProgressAsync(chapterId, page, read);
    }

    private void SetMode(ReaderMode next)
    {
        Services.Settings.ReaderMode = next;
        Services.Settings.ReaderModeChosen = true;
        Services.Settings.Save();
        if (next == mode)
        {
            return;
        }
        var keep = index;
        mode = next;
        ApplyModeButtons();
        ReleaseAll();
        if (pages.Count == 0)
        {
            return;
        }
        index = keep;
        if (mode == ReaderMode.Webtoon)
        {
            ShowStrip();
        }
        else
        {
            ShowPaged();
            _ = GoToPageAsync(keep, animate: false, force: true);
        }
        Toasts.Show(mode switch { ReaderMode.Single => "Single page", ReaderMode.Double => "Two pages", _ => "Long strip" }, null, Icons.BookOpen, seconds: 2);
    }

    private void ApplyModeButtons()
    {
        foreach (var (button, m) in new[] { (ModeSingle, ReaderMode.Single), (ModeDouble, ReaderMode.Double), (ModeStrip, ReaderMode.Webtoon) })
        {
            button.Background = m == mode ? new SolidColorBrush(Windows.UI.Color.FromArgb(46, 255, 255, 255)) : new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        }
        DirectionButton.Visibility = mode == ReaderMode.Webtoon ? Visibility.Collapsed : Visibility.Visible;
        FitButton.Visibility = mode == ReaderMode.Webtoon ? Visibility.Collapsed : Visibility.Visible;
    }

    private void ToggleDirection()
    {
        Services.Settings.RightToLeft = !Services.Settings.RightToLeft;
        Services.Settings.Save();
        DirectionText.Text = Services.Settings.RightToLeft ? "RTL" : "LTR";
        _ = GoToPageAsync(index, animate: false, force: true);
        Toasts.Show(Services.Settings.RightToLeft ? "Right to left" : "Left to right", "Arrow keys and clicks follow the reading direction.", Icons.BookOpen, seconds: 2);
    }

    private void CycleFit()
    {
        Services.Settings.ReaderFit = Services.Settings.ReaderFit switch
        {
            ReaderFit.Height => ReaderFit.Width,
            ReaderFit.Width => ReaderFit.Original,
            _ => ReaderFit.Height,
        };
        Services.Settings.Save();
        LayoutPaged();
        Toasts.Show(Services.Settings.ReaderFit switch { ReaderFit.Height => "Fit to screen", ReaderFit.Width => "Fit to width", _ => "Original size" }, null, Icons.Maximize, seconds: 2);
    }

    private void ToggleFullscreen()
    {
        var window = App.MainWindow.AppWindow;
        var full = window.Presenter.Kind == AppWindowPresenterKind.FullScreen;
        window.SetPresenter(full ? AppWindowPresenterKind.Overlapped : AppWindowPresenterKind.FullScreen);
        CaptionSpace.Width = new GridLength(full ? 140 : 0);
    }

    private void SetChrome(bool visible)
    {
        if (visible == chromeVisible)
        {
            if (visible)
            {
                chromeTimer.Stop();
                chromeTimer.Start();
            }
            return;
        }
        chromeVisible = visible;
        if (Motion.FluentTimings && !Motion.Reduced)
        {
            Motion.Spring(TopBar, "Translation", new Vector3(0, visible ? 0 : -56, 0), damping: 0.78f, periodMs: 55);
            Motion.SpringScalar(TopBar, "Opacity", visible ? 1 : 0);
            Motion.Spring(BottomBar, "Translation", new Vector3(0, visible ? 0 : 40, 0), damping: 0.78f, periodMs: 55);
            Motion.SpringScalar(BottomBar, "Opacity", visible ? 1 : 0);
        }
        else
        {
            var top = Motion.VisualOf(TopBar);
            var bottom = Motion.VisualOf(BottomBar);
            var c = top.Compositor;
            var duration = visible ? Motion.Short4 : TimeSpan.FromMilliseconds(167);
            var curve = visible ? Curve.EmphasizedDecelerate : Curve.EmphasizedAccelerate;
            top.StartAnimation("Translation", Motion.Vector3(c, new Vector3(0, visible ? -56 : 0, 0), new Vector3(0, visible ? 0 : -56, 0), duration, curve));
            top.StartAnimation("Opacity", Motion.Scalar(c, visible ? 0 : 1, visible ? 1 : 0, duration, curve));
            bottom.StartAnimation("Translation", Motion.Vector3(c, new Vector3(0, visible ? 40 : 0, 0), new Vector3(0, visible ? 0 : 40, 0), duration, curve));
            bottom.StartAnimation("Opacity", Motion.Scalar(c, visible ? 0 : 1, visible ? 1 : 0, duration, curve));
        }
        TopBar.IsHitTestVisible = visible;
        BottomBar.IsHitTestVisible = visible;
        if (visible)
        {
            chromeTimer.Start();
        }
    }

    private void OnPointerMoved(object sender, PointerRoutedEventArgs e)
    {
        var y = e.GetCurrentPoint(Root).Position.Y;
        if (y < 80 || y > Root.ActualHeight - 110)
        {
            SetChrome(true);
        }
    }

    private void OnTapped(object sender, TappedRoutedEventArgs e)
    {
        HideHint();
        if (IsInside(e.OriginalSource as DependencyObject, TopBar) || IsInside(e.OriginalSource as DependencyObject, BottomBar)
            || IsInside(e.OriginalSource as DependencyObject, EndCard) || IsInside(e.OriginalSource as DependencyObject, Message))
        {
            return;
        }
        var x = e.GetPosition(Root).X / Math.Max(1, Root.ActualWidth);
        if (mode == ReaderMode.Webtoon || (x > 0.3 && x < 0.7))
        {
            SetChrome(!chromeVisible);
            return;
        }
        var forward = x >= 0.7 ^ Services.Settings.RightToLeft;
        _ = forward ? NextAsync() : PreviousAsync();
    }

    private static bool IsInside(DependencyObject? node, DependencyObject container)
    {
        while (node is not null)
        {
            if (node == container)
            {
                return true;
            }
            node = VisualTreeHelper.GetParent(node);
        }
        return false;
    }

    private void OnDoubleTapped(object sender, DoubleTappedRoutedEventArgs e)
    {
        if (mode == ReaderMode.Webtoon)
        {
            return;
        }
        var zoomed = PagedScroll.ZoomFactor > 1.05f;
        var point = e.GetPosition(PagedScroll);
        PagedScroll.ZoomTo(zoomed ? 1f : 2.2f, zoomed ? null : new Vector2((float)point.X, (float)point.Y), new ScrollingZoomOptions(ScrollingAnimationMode.Enabled));
    }

    private void OnPagedWheel(object sender, PointerRoutedEventArgs e)
    {
        if (mode == ReaderMode.Webtoon)
        {
            return;
        }
        var props = e.GetCurrentPoint(PagedScroll).Properties;
        if (e.KeyModifiers.HasFlag(VirtualKeyModifiers.Control) || props.IsHorizontalMouseWheel)
        {
            return;
        }
        var delta = props.MouseWheelDelta;
        var scrollable = PagedScroll.ScrollableHeight > 1 || PagedScroll.ZoomFactor > 1.02f;
        if (scrollable)
        {
            var atBottom = PagedScroll.VerticalOffset >= PagedScroll.ScrollableHeight - 1;
            var atTop = PagedScroll.VerticalOffset <= 1;
            if (!((delta < 0 && atBottom) || (delta > 0 && atTop)))
            {
                wheelAccumulator = 0;
                return;
            }
        }
        wheelAccumulator += delta;
        if ((DateTime.UtcNow - lastWheelTurn).TotalMilliseconds < 220)
        {
            return;
        }
        if (Math.Abs(wheelAccumulator) >= 100)
        {
            var forward = wheelAccumulator < 0;
            wheelAccumulator = 0;
            lastWheelTurn = DateTime.UtcNow;
            e.Handled = true;
            _ = forward ? NextAsync() : PreviousAsync();
        }
    }

    private void UpdatePagedEdgeState()
    {
    }

    private void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        HideHint();
        var rtl = Services.Settings.RightToLeft && mode != ReaderMode.Webtoon;
        var ctrl = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        var shift = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Shift).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        switch (e.Key)
        {
            case VirtualKey.Right:
                _ = rtl ? PreviousAsync() : NextAsync();
                break;
            case VirtualKey.Left:
                _ = rtl ? NextAsync() : PreviousAsync();
                break;
            case VirtualKey.Space:
                _ = shift ? PreviousAsync() : NextAsync();
                break;
            case VirtualKey.PageDown:
                _ = NextAsync();
                break;
            case VirtualKey.PageUp:
                _ = PreviousAsync();
                break;
            case VirtualKey.Down when mode == ReaderMode.Webtoon:
                ScrollStrip(0.2);
                break;
            case VirtualKey.Up when mode == ReaderMode.Webtoon:
                ScrollStrip(-0.2);
                break;
            case VirtualKey.Home:
                if (mode == ReaderMode.Webtoon) ScrollStripTo(0); else _ = GoToPageAsync(0, animate: true, direction: -1);
                break;
            case VirtualKey.End:
                if (mode == ReaderMode.Webtoon) ScrollStripTo(pages.Count - 1); else _ = GoToPageAsync(pages.Count - 1, animate: true, direction: 1);
                break;
            case VirtualKey.F11:
                ToggleFullscreen();
                break;
            case VirtualKey.Escape:
                if (App.MainWindow.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen)
                {
                    ToggleFullscreen();
                }
                else
                {
                    _ = CloseAsync();
                }
                break;
            case VirtualKey.Back:
                _ = CloseAsync();
                break;
            case VirtualKey.Number1:
                SetMode(ReaderMode.Single);
                break;
            case VirtualKey.Number2:
                SetMode(ReaderMode.Double);
                break;
            case VirtualKey.Number3:
                SetMode(ReaderMode.Webtoon);
                break;
            case VirtualKey.R:
                ToggleDirection();
                break;
            case VirtualKey.F:
                CycleFit();
                break;
            case (VirtualKey)219:
                _ = GoChapterAsync(-1);
                break;
            case (VirtualKey)221:
                _ = GoChapterAsync(1);
                break;
            case VirtualKey.Number0 when ctrl:
                PagedScroll.ZoomTo(1, null, new ScrollingZoomOptions(ScrollingAnimationMode.Enabled));
                StripScroll.ZoomTo(1, null, new ScrollingZoomOptions(ScrollingAnimationMode.Enabled));
                break;
            default:
                return;
        }
        e.Handled = true;
    }

    public async Task CloseAsync(bool animate = true)
    {
        if (closing)
        {
            return;
        }
        closing = true;
        await FlushProgressAsync();
        var window = App.MainWindow;
        if (window.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen)
        {
            window.AppWindow.SetPresenter(AppWindowPresenterKind.Overlapped);
        }
        window.RestoreTitleBar();
        if (animate)
        {
            var layer = window.ReaderLayer;
            if (origin is not null && origin.XamlRoot is not null && !Motion.Reduced)
            {
                var rect = ContainerTransform.BoundsIn(origin, layer);
                if (ContainerTransform.IsOnScreen(rect, layer))
                {
                    await ContainerTransform.Collapse(this, rect, new Rect(0, 0, ActualWidth, ActualHeight), 18, Motion.FluentTimings ? Motion.Short4 : Motion.Medium4, [TopBar, BottomBar, PagedScroll, StripScroll], null, BeginTransition());
                    Finish();
                    return;
                }
            }
            var v = Motion.VisualOf(this);
            await Motion.Run(v.Compositor, _ => v.StartAnimation("Opacity", Motion.Scalar(v.Compositor, 1, 0, Motion.Short4, Curve.EmphasizedAccelerate)));
        }
        Finish();
    }

    private CancellationTokenSource? transition;

    private CancellationToken BeginTransition()
    {
        transition?.Cancel();
        return (transition = new CancellationTokenSource()).Token;
    }

    private void Finish()
    {
        ReleaseAll();
        var window = App.MainWindow;
        window.ReaderLayer.Children.Remove(this);
        if (window.ReaderLayer.Children.Count == 0)
        {
            window.ReaderLayer.Visibility = Visibility.Collapsed;
        }
        if (current == this)
        {
            current = null;
        }
    }

    private double StripWidth => Math.Min(Math.Max(Root.ActualWidth, 400), 900);

    private void ShowStrip()
    {
        PagedScroll.Visibility = Visibility.Collapsed;
        StripScroll.Visibility = Visibility.Visible;
        LoadingRing.IsActive = false;
        stripFactory.Width = StripWidth;
        Strip.Width = StripWidth;
        Strip.ItemsSource = Enumerable.Range(0, pages.Count + 1).ToList();
        var start = index;
        DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, () => ScrollStripTo(start));
        UpdatePageLabels(index, null);
    }

    private void ScrollStrip(double viewportFraction)
    {
        StripScroll.ScrollBy(0, StripScroll.ViewportHeight * viewportFraction, new ScrollingScrollOptions(ScrollingAnimationMode.Enabled));
    }

    private void ScrollStripTo(int page)
    {
        if (page <= 0)
        {
            StripScroll.ScrollTo(0, 0, new ScrollingScrollOptions(ScrollingAnimationMode.Disabled));
            return;
        }
        var element = Strip.GetOrCreateElement(Math.Clamp(page, 0, pages.Count));
        element.UpdateLayout();
        element.StartBringIntoView(new BringIntoViewOptions { VerticalAlignmentRatio = 0, AnimationDesired = false });
    }

    private void OnStripScrolled()
    {
        if (mode != ReaderMode.Webtoon || pages.Count == 0)
        {
            return;
        }
        var center = StripScroll.VerticalOffset / Math.Max(StripScroll.ZoomFactor, 0.01) + StripScroll.ViewportHeight / 2 / Math.Max(StripScroll.ZoomFactor, 0.01);
        var best = stripFactory.PageAt(center);
        if (best is { } page && page != index && page < pages.Count)
        {
            index = page;
            UpdatePageLabels(page, null);
            QueueProgress(page);
            Services.Engine?.Pages.Prefetch(pages.Skip(page + 1).Take(6).Select(p => p.ImageUrl));
            if (page >= pages.Count - 2)
            {
                PrefetchNextChapter();
            }
        }
    }

    internal async Task BindStripPageAsync(StripPageView view, int page)
    {
        if (page >= pages.Count)
        {
            view.ShowFooter(NeighbourChapter(1), () => _ = GoChapterAsync(1));
            return;
        }
        var version = chapterVersion;
        try
        {
            var result = await GetPageAsync(page, ImagePriority.Visible);
            if (version != chapterVersion || view.Page != page)
            {
                return;
            }
            view.SetPage(result.Page, result.Sources);
            Evict(k => Math.Abs(k - index) <= 6 || stripFactory.IsRealized(k));
        }
        catch (Exception)
        {
            if (view.Page == page)
            {
                view.ShowError(() => _ = BindStripPageAsync(view, page));
            }
        }
    }
}
