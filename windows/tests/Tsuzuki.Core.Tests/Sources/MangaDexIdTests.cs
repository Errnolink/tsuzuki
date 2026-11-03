using System.Security.Cryptography;
using System.Text;
using Tsuzuki.Core.Sources.MangaDex;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class MangaDexIdTests
{
    [Fact]
    public void English_id_matches_the_keiyoushi_extension()
    {
        Assert.Equal(2499283573021220255L, MangaDexIds.For("en"));
    }

    [Theory]
    [InlineData("ja")]
    [InlineData("pt-BR")]
    [InlineData("zh-Hans")]
    [InlineData("es-419")]
    [InlineData("fil")]
    public void Ids_match_the_extension_formula_for_other_languages(string lang)
    {
        var digest = MD5.HashData(Encoding.UTF8.GetBytes($"mangadex/{lang}/1"));
        var expected = BitConverter.ToInt64(digest.Take(8).Reverse().ToArray()) & long.MaxValue;
        Assert.Equal(expected, MangaDexIds.For(lang));
    }

    [Fact]
    public void Every_extension_language_gets_a_distinct_positive_id()
    {
        var ids = MangaDexIds.ExtensionLanguages.Select(MangaDexIds.For).ToHashSet();
        Assert.Equal(MangaDexIds.ExtensionLanguages.Count, ids.Count);
        Assert.All(ids, id => Assert.True(id > 0));
    }
}
