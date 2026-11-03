using System.Globalization;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;

namespace Tsuzuki.Core.Sources.MangaDex;

public static partial class MangaDexMapper
{
    public const string LegacyNoGroupId = "00e03853-1b96-4f41-9542-c71b8692033b";
    public const string OneshotTagId = "0234a31e-a729-4e28-9d6a-3f87c4966b9e";
    public const string AnthologyTagId = "51d83883-4103-437c-b4b1-731cb73d786c";

    private static readonly string[] TagGroupOrder = ["content", "format", "genre", "theme"];

    private static readonly IReadOnlyDictionary<string, string> TagNames = new Dictionary<string, string>
    {
        ["b29d6a3d-1569-4e7a-8caf-7557bc92cd5d"] = "Gore",
        ["97893a4c-12af-4dac-b6be-0dffb353568e"] = "Sexual Violence",
        ["b11fda93-8f1d-4bef-b2ed-8803d3733170"] = "Yonkoma",
        ["f4122d1c-3b44-44d0-9936-ff7502c39ad3"] = "Adaptation",
        ["51d83883-4103-437c-b4b1-731cb73d786c"] = "Anthology",
        ["0a39b5a1-b235-4886-a747-1d05d216532d"] = "Award Winning",
        ["b13b2a48-c720-44a9-9c77-39c9979373fb"] = "Doujinshi",
        ["7b2ce280-79ef-4c09-9b58-12b7c23a9b78"] = "Fan Colored",
        ["f5ba408b-0e7a-484d-8d49-4e9125ac96de"] = "Full Color",
        ["3e2b8dae-350e-4ab8-a8ce-016e844b9f0d"] = "Long Strip",
        ["320831a8-4026-470b-94f6-8353740e6f04"] = "Official Colored",
        ["0234a31e-a729-4e28-9d6a-3f87c4966b9e"] = "Oneshot",
        ["891cf039-b895-47f0-9229-bef4c96eccd4"] = "User Created",
        ["e197df38-d0e7-43b5-9b09-2842d0c326dd"] = "Web Comic",
        ["391b0423-d847-456f-aff0-8b0cfc03066b"] = "Action",
        ["87cc87cd-a395-47af-b27a-93258283bbc6"] = "Adventure",
        ["5920b825-4181-4a17-beeb-9918b0ff7a30"] = "Boys' Love",
        ["4d32cc48-9f00-4cca-9b5a-a839f0764984"] = "Comedy",
        ["5ca48985-9a9d-4bd8-be29-80dc0303db72"] = "Crime",
        ["b9af3a63-f058-46de-a9a0-e0c13906197a"] = "Drama",
        ["cdc58593-87dd-415e-bbc0-2ec27bf404cc"] = "Fantasy",
        ["a3c67850-4684-404e-9b7f-c69850ee5da6"] = "Girls' Love",
        ["33771934-028e-4cb3-8744-691e866a923e"] = "Historical",
        ["cdad7e68-1419-41dd-bdce-27753074a640"] = "Horror",
        ["ace04997-f6bd-436e-b261-779182193d3d"] = "Isekai",
        ["81c836c9-914a-4eca-981a-560dad663e73"] = "Magical Girls",
        ["50880a9d-5440-4732-9afb-8f457127e836"] = "Mecha",
        ["c8cbe35b-1b2b-4a3f-9c37-db84c4514856"] = "Medical",
        ["ee968100-4191-4968-93d3-f82d72be7e46"] = "Mystery",
        ["b1e97889-25b4-4258-b28b-cd7f4d28ea9b"] = "Philosophical",
        ["423e2eae-a7a2-4a8b-ac03-a8351462d71d"] = "Romance",
        ["256c8bd9-4904-4360-bf4f-508a76d67183"] = "Sci-Fi",
        ["e5301a23-ebd9-49dd-a0cb-2add944c7fe9"] = "Slice of Life",
        ["69964a64-2f90-4d33-beeb-f3ed2875eb4c"] = "Sports",
        ["7064a261-a137-4d3a-8848-2d385de3a99c"] = "Superhero",
        ["07251805-a27e-4d59-b488-f0bfbec15168"] = "Thriller",
        ["f8f62932-27da-4fe4-8ee1-6779a8c5edba"] = "Tragedy",
        ["acc803a4-c95a-4c22-86fc-eb6b582d82a2"] = "Wuxia",
        ["e64f6742-c834-471d-8d72-dd51fc02b835"] = "Aliens",
        ["3de8c75d-8ee3-48ff-98ee-e20a65c86451"] = "Animals",
        ["ea2bc92d-1c26-4930-9b7c-d5c0dc1b6869"] = "Cooking",
        ["9ab53f92-3eed-4e9b-903a-917c86035ee3"] = "Crossdressing",
        ["da2d50ca-3018-4cc0-ac7a-6b7d472a29ea"] = "Delinquents",
        ["39730448-9a5f-48a2-85b0-a70db87b1233"] = "Demons",
        ["2bd2e8d0-f146-434a-9b51-fc9ff2c5fe6a"] = "Gender Swap",
        ["3bb26d85-09d5-4d2e-880c-c34b974339e9"] = "Ghosts",
        ["fad12b5e-68ba-460e-b933-9ae8318f5b65"] = "Gyaru",
        ["aafb99c1-7f60-43fa-b75f-fc9502ce29c7"] = "Harem",
        ["5bd0e105-4481-44ca-b6e7-7544da56b1a3"] = "Incest",
        ["2d1f5d56-a1e5-4d0d-a961-2193588b08ec"] = "Loli",
        ["85daba54-a71c-4554-8a28-9901a8b0afad"] = "Mafia",
        ["a1f53773-c69a-4ce5-8cab-fffcd90b1565"] = "Magic",
        ["799c202e-7daa-44eb-9cf7-8a3c0441531e"] = "Martial Arts",
        ["ac72833b-c4e9-4878-b9db-6c8a4a99444a"] = "Military",
        ["dd1f77c5-dea9-4e2b-97ae-224af09caf99"] = "Monster Girls",
        ["36fd93ea-e8b8-445e-b836-358f02b3d33d"] = "Monsters",
        ["f42fbf9e-188a-447b-9fdc-f19dc1e4d685"] = "Music",
        ["489dd859-9b61-4c37-af75-5b18e88daafc"] = "Ninja",
        ["92d6d951-ca5e-429c-ac78-451071cbf064"] = "Office Workers",
        ["df33b754-73a3-4c54-80e6-1a74a8058539"] = "Police",
        ["9467335a-1b83-4497-9231-765337a00b96"] = "Post-Apocalyptic",
        ["3b60b75c-a2d7-4860-ab56-05f391bb889c"] = "Psychological",
        ["0bc90acb-ccc1-44ca-a34a-b9f3a73259d0"] = "Reincarnation",
        ["65761a2a-415e-47f3-bef2-a9dababba7a6"] = "Reverse Harem",
        ["81183756-1453-4c81-aa9e-f6e1b63be016"] = "Samurai",
        ["caaa44eb-cd40-4177-b930-79d3ef2afe87"] = "School Life",
        ["ddefd648-5140-4e5f-ba18-4eca4071d19b"] = "Shota",
        ["eabc5b4c-6aff-42f3-b657-3e90cbd00b75"] = "Supernatural",
        ["5fff9cde-849c-4d78-aab0-0d52b2ee1d25"] = "Survival",
        ["292e862b-2d17-4062-90a2-0356caa4ae27"] = "Time Travel",
        ["31932a7e-5b8e-49a6-9f12-2afa39dc544c"] = "Traditional Games",
        ["d7d1730f-6eb0-4ba6-9437-602cac38664c"] = "Vampires",
        ["9438db5a-7e2a-4ac0-b39e-e0d95a34b8a8"] = "Video Games",
        ["d14322ac-4d6f-4e9b-afd9-629d5f4d8a41"] = "Villainess",
        ["8c86611e-fab7-4986-9dec-d1a2f44acdd5"] = "Virtual Reality",
        ["631ef465-9aba-4afb-b0fc-ea10efe274a8"] = "Zombies",
    };

