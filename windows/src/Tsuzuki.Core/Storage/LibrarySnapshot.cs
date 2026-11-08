using System.Text.Json.Nodes;
using Microsoft.Data.Sqlite;

namespace Tsuzuki.Core.Storage;

public static class LibrarySnapshot
{
    public static bool SeedFrom(AppPaths source, AppPaths target)
    {
        if (File.Exists(target.Database) || !File.Exists(source.Database))
        {
            return false;
        }
        target.EnsureCreated();
        var staging = Path.Combine(target.Root, "seeding");
        if (Directory.Exists(staging))
        {
            Directory.Delete(staging, recursive: true);
        }
        Directory.CreateDirectory(staging);
        try
        {
            var copy = Path.Combine(staging, "library.db");
            File.Copy(source.Database, copy);
            foreach (var suffix in new[] { "-wal", "-shm" })
            {
                if (File.Exists(source.Database + suffix))
                {
                    File.Copy(source.Database + suffix, copy + suffix);
                }
            }
            var temp = target.Database + ".seeding";
            File.Delete(temp);
            using (var from = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = copy, Pooling = false }.ToString()))
            using (var to = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = temp, Pooling = false }.ToString()))
            {
                from.Open();
                to.Open();
                from.BackupDatabase(to);
                using var forget = to.CreateCommand();
                forget.CommandText = "DELETE FROM meta WHERE key = 'api_key'; DELETE FROM device;";
                forget.ExecuteNonQuery();
            }
            File.Move(temp, target.Database);
        }
        finally
        {
            Directory.Delete(staging, recursive: true);
        }
        if (File.Exists(source.Settings) && JsonNode.Parse(File.ReadAllText(source.Settings)) is JsonObject settings)
        {
            settings["AutoBackup"] = false;
            settings["BackupDrive"] = false;
            settings["BackupDriveChosen"] = true;
            settings["TrayHintShown"] = false;
            settings.Remove("BackupFolder");
            settings.Remove("LastAutoBackup");
            File.WriteAllText(target.Settings, settings.ToJsonString(new() { WriteIndented = true }));
        }
        return true;
    }
}
