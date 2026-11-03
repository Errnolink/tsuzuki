using System.Text.Json;
using System.Text.Json.Serialization;

namespace Tsuzuki.App;

public enum ThemeChoice
{
    System,
    Light,
    Dark,
}

public enum ReaderMode
{
    Single,
    Double,
    Webtoon,
}

public enum ReaderFit
{
    Height,
    Width,
    Original,
}

public sealed class AppSettings
{
    public ThemeChoice Theme { get; set; } = ThemeChoice.Dark;
    public ReaderMode ReaderMode { get; set; } = ReaderMode.Single;
    public bool ReaderModeChosen { get; set; }
    public ReaderFit ReaderFit { get; set; } = ReaderFit.Height;
    public bool RightToLeft { get; set; } = true;
    public bool FirstPageAlone { get; set; } = true;
    public bool DataSaver { get; set; }
    public double TileWidth { get; set; } = 168;
    public bool OnboardingDone { get; set; }
    public bool AutoBackup { get; set; } = true;
    public string? BackupFolder { get; set; }
    public bool BackupDrive { get; set; }
    public bool BackupDriveChosen { get; set; }
    public int AutoBackupKeep { get; set; } = 10;
    public DateTimeOffset LastAutoBackup { get; set; }
    public string LibrarySort { get; set; } = "recent";
    public long? LibraryCategory { get; set; }
    public bool KeepSyncing { get; set; } = true;
    public bool CheckForUpdates { get; set; } = true;
    public bool TrayHintShown { get; set; }
    public bool ReaderHintsShown { get; set; }
    public bool LabMotion { get; set; } = true;
    public bool LabLists { get; set; } = true;
    public bool LabSkeletons { get; set; } = true;
    public int WindowX { get; set; }
    public int WindowY { get; set; }
    public int WindowWidth { get; set; }
    public int WindowHeight { get; set; }
    public bool WindowMaximized { get; set; }

    [JsonIgnore]
    public string? FilePath { get; private set; }

    public static AppSettings Load(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                var loaded = JsonSerializer.Deserialize(File.ReadAllText(path), SettingsJson.Default.AppSettings);
                if (loaded is not null)
                {
                    loaded.FilePath = path;
                    return loaded;
                }
            }
        }
        catch (JsonException)
        {
        }
        return new AppSettings { FilePath = path };
    }

    public void Save()
    {
        if (FilePath is null)
        {
            return;
        }
        var temp = FilePath + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(this, SettingsJson.Default.AppSettings));
        File.Move(temp, FilePath, overwrite: true);
    }
}

[JsonSourceGenerationOptions(WriteIndented = true, UseStringEnumConverter = true)]
[JsonSerializable(typeof(AppSettings))]
internal sealed partial class SettingsJson : JsonSerializerContext;
