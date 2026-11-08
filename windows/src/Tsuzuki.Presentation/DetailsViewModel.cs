using Tsuzuki.Core.Library;

namespace Tsuzuki.Presentation;

public sealed record DetailsSnapshot(MangaDetails Manga, IReadOnlyList<ChapterRow> Chapters);

public enum DetailsChangeKind
{
    Missing,
    Loaded,
    RefreshStarted,
    Refreshed,
    RefreshFailed,
    LibraryChanged,
}

public sealed record DetailsChange(DetailsChangeKind Kind, ChapterSyncResult? Result = null);

public sealed class DetailsViewModel
{
    private static readonly HashSet<long> RefreshedThisSession = [];

    private readonly long mangaId;
    private readonly ILibraryStore library;
    private readonly Func<long, bool, Task<ChapterSyncResult?>> refresh;
    private readonly IUiThread ui;
    private readonly Func<bool> readerOpen;
    private readonly TimeSpan coalesce;
    private HashSet<long> watched;
    private CancellationTokenSource? timer;
    private bool changedDuringRefresh;
    private bool staleAfterReader;
    private bool active;
    private int changeVersion;

    public DetailsViewModel(long mangaId, ILibraryStore library, Func<long, bool, Task<ChapterSyncResult?>> refresh, IUiThread ui, Func<bool> readerOpen, TimeSpan? coalesce = null)
    {
        this.mangaId = mangaId;
        this.library = library;
        this.refresh = refresh;
        this.ui = ui;
        this.readerOpen = readerOpen;
        this.coalesce = coalesce ?? TimeSpan.FromMilliseconds(300);
        watched = [mangaId];
    }

    public event EventHandler<DetailsChange>? Changed;

    public long MangaId => mangaId;

    public MangaDetails? Manga { get; private set; }

    public IReadOnlyList<ChapterRow> Chapters { get; private set; } = [];

    public bool Refreshing { get; private set; }

    public static void ForgetSession() => RefreshedThisSession.Clear();

    public async Task StartAsync(Task<DetailsSnapshot?> snapshot)
    {
        var data = await snapshot;
        if (data is null)
        {
            Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.Missing));
            return;
        }
        Manga ??= data.Manga;
        Chapters = data.Chapters;
        Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.Loaded));
        _ = ResolveWatchedAsync();
        if (Chapters.Count == 0 || data.Manga.RefreshDue(DateTimeOffset.UtcNow) && RefreshedThisSession.Add(mangaId))
        {
            await RefreshAsync(includeDetails: string.IsNullOrEmpty(Manga.Description) || !Manga.Favorite);
        }
    }

    public void Activate()
    {
        if (active)
        {
            return;
        }
        active = true;
        library.Changed += OnLibraryChanged;
    }

    public void Deactivate()
    {
        if (!active)
        {
            return;
        }
        active = false;
        library.Changed -= OnLibraryChanged;
        timer?.Cancel();
        timer = null;
    }

    public void ReaderClosed()
    {
        if (!staleAfterReader)
        {
            return;
        }
        staleAfterReader = false;
        Schedule();
    }

    public async Task RefreshAsync(bool includeDetails)
    {
        if (Refreshing)
        {
            return;
        }
        Refreshing = true;
        Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.RefreshStarted));
        try
        {
            var result = await refresh(mangaId, includeDetails);
            Manga = await library.GetMangaAsync(mangaId) ?? Manga;
            Chapters = await library.GetChaptersAsync(mangaId);
            Refreshing = false;
            Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.Refreshed, result));
        }
        catch (Exception e) when (e is not OperationCanceledException || e is TaskCanceledException)
        {
            Refreshing = false;
            Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.RefreshFailed));
        }
        finally
        {
            Refreshing = false;
            if (changedDuringRefresh)
            {
                changedDuringRefresh = false;
                Schedule();
            }
        }
    }

    private async Task ResolveWatchedAsync()
    {
        var ids = new HashSet<long> { mangaId };
        if (await library.GetMangaAsync(mangaId) is { } manga)
        {
            foreach (var reference in manga.MergedRefs)
            {
                if (await library.FindMangaIdAsync(reference.Source, reference.Url) is { } id)
                {
                    ids.Add(id);
                }
            }
        }
        watched = ids;
    }

    private void OnLibraryChanged(object? sender, LibraryChangedEventArgs e)
    {
        var ids = watched;
        if (e.MangaIds.Count > 0 && !e.MangaIds.Any(ids.Contains))
        {
            return;
        }
        ui.Post(() =>
        {
            if (Refreshing)
            {
                changedDuringRefresh = true;
                return;
            }
            if (e.IsProgressOnly && readerOpen())
            {
                staleAfterReader = true;
                return;
            }
            Schedule();
        });
    }

    private void Schedule()
    {
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
                        _ = ApplyLibraryChangeAsync();
                    }
                });
            }
        }, TaskScheduler.Default);
    }

    private async Task ApplyLibraryChangeAsync()
    {
        if (Refreshing)
        {
            changedDuringRefresh = true;
            return;
        }
        var my = ++changeVersion;
        var manga = await library.GetMangaAsync(mangaId);
        var chapters = await library.GetChaptersAsync(mangaId);
        await ResolveWatchedAsync();
        if (my != changeVersion || Refreshing)
        {
            return;
        }
        Manga = manga ?? Manga;
        Chapters = chapters;
        Changed?.Invoke(this, new DetailsChange(DetailsChangeKind.LibraryChanged));
    }
}
