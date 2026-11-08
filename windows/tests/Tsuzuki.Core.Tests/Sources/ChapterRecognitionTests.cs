using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class ChapterRecognitionTests
{
    [Theory]
    [InlineData("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4.0)]
    [InlineData("Mokushiroku Alice", "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation", 4.0)]
    [InlineData("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation", 4.1)]
    [InlineData("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation", 4.1)]
    [InlineData("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation", 4.99)]
    [InlineData("Bleach", "Bleach 567 Down With Snowwhite", 567.0)]
    [InlineData("Bleach", "Bleach 567.b Down With Snowwhite", 567.2)]
    [InlineData("Solanin", "Solanin 028 Vol. 2", 28.0)]
    [InlineData("Onepunch-Man", "Onepunch-Man Punch Ver002 028", 28.0)]
    [InlineData("Onepunch-Man", "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]", 86.0)]
    [InlineData("random", "Vol.1 Ch.5v.2: Alones", 5.0)]
    [InlineData("Ayame 14", "Ayame 14 1 - The summer of 14", 1.0)]
    [InlineData("Ayame 14", "Vol.1 Ch.1: March 25 (First Day Cohabiting)", 1.0)]
    [InlineData("random", "Vol.001 Ch.003: Kaguya Doesn't Know Much", 3.0)]
    [InlineData("Ansatsu Kyoushitsu", "Ansatsu Kyoushitsu 011v002: Assembly Time", 11.0)]
    [InlineData("Tokyo ESP", "Tokyo ESP 027: Part 002: Chapter 001", 27.0)]
    [InlineData("One-punch Man", "Mag Version 195.5", 195.5)]
    [InlineData("random", "Foo", -1.0)]
    [InlineData("random", "Fairy Tail 404: 00:00", 404.0)]
    [InlineData("random", "Asu No Yoichi 19a", 19.1)]
    [InlineData("Fairy Tail", "Fairy Tail 404 extravol002", 404.99)]
    [InlineData("Fairy Tail", "Fairy Tail 404.omakevol002", 404.98)]
    [InlineData("Fairy Tail", "Fairy Tail 404 specialvol002", 404.97)]
    [InlineData("One Piece", "One Piece 300,a", 300.1)]
    [InlineData("One Piece", "One Piece the sunny, goes swimming 024,005", 24.005)]
    [InlineData("Solo Leveling", "ch 122-a", 122.1)]
    [InlineData("Solo Leveling", "Ch.191-200 Read Online", 191.200)]
    [InlineData("D.I.C.E", "D.I.C.E[Season 001] Ep. 007", 7.0)]
    [InlineData("The Gamer", "S3 - Chapter 20", 20.0)]
    [InlineData("One Outs", "One Outs 001", 1.0)]
    [InlineData("The Sister of the Woods with a Thousand Young", "The 2nd Night", 2.0)]
    [InlineData("Wind Breaker", "Chapter 73.5", 73.5)]
    [InlineData("Wind Breaker", "S4 - Episode 178", 178.0)]
    [InlineData("Wind Breaker", "# 79.5", 79.5)]
    public void Recognises_chapter_numbers_like_mihon(string mangaTitle, string name, double expected)
    {
        Assert.Equal(expected, ChapterRecognition.ParseChapterNumber(mangaTitle, name));
    }

    [Theory]
    [InlineData(7.0)]
    [InlineData(0.0)]
    [InlineData(-2.0)]
    public void Keeps_a_number_the_source_gave(double given)
    {
        Assert.Equal(given, ChapterRecognition.ParseChapterNumber("Wind Breaker", "Chapter 73.5", given));
    }

    [Fact]
    public void Prepares_source_chapters_the_way_the_phone_syncs_them()
    {
        var prepared = ChapterRecognition.PrepareForSync("Wind Breaker",
        [
            new SChapter("/chapters/a", "  Wind Breaker - Chapter 73.5 "),
            new SChapter("/chapters/b", "S4 - Episode 178") { ChapterNumber = 556 },
            new SChapter("/chapters/a", "duplicate"),
            new SChapter("/chapters/c", "Character Profiles"),
        ]);

        Assert.Equal(["/chapters/a", "/chapters/b", "/chapters/c"], prepared.Select(c => c.Url));
        Assert.Equal("Chapter 73.5", prepared[0].Name);
        Assert.Equal(73.5, prepared[0].ChapterNumber);
        Assert.Equal(556, prepared[1].ChapterNumber);
        Assert.Equal(-1, prepared[2].ChapterNumber);
    }
}
