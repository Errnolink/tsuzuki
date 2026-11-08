using Google.Protobuf;
using Tsuzuki.Core.Proto;
using BackupMessage = Tsuzuki.Core.Proto.Backup;

namespace Tsuzuki.Core.Storage;

internal enum SyncKind
{
    Manga,
    Chapter,
    Category,
    Source,
    AppPref,
    SourcePref,
    ExtStore,
    SavedSearch,
    Field,
}

internal sealed record SyncItem
{
    public required SyncKind Kind { get; init; }
    public required string Key { get; init; }
    public string ParentKey { get; init; } = "";
    public string Name { get; init; } = "";
    public long Version { get; init; }
    public long ModifiedAt { get; init; }
    public bool Deleted { get; init; }
    public IReadOnlyList<string> Refs { get; init; } = Array.Empty<string>();
    public required byte[] Payload { get; init; }
    public long Seq { get; init; }
    public string? Origin { get; init; }

    public bool IsVersioned => Kind is SyncKind.Manga or SyncKind.Chapter or SyncKind.Category;
    public bool IsField => Kind == SyncKind.Field;
}

internal static class SyncRows
{
    public const string MergedSourceId = "6969";
    private const char UnitSeparator = '\u001f';

    public static string MangaKey(long source, string url) => source + "|" + url;

    public static (long Source, string Url)? MangaKeyParts(string key)
    {
        var i = key.IndexOf('|');
        if (i < 0 || !long.TryParse(key[..i], out var source)) return null;
        return (source, key[(i + 1)..]);
    }

    public static string ChapterKey(string mangaKey, string url) => mangaKey + UnitSeparator + url;

    public static (string MangaKey, string Url)? ChapterKeyParts(string key)
    {
        var i = key.IndexOf(UnitSeparator);
        if (i < 0) return null;
        return (key[..i], key[(i + 1)..]);
    }

    public static string CategoryKey(BackupCategory category) =>
        category.Uid != 0 ? "uid:" + category.Uid : "name:" + category.Name;

    public static string SavedSearchKey(BackupSavedSearch search) => search.Name + "|" + search.Source;

    public static string SourcePrefKey(string sourceKey, string prefKey) => sourceKey + UnitSeparator + prefKey;

    public static byte[] Marshal(IMessage message) => message.ToByteArray();

    public static bool PayloadEquals(SyncKind kind, byte[] a, byte[] b)
    {
        if (a.AsSpan().SequenceEqual(b)) return true;
        if (kind is not (SyncKind.Manga or SyncKind.Chapter or SyncKind.Category)) return false;
        return MarshalWithoutTimestamps(kind, a).AsSpan().SequenceEqual(MarshalWithoutTimestamps(kind, b));
    }

    private static byte[] MarshalWithoutTimestamps(SyncKind kind, byte[] payload)
    {
        IMessage cleared = kind switch
        {
            SyncKind.Manga => Parse<BackupManga>(payload),
            SyncKind.Chapter => Parse<BackupChapter>(payload),
            _ => Parse<BackupCategory>(payload),
        };
        switch (cleared)
        {
            case BackupManga m:
                m.LastModifiedAt = 0;
                m.ClearFavoriteModifiedAt();
                break;
            case BackupChapter c:
                c.LastModifiedAt = 0;
                break;
            case BackupCategory cat:
                cat.LastModifiedAt = 0;
                break;
        }
        return Marshal(cleared);
    }

    private static T Parse<T>(byte[] payload) where T : IMessage<T>, new() =>
        new MessageParser<T>(() => new T()).ParseFrom(payload);

