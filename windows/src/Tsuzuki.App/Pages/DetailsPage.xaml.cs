using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Windows.ApplicationModel.DataTransfer;
using Windows.Foundation;
using Windows.UI;

namespace Tsuzuki.App.Pages;

public interface ITileHost
{
    MangaTile? FindTile(long mangaId);
}

public sealed partial class DetailsPage : UserControl, ITzPage, ITransitionPage, ITransitionSkip
{
    private static readonly HashSet<long> RefreshedThisSession = [];

    private readonly long mangaId;
    private readonly MangaTile? fromTile;
    private readonly ChapterRowFactory rowFactory;
    private MangaDetails? manga;
    private IReadOnlyList<ChapterRow> chapters = [];
    private IReadOnlyList<ChapterGroup> groups = [];
    private ChapterGroup? next;
    private bool newestFirst;
    private bool descriptionExpanded;
    private bool stickyShown;
    private bool refreshing;
    private Color? ambientAccent;

    public DetailsPage(long mangaId, MangaTile? from)
    {
        PointerPressed += (_, _) => SkipTransition();
        InitializeComponent();
        A11y.NameFromTooltips(CategoryButton);
        this.mangaId = mangaId;
        fromTile = from;
        if (from?.Data is { } data)
        {
            TitleText.Text = data.Title;
            StickyTitle.Text = data.Title;
            SetCover(data.ThumbnailUrl, from?.Cover.Source);
        }
        rowFactory = new ChapterRowFactory(row => _ = OpenGroupAsync(row.Group!, row), ShowChapterMenu);
        ChapterList.ItemTemplate = rowFactory;
        ApplyAmbientFade();
        ActualThemeChanged += (_, _) => ApplyAmbientFade();
        Scroller.ViewChanged += (_, _) => UpdateSticky();
        ContinueButton.Click += (_, _) => _ = ContinueAsync(ContinueButton);
        StickyContinue.Click += (_, _) => _ = ContinueAsync(StickyContinue);
        LibraryButton.Click += async (_, _) => await ToggleLibraryAsync();
        UnreadOnly.Click += (_, _) => BindChapters(animate: false);
        SortChapters.Click += (_, _) =>
        {
            newestFirst = !newestFirst;
            SortText.Text = newestFirst ? "Newest first" : "Oldest first";
            BindChapters(animate: false);
        };
        MoreButton.Click += (_, _) =>
        {
            descriptionExpanded = !descriptionExpanded;
            Description.MaxLines = descriptionExpanded ? 0 : 4;
            MoreButton.Content = descriptionExpanded ? "Less" : "More";
        };
        CategoryButton.Click += async (_, _) => await ShowCategoriesAsync();
        App.MainWindow.SetTopBar(this, new TopBarState(null,
            [new TopBarAction(Icons.More, "More actions", anchor => LiquidMenu.Show(anchor, MoreMenuItems()))]));
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += OnLibraryChanged;
            Unloaded += (_, _) => engine.Library.Changed -= OnLibraryChanged;
        }
        if (fromTile is null)
        {
            BuildSkeleton();
            _ = Log.Guard("details", LoadAsync);
        }
    }

    public long MangaId => mangaId;

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private void SetCover(string? url, ImageSource? initialSource = null)
    {
        var empty = string.IsNullOrWhiteSpace(url);
        HeaderCover.DecodeWidth = 220;
        if (initialSource is not null)
        {
            HeaderCover.SetSource(initialSource, url);
            AmbientImage.SetSource(initialSource, url);
            StickyCover.SetSource(initialSource, url);
        }
        else
        {
            HeaderCover.Url = url;
            AmbientImage.Url = url;
            StickyCover.Url = url;
        }
        CoverGhost.Visibility = empty ? Visibility.Visible : Visibility.Collapsed;
        AmbientImage.Stretch = Stretch.UniformToFill;
    }

    private void ApplyAmbientFade()
    {
        var bg = Design.Theme.Color("TzBackground");
        var light = ActualTheme == ElementTheme.Light;
        if (light)
        {
            var wash = (ambientAccent ?? Design.Theme.Color("TzAccent")) with { A = 255 };
            AmbientFade.Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0, 0),
                EndPoint = new Windows.Foundation.Point(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Color.FromArgb(46, wash.R, wash.G, wash.B), Offset = 0 },
                    new GradientStop { Color = Color.FromArgb(14, wash.R, wash.G, wash.B), Offset = 0.3 },
                    new GradientStop { Color = Color.FromArgb(226, bg.R, bg.G, bg.B), Offset = 0.55 },
                    new GradientStop { Color = Color.FromArgb(255, bg.R, bg.G, bg.B), Offset = 1 },
                },
            };
        }
        else
        {
            AmbientFade.Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0, 0),
                EndPoint = new Windows.Foundation.Point(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Color.FromArgb(70, bg.R, bg.G, bg.B), Offset = 0 },
                    new GradientStop { Color = Color.FromArgb(170, bg.R, bg.G, bg.B), Offset = 0.55 },
                    new GradientStop { Color = Color.FromArgb(255, bg.R, bg.G, bg.B), Offset = 1 },
                },
            };
        }
        AmbientImage.Opacity = light ? 0.16 : 0.6;
        if (StickyBar.Background is AcrylicBrush acrylic)
        {
            var tint = Design.Theme.Color("TzCard");
            acrylic.TintColor = tint;
            acrylic.FallbackColor = tint;
        }
    }

    private void BuildSkeleton()
    {
        ChapterSkeleton.Children.Clear();
        List<UIElement> ghosts = [];
        for (var i = 0; i < 6; i++)
        {
            var ghost = new Border { Height = 52, CornerRadius = new CornerRadius(10), Background = Design.Theme.Brush("TzSkeleton"), Opacity = 1 - i * 0.14 };
            ChapterSkeleton.Children.Add(ghost);
            ghosts.Add(ghost);
        }
        ChapterSkeleton.Visibility = Visibility.Visible;
        Design.Skeleton.Pulse(ghosts);
    }

    private async Task LoadAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var (loadedManga, loadedChapters) = await Task.Run(async () =>
        {
            var m = await engine.Library.GetMangaAsync(mangaId);
            if (m is not null && string.IsNullOrWhiteSpace(m.ThumbnailUrl) && m.MergedRefs.Count > 0)
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
            var chs = m is not null ? await engine.Library.GetChaptersAsync(mangaId) : (IReadOnlyList<ChapterRow>)Array.Empty<ChapterRow>();
            return (m, chs);
        });
        manga = loadedManga;
        if (manga is null)
        {
            ChapterSkeleton.Visibility = Visibility.Collapsed;
            ChapterMessage.Text = "This title is no longer in your library.";
            ChapterMessage.Visibility = Visibility.Visible;
            return;
        }
        BindHeader();
        chapters = loadedChapters;
        BindChapters(animate: true);
        if (RefreshedThisSession.Add(mangaId) || chapters.Count == 0)
        {
            await RefreshAsync(includeDetails: string.IsNullOrEmpty(manga.Description) || !manga.Favorite);
        }
    }

    private async Task RefreshAsync(bool includeDetails)
    {
        if (Services.Engine is not { } engine || refreshing)
        {
            return;
        }
        refreshing = true;
        var previous = ChapterSub.Text;
        ChapterSub.Text = "Checking for new chapters…";
        try
        {
            var result = await engine.Refresher.RefreshAsync(mangaId, includeDetails);
            manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
            BindHeader();
            chapters = await engine.Library.GetChaptersAsync(mangaId);
            BindChapters(animate: chapters.Count > 0 && groups.Count == 0);
            if (result is null && chapters.Count == 0)
            {
                ChapterMessage.Text = manga is not null && !engine.Sources.All.Any(s => s.Id == manga.Source) && manga.Source != LibraryRefresher.MergedSourceId
                    ? $"{engine.Sources.DisplayName(manga!.Source)} reads on your phone. Your progress still syncs here."
                    : "No chapters yet.";
                ChapterMessage.Visibility = Visibility.Visible;
            }
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or InvalidOperationException)
        {
            ChapterSub.Text = previous;
            if (chapters.Count == 0)
            {
                ChapterSkeleton.Visibility = Visibility.Collapsed;
                ChapterMessage.Text = "Couldn't reach the source. Check your connection and try again.";
                ChapterMessage.Visibility = Visibility.Visible;
            }
        }
        finally
        {
            refreshing = false;
            UpdateChapterSub();
        }
    }

    private void BindHeader()
    {
        if (manga is null || Services.Engine is not { } engine)
        {
            return;
        }
        SourceLine.Text = manga.MergedRefs.Count > 0
            ? $"MERGED · {manga.MergedRefs.Count(r => !(r.Source == manga.Source && r.Url == manga.Url))} SOURCES"
            : engine.Sources.DisplayName(manga.Source).ToUpperInvariant();
        TitleText.Text = manga.Title;
        StickyTitle.Text = manga.Title;
        var people = new[] { manga.Author, manga.Artist }
            .Where(p => !string.IsNullOrWhiteSpace(p))
            .SelectMany(p => p!.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        AuthorText.Text = people.Count > 0 ? string.Join(" · ", people) : string.Empty;
        AuthorText.Visibility = people.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        if (HeaderCover.Url != manga.ThumbnailUrl)
        {
            SetCover(manga.ThumbnailUrl);
        }

        MetaRow.Children.Clear();
        MetaRow.Children.Add(Meta(StatusDot(manga.Status), StatusLabel(manga.Status)));
        if (manga.Source != LibraryRefresher.MergedSourceId && engine.Sources.Get(manga.Source) is null)
        {
            MetaRow.Children.Add(Meta(new FontIcon
            {
                Glyph = Icons.Smartphone,
                FontFamily = Res<FontFamily>("TzIconFont"),
                FontSize = 13,
                Foreground = Design.Theme.Brush("TzAccent"),
                VerticalAlignment = VerticalAlignment.Center,
            }, "Reads on your phone"));
        }
        if (manga.Genres.Count > 0)
        {
            Tags.Children.Clear();
            var labels = manga.Genres
                .Where(g => !g.StartsWith("Content Rating:", StringComparison.OrdinalIgnoreCase) && !g.StartsWith("Original Language:", StringComparison.OrdinalIgnoreCase))
                .Select(g => g.IndexOf(": ", StringComparison.Ordinal) is var i and >= 0 ? g[(i + 2)..] : g)
                .Distinct(StringComparer.OrdinalIgnoreCase);
            foreach (var genre in labels.Take(12))
            {
                Tags.Children.Add(new Border
                {
                    CornerRadius = new CornerRadius(8),
                    Padding = new Thickness(9, 4, 9, 5),
                    Background = Design.Theme.Brush("TzChipBackground"),
                    BorderBrush = Design.Theme.Brush("TzStrokeStrong"),
                    BorderThickness = new Thickness(1),
                    Child = new TextBlock { Text = genre, FontSize = 12, Foreground = Design.Theme.Brush("TzText") },
                });
            }
        }
        var description = (manga.Description ?? string.Empty).Trim();
        var lines = description.Split('\n');
        var noise = 0;
        while (noise < lines.Length
               && (lines[noise].StartsWith("Rating:", StringComparison.OrdinalIgnoreCase)
                   || lines[noise].StartsWith("Bookmarks:", StringComparison.OrdinalIgnoreCase)))
        {
            noise++;
        }
        description = string.Join('\n', lines.Skip(noise)).Trim();
        Description.Text = description;
        Description.Visibility = description.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
        MoreButton.Visibility = description.Length > 300 ? Visibility.Visible : Visibility.Collapsed;
        LibraryText.Text = manga.Favorite ? "In library" : "Add to library";
        LibraryIcon.Glyph = manga.Favorite ? Icons.Check : Icons.Plus;
        CategoryButton.Visibility = manga.Favorite ? Visibility.Visible : Visibility.Collapsed;
        _ = ApplyAccentAsync(manga.ThumbnailUrl);
    }

    private async Task ApplyAccentAsync(string? url)
    {
        var color = await CoverColors.AccentAsync(url);
        ambientAccent = color is { } c ? CoverColors.EnsureReadable(c) : null;
        if (ambientAccent is { } safe)
        {
            var brush = new SolidColorBrush(safe);
            ContinueButton.Background = brush;
            ContinueButton.Foreground = new SolidColorBrush(Microsoft.UI.Colors.White);
            StickyContinue.Background = brush;
            StickyContinue.Foreground = new SolidColorBrush(Microsoft.UI.Colors.White);
        }
        ApplyAmbientFade();
    }

    private static StackPanel Meta(UIElement lead, string text)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7 };
        panel.Children.Add(lead);
        panel.Children.Add(new TextBlock { Text = text, Style = Res<Style>("TzCaption"), FontSize = 13, VerticalAlignment = VerticalAlignment.Center });
        return panel;
    }

    private static UIElement StatusDot(MangaStatus status) => new Microsoft.UI.Xaml.Shapes.Ellipse
    {
        Width = 8,
        Height = 8,
        VerticalAlignment = VerticalAlignment.Center,
        Fill = Design.Theme.Brush(status switch
        {
            MangaStatus.Ongoing => "TzSuccess",
            MangaStatus.Completed or MangaStatus.PublishingFinished => "TzAccent",
            MangaStatus.Cancelled => "TzDanger",
            MangaStatus.OnHiatus => "TzWarning",
            _ => "TzTextTertiary",
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
        groups = ChapterGrouping.Group(chapters);
        next = ChapterGrouping.NextToRead(groups);
        IEnumerable<ChapterGroup> visible = groups;
        if (UnreadOnly.IsChecked == true)
        {
            visible = visible.Where(g => !g.Read && (g.Primary.Readable || g.Alternates.Any(a => a.Readable)));
        }
        if (!newestFirst)
        {
            visible = visible.Reverse();
        }
        var list = visible.ToList();
        ChapterList.ItemsSource = list;
        ChapterCount.Text = groups.Count == 1 ? "1 chapter" : $"{groups.Count} chapters";
        UpdateChapterSub();
        if (chapters.Count > 0)
        {
            ChapterSkeleton.Visibility = Visibility.Collapsed;
            ChapterMessage.Visibility = Visibility.Collapsed;
            if (animate)
            {
                _ = Motion.FadeIn(ChapterList, Motion.Medium2, 10);
            }
        }
        else if (!refreshing)
        {
            ChapterSkeleton.Visibility = Visibility.Collapsed;
        }
        UpdateContinue();
    }

    private void UpdateChapterSub()
    {
        if (refreshing)
        {
            return;
        }
        var unread = groups.Count(g => !g.Read);
        ChapterSub.Text = groups.Count == 0 ? string.Empty : unread == 0 ? "All read" : $"{unread} unread";
    }

    private void UpdateContinue()
    {
        string label;
        if (next is null)
        {
            label = "No chapters";
            ContinueButton.IsEnabled = false;
        }
        else
        {
            ContinueButton.IsEnabled = true;
            var number = next.Number >= 0 ? "Ch. " + Format.ChapterNumber(next.Number) : next.Primary.Name;
            var anyRead = groups.Any(g => g.LastReadAt > 0 || g.Read);
            if (next.InProgress)
            {
                var page = Math.Max(next.Primary.LastPageRead, next.Alternates.Select(a => a.LastPageRead).DefaultIfEmpty(0).Max());
                label = $"Continue · {number} · p. {page + 1}";
            }
            else if (groups.All(g => g.Read))
            {
                label = "Read again · " + number;
            }
            else
            {
                label = anyRead ? $"Continue · {number}" : $"Start · {number}";
            }
        }
        ContinueText.Text = label;
        StickyContinueText.Text = next?.InProgress == true || groups.Any(g => g.Read) ? "Continue" : "Start reading";
    }

    private async Task ContinueAsync(FrameworkElement from)
    {
        if (next is not null)
        {
            await OpenGroupAsync(next, from);
        }
    }

    private async Task OpenGroupAsync(ChapterGroup group, FrameworkElement from)
    {
        var copy = group.Primary.Readable && group.Primary.ExternalUrl is null
            ? group.Primary
            : group.Alternates.FirstOrDefault(a => a.Readable && a.ExternalUrl is null);
        if (copy is null)
        {
            if (group.Primary.ExternalUrl is { } external && Uri.TryCreate(external, UriKind.Absolute, out var uri))
            {
                await Windows.System.Launcher.LaunchUriAsync(uri);
                return;
            }
            Toasts.Show("Read this one on your phone", "Its source isn't available on Windows yet. Your progress still syncs.", Icons.Smartphone);
            return;
        }
        var page = group.Read ? 0 : Math.Max(copy.LastPageRead, 0);
        await AppNav.OpenReaderAsync(mangaId, copy.ChapterId, page, from);
    }

    private void ShowChapterMenu(ChapterRowView row)
    {
        var group = row.Group!;
        var menu = new MenuFlyout();
        var toggle = new MenuFlyoutItem { Text = group.Read ? "Mark as unread" : "Mark as read", Icon = new FontIcon { Glyph = group.Read ? Icons.EyeOff : Icons.Check, FontFamily = Res<FontFamily>("TzIconFont") } };
        toggle.Click += async (_, _) => await MarkAsync([group], !group.Read);
        menu.Items.Add(toggle);
        var previous = new MenuFlyoutItem { Text = "Mark previous as read", Icon = new FontIcon { Glyph = Icons.CheckCheck, FontFamily = Res<FontFamily>("TzIconFont") } };
        previous.Click += async (_, _) => await MarkAsync(groups.Where(g => g.Number >= 0 && g.Number < group.Number).ToList(), true);
        menu.Items.Add(previous);
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
            var web = new MenuFlyoutItem { Text = "Open on MangaDex", Icon = new FontIcon { Glyph = Icons.ExternalLink, FontFamily = Res<FontFamily>("TzIconFont") } };
            web.Click += async (_, _) => await Windows.System.Launcher.LaunchUriAsync(new Uri("https://mangadex.org" + group.Primary.Url));
            menu.Items.Add(web);
        }
        menu.ShowAt(row);
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

    private async Task ToggleLibraryAsync()
    {
        if (Services.Engine is not { } engine || manga is null)
        {
            return;
        }
        var favorite = !manga.Favorite;
        await engine.Library.SetFavoriteAsync(mangaId, favorite);
        manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
        BindHeader();
        Toasts.Show(favorite ? "Added to your library" : "Removed from your library", favorite ? "It will sync to your phone." : null, favorite ? Icons.Library : Icons.Close);
    }

    private async Task ShowCategoriesAsync()
    {
        if (Services.Engine is not { } engine || manga is null)
        {
            return;
        }
        var categories = await engine.Library.GetCategoriesAsync();
        var menu = new MenuFlyout { Placement = FlyoutPlacementMode.Bottom };
        var selected = manga.CategoryUids.ToHashSet();
        foreach (var category in categories.OrderBy(c => c.Order))
        {
            var item = new ToggleMenuFlyoutItem { Text = category.Name, IsChecked = selected.Contains(category.Uid) };
            item.Click += async (_, _) =>
            {
                if (item.IsChecked)
                {
                    selected.Add(category.Uid);
                }
                else
                {
                    selected.Remove(category.Uid);
                }
                await engine.Library.SetMangaCategoriesAsync(mangaId, selected.ToList());
                manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
            };
            menu.Items.Add(item);
        }
        if (categories.Count > 0)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
        }
        var create = new MenuFlyoutItem { Text = "New category…", Icon = new FontIcon { Glyph = Icons.Plus, FontFamily = Res<FontFamily>("TzIconFont") } };
        create.Click += async (_, _) =>
        {
            var name = await Dialogs.PromptAsync("New category", "Name", "Create");
            if (!string.IsNullOrWhiteSpace(name))
            {
                var category = await engine.Library.CreateCategoryAsync(name.Trim());
                selected.Add(category.Uid);
                await engine.Library.SetMangaCategoriesAsync(mangaId, selected.ToList());
                manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
            }
        };
        menu.Items.Add(create);
        menu.ShowAt(CategoryButton);
    }

    private List<LiquidMenuItem> MoreMenuItems()
    {
        List<LiquidMenuItem> items =
        [
            new(Icons.Refresh, "Refresh from source", () => _ = RefreshAsync(includeDetails: true)),
            new(Icons.CheckCheck, "Mark all as read", () => _ = MarkAsync(groups, true)),
            new(Icons.EyeOff, "Mark all as unread", () => _ = MarkAsync(groups, false)),
        ];
        if (WebUrl() is not null)
        {
            items.Add(new LiquidMenuItem(Icons.ExternalLink, "Open on MangaDex", async () =>
            {
                if (WebUrl() is { } url)
                {
                    await Windows.System.Launcher.LaunchUriAsync(url);
                }
            }));
        }
        items.Add(new LiquidMenuItem(Icons.Copy, "Copy title", () =>
        {
            var package = new DataPackage();
            package.SetText(manga?.Title ?? TitleText.Text);
            Clipboard.SetContent(package);
        }));
        return items;
    }

    private Uri? WebUrl()
    {
        if (manga is null)
        {
            return null;
        }
        var mdUrl = manga.Url.StartsWith("/manga/", StringComparison.Ordinal) ? manga.Url
            : manga.MergedRefs.FirstOrDefault(r => r.Url.StartsWith("/manga/", StringComparison.Ordinal))?.Url;
        return mdUrl is null ? null : new Uri("https://mangadex.org/title/" + mdUrl["/manga/".Length..]);
    }

    private void UpdateSticky()
    {
        var threshold = Header.ActualHeight + Body.Padding.Top - 40;
        var show = Scroller.VerticalOffset > threshold;
        if (show == stickyShown)
        {
            return;
        }
        stickyShown = show;
        StickyBar.IsHitTestVisible = show;
        var v = Motion.VisualOf(StickyBar);
        var c = v.Compositor;
        if (Motion.FluentTimings && !Motion.Reduced)
        {
            Motion.Spring(StickyBar, "Translation", new(0, show ? 0 : -8, 0), damping: 0.78f, periodMs: 55);
            Motion.SpringScalar(StickyBar, "Opacity", show ? 1 : 0);
        }
        else
        {
            v.StartAnimation("Opacity", Motion.Scalar(c, show ? 0 : 1, show ? 1 : 0, Motion.Short4, show ? Curve.StandardDecelerate : Curve.StandardAccelerate));
            if (!Motion.Reduced)
            {
                v.StartAnimation("Translation", Motion.Vector3(c, new(0, show ? -8 : 0, 0), new(0, show ? 0 : -8, 0), Motion.Short4, Curve.StandardDecelerate));
            }
        }
        StickyBar.Opacity = 1;
    }

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e)
    {
        if (!e.MangaIds.Contains(mangaId) && !(manga?.MergedRefs.Count > 0))
        {
            return;
        }
        DispatcherQueue.TryEnqueue(async () =>
        {
            if (Services.Engine is not { } engine)
            {
                return;
            }
            manga = await engine.Library.GetMangaAsync(mangaId) ?? manga;
            chapters = await engine.Library.GetChaptersAsync(mangaId);
            BindHeader();
            BindChapters(animate: false);
        });
    }

    public async Task EnterAsync(FrameworkElement? below)
    {
        var host = App.MainWindow.Nav.Host;
        if (fromTile is not null && below is not null && fromTile.XamlRoot is not null
            && ContainerTransform.IsOnScreen(ContainerTransform.BoundsIn(fromTile.CoverElement, host), host))
        {
            var pageVisual = Motion.VisualOf(this);
            pageVisual.Opacity = 0;
            BuildSkeleton();
            await SettleLayoutAndRenderAsync(120);
            var from = ContainerTransform.BoundsIn(fromTile.CoverElement, host);
            var cover = ContainerTransform.BoundsIn(CoverFrame, this);
            if (cover.Width > 1 && cover.Height > 1)
            {
                var duration = Motion.Reduced ? Motion.Short3 : Motion.HeroOpen;
                var token = BeginTransition();
                fromTile.CoverElement.Opacity = 0;
                pageVisual.Opacity = 1;
                var flight = ContainerTransform.Expand(this, from, cover, 10, duration, [Info, ChapterHeader, ChapterList, ChapterSkeleton, Ambient], below, token);
                _ = Log.Guard("details", LoadAsync);
                await flight;
                fromTile.CoverElement.Opacity = 1;
                ResetBelow(below);
                RefreshBorrowedCovers();
                return;
            }
            pageVisual.Opacity = 1;
            _ = Log.Guard("details", LoadAsync);
            await Motion.SharedAxisX(below, this, forward: true);
            RefreshBorrowedCovers();
            return;
        }
        BuildSkeleton();
        _ = Log.Guard("details", LoadAsync);
        await Motion.SharedAxisX(below, this, forward: true);
        RefreshBorrowedCovers();
    }

    private void RefreshBorrowedCovers()
    {
        HeaderCover.RefreshQuietly();
        AmbientImage.RefreshQuietly();
        StickyCover.RefreshQuietly();
    }

    private async Task SettleLayoutAndRenderAsync(int maxMs)
    {
        UpdateLayout();
        if (CoverFrame.ActualWidth > 1 && CoverFrame.ActualHeight > 1 && IsLoaded)
        {
            await WaitForNextRenderAsync(maxMs);
            return;
        }
        var tcs = new TaskCompletionSource();
        using var cts = new CancellationTokenSource(maxMs);
        cts.Token.Register(() => tcs.TrySetResult());
        RoutedEventHandler? loadedHandler = null;
        EventHandler<object>? layoutHandler = null;
        void Cleanup()
        {
            if (loadedHandler is not null)
            {
                Loaded -= loadedHandler;
            }
            if (layoutHandler is not null)
            {
                LayoutUpdated -= layoutHandler;
            }
        }
        void CheckSettle()
        {
            if (CoverFrame.ActualWidth > 1 && CoverFrame.ActualHeight > 1)
            {
                Cleanup();
                tcs.TrySetResult();
            }
        }
        loadedHandler = (_, _) => CheckSettle();
        layoutHandler = (_, _) => CheckSettle();
        Loaded += loadedHandler;
        LayoutUpdated += layoutHandler;
        UpdateLayout();
        CheckSettle();
        await tcs.Task;
        Cleanup();
        await WaitForNextRenderAsync(maxMs);
    }

    private static Task WaitForNextRenderAsync(int maxMs)
    {
        var tcs = new TaskCompletionSource();
        using var cts = new CancellationTokenSource(maxMs);
        cts.Token.Register(() => tcs.TrySetResult());
        EventHandler<object>? renderHandler = null;
        renderHandler = (_, _) =>
        {
            CompositionTarget.Rendering -= renderHandler;
            tcs.TrySetResult();
        };
        CompositionTarget.Rendering += renderHandler;
        return tcs.Task;
    }

    public async Task ExitAsync(FrameworkElement? below)
    {
        var host = App.MainWindow.Nav.Host;
        var tile = (below as ITileHost)?.FindTile(mangaId);
        below?.UpdateLayout();
        if (tile is not null && below is not null)
        {
            var to = ContainerTransform.BoundsIn(tile.CoverElement, host);
            if (!ContainerTransform.IsOnScreen(to, host))
            {
                ItemsTree.FindAncestor<GridViewItem>(tile)?.StartBringIntoView();
                below.UpdateLayout();
                to = ContainerTransform.BoundsIn(tile.CoverElement, host);
            }
            var cover = ContainerTransform.BoundsIn(CoverFrame, this);
            if (ContainerTransform.IsOnScreen(to, host) && cover.Width > 1 && cover.Height > 1)
            {
                var duration = Motion.Reduced ? Motion.Short3 : Motion.HeroBack;
                var token = BeginTransition();
                tile.CoverElement.Opacity = 0;
                await ContainerTransform.Collapse(this, to, cover, 10, duration, [Info, ChapterHeader, ChapterList, ChapterSkeleton, Ambient, StickyBar], below, token);
                tile.CoverElement.Opacity = 1;
                ResetBelow(below);
                return;
            }
        }
        if (below is not null)
        {
            ResetBelow(below);
            await Motion.SharedAxisX(this, below, forward: false);
        }
    }

    private CancellationTokenSource? transition;

    private CancellationToken BeginTransition()
    {
        transition?.Cancel();
        return (transition = new CancellationTokenSource()).Token;
    }

    public void SkipTransition()
    {
        transition?.Cancel();
    }

    private static void ResetBelow(FrameworkElement below)
    {
        var v = Motion.VisualOf(below);
        v.StopAnimation("Scale");
        v.StopAnimation("Opacity");
        v.Scale = System.Numerics.Vector3.One;
        v.Opacity = 1;
    }
}
