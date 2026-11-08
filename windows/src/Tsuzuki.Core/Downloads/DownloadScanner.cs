using System.IO.Compression;

namespace Tsuzuki.Core.Downloads;

internal sealed record DiskChapter(string SourceFolder, string MangaFolder, string Name, string FullPath, bool IsArchive);

internal static class DownloadScanner
{
    private static readonly HashSet<string> ImageExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".jxl", ".bmp", ".heif", ".heic",
    };

    public static bool IsImage(string name) => ImageExtensions.Contains(Path.GetExtension(name));

    public static IEnumerable<DiskChapter> Enumerate(string root)
    {
        if (!Directory.Exists(root))
        {
            yield break;
        }
        foreach (var sourceDir in SafeDirectories(root))
        {
            foreach (var mangaDir in SafeDirectories(sourceDir))
            {
                foreach (var entry in SafeEntries(mangaDir))
                {
                    var name = Path.GetFileName(entry);
                    if (name.EndsWith(DownloadNames.TempSuffix, StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }
                    var isDirectory = Directory.Exists(entry);
                    var isArchive = !isDirectory && (name.EndsWith(".cbz", StringComparison.OrdinalIgnoreCase) || name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase));
                    if (!isDirectory && !isArchive)
                    {
                        continue;
                    }
                    yield return new DiskChapter(Path.GetFileName(sourceDir), Path.GetFileName(mangaDir), name, entry, isArchive);
                }
            }
        }
    }

    public static DownloadedChapter Describe(long mangaId, string chapterUrl, string root, string fullPath, long completedAt)
    {
        var relative = Path.GetRelativePath(root, fullPath);
        if (File.Exists(fullPath))
        {
            return new DownloadedChapter(mangaId, chapterUrl, relative, CountArchivePages(fullPath), new FileInfo(fullPath).Length, completedAt);
        }
        var files = Directory.EnumerateFiles(fullPath).Select(f => new FileInfo(f)).ToList();
        return new DownloadedChapter(mangaId, chapterUrl, relative, files.Count(f => IsImage(f.Name)), files.Sum(f => f.Length), completedAt);
    }

    public static int CountArchivePages(string path)
    {
        try
        {
            using var zip = ZipFile.OpenRead(path);
            return zip.Entries.Count(e => e.Length > 0 && IsImage(e.Name));
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException)
        {
            return 0;
        }
    }

    public static long SizeOf(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                return new FileInfo(path).Length;
            }
            return Directory.Exists(path)
                ? Directory.EnumerateFiles(path, "*", SearchOption.AllDirectories).Sum(f => new FileInfo(f).Length)
                : 0;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return 0;
        }
    }

    private static IEnumerable<string> SafeDirectories(string path)
    {
        try
        {
            return Directory.GetDirectories(path);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    private static IEnumerable<string> SafeEntries(string path)
    {
        try
        {
            return Directory.GetFileSystemEntries(path);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }
}
