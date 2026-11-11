using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.Presentation;

public interface IUiThread
{
    void Post(Action work);
}

public sealed record LibraryQuery(
    long? Category = null,
    string Search = "",
    string Sort = "recent",
    bool UnreadOnly = false,
    bool ReadableOnly = false,
    long? Status = null,
    long? Source = null,
    bool DownloadedOnly = false,
    bool MergedOnly = false)
{
    public string Shape => $"{Sort}|{Category?.ToString() ?? "-"}|{UnreadOnly}|{ReadableOnly}|{Status}|{Source}|{DownloadedOnly}|{MergedOnly}";
}

public sealed class LibraryViewModel
{
    public const int InPlaceLimit = 8;
    private static readonly TimeSpan MaxCoalesce = TimeSpan.FromSeconds(2);

    private readonly ILibraryStore library;
    private readonly Func<long, string> sourceName;
    private readonly IUiThread ui;
    private readonly Func<bool> readerOpen;
    private readonly TimeSpan coalesce;
    private readonly Func<IReadOnlySet<long>>? downloaded;
    private readonly HashSet<long> pendingIds = [];
    private readonly HashSet<long> deferredIds = [];
    private readonly Dictionary<long, MangaStatus> statuses = [];
    private CancellationTokenSource? timer;
    private DateTimeOffset pendingSince;
    private bool pendingFull = true;
    private bool deferredFull;
    private bool reloading;
    private bool active;
    private int filterVersion;

    public LibraryViewModel(ILibraryStore library, Func<long, string> sourceName, IUiThread ui, Func<bool> readerOpen, TimeSpan? coalesce = null, Func<IReadOnlySet<long>>? downloaded = null)
    {
        this.library = library;
        this.sourceName = sourceName;
        this.ui = ui;
        this.readerOpen = readerOpen;
        this.coalesce = coalesce ?? TimeSpan.FromMilliseconds(400);
        this.downloaded = downloaded;
        library.Changed += OnLibraryChanged;
    }

    public event EventHandler<LibraryViewChange>? Changed;

    public IReadOnlyList<LibraryEntry> All { get; private set; } = [];

    public IReadOnlyList<LibraryEntry> Visible { get; private set; } = [];

    public IReadOnlyList<CategoryInfo> Categories { get; private set; } = [];

    public LibraryQuery Query { get; private set; } = new();

    public bool Loaded { get; private set; }

    public long UnreadTotal => All.Sum(e => (long)e.UnreadCount);

    public void Activate()
    {
        active = true;
        if (Pending)
        {
            Schedule(immediate: !Loaded);
        }
    }

    public void Deactivate() => active = false;

    public void ReaderClosed()
    {
        if (!deferredFull && deferredIds.Count == 0)
        {
            return;
        }
        List<long> ids = deferredFull ? [] : [.. deferredIds];
        deferredIds.Clear();
        deferredFull = false;
        Queue(ids);
    }

    public void Invalidate()
    {
        pendingFull = true;
        pendingIds.Clear();
        statuses.Clear();
        if (active)
        {
            Schedule(immediate: true);
        }
    }

    public Task SetQueryAsync(LibraryQuery query)
    {
        var shapeChanged = query.Shape != Query.Shape;
        Query = query;
        return ApplyAsync(shapeChanged ? LibraryViewChangeKind.Reshaped : LibraryViewChangeKind.Filtered);
    }

