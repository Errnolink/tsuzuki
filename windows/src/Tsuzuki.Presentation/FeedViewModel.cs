using Tsuzuki.Core.Library;

namespace Tsuzuki.Presentation;

public interface IFeed
{
    void Activate();
    void Deactivate();
    void ReaderClosed();
    void MarkDirty();
}

public sealed class FeedViewModel<T> : IFeed
{
    private readonly ILibraryStore library;
    private readonly Func<ILibraryStore, Task<IReadOnlyList<T>>> fetch;
    private readonly IUiThread ui;
    private readonly Func<bool> readerOpen;
    private readonly TimeSpan coalesce;
    private CancellationTokenSource? timer;
    private bool dirty = true;
    private bool staleAfterReader;
    private bool active;
    private int version;

    public FeedViewModel(ILibraryStore library, Func<ILibraryStore, Task<IReadOnlyList<T>>> fetch, IUiThread ui, Func<bool> readerOpen, TimeSpan? coalesce = null)
    {
        this.library = library;
        this.fetch = fetch;
        this.ui = ui;
        this.readerOpen = readerOpen;
        this.coalesce = coalesce ?? TimeSpan.FromMilliseconds(300);
        library.Changed += OnLibraryChanged;
    }

    public event EventHandler? Changed;

    public IReadOnlyList<T> Items { get; private set; } = [];

    public bool Loaded { get; private set; }

    public void Activate()
    {
        active = true;
        if (dirty)
        {
            _ = LoadAsync();
        }
    }

    public void Deactivate() => active = false;

    public void ReaderClosed()
    {
        if (staleAfterReader)
        {
            staleAfterReader = false;
            MarkDirty();
        }
    }

    public void MarkDirty()
    {
        dirty = true;
        if (!active)
        {
            return;
        }
        timer?.Cancel();
        var cts = timer = new CancellationTokenSource();
        _ = Task.Delay(coalesce, cts.Token).ContinueWith(t =>
        {
            if (!t.IsCanceled)
            {
                ui.Post(() =>
                {
                    if (timer == cts)
                    {
                        timer = null;
                        _ = LoadAsync();
                    }
                });
            }
        }, TaskScheduler.Default);
    }

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e) =>
        ui.Post(() =>
        {
            if (e.IsProgressOnly && readerOpen())
            {
                staleAfterReader = true;
                return;
            }
            MarkDirty();
        });

    private async Task LoadAsync()
    {
        var my = ++version;
        dirty = false;
        var items = await fetch(library);
        if (my != version)
        {
            return;
        }
        Items = items;
        Loaded = true;
        Changed?.Invoke(this, EventArgs.Empty);
    }
}

public static class UpdateClusters
{
    public static IReadOnlyList<IReadOnlyList<UpdateEntry>> Build(IReadOnlyList<UpdateEntry> updates, bool unreadOnly, Func<long, string> dayOf)
    {
        var clusters = new List<List<UpdateEntry>>();
        var byTitle = new Dictionary<(string Day, long MangaId), List<UpdateEntry>>();
        foreach (var update in unreadOnly ? updates.Where(u => !u.Read) : updates)
        {
            var key = (dayOf(update.DateFetch), update.MangaId);
            if (!byTitle.TryGetValue(key, out var cluster))
            {
                byTitle[key] = cluster = [];
                clusters.Add(cluster);
            }
            cluster.Add(update);
        }
        return clusters;
    }

    public static IReadOnlyList<long> UnreadIds(IReadOnlyList<UpdateEntry> updates) =>
        updates.Where(u => !u.Read).Select(u => u.ChapterId).Distinct().ToList();
}

public static class HistoryStreaks
{
    public static IReadOnlyList<IReadOnlyList<HistoryEntry>> Build(IReadOnlyList<HistoryEntry> history, Func<long, string> dayOf)
    {
        var streaks = new List<List<HistoryEntry>>();
        foreach (var entry in history)
        {
            if (streaks.Count > 0 && streaks[^1][0].MangaId == entry.MangaId && dayOf(streaks[^1][0].LastReadAt) == dayOf(entry.LastReadAt))
            {
                streaks[^1].Add(entry);
                continue;
            }
            streaks.Add([entry]);
        }
        return streaks;
    }
}