    public static string CoverSuffix(MangaDexCoverQuality quality) => quality switch
    {
        MangaDexCoverQuality.Medium => ".512.jpg",
        MangaDexCoverQuality.Low => ".256.jpg",
        _ => "",
    };

    public static bool IsInvalid(ChapterAttributes chapter) => chapter.ExternalUrl != null
        && (chapter.Pages == 0 || (chapter.Pages == 1 && chapter.UpdatedAt != chapter.ReadableAt));

    public static string? SelectTitle(MangaAttributes attributes, string lang)
    {
        var fromTitleMap = attributes.Title.TryGetValue(lang, out var exact) ? exact : null;
        if (fromTitleMap != null)
        {
            return WebUtility.HtmlDecode(fromTitleMap);
        }

        var mainTitle = attributes.Title.Values.FirstOrDefault();
        var langTitle = FindAltTitle(attributes.AltTitles, lang);
        var enTitle = FindAltTitle(attributes.AltTitles, "en");
        var chosen = langTitle ?? mainTitle ?? enTitle;
        return chosen is null ? null : WebUtility.HtmlDecode(chosen);
    }

    public static string? ThumbnailUrl(MangaData manga, MangaDexCoverQuality quality)
    {
        var fileName = manga.Relationships
            .FirstOrDefault(r => r.Type == "cover_art")?.Attributes?.FileName;
        return fileName is null
            ? null
            : $"{MangaDexClient.CdnBaseUrl}/covers/{manga.Id}/{fileName}{CoverSuffix(quality)}";
    }

