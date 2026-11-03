using System.Collections.ObjectModel;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media.Animation;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Pages;

public enum ListKind
{
    Updates,
    History,
}

public sealed partial class ListPage : UserControl, ITzPage
{
    private static readonly DataTemplate RowTemplate = (DataTemplate)Application.Current.Resources["TzMediaRowTemplate"];

    private static readonly DataTemplate HeaderTemplate = (DataTemplate)Application.Current.Resources["TzSectionHeaderTemplate"];

    private readonly ListKind kind;
    private readonly ObservableCollection<object> rows = [];
    private readonly Dictionary<DependencyObject, MediaRow> rowByContainer = [];
    private readonly HashSet<MediaRow> realized = [];
    private readonly HashSet<long> expandingGroups = [];
    private readonly Dictionary<long, List<MediaRow>> pendingGrow = [];
    private readonly HashSet<long> busyGroups = [];
    private readonly DispatcherQueueTimer refreshTimer;
    private long nextGroupId;
    private bool dirty = true;
    private bool shown;
    private bool firstLoad = true;

    private const double RowHeight = 76;

    private sealed record HistoryGroup(long Id, IReadOnlyList<HistoryEntry> Entries, string Tip);

    public ListPage(ListKind kind)
    {
        InitializeComponent();
        this.kind = kind;
        Kicker.Text = kind == ListKind.Updates ? "UPDATES · 新着" : "HISTORY · 履歴";
        Heading.Text = kind == ListKind.Updates ? "Updates" : "History";
        EmptyGlyph.Text = kind == ListKind.Updates ? "新" : "履";
        EmptyTitle.Text = kind == ListKind.Updates ? "No new chapters yet" : "Nothing read yet";
        EmptyBody.Text = kind == ListKind.Updates
            ? "New chapters for titles in your library show up here after a check."
            : "Chapters you read here or on your phone show up here.";
        List.ItemTemplateSelector = new MediaTemplateSelector(RowTemplate, HeaderTemplate);
        List.ItemContainerTransitions = ListMotion.ItemTransitions();
        List.ItemsSource = rows;
        List.ContainerContentChanging += OnContainerChanging;
        refreshTimer = DispatcherQueue.CreateTimer();
        refreshTimer.Interval = TimeSpan.FromMilliseconds(300);
        refreshTimer.IsRepeating = false;
        refreshTimer.Tick += (_, _) => _ = Log.Guard("list", LoadAsync);
        App.MainWindow.SetTopBar(this, new TopBarState(null,
            [new TopBarAction(Icons.Refresh, "Check for updates", anchor => _ = CheckNowAsync(anchor))]));
        if (Services.Engine is { } engine)
        {
            engine.Library.Changed += (_, _) => DispatcherQueue.TryEnqueue(() =>
            {
                dirty = true;
                if (shown)
                {
                    refreshTimer.Start();
                }
            });
        }
    }

