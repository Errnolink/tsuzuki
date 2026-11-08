using System.Text.Json;
using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Reader;

public enum ReaderLayout
{
    Single,
    Double,
    Strip,
}

public enum PageFit
{
    Screen,
    Width,
    Original,
}

public sealed record ReaderProfile(ReaderLayout Layout, bool RightToLeft, PageFit Fit, bool SpreadOffset);

public sealed class ReaderProfiles
{
    private readonly string path;
    private readonly Dictionary<long, ReaderProfile> profiles;
    private readonly Lock gate = new();
    private readonly Lock writeGate = new();

    private ReaderProfiles(string path, Dictionary<long, ReaderProfile> profiles)
    {
        this.path = path;
        this.profiles = profiles;
    }

    public static ReaderProfiles Load(string path)
    {
        try
        {
            if (File.Exists(path) && JsonSerializer.Deserialize(File.ReadAllText(path), ReaderProfilesJson.Default.DictionaryInt64ReaderProfile) is { } loaded)
            {
                return new ReaderProfiles(path, loaded);
            }
        }
        catch (Exception e) when (e is JsonException or IOException or UnauthorizedAccessException)
        {
        }
        return new ReaderProfiles(path, []);
    }

    public ReaderProfile? Get(long mangaId)
    {
        lock (gate)
        {
            return profiles.GetValueOrDefault(mangaId);
        }
    }

    public void Set(long mangaId, ReaderProfile profile)
    {
        lock (gate)
        {
            profiles[mangaId] = profile;
        }
    }

    public void Save()
    {
        lock (writeGate)
        {
            string json;
            lock (gate)
            {
                json = JsonSerializer.Serialize(profiles, ReaderProfilesJson.Default.DictionaryInt64ReaderProfile);
            }
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
            var temp = path + ".tmp";
            File.WriteAllText(temp, json);
            File.Move(temp, path, overwrite: true);
        }
    }
}

[JsonSourceGenerationOptions(UseStringEnumConverter = true)]
[JsonSerializable(typeof(Dictionary<long, ReaderProfile>))]
internal sealed partial class ReaderProfilesJson : JsonSerializerContext;
