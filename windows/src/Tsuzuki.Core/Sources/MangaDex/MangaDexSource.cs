using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed partial class MangaDexSource : ISource
{
    public const int MangaLimit = 20;
    public const int LatestChapterLimit = 100;
    public const int FeedLimit = 500;

    private static readonly IReadOnlyList<string> DefaultBlockedGroups =
    [
        "5fed0576-8b94-4f9a-b6a7-08eecd69800d",
        "06a9fecb-b608-4f19-b93c-7caab06b7f44",
        "8d8ecf83-8d42-4f8c-add8-60963f9f28d9",
        "caa63201-4a17-4b7f-95ff-ed884a2b7e60",
        "319c1b10-cbd0-4f55-a46e-c4ee17e65139",
        "4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb",
    ];

    private static readonly string[] AllContentRatings = ["safe", "suggestive", "erotica", "pornographic"];

    private readonly MangaDexClient client;
    private readonly MangaDexOptions options;

    public MangaDexSource(MangaDexClient client, MangaDexOptions options, string lang)
    {
        this.client = client;
        this.options = options;
        Lang = lang;
    }

    public long Id => MangaDexIds.For(Lang);

    public string Name => MangaDexIds.SourceName;

    public string Lang { get; }

    public bool SupportsLatest => true;

    private string DexLang => Lang switch
    {
        "zh-Hans" => "zh",
        "zh-Hant" => "zh-hk",
        "fil" => "tl",
        "pt-BR" => "pt-br",
        "es-419" => "es-la",
        _ => Lang,
    };

    public async Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default)
    {
        var offset = MangaLimit * (page - 1);
        var query = new QueryBuilder()
            .Add("order[followedCount]", "desc")
            .Add("availableTranslatedLanguage[]", DexLang)
            .Add("limit", MangaLimit)
            .Add("offset", offset)
            .Add("includes[]", "cover_art")
            .AddEach("contentRating[]", options.ContentRatings)
            .Add("createdAtSince", DateTime.UtcNow.Date.AddDays(-30).ToString("yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture));
        var response = await client.GetAsync<MangaListResponse>($"/manga{query}", ct).ConfigureAwait(false);
        return ToMangasPage(response, offset);
    }

    public async Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default)
    {
        var offset = LatestChapterLimit * (page - 1);
        var query = new QueryBuilder()
            .Add("offset", offset)
            .Add("limit", LatestChapterLimit)
            .Add("translatedLanguage[]", DexLang)
            .Add("order[publishAt]", "desc")
            .Add("includeFutureUpdates", 0)
            .AddEach("contentRating[]", options.ContentRatings)
            .AddEach("excludedGroups[]", DefaultBlockedGroups)
            .Add("includeFuturePublishAt", 0)
            .Add("includeEmptyPages", 0);
        var chapterResponse = await client.GetAsync<ChapterListResponse>($"/chapter{query}", ct).ConfigureAwait(false);
        if (chapterResponse is null)
        {
            return new MangasPage([], false);
        }

        var mangaIds = ChapterMangaIds(chapterResponse.Data);
        if (mangaIds.Count == 0)
        {
            return new MangasPage([], HasNextPage(chapterResponse, offset));
        }

        var mangaQuery = new QueryBuilder()
            .Add("includes[]", "cover_art")
            .Add("limit", mangaIds.Count)
            .AddEach("contentRating[]", options.ContentRatings)
            .AddEach("ids[]", mangaIds);
        var mangaResponse = await client.GetAsync<MangaListResponse>($"/manga{mangaQuery}", ct).ConfigureAwait(false);
        var byId = (mangaResponse?.Data ?? []).ToDictionary(m => m.Id, m => m);
        var mangas = mangaIds
            .Where(byId.ContainsKey)
            .Select(id => MangaDexMapper.CreateBasic(byId[id], DexLang, options.CoverQuality))
            .ToList();
        return new MangasPage(mangas, HasNextPage(chapterResponse, offset));
    }

    public async Task<MangasPage> SearchAsync(string query, int page, CancellationToken ct = default)
    {
        var offset = MangaLimit * (page - 1);
        var builder = new QueryBuilder()
            .Add("limit", MangaLimit)
            .Add("offset", offset)
            .Add("includes[]", "cover_art");
        var trimmed = CollapseWhitespace().Replace(query.Trim(), " ");
        if (trimmed.Length > 0)
        {
            builder.Add("title", trimmed);
        }
        builder.AddEach("contentRating[]", options.ContentRatings);
        builder.Add("order[relevance]", "desc");
        builder.Add("includedTagsMode", "AND");
        builder.Add("excludedTagsMode", "AND");
        var response = await client.GetAsync<MangaListResponse>($"/manga{builder}", ct).ConfigureAwait(false);
        return ToMangasPage(response, offset);
    }

    public async Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default)
    {
        var uuid = UuidFromUrl(mangaUrl);
        var query = new QueryBuilder()
            .Add("includes[]", "cover_art")
            .Add("includes[]", "author")
            .Add("includes[]", "artist");
        var response = (await client.GetAsync<MangaResponse>($"/manga/{uuid}{query}", ct).ConfigureAwait(false))?.Data
            ?? throw new HttpRequestException($"MangaDex returned no manga for {mangaUrl}");
        AggregateResponse? aggregate = null;
        try
        {
            aggregate = await client.GetAsync<AggregateResponse>($"/manga/{uuid}/aggregate?translatedLanguage[]={Uri.EscapeDataString(DexLang)}", ct).ConfigureAwait(false);
        }
        catch (HttpRequestException)
        {
        }
        return MangaDexMapper.CreateFull(response, aggregate, DexLang, options.CoverQuality);
    }

    public async Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default)
    {
        var uuid = UuidFromUrl(mangaUrl);
        var chapters = new List<ChapterData>();
        var offset = 0;
        while (true)
        {
            var query = new QueryBuilder()
                .Add("includes[]", "scanlation_group")
                .Add("includes[]", "user")
                .Add("limit", FeedLimit)
                .Add("offset", offset)
                .Add("translatedLanguage[]", DexLang)
                .Add("order[volume]", "desc")
                .Add("order[chapter]", "desc")
                .Add("includeFuturePublishAt", 0)
                .Add("includeEmptyPages", 0)
                .AddEach("contentRating[]", AllContentRatings)
                .Add("includeUnavailable", 0);
            var response = await client.GetAsync<ChapterListResponse>($"/manga/{uuid}/feed{query}", ct).ConfigureAwait(false);
            if (response is null)
            {
                break;
            }
            chapters.AddRange(response.Data);
            offset += response.Limit;
            if (!HasNextPage(response, response.Offset))
            {
                break;
            }
        }
        return chapters
            .Where(c => c.Attributes is null || !MangaDexMapper.IsInvalid(c.Attributes))
            .Select(MangaDexMapper.CreateChapter)
            .ToList();
    }

    public async Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, CancellationToken ct = default)
    {
        var chapterId = UuidFromUrl(chapterUrl);
        var response = await client.GetAsync<AtHomeResponse>($"/at-home/server/{chapterId}", ct).ConfigureAwait(false)
            ?? throw new HttpRequestException($"MangaDex returned no at-home server for {chapterUrl}");
        var files = options.DataSaver ? response.Chapter.DataSaver : response.Chapter.Data;
        var prefix = options.DataSaver ? "/data-saver/" : "/data/";
        return files
            .Select((file, index) => new PageRef(index, new Uri($"{response.BaseUrl}{prefix}{response.Chapter.Hash}/{file}")))
            .ToList();
    }

    private MangasPage ToMangasPage(MangaListResponse? response, int offset)
    {
        if (response is null)
        {
            return new MangasPage([], false);
        }
        var mangas = response.Data
            .Select(m => MangaDexMapper.CreateBasic(m, DexLang, options.CoverQuality))
            .ToList();
        return new MangasPage(mangas, HasNextPage(response, offset));
    }

    private static bool HasNextPage(ChapterListResponse response, int offset) => response.Limit + offset < response.Total;

    private static bool HasNextPage(MangaListResponse response, int offset) => response.Limit + offset < response.Total;

    private static List<string> ChapterMangaIds(IReadOnlyList<ChapterData> chapters)
    {
        var ids = new List<string>();
        foreach (var chapter in chapters)
        {
            foreach (var relationship in chapter.Relationships)
            {
                if (relationship.Type == "manga" && !ids.Contains(relationship.Id))
                {
                    ids.Add(relationship.Id);
                }
            }
        }
        return ids;
    }

    private static string UuidFromUrl(string url)
    {
        var match = UuidRegex().Match(url);
        if (!match.Success)
        {
            throw new ArgumentException($"Not a MangaDex url with a UUID: {url}", nameof(url));
        }
        return match.Value;
    }

    private sealed class QueryBuilder
    {
        private readonly StringBuilder builder = new();

        public QueryBuilder Add(string key, string value) => Append($"{Uri.EscapeDataString(key)}={Uri.EscapeDataString(value)}");

        public QueryBuilder Add(string key, int value) => Add(key, value.ToString(CultureInfo.InvariantCulture));

        public QueryBuilder AddEach(string key, IReadOnlyList<string> values)
        {
            foreach (var value in values)
            {
                Add(key, value);
            }
            return this;
        }

        public override string ToString() => builder.ToString();

        private QueryBuilder Append(string pair)
        {
            builder.Append(builder.Length == 0 ? '?' : '&').Append(pair);
            return this;
        }
    }

    [GeneratedRegex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")]
    private static partial Regex UuidRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex CollapseWhitespace();
}
