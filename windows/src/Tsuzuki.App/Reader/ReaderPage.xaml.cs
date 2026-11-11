using System.Numerics;
using System.Runtime.ExceptionServices;
using System.Runtime.InteropServices;
using Microsoft.UI.Input;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Reader;
using Tsuzuki.Core.Sources;
using Windows.Foundation;
using Windows.System;
using DispatcherQueueTimer = Microsoft.UI.Dispatching.DispatcherQueueTimer;
using DispatcherQueuePriority = Microsoft.UI.Dispatching.DispatcherQueuePriority;

namespace Tsuzuki.App.Reader;

public sealed partial class ReaderPage : UserControl
{
    private static ReaderPage? current;
    private static Task<ReaderProfiles>? profilesLoad;

    private readonly long mangaId;
    private readonly Dictionary<int, DecodedPage> decoded = [];
    private readonly Dictionary<int, Task<DecodedPage>> decoding = [];
    private readonly List<DecodedPage> retired = [];
    private readonly HashSet<long> prefetchedChapters = [];
    private readonly DispatcherQueueTimer chromeTimer;
    private readonly DispatcherQueueTimer saveTimer;
    private readonly DispatcherQueueTimer pillTimer;
    private readonly DispatcherQueueTimer autoTimer;
    private readonly Border autoMarker;
    private bool autoScrollActive;
    private double autoOriginY;
    private double autoPointerY;
    private readonly StripFactory stripFactory;
    private CancellationTokenSource generationCts = new();
    private MangaDetails? manga;
    private readonly Tsuzuki.Presentation.ReaderSessionViewModel session = new();
    private ChapterRow? chapter;
    private IReadOnlyList<PageRef> pages = [];
    private Task<IReadOnlyList<PageRef>?>? pageRefresh;
    private IReadOnlyList<PageRef>? pageRefreshFor;
    private ReaderSpreadPlanner? spreadPlanner;
    private (bool CoverAlone, bool Offset, int Count)? spreadKey;
    private readonly HashSet<int> landscapePages = [];
    private int index;
    private int loadVersion;
    private int settledLoad;
    private int generation;
    private bool chromeVisible = true;
    private bool pointerOverChrome;
    private double wheelAccumulator;
    private DateTime lastWheelTurn;
    private double stripWheelTarget;
    private DateTimeOffset lastStripWheelAt;
    private long lastTurnMs;
    private int pendingSaveIndex = -1;
    private long pendingSaveChapter = -1;
    private TransitionOrigin origin = TransitionOrigin.None;
    private ReaderLayout layout;
    private bool rightToLeft;
    private PageFit fit;
    private bool spreadOffset;
    private bool closing;
    private bool loadingChapter;
    private bool suppressSlider;
    private bool cursorHidden;
    private int pillVersion;
    private Action? messageRetry;

