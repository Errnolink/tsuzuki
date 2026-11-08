using System.Globalization;
using System.Text.RegularExpressions;

namespace Tsuzuki.Core.Sources;

public static partial class ChapterRecognition
{
    private const string NumberPattern = @"([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?";

    public static IReadOnlyList<SChapter> PrepareForSync(string mangaTitle, IReadOnlyList<SChapter> chapters)
        => chapters
            .DistinctBy(c => c.Url)
            .Select(c =>
            {
                var name = ChapterSanitizer.Sanitize(c.Name, mangaTitle);
                return c with { Name = name, ChapterNumber = ParseChapterNumber(mangaTitle, name, c.ChapterNumber) };
            })
            .ToList();

    public static double ParseChapterNumber(string mangaTitle, string chapterName, double? chapterNumber = null)
    {
        if (chapterNumber is { } known && (known == -2 || known > -1))
        {
            return known;
        }

        var clean = chapterName.ToLowerInvariant();
        var title = mangaTitle.ToLowerInvariant();
        if (title.Length > 0)
        {
            clean = clean.Replace(title, "", StringComparison.Ordinal);
        }
        clean = UnwantedWhiteSpace().Replace(clean.Trim().Replace(',', '.').Replace('-', '.'), "");

        var matches = Number().Matches(clean);
        if (matches.Count == 0)
        {
            return chapterNumber ?? -1;
        }
        if (matches.Count > 1)
        {
            var name = Unwanted().Replace(clean, "");
            var basic = Basic().Match(name);
            if (basic.Success)
            {
                return FromMatch(basic);
            }
            var first = Number().Match(name);
            if (first.Success)
            {
                return FromMatch(first);
            }
        }
        return FromMatch(matches[0]);
    }

    private static double FromMatch(Match match)
    {
        var initial = double.Parse(match.Groups[1].Value, CultureInfo.InvariantCulture);
        return initial + Fraction(match.Groups[2].Value, match.Groups[3].Value);
    }

    private static double Fraction(string decimals, string alpha)
    {
        if (decimals.Length > 0)
        {
            return double.Parse(decimals, CultureInfo.InvariantCulture);
        }
        if (alpha.Length > 0)
        {
            if (alpha.Contains("extra", StringComparison.Ordinal))
            {
                return 0.99;
            }
            if (alpha.Contains("omake", StringComparison.Ordinal))
            {
                return 0.98;
            }
            if (alpha.Contains("special", StringComparison.Ordinal))
            {
                return 0.97;
            }
            var letter = alpha.TrimStart('.');
            if (letter.Length == 1)
            {
                var number = letter[0] - ('a' - 1);
                return number >= 10 ? 0 : number / 10.0;
            }
        }
        return 0;
    }

    [GeneratedRegex(@"(?<=ch\.) *" + NumberPattern)]
    private static partial Regex Basic();

    [GeneratedRegex(NumberPattern)]
    private static partial Regex Number();

    [GeneratedRegex(@"\b(?:v|ver|vol|version|volume|season|s)[^a-z]?[0-9]+")]
    private static partial Regex Unwanted();

    [GeneratedRegex(@"[ \t\n\x0B\f\r](?=extra|special|omake)")]
    private static partial Regex UnwantedWhiteSpace();
}

public static class ChapterSanitizer
{
    private static readonly char[] TrimChars =
    [
        ' ', '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u0085', '\u00A0', '\u1680',
        '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008',
        '\u2009', '\u200A', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000',
        '-', '_', ',', ':',
    ];

    public static string Sanitize(string chapterName, string mangaTitle)
    {
        var name = chapterName.Trim();
        if (mangaTitle.Length > 0 && name.StartsWith(mangaTitle, StringComparison.Ordinal))
        {
            name = name[mangaTitle.Length..];
        }
        return name.Trim(TrimChars);
    }
}
