using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Extensions;

public sealed record InstalledExtension(
    string Package,
    string Name,
    string VersionName,
    long VersionCode,
    string StoreUrl,
    string ApkFile,
    string? IconUrl,
    ContentWarning Warning,
    IReadOnlyList<InstalledSource> Sources);

public sealed record InstalledSource(long Id, string Name, string Lang, bool SupportsLatest, string BaseUrl);

[JsonSourceGenerationOptions(WriteIndented = true, UseStringEnumConverter = true)]
[JsonSerializable(typeof(List<ExtensionStore>))]
[JsonSerializable(typeof(List<InstalledExtension>))]
[JsonSerializable(typeof(Dictionary<string, string>))]
internal sealed partial class ExtensionJson : JsonSerializerContext;