    public static List<SyncItem> Split(BackupMessage backup, ReadOnlyMemory<byte>? raw = null)
    {
        var items = new List<SyncItem>();
        var orderToKey = new Dictionary<long, string>();
        foreach (var category in backup.BackupCategories)
        {
            var key = CategoryKey(category);
            orderToKey[category.Order] = key;
            items.Add(new SyncItem
            {
                Kind = SyncKind.Category,
                Key = key,
                Name = category.Name,
                Version = category.Version,
                ModifiedAt = category.LastModifiedAt,
                Payload = Marshal(category),
            });
        }

        foreach (var manga in backup.BackupManga)
        {
            if (manga.Url.Length == 0) continue;
            var key = MangaKey(manga.Source, manga.Url);
            var refs = new List<string>();
            foreach (var order in manga.Categories)
            {
                if (orderToKey.TryGetValue(order, out var categoryKey)) refs.Add(categoryKey);
            }
            items.Add(new SyncItem
            {
                Kind = SyncKind.Manga,
                Key = key,
                Version = manga.Version,
                ModifiedAt = manga.LastModifiedAt,
                Refs = refs,
                Payload = Marshal(StripManga(manga)),
            });

            foreach (var chapter in manga.Chapters)
            {
                if (chapter.Url.Length == 0) continue;
                items.Add(new SyncItem
                {
                    Kind = SyncKind.Chapter,
                    Key = ChapterKey(key, chapter.Url),
                    ParentKey = key,
                    Version = chapter.Version,
                    ModifiedAt = chapter.LastModifiedAt,
                    Payload = Marshal(chapter),
                });
            }
        }

        foreach (var source in backup.BackupSources)
        {
            items.Add(new SyncItem { Kind = SyncKind.Source, Key = source.SourceId.ToString(), Payload = Marshal(source) });
        }
        foreach (var preference in backup.BackupPreferences)
        {
            items.Add(new SyncItem { Kind = SyncKind.AppPref, Key = preference.Key, Payload = Marshal(preference) });
        }
        foreach (var group in backup.BackupSourcePreferences)
        {
            foreach (var preference in group.Prefs)
            {
                items.Add(new SyncItem
                {
                    Kind = SyncKind.SourcePref,
                    Key = SourcePrefKey(group.SourceKey, preference.Key),
                    ParentKey = group.SourceKey,
                    Payload = Marshal(preference),
                });
            }
        }
        foreach (var store in backup.BackupExtensionStores)
        {
            items.Add(new SyncItem { Kind = SyncKind.ExtStore, Key = store.IndexUrl, Payload = Marshal(store) });
        }
        foreach (var search in backup.BackupSavedSearches)
        {
            items.Add(new SyncItem { Kind = SyncKind.SavedSearch, Key = SavedSearchKey(search), Payload = Marshal(search) });
        }
        var fieldSource = raw.HasValue ? raw.Value : (ReadOnlyMemory<byte>)backup.ToByteArray();
        foreach (var field in ExtractUnknownFields(fieldSource))
        {
            items.Add(new SyncItem { Kind = SyncKind.Field, Key = "field:" + field.FieldNumber, Payload = field.WireBytes });
        }

        return Dedupe(items);
    }

    public static BackupManga StripManga(BackupManga manga)
    {
        var stripped = manga.Clone();
        stripped.Chapters.Clear();
        stripped.Categories.Clear();
        return stripped;
    }

    private static List<SyncItem> Dedupe(List<SyncItem> items)
    {
        var index = new Dictionary<(SyncKind, string), int>();
        var output = new List<SyncItem>();
        foreach (var item in items)
        {
            var id = (item.Kind, item.Key);
            if (index.TryGetValue(id, out var position))
            {
                var current = output[position];
                if (item.Version > current.Version
                    || (item.Version == current.Version && item.ModifiedAt >= current.ModifiedAt))
                {
                    output[position] = item;
                }
                continue;
            }
            index[id] = output.Count;
            output.Add(item);
        }
        return output;
    }

    public static BackupMessage Render(IEnumerable<SyncItem> items)
    {
        var list = items.ToList();
        var backup = new BackupMessage();

        var categories = new List<BackupCategory>();
        foreach (var item in list)
        {
            if (item.Kind != SyncKind.Category || item.Deleted) continue;
            categories.Add(Parse<BackupCategory>(item.Payload));
        }
        var orderedCategories = categories.OrderBy(c => c.Order).ToList();
        backup.BackupCategories.AddRange(orderedCategories);
        var keyToOrder = new Dictionary<string, long>();
        foreach (var category in orderedCategories) keyToOrder[CategoryKey(category)] = category.Order;

        var chaptersByManga = new Dictionary<string, List<BackupChapter>>();
        foreach (var item in list)
        {
            if (item.Kind != SyncKind.Chapter) continue;
            var chapter = Parse<BackupChapter>(item.Payload);
            if (!chaptersByManga.TryGetValue(item.ParentKey, out var chapters))
            {
                chapters = new List<BackupChapter>();
                chaptersByManga[item.ParentKey] = chapters;
            }
            chapters.Add(chapter);
        }

        foreach (var item in list.Where(i => i.Kind == SyncKind.Manga).OrderBy(i => i.Key, StringComparer.Ordinal))
        {
            var manga = Parse<BackupManga>(item.Payload);
            manga.Categories.Clear();
            foreach (var reference in item.Refs)
            {
                if (keyToOrder.TryGetValue(reference, out var order)) manga.Categories.Add(order);
            }
            if (chaptersByManga.TryGetValue(item.Key, out var chapters))
            {
                manga.Chapters.AddRange(chapters.OrderBy(c => c.SourceOrder).ThenBy(c => c.Url, StringComparer.Ordinal));
            }
            backup.BackupManga.Add(manga);
        }

        var sourceIds = new HashSet<long>();
        foreach (var item in list)
        {
            if (item.Kind == SyncKind.Manga && MangaKeyParts(item.Key) is { } parts)
            {
                sourceIds.Add(parts.Source);
            }
        }
        return RenderSections(backup, list, sourceIds);
    }

