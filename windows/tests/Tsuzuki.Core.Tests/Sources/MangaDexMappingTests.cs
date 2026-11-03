using System.Text.Json;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Tsuzuki.Core.Tests.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class MangaDexMappingTests
{
    private static MangaListResponse MangaList(string fixture) =>
        JsonSerializer.Deserialize(FakeMangaDexServer.ReadFixture(fixture), typeof(MangaListResponse), MangaDexJsonContext.Default) as MangaListResponse
        ?? throw new InvalidOperationException($"fixture {fixture} is not a manga list");

    private static MangaData Manga(string fixture)
    {
        using var document = JsonDocument.Parse(FakeMangaDexServer.ReadFixture(fixture));
        var payload = document.RootElement.GetProperty("data").GetRawText();
        return JsonSerializer.Deserialize(payload, typeof(MangaData), MangaDexJsonContext.Default) as MangaData
            ?? throw new InvalidOperationException($"fixture {fixture} has no manga data");
    }

    private static ChapterListResponse ChapterList(string fixture) =>
        JsonSerializer.Deserialize(FakeMangaDexServer.ReadFixture(fixture), typeof(ChapterListResponse), MangaDexJsonContext.Default) as ChapterListResponse
        ?? throw new InvalidOperationException($"fixture {fixture} is not a chapter list");

    private static AggregateResponse? Aggregate(string fixture) =>
        JsonSerializer.Deserialize(FakeMangaDexServer.ReadFixture(fixture), typeof(AggregateResponse), MangaDexJsonContext.Default) as AggregateResponse;

    [Fact]
    public void Details_fall_back_to_an_alt_title_when_the_title_map_lacks_the_language()
    {
        var manga = Manga("yotsuba-details.json");
        var mapped = MangaDexMapper.CreateFull(manga, Aggregate("yotsuba-aggregate.json"), "en", MangaDexCoverQuality.Original);
        Assert.Equal("Yotsuba&!", mapped.Title);
        Assert.Equal("/manga/58be6aa6-06cb-4ca5-bd20-f1392ce451fb", mapped.Url);
        Assert.True(mapped.Initialized);
    }

    [Fact]
    public void Details_prefer_the_extension_language_over_the_original_title_in_lists()
    {
        var manga = MangaList("popular-en.json").Data[0];
        var mapped = MangaDexMapper.CreateBasic(manga, "en", MangaDexCoverQuality.Original);
        Assert.Equal("Kirara Hoshikita Wants to Shine!", mapped.Title);
    }

    [Fact]
    public void Details_carry_author_artist_clean_description_status_and_ordered_genres()
    {
        var mapped = MangaDexMapper.CreateFull(
            Manga("yotsuba-details.json"),
            Aggregate("yotsuba-aggregate.json"),
            "en",
            MangaDexCoverQuality.Original);
        Assert.Equal("Azuma Kiyohiko", mapped.Author);
        Assert.Equal("Azuma Kiyohiko", mapped.Artist);
        Assert.StartsWith("Yotsuba is a strange little girl with a big personality!", mapped.Description);
        Assert.DoesNotContain("Won the Excellence Award", mapped.Description);
        Assert.Equal(MangaStatus.OnHiatus, mapped.Status);
        Assert.Equal(
            ["Award Winning", "Comedy", "Philosophical", "Slice of Life", "Shounen", "Japanese"],
            mapped.Genres);
    }

    [Fact]
    public void Completed_manga_with_last_chapter_in_aggregate_reads_as_completed()
    {
        var mapped = MangaDexMapper.CreateFull(
            Manga("komisan-details.json"),
            Aggregate("komisan-aggregate.json"),
            "en",
            MangaDexCoverQuality.Original);
        Assert.Equal(MangaStatus.Completed, mapped.Status);
        Assert.EndsWith("Final chapter:\nVol.37 Ch.500", mapped.Description);
    }

    [Fact]
    public void Thumbnail_follows_the_cover_quality_suffix()
    {
        var manga = Manga("yotsuba-details.json");
        var original = MangaDexMapper.CreateBasic(manga, "en", MangaDexCoverQuality.Original);
        var medium = MangaDexMapper.CreateBasic(manga, "en", MangaDexCoverQuality.Medium);
        var low = MangaDexMapper.CreateBasic(manga, "en", MangaDexCoverQuality.Low);
        Assert.Equal("https://uploads.mangadex.org/covers/58be6aa6-06cb-4ca5-bd20-f1392ce451fb/ff8d4f70-1797-4036-8241-c63b2237254e.jpg", original.ThumbnailUrl);
        Assert.EndsWith(".512.jpg", medium.ThumbnailUrl);
        Assert.EndsWith(".256.jpg", low.ThumbnailUrl);
    }

    [Fact]
    public void Chapters_use_the_extension_name_scanlator_number_and_ms_dates()
    {
        var chapters = ChapterList("yotsuba-feed.json").Data.Select(MangaDexMapper.CreateChapter).ToList();
        var newest = chapters[0];
        Assert.Equal("/chapter/b82f842f-d581-49cf-bee1-6f9d732e57ad", newest.Url);
        Assert.Equal("Vol.16 Ch.114 - Yotsuba & The Teacher", newest.Name);
        Assert.Equal("Ralen", newest.Scanlator);
        Assert.Equal(114, newest.ChapterNumber);
        Assert.Equal(47, newest.PageCount);
        Assert.Equal(1741482872000L, newest.DateUpload);

        var oldest = chapters[^1];
        Assert.Equal("Vol.1 Ch.1 - Yotsuba & Moving", oldest.Name);
        Assert.Equal("Momotato", oldest.Scanlator);
        Assert.Equal(1517855038000L, oldest.DateUpload);
    }

    [Fact]
    public void Multi_group_chapters_join_group_names_with_an_ampersand()
    {
        var raw = ChapterList("yotsuba-feed.json").Data
            .First(d => d.Attributes!.Chapter == "108" && d.Attributes.Volume == "16");
        var chapter = MangaDexMapper.CreateChapter(raw);
        Assert.Equal("Sense Scans & Ralen", chapter.Scanlator);
    }

    [Fact]
    public void Chapters_without_a_group_fall_back_to_the_uploader()
    {
        var chapter = ChapterList("yotsuba-feed.json").Data
            .Where(d => !d.Relationships.Any(r => r.Type == "scanlation_group"))
            .Select(MangaDexMapper.CreateChapter)
            .Single();
        Assert.Equal("Uploaded by BitterJames", chapter.Scanlator);
        Assert.Equal("Vol.15 Ch.102 - Yotsuba & Watercolor", chapter.Name);
    }

    [Fact]
    public void External_chapters_carry_the_external_url()
    {
        var raw = ChapterList("onepiece-feed.json").Data
            .First(d => d.Attributes!.ExternalUrl == "https://mangaplus.shueisha.co.jp/viewer/1029978");
        var chapter = MangaDexMapper.CreateChapter(raw);
        Assert.Equal("/chapter/" + raw.Id, chapter.Url);
        Assert.Equal("Ch.1191 - There's Still Loki", chapter.Name);
    }

    [Fact]
    public void Invalid_external_chapters_are_not_listed()
    {
        var list = ChapterList("onepiece-feed.json");
        Assert.Equal(6, list.Data.Count);
        var kept = list.Data
            .Where(c => c.Attributes is null || !MangaDexMapper.IsInvalid(c.Attributes))
            .Select(MangaDexMapper.CreateChapter)
            .ToList();
        var chapter = Assert.Single(kept);
        Assert.Null(chapter.ExternalUrl);
        Assert.Equal("Ch.1191 - Loki Is Here", chapter.Name);
    }
}
