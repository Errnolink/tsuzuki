using System.Text;
using Google.Protobuf;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Storage;
using Tsuzuki.Core.Sync;
using Xunit;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Tests;

public class MergeBehaviourTests
{
    private const long MdSource = 2499283573021220255;

    private static (LibraryDatabase Db, SyncEngine Engine) NewEngine()
    {
        var (db, _) = TestLib.NewLibrary();
        return (db, new SyncEngine(db));
    }

    private static IReadOnlyList<long> NoDeleted() => Array.Empty<long>();

    private static BackupMessage RenderAll(LibraryDatabase db) =>
        db.ReadAsync(connection => SyncRows.Render(new ItemStore(db).AllItems(connection))).GetAwaiter().GetResult();

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

    [Fact]
    public async Task ColdFullUploadStartsTheStore()
    {
        var (db, engine) = NewEngine();
        var result = await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "One", version: 5, modified: 1000,
                TestLib.Chapter("/chapter/1", number: 1)))
                .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900)),
            clientCursor: 0, forceFull: true, NoDeleted());

        Assert.Equal(1, result.Cursor);
        Assert.True(result.Changed);
        Assert.False(result.FullRequested);
        var manga = TestLib.FindManga(TestLib.BackupOf(result), MdSource, "/manga/abc");
        Assert.Equal("One", manga.Title);
        Assert.Single(manga.Chapters);
        Assert.Contains(TestLib.BackupOf(result).BackupCategories, c => c.Uid == 3);
        Assert.Contains(TestLib.BackupOf(result).BackupSources, s => s.SourceId == MdSource);
    }

    [Fact]
    public async Task SecondDeviceColdJoinReceivesFullStore()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000)), 0, true, NoDeleted());

        var join = await engine.Merge("B", "Desktop", new BackupMessage(), 0, false, NoDeleted());
        Assert.True(join.Changed);
        Assert.Equal(1, join.Cursor);
        Assert.True(TestLib.HasManga(TestLib.BackupOf(join), MdSource, "/manga/abc"));
    }

    [Fact]
    public async Task NoOpEchoLeavesStoreUnchanged()
    {
        var (db, engine) = NewEngine();
        var payload = TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000))
            .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900));
        var first = await engine.Merge("A", "Android", payload, 0, true, NoDeleted());
        Assert.Equal(1, first.Cursor);

        var echo = await engine.Merge("A", "Android", payload, first.Cursor, false, NoDeleted());
        Assert.Equal(first.Cursor, echo.Cursor);
        Assert.False(echo.Changed);
        Assert.False(TestLib.HasManga(TestLib.BackupOf(echo), MdSource, "/manga/abc"));
        Assert.Contains(TestLib.BackupOf(echo).BackupCategories, c => c.Uid == 3);
    }

    [Fact]
    public async Task ChapterChangeIsDeliveredAsWholeMangaToOtherDevice()
    {
        var (db, engine) = NewEngine();
        var chapters = new[]
        {
            TestLib.Chapter("/chapter/1", number: 1, version: 5, modified: 100),
            TestLib.Chapter("/chapter/2", number: 2, version: 5, modified: 100),
        };
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 100, chapters)), 0, true, NoDeleted());

        var changed = new[]
        {
            chapters[0],
            TestLib.Chapter("/chapter/2", number: 2, version: 6, modified: 2000),
        };
        var own = await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 100, changed)), 1, false, NoDeleted());
        Assert.Equal(2, own.Cursor);
        Assert.False(own.Changed);

        var other = await engine.Merge("B", "Desktop", new BackupMessage(), 1, false, NoDeleted());
        Assert.True(other.Changed);
        var manga = TestLib.FindManga(TestLib.BackupOf(other), MdSource, "/manga/abc");
        Assert.Equal(2, manga.Chapters.Count);
        Assert.Contains(manga.Chapters, c => c.Url == "/chapter/1");
        Assert.Contains(manga.Chapters, c => c.Url == "/chapter/2" && c.Version == 6);
    }

    [Fact]
    public async Task ReadChaptersWithoutHistoryStillAdvanceTheContinueShelf()
    {
        var (db, store) = TestLib.NewLibrary();
        var engine = new SyncEngine(db);
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Tales", version: 5, modified: 1000,
                TestLib.Chapter("/chapter/533", number: 533, read: true, version: 2, modified: 2000),
                TestLib.Chapter("/chapter/533.5", number: 533.5, read: true, version: 2, modified: 3000))),
            0, true, NoDeleted());

        var entry = Assert.Single(await store.GetContinueReadingAsync(10));
        Assert.Equal(533.5, entry.ChapterNumber);
    }

    [Fact]
    public async Task LowerVersionLosesAndReceivesServerCopy()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Server Title", version: 5, modified: 1000)), 0, true, NoDeleted());

        var stale = await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Stale Title", version: 4, modified: 5000)), 1, false, NoDeleted());

        Assert.True(stale.Changed);
        Assert.Equal(1, stale.Cursor);
        var manga = TestLib.FindManga(TestLib.BackupOf(stale), MdSource, "/manga/abc");
        Assert.Equal("Server Title", manga.Title);
        Assert.Equal(5, manga.Version);
    }

    [Fact]
    public async Task EqualVersionBreaksTieByModificationTime()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "X", version: 7, modified: 3000)), 0, true, NoDeleted());

        var older = await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Y", version: 7, modified: 2500)), 1, false, NoDeleted());
        Assert.Equal("X", TestLib.FindManga(TestLib.BackupOf(older), MdSource, "/manga/abc").Title);
        Assert.True(older.Changed);

        var fullTie = await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Z", version: 7, modified: 3000)), 1, false, NoDeleted());
        Assert.Equal(1, fullTie.Cursor);
        Assert.True(fullTie.Changed);
        Assert.Equal("X", TestLib.FindManga(TestLib.BackupOf(fullTie), MdSource, "/manga/abc").Title);
    }

    [Fact]
    public async Task NewerModificationTimeWinsTheTie()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Y", version: 7, modified: 2500)), 0, true, NoDeleted());

        var newer = await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "X", version: 7, modified: 3000)), 1, false, NoDeleted());
        Assert.Equal(2, newer.Cursor);

        var bPoll = await engine.Merge("B", "Desktop", new BackupMessage(), 1, false, NoDeleted());
        Assert.Equal("X", TestLib.FindManga(TestLib.BackupOf(bPoll), MdSource, "/manga/abc").Title);
        Assert.True(bPoll.Changed);
    }

    [Fact]
    public async Task CategoryTombstoneDeletesAndStaysIdempotent()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000).WithCategories(0))
                .WithCategories(TestLib.Category("Reading", 3, 0, version: 2, modified: 900)),
            0, true, NoDeleted());

        var tombstone = await engine.Merge("A", "Android", new BackupMessage(), 1, false, new[] { 3L });
        Assert.Equal(2, tombstone.Cursor);

        var other = await engine.Merge("B", "Desktop", new BackupMessage(), 0, false, NoDeleted());
        Assert.True(other.Changed);
        Assert.DoesNotContain(TestLib.BackupOf(other).BackupCategories, c => c.Uid == 3);
        Assert.All(TestLib.BackupOf(other).BackupManga, m => Assert.DoesNotContain(m.Categories, order => order == 0));

        var repeat = await engine.Merge("A", "Android", new BackupMessage(), 2, false, new[] { 3L });
        Assert.Equal(2, repeat.Cursor);
        Assert.False(repeat.Changed);
    }

    [Fact]
    public async Task UidLessCategoryMergesByNameIntoUidKey()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 1, modified: 10).WithCategories(5))
                .WithCategories(TestLib.Category("Reading", 3, 5, version: 2, modified: 900)),
            0, true, NoDeleted());

        var result = await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/def", "Manga", version: 1, modified: 10).WithCategories(7))
                .WithCategories(TestLib.Category("Reading", 0, 7, version: 2, modified: 900)),
            0, true, NoDeleted());

        Assert.Contains(TestLib.BackupOf(result).BackupCategories, c => c.Name == "Reading" && c.Uid == 3);
        Assert.Single(RenderAll(db).BackupCategories);
    }

    [Fact]
    public async Task CursorAheadOfSeqForcesFullResponse()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000)), 0, true, NoDeleted());

        var ahead = await engine.Merge("A", "Android", new BackupMessage(), 99, false, NoDeleted());
        Assert.True(ahead.FullRequested);
        Assert.Equal(1, ahead.Cursor);
        Assert.True(ahead.Changed);
        Assert.True(TestLib.HasManga(TestLib.BackupOf(ahead), MdSource, "/manga/abc"));
    }

    [Fact]
    public async Task EmptyBodyIsAPurePoll()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000)), 0, true, NoDeleted());
        await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(1, "/manga/other", "Manga", version: 1, modified: 700)), 1, false, NoDeleted());

        var poll = await engine.Merge("A", "Android", new BackupMessage(), 1, false, NoDeleted());
        Assert.Equal(2, poll.Cursor);
        Assert.True(poll.Changed);
        Assert.True(TestLib.HasManga(TestLib.BackupOf(poll), 1, "/manga/other"));
        Assert.False(TestLib.HasManga(TestLib.BackupOf(poll), MdSource, "/manga/abc"));
    }

    [Fact]
    public async Task AbsenceNeverDeletes()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup(
                TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 5, modified: 1000),
                TestLib.Manga(MdSource, "/manga/gone", "Manga", version: 5, modified: 1000)),
            0, true, NoDeleted());

        await engine.Merge("B", "Desktop",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 6, modified: 2000)), 1, false, NoDeleted());

        Assert.True(TestLib.HasManga(RenderAll(db), MdSource, "/manga/gone"));
    }

    [Fact]
    public async Task UnknownFieldsRoundTripThroughTheStore()
    {
        var (db, engine) = NewEngine();
        var manga = TestLib.Manga(MdSource, "/manga/abc", "WithExtras", version: 1, modified: 10);
        var mangaBytes = AppendField(manga.ToByteArray(), 900, "merged-note");
        var chapter = TestLib.Chapter("/chapter/1", number: 1);
        var chapterBytes = AppendField(chapter.ToByteArray(), 905, "local-note");
        var fullManga = BackupManga.Parser.ParseFrom(mangaBytes);
        var fullChapter = BackupChapter.Parser.ParseFrom(chapterBytes);
        fullManga.Chapters.Add(fullChapter);

        var backup = new BackupMessage();
        backup.BackupManga.Add(fullManga);
        var backupBytes = backup.ToByteArray();
        var stream = new MemoryStream();
        stream.Write(AppendField(backupBytes, 610, "komikku-feeds"));
        stream.Write(AppendField(Array.Empty<byte>(), 700, "wanted-extensions"));
        var incoming = BackupMessage.Parser.ParseFrom(stream.ToArray());

        await engine.Merge("A", "Android", incoming, 0, true, NoDeleted());

        var rendered = RenderAll(db);
        Assert.Equal("komikku-feeds", ReadUnknownField(rendered, 610));
        Assert.Equal("wanted-extensions", ReadUnknownField(rendered, 700));
        var outManga = TestLib.FindManga(rendered, MdSource, "/manga/abc");
        Assert.Equal("merged-note", ReadUnknownField(outManga, 900));
        Assert.Single(outManga.Chapters);
        Assert.Equal("local-note", ReadUnknownField(outManga.Chapters[0], 905));
    }

    [Fact]
    public async Task RepeatedTombstoneHeadersDoNotBumpSeq()
    {
        var (db, engine) = NewEngine();
        await engine.Merge("A", "Android",
            TestLib.Backup().WithCategories(TestLib.Category("Read", 9, 0, version: 1, modified: 10)), 0, true, NoDeleted());

        var first = await engine.Merge("A", "Android", new BackupMessage(), 1, false, new[] { 9L });
        Assert.Equal(2, first.Cursor);
        var second = await engine.Merge("A", "Android", new BackupMessage(), first.Cursor, false, new[] { 9L });
        Assert.Equal(first.Cursor, second.Cursor);
        var third = await engine.Merge("A", "Android", new BackupMessage(), second.Cursor, false, new[] { 9L });
        Assert.Equal(second.Cursor, third.Cursor);
        Assert.False(third.Changed);
    }

    [Fact]
    public async Task DeviceKeyFallsBackToName()
    {
        var (db, engine) = NewEngine();
        var first = await engine.Merge("", "SoloDevice",
            TestLib.Backup(TestLib.Manga(MdSource, "/manga/abc", "Manga", version: 1, modified: 10)), 0, true, NoDeleted());
        Assert.Equal(1, first.Cursor);
        var again = await engine.Merge("", "SoloDevice", new BackupMessage(), 1, false, NoDeleted());
        Assert.False(again.Changed);
    }

    [Fact]
    public async Task PreferenceWithEmptyValueKeepsItsValueFieldOnTheWayBack()
    {
        var (db, engine) = NewEngine();
        static byte[] Field(int number, byte[] payload)
        {
            using var stream = new MemoryStream();
            var output = new CodedOutputStream(stream);
            output.WriteTag(number, WireFormat.WireType.LengthDelimited);
            output.WriteBytes(ByteString.CopyFrom(payload));
            output.Flush();
            return stream.ToArray();
        }
        var kotlinFalseValue = Field(1, Encoding.UTF8.GetBytes("eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue"))
            .Concat(Field(2, []))
            .ToArray();
        var preferenceBytes = Field(1, Encoding.UTF8.GetBytes("pref_show_nsfw")).Concat(Field(2, kotlinFalseValue)).ToArray();
        var incoming = BackupMessage.Parser.ParseFrom(Field(104, preferenceBytes));

        await engine.Merge("A", "Android", incoming, 0, true, NoDeleted());
        var preference = Assert.Single(RenderAll(db).BackupPreferences);

        var valueBytes = preference.Value.ToByteArray();
        Assert.Equal(new byte[] { 0x12, 0x00 }, valueBytes[^2..]);
    }
}
