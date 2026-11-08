using System.Security.Cryptography;
using System.Text;

namespace Tsuzuki.Core.Downloads;

public static class DownloadNames
{
    public const int MaxFileNameBytes = 240;
    public const string TempSuffix = "_tmp";
    public const string ComicInfoFile = "ComicInfo.xml";
    public const string CoverFile = "cover.jpg";

    public static string BuildValidFilename(string original, int maxBytes = MaxFileNameBytes)
    {
        var name = original.Trim('.', ' ');
        if (name.Length == 0)
        {
            return "(invalid)";
        }
        var builder = new StringBuilder(name.Length);
        foreach (var c in name)
        {
            builder.Append(IsValidFatFilenameChar(c) ? c : '_');
        }
        return TruncateToLength(builder.ToString(), maxBytes);
    }

    public static string SourceFolder(string sourceName, string lang) =>
        BuildValidFilename($"{sourceName} ({lang.ToUpperInvariant()})");

    public static string MangaFolder(string title) => BuildValidFilename(title);

    public static string ChapterName(string chapterName, string? scanlator, string chapterUrl, bool withHash = false)
    {
        var name = string.IsNullOrWhiteSpace(chapterName) ? "Chapter" : chapterName;
        var dirName = BuildValidFilename(
            string.IsNullOrWhiteSpace(scanlator) ? name : $"{scanlator}_{name}",
            MaxFileNameBytes - 11);
        return withHash ? dirName + "_" + UrlHash(chapterUrl) : dirName;
    }

    public static IEnumerable<string> ChapterCandidates(string chapterName, string? scanlator, string chapterUrl)
    {
        var plain = ChapterName(chapterName, scanlator, chapterUrl);
        var hashed = ChapterName(chapterName, scanlator, chapterUrl, withHash: true);
        yield return plain + ".cbz";
        yield return plain;
        yield return hashed + ".cbz";
        yield return hashed;
    }

    public static string PageFile(int index, int pageCount, string extension)
    {
        var digits = Math.Max(3, pageCount.ToString().Length);
        return (index + 1).ToString().PadLeft(digits, '0') + extension;
    }

    private static string UrlHash(string url) =>
        Convert.ToHexStringLower(MD5.HashData(Encoding.UTF8.GetBytes(url)))[..6];

    private static string TruncateToLength(string value, int maxBytes)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        if (bytes.Length <= maxBytes)
        {
            return value;
        }
        var cut = maxBytes;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80)
        {
            cut--;
        }
        return Encoding.UTF8.GetString(bytes, 0, cut);
    }

    private static bool IsValidFatFilenameChar(char c) =>
        c > 0x1f && c is not ('"' or '*' or '/' or ':' or '<' or '>' or '?' or '\\' or '|' or (char)0x7f);
}