    private ReaderPage(long mangaId)
    {
        InitializeComponent();
        this.mangaId = mangaId;
        ApplyProfile(DefaultProfile());
        chromeTimer = DispatcherQueue.CreateTimer();
        chromeTimer.Interval = TimeSpan.FromMilliseconds(2400);
        chromeTimer.IsRepeating = false;
        chromeTimer.Tick += (_, _) =>
        {
            if (!DevHarness.Active && AutoHideEnabled && !pointerOverChrome && !ExplorerOpen && !ActiveSlider.FocusState.HasFlag(FocusState.Pointer))
            {
                SetChrome(false);
            }
        };
        saveTimer = DispatcherQueue.CreateTimer();
        saveTimer.Interval = TimeSpan.FromMilliseconds(600);
        saveTimer.IsRepeating = false;
        saveTimer.Tick += (_, _) => _ = Log.Guard("reader progress", FlushProgressAsync);
        pillTimer = DispatcherQueue.CreateTimer();
        pillTimer.Interval = TimeSpan.FromMilliseconds(1000);
        pillTimer.IsRepeating = false;
        pillTimer.Tick += (_, _) => _ = HidePillAsync();

        stripFactory = new StripFactory(this);
        Strip.ItemTemplate = stripFactory;

        autoTimer = DispatcherQueue.CreateTimer();
        autoTimer.Interval = TimeSpan.FromMilliseconds(16);
        autoTimer.Tick += (_, _) => AutoScrollTick();
        autoMarker = new Border
        {
            Width = 30,
            Height = 30,
            CornerRadius = new CornerRadius(15),
            Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(230, 0, 0, 0)),
            BorderBrush = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(140, 255, 255, 255)),
            BorderThickness = new Thickness(1),
            Opacity = 0.85,
            IsHitTestVisible = false,
            Child = new FontIcon { Glyph = "\uEC8F", FontSize = 14 },
        };
        ToolTipService.SetToolTip(autoMarker, "Auto-scroll: move the mouse above or below to scroll; middle-click or Esc to stop");
        Root.Children.Add(autoMarker);
        autoMarker.Visibility = Visibility.Collapsed;
        EndNext.Click += (_, _) => _ = GoChapterAsync(1);
        EndClose.Click += (_, _) => _ = CloseAsync();
        EndRefresh.Click += (_, _) => _ = Log.Guard("reader check chapters", CheckForNewChaptersAsync);
        MessageAction.Click += (_, _) => messageRetry?.Invoke();
        GoToInput.KeyDown += OnGoToKeyDown;
        GoToInput.LostFocus += (_, _) => HideGoTo(refocus: false);
        GoToInput.BeforeTextChanging += (_, e) => e.Cancel = e.NewText.Any(c => !char.IsAsciiDigit(c) && c != '/' && c != ' ');

        BuildNextChrome();
        foreach (var bar in new FrameworkElement[] { ChromeTop, ChromeBottom })
        {
            bar.PointerEntered += (_, _) => pointerOverChrome = true;
            bar.PointerExited += (_, _) => { pointerOverChrome = false; chromeTimer.Start(); };
        }
        Root.PointerMoved += OnPointerMoved;
        Root.Tapped += OnTapped;
        Root.DoubleTapped += OnDoubleTapped;
        PagedScroll.AddHandler(PointerWheelChangedEvent, new PointerEventHandler(OnPagedWheel), true);
        StripScroll.ViewChanged += (_, _) => OnStripScrolled();
        Root.SizeChanged += (_, _) => LayoutPaged();
        KeyDown += OnKeyDown;
        Root.PointerPressed += (_, e) =>
        {
            transition?.Cancel();
            var point = e.GetCurrentPoint(Root);
            if (point.Properties.IsMiddleButtonPressed && layout == ReaderLayout.Strip)
            {
                e.Handled = true;
                ToggleAutoScroll(point.Position);
            }
            else if (autoScrollActive)
            {
                StopAutoScroll();
            }
            if (point.Properties.IsXButton1Pressed)
            {
                e.Handled = true;
                _ = CloseAsync();
            }
        };
        StripScroll.AddHandler(PointerWheelChangedEvent, new PointerEventHandler(OnStripWheel), true);
        UpdateStripWheelOwnership();
    }

    public static Task CloseCurrentAsync() => current?.CloseAsync(animate: false) ?? Task.CompletedTask;

    public static async Task OpenAsync(long mangaId, long chapterId, int page, TransitionOrigin from)
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
        window.EnterReaderTitleBar(reader.ActiveDragArea);
        reader.Focus(FocusState.Programmatic);
        var reveal = reader.RevealAsync();
        var load = reader.InitializeAsync(chapterId, page);
        await Task.WhenAll(reveal, load);
        reader.ShowHintOnce();
        reader.chromeTimer.Start();
    }

    private static Task<ReaderProfiles> ProfilesAsync() =>
        profilesLoad ??= Task.Run(() => ReaderProfiles.Load(Path.Combine(DevHarness.ResolvePaths().Root, "reader-profiles.json")));

    private static ReaderProfile DefaultProfile() => new(
        LayoutOf(Services.Settings.ReaderMode),
        Services.Settings.RightToLeft,
        Services.Settings.ReaderFit switch
        {
            ReaderFit.Width => PageFit.Width,
            ReaderFit.Original => PageFit.Original,
            _ => PageFit.Screen,
        },
        SpreadOffset: false);

    private static ReaderLayout LayoutOf(ReaderMode mode) => mode switch
    {
        ReaderMode.Double => ReaderLayout.Double,
        ReaderMode.Webtoon => ReaderLayout.Strip,
        _ => ReaderLayout.Single,
    };

    private void ApplyProfile(ReaderProfile profile)
    {
        layout = profile.Layout;
        rightToLeft = profile.RightToLeft;
        fit = profile.Fit;
        spreadOffset = profile.SpreadOffset;
        spreadPlanner = null;
        UpdateStripWheelOwnership();
        ApplyNextState();
    }

    private void RememberProfile()
    {
        var profile = new ReaderProfile(layout, rightToLeft, fit, spreadOffset);
        var id = mangaId;
        _ = Log.Guard("reader profile", async () =>
        {
            var store = await ProfilesAsync();
            store.Set(id, profile);
            await Task.Run(store.Save);
        });
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
        HintText.Text = layout == ReaderLayout.Strip
            ? "Scroll to read · [ ] switch chapters · ? shows every shortcut"
            : "← → or click the page edges to turn · scroll wheel pages · ? shows every shortcut";
        HintBar.Visibility = Visibility.Visible;
        var visual = Motion.VisualOf(HintBar);
        var compositor = visual.Compositor;
        visual.StartAnimation("Opacity", Motion.Scalar(compositor, 0, 1, Motion.Medium2, Curve.StandardDecelerate));
        hintTimer = DispatcherQueue.CreateTimer();
        hintTimer.Interval = TimeSpan.FromSeconds(7);
        hintTimer.IsRepeating = false;
        hintTimer.Tick += (_, _) => _ = HideHintAsync();
        hintTimer.Start();
    }

    private async Task HideHintAsync()
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

    private async Task RevealAsync()
    {
        var layer = App.MainWindow.ReaderLayer;
        var plan = ContainerTransform.PlanOpen(origin, layer);
        if (plan.Transform && ActualWidth > 1 && ActualHeight > 1)
        {
            await ContainerTransform.Expand(this, ContainerTransform.ToRect(plan.Anchor), new Rect(0, 0, ActualWidth, ActualHeight), ContainerTransform.RadiusOf(origin.Element), RevealParts(), BeginTransition());
            return;
        }
        await ContainerTransform.Fade(this, opening: true, BeginTransition());
    }

    private HeroParts RevealParts() => new([], [ChromeTop, ChromeBottom, PagedScroll, StripScroll]);

    private async Task InitializeAsync(long chapterId, int page)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        ReaderProfiles store;
        try
        {
            store = await ProfilesAsync();
            manga = await engine.Library.GetMangaAsync(mangaId);
            var rows = await engine.Library.GetChaptersAsync(mangaId);
            session.SetChapters(rows, mangaId);
        }
        catch (Exception e)
        {
            Log.Error("reader setup", e);
            ShowMessage("Couldn't open this chapter", "Your library couldn't be read right now.", "Try again", () => _ = InitializeAsync(chapterId, page));
            return;
        }
        var saved = store.Get(mangaId);
        var profile = saved ?? DefaultProfile();
        if (saved is null && !Services.Settings.ReaderModeChosen && manga is not null
            && manga.Genres.Any(g => g.Equals("Long Strip", StringComparison.OrdinalIgnoreCase) || g.Equals("Web Comic", StringComparison.OrdinalIgnoreCase)))
        {
            profile = profile with { Layout = ReaderLayout.Strip };
        }
        if (DevHarness.Current is { Mode: { } forced, ReaderManga: { } harnessManga } && harnessManga == mangaId)
        {
            profile = profile with { Layout = LayoutOf(forced) };
        }
        ApplyProfile(profile);
        await LoadChapterAsync(chapterId, page);
    }

    private async Task LoadChapterAsync(long chapterId, int page)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        loadingChapter = true;
        Log.Trail($"reader chapter {mangaId}:{chapterId} page {page} {layout}");
        try
        {
            await FlushProgressAsync();
            Retire();
            landscapePages.Clear();
            await LoadChapterCoreAsync(engine, chapterId, page, generation, generationCts.Token);
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception e)
        {
            Log.Error("reader chapter", e);
            if (!closing)
            {
                ClearChapter();
                ShowMessage("Couldn't open this chapter", "Something went wrong while loading it.", "Try again", () => RetryChapter(chapterId, page));
            }
        }
        finally
        {
            loadingChapter = false;
        }
    }

    private void RetryChapter(long chapterId, int page)
    {
        if (!loadingChapter)
        {
            _ = LoadChapterAsync(chapterId, page);
        }
    }

    private async Task LoadChapterCoreAsync(Tsuzuki.Core.Engine engine, long chapterId, int page, int my, CancellationToken ct)
    {
        Message.Visibility = Visibility.Collapsed;
        EndCard.Visibility = Visibility.Collapsed;
        pages = [];
        _ = SpinWhileAsync(() => my == generation && loadingChapter);
        chapter = await engine.Library.GetChapterAsync(chapterId);
        session.Chapter = chapter;
        if (my != generation)
        {
            return;
        }
        if (chapter is null)
        {
            ClearChapter();
            ShowMessage("This chapter is gone", "The source no longer lists it.", null, null);
            return;
        }
        UpdateNextTitle();
        UpdateChapterButtons();
        IReadOnlyList<PageRef>? downloaded = null;
        try
        {
            downloaded = await engine.Downloads.OpenLocalAsync(chapter.OwnerMangaId, chapter.Url, ct);
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException)
        {
            Log.Error("reader local", e);
        }
        if (my != generation)
        {
            return;
        }
        var source = engine.Sources.Get(chapter.Source);
        if (source is null && downloaded is null)
        {
            ClearChapter();
            ShowMessage("This chapter reads on your phone", $"{engine.Sources.DisplayName(chapter.Source)} isn't available on Windows yet.", null, null);
            return;
        }
        IReadOnlyList<PageRef> list;
        try
        {
            list = downloaded ?? await source!.GetPageListAsync(chapter.Url, ct: ct);
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            if (my == generation)
            {
                ClearChapter();
                ShowMessage("Couldn't load this chapter", "Check your connection, then try again.", "Try again", () => RetryChapter(chapterId, page));
            }
            return;
        }
        if (my != generation)
        {
            return;
        }
        if (list.Count == 0)
        {
            ClearChapter();
            ShowMessage("No pages here", chapter.ExternalUrl is null ? "This chapter has no pages on the source." : "This chapter is hosted on the publisher's site.", null, null);
            return;
        }
        pages = list;
        ResetThumbnails();
        spreadPlanner = null;
        index = Math.Clamp(page, 0, pages.Count - 1);
        suppressSlider = true;
        ActiveSlider.Minimum = 1;
        ActiveSlider.Maximum = Math.Max(1, pages.Count);
        ActiveSlider.Value = index + 1;
        suppressSlider = false;
        engine.Pages.Prefetch(PageRequests(index, 3));
        if (layout == ReaderLayout.Strip)
        {
            stripFactory.Reset();
            ShowStrip();
        }
        else
        {
            Strip.ItemsSource = null;
            stripFactory.Reset();
            ShowPaged();
            await GoToPageAsync(index, animate: false, force: true);
        }
        QueueProgress(index);
    }

    private void ShowMessage(string title, string body, string? action, Action? retry)
    {
        LoadingRing.IsActive = false;
        MessageTitle.Text = title;
        MessageBody.Text = body;
        MessageAction.Content = action ?? string.Empty;
        MessageAction.Visibility = action is null ? Visibility.Collapsed : Visibility.Visible;
        messageRetry = retry;
        Message.Visibility = Visibility.Visible;
        SetChrome(true);
    }

    private static string ChapterTitle(ChapterRow c)
    {
        var number = c.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(c.ChapterNumber) : c.Name;
        return c.Scanlator is null ? number : $"{number} · {c.Scanlator}";
    }

    private async Task SpinWhileAsync(Func<bool> waiting)
    {
        await Task.Delay(140);
        if (waiting())
        {
            LoadingRing.IsActive = true;
        }
    }

    private int ViewportPixelHeight => (int)Math.Max(800, Root.ActualHeight * (XamlRoot?.RasterizationScale ?? 1));

    private int PagedDecodeHeight(PageFit forFit) =>
        forFit == PageFit.Screen ? Math.Max(1600, (int)Math.Min(3400, ViewportPixelHeight * 1.6)) : 4000;

    private Task<DecodedPage> GetPageAsync(int i, ImagePriority priority)
    {
        if (decoded.TryGetValue(i, out var ready))
        {
            return Task.FromResult(ready);
        }
        if (decoding.TryGetValue(i, out var pending))
        {
            return pending;
        }
        var task = LoadPageAsync(i, priority, generation, generationCts.Token);
        if (!task.IsCompleted)
        {
            decoding[i] = task;
        }
        return task;
    }

    private async Task<DecodedPage> LoadPageAsync(int i, ImagePriority priority, int version, CancellationToken ct)
    {
        try
        {
            var path = await FetchPageFileAsync(i, priority, version, ct);
            var scale = XamlRoot?.RasterizationScale ?? 1;
            DecodedPage page;
            try
            {
                page = layout == ReaderLayout.Strip
                    ? await PageDecoder.DecodeAsync(path, (int)Math.Min(2000, StripWidth * scale), int.MaxValue, ct)
                    : await PageDecoder.DecodeAsync(path, 4000, PagedDecodeHeight(fit), ct);
            }
            catch (Exception e) when (e is not OperationCanceledException && i < pages.Count && chapter is { } current)
            {
                Services.Engine?.Pages.Invalidate(ImageRequest.Page(current.Source, pages[i]));
                throw;
            }
            if (version != generation)
            {
                page.Dispose();
                throw new OperationCanceledException();
            }
            if (page.Landscape)
            {
                landscapePages.Add(i);
            }
            if (!decoded.TryAdd(i, page))
            {
                page.Dispose();
                return decoded[i];
            }
            return page;
        }
        finally
        {
            if (version == generation)
            {
                decoding.Remove(i);
            }
        }
    }

    private async Task<string> FetchPageFileAsync(int i, ImagePriority priority, int version, CancellationToken ct)
    {
        var engine = Services.Engine!;
        var list = pages;
        var sourceId = chapter!.Source;
        Exception failure;
        try
        {
            return await engine.Pages.GetFileAsync(ImageRequest.Page(sourceId, list[i]), priority, ct);
        }
        catch (Exception e) when (e is HttpRequestException || (e is TaskCanceledException && !ct.IsCancellationRequested))
        {
            failure = e;
        }
        var fresh = await RefreshedPagesAsync(list, version);
        if (fresh is null || i >= fresh.Count)
        {
            ExceptionDispatchInfo.Throw(failure);
        }
        return await engine.Pages.GetFileAsync(ImageRequest.Page(sourceId, fresh[i]), priority, ct);
    }

    private IEnumerable<ImageRequest> PageRequests(int from, int count)
    {
        var sourceId = chapter!.Source;
        return pages.Skip(from).Take(count).Select(p => ImageRequest.Page(sourceId, p)).ToList();
    }

    private Task<IReadOnlyList<PageRef>?> RefreshedPagesAsync(IReadOnlyList<PageRef> failed, int version)
    {
        if (version != generation)
        {
            return Task.FromResult<IReadOnlyList<PageRef>?>(null);
        }
        if (!ReferenceEquals(failed, pages))
        {
            return Task.FromResult<IReadOnlyList<PageRef>?>(pages);
        }
        if (pageRefresh is { IsCompleted: false } && ReferenceEquals(pageRefreshFor, failed))
        {
            return pageRefresh;
        }
        pageRefreshFor = failed;
        return pageRefresh = RefreshPagesAsync(version, generationCts.Token);
    }

    private async Task<IReadOnlyList<PageRef>?> RefreshPagesAsync(int version, CancellationToken ct)
    {
        if (chapter is not { } c || Services.Engine is not { } engine || engine.Downloads.IsDownloaded(c.OwnerMangaId, c.Url) || engine.Sources.Get(c.Source) is not { } source)
        {
            return null;
        }
        try
        {
            var list = await source.GetPageListAsync(c.Url, refresh: true, ct: ct);
            if (version != generation || list.Count != pages.Count)
            {
                return null;
            }
            pages = list;
            thumbnails.Load(list, c.Source);
            return list;
        }
        catch (Exception e) when (!ct.IsCancellationRequested)
        {
            Log.Error("reader page refresh", e);
            return null;
        }
        catch (OperationCanceledException)
        {
            return null;
        }
    }

    private void Evict(Func<int, bool> keep)
    {
        foreach (var key in decoded.Keys.Where(k => !keep(k)).ToList())
        {
            decoded[key].Dispose();
            decoded.Remove(key);
        }
    }

    private void Retire()
    {
        generation++;
        generationCts.Cancel();
        generationCts = new CancellationTokenSource();
        retired.AddRange(decoded.Values);
        decoded.Clear();
        decoding.Clear();
        pageRefresh = null;
        pageRefreshFor = null;
    }

    private void DisposeRetired()
    {
        foreach (var page in retired)
        {
            page.Dispose();
        }
        retired.Clear();
    }

    private void ReleaseAll()
    {
        Retire();
        ClearSpread();
        DisposeRetired();
        stripFactory.Reset();
        Strip.ItemsSource = null;
        pages = [];
        ResetThumbnails();
    }

    private void ClearSpread()
    {
        PageLeft.Source = null;
        PageRight.Source = null;
        PageLeft.Tag = null;
        PageRight.Tag = null;
        PageRight.Visibility = Visibility.Collapsed;
        Spine.Visibility = Visibility.Collapsed;
    }

    private void ClearChapter()
    {
        pages = [];
        ClearSpread();
        DisposeRetired();
        Strip.ItemsSource = null;
        stripFactory.Reset();
        ResetThumbnails();
    }

    private void ShowPaged()
    {
        StripScroll.Visibility = Visibility.Collapsed;
        PagedScroll.Visibility = Visibility.Visible;
    }

    private ReaderSpreadPlanner Planner()
    {
        var key = (CoverAlone: Services.Settings.FirstPageAlone, Offset: spreadOffset, Count: pages.Count);
        if (spreadPlanner is null || spreadKey != key)
        {
            spreadKey = key;
            spreadPlanner = new ReaderSpreadPlanner(key.Count, key.CoverAlone, key.Offset, landscapePages.Contains);
        }
        return spreadPlanner;
    }

    private (int First, int? Second) SpreadFor(int i) =>
        layout != ReaderLayout.Double || pages.Count < 2 ? (i, null) : Planner().SpreadFor(i);

    private async Task GoToPageAsync(int target, bool animate, bool force = false, int direction = 0)
    {
        if (pages.Count == 0 || layout == ReaderLayout.Strip)
        {
            return;
        }
        target = Math.Clamp(target, 0, pages.Count - 1);
        if (!force && target == index && PageLeft.Source is not null && Message.Visibility != Visibility.Visible)
        {
            return;
        }
        var my = ++loadVersion;
        var version = generation;
        var engine = Services.Engine!;
        _ = SpinWhileAsync(() => my == loadVersion && settledLoad != my);
        var (first, second) = SpreadFor(target);
        DecodedPage firstPage;
        DecodedPage? secondPage = null;
        try
        {
            for (var attempt = 0; ; attempt++)
            {
                firstPage = await GetPageAsync(first, ImagePriority.Visible);
                secondPage = second is { } s ? await GetPageAsync(s, ImagePriority.Visible) : null;
                var settled = SpreadFor(target);
                if (settled == (first, second) || attempt == 3)
                {
                    break;
                }
                (first, second) = settled;
            }
        }
        catch (Exception e)
        {
            if (my == loadVersion && version == generation)
            {
                settledLoad = my;
                Log.Error("reader page", e);
                ClearSpread();
                DisposeRetired();
                ShowMessage("Couldn't load this page", "Check your connection, then try again.", "Try again", () => _ = GoToPageAsync(target, animate: false, force: true));
            }
            return;
        }
        if (my != loadVersion || version != generation)
        {
            return;
        }
        settledLoad = my;
        LoadingRing.IsActive = false;
        Message.Visibility = Visibility.Collapsed;
        index = target;
        Log.Trail($"reader page {first}{(second is { } paired ? "+" + paired : string.Empty)} of {pages.Count} {layout} rtl={rightToLeft} offset={spreadOffset}");
        var (leftIndex, rightIndex) = ReaderSpreadPlanner.Arrange(first, second, rightToLeft);
        var left = leftIndex == first ? firstPage : secondPage!;
        var right = rightIndex is null ? null : rightIndex == first ? firstPage : secondPage;
        PageLeft.Source = left.Slices[0].Source;
        PageLeft.Tag = left;
        if (right is not null)
        {
            PageRight.Source = right.Slices[0].Source;
            PageRight.Tag = right;
            PageRight.Visibility = Visibility.Visible;
            Spine.Visibility = Visibility.Visible;
        }
        else
        {
            PageRight.Source = null;
            PageRight.Tag = null;
            PageRight.Visibility = Visibility.Collapsed;
            Spine.Visibility = Visibility.Collapsed;
        }
        DisposeRetired();
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
            var dx = direction == 0 ? 0 : (direction > 0 ^ rightToLeft ? 1f : -1f) * (float)(20 + 24 * pace);
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
        engine.Pages.Prefetch(PageRequests(shownLast + 1, 3));
        for (var ahead = shownLast + 1; ahead <= Math.Min(pages.Count - 1, shownLast + 2); ahead++)
        {
            _ = GetPageAsync(ahead, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
        if (first > 0)
        {
            _ = GetPageAsync(first - 1, ImagePriority.Prefetch).ContinueWith(t => _ = t.Exception, TaskScheduler.Default);
        }
        if (shownLast >= pages.Count - 3)
        {
            PrefetchNextChapter();
        }
    }

    private void LayoutPaged()
    {
        if (layout == ReaderLayout.Strip || PageLeft.Tag is not DecodedPage left)
        {
            return;
        }
        var vw = Math.Max(1, Root.ActualWidth);
        var vh = Math.Max(1, Root.ActualHeight);
        var right = PageRight.Visibility == Visibility.Visible ? PageRight.Tag as DecodedPage : null;
        var totalAspectWidth = 1 / left.Aspect + (right is null ? 0 : 1 / right.Aspect);
        var height = fit switch
        {
            PageFit.Width => vw / totalAspectWidth,
            PageFit.Original => left.SourceHeight / (XamlRoot?.RasterizationScale ?? 1),
            _ => Math.Min(vh, vw / totalAspectWidth),
        };
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
        ActiveSlider.Value = first + 1;
        UpdateThumbPosition(first, second);
    }

    private void UpdateChapterButtons()
    {
        var position = CurrentGroupIndex();
        nextPrev!.IsEnabled = position > 0;
        nextNext!.IsEnabled = position >= 0 && position < session.Ascending.Count - 1;
    }

    private int CurrentGroupIndex()
    {
        if (chapter is null)
        {
            return -1;
        }
        for (var i = 0; i < session.Ascending.Count; i++)
        {
            var g = session.Ascending[i];
            if (g.Primary.ChapterId == chapter.ChapterId || g.Alternates.Any(a => a.ChapterId == chapter.ChapterId))
            {
                return i;
            }
        }
        return -1;
    }

    private ChapterRow? NeighbourChapter(int delta) => session.Neighbour(delta);

    private void PrefetchNextChapter()
    {
        if (NeighbourChapter(1) is not { } next || Services.Engine is not { } engine || engine.Sources.Get(next.Source) is not { } source)
        {
            return;
        }
        if (engine.Downloads.IsDownloaded(next.OwnerMangaId, next.Url) || !prefetchedChapters.Add(next.ChapterId))
        {
            return;
        }
        _ = Task.Run(async () =>
        {
            try
            {
                var list = await source.GetPageListAsync(next.Url);
                engine.Pages.Prefetch(list.Take(2).Select(p => ImageRequest.Page(next.Source, p)));
            }
            catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
            {
            }
        });
    }

    private async Task GoChapterAsync(int delta)
    {
        if (loadingChapter)
        {
            return;
        }
        var target = NeighbourChapter(delta);
        if (target is null)
        {
            if (delta > 0)
            {
                ShowEndCard(null);
            }
            return;
        }
        loadingChapter = true;
        try
        {
            if (delta > 0 && chapter is not null)
            {
                await MarkCurrentReadAsync();
            }
            var fresh = Services.Engine is { } engine ? await engine.Library.GetChapterAsync(target.ChapterId) ?? target : target;
            await LoadChapterAsync(fresh.ChapterId, fresh.Read ? 0 : fresh.LastPageRead);
        }
        catch (Exception e)
        {
            Log.Error("reader chapter switch", e);
        }
        finally
        {
            loadingChapter = false;
        }
    }

    private async Task NextChapterAsync()
    {
        if (loadingChapter)
        {
            return;
        }
        if (layout != ReaderLayout.Strip && EndCard.Visibility != Visibility.Visible && pages.Count > 0)
        {
            var (first, second) = SpreadFor(index);
            if ((second ?? first) >= pages.Count - 1)
            {
                await NextAsync();
                return;
            }
        }
        await GoChapterAsync(1);
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
        ReportRead(engine, chapter);
    }

    private void ReportRead(Tsuzuki.Core.Engine engine, ChapterRow finished) =>
        _ = Log.Guard("tracking-read", () => engine.Tracking.ChapterReadAsync(mangaId, finished.ChapterNumber));

    private void ShowEndCard(ChapterRow? next)
    {
        next ??= NeighbourChapter(1);
        var finished = chapter is { } current ? ChapterLabel(current) : "Chapter";
        EndFinished.Text = $"{finished} finished";
        EndSeries.Text = manga?.Title ?? string.Empty;
        EndCover.SourceId = manga?.Source ?? 0;
        EndCover.Url = manga?.ThumbnailUrl;
        var position = CurrentGroupIndex();
        var total = session.Ascending.Count;
        var read = session.Ascending.Count(g => g.Read) + (position >= 0 && !session.Ascending[position].Read ? 1 : 0);
        EndProgress.Value = total == 0 ? 0 : Math.Min(1, read / (double)total);
        var left = Math.Max(0, total - read);
        EndProgressText.Text = total == 0 ? string.Empty : left == 0 ? $"All {total:N0} chapters read" : $"{read:N0} of {total:N0} chapters read · {left:N0} left";
        var forward = rightToLeft && layout != ReaderLayout.Strip ? "←" : "→";
        var back = rightToLeft && layout != ReaderLayout.Strip ? "→" : "←";
        if (next is null)
        {
            EndNextCard.Visibility = Visibility.Collapsed;
            EndNext.Visibility = Visibility.Collapsed;
            EndCaughtUp.Visibility = Visibility.Visible;
            EndRefresh.Visibility = Visibility.Visible;
            EndRefreshText.Text = "Check for new chapters";
            EndRefresh.IsEnabled = true;
            EndCaughtUpBody.Text = $"{finished} is the latest chapter here. New chapters show up in Updates as soon as they're out.";
            EndHint.Text = $"{back} back to the last page";
        }
        else
        {
            var label = ChapterLabel(next);
            EndNextCard.Visibility = Visibility.Visible;
            EndNext.Visibility = Visibility.Visible;
            EndCaughtUp.Visibility = Visibility.Collapsed;
            EndRefresh.Visibility = Visibility.Collapsed;
            EndTitle.Text = label;
            var name = next.Name.Trim();
            EndChapterName.Text = name;
            EndChapterName.Visibility = name.Length == 0 || name.Equals(label, StringComparison.OrdinalIgnoreCase) || name.StartsWith("Chapter ", StringComparison.OrdinalIgnoreCase) || name.StartsWith("Ch.", StringComparison.OrdinalIgnoreCase)
                ? Visibility.Collapsed
                : Visibility.Visible;
            var meta = new List<string>();
            if (!string.IsNullOrWhiteSpace(next.Scanlator))
            {
                meta.Add(next.Scanlator!);
            }
            if (next.DateUpload > 0)
            {
                meta.Add(Format.Relative(next.DateUpload));
            }
            if (next.PageCount is { } count and > 0)
            {
                meta.Add($"{count} pages");
            }
            if (next.LastPageRead > 0 && !next.Read)
            {
                meta.Add($"resume on page {next.LastPageRead + 1}");
            }
            EndBody.Text = string.Join(" · ", meta);
            EndNextText.Text = next.LastPageRead > 0 && !next.Read ? $"Resume {label}" : $"Read {label}";
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(EndNext, EndNextText.Text);
            EndHint.Text = $"{forward} or Space to continue · {back} back to the last page";
        }
        EndCard.Visibility = Visibility.Visible;
        _ = Motion.FadeIn(EndCard, Motion.Short4, 0);
        _ = Motion.FadeIn(EndColumn, Motion.Medium1, Motion.Reduced ? 0 : 16);
        (next is null ? EndClose : EndNext).Focus(FocusState.Programmatic);
        if (next is null)
        {
            _ = Log.Guard("reader mark read", MarkCurrentReadAsync);
        }
        else
        {
            PrefetchNextChapter();
        }
    }

    private static string ChapterLabel(ChapterRow c) =>
        c.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(c.ChapterNumber) : c.Name;

    private async Task CheckForNewChaptersAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        EndRefresh.IsEnabled = false;
        EndRefreshText.Text = "Checking…";
        try
        {
            await engine.Refresher.RefreshQueuedAsync(mangaId);
            var rows = await engine.Library.GetChaptersAsync(mangaId);
            session.SetChapters(rows, mangaId);
            if (EndCard.Visibility != Visibility.Visible)
            {
                return;
            }
            if (NeighbourChapter(1) is { } fresh)
            {
                ShowEndCard(fresh);
                return;
            }
            EndRefreshText.Text = "No new chapters yet";
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            Log.Error("reader check chapters", e);
            EndRefreshText.Text = "Couldn't check. Try again";
            EndRefresh.IsEnabled = true;
        }
    }

    private async Task NextAsync()
    {
        if (loadingChapter)
        {
            return;
        }
        var last = pages.Count == 0 ? 0 : SpreadFor(index) is var (first, second) ? second ?? first : 0;
        var step = session.Next(layout == ReaderLayout.Strip, EndCard.Visibility == Visibility.Visible, pages.Count, last);
        switch (step.Kind)
        {
            case Tsuzuki.Presentation.ReaderStepKind.Scroll:
                ScrollStrip(0.85);
                break;
            case Tsuzuki.Presentation.ReaderStepKind.NextChapter:
                await GoChapterAsync(1);
                break;
            case Tsuzuki.Presentation.ReaderStepKind.FinishChapter when EndCard.Visibility != Visibility.Visible:
                await MarkCurrentReadAsync();
                ShowEndCard(null);
                break;
            case Tsuzuki.Presentation.ReaderStepKind.Page:
                await GoToPageAsync(step.Page, animate: true, direction: 1);
                break;
        }
    }

    private async Task PreviousAsync()
    {
        if (loadingChapter)
        {
            return;
        }
        var firstShown = pages.Count == 0 ? 0 : SpreadFor(index).Item1;
        var step = session.Previous(layout == ReaderLayout.Strip, EndCard.Visibility == Visibility.Visible, pages.Count, firstShown);
        switch (step.Kind)
        {
            case Tsuzuki.Presentation.ReaderStepKind.Scroll:
                ScrollStrip(-0.85);
                break;
            case Tsuzuki.Presentation.ReaderStepKind.HideEndCard:
                EndCard.Visibility = Visibility.Collapsed;
                break;
            case Tsuzuki.Presentation.ReaderStepKind.PreviousChapter:
                await LoadChapterAsync(step.Chapter!.ChapterId, int.MaxValue);
                break;
            case Tsuzuki.Presentation.ReaderStepKind.Page:
                var target = step.Page;
                if (layout == ReaderLayout.Double)
                {
                    target = SpreadFor(target).Item1;
                }
                await GoToPageAsync(target, animate: true, direction: -1);
                break;
        }
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
        var current = chapter;
        var read = current?.ChapterId == chapterId && pages.Count > 0 && page >= pages.Count - 1;
        pendingSaveIndex = -1;
        pendingSaveChapter = -1;
        await engine.Library.SetChapterProgressAsync(chapterId, page, read);
        if (read && current is not null)
        {
            ReportRead(engine, current);
        }
    }

    private static string LayoutName(ReaderLayout value) => value switch
    {
        ReaderLayout.Single => "Single page",
        ReaderLayout.Double => "Two pages",
        _ => "Long strip",
    };

    private void SetLayout(ReaderLayout next)
    {
        if (loadingChapter)
        {
            return;
        }
        StopAutoScroll();
        Flash(LayoutName(next));
        if (next == layout)
        {
            return;
        }
        var keep = index;
        var fromStrip = layout == ReaderLayout.Strip;
        layout = next;
        stripWheelTarget = StripScroll.VerticalOffset;
        UpdateStripWheelOwnership();
        spreadPlanner = null;
        ApplyNextState();
        RememberProfile();
        if (pages.Count == 0)
        {
            return;
        }
        index = keep;
        if (layout == ReaderLayout.Strip)
        {
            Retire();
            ClearSpread();
            ShowStrip();
            return;
        }
        if (fromStrip)
        {
            Retire();
            Strip.ItemsSource = null;
            ShowPaged();
        }
        _ = GoToPageAsync(keep, animate: false, force: true);
    }

    private void ToggleOffset()
    {
        if (loadingChapter)
        {
            return;
        }
        if (layout != ReaderLayout.Double)
        {
            Flash("Offset works in two-page view (2)");
            return;
        }
        spreadOffset = !spreadOffset;
        spreadPlanner = null;
        ApplyNextState();
        RememberProfile();
        Flash(spreadOffset ? "Offset +1" : "Offset off");
        _ = GoToPageAsync(index, animate: false, force: true);
    }

    private void ToggleDirection()
    {
        if (loadingChapter || layout == ReaderLayout.Strip)
        {
            return;
        }
        rightToLeft = !rightToLeft;
        ApplyNextState();
        RememberProfile();
        Flash(rightToLeft ? "Right to left" : "Left to right");
        _ = GoToPageAsync(index, animate: false, force: true);
    }

    private void CycleFit()
    {
        if (loadingChapter || layout == ReaderLayout.Strip)
        {
            return;
        }
        SetFit(fit switch
        {
            PageFit.Screen => PageFit.Width,
            PageFit.Width => PageFit.Original,
            _ => PageFit.Screen,
        });
    }

    private void SetFit(PageFit next)
    {
        if (loadingChapter || layout == ReaderLayout.Strip || next == fit)
        {
            return;
        }
        var previous = fit;
        fit = next;
        RememberProfile();
        Flash(fit switch { PageFit.Screen => "Fit to screen", PageFit.Width => "Fit width", _ => "Original size" });
        PagedScroll.ZoomTo(1, null, new ScrollingZoomOptions(ScrollingAnimationMode.Disabled));
        if (PagedDecodeHeight(previous) != PagedDecodeHeight(fit) && pages.Count > 0)
        {
            Retire();
            _ = GoToPageAsync(index, animate: false, force: true);
            return;
        }
        LayoutPaged();
    }

    private void Flash(string text)
    {
        pillVersion++;
        PillText.Text = text;
        var covered = Message.Visibility == Visibility.Visible || EndCard.Visibility == Visibility.Visible;
        Pill.VerticalAlignment = covered ? VerticalAlignment.Top : VerticalAlignment.Center;
        Pill.Margin = covered ? new Thickness(0, 76, 0, 0) : new Thickness(0);
        var visual = Motion.VisualOf(Pill);
        visual.StopAnimation("Opacity");
        if (Pill.Visibility == Visibility.Visible || Motion.Reduced)
        {
            visual.Opacity = 1;
        }
        else
        {
            visual.StartAnimation("Opacity", Motion.Scalar(visual.Compositor, 0, 1, Motion.Short2, Curve.StandardDecelerate));
        }
        Pill.Visibility = Visibility.Visible;
        pillTimer.Stop();
        pillTimer.Start();
    }

    private async Task HidePillAsync()
    {
        var my = pillVersion;
        if (Pill.Visibility != Visibility.Visible)
        {
            return;
        }
        if (!Motion.Reduced)
        {
            var visual = Motion.VisualOf(Pill);
            var c = visual.Compositor;
            await Motion.Run(c, _ => visual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short3, Curve.StandardAccelerate)));
        }
        if (my == pillVersion)
        {
            Pill.Visibility = Visibility.Collapsed;
        }
    }

    private void ShowShortcutSheet()
    {
        ShortcutTitle.Text = layout == ReaderLayout.Strip ? "Shortcuts · long strip" : layout == ReaderLayout.Double ? "Shortcuts · two pages" : "Shortcuts · single page";
        ShortcutRows.Children.Clear();
        ShortcutRows.RowDefinitions.Clear();
        var row = 0;
        foreach (var (keys, action) in ShortcutList())
        {
            ShortcutRows.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var key = new TextBlock { Text = keys, Foreground = new SolidColorBrush(Microsoft.UI.Colors.White), FontSize = 13, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold };
            var label = new TextBlock { Text = action, Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(200, 255, 255, 255)), FontSize = 13, TextWrapping = TextWrapping.Wrap };
            Grid.SetRow(key, row);
            Grid.SetRow(label, row);
            Grid.SetColumn(label, 1);
            ShortcutRows.Children.Add(key);
            ShortcutRows.Children.Add(label);
            row++;
        }
        ShortcutLayer.Visibility = Visibility.Visible;
        if (!Motion.Reduced)
        {
            _ = Motion.FadeIn(ShortcutLayer, Motion.Short4);
        }
    }

    private void HideShortcutSheet() => ShortcutLayer.Visibility = Visibility.Collapsed;

    private List<(string Keys, string Action)> ShortcutList()
    {
        var list = new List<(string, string)>();
        if (layout == ReaderLayout.Strip)
        {
            list.Add(("Wheel · ↑ ↓", "Scroll"));
            list.Add(("Space · Shift+Space", "Scroll a screen down or up"));
            list.Add(("Ctrl+wheel", "Zoom"));
        }
        else
        {
            list.Add((rightToLeft ? "← · →" : "→ · ←", rightToLeft ? "Next · previous page (right to left)" : "Next · previous page"));
            list.Add(("Space · Shift+Space", "Next · previous page"));
            list.Add(("Wheel", "Turn pages"));
            list.Add(("Ctrl+wheel", "Zoom at the pointer"));
            list.Add(("Double-click", "Actual size · fit"));
        }
        list.Add(("Home · End", "First · last page"));
        list.Add(("G", "Go to page"));
        list.Add(("T", "Page thumbnails"));
        list.Add(("S", "Filmstrip on or off"));
        list.Add(("Middle-click", "Auto-scroll (long strip): move the mouse above or below the marker; middle-click or Esc stops"));
        list.Add(("[ · ]", "Previous · next chapter"));
        list.Add(("1 · 2 · 3", "Single page · two pages · long strip"));
        list.Add(("D", "Two pages on or off"));
        if (layout == ReaderLayout.Double)
        {
            list.Add(("O", spreadOffset ? "Pair pages normally" : "Shift page pairs by one"));
        }
        if (layout != ReaderLayout.Strip)
        {
            list.Add(("R", "Reading direction"));
            list.Add(("F", "Fit to screen · width · original"));
        }
        list.Add(("F11", "Full screen"));
        list.Add(("Esc", "Close thumbnails, reset zoom, leave full screen, close"));
        list.Add(("?", "Show or hide this list"));
        return list;
    }

    private void ShowGoTo()
    {
        if (pages.Count == 0 || loadingChapter)
        {
            return;
        }
        GoToTotal.Text = "of " + pages.Count;
        GoToInput.Text = string.Empty;
        GoToInput.PlaceholderText = (index + 1).ToString();
        GoToBox.Visibility = Visibility.Visible;
        DispatcherQueue.TryEnqueue(() => GoToInput.Focus(FocusState.Keyboard));
    }

    private void HideGoTo(bool refocus)
    {
        if (GoToBox.Visibility != Visibility.Visible)
        {
            return;
        }
        GoToBox.Visibility = Visibility.Collapsed;
        if (refocus)
        {
            Focus(FocusState.Programmatic);
        }
    }

    private void OnGoToKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Escape)
        {
            e.Handled = true;
            HideGoTo(refocus: true);
            return;
        }
        if (e.Key != VirtualKey.Enter)
        {
            return;
        }
        e.Handled = true;
        var target = GoToPage.Parse(GoToInput.Text, pages.Count);
        HideGoTo(refocus: true);
        if (target is not { } page)
        {
            return;
        }
        JumpToPage(page);
    }

    private void ToggleFullscreen()
    {
        var window = App.MainWindow.AppWindow;
        var full = window.Presenter.Kind == AppWindowPresenterKind.FullScreen;
        window.SetPresenter(full ? AppWindowPresenterKind.Overlapped : AppWindowPresenterKind.FullScreen);
        nextCaption.Width = new GridLength(full ? 140 : 0);
        nextFullIcon!.Glyph = full ? "\uE740" : "\uE73F";
    }

    private bool ResetZoom()
    {
        var scroll = layout == ReaderLayout.Strip ? StripScroll : PagedScroll;
        if (Math.Abs(scroll.ZoomFactor - 1) < 0.02f)
        {
            return false;
        }
        scroll.ZoomTo(1, null, new ScrollingZoomOptions(Motion.Reduced ? ScrollingAnimationMode.Disabled : ScrollingAnimationMode.Enabled));
        return true;
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
            else
            {
                SetCursorHidden(App.MainWindow.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen);
            }
            return;
        }
        chromeVisible = visible;
        SetCursorHidden(!visible && App.MainWindow.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen);
        if (Motion.FluentTimings && !Motion.Reduced)
        {
            Motion.Spring(ChromeTop, "Translation", new Vector3(0, visible ? 0 : -TopShift, 0), damping: 0.78f, periodMs: 55);
            Motion.SpringScalar(ChromeTop, "Opacity", visible ? 1 : 0);
            Motion.Spring(ChromeBottom, "Translation", new Vector3(0, visible ? 0 : BottomShift, 0), damping: 0.78f, periodMs: 55);
            Motion.SpringScalar(ChromeBottom, "Opacity", visible ? 1 : 0);
        }
        else
        {
            var top = Motion.VisualOf(ChromeTop);
            var bottom = Motion.VisualOf(ChromeBottom);
            var up = TopShift;
            var down = BottomShift;
            var c = top.Compositor;
            var duration = visible ? Motion.Short4 : TimeSpan.FromMilliseconds(167);
            var curve = visible ? Curve.EmphasizedDecelerate : Curve.EmphasizedAccelerate;
            top.StartAnimation("Translation", Motion.Vector3(c, new Vector3(0, visible ? -up : 0, 0), new Vector3(0, visible ? 0 : -up, 0), duration, curve));
            top.StartAnimation("Opacity", Motion.Scalar(c, visible ? 0 : 1, visible ? 1 : 0, duration, curve));
            bottom.StartAnimation("Translation", Motion.Vector3(c, new Vector3(0, visible ? down : 0, 0), new Vector3(0, visible ? 0 : down, 0), duration, curve));
            bottom.StartAnimation("Opacity", Motion.Scalar(c, visible ? 0 : 1, visible ? 1 : 0, duration, curve));
        }
        ChromeTop.IsHitTestVisible = visible;
        ChromeBottom.IsHitTestVisible = visible;
        if (visible)
        {
            chromeTimer.Start();
        }
    }

    private void SetCursorHidden(bool hidden)
    {
        if (hidden == cursorHidden)
        {
            return;
        }
        cursorHidden = hidden;
        ShowCursor(!hidden);
    }

    [LibraryImport("user32.dll")]
    private static partial int ShowCursor([MarshalAs(UnmanagedType.Bool)] bool show);

    private void OnPointerMoved(object sender, PointerRoutedEventArgs e)
    {
        var y = e.GetCurrentPoint(Root).Position.Y;
        autoPointerY = y;
        if (cursorHidden)
        {
            SetCursorHidden(false);
            chromeTimer.Stop();
            chromeTimer.Start();
        }
        if (y < 80 || y > Root.ActualHeight - BottomRevealZone)
        {
            SetChrome(true);
        }
    }

    private void OnTapped(object sender, TappedRoutedEventArgs e)
    {
        _ = HideHintAsync();
        if (autoScrollActive && !IsOverChrome(e.OriginalSource as DependencyObject))
        {
            StopAutoScroll();
        }
        var source = e.OriginalSource as DependencyObject;
        if (ShortcutLayer.Visibility == Visibility.Visible)
        {
            HideShortcutSheet();
            return;
        }
        if (IsOverChrome(source))
        {
            return;
        }
        var x = e.GetPosition(Root).X / Math.Max(1, Root.ActualWidth);
        if (layout == ReaderLayout.Strip || (x > 0.3 && x < 0.7))
        {
            SetChrome(!chromeVisible);
            return;
        }
        var forward = x >= 0.7 ^ rightToLeft;
        _ = forward ? NextAsync() : PreviousAsync();
    }
    private bool IsOverChrome(DependencyObject? source) =>
        IsInside(source, ChromeTop) || IsInside(source, ChromeBottom) || IsInside(source, EndCard) || IsInside(source, Message)
        || IsInside(source, GoToBox) || IsInside(source, ShortcutLayer)
        || (explorer is not null && IsInside(source, explorer));


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
        var source = e.OriginalSource as DependencyObject;
        if (layout == ReaderLayout.Strip || PageLeft.Tag is not DecodedPage left || IsOverChrome(source))
        {
            return;
        }
        if (PagedScroll.ZoomFactor > 1.02f)
        {
            PagedScroll.ZoomTo(1, null, new ScrollingZoomOptions(Motion.Reduced ? ScrollingAnimationMode.Disabled : ScrollingAnimationMode.Enabled));
            return;
        }
        var actual = left.SourceHeight / (XamlRoot?.RasterizationScale ?? 1) / Math.Max(1, PageLeft.ActualHeight);
        var target = (float)Math.Min(6, actual >= 1.25 ? actual : 2);
        var point = e.GetPosition(PagedScroll);
        PagedScroll.ZoomTo(target, new Vector2((float)point.X, (float)point.Y), new ScrollingZoomOptions(Motion.Reduced ? ScrollingAnimationMode.Disabled : ScrollingAnimationMode.Enabled));
    }

    private void OnPagedWheel(object sender, PointerRoutedEventArgs e)
    {
        if (layout == ReaderLayout.Strip || e.KeyModifiers.HasFlag(VirtualKeyModifiers.Control))
        {
            return;
        }
        var props = e.GetCurrentPoint(PagedScroll).Properties;
        var delta = props.MouseWheelDelta;
        if (delta == 0)
        {
            return;
        }
        double towardNext;
        if (props.IsHorizontalMouseWheel)
        {
            if (PagedScroll.ScrollableWidth > 1)
            {
                var atRight = PagedScroll.HorizontalOffset >= PagedScroll.ScrollableWidth - 1;
                var atLeft = PagedScroll.HorizontalOffset <= 1;
                if (!((delta > 0 && atRight) || (delta < 0 && atLeft)))
                {
                    wheelAccumulator = 0;
                    return;
                }
            }
            towardNext = rightToLeft ? -delta : delta;
        }
        else
        {
            if (PagedScroll.ScrollableHeight > 1 || PagedScroll.ZoomFactor > 1.02f)
            {
                var atBottom = PagedScroll.VerticalOffset >= PagedScroll.ScrollableHeight - 1;
                var atTop = PagedScroll.VerticalOffset <= 1;
                if (!((delta < 0 && atBottom) || (delta > 0 && atTop)))
                {
                    wheelAccumulator = 0;
                    return;
                }
            }
            towardNext = -delta;
        }
        e.Handled = true;
        if (Math.Sign(towardNext) != Math.Sign(wheelAccumulator))
        {
            wheelAccumulator = 0;
        }
        wheelAccumulator += towardNext;
        if ((DateTime.UtcNow - lastWheelTurn).TotalMilliseconds < 220 || Math.Abs(wheelAccumulator) < 100)
        {
            return;
        }
        var forward = wheelAccumulator > 0;
        wheelAccumulator = 0;
        lastWheelTurn = DateTime.UtcNow;
        _ = forward ? NextAsync() : PreviousAsync();
    }

    private void OnStripWheel(object sender, PointerRoutedEventArgs e)
    {
        var step = Services.Settings.ReaderWheelScroll;
        if (step <= 0 || layout != ReaderLayout.Strip)
        {
            return;
        }
        var point = e.GetCurrentPoint(StripScroll);
        var delta = point.Properties.MouseWheelDelta;
        if (delta == 0)
        {
            return;
        }
        if (e.KeyModifiers.HasFlag(VirtualKeyModifiers.Control))
        {
            e.Handled = true;
            var zoom = (float)Math.Clamp(StripScroll.ZoomFactor * Math.Pow(1.1, delta / 120.0), StripScroll.MinZoomFactor, StripScroll.MaxZoomFactor);
            StripScroll.ZoomTo(zoom, new Vector2((float)point.Position.X, (float)point.Position.Y), new ScrollingZoomOptions(Motion.Reduced ? ScrollingAnimationMode.Disabled : ScrollingAnimationMode.Enabled));
            stripWheelTarget = StripScroll.VerticalOffset;
            return;
        }
        var now = DateTimeOffset.UtcNow;
        if (now - lastStripWheelAt > TimeSpan.FromMilliseconds(400))
        {
            stripWheelTarget = StripScroll.VerticalOffset;
        }
        lastStripWheelAt = now;
        var (target, handled) = Tsuzuki.Presentation.StripWheel.Next(stripWheelTarget, step, delta, StripScroll.ViewportHeight, StripScroll.ScrollableHeight);
        if (!handled)
        {
            return;
        }
        e.Handled = true;
        stripWheelTarget = target;
        StripScroll.ScrollTo(0, target, new ScrollingScrollOptions(Motion.Reduced ? ScrollingAnimationMode.Disabled : ScrollingAnimationMode.Enabled));
    }

    private void UpdateStripWheelOwnership()
    {
        var own = Services.Settings.ReaderWheelScroll > 0 && layout == ReaderLayout.Strip;
        StripScroll.IgnoredInputKinds = own ? ScrollingInputKinds.MouseWheel : ScrollingInputKinds.None;
    }

    private void ToggleAutoScroll(Windows.Foundation.Point at)
    {
        if (autoScrollActive)
        {
            StopAutoScroll();
            return;
        }
        autoScrollActive = true;
        autoOriginY = at.Y;
        autoPointerY = at.Y;
        Canvas.SetZIndex(autoMarker, 200);
        autoMarker.RenderTransform = new TranslateTransform { X = at.X - 15, Y = at.Y - 15 };
        autoMarker.Visibility = Visibility.Visible;
        SetCursorHidden(false);
        autoTimer.Start();
        Flash("Auto-scroll");
    }

    private void StopAutoScroll()
    {
        if (!autoScrollActive)
        {
            return;
        }
        autoScrollActive = false;
        autoTimer.Stop();
        autoMarker.Visibility = Visibility.Collapsed;
    }

    private void AutoScrollTick()
    {
        var dy = autoPointerY - autoOriginY;
        var magnitude = Math.Max(0, Math.Abs(dy) - 12) / 12.0;
        if (magnitude == 0)
        {
            return;
        }
        var speed = Math.Min(4800, Math.Pow(magnitude, 1.35) * 300);
        _ = StripScroll.ScrollBy(0, Math.Sign(dy) * speed * 0.016);
    }

    private static bool IsModifier(VirtualKey key) => key is VirtualKey.Shift or VirtualKey.LeftShift or VirtualKey.RightShift
        or VirtualKey.Control or VirtualKey.LeftControl or VirtualKey.RightControl
        or VirtualKey.Menu or VirtualKey.LeftMenu or VirtualKey.RightMenu or VirtualKey.LeftWindows or VirtualKey.RightWindows;

    private void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.OriginalSource is TextBox || IsModifier(e.Key))
        {
            return;
        }
        _ = HideHintAsync();
        if (autoScrollActive && e.Key == VirtualKey.Escape)
        {
            StopAutoScroll();
            e.Handled = true;
            return;
        }
        var rtl = rightToLeft && layout != ReaderLayout.Strip;
        var ctrl = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        var shift = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Shift).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        var question = shift && (e.Key == (VirtualKey)191 || e.KeyStatus.ScanCode == 0x35);
        if (ShortcutLayer.Visibility == Visibility.Visible)
        {
            HideShortcutSheet();
            if (question || e.Key == VirtualKey.Escape)
            {
                e.Handled = true;
                return;
            }
        }
        if (question)
        {
            ShowShortcutSheet();
            e.Handled = true;
            return;
        }
        if (ExplorerOpen)
        {
            if (e.Key is VirtualKey.Escape or VirtualKey.T)
            {
                CloseExplorer();
                e.Handled = true;
            }
            return;
        }
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
            case VirtualKey.Down when layout == ReaderLayout.Strip:
                ScrollStrip(0.2);
                break;
            case VirtualKey.Up when layout == ReaderLayout.Strip:
                ScrollStrip(-0.2);
                break;
            case VirtualKey.Home when !loadingChapter && pages.Count > 0:
                if (layout == ReaderLayout.Strip) ScrollStripTo(0); else _ = GoToPageAsync(0, animate: true, direction: -1);
                break;
            case VirtualKey.End when !loadingChapter && pages.Count > 0:
                if (layout == ReaderLayout.Strip) ScrollStripTo(pages.Count - 1); else _ = GoToPageAsync(pages.Count - 1, animate: true, direction: 1);
                break;
            case VirtualKey.F11:
                ToggleFullscreen();
                break;
            case VirtualKey.Escape:
                if (!ResetZoom())
                {
                    if (App.MainWindow.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen)
                    {
                        ToggleFullscreen();
                    }
                    else
                    {
                        _ = CloseAsync();
                    }
                }
                break;
            case VirtualKey.Back:
                _ = CloseAsync();
                break;
            case VirtualKey.Number1 when !ctrl:
                SetLayout(ReaderLayout.Single);
                break;
            case VirtualKey.Number2 when !ctrl:
                SetLayout(ReaderLayout.Double);
                break;
            case VirtualKey.Number3 when !ctrl:
                SetLayout(ReaderLayout.Strip);
                break;
            case VirtualKey.D when !ctrl:
                SetLayout(layout == ReaderLayout.Double ? ReaderLayout.Single : ReaderLayout.Double);
                break;
            case VirtualKey.O when !ctrl:
                ToggleOffset();
                break;
            case VirtualKey.R when !ctrl:
                ToggleDirection();
                break;
            case VirtualKey.F when !ctrl:
                CycleFit();
                break;
            case VirtualKey.G when !ctrl:
                ShowGoTo();
                break;
            case VirtualKey.T when !ctrl:
                ToggleExplorer();
                break;
            case VirtualKey.S when !ctrl:
                ToggleFilmstrip();
                break;
            case (VirtualKey)219:
                _ = GoChapterAsync(-1);
                break;
            case (VirtualKey)221:
                _ = NextChapterAsync();
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
        StopAutoScroll();
        try
        {
            await FlushProgressAsync();
        }
        catch (Exception e)
        {
            Log.Error("reader progress", e);
        }
        var window = App.MainWindow;
        if (window.AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen)
        {
            window.AppWindow.SetPresenter(AppWindowPresenterKind.Overlapped);
        }
        window.RestoreTitleBar();
        if (animate)
        {
            var plan = ContainerTransform.PlanReturn([], origin, window.ReaderLayer);
            if (plan.Target != Tsuzuki.Presentation.ReturnTarget.Fade && ActualWidth > 1 && ActualHeight > 1)
            {
                await ContainerTransform.Collapse(this, ContainerTransform.ToRect(plan.Anchor), new Rect(0, 0, ActualWidth, ActualHeight), ContainerTransform.RadiusOf(origin.Element), RevealParts(), BeginTransition());
                Finish();
                return;
            }
            await ContainerTransform.Fade(this, opening: false, BeginTransition());
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
        chromeTimer.Stop();
        saveTimer.Stop();
        pillTimer.Stop();
        hintTimer?.Stop();
        ReleaseAll();
        SetCursorHidden(false);
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
        if (layout != ReaderLayout.Strip || Strip.ItemsSource is null)
        {
            return;
        }
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
        if (layout != ReaderLayout.Strip || pages.Count == 0)
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
            Services.Engine?.Pages.Prefetch(PageRequests(page + 1, 3));
            if (page >= pages.Count - 2)
            {
                PrefetchNextChapter();
            }
        }
    }

    internal async Task BindStripPageAsync(StripPageView view, int page)
    {
        if (pages.Count == 0)
        {
            return;
        }
        if (page >= pages.Count)
        {
            view.ShowFooter(NeighbourChapter(1), () => _ = GoChapterAsync(1));
            return;
        }
        var version = generation;
        try
        {
            var result = await GetPageAsync(page, ImagePriority.Visible);
            if (version != generation || view.Page != page)
            {
                return;
            }
            view.SetPage(result);
            DisposeRetired();
            Evict(k => Math.Abs(k - index) <= 6 || stripFactory.IsRealized(k));
        }
        catch (Exception e)
        {
            if (version == generation && view.Page == page)
            {
                Log.Error("reader strip page", e);
                view.ShowError(() => _ = BindStripPageAsync(view, page));
            }
        }
    }
}
