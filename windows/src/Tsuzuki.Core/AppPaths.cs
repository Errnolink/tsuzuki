namespace Tsuzuki.Core;

public sealed class AppPaths(string root)
{
    public static AppPaths Default { get; } = new(Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Tsuzuki Reader"));

    public string Root { get; } = root;
    public string Database => Path.Combine(Root, "library.db");
    public string Settings => Path.Combine(Root, "settings.json");
    public string CoverCache => Path.Combine(Root, "cache", "covers");
    public string PageCache => Path.Combine(Root, "cache", "pages");
    public string Backups => Path.Combine(Root, "backups");
    public string Logs => Path.Combine(Root, "logs");

    public void EnsureCreated()
    {
        foreach (var dir in new[] { Root, CoverCache, PageCache, Backups, Logs })
        {
            Directory.CreateDirectory(dir);
        }
    }
}
