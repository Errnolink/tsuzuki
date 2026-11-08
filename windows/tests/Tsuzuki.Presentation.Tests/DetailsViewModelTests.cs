using System.Collections.Concurrent;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class DetailsViewModelTests : IDisposable
{
    private const long Source = 2499283573021220255;

    private readonly LibraryDatabase db;
    private readonly SqliteLibraryStore store;
    private readonly SerialUi ui = new();

    public DetailsViewModelTests()
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

    private async Task<long> AddAsync(int chapters)
    {
        var id = await store.UpsertFromSourceAsync(Source, new SManga("/manga/" + Guid.NewGuid().ToString("N"), "Yotsuba&!") { Description = "Daily life." });
        await store.SyncChaptersFromSourceAsync(id, [.. Enumerable.Range(1, chapters).Select(n => new SChapter($"/chapter/{id}-{n}", $"Ch. {n}") { ChapterNumber = n })]);
        await store.SetFavoriteAsync(id, true);
        return id;
    }

    private async Task<DetailsSnapshot?> SnapshotAsync(long id) =>
        await store.GetMangaAsync(id) is { } manga ? new DetailsSnapshot(manga, await store.GetChaptersAsync(id)) : null;

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
    public async Task A_change_that_lands_during_a_refresh_is_shown_once_the_refresh_ends()
    {
        var id = await AddAsync(3);
        var gate = new TaskCompletionSource<ChapterSyncResult?>();
        var model = new DetailsViewModel(id, store, (_, _) => gate.Task, ui, () => false, TimeSpan.FromMilliseconds(30));
        var changes = new ConcurrentQueue<DetailsChange>();
        model.Changed += (_, change) => changes.Enqueue(change);
        model.Activate();
        await model.StartAsync(SnapshotAsync(id));

        var refresh = model.RefreshAsync(includeDetails: false);
        var first = (await store.GetChaptersAsync(id)).OrderBy(c => c.ChapterNumber).First();
        await store.SetChaptersReadAsync([first.ChapterId], true);
        await Task.Delay(150);
        Assert.DoesNotContain(changes, c => c.Kind == DetailsChangeKind.LibraryChanged);

        gate.SetResult(null);
        await refresh;
        await Until(() => changes.Any(c => c.Kind == DetailsChangeKind.LibraryChanged));
        Assert.True(model.Chapters.Single(c => c.ChapterId == first.ChapterId).Read);
    }

    [Fact]
    public async Task Opening_a_title_checked_recently_does_not_ask_the_source_again()
    {
        var id = await AddAsync(3);
        var calls = 0;
        var model = new DetailsViewModel(id, store, (_, _) =>
        {
            Interlocked.Increment(ref calls);
            return Task.FromResult<ChapterSyncResult?>(null);
        }, ui, () => false);

        await model.StartAsync(SnapshotAsync(id));

        Assert.Equal(0, calls);
    }

    [Fact]
    public async Task Changes_to_other_titles_leave_the_page_alone()
    {
        var id = await AddAsync(3);
        var other = await AddAsync(2);
        var model = new DetailsViewModel(id, store, (_, _) => Task.FromResult<ChapterSyncResult?>(null), ui, () => false, TimeSpan.FromMilliseconds(30));
        var changes = new ConcurrentQueue<DetailsChange>();
        model.Changed += (_, change) => changes.Enqueue(change);
        model.Activate();
        await model.StartAsync(SnapshotAsync(id));

        await store.SetChaptersReadAsync([(await store.GetChaptersAsync(other)).First().ChapterId], true);
        await Task.Delay(200);

        Assert.DoesNotContain(changes, c => c.Kind == DetailsChangeKind.LibraryChanged);
    }

    [Fact]
    public async Task A_merged_page_follows_new_chapters_of_a_source_added_while_it_is_open()
    {
        var merges = new SqliteMergeEditor(db, store);
        var parent = await merges.MergeAsync(await AddAsync(2), await AddAsync(2));
        var model = new DetailsViewModel(parent, store, (_, _) => Task.FromResult<ChapterSyncResult?>(null), ui, () => false, TimeSpan.FromMilliseconds(30));
        model.Activate();
        await model.StartAsync(SnapshotAsync(parent));
        var added = await AddAsync(1);

        await merges.MergeAsync(parent, added);
        await Until(() => model.Chapters.Count == 5);
        await store.SyncChaptersFromSourceAsync(added, [new SChapter($"/chapter/{added}-1", "Ch. 1") { ChapterNumber = 1 }, new SChapter($"/chapter/{added}-2", "Ch. 2") { ChapterNumber = 2 }]);

        await Until(() => model.Chapters.Count == 6);
    }

    private sealed class SerialUi : IUiThread, IDisposable
    {
        private readonly BlockingCollection<Action> work = [];

        public SerialUi()
        {
            new Thread(() =>
            {
                foreach (var item in work.GetConsumingEnumerable())
                {
                    item();
                }
            }) { IsBackground = true }.Start();
        }

        public void Post(Action action) => work.Add(action);

        public void Dispose() => work.CompleteAdding();
    }
}
