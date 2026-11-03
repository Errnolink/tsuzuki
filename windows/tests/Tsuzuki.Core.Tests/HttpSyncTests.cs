using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using Google.Protobuf;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public class HttpSyncTests : IDisposable
{
    private const long MdSource = 2499283573021220255;

    private readonly (LibraryDatabase Db, SqliteLibraryStore Store) _library;
    private readonly SyncHub _hub;
    private readonly HttpClient _client;
    private readonly string _key;

    public HttpSyncTests()
    {
        _library = TestLib.NewLibrary();
        _hub = new SyncHub(_library.Db, _library.Store, preferredPort: 0);
        _hub.StartAsync().GetAwaiter().GetResult();
        _key = _hub.GetPairing().ApiKey;
        _client = new HttpClient { BaseAddress = new Uri($"http://127.0.0.1:{_hub.Status.Port}") };
    }

    public void Dispose()
    {
        _client.Dispose();
        _hub.Dispose();
        _library.Db.Dispose();
    }

    private HttpRequestMessage Merge(object body, string device, long cursor, bool full = false,
        string? deletedCategories = null, string? token = null)
    {
        var bytes = body switch
        {
            BackupMessage backup => backup.ToByteArray(),
            byte[] raw => raw,
            _ => Array.Empty<byte>(),
        };
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge");
        request.Headers.TryAddWithoutValidation("X-API-Token", token ?? _key);
        request.Headers.TryAddWithoutValidation("X-Device-ID", device);
        request.Headers.TryAddWithoutValidation("X-Device-Name", device + "-name");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", cursor.ToString());
        if (full) request.Headers.TryAddWithoutValidation("X-Sync-Full", "true");
        if (deletedCategories != null) request.Headers.TryAddWithoutValidation("X-Sync-Deleted-Categories", deletedCategories);
        request.Content = new ByteArrayContent(bytes);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        return request;
    }

    [Fact]
    public async Task CapabilitiesReportsV2()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/capabilities");
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        using var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<Dictionary<string, System.Text.Json.JsonElement>>();
        Assert.Equal(2, body["version"].GetInt32());
        Assert.Equal("/api/sync/v2/merge", body["merge"].GetString());
        Assert.Equal("/api/sync/v2/snapshot", body["snapshot"].GetString());
        Assert.True(body["seq"].ValueKind == System.Text.Json.JsonValueKind.Number, "capabilities must carry the store seq");
    }

    [Fact]
    public async Task VersionEndpointTracksStoreSeqAndRequiresAuth()
    {
        async Task<long> Version()
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/version");
            request.Headers.TryAddWithoutValidation("X-API-Token", _key);
            using var response = await _client.SendAsync(request);
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            Assert.Contains("application/json", response.Content.Headers.ContentType?.ToString() ?? "");
            var body = await response.Content.ReadFromJsonAsync<Dictionary<string, System.Text.Json.JsonElement>>();
            return body["seq"].GetInt64();
        }

        Assert.Equal(0, await Version());

        var first = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000)), "A", 0, full: true));
        Assert.Equal("1", first.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal(1, await Version());

        var second = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "One", version: 6, modified: 2000)), "B", 1));
        Assert.Equal("2", second.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal(2, await Version());
    }

    [Fact]
    public async Task PageTurnsWakeWaitingPhonesOnceAfterReadingPauses()
    {
        var mangaId = await _library.Store.UpsertFromSourceAsync(MdSource, new SManga("/manga/abc", "Title"));
        await _library.Store.SyncChaptersFromSourceAsync(mangaId, new[] { new SChapter("/chapter/1", "1") { ChapterNumber = 1 } });
        var chapter = (await _library.Store.GetChaptersAsync(mangaId)).Single();
        var since = await _library.Db.ReadAsync(db => _library.Db.GetSeq(db));

        var clock = System.Diagnostics.Stopwatch.StartNew();
        async Task<(long Seq, TimeSpan WokeAt)> Wait(int seconds)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, $"/api/sync/v2/version?since={since}&wait={seconds}");
            request.Headers.TryAddWithoutValidation("X-API-Token", _key);
            using var response = await _client.SendAsync(request);
            var body = await response.Content.ReadFromJsonAsync<Dictionary<string, System.Text.Json.JsonElement>>();
            return (body!["seq"].GetInt64(), clock.Elapsed);
        }

        var waiting = Wait(20);
        var lastPageAt = TimeSpan.Zero;
        for (var page = 1; page <= 6; page++)
        {
            await _library.Store.SetChapterProgressAsync(chapter.ChapterId, page, read: false);
            lastPageAt = clock.Elapsed;
            await Task.Delay(500);
        }
        var finalSeq = await _library.Db.ReadAsync(db => _library.Db.GetSeq(db));
        var (seq, wokeAt) = await waiting;

        Assert.Equal(finalSeq, seq);
        Assert.True(wokeAt >= lastPageAt, $"woke at {wokeAt.TotalSeconds:F1} s, before the last page turn at {lastPageAt.TotalSeconds:F1} s");
        Assert.True(wokeAt <= lastPageAt + TimeSpan.FromSeconds(6), $"woke at {wokeAt.TotalSeconds:F1} s, more than 6 s after the last page turn at {lastPageAt.TotalSeconds:F1} s");
    }

    [Fact]
    public async Task VersionEndpointRejectsMissingToken()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/version");
        using var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task QueryStringApiKeyDoesNotAuthenticate()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/version?apikey=" + Uri.EscapeDataString(_key));
        using var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task MissingTokenIsRejectedAsPlainText()
    {
        using var request = Merge(new BackupMessage(), "A", 0);
        request.Headers.Remove("X-API-Token");
        using var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Contains("text/plain", response.Content.Headers.ContentType?.ToString() ?? "");
    }

    [Fact]
    public async Task FullMergeFromFirstDeviceCarriesMandatoryHeaders()
    {
        var response = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000,
                TestLib.Chapter("/chapter/1", number: 1)))
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900)), "A", 0, full: true));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("1", response.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal("true", response.Headers.GetValues("X-Sync-Changed").Single());
        Assert.Equal("seq=1", response.Headers.GetValues("ETag").Single());
        Assert.Equal("application/octet-stream", response.Content.Headers.ContentType?.MediaType);
        Assert.NotNull(response.Content.Headers.ContentLength);
        Assert.False(response.Headers.Contains("X-Sync-Full-Requested"));
        var backup = BackupMessage.Parser.ParseFrom(await response.Content.ReadAsByteArrayAsync());
        Assert.True(TestLib.HasManga(backup, MdSource, "/manga/abc"));
    }

    [Fact]
    public async Task GzipBodyWithoutContentEncodingHeaderIsAccepted()
    {
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge");
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        request.Headers.TryAddWithoutValidation("X-Device-ID", "A");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", "0");
        request.Headers.TryAddWithoutValidation("X-Sync-Full", "true");
        request.Content = new ByteArrayContent(TestLib.Gzip(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/gz", "Gzip", version: 1, modified: 10)).ToByteArray()));
        var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var rendered = BackupMessage.Parser.ParseFrom(await response.Content.ReadAsByteArrayAsync());
        Assert.True(TestLib.HasManga(rendered, MdSource, "/manga/gz"));
    }

    [Fact]
    public async Task UnchangedMergeForGzipClientIsAValidGzipOrUnencodedReply()
    {
        var first = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000)), "A", 0, full: true));
        var cursor = first.Headers.GetValues("X-Sync-Cursor").Single();

        var request = Merge(new BackupMessage(), "A", long.Parse(cursor));
        request.Headers.TryAddWithoutValidation("Accept-Encoding", "gzip");
        var response = await _client.SendAsync(request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("false", response.Headers.GetValues("X-Sync-Changed").Single());

        var body = await response.Content.ReadAsByteArrayAsync();
        if (response.Content.Headers.ContentEncoding.Contains("gzip"))
        {
            Assert.True(body.Length >= 18 && body[0] == 0x1f && body[1] == 0x8b, $"gzip-labelled reply must be a complete gzip stream, got {body.Length} bytes");
        }
        else
        {
            Assert.Empty(BackupMessage.Parser.ParseFrom(body).BackupManga);
        }
    }

    [Fact]
    public async Task TwoDeviceRoundTrip()
    {
        var first = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000,
                TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
                TestLib.Chapter("/chapter/2", number: 2, version: 5, modified: 100)))
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900)), "A", 0, full: true));
        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal("1", first.Headers.GetValues("X-Sync-Cursor").Single());

        var join = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        Assert.Equal(HttpStatusCode.OK, join.StatusCode);
        Assert.Equal("true", join.Headers.GetValues("X-Sync-Changed").Single());
        var joinBody = BackupMessage.Parser.ParseFrom(await join.Content.ReadAsByteArrayAsync());
        Assert.True(TestLib.HasManga(joinBody, MdSource, "/manga/abc"));

        var updated = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 100,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
            TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 5000)));
        var bWrite = await _client.SendAsync(Merge(updated, "B", 1));
        Assert.Equal("2", bWrite.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal("false", bWrite.Headers.GetValues("X-Sync-Changed").Single());

        var aPoll = await _client.SendAsync(Merge(new BackupMessage(), "A", 1));
        Assert.Equal("true", aPoll.Headers.GetValues("X-Sync-Changed").Single());
        Assert.Equal("2", aPoll.Headers.GetValues("X-Sync-Cursor").Single());
        var pollBody = BackupMessage.Parser.ParseFrom(await aPoll.Content.ReadAsByteArrayAsync());
        var manga = TestLib.FindManga(pollBody, MdSource, "/manga/abc");
        Assert.Equal(2, manga.Chapters.Count);
        Assert.Equal(6, manga.Chapters.First(c => c.Url == "/chapter/2").Version);
    }

    [Fact]
    public async Task SelfEchoAndTombstoneIdempotence()
    {
        var payload = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000))
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900));
        var first = await _client.SendAsync(Merge(payload, "A", 0, full: true));
        Assert.Equal("1", first.Headers.GetValues("X-Sync-Cursor").Single());

        var echo = await _client.SendAsync(Merge(payload, "A", 1));
        Assert.Equal("false", echo.Headers.GetValues("X-Sync-Changed").Single());
        Assert.Equal("1", echo.Headers.GetValues("X-Sync-Cursor").Single());

        var tombstone = await _client.SendAsync(Merge(new BackupMessage(), "A", 1, deletedCategories: "3"));
        Assert.Equal("2", tombstone.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal("false", tombstone.Headers.GetValues("X-Sync-Changed").Single());

        var repeat = await _client.SendAsync(Merge(new BackupMessage(), "A", 2, deletedCategories: "3"));
        Assert.Equal("2", repeat.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal("false", repeat.Headers.GetValues("X-Sync-Changed").Single());

        var other = await _client.SendAsync(Merge(new BackupMessage(), "B", 1));
        Assert.Equal("true", other.Headers.GetValues("X-Sync-Changed").Single());
        var body = BackupMessage.Parser.ParseFrom(await other.Content.ReadAsByteArrayAsync());
        Assert.DoesNotContain(body.BackupCategories, c => c.Uid == 3);
    }

    [Fact]
    public async Task SnapshotEndpointReturns404ThenFullThen304()
    {
        using var before = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/snapshot");
        before.Headers.TryAddWithoutValidation("X-API-Token", _key);
        using var empty = await _client.SendAsync(before);
        Assert.Equal(HttpStatusCode.NotFound, empty.StatusCode);

        await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000)), "A", 0, full: true));

        using var full = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/snapshot");
        full.Headers.TryAddWithoutValidation("X-API-Token", _key);
        using var snapshot = await _client.SendAsync(full);
        Assert.Equal(HttpStatusCode.OK, snapshot.StatusCode);
        Assert.Equal("1", snapshot.Headers.GetValues("X-Sync-Cursor").Single());
        Assert.Equal("seq=1", snapshot.Headers.GetValues("ETag").Single());

        using var conditional = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/snapshot");
        conditional.Headers.TryAddWithoutValidation("X-API-Token", _key);
        conditional.Headers.TryAddWithoutValidation("X-Sync-Cursor", "1");
        using var notModified = await _client.SendAsync(conditional);
        Assert.Equal(HttpStatusCode.NotModified, notModified.StatusCode);
        Assert.Equal(0, (await notModified.Content.ReadAsByteArrayAsync()).Length);
    }

    [Fact]
    public async Task BadRequestsReturnSpecStatusCodes()
    {
        var noDevice = await _client.SendAsync(Merge(new BackupMessage(), "", 0));
        Assert.Equal(HttpStatusCode.BadRequest, noDevice.StatusCode);

        var badCursor = await _client.SendAsync(Merge(new BackupMessage(), "A", cursor: 0));
        // cursor validated via header; send explicit garbage instead
        using var cursorRequest = Merge(new BackupMessage(), "A", 0);
        cursorRequest.Headers.Remove("X-Sync-Cursor");
        cursorRequest.Headers.TryAddWithoutValidation("X-Sync-Cursor", "-1");
        using var badCursorResponse = await _client.SendAsync(cursorRequest);
        Assert.Equal(HttpStatusCode.BadRequest, badCursorResponse.StatusCode);

        using var badDeleted = Merge(new BackupMessage(), "A", 0, deletedCategories: "x");
        using var badDeletedResponse = await _client.SendAsync(badDeleted);
        Assert.Equal(HttpStatusCode.BadRequest, badDeletedResponse.StatusCode);

        var truncated = new byte[] { 0x0a, 0xff, 0x01 };
        var badBody = await _client.SendAsync(Merge(truncated, "A", 0));
        Assert.Equal(HttpStatusCode.BadRequest, badBody.StatusCode);
        Assert.Contains("not a valid backup", await badBody.Content.ReadAsStringAsync());

        using var noKey = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/capabilities");
        using var unauthorised = await _client.SendAsync(noKey);
        Assert.Equal(HttpStatusCode.Unauthorized, unauthorised.StatusCode);
    }

    [Fact]
    public async Task EventAndSnapshotDoNotRegisterDevices()
    {
        using var valid = new HttpRequestMessage(HttpMethod.Post, "/api/sync/event");
        valid.Headers.TryAddWithoutValidation("X-API-Token", _key);
        valid.Headers.TryAddWithoutValidation("X-Device-ID", "spoof");
        valid.Headers.TryAddWithoutValidation("X-Device-Name", "Spoof");
        valid.Content = new StringContent(
            """{"event":"SYNC_SUCCESS","device_id":"json-spoof","device_name":"Pixel","message":"ok"}""",
            Encoding.UTF8, "application/json");
        using var ok = await _client.SendAsync(valid);
        Assert.Equal(HttpStatusCode.NoContent, ok.StatusCode);

        using var unknown = new HttpRequestMessage(HttpMethod.Post, "/api/sync/event");
        unknown.Headers.TryAddWithoutValidation("X-API-Token", _key);
        unknown.Content = new StringContent("""{"event":"SYNC_WHATEVER"}""", Encoding.UTF8, "application/json");
        using var bad = await _client.SendAsync(unknown);
        Assert.Equal(HttpStatusCode.BadRequest, bad.StatusCode);

        using var garbage = new HttpRequestMessage(HttpMethod.Post, "/api/sync/event");
        garbage.Headers.TryAddWithoutValidation("X-API-Token", _key);
        garbage.Content = new StringContent("{oops", Encoding.UTF8, "application/json");
        using var badJson = await _client.SendAsync(garbage);
        Assert.Equal(HttpStatusCode.BadRequest, badJson.StatusCode);

        using var anonymous = new HttpRequestMessage(HttpMethod.Post, "/api/sync/event");
        anonymous.Content = new StringContent("""{"event":"SYNC_SUCCESS"}""", Encoding.UTF8, "application/json");
        using var unauthorised = await _client.SendAsync(anonymous);
        Assert.Equal(HttpStatusCode.Unauthorized, unauthorised.StatusCode);

        using var snapshot = new HttpRequestMessage(HttpMethod.Get, "/api/sync/v2/snapshot");
        snapshot.Headers.TryAddWithoutValidation("X-API-Token", _key);
        snapshot.Headers.TryAddWithoutValidation("X-Device-ID", "snapshot-spoof");
        using var snapshotResponse = await _client.SendAsync(snapshot);
        Assert.Equal(HttpStatusCode.NotFound, snapshotResponse.StatusCode);

        Assert.Empty(await _hub.GetDevicesAsync());
    }

    [Fact]
    public async Task OnlyMergesCreateDeviceRowsAndRowsAreCapped()
    {
        await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 1, modified: 10)), "A", 0, full: true));
        Assert.Equal("A", (await _hub.GetDevicesAsync()).Single().DeviceId);

        for (var i = 0; i < 12; i++)
        {
            await _client.SendAsync(Merge(new BackupMessage(), $"D{i}", 1));
            await Task.Delay(5);
        }

        var devices = (await _hub.GetDevicesAsync()).ToList();
        Assert.Equal(10, devices.Count);
        Assert.Contains(devices, d => d.DeviceId is "D2" or "D11");
        Assert.DoesNotContain(devices, d => d.DeviceId is "A" or "D0" or "D1");
    }

    [Fact]
    public async Task MergesRegisterDevicesWithCursors()
    {
        await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 1, modified: 10)), "A", 0, full: true));
        await _client.SendAsync(Merge(new BackupMessage(), "B", 0));

        var devices = (await _hub.GetDevicesAsync()).ToDictionary(d => d.DeviceId);
        Assert.Equal(2, devices.Count);
        Assert.Equal(1, devices["A"].Cursor);
        Assert.Equal(1, devices["B"].Cursor);
    }

    [Fact]
    public async Task RemoteProgressRaisesChangedEventWithHandoff()
    {
        var libraryChanged = new TaskCompletionSource<LibraryChangedEventArgs>(TaskCreationOptions.RunContinuationsAsynchronously);
        var deviceB = "B-device";
        var first = await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000,
                TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100))),
            "A", 0, full: true));
        Assert.Equal("1", first.Headers.GetValues("X-Sync-Cursor").Single());
        _library.Store.Changed += (_, args) => libraryChanged.TrySetResult(args);

        var payload = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 6, modified: 2000,
            TestLib.Chapter("/chapter/1", number: 1, read: true, page: 7, version: 6, modified: 2000)));
        var favouritePayload = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 6, modified: 2000,
            TestLib.Chapter("/chapter/1", number: 1, read: true, page: 7, version: 6, modified: 2000)));
        favouritePayload.BackupManga[0].Favorite = true;
        Assert.True(favouritePayload.BackupManga[0].HasFavorite);
        var bWrite = await _client.SendAsync(Merge(favouritePayload, deviceB, 1));
        Assert.Equal("2", bWrite.Headers.GetValues("X-Sync-Cursor").Single());

        var args = await libraryChanged.Task.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Equal(ChangeOrigin.Remote, args.Origin);
        Assert.Equal(deviceB + "-name", args.DeviceName);
        var library = await _library.Store.GetLibraryAsync();
        var entry = Assert.Single(library);
        var chapter = Assert.Single(await _library.Store.GetChaptersAsync(entry.MangaId));
        var handoff = Assert.Single(args.Handoffs);
        Assert.Equal(entry.MangaId, handoff.MangaId);
        Assert.Equal(chapter.ChapterId, handoff.ChapterId);
        Assert.True(handoff.Read);
        Assert.Equal(7, handoff.LastPageRead);
        Assert.Equal(deviceB + "-name", handoff.DeviceName);
    }

    [Fact]
    public async Task ThrottleBlocksWrongKeysUntilACorrectKeyResetsIt()
    {
        for (var attempt = 0; attempt < 10; attempt++)
        {
            using var wrong = await _client.SendAsync(Merge(new BackupMessage(), "T", 0, token: "wrong-" + attempt));
            Assert.Equal(HttpStatusCode.Unauthorized, wrong.StatusCode);
        }
        using (var locked = await _client.SendAsync(Merge(new BackupMessage(), "T", 0, token: "wrong-locked")))
        {
            Assert.Equal(HttpStatusCode.TooManyRequests, locked.StatusCode);
        }
        using (var correct = await _client.SendAsync(Merge(new BackupMessage(), "T", 0)))
        {
            Assert.Equal(HttpStatusCode.OK, correct.StatusCode);
        }
        using (var after = await _client.SendAsync(Merge(new BackupMessage(), "T", 0, token: "wrong-after-reset")))
        {
            Assert.Equal(HttpStatusCode.Unauthorized, after.StatusCode);
        }
    }
}
