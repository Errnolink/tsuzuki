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
    public long? BrowseSource { get; set; }
    public bool KeepSyncing { get; set; } = true;
    public bool CheckForUpdates { get; set; } = true;
    public bool TrayHintShown { get; set; }
    public bool ReaderHintsShown { get; set; }
    public bool LabMotion { get; set; } = true;
    public bool LabLists { get; set; } = true;
    public bool LabSkeletons { get; set; } = true;
    public bool NextPaneCompact { get; set; }
    public Tsuzuki.Presentation.DetailsHeaderStyle DetailsHeader { get; set; } = Tsuzuki.Presentation.DetailsHeaderStyle.Centred;
    public bool DetailsCoverColour { get; set; }
    public string LibraryView { get; set; } = "grid";
    public string LibraryFilter { get; set; } = "";
    public string ReaderBackground { get; set; } = "black";
    public int ReaderAutoHideMs { get; set; } = 2400;
    public bool ReaderFilmstrip { get; set; }
    public int ReaderWheelScroll { get; set; }
    public string? DownloadsRoot { get; set; }
    public bool DownloadsUnmeteredOnly { get; set; }
    public int? DownloadsDeleteAfterRead { get; set; }
    public List<long> DownloadsAutoCategories { get; set; } = [];
    public bool TrackingUpdateAfterReading { get; set; } = true;
    public int WindowX { get; set; }
    public int WindowY { get; set; }
    public int WindowWidth { get; set; }
    public int WindowHeight { get; set; }
    public bool WindowMaximized { get; set; }

    [JsonIgnore]
    public string? FilePath { get; private set; }

    private string? unreadable;

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
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            var defaults = new AppSettings { FilePath = path };
            defaults.unreadable = defaults.UserFacing();
            return defaults;
        }
        return new AppSettings { FilePath = path };
    }

    public void Save()
    {
        if (FilePath is null)
        {
            return;
        }
        var json = Serialize();
        if (unreadable is not null)
        {
            if (UserFacing() == unreadable)
            {
                return;
            }
            unreadable = null;
        }
        var temp = FilePath + ".tmp";
        try
        {
            File.WriteAllText(temp, json);
            File.Move(temp, FilePath, overwrite: true);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Log.Error("settings-save", e);
        }
    }

    private string Serialize() => JsonSerializer.Serialize(this, SettingsJson.Default.AppSettings);

    private string UserFacing()
    {
        var copy = JsonSerializer.Deserialize(Serialize(), SettingsJson.Default.AppSettings)!;
        copy.BackupDrive = false;
        copy.BackupDriveChosen = false;
        copy.WindowX = copy.WindowY = copy.WindowWidth = copy.WindowHeight = 0;
        copy.WindowMaximized = false;
        return copy.Serialize();
    }
}

[JsonSourceGenerationOptions(WriteIndented = true, UseStringEnumConverter = true)]
[JsonSerializable(typeof(AppSettings))]
internal sealed partial class SettingsJson : JsonSerializerContext;
