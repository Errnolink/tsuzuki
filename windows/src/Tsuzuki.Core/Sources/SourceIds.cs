using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Tsuzuki.Core.Sources;

public static class SourceIds
{
    public static long Generate(string name, string lang, int versionId = 1)
    {
        var digest = MD5.HashData(Encoding.UTF8.GetBytes($"{name.ToLowerInvariant()}/{lang}/{versionId}"));
        return BinaryPrimitives.ReadInt64BigEndian(digest) & long.MaxValue;
    }
}
