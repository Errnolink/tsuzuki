using System.Net;
using System.Text;
using Google.Protobuf;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public class SyncEdgesTests : IDisposable
{
    private const long MdSource = 2499283573021220255;

    private readonly (LibraryDatabase Db, SqliteLibraryStore Store) _library;
    private readonly SyncHub _hub;
    private readonly HttpClient _client;
    private readonly string _key;

    public SyncEdgesTests()
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
        string? deletedCategories = null)
    {
        var bytes = body switch
        {
            BackupMessage backup => backup.ToByteArray(),
            byte[] raw => raw,
            _ => Array.Empty<byte>(),
        };
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/sync/v2/merge");
        request.Headers.TryAddWithoutValidation("X-API-Token", _key);
        request.Headers.TryAddWithoutValidation("X-Device-ID", device);
        request.Headers.TryAddWithoutValidation("X-Device-Name", device + "-name");
        request.Headers.TryAddWithoutValidation("X-Sync-Cursor", cursor.ToString());
        if (full) request.Headers.TryAddWithoutValidation("X-Sync-Full", "true");
        if (deletedCategories != null) request.Headers.TryAddWithoutValidation("X-Sync-Deleted-Categories", deletedCategories);
        request.Content = new ByteArrayContent(bytes);
        request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        return request;
    }

    private static BackupMessage Parse(HttpResponseMessage response) =>
        BackupMessage.Parser.ParseFrom(response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult());

    private static string Header(HttpResponseMessage response, string name) =>
        response.Headers.GetValues(name).Single();

    [Fact]
    public async Task RemoteProgressOnNonFavouriteRaisesNoHandoff()
    {
        var events = new List<LibraryChangedEventArgs>();
        _library.Store.Changed += (_, args) => events.Add(args);
        var initial = TestLib.Manga(MdSource, "/manga/nf", "NotFavourite", version: 5, modified: 1000,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100));
        initial.Favorite = false;
        await _client.SendAsync(Merge(TestLib.Backup(initial), "A", 0, full: true));

        var progressed = TestLib.Manga(MdSource, "/manga/nf", "NotFavourite", version: 6, modified: 2000,
            TestLib.Chapter("/chapter/1", number: 1, read: true, page: 5, version: 6, modified: 2000));
        progressed.Favorite = false;
        var response = await _client.SendAsync(Merge(TestLib.Backup(progressed), "B", 1));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Empty(events);
        var mangaId = (await _library.Store.FindMangaIdAsync(MdSource, "/manga/nf"))!.Value;
        var chapter = Assert.Single(await _library.Store.GetChaptersAsync(mangaId));
        Assert.True(chapter.Read);
        Assert.Equal(5, chapter.LastPageRead);
        Assert.DoesNotContain(await _library.Store.GetLibraryAsync(), e => e.MangaId == mangaId);
    }

    [Fact]
    public async Task FavouriteRemovedOnPhoneDropsFromLibraryButKeepsRows()
    {
        var favourite = TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100));
        favourite.Favorite = true;
        await _client.SendAsync(Merge(TestLib.Backup(favourite), "A", 0, full: true));
        var mangaId = (await _library.Store.FindMangaIdAsync(MdSource, "/manga/abc"))!.Value;
        Assert.Single(await _library.Store.GetLibraryAsync());

        var join = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        Assert.Equal("1", Header(join, "X-Sync-Cursor"));

        var removed = TestLib.Manga(MdSource, "/manga/abc", "One", version: 6, modified: 2000,
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100));
        removed.Favorite = false;
        var removal = await _client.SendAsync(Merge(TestLib.Backup(removed), "B", 1));
        Assert.Equal(HttpStatusCode.OK, removal.StatusCode);

        Assert.Empty(await _library.Store.GetLibraryAsync());
        Assert.NotNull(await _library.Store.FindMangaIdAsync(MdSource, "/manga/abc"));
        var chapter = Assert.Single(await _library.Store.GetChaptersAsync(mangaId));
        Assert.Equal("/chapter/1", chapter.Url);
        var row = (await _library.Db.ReadAsync(db => db.Query(
                "SELECT favorite FROM manga WHERE id = $id", ("$id", mangaId)).ToList()))
            .Single();
        Assert.False(row.Bool("favorite"));
    }

    [Fact]
    public async Task LocallyCreatedCategoryGetsIdentityAndRidesEveryDelta()
    {
        var created = await _library.Store.CreateCategoryAsync("Reading");
        Assert.True(created.Uid > 0);
        var row = (await _library.Db.ReadAsync(db => db.Query(
                "SELECT version, seq FROM category WHERE uid = $u", ("$u", created.Uid)).ToList()))
            .Single();
        Assert.True(row.Long("version") >= 1);
        Assert.Equal(1, row.Long("seq"));
        Assert.Equal(1, _library.Db.ReadAsync(db => db.Scalar<long>(
            "SELECT value FROM meta WHERE key = 'hub_seq'")).GetAwaiter().GetResult());

        var join = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        Assert.Equal("1", Header(join, "X-Sync-Cursor"));
        var joined = Parse(join);
        var category = Assert.Single(joined.BackupCategories);
        Assert.Equal(created.Uid, category.Uid);
        Assert.Equal("Reading", category.Name);
        Assert.True(category.Version >= 1);

        var poll = await _client.SendAsync(Merge(new BackupMessage(), "B", 1));
        Assert.Equal("false", Header(poll, "X-Sync-Changed"));
        Assert.Equal("1", Header(poll, "X-Sync-Cursor"));
        var delta = Parse(poll);
        Assert.Empty(delta.BackupManga);
        var deltaCategory = Assert.Single(delta.BackupCategories);
        Assert.Equal(created.Uid, deltaCategory.Uid);
    }

    [Fact]
    public async Task CategoryTombstoneAppliedTwiceIsIdempotentAndStaysGone()
    {
        var payload = TestLib.Backup(
                TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000).WithCategories(0))
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900));
        await _client.SendAsync(Merge(payload, "A", 0, full: true));

        var first = await _client.SendAsync(Merge(new BackupMessage(), "A", 1, deletedCategories: "3"));
        Assert.Equal("2", Header(first, "X-Sync-Cursor"));
        var repeat = await _client.SendAsync(Merge(new BackupMessage(), "A", 2, deletedCategories: "3"));
        Assert.Equal("2", Header(repeat, "X-Sync-Cursor"));
        Assert.Equal("false", Header(repeat, "X-Sync-Changed"));

        var fresh = await _client.SendAsync(Merge(new BackupMessage(), "C", 0));
        var rendered = Parse(fresh);
        Assert.DoesNotContain(rendered.BackupCategories, c => c.Uid == 3);
        Assert.All(rendered.BackupManga, m => Assert.DoesNotContain(m.Categories, order => order == 0));
        Assert.Empty(await _library.Store.GetCategoriesAsync());
    }

    [Fact]
    public async Task UnknownTopLevelFieldsSurviveUploadAndNextFullRender()
    {
        var backup = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 1, modified: 10));
        var bytes = AppendField(AppendField(backup.ToByteArray(), 610, "komikku-feeds"), 700, "wanted-extensions");
        var upload = await _client.SendAsync(Merge(bytes, "A", 0));
        Assert.Equal(HttpStatusCode.OK, upload.StatusCode);

        var full = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        var rendered = Parse(full);
        Assert.Equal("komikku-feeds", ReadUnknownField(rendered, 610));
        Assert.Equal("wanted-extensions", ReadUnknownField(rendered, 700));
        Assert.True(TestLib.HasManga(rendered, MdSource, "/manga/abc"));
    }

    [Fact]
    public async Task NoChangeDeltaFromSecondDeviceReceivesNothingWithCursorAndUnchanged()
    {
        await _client.SendAsync(Merge(TestLib.Backup(
            TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000)), "A", 0, full: true));

        var join = await _client.SendAsync(Merge(new BackupMessage(), "B", 0));
        Assert.Equal("true", Header(join, "X-Sync-Changed"));
        Assert.Equal("1", Header(join, "X-Sync-Cursor"));

        var poll = await _client.SendAsync(Merge(new BackupMessage(), "B", 1));
        Assert.Equal("1", Header(poll, "X-Sync-Cursor"));
        Assert.Equal("false", Header(poll, "X-Sync-Changed"));
        var body = Parse(poll);
        Assert.Empty(body.BackupManga);
        Assert.Empty(body.BackupCategories);
    }

    private static byte[] AppendField(byte[] messageBytes, int fieldNumber, string value)
    {
        var tag = (uint)((fieldNumber << 3) | 2);
        using var sink = new MemoryStream();
        WriteVarint(sink, tag);
        WriteVarint(sink, (ulong)Encoding.UTF8.GetByteCount(value));
        sink.Write(Encoding.UTF8.GetBytes(value));
        using var output = new MemoryStream();
        output.Write(messageBytes);
        output.Write(sink.ToArray());
        return output.ToArray();
    }

    private static void WriteVarint(MemoryStream sink, ulong value)
    {
        while (true)
        {
            if ((value & ~0x7FUL) == 0)
            {
                sink.WriteByte((byte)value);
                return;
            }
            sink.WriteByte((byte)((value & 0x7F) | 0x80));
            value >>= 7;
        }
    }

    private static string ReadUnknownField(Google.Protobuf.IMessage message, int fieldNumber)
    {
        var raw = SyncRows.ExtractUnknownFields(message.ToByteArray());
        var bytes = raw.Where(f => f.FieldNumber == fieldNumber).Select(f => f.WireBytes).FirstOrDefault();
        if (bytes == null) return "";
        var input = new CodedInputStream(bytes);
        input.ReadTag();
        return input.ReadString();
    }
}
