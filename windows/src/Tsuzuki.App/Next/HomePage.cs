using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Automation.Peers;
using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class HomePage : TzPage, ITzPage, ICoverHost
{
    private const int ShelfSize = 20;
    private const long MangaDexSource = 2499283573021220255;
    private static readonly TimeSpan PickUpAfter = TimeSpan.FromDays(14);

    private readonly StackPanel body;
    private readonly TextBlock greeting = ShellTheme.Text(string.Empty, "TitleLargeTextBlockStyle");
    private readonly TextBlock summary = ShellTheme.Text(string.Empty, "CaptionTextBlockStyle");
    private readonly HomeHero hero = new() { Visibility = Visibility.Collapsed };
    private readonly HomeShelf continueShelf;
    private readonly HomeShelf newShelf;
    private readonly HomeShelf addedShelf;
    private readonly HomeShelf pickShelf;
    private readonly FrameworkElement empty;
    private readonly DispatcherQueueTimer reloadTimer;
    private (int NewChapters, int InProgress)? counts;
    private bool shown;
    private bool dirty = true;
    private bool staleAfterReader;
    private bool entered;
    private int version;

    public HomePage()
    {
        AutomationProperties.SetName(this, "Home");
        greeting.Text = Greeting();
        AutomationProperties.SetHeadingLevel(greeting, AutomationHeadingLevel.Level1);
        summary.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");
        summary.Margin = new Thickness(0, 4, 0, 0);
        var header = new StackPanel();
        header.Children.Add(greeting);
        header.Children.Add(summary);

        continueShelf = new HomeShelf("Continue reading", "See all reading history", () => App.MainWindow.NavigateRootAsync("history"));
        newShelf = new HomeShelf("New chapters", "See all updates", () => App.MainWindow.NavigateRootAsync("updates"));
        addedShelf = new HomeShelf("Recently added", "See your whole library", () => App.MainWindow.NavigateRootAsync("library"));
        pickShelf = new HomeShelf("Pick up again", "See your whole library", () => App.MainWindow.NavigateRootAsync("library"));
        foreach (var shelf in (HomeShelf[])[continueShelf, newShelf, addedShelf, pickShelf])
        {
            shelf.Visibility = Visibility.Collapsed;
        }
        empty = BuildEmpty();
        empty.Visibility = Visibility.Collapsed;

        body = new StackPanel { Padding = new Thickness(36, 28, 36, 40), Spacing = 32 };
        body.Children.Add(header);
        body.Children.Add(hero);
        body.Children.Add(empty);
        body.Children.Add(continueShelf);
        body.Children.Add(newShelf);
        body.Children.Add(addedShelf);
        body.Children.Add(pickShelf);
        Content = new ScrollViewer
        {
            Content = body,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
        };

        reloadTimer = DispatcherQueue.CreateTimer();
        reloadTimer.Interval = TimeSpan.FromMilliseconds(400);
        reloadTimer.IsRepeating = false;
        reloadTimer.Tick += (_, _) => _ = Log.Guard("home", ReloadAsync);

        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += OnLibraryChanged;
        }
        AppHost.EngineReady += OnEngineReady;
        Ui.ReaderClosed += OnReaderClosed;
        App.MainWindow.SyncStatusChanged += (_, _) => UpdateSummary();
        UpdateSummary();
    }

    public void OnShown()
    {
        shown = true;
        greeting.Text = Greeting();
        UpdateSummary();
        if (dirty)
        {
            _ = Log.Guard("home", ReloadAsync);
        }
    }

    public void OnHidden() => shown = false;

    public IEnumerable<FrameworkElement> CoversOf(long mangaId)
    {
        foreach (var shelf in (HomeShelf[])[continueShelf, newShelf, addedShelf, pickShelf])
        {
            if (shelf.Visibility != Visibility.Visible)
            {
                continue;
            }
            foreach (var child in shelf.Row.Children)
            {
                var cover = child switch
                {
                    CoverTile { Data.MangaId: var id } tile when id == mangaId => tile.CoverElement,
                    HomeContinueCard { Entry.MangaId: var id } card when id == mangaId => card.CoverElement,
                    _ => null,
                };
                if (cover is not null)
                {
                    yield return cover;
                }
            }
        }
        if (hero.Current?.MangaId == mangaId && hero.Visibility == Visibility.Visible)
        {
            yield return hero.CoverElement;
        }
    }

    private void OnEngineReady(object? sender, EventArgs e) => Ui.Enqueue(DispatcherQueue, () =>
    {
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed -= OnLibraryChanged;
            engine.Library.Changed += OnLibraryChanged;
        }
        dirty = true;
        if (shown)
        {
            _ = Log.Guard("home", ReloadAsync);
        }
    }, "home-engine");

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e) => Ui.Enqueue(DispatcherQueue, () =>
    {
        if (e.IsProgressOnly && Ui.ReaderOpen)
        {
            staleAfterReader = true;
            return;
        }
        QueueReload();
    }, "home-changed");

    private void OnReaderClosed(object? sender, EventArgs e)
    {
        if (!staleAfterReader)
        {
            return;
        }
        staleAfterReader = false;
        QueueReload();
    }

    private void QueueReload()
    {
        dirty = true;
        if (!shown)
        {
            return;
        }
        reloadTimer.Stop();
        reloadTimer.Start();
    }

    private async Task ReloadAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var my = ++version;
        dirty = false;
        var library = engine.Library;
        var continueTask = library.GetContinueReadingAsync(ShelfSize);
        var updatesTask = library.GetUpdatesAsync(300);
        var entriesTask = library.GetLibraryAsync();
        await Task.WhenAll(continueTask, updatesTask, entriesTask);
        if (my != version)
        {
            return;
        }
        var continuing = continueTask.Result;
        var updates = updatesTask.Result;
        var entries = entriesTask.Result;
        var byId = entries.DistinctBy(e => e.MangaId).ToDictionary(e => e.MangaId);

        var fresh = updates
            .Where(u => !u.Read)
            .GroupBy(u => u.MangaId)
            .Select(g => (Latest: g.MaxBy(u => u.DateFetch)!, Count: g.Count()))
            .OrderByDescending(g => g.Latest.DateFetch)
            .ToList();
        var cutoff = DateTimeOffset.Now.Add(-PickUpAfter).ToUnixTimeMilliseconds();
        var added = entries.Where(e => e.DateAdded > 0).OrderByDescending(e => e.DateAdded).Take(ShelfSize).ToList();
        var pick = entries
            .Where(e => e.UnreadCount > 0 && e.LastReadAt > 0 && e.LastReadAt < cutoff)
            .OrderByDescending(e => e.LastReadAt)
            .Take(ShelfSize)
            .ToList();

        counts = (updates.Count(u => !u.Read), entries.Count(e => e.LastReadAt > 0 && e.UnreadCount > 0 && e.UnreadCount < e.ChapterCount));
        UpdateSummary();

        var isEmpty = entries.Count == 0 && continuing.Count == 0;
        empty.Visibility = isEmpty ? Visibility.Visible : Visibility.Collapsed;
        hero.Visibility = continuing.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        hero.SetEntries(continuing);

        SyncCards(continueShelf, continuing);
        SyncTiles(newShelf, [.. fresh.Take(ShelfSize).Select(f => NewTile(f.Latest, f.Count, byId.GetValueOrDefault(f.Latest.MangaId)))]);
        newShelf.SetCount(fresh.Count == 0 ? string.Empty : fresh.Count == 1 ? "1 series" : $"{fresh.Count:N0} series");
        SyncTiles(addedShelf, [.. added.Select(e => Tile(e, "Added " + Format.Relative(e.DateAdded)))]);
        SyncTiles(pickShelf, [.. pick.Select(e => Tile(e, $"{e.UnreadCount:N0} unread · read {Format.Relative(e.LastReadAt)}"))]);

        if (!entered)
        {
            entered = true;
            var i = 0;
            foreach (UIElement section in body.Children)
            {
                if (section.Visibility == Visibility.Visible)
                {
                    _ = Motion.FadeIn(section, Motion.Medium1, rise: 12, delay: TimeSpan.FromMilliseconds(40 * i++));
                }
            }
        }
    }

    private static TileData Tile(LibraryEntry e, string caption) =>
        new(e.MangaId, e.Title, e.ThumbnailUrl, caption, e.UnreadCount, !e.Readable, e.IsMerged, e, InLibrary: true, Source: e.Source);

    private static TileData NewTile(UpdateEntry latest, int count, LibraryEntry? entry)
    {
        var caption = $"+{count:N0} new · {Format.Relative(latest.DateFetch)}";
        return entry is null
            ? new(latest.MangaId, latest.Title, latest.ThumbnailUrl, caption, count, false, false, latest, InLibrary: true, Source: latest.Source)
            : new(entry.MangaId, entry.Title, entry.ThumbnailUrl, caption, count, !entry.Readable, entry.IsMerged, entry, InLibrary: true, Source: entry.Source);
    }

    private static void SyncTiles(HomeShelf shelf, IReadOnlyList<TileData> items)
    {
        var row = shelf.Row.Children;
        while (row.Count > items.Count)
        {
            row.RemoveAt(row.Count - 1);
        }
        for (var i = 0; i < items.Count; i++)
        {
            if (i >= row.Count)
            {
                var tile = new CoverTile { Width = 160 };
                tile.Clicked += (_, clicked) => _ = Log.Guard("home-tile", () => clicked.Data is { } data ? AppNav.OpenDetailsAsync(data.MangaId, (FrameworkElement?)clicked.CoverElement) : Task.CompletedTask);
                row.Add(tile);
            }
            ((CoverTile)row[i]).Data = items[i];
        }
        shelf.Visibility = items.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private static void SyncCards(HomeShelf shelf, IReadOnlyList<ContinueEntry> items)
    {
        var row = shelf.Row.Children;
        while (row.Count > items.Count)
        {
            row.RemoveAt(row.Count - 1);
        }
        for (var i = 0; i < items.Count; i++)
        {
            if (i >= row.Count)
            {
                row.Add(new HomeContinueCard());
            }
            ((HomeContinueCard)row[i]).Bind(items[i]);
        }
        shelf.Visibility = items.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void UpdateSummary()
    {
        List<string> parts = [];
        if (counts is { } c)
        {
            parts.Add(c.NewChapters switch
            {
                0 => "No new chapters",
                1 => "1 new chapter",
                _ => $"{c.NewChapters:N0} new chapters",
            });
            if (c.InProgress > 0)
            {
                parts.Add(c.InProgress == 1 ? "1 series in progress" : $"{c.InProgress:N0} series in progress");
            }
        }
        parts.Add(App.MainWindow.SyncStatus);
        summary.Text = string.Join(" · ", parts);
        AutomationProperties.SetName(summary, summary.Text);
    }

    private static string Greeting() => DateTime.Now.Hour switch
    {
        >= 5 and < 12 => "Good morning",
        >= 12 and < 18 => "Good afternoon",
        _ => "Good evening",
    };

    private static FrameworkElement BuildEmpty()
    {
        var art = new Grid { Width = 120, Height = 96, HorizontalAlignment = HorizontalAlignment.Center };
        var back = ShellTheme.Glyph("\uE8F1", 56);
        back.Foreground = ShellTheme.Brush("TextFillColorTertiaryBrush");
        back.HorizontalAlignment = HorizontalAlignment.Left;
        back.VerticalAlignment = VerticalAlignment.Top;
        back.Margin = new Thickness(8, 4, 0, 0);
        var front = ShellTheme.Glyph("\uE736", 64);
        front.Foreground = ShellTheme.Brush("AccentFillColorDefaultBrush");
        front.HorizontalAlignment = HorizontalAlignment.Right;
        front.VerticalAlignment = VerticalAlignment.Bottom;
        art.Children.Add(back);
        art.Children.Add(front);
        AutomationProperties.SetAccessibilityView(art, AccessibilityView.Raw);

        var title = ShellTheme.Text("Your shelves are empty", "SubtitleTextBlockStyle");
        title.HorizontalAlignment = HorizontalAlignment.Center;
        title.Margin = new Thickness(0, 20, 0, 0);
        var text = new TextBlock
        {
            Text = "Bring your library over from your phone, restore a backup, or find something new to read.",
            Style = ShellTheme.Style("BodyTextBlockStyle"),
            Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush"),
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            MaxWidth = 420,
            Margin = new Thickness(0, 6, 0, 20),
        };

        var pair = Action("\uE8EA", "Pair my phone", accent: true, () =>
        {
            DevicesPanel.Show();
            return Task.CompletedTask;
        });
        var import = Action("\uE8B5", "Import backup", accent: false, SettingsPage.RestoreAsync);
        var explore = Action("\uE774", "Explore MangaDex", accent: false, () => AppNav.OpenBrowseAsync(MangaDexSource));
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center };
        actions.Children.Add(pair);
        actions.Children.Add(import);
        actions.Children.Add(explore);

        var panel = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, Padding = new Thickness(40, 48, 40, 48) };
        panel.Children.Add(art);
        panel.Children.Add(title);
        panel.Children.Add(text);
        panel.Children.Add(actions);
        return new Border
        {
            CornerRadius = new CornerRadius(8),
            BorderThickness = new Thickness(1),
            Background = ShellTheme.Brush("CardBackgroundFillColorDefaultBrush"),
            BorderBrush = ShellTheme.Brush("CardStrokeColorDefaultBrush"),
            Child = panel,
        };
    }

    private static Button Action(string glyph, string label, bool accent, Func<Task> run)
    {
        var content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        content.Children.Add(ShellTheme.Glyph(glyph, 14));
        content.Children.Add(new TextBlock { Text = label });
        var button = new Button { Content = content, Padding = new Thickness(14, 6, 14, 7) };
        if (accent)
        {
            button.Style = ShellTheme.Style("AccentButtonStyle");
        }
        AutomationProperties.SetName(button, label);
        button.Click += (_, _) => _ = Log.Guard("home-empty", async () =>
        {
            if (!Services.Settings.OnboardingDone)
            {
                Services.Settings.OnboardingDone = true;
                Services.Settings.Save();
            }
            await run();
        });
        return button;
    }
}
