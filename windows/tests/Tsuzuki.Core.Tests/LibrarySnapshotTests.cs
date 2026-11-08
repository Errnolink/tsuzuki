using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Storage;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class LibrarySnapshotTests
{
    [Fact]
    public async Task PreviewStartsWithTheStableLibraryAndNeverWritesBack()
    {
        var root = Path.Combine(TestLib.TempRoot, Guid.NewGuid().ToString("N"));
        var stable = new AppPaths(Path.Combine(root, "stable"));
        var preview = new AppPaths(Path.Combine(root, "preview"));
        stable.EnsureCreated();
        using (var db = new LibraryDatabase(stable.Database))
        {
            db.Initialise();
            var store = new SqliteLibraryStore(db, _ => true);
            var id = await store.UpsertFromSourceAsync(2499283573021220255, new SManga("/manga/abc", "Kept"));
            await store.SetFavoriteAsync(id, true);
            await db.SetMetaAsync("api_key", "STABLE-KEY");
            await db.WriteAsync(c => c.ExecuteNonQuery("INSERT INTO device(id, name, cursor) VALUES ('phone', 'Phone', 42)"));
            await db.FlushAsync();
        }
        File.WriteAllText(stable.Settings, """{ "Theme": "Light", "AutoBackup": true, "BackupDrive": true, "BackupFolder": "G:\\My Drive", "LastAutoBackup": "2026-10-30T21:54:13+05:30" }""");
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        var stableFiles = Directory.GetFiles(stable.Root).ToDictionary(f => f, File.GetLastWriteTimeUtc);

        Assert.True(LibrarySnapshot.SeedFrom(stable, preview));

        Assert.Equal(stableFiles, Directory.GetFiles(stable.Root).ToDictionary(f => f, File.GetLastWriteTimeUtc));
        using (var db = new LibraryDatabase(preview.Database))
        {
            db.Initialise();
            var store = new SqliteLibraryStore(db, _ => true);
            var library = await store.GetLibraryAsync();
            Assert.Equal("Kept", Assert.Single(library).Title);
            Assert.Equal("", await db.GetMetaAsync("api_key"));
            Assert.Equal(0, await db.ReadAsync(c => c.Scalar<long>("SELECT COUNT(*) FROM device")));
            await store.SetFavoriteAsync(library[0].MangaId, false);
        }
        using (var db = new LibraryDatabase(stable.Database))
        {
            db.Initialise();
            Assert.Single(await new SqliteLibraryStore(db, _ => true).GetLibraryAsync());
            Assert.Equal("STABLE-KEY", await db.GetMetaAsync("api_key"));
        }
        var settings = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(preview.Settings))!.AsObject();
        Assert.Equal("Light", (string?)settings["Theme"]);
        Assert.False((bool)settings["AutoBackup"]!);
        Assert.False((bool)settings["BackupDrive"]!);
        Assert.False(settings.ContainsKey("BackupFolder"));
        Assert.False(settings.ContainsKey("LastAutoBackup"));
        Assert.False(LibrarySnapshot.SeedFrom(stable, preview));
    }
}