    public static SManga CreateBasic(MangaData manga, string lang, MangaDexCoverQuality quality)
    {
        var attributes = manga.Attributes
            ?? throw new ArgumentException("MangaDex manga response has no attributes", nameof(manga));
        return new SManga($"/manga/{manga.Id}", SelectTitle(attributes, lang) ?? "")
        {
            ThumbnailUrl = ThumbnailUrl(manga, quality),
        };
    }

    public static SManga CreateFull(MangaData manga, AggregateResponse? aggregate, string lang, MangaDexCoverQuality quality)
    {
        var attributes = manga.Attributes
            ?? throw new ArgumentException("MangaDex manga response has no attributes", nameof(manga));
        var basic = CreateBasic(manga, lang, quality);

        var description = attributes.Description.TryGetValue(lang, out var localized)
            ? localized
            : attributes.Description.TryGetValue("en", out var english) ? english : null;

        var genres = new List<string>();
        var grouped = attributes.Tags
            .Where(t => t.Attributes?.Group != null && TagNames.ContainsKey(t.Id))
            .GroupBy(t => t.Attributes!.Group!)
            .ToDictionary(
                g => g.Key,
                g => g.Select(t => TagNames[t.Id]).OrderBy(n => n, StringComparer.Create(CultureInfo.InvariantCulture, true)).ToList());
        foreach (var group in TagGroupOrder)
        {
            if (grouped.TryGetValue(group, out var names))
            {
                genres.AddRange(names);
            }
        }
        genres.AddRange(NonGenres(attributes));
        genres.RemoveAll(string.IsNullOrEmpty);

        var authors = Names(manga.Relationships, "author");
        var artists = Names(manga.Relationships, "artist");

        var descriptionParts = new List<string>();
        if (description is not null)
        {
            descriptionParts.Add(CleanDescription(description));
        }
        var finalChapter = new List<string>();
        if (!string.IsNullOrEmpty(attributes.LastVolume))
        {
            finalChapter.Add($"Vol.{attributes.LastVolume}");
        }
        if (!string.IsNullOrEmpty(attributes.LastChapter))
        {
            finalChapter.Add($"Ch.{attributes.LastChapter}");
        }
        if (finalChapter.Count > 0)
        {
            descriptionParts.Add(WebUtility.HtmlDecode($"Final chapter:\n{string.Join(" ", finalChapter)}"));
        }

        return basic with
        {
            Initialized = true,
            Description = string.Join("\n\n", descriptionParts),
            Author = authors.Count > 0 ? string.Join(", ", authors) : null,
            Artist = artists.Count > 0 ? string.Join(", ", artists) : null,
            Genres = genres,
            Status = PublicationStatus(attributes, aggregate),
        };
    }

    public static MangaStatus PublicationStatus(MangaAttributes attributes, AggregateResponse? aggregate)
    {
        var temporary = attributes.Status switch
        {
            "ongoing" => MangaStatus.Ongoing,
            "cancelled" => MangaStatus.Cancelled,
            "completed" => MangaStatus.PublishingFinished,
            "hiatus" => MangaStatus.OnHiatus,
            _ => MangaStatus.Unknown,
        };
        var publishedOrCancelled = temporary is MangaStatus.PublishingFinished or MangaStatus.Cancelled;
        var isOneShot = HasTag(attributes, OneshotTagId) && !HasTag(attributes, AnthologyTagId);

        if (publishedOrCancelled
            && !string.IsNullOrEmpty(attributes.LastChapter)
            && aggregate is not null
            && aggregate.Volumes.Values
                .SelectMany(v => v.Chapters.Values)
                .Any(c => c.Chapter == attributes.LastChapter))
        {
            return MangaStatus.Completed;
        }
        if (isOneShot
            && aggregate is not null
            && aggregate.Volumes.TryGetValue("none", out var none)
            && none.Chapters.ContainsKey("none"))
        {
            return MangaStatus.Completed;
        }
        return temporary;
    }

