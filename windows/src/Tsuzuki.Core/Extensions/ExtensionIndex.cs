using System.IO.Compression;
using System.Text.Json;
using Google.Protobuf;

namespace Tsuzuki.Core.Extensions;

public enum ContentWarning
{
    Unspecified = 0,
    Safe = 1,
    Mixed = 2,
    Nsfw = 3,
}

public sealed record StoreSource(long Id, string Name, string Language, string? HomeUrl);

public sealed record StoreExtension(
    string Name,
    string Package,
    string ApkUrl,
    string? IconUrl,
    string Lib,
    long VersionCode,
    string VersionName,
    ContentWarning Warning,
    IReadOnlyList<StoreSource> Sources)
{
    public string Language => Sources.Select(s => s.Language).Distinct().Count() > 1 ? "all" : Sources.FirstOrDefault()?.Language ?? "all";
}

public sealed record StoreIndex(string Name, string? BadgeLabel, string? SigningKey, IReadOnlyList<StoreExtension> Extensions);

public static class ExtensionIndex
{
    public static StoreIndex Parse(byte[] data, Uri indexUrl)
    {
        var bytes = data.Length > 2 && data[0] == 0x1f && data[1] == 0x8b ? Gunzip(data) : data;
        var first = bytes.SkipWhile(b => b is (byte)' ' or (byte)'\n' or (byte)'\r' or (byte)'\t' or 0xEF or 0xBB or 0xBF).FirstOrDefault();
        return first is (byte)'[' or (byte)'{' ? ParseJson(bytes, indexUrl) : ParseProto(bytes);
    }

    private static byte[] Gunzip(byte[] data)
    {
        using var input = new GZipStream(new MemoryStream(data), CompressionMode.Decompress);
        using var output = new MemoryStream();
        input.CopyTo(output);
        return output.ToArray();
    }

    private static StoreIndex ParseProto(byte[] bytes)
    {
        string name = string.Empty, badge = string.Empty, key = string.Empty;
        var extensions = new List<StoreExtension>();
        var input = new CodedInputStream(bytes);
        uint tag;
        while ((tag = input.ReadTag()) != 0)
        {
            switch (WireFormat.GetTagFieldNumber(tag))
            {
                case 1: name = input.ReadString(); break;
                case 2: badge = input.ReadString(); break;
                case 3: key = input.ReadString(); break;
                case 101:
                    var list = new CodedInputStream(input.ReadBytes().ToByteArray());
                    uint inner;
                    while ((inner = list.ReadTag()) != 0)
                    {
                        if (WireFormat.GetTagFieldNumber(inner) == 1)
                        {
                            extensions.Add(ReadExtension(list.ReadBytes().ToByteArray()));
                        }
                        else
                        {
                            list.SkipLastField();
                        }
                    }
                    break;
                case 102:
                    throw new InvalidDataException("This store publishes its extension list at a separate URL, which isn't supported yet.");
                default: input.SkipLastField(); break;
            }
        }
        return new StoreIndex(name, badge.Length > 0 ? badge : null, key.Length > 0 ? key : null, extensions);
    }

    private static StoreExtension ReadExtension(byte[] bytes)
    {
        string name = string.Empty, package = string.Empty, apk = string.Empty, lib = string.Empty, versionName = string.Empty;
        string? icon = null;
        long versionCode = 0;
        var warning = ContentWarning.Unspecified;
        var sources = new List<StoreSource>();
        var input = new CodedInputStream(bytes);
        uint tag;
        while ((tag = input.ReadTag()) != 0)
        {
            switch (WireFormat.GetTagFieldNumber(tag))
            {
                case 1: name = input.ReadString(); break;
                case 2: package = input.ReadString(); break;
                case 3:
                    var resources = new CodedInputStream(input.ReadBytes().ToByteArray());
                    uint r;
                    while ((r = resources.ReadTag()) != 0)
                    {
                        switch (WireFormat.GetTagFieldNumber(r))
                        {
                            case 1: apk = resources.ReadString(); break;
                            case 2: icon = resources.ReadString(); break;
                            default: resources.SkipLastField(); break;
                        }
                    }
                    break;
                case 4: lib = input.ReadString(); break;
                case 5: versionCode = input.ReadInt64(); break;
                case 6: versionName = input.ReadString(); break;
                case 7: warning = (ContentWarning)input.ReadEnum(); break;
                case 8: sources.Add(ReadSource(input.ReadBytes().ToByteArray())); break;
                default: input.SkipLastField(); break;
            }
        }
        return new StoreExtension(name, package, apk, icon, lib, versionCode, versionName, warning, sources);
    }

    private static StoreSource ReadSource(byte[] bytes)
    {
        long id = 0;
        string name = string.Empty, language = string.Empty;
        string? home = null;
        var input = new CodedInputStream(bytes);
        uint tag;
        while ((tag = input.ReadTag()) != 0)
        {
            switch (WireFormat.GetTagFieldNumber(tag))
            {
                case 1: id = input.ReadInt64(); break;
                case 2: name = input.ReadString(); break;
                case 3: language = input.ReadString(); break;
                case 4: home = input.ReadString(); break;
                default: input.SkipLastField(); break;
            }
        }
        return new StoreSource(id, name, language, home);
    }

    private static StoreIndex ParseJson(byte[] bytes, Uri indexUrl)
    {
        using var document = JsonDocument.Parse(bytes);
        var baseUrl = new Uri(indexUrl, ".");
        var extensions = new List<StoreExtension>();
        foreach (var entry in document.RootElement.ValueKind == JsonValueKind.Array ? document.RootElement.EnumerateArray() : Enumerable.Empty<JsonElement>())
        {
            var package = Text(entry, "pkg");
            var apk = Text(entry, "apk");
            if (package.Length == 0 || apk.Length == 0)
            {
                continue;
            }
            var sources = new List<StoreSource>();
            if (entry.TryGetProperty("sources", out var list) && list.ValueKind == JsonValueKind.Array)
            {
                foreach (var source in list.EnumerateArray())
                {
                    var id = source.TryGetProperty("id", out var raw) ? raw.ValueKind == JsonValueKind.Number ? raw.GetInt64() : long.TryParse(raw.GetString(), out var parsed) ? parsed : 0 : 0;
                    sources.Add(new StoreSource(id, Text(source, "name"), Text(source, "lang"), Text(source, "baseUrl")));
                }
            }
            var nsfw = entry.TryGetProperty("nsfw", out var flag) && flag.ValueKind == JsonValueKind.Number && flag.GetInt32() == 1;
            var version = Text(entry, "version");
            extensions.Add(new StoreExtension(
                Text(entry, "name").Replace("Tachiyomi: ", string.Empty),
                package,
                new Uri(baseUrl, "apk/" + apk).ToString(),
                new Uri(baseUrl, "icon/" + package + ".png").ToString(),
                version.Split('.').Take(2) is var parts ? string.Join('.', parts) : string.Empty,
                entry.TryGetProperty("code", out var code) && code.ValueKind == JsonValueKind.Number ? code.GetInt64() : 0,
                version,
                nsfw ? ContentWarning.Nsfw : ContentWarning.Safe,
                sources));
        }
        return new StoreIndex(indexUrl.Host, null, null, extensions);
    }

    private static string Text(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? string.Empty : string.Empty;
}