    private async Task CheckNowAsync(FrameworkElement anchor)
    {
        var button = anchor as Button;
        if (button is not null)
        {
            button.IsEnabled = false;
        }
        try
        {
            var result = await LibraryUpdater.RunAsync();
            Toasts.Show(result.NewChapters > 0 ? $"{result.NewChapters} new chapters" : "You're up to date",
                result.Failed > 0 ? $"{result.Failed} titles could not be checked" : $"Checked {result.Checked} titles", Icons.Sparkles);
        }
        finally
        {
            if (button is not null)
            {
                button.IsEnabled = true;
            }
        }
    }

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.ItemContainer is not { } container)
        {
            return;
        }
        if (!rowByContainer.TryGetValue(container, out var row))
        {
            row = ItemsTree.FindDescendant<MediaRow>(container);
            if (row is null)
            {
                return;
            }
            rowByContainer[container] = row;
            row.Click += OnRowClick;
            row.ContextRequested += OnRowContextRequested;
        }
        if (args.InRecycleQueue)
        {
            realized.Remove(row);
            row.ResetPresentation();
            return;
        }
        realized.Add(row);
        if (args.Item is MediaRowData { Group: false, Tag: HistoryGroup group } && expandingGroups.Contains(group.Id))
        {
            // Collapse before first layout so the grow storyboard opens from zero.
            row.Height = 0;
            row.Opacity = 0;
            if (!pendingGrow.TryGetValue(group.Id, out var batch))
            {
                pendingGrow[group.Id] = batch = [];
            }
            batch.Add(row);
        }
        else
        {
            row.ResetPresentation();
        }
    }

    public void OnShown()
    {
        shown = true;
        List.ItemContainerTransitions = ListMotion.ItemTransitions();
        if (dirty)
        {
            _ = Log.Guard("list", LoadAsync);
        }
    }

    public void OnHidden() => shown = false;

    private async Task LoadAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        dirty = false;
        rows.Clear();
        DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, () =>
        {
            rowByContainer.Clear();
            realized.Clear();
            expandingGroups.Clear();
            pendingGrow.Clear();
            busyGroups.Clear();
        });
        string? currentDay = null;
        if (kind == ListKind.Updates)
        {
            var updates = await engine.Library.GetUpdatesAsync(300);
            foreach (var u in updates)
            {
                var day = DayLabel(u.DateFetch);
                if (day != currentDay)
                {
                    rows.Add(day);
                    currentDay = day;
                }
                var number = u.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(u.ChapterNumber) : u.ChapterName;
                rows.Add(new MediaRowData(u.MangaId, u.ChapterId, u.Title, u.Scanlator is null ? number : $"{number} · {u.Scanlator}",
                    Format.Relative(u.DateUpload > 0 ? u.DateUpload : u.DateFetch), u.ThumbnailUrl, !u.Read, u.LastPageRead));
            }
            Summary.Text = updates.Count == 0 ? string.Empty : $"{updates.Count(u => !u.Read)} unread";
        }
        else
        {
            var history = await engine.Library.GetHistoryAsync(200);
            List<HistoryEntry>? streak = null;
            foreach (var h in history)
            {
                if (streak is { } current && current[0].MangaId == h.MangaId && DayLabel(current[0].LastReadAt) == DayLabel(h.LastReadAt))
                {
                    current.Add(h);
                    continue;
                }
                if (streak is { } finished)
                {
                    AddHistoryRow(rows, finished);
                }
                streak = [h];
            }
            if (streak is { } last)
            {
                AddHistoryRow(rows, last);
            }
            Summary.Text = history.Count == 0 ? string.Empty : $"{history.Select(h => h.MangaId).Distinct().Count()} titles";
        }
        EmptyState.Visibility = rows.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        if (firstLoad && rows.Count > 0)
        {
            _ = Motion.FadeIn(List, Motion.Medium2, 12);
        }
        firstLoad = false;
    }

    private void AddHistoryRow(ObservableCollection<object> items, List<HistoryEntry> streak)
    {
        var day = DayLabel(streak[0].LastReadAt);
        if (items.Count == 0 || items[^1] is not string previousDay || previousDay != day)
        {
            items.Add(day);
        }
        var newest = streak[0];
        var subtitle = streak.Count == 1
            ? ReadSubtitle(newest)
            : $"{ChapterLabel(newest)} + {streak.Count - 1} more";
        HistoryGroup? group = null;
        if (streak.Count > 1)
        {
            group = new HistoryGroup(nextGroupId++, [.. streak],
                string.Join("\n", streak.Select(e => $"{ReadSubtitle(e)} — {ExactTime(e.LastReadAt)}")));
        }
        items.Add(new MediaRowData(newest.MangaId, newest.ChapterId, newest.Title, subtitle,
            DateTimeOffset.FromUnixTimeMilliseconds(newest.LastReadAt).ToLocalTime().ToString("HH:mm"),
            newest.ThumbnailUrl, false, newest.LastPageRead, group,
            Group: group is not null, Tip: group?.Tip));
    }

    private static string ChapterLabel(HistoryEntry h) =>
        h.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(h.ChapterNumber) : h.ChapterName;

    private static string ReadSubtitle(HistoryEntry h)
    {
        var label = ChapterLabel(h);
        return h.LastPageRead > 0 ? $"{label} · page {h.LastPageRead + 1}" : label;
    }

    private static string ExactTime(long ms) =>
        DateTimeOffset.FromUnixTimeMilliseconds(ms).ToLocalTime().ToString("HH:mm:ss");

    private static MediaRowData ChildRow(HistoryGroup group, HistoryEntry entry) =>
        new(entry.MangaId, entry.ChapterId, entry.Title, ReadSubtitle(entry),
            ExactTime(entry.LastReadAt), entry.ThumbnailUrl, false, entry.LastPageRead, group);

    private static string DayLabel(long ms)
    {
        if (ms <= 0)
        {
            return "Earlier";
        }
        var date = DateTimeOffset.FromUnixTimeMilliseconds(ms).ToLocalTime().Date;
        if (date == DateTime.Today) return "Today";
        if (date == DateTime.Today.AddDays(-1)) return "Yesterday";
        if (date > DateTime.Today.AddDays(-7)) return date.ToString("dddd");
        return date.Year == DateTime.Today.Year ? date.ToString("d MMMM") : date.ToString("d MMMM yyyy");
    }

    private void OnRowClick(object sender, RoutedEventArgs e)
    {
        if (sender is MediaRow row && row.Data is { } data)
        {
            if (data.Group)
            {
                _ = Log.Guard("history", () => ToggleGroupAsync(row, data));
                return;
            }
            _ = AppNav.OpenReaderAsync(data.MangaId, data.ChapterId, data.Page, row.Thumb);
        }
    }

    private async Task ToggleGroupAsync(MediaRow row, MediaRowData head)
    {
        if (head.Tag is not HistoryGroup group || busyGroups.Contains(group.Id))
        {
            return;
        }
        var index = IndexOfRef(rows, head);
        if (index < 0)
        {
            return;
        }
        busyGroups.Add(group.Id);
        try
        {
            if (head.Expanded)
            {
                await CollapseAsync(row, head, group);
            }
            else
            {
                Expand(row, index, head, group);
                if (!Design.Motion.Reduced && !Services.Settings.LabLists)
                {
                    // Let the containers prepare at zero height, then grow them in place.
                    var dispatched = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
                    DispatcherQueue.TryEnqueue(DispatcherQueuePriority.Low, () => dispatched.TrySetResult());
                    await dispatched.Task;
                    await CloseGrowWindow(group.Id);
                }
            }
        }
        finally
        {
            busyGroups.Remove(group.Id);
        }
    }

    private void Expand(MediaRow row, int index, MediaRowData head, HistoryGroup group)
    {
        row.AnimateChevron(true);
        rows[index] = head with { Expanded = true };
        if (!Design.Motion.Reduced && !Services.Settings.LabLists)
        {
            expandingGroups.Add(group.Id);
            pendingGrow[group.Id] = [];
        }
        for (var i = 0; i < group.Entries.Count; i++)
        {
            rows.Insert(index + 1 + i, ChildRow(group, group.Entries[i]));
        }
    }

    private async Task CollapseAsync(MediaRow row, MediaRowData head, HistoryGroup group)
    {
        expandingGroups.Remove(group.Id);
        pendingGrow.Remove(group.Id);
        var headIndex = IndexOfRef(rows, head);
        if (headIndex < 0)
        {
            return;
        }
        row.AnimateChevron(false);
        rows[headIndex] = head with { Expanded = false, Tip = group.Tip };
        List<MediaRow>? closing = null;
        foreach (var candidate in realized)
        {
            if (candidate.Data is { Group: false, Tag: HistoryGroup g } && g.Id == group.Id)
            {
                (closing ??= []).Add(candidate);
            }
        }
        if (closing is { Count: > 0 } rowsToClose && !Design.Motion.Reduced && !Services.Settings.LabLists)
        {
            await CloseAsync(rowsToClose);
        }
        var index = IndexOfRef(rows, head);
        if (index < 0)
        {
            return; // the page rebuilt while the collapse animation ran
        }
        for (var i = rows.Count - 1; i > index; i--)
        {
            if (rows[i] is MediaRowData d && d.Tag is HistoryGroup g && g.Id == group.Id)
            {
                rows.RemoveAt(i);
            }
        }
    }

    private Task CloseGrowWindow(long groupId)
    {
        expandingGroups.Remove(groupId);
        if (!pendingGrow.Remove(groupId, out var batch) || batch.Count == 0)
        {
            return Task.CompletedTask;
        }
        var pending = batch.Count;
        var done = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        for (var i = 0; i < batch.Count; i++)
        {
            var grow = GrowBoard(batch[i], i * 24, open: true);
            var row = batch[i];
            grow.Completed += (_, _) =>
            {
                // Land locals on the final values before releasing the held animation.
                row.Height = RowHeight;
                row.Opacity = 1;
                row.Board = null;
                if (Interlocked.Decrement(ref pending) == 0)
                {
                    done.TrySetResult();
                }
            };
            row.Board = grow;
            grow.Begin();
        }
        return done.Task.WaitAsync(TimeSpan.FromSeconds(2));
    }

    private static Task CloseAsync(IReadOnlyList<MediaRow> rowsToClose)
    {
        var pending = rowsToClose.Count;
        var done = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        foreach (var row in rowsToClose)
        {
            var board = GrowBoard(row, 0, open: false);
            board.Completed += (_, _) =>
            {
                // Hold the shut state in locals before releasing the held animation.
                row.Height = 0;
                row.Opacity = 0;
                row.Board = null;
                if (Interlocked.Decrement(ref pending) == 0)
                {
                    done.TrySetResult();
                }
            };
            row.Board = board;
            board.Begin();
        }
        return done.Task.WaitAsync(TimeSpan.FromSeconds(2));
    }

    private static Storyboard GrowBoard(MediaRow row, int delayMs, bool open)
    {
        var height = new DoubleAnimation
        {
            From = open ? 0 : null,
            To = open ? RowHeight : 0,
            Duration = new Duration(TimeSpan.FromMilliseconds(open ? 220 : 180)),
            BeginTime = TimeSpan.FromMilliseconds(delayMs),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        Storyboard.SetTarget(height, row);
        Storyboard.SetTargetProperty(height, "Height");
        var fade = new DoubleAnimation
        {
            From = open ? 0 : null,
            To = open ? 1 : 0,
            Duration = new Duration(TimeSpan.FromMilliseconds(open ? 140 : 120)),
            BeginTime = TimeSpan.FromMilliseconds(delayMs),
        };
        Storyboard.SetTarget(fade, row);
        Storyboard.SetTargetProperty(fade, "Opacity");
        var board = new Storyboard();
        board.Children.Add(height);
        board.Children.Add(fade);
        return board;
    }

    private static int IndexOfRef(ObservableCollection<object> items, object target)
    {
        for (var i = 0; i < items.Count; i++)
        {
            if (ReferenceEquals(items[i], target))
            {
                return i;
            }
        }
        return -1;
    }

    private void OnRowContextRequested(UIElement sender, ContextRequestedEventArgs e)
    {
        e.Handled = true;
        if (sender is MediaRow row)
        {
            OnRowContext(row);
        }
    }

    private void OnRowContext(MediaRow row)
    {
        var data = row.Data!;
        var menu = new MenuFlyout();
        var details = new MenuFlyoutItem { Text = "Open details" };
        details.Click += (_, _) => _ = AppNav.OpenDetailsAsync(data.MangaId, null);
        menu.Items.Add(details);
        var read = new MenuFlyoutItem { Text = data.Unread ? "Mark as read" : "Mark as unread" };
        read.Click += async (_, _) =>
        {
            if (Services.Engine is { } engine)
            {
                await engine.Library.SetChaptersReadAsync([data.ChapterId], data.Unread);
            }
        };
        menu.Items.Add(read);
        menu.ShowAt(row);
    }
}

file sealed class MediaTemplateSelector(DataTemplate row, DataTemplate header) : DataTemplateSelector
{
    protected override DataTemplate? SelectTemplateCore(object item) => item is string ? header : row;
}
