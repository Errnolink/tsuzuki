using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Downloads;

public sealed class LocalChapterProvider(string extractRoot, int keepExtracted = 8)
{
    private const string CompleteMarker = ".complete";
    private readonly SemaphoreSlim gate = new(1, 1);

    public async Task<IReadOnlyList<PageRef>> OpenAsync(string path, CancellationToken ct = default)
    {
        await gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            return await Task.Run(() => Materialise(path, ct), ct).ConfigureAwait(false);
        }
        finally
        {
            gate.Release();
        }
    }

    private IReadOnlyList<PageRef> Materialise(string path, CancellationToken ct)
    {
        var isFolder = Directory.Exists(path);
        if (!isFolder && !File.Exists(path))
        {
            throw new FileNotFoundException("The downloaded chapter is missing.", path);
        }
        var stamp = isFolder
            ? string.Join("|", Directory.EnumerateFiles(path).Select(f => new FileInfo(f)).Select(f => $"{f.Name}:{f.Length}:{f.LastWriteTimeUtc.Ticks}"))
            : $"{new FileInfo(path).Length}:{File.GetLastWriteTimeUtc(path).Ticks}";
        var key = Convert.ToHexStringLower(SHA1.HashData(Encoding.UTF8.GetBytes(Path.GetFullPath(path) + "|" + stamp)))[..16];
        var target = Path.Combine(extractRoot, key);
        if (!File.Exists(Path.Combine(target, CompleteMarker)))
        {
            ChapterDownloader.DeleteDirectory(target);
            Directory.CreateDirectory(target);
            if (isFolder)
            {
                CopyFolder(path, target, ct);
            }
            else
            {
                ExtractArchive(path, target, ct);
            }
            File.WriteAllText(Path.Combine(target, CompleteMarker), path);
            Trim(target);
        }
        else
        {
            Directory.SetLastWriteTimeUtc(target, DateTime.UtcNow);
        }
        return Directory.EnumerateFiles(target)
            .Where(DownloadScanner.IsImage)
            .Order(NaturalComparer.Instance)
            .Select((file, i) => new PageRef(i, new Uri(file), "local:" + path + "#" + i))
            .ToList();
    }

    private static void CopyFolder(string folder, string target, CancellationToken ct)
    {
        var files = Directory.EnumerateFiles(folder).Where(DownloadScanner.IsImage).Order(NaturalComparer.Instance).ToList();
        var digits = Math.Max(4, files.Count.ToString().Length);
        for (var i = 0; i < files.Count; i++)
        {
            ct.ThrowIfCancellationRequested();
            File.Copy(files[i], Path.Combine(target, PageName(i, digits, files[i])));
        }
    }

    private static void ExtractArchive(string archive, string target, CancellationToken ct)
    {
        using var zip = ZipFile.OpenRead(archive);
        var entries = zip.Entries
            .Where(e => e.Length > 0 && DownloadScanner.IsImage(e.Name))
            .OrderBy(e => e.FullName, NaturalComparer.Instance)
            .ToList();
        var digits = Math.Max(4, entries.Count.ToString().Length);
        for (var i = 0; i < entries.Count; i++)
        {
            ct.ThrowIfCancellationRequested();
            using var input = entries[i].Open();
            using var output = File.Create(Path.Combine(target, PageName(i, digits, entries[i].Name)));
            input.CopyTo(output);
        }
    }

    private static string PageName(int index, int digits, string original) =>
        (index + 1).ToString().PadLeft(digits, '0') + Path.GetExtension(original).ToLowerInvariant();

    private void Trim(string keep)
    {
        try
        {
            var stale = new DirectoryInfo(extractRoot).GetDirectories()
                .Where(d => !string.Equals(d.FullName, Path.GetFullPath(keep), StringComparison.OrdinalIgnoreCase))
                .OrderByDescending(d => d.LastWriteTimeUtc)
                .Skip(keepExtracted - 1);
            foreach (var dir in stale)
            {
                ChapterDownloader.DeleteDirectory(dir.FullName);
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }
}

internal sealed class NaturalComparer : IComparer<string>
{
    public static NaturalComparer Instance { get; } = new();

    public int Compare(string? x, string? y)
    {
        if (x is null || y is null)
        {
            return string.Compare(x, y, StringComparison.OrdinalIgnoreCase);
        }
        int i = 0, j = 0;
        while (i < x.Length && j < y.Length)
        {
            if (char.IsAsciiDigit(x[i]) && char.IsAsciiDigit(y[j]))
            {
                var si = i;
                var sj = j;
                while (i < x.Length && char.IsAsciiDigit(x[i])) i++;
                while (j < y.Length && char.IsAsciiDigit(y[j])) j++;
                var a = x[si..i].TrimStart('0');
                var b = y[sj..j].TrimStart('0');
                var byLength = a.Length.CompareTo(b.Length);
                if (byLength != 0) return byLength;
                var byDigits = string.CompareOrdinal(a, b);
                if (byDigits != 0) return byDigits;
                continue;
            }
            var byChar = char.ToUpperInvariant(x[i]).CompareTo(char.ToUpperInvariant(y[j]));
            if (byChar != 0) return byChar;
            i++;
            j++;
        }
        return (x.Length - i).CompareTo(y.Length - j);
    }
}
