using System.Net;
using System.Text;
using System.Text.Json.Nodes;
using Google.Protobuf;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Tsuzuki.Core.Tracking;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public sealed class TrackingTests : IDisposable
{
    private const long MdSource = 2499283573021220255;
    private const string MangaUrl = "/manga/58be6aa6-06cb-4ca5-bd20-f1392ce451fb";

    private readonly (LibraryDatabase Db, SqliteLibraryStore Store) library = TestLib.NewLibrary();
    private readonly SyncHub hub;
    private readonly HttpClient client;
    private readonly string key;

    public TrackingTests()
    {
        hub = new SyncHub(library.Db, library.Store, preferredPort: 0);
        hub.StartAsync().GetAwaiter().GetResult();
        key = hub.GetPairing().ApiKey;
        client = new HttpClient { BaseAddress = new Uri($"http://127.0.0.1:{hub.Status.Port}") };
    }

    public void Dispose()
    {
        client.Dispose();
        hub.Dispose();
        library.Db.Dispose();
    }

    private async Task PushFromPhoneAsync(BackupManga manga, long cursor, bool full)
    {
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge");
        request.Headers.TryAddWithoutValidation("X-API-Token", key);
        request.Headers.TryAddWithoutValidation("X-Device-ID", "phone");
        request.Headers.TryAddWithoutValidation("X-Device-Name", "Phone");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", cursor.ToString());
        if (full) request.Headers.TryAddWithoutValidation("X-Sync-Full", "true");
        request.Content = new ByteArrayContent(TestLib.Backup(manga).ToByteArray());
        request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        using var response = await client.SendAsync(request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    private static BackupManga Tracked(int version, long modified, params BackupTracking[] tracks)
    {
        var manga = TestLib.Manga(MdSource, MangaUrl, "Yotsuba", version: version, modified: modified,
            TestLib.Chapter("/chapter/1", number: 1, version: 1, modified: 10));
        manga.Favorite = true;
        manga.Tracking.AddRange(tracks);
        return manga;
    }

    private static BackupTracking AniList(float chapter, long started = 0) =>
        new() { SyncId = TrackerIds.AniList, MediaId = 104, Title = "Yotsuba&!", LastChapterRead = chapter, Status = 1, StartedReadingDate = started };

    private TrackService Service(params ITracker[] trackers) => new(library.Db, library.Store, trackers);

    private async Task<long> MangaIdAsync() => (await library.Store.FindMangaIdAsync(MdSource, MangaUrl))!.Value;

    [Fact]
    public async Task Reading_on_the_pc_moves_every_bound_tracker_forward_for_the_phone_to_see()
    {
        await PushFromPhoneAsync(Tracked(1, 1000, AniList(3)), 0, full: true);
        var id = await MangaIdAsync();

        await Service().ChapterReadAsync(id, 5);
        await Service().ChapterReadAsync(id, 4);

        var track = Assert.Single(await library.Store.GetTrackingAsync(id));
        Assert.Equal(5f, track.LastChapterRead);
        Assert.NotEqual(0, track.StartedReadingDate);
    }

    [Fact]
    public async Task A_stale_phone_copy_with_lower_progress_does_not_lower_the_pc_value()
    {
        await PushFromPhoneAsync(Tracked(1, 1000, AniList(3, started: 500)), 0, full: true);
        var id = await MangaIdAsync();
        await Service().ChapterReadAsync(id, 9);

        await PushFromPhoneAsync(Tracked(50, 9_999_999_999, AniList(4, started: 900)), 1, full: false);

        var track = Assert.Single(await library.Store.GetTrackingAsync(id));
        Assert.Equal(9f, track.LastChapterRead);
        Assert.Equal(500, track.StartedReadingDate);
    }

    [Fact]
    public async Task Self_hosted_tracker_entries_survive_a_pc_write_unchanged()
    {
        var komga = new BackupTracking { SyncId = 6, MediaId = 777, Title = "Yotsuba", LastChapterRead = 2, Score = 8, Status = 2, TrackingUrl = "https://komga.home/api/v1/series/0A" };
        await PushFromPhoneAsync(Tracked(1, 1000, AniList(3), komga), 0, full: true);
        var id = await MangaIdAsync();
        var before = (await library.Store.GetTrackingAsync(id)).Single(t => t.SyncId == 6).ToByteArray();

        await Service().ChapterReadAsync(id, 6);

        var tracks = await library.Store.GetTrackingAsync(id);
        Assert.Equal(before, tracks.Single(t => t.SyncId == 6).ToByteArray());
        Assert.Equal(6f, tracks.Single(t => t.SyncId == TrackerIds.AniList).LastChapterRead);
    }

    [Fact]
    public async Task Finishing_the_last_chapter_marks_the_entry_completed_and_pushes_it()
    {
        var web = new FakeMangaUpdates();
        var tracker = web.Tracker();
        var bound = new BackupTracking { SyncId = TrackerIds.MangaUpdates, MediaId = 42, Title = "Yotsuba", LastChapterRead = 9, TotalChapters = 10, Status = MangaUpdatesTracker.Wish };
        await PushFromPhoneAsync(Tracked(1, 1000, bound), 0, full: true);
        var id = await MangaIdAsync();

        await Service(tracker).ChapterReadAsync(id, 10);

        var track = Assert.Single(await library.Store.GetTrackingAsync(id));
        Assert.Equal(MangaUpdatesTracker.Complete, track.Status);
        Assert.NotEqual(0, track.FinishedReadingDate);
        var update = Assert.Single(web.Updates);
        Assert.Equal(10, update[0]!["status"]!["chapter"]!.GetValue<int>());
        Assert.Equal(MangaUpdatesTracker.Complete, update[0]!["list_id"]!.GetValue<int>());
    }

    [Fact]
    public async Task Updates_that_fail_offline_are_kept_and_sent_once_the_site_answers()
    {
        var web = new FakeMangaUpdates { Offline = true };
        var bound = new BackupTracking { SyncId = TrackerIds.MangaUpdates, MediaId = 42, LastChapterRead = 1, Status = MangaUpdatesTracker.Reading };
        await PushFromPhoneAsync(Tracked(1, 1000, bound), 0, full: true);
        var id = await MangaIdAsync();
        var clock = new Clock();
        var service = new TrackService(library.Db, library.Store, [web.Tracker()], clock);

        await service.ChapterReadAsync(id, 2);
        await service.ChapterReadAsync(id, 3);
        Assert.Equal(1, await service.PendingAsync());

        web.Offline = false;
        clock.Advance(TimeSpan.FromHours(1));
        await service.DrainAsync();

        Assert.Equal(0, await service.PendingAsync());
        Assert.Equal(3, Assert.Single(web.Updates)[0]!["status"]!["chapter"]!.GetValue<int>());
    }

    private sealed class Clock : TimeProvider
    {
        private DateTimeOffset now = new(2025, 11, 6, 12, 0, 0, TimeSpan.Zero);

        public override DateTimeOffset GetUtcNow() => now;

        public void Advance(TimeSpan by) => now += by;
    }

    private sealed class MemoryCredentials : ITrackerCredentials
    {
        private readonly Dictionary<int, string> secrets = [];

        public string? Read(int trackerId) => secrets.GetValueOrDefault(trackerId);

        public void Write(int trackerId, string? secret)
        {
            if (secret is null) secrets.Remove(trackerId);
            else secrets[trackerId] = secret;
        }
    }

    private sealed class FakeMangaUpdates : HttpMessageHandler
    {
        public bool Offline { get; set; }

        public List<JsonArray> Updates { get; } = [];

        public MangaUpdatesTracker Tracker()
        {
            var credentials = new MemoryCredentials();
            credentials.Write(TrackerIds.MangaUpdates, "token");
            return new MangaUpdatesTracker(new HttpClient(this), credentials);
        }

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (Offline)
            {
                throw new HttpRequestException("offline");
            }
            if (request.RequestUri!.AbsolutePath == "/v1/lists/series/update")
            {
                Updates.Add(JsonNode.Parse(await request.Content!.ReadAsStringAsync(cancellationToken))!.AsArray());
            }
            return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("{}", Encoding.UTF8, "application/json") };
        }
    }
}