    public static SChapter CreateChapter(ChapterData chapter)
    {
        var attributes = chapter.Attributes
            ?? throw new ArgumentException("MangaDex chapter response has no attributes", nameof(chapter));

        var groups = chapter.Relationships
            .Where(r => r.Type == "scanlation_group" && r.Id != LegacyNoGroupId)
            .Select(r => r.Attributes?.Name)
            .Where(n => n is not null)
            .Select(n => n!)
            .ToList();
        var scanlator = groups.Count > 0
            ? string.Join(" & ", groups)
            : UploaderLabel(chapter.Relationships) is { } uploader ? $"Uploaded by {uploader}" : "No Group";
        if (attributes.IsUnavailable == true)
        {
            scanlator = $"UNAVAILABLE - {scanlator}";
        }

        var name = new List<string>();
        if (!string.IsNullOrEmpty(attributes.Volume))
        {
            name.Add($"Vol.{attributes.Volume}");
        }
        if (!string.IsNullOrEmpty(attributes.Chapter))
        {
            name.Add($"Ch.{attributes.Chapter}");
        }
        if (!string.IsNullOrEmpty(attributes.Title))
        {
            if (name.Count > 0)
            {
                name.Add("-");
            }
            name.Add(attributes.Title);
        }
        if (name.Count == 0)
        {
            name.Add("Oneshot");
        }

        return new SChapter($"/chapter/{chapter.Id}", WebUtility.HtmlDecode(string.Join(" ", name)))
        {
            Scanlator = scanlator,
            ChapterNumber = double.TryParse(attributes.Chapter, NumberStyles.Float, CultureInfo.InvariantCulture, out var number)
                ? number
                : -1,
            DateUpload = DateTimeOffset.Parse(attributes.PublishAt, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal)
                .ToUnixTimeMilliseconds(),
            PageCount = attributes.Pages,
            ExternalUrl = attributes.ExternalUrl,
        };
    }

    public static string CleanDescription(string raw)
    {
        var text = WebUtility.HtmlDecode(raw);
        var cut = text.IndexOf("\n---", StringComparison.Ordinal);
        if (cut >= 0)
        {
            text = text[..cut];
        }
        text = MarkdownLinks().Replace(text, "$1");
        text = MarkdownBoldItalic().Replace(text, "$1");
        text = MarkdownItalic().Replace(text, "$1");
        return text.Trim();
    }

    private static string? UploaderLabel(IReadOnlyList<MangaRelationship> relationships)
    {
        var users = relationships
            .Where(r => r.Type == "user")
            .Select(r => r.Attributes?.Username)
            .Where(n => n is not null)
            .Select(n => n!)
            .ToList();
        return users.Count > 0 ? string.Join(" & ", users) : null;
    }

    private static IReadOnlyList<string> Names(IReadOnlyList<MangaRelationship> relationships, string type)
        => relationships
            .Where(r => r.Type == type)
            .Select(r => r.Attributes?.Name)
            .Where(n => n is not null)
            .Select(n => n!)
            .Distinct()
            .ToList();

    private static string? FindAltTitle(IReadOnlyList<IReadOnlyDictionary<string, string>> altTitles, string lang)
    {
        foreach (var title in altTitles)
        {
            if (title.ContainsKey(lang))
            {
                return title.Count == 1 ? title.Values.First() : null;
            }
        }
        return null;
    }

    private static List<string> NonGenres(MangaAttributes attributes)
    {
        var nonGenres = new List<string>();
        if (attributes.PublicationDemographic is { } demographic)
        {
            nonGenres.Add(Capitalize(demographic));
        }
        if (attributes.ContentRating is { } rating && rating != "safe")
        {
            nonGenres.Add($"Content rating: {Capitalize(rating)}");
        }
        if (attributes.OriginalLanguage is { } original)
        {
            nonGenres.Add(Capitalize(DisplayName(original)));
        }
        return nonGenres;
    }

    private static string Capitalize(string value) => value.Length == 0 ? value : char.ToUpperInvariant(value[0]) + value[1..];

    private static string DisplayName(string languageTag)
    {
        try
        {
            return CultureInfo.GetCultureInfo(languageTag).DisplayName;
        }
        catch (CultureNotFoundException)
        {
            return languageTag;
        }
    }

    private static bool HasTag(MangaAttributes attributes, string tagId)
        => attributes.Tags.Any(t => t.Id == tagId);

    [GeneratedRegex(@"\[([^]]+)\]\(([^)]+)\)")]
    private static partial Regex MarkdownLinks();

    [GeneratedRegex(@"\*+\s*([^\*]*)\s*\*+")]
    private static partial Regex MarkdownBoldItalic();

    [GeneratedRegex(@"_+\s*([^_]*)\s*_+")]
    private static partial Regex MarkdownItalic();
}
