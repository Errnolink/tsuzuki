using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class SourceIdsTests
{
    [Theory]
    [InlineData("MangaDex", "en", 2499283573021220255L)]
    [InlineData("Weeb Central", "en", 2131019126180322627L)]
    [InlineData("Asura Scans", "en", 6247824327199706550L)]
    public void Generated_ids_match_the_ids_in_phone_libraries(string name, string lang, long expected)
    {
        Assert.Equal(expected, SourceIds.Generate(name, lang, 1));
    }
}