    /// <summary>Attaches section items, fallback sources and unknown backup fields, then
    /// returns the message (re-parsed when unknown fields were appended). Caller supplies
    /// the manga sources already present so missing ones get synthetic rows.</summary>
    public static BackupMessage RenderSections(BackupMessage backup, IEnumerable<SyncItem> items, HashSet<long> sourceIds)
    {
        var list = items.ToList();
        foreach (var item in list)
        {
            switch (item.Kind)
            {
                case SyncKind.Source:
                    backup.BackupSources.Add(Parse<BackupSource>(item.Payload));
                    break;
                case SyncKind.AppPref:
                    backup.BackupPreferences.Add(Parse<BackupPreference>(item.Payload));
                    break;
                case SyncKind.SourcePref:
                    var group = backup.BackupSourcePreferences.FirstOrDefault(g => g.SourceKey == item.ParentKey);
                    if (group == null)
                    {
                        group = new BackupSourcePreferences { SourceKey = item.ParentKey };
                        backup.BackupSourcePreferences.Add(group);
                    }
                    group.Prefs.Add(Parse<BackupPreference>(item.Payload));
                    break;
                case SyncKind.ExtStore:
                    backup.BackupExtensionStores.Add(Parse<BackupExtensionStore>(item.Payload));
                    break;
                case SyncKind.SavedSearch:
                    backup.BackupSavedSearches.Add(Parse<BackupSavedSearch>(item.Payload));
                    break;
            }
        }
        foreach (var source in backup.BackupSources) sourceIds.Remove(source.SourceId);
        foreach (var sourceId in sourceIds.OrderBy(id => id))
        {
            backup.BackupSources.Add(new BackupSource { SourceId = sourceId });
        }

        var known = backup.ToByteArray();
        var unknownFields = list.Where(i => i.IsField)
            .OrderBy(i => int.Parse(i.Key["field:".Length..]))
            .Select(i => i.Payload)
            .ToList();
        if (unknownFields.Count == 0) return backup;
        using var output = new MemoryStream();
        output.Write(known);
        foreach (var field in unknownFields) output.Write(field);
        return ParseBackup(output.ToArray());
    }

    public static BackupMessage ParseBackup(byte[] data) => Parse<BackupMessage>(data);

    public static List<(int FieldNumber, byte[] WireBytes)> ExtractUnknownFields(ReadOnlyMemory<byte> data)
    {
        var known = new HashSet<int> { 1, 2, 101, 104, 105, 106, 600 };
        var fields = new Dictionary<int, MemoryStream>();
        var segment = System.Runtime.InteropServices.MemoryMarshal.TryGetArray(data, out var seg) ? seg : new ArraySegment<byte>(data.ToArray());
        var input = new CodedInputStream(segment.Array!, segment.Offset, segment.Count);
        while (!input.IsAtEnd)
        {
            var tag = input.ReadTag();
            if (tag == 0) break;
            var number = WireFormat.GetTagFieldNumber(tag);
            var wire = WireFormat.GetTagWireType(tag);
            if (known.Contains(number))
            {
                input.SkipLastField();
                continue;
            }
            if (!fields.TryGetValue(number, out var sink))
            {
                sink = new MemoryStream();
                fields[number] = sink;
            }
            WriteTag(sink, tag);
            switch (wire)
            {
                case WireFormat.WireType.Varint:
                    WriteVarint(sink, input.ReadUInt64());
                    break;
                case WireFormat.WireType.Fixed64:
                    WriteFixed64(sink, input.ReadFixed64());
                    break;
                case WireFormat.WireType.LengthDelimited:
                    WriteBytes(sink, input.ReadBytes());
                    break;
                case WireFormat.WireType.Fixed32:
                    WriteFixed32(sink, input.ReadFixed32());
                    break;
                case WireFormat.WireType.StartGroup:
                    var start = (int)input.Position;
                    input.SkipLastField();
                    sink.Write(segment.Array!, start, (int)input.Position - start);
                    break;
                default:
                    throw new InvalidDataException("unexpected end-group tag in backup");
            }
        }
        return fields.OrderBy(f => f.Key).Select(f => (f.Key, f.Value.ToArray())).ToList();
    }

    private static void WriteTag(MemoryStream sink, uint tag) => WriteVarint(sink, tag);

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

    private static void WriteFixed64(MemoryStream sink, ulong value)
    {
        for (var i = 0; i < 8; i++)
        {
            sink.WriteByte((byte)value);
            value >>= 8;
        }
    }

    private static void WriteFixed32(MemoryStream sink, uint value)
    {
        for (var i = 0; i < 4; i++)
        {
            sink.WriteByte((byte)value);
            value >>= 8;
        }
    }

    private static void WriteBytes(MemoryStream sink, ByteString bytes)
    {
        WriteVarint(sink, (ulong)bytes.Length);
        bytes.WriteTo(sink);
    }
}
