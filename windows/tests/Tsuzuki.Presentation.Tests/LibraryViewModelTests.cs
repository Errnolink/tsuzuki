using System.Collections.Concurrent;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class LibraryViewModelTests : IDisposable
{
    private const long Source = 2499283573021220255;

    private readonly LibraryDatabase db;
    private readonly SqliteLibraryStore store;
    private readonly SerialUi ui = new();

    public LibraryViewModelTests()
    {
        var dir = Path.Combine(Path.GetTempPath(), "tsuzuki-presentation-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        db = new LibraryDatabase(Path.Combine(dir, "library.db"));
        db.Initialise();
        db.SetMetaAsync("local_device_id", "pc-test").GetAwaiter().GetResult();
        store = new SqliteLibraryStore(db, _ => true);
    }

    public void Dispose()
    {
        ui.Dispose();
        db.Dispose();
    }

    private async Task<long> AddAsync(string title, int chapters, int read)
    {
        var id = await store.UpsertFromSourceAsync(Source, new SManga("/manga/" + Guid.NewGuid().ToString("N"), title));
        await store.SyncChaptersFromSourceAsync(id, [.. Enumerable.Range(1, chapters).Select(n => new SChapter($"/chapter/{id}-{n}", $"Ch. {n}") { ChapterNumber = n })]);
        var rows = await store.GetChaptersAsync(id);
        if (read > 0)
        {
            await store.SetChaptersReadAsync([.. rows.OrderBy(r => r.ChapterNumber).Take(read).Select(r => r.ChapterId)], true);
        }
        await store.SetFavoriteAsync(id, true);
        return id;
    }

    private (LibraryViewModel Model, List<LibraryViewChange> Changes) Model(Func<bool>? readerOpen = null, ILibraryStore? library = null)
    {
        var model = new LibraryViewModel(library ?? store, _ => "MangaDex", ui, readerOpen ?? (() => false), TimeSpan.FromMilliseconds(40));
        var changes = new List<LibraryViewChange>();
        model.Changed += (_, change) => changes.Add(change);
        return (model, changes);
    }

    private static async Task Until(Func<bool> condition, int timeoutMs = 3000)
    {
        var watch = System.Diagnostics.Stopwatch.StartNew();
        while (!condition())
        {
            if (watch.ElapsedMilliseconds > timeoutMs)
            {
                throw new TimeoutException("condition not reached");
            }
            await Task.Delay(10);
        }
    }

    [Fact]
    public async Task A_burst_of_library_changes_repaints_the_library_once()
    {
        var ids = new List<long>();
        for (var i = 0; i < 20; i++)
        {
            ids.Add(await AddAsync("Title " + i, 3, 0));
        }
        var (model, changes) = Model();
        model.Activate();
        await Until(() => model.Loaded && changes.Count == 1);

        foreach (var id in ids)
        {
            await store.SetChaptersReadAsync([.. (await store.GetChaptersAsync(id)).Take(1).Select(c => c.ChapterId)], true);
        }
        await Until(() => changes.Count >= 2);
        await Task.Delay(300);

        Assert.Equal(2, changes.Count);
        Assert.All(model.All, e => Assert.Equal(2, e.UnreadCount));
    }

    [Fact]
    public async Task Unread_only_and_search_narrow_the_library_together()
    {
        await AddAsync("Yotsuba&!", 5, 5);
        await AddAsync("Yotsuba Side Stories", 5, 2);
        await AddAsync("Blue Lock", 5, 0);
        var (model, changes) = Model();
        model.Activate();
        await Until(() => model.Loaded);

        await model.SetQueryAsync(new LibraryQuery(Search: "yotsuba", UnreadOnly: true));

        var only = Assert.Single(model.Visible);
        Assert.Equal("Yotsuba Side Stories", only.Title);
    }

    [Fact]
    public async Task A_slow_earlier_filter_never_replaces_a_newer_one()
    {
        await AddAsync("Slow", 2, 0);
        await AddAsync("Fast", 2, 0);
        var slow = new SlowStatuses(store);
        var (model, _) = Model(library: slow);
        model.Activate();
        await Until(() => model.Loaded);

        slow.Hold = true;
        var older = model.SetQueryAsync(new LibraryQuery(Status: (long)MangaStatus.Ongoing));
        var newer = model.SetQueryAsync(new LibraryQuery(Search: "fast"));
        await newer;
        slow.Hold = false;
        await older;

        Assert.Equal("Fast", Assert.Single(model.Visible).Title);
    }

    [Fact]
    public async Task Page_progress_while_the_reader_is_open_repaints_only_after_it_closes()
    {
        var id = await AddAsync("Yotsuba&!", 4, 0);
        var readerOpen = true;
        var (model, changes) = Model(() => readerOpen);
        model.Activate();
        await Until(() => model.Loaded);
        var before = changes.Count;

        var chapter = (await store.GetChaptersAsync(id)).First();
        await store.SetChapterProgressAsync(chapter.ChapterId, 2, false);
        await Task.Delay(300);
        Assert.Equal(before, changes.Count);

        readerOpen = false;
        ui.Post(model.ReaderClosed);
        await Until(() => changes.Count > before);
    }

    private sealed class SerialUi : IUiThread, IDisposable
    {
        private readonly BlockingCollection<Action> work = [];
        private readonly Thread thread;

        public SerialUi()
        {
            thread = new Thread(() =>
            {
                foreach (var item in work.GetConsumingEnumerable())
                {
                    item();
                }
            }) { IsBackground = true };
            thread.Start();
        }

        public void Post(Action action) => work.Add(action);

        public void Dispose() => work.CompleteAdding();
    }

    private sealed class SlowStatuses(ILibraryStore inner) : ILibraryStore
    {
        public volatile bool Hold;

        public async Task<MangaDetails?> GetMangaAsync(long mangaId, CancellationToken ct = default)
        {
            while (Hold)
            {
                await Task.Delay(10, ct);
            }
            return await inner.GetMangaAsync(mangaId, ct);
        }

        public Task<IReadOnlyList<LibraryEntry>> GetLibraryAsync(CancellationToken ct = default) => inner.GetLibraryAsync(ct);
        public Task<IReadOnlySet<string>> GetFavouriteKeysAsync(CancellationToken ct = default) => inner.GetFavouriteKeysAsync(ct);
        public Task<bool> HasFavouritesAsync(CancellationToken ct = default) => inner.HasFavouritesAsync(ct);
        public Task<IReadOnlyList<CategoryInfo>> GetCategoriesAsync(CancellationToken ct = default) => inner.GetCategoriesAsync(ct);
        public Task<IReadOnlyList<ContinueEntry>> GetContinueReadingAsync(int limit, CancellationToken ct = default) => inner.GetContinueReadingAsync(limit, ct);
        public Task<long?> FindMangaIdAsync(long source, string url, CancellationToken ct = default) => inner.FindMangaIdAsync(source, url, ct);
        public Task<IReadOnlyList<ChapterRow>> GetChaptersAsync(long mangaId, CancellationToken ct = default) => inner.GetChaptersAsync(mangaId, ct);
        public Task<ChapterRow?> GetChapterAsync(long chapterId, CancellationToken ct = default) => inner.GetChapterAsync(chapterId, ct);
        public Task<IReadOnlyList<HistoryEntry>> GetHistoryAsync(int limit, CancellationToken ct = default) => inner.GetHistoryAsync(limit, ct);
        public Task<IReadOnlyList<UpdateEntry>> GetUpdatesAsync(int limit, CancellationToken ct = default) => inner.GetUpdatesAsync(limit, ct);
        public Task<long> UpsertFromSourceAsync(long source, SManga manga, CancellationToken ct = default) => inner.UpsertFromSourceAsync(source, manga, ct);
        public Task<ChapterSyncResult> SyncChaptersFromSourceAsync(long mangaId, IReadOnlyList<SChapter> chapters, CancellationToken ct = default) => inner.SyncChaptersFromSourceAsync(mangaId, chapters, ct);
        public Task SetFavoriteAsync(long mangaId, bool favorite, CancellationToken ct = default) => inner.SetFavoriteAsync(mangaId, favorite, ct);
        public Task SetChapterProgressAsync(long chapterId, int lastPageRead, bool read, CancellationToken ct = default) => inner.SetChapterProgressAsync(chapterId, lastPageRead, read, ct);
        public Task SetChaptersReadAsync(IReadOnlyList<long> chapterIds, bool read, CancellationToken ct = default) => inner.SetChaptersReadAsync(chapterIds, read, ct);
        public Task SetMangaCategoriesAsync(long mangaId, IReadOnlyList<long> categoryUids, CancellationToken ct = default) => inner.SetMangaCategoriesAsync(mangaId, categoryUids, ct);
        public Task<CategoryInfo> CreateCategoryAsync(string name, CancellationToken ct = default) => inner.CreateCategoryAsync(name, ct);
        public Task FlushAsync(CancellationToken ct = default) => inner.FlushAsync(ct);
        public IDisposable BeginBatch() => inner.BeginBatch();

        public event EventHandler<LibraryChangedEventArgs>? Changed
        {
            add => inner.Changed += value;
            remove => inner.Changed -= value;
        }
    }
}