    private bool Pending => pendingFull || pendingIds.Count > 0;

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e) =>
        ui.Post(() =>
        {
            if (e.IsProgressOnly && readerOpen())
            {
                deferredIds.UnionWith(e.MangaIds);
                deferredFull |= e.MangaIds.Count == 0;
                return;
            }
            Queue(e.MangaIds);
        });

    private void Queue(IReadOnlyCollection<long> ids)
    {
        if (ids.Count == 0)
        {
            statuses.Clear();
        }
        foreach (var id in ids)
        {
            statuses.Remove(id);
        }
        if (!pendingFull)
        {
            pendingIds.UnionWith(ids);
            if (ids.Count == 0 || pendingIds.Count > InPlaceLimit)
            {
                pendingFull = true;
                pendingIds.Clear();
            }
        }
        if (active)
        {
            Schedule(immediate: false);
        }
    }

    private void Schedule(bool immediate)
    {
        if (reloading)
        {
            return;
        }
        var now = DateTimeOffset.UtcNow;
        if (timer is null)
        {
            pendingSince = now;
        }
        else if (now - pendingSince >= MaxCoalesce)
        {
            return;
        }
        timer?.Cancel();
        var cts = timer = new CancellationTokenSource();
        _ = Task.Delay(immediate ? TimeSpan.Zero : coalesce, cts.Token).ContinueWith(t =>
        {
            if (!t.IsCanceled)
            {
                ui.Post(() =>
                {
                    if (timer == cts)
                    {
                        timer = null;
                        _ = ReloadAsync();
                    }
                });
            }
        }, TaskScheduler.Default);
    }

    private async Task ReloadAsync()
    {
        if (reloading || !Pending)
        {
            return;
        }
        reloading = true;
        var full = pendingFull;
        var ids = pendingIds.ToList();
        pendingFull = false;
        pendingIds.Clear();
        try
        {
            if (full || !await TryUpdateInPlaceAsync(ids))
            {
                await LoadAsync();
            }
        }
        finally
        {
            reloading = false;
            if (active && Pending)
            {
                Schedule(immediate: false);
            }
        }
    }

    private async Task LoadAsync()
    {
        var entries = library.GetLibraryAsync();
        var categories = library.GetCategoriesAsync();
        All = await entries;
        Categories = await categories;
        Loaded = true;
        await ApplyAsync(LibraryViewChangeKind.Loaded);
    }

    private async Task<bool> TryUpdateInPlaceAsync(IReadOnlyList<long> ids)
    {
        var positions = new Dictionary<long, int>(All.Count);
        for (var i = 0; i < All.Count; i++)
        {
            positions[All[i].MangaId] = i;
        }
        var changes = new Dictionary<long, LibraryEntry?>();
        foreach (var id in ids)
        {
            var manga = await library.GetMangaAsync(id);
            if (!positions.TryGetValue(id, out var index))
            {
                if (manga is { Favorite: true } || All.Any(e => e.IsMerged))
                {
                    return false;
                }
                continue;
            }
            var existing = All[index];
            if (existing.IsMerged)
            {
                return false;
            }
            if (manga is not { Favorite: true })
            {
                changes[id] = null;
                continue;
            }
            statuses[id] = manga.Status;
            var groups = ChapterGrouping.Group(await library.GetChaptersAsync(id), id);
            changes[id] = existing with
            {
                Title = manga.Title,
                ThumbnailUrl = manga.ThumbnailUrl,
                UnreadCount = groups.Count(g => !g.Read),
                ChapterCount = groups.Count,
                LastReadAt = groups.Count == 0 ? 0 : groups.Max(g => g.LastReadAt),
                CategoryUids = manga.CategoryUids,
            };
        }
        if (changes.Count > 0)
        {
            var next = new List<LibraryEntry>(All.Count);
            foreach (var entry in All)
            {
                if (!changes.TryGetValue(entry.MangaId, out var changed))
                {
                    next.Add(entry);
                }
                else if (changed is not null)
                {
                    next.Add(changed);
                }
            }
            All = next;
            await ApplyAsync(LibraryViewChangeKind.Updated);
        }
        return true;
    }

    private async Task ApplyAsync(LibraryViewChangeKind kind)
    {
        var my = ++filterVersion;
        var entries = All;
        var query = Query;
        if (query.Status is not null)
        {
            foreach (var entry in entries)
            {
                if (!statuses.ContainsKey(entry.MangaId))
                {
                    statuses[entry.MangaId] = (await library.GetMangaAsync(entry.MangaId))?.Status ?? MangaStatus.Unknown;
                }
                if (my != filterVersion)
                {
                    return;
                }
            }
        }
        var statusSnapshot = query.Status is null ? null : new Dictionary<long, MangaStatus>(statuses);
        var names = query.Sort == "source" ? entries.Select(e => e.Source).Distinct().ToDictionary(id => id, sourceName) : null;
        var downloadedIds = query.DownloadedOnly ? downloaded?.Invoke() ?? new HashSet<long>() : null;
        var visible = await Task.Run(() => Filter(entries, query, statusSnapshot, names, downloadedIds));
        if (my != filterVersion)
        {
            return;
        }
        Visible = visible;
        Changed?.Invoke(this, new LibraryViewChange(kind));
    }

    public static IReadOnlyList<LibraryEntry> Filter(IReadOnlyList<LibraryEntry> entries, LibraryQuery query, IReadOnlyDictionary<long, MangaStatus>? statuses, IReadOnlyDictionary<long, string>? sourceNames, IReadOnlySet<long>? downloadedIds = null)
    {
        IEnumerable<LibraryEntry> result = entries;
        if (query.Category is { } uid)
        {
            result = uid == 0 ? result.Where(e => e.CategoryUids.Count == 0) : result.Where(e => e.CategoryUids.Contains(uid));
        }
        if (query.UnreadOnly)
        {
            result = result.Where(e => e.UnreadCount > 0);
        }
        if (query.ReadableOnly)
        {
            result = result.Where(e => e.Readable);
        }
        if (query.DownloadedOnly)
        {
            result = result.Where(e => downloadedIds?.Contains(e.MangaId) == true);
        }
        if (query.MergedOnly)
        {
            result = result.Where(e => e.IsMerged);
        }
        if (query.Source is { } sid)
        {
            result = result.Where(e => e.Source == sid);
        }
        if (query.Status is { } st && statuses is not null)
        {
            result = result.Where(e => statuses.TryGetValue(e.MangaId, out var s) && (long)s == st);
        }
        if (query.Search.Length > 0)
        {
            result = result.Where(e => e.Title.Contains(query.Search, StringComparison.CurrentCultureIgnoreCase));
        }
        result = query.Sort switch
        {
            "title" => result.OrderBy(e => e.Title, StringComparer.CurrentCultureIgnoreCase),
            "unread" => result.OrderByDescending(e => e.UnreadCount).ThenByDescending(e => e.LastReadAt),
            "added" => result.OrderByDescending(e => e.DateAdded),
            "source" => result.OrderBy(e => sourceNames?.GetValueOrDefault(e.Source) ?? string.Empty, StringComparer.CurrentCultureIgnoreCase).ThenBy(e => e.Title, StringComparer.CurrentCultureIgnoreCase),
            "progress" => result.OrderByDescending(e => e.ChapterCount == 0 ? 0 : (double)(e.ChapterCount - e.UnreadCount) / e.ChapterCount).ThenByDescending(e => e.LastReadAt),
            _ => result.OrderByDescending(e => e.LastReadAt).ThenByDescending(e => e.DateAdded),
        };
        return result.ToList();
    }
}

public enum LibraryViewChangeKind
{
    Loaded,
    Updated,
    Filtered,
    Reshaped,
}

public sealed record LibraryViewChange(LibraryViewChangeKind Kind);
