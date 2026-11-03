using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Tsuzuki.Core.Sources.MangaDex;

public static class MangaDexIds
{
    public const string SourceName = "MangaDex";
    public const int VersionId = 1;

    public static readonly IReadOnlyList<string> ExtensionLanguages =
    [
        "af", "sq", "ar", "az", "eu", "be", "bn", "bg", "my", "ca", "zh-Hans", "zh-Hant",
        "cv", "hr", "cs", "da", "nl", "en", "eo", "et", "fil", "fi", "fr", "ka", "de", "el",
        "he", "hi", "hu", "ga", "id", "it", "ja", "jv", "kk", "ko", "la", "lt", "ms", "mn",
        "ne", "no", "fa", "pl", "pt-BR", "pt", "ro", "ru", "sr", "sk", "es-419", "es", "sv",
        "ta", "te", "th", "tr", "uk", "ur", "uz", "vi",
    ];

    public static long For(string lang)
    {
        var input = $"{SourceName.ToLowerInvariant()}/{lang}/{VersionId}";
        var digest = MD5.HashData(Encoding.UTF8.GetBytes(input));
        return BinaryPrimitives.ReadInt64BigEndian(digest) & long.MaxValue;
    }
}
