using System.Collections.Concurrent;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed partial class MangaDexSource : ISource
{
    public const int MangaLimit = 20;
    public const int LatestChapterLimit = 100;
    public const int FeedLimit = 500;
    private const int MaxOffsetWindow = 10000;
    private static readonly TimeSpan ShortCallTimeout = TimeSpan.FromSeconds(30);

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

    private static readonly FilterSpec OrderFilter = new("order", "Sort", FilterKind.Select,
    [
        new("Relevance", "relevance"),
        new("Latest upload", "latestUploadedChapter"),
        new("Follows", "followedCount"),
        new("Rating", "rating"),
        new("Year", "year"),
        new("Title", "title"),
    ]) { Default = ["relevance"] };

    private static readonly FilterSpec DirectionFilter = new("direction", "Sort order", FilterKind.Select,
        [new("Descending", "desc"), new("Ascending", "asc")]) { Default = ["desc"] };

    private static readonly FilterSpec StatusFilter = new("status", "Publication status", FilterKind.Multi,
        [new("Ongoing", "ongoing"), new("Completed", "completed"), new("Hiatus", "hiatus"), new("Cancelled", "cancelled")]);

    private static readonly FilterSpec DemographicFilter = new("publicationDemographic", "Demographic", FilterKind.Multi,
        [new("None", "none"), new("Shounen", "shounen"), new("Shoujo", "shoujo"), new("Seinen", "seinen"), new("Josei", "josei")]);

    private static readonly FilterSpec LanguageFilter = new("originalLanguage", "Original language", FilterKind.Multi,
        [new("Japanese (manga)", "ja"), new("Chinese (manhua)", "zh"), new("Korean (manhwa)", "ko")]);

    private static readonly FilterSpec IncludedModeFilter = new("includedTagsMode", "Included tags mode", FilterKind.Select,
        [new("All (and)", "AND"), new("Any (or)", "OR")]) { Default = ["AND"] };

    private static readonly FilterSpec ExcludedModeFilter = new("excludedTagsMode", "Excluded tags mode", FilterKind.Select,
        [new("All (and)", "AND"), new("Any (or)", "OR")]) { Default = ["OR"] };

    private static readonly string[] TagGroups = ["content", "format", "genre", "theme"];

    private static readonly FilterSpec TagSelection = new("tags", "Tags", FilterKind.TriState, []);

    private const long PageListLifetimeMs = 10 * 60 * 1000;

    private readonly MangaDexClient client;
    private readonly MangaDexOptions options;
    private readonly FilterSpec contentRatingFilter;
    private readonly ConcurrentDictionary<string, (long At, Task<IReadOnlyList<PageRef>> Pages)> pageLists = new();
    private readonly Lock filtersGate = new();
    private Task<IReadOnlyList<FilterSpec>>? filters;

    public MangaDexSource(MangaDexClient client, MangaDexOptions options, string lang)
    {
        this.client = client;
        this.options = options;
        Lang = lang;
        contentRatingFilter = new FilterSpec("contentRating", "Content rating", FilterKind.Multi,
            [new("Safe", "safe"), new("Suggestive", "suggestive"), new("Erotica", "erotica"), new("Pornographic", "pornographic")])
        {
            Default = options.ContentRatings,
        };
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

    public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default)
    {
        lock (filtersGate)
        {
            if (filters is null || filters.IsFaulted || filters.IsCanceled)
            {
                filters = FetchFiltersAsync();
            }
            return filters.WaitAsync(ct);
        }
    }

    public async Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default)
    {
        var offset = MangaLimit * (page - 1);
        var builder = new QueryBuilder()
            .Add("limit", MangaLimit)
            .Add("offset", offset)
            .Add("includes[]", "cover_art");
        var trimmed = CollapseWhitespace().Replace(query.Text.Trim(), " ");
        if (trimmed.Length > 0)
        {
            builder.Add("title", trimmed);
        }
        foreach (var language in query.Get(LanguageFilter))
        {
            if (language == "zh")
            {
                builder.Add("originalLanguage[]", "zh-hk");
            }
            builder.Add("originalLanguage[]", language);
        }
        builder.AddEach("contentRating[]", query.Get(contentRatingFilter));
        builder.AddEach("publicationDemographic[]", query.Get(DemographicFilter));
        builder.AddEach("status[]", query.Get(StatusFilter));
        builder.Add($"order[{query.Single(OrderFilter)}]", query.Single(DirectionFilter));
        builder.AddEach("includedTags[]", query.Get(TagSelection));
        builder.AddEach("excludedTags[]", query.Excluded(TagSelection));
        builder.Add("includedTagsMode", query.Single(IncludedModeFilter));
        builder.Add("excludedTagsMode", query.Single(ExcludedModeFilter));
        var response = await client.GetAsync<MangaListResponse>($"/manga{builder}", ct).ConfigureAwait(false);
        return ToMangasPage(response, offset);
    }

    public Uri WebUrl(string mangaUrl) => new($"https://mangadex.org/title/{UuidFromUrl(mangaUrl)}");

    private async Task<IReadOnlyList<FilterSpec>> FetchFiltersAsync()
    {
        var response = await GetWithTimeoutAsync<TagListResponse>("/manga/tag").ConfigureAwait(false)
            ?? throw new HttpRequestException("MangaDex returned no tag list");
        var tags = response.Data
            .Where(tag => tag.Attributes is { Name.Length: > 0 })
            .OrderBy(tag => Array.IndexOf(TagGroups, tag.Attributes!.Group) is var index and >= 0 ? index : TagGroups.Length)
            .ThenBy(tag => tag.Attributes!.Name, StringComparer.OrdinalIgnoreCase)
            .Select(tag => new FilterOption(tag.Attributes!.Name!, tag.Id, GroupLabel(tag.Attributes.Group)))
            .ToList();
        return
        [
            OrderFilter,
            DirectionFilter,
            contentRatingFilter,
            StatusFilter,
            DemographicFilter,
            LanguageFilter,
            TagSelection with { Options = tags },
            IncludedModeFilter,
            ExcludedModeFilter,
        ];
    }

    private static string GroupLabel(string? group) => group switch
    {
        "content" => "Content",
        "format" => "Format",
        "genre" => "Genre",
        "theme" => "Theme",
        _ => "Other",
    };

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
            var response = await client.GetAsync<ChapterListResponse>($"/manga/{uuid}/feed{query}", ct).ConfigureAwait(false)
                ?? throw new HttpRequestException($"MangaDex returned no feed page at offset {offset} for {mangaUrl}");
            chapters.AddRange(response.Data);
            offset += response.Limit;
            if (!HasNextPage(response, response.Offset))
            {
                break;
            }
        }
        return chapters
            .Where(c => c.Attributes is { } attributes && !MangaDexMapper.IsInvalid(attributes))
            .Select(MangaDexMapper.CreateChapter)
            .ToList();
    }

    public Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default)
    {
        var chapterId = UuidFromUrl(chapterUrl);
        var now = Environment.TickCount64;
        if (!refresh && pageLists.TryGetValue(chapterId, out var cached) && now - cached.At < PageListLifetimeMs
            && !cached.Pages.IsFaulted && !cached.Pages.IsCanceled)
        {
            return cached.Pages.WaitAsync(ct);
        }
        foreach (var entry in pageLists)
        {
            if (now - entry.Value.At >= PageListLifetimeMs)
            {
                pageLists.TryRemove(entry);
            }
        }
        var fetch = FetchPageListAsync(chapterId, chapterUrl);
        pageLists[chapterId] = (now, fetch);
        return fetch.WaitAsync(ct);
    }

    public HttpRequestMessage CreateImageRequest(Uri url)
    {
        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.UserAgent.ParseAdd(MangaDexClient.UserAgent);
        return request;
    }

    private async Task<IReadOnlyList<PageRef>> FetchPageListAsync(string chapterId, string chapterUrl)
    {
        var response = await GetWithTimeoutAsync<AtHomeResponse>($"/at-home/server/{chapterId}").ConfigureAwait(false)
            ?? throw new HttpRequestException($"MangaDex returned no at-home server for {chapterUrl}");
        var quality = options.DataSaver ? "data-saver" : "data";
        var files = options.DataSaver ? response.Chapter.DataSaver : response.Chapter.Data;
        var hash = response.Chapter.Hash;
        return files
            .Select((file, index) => new PageRef(index, new Uri($"{response.BaseUrl}/{quality}/{hash}/{file}"), $"{quality}/{hash}/{file}"))
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

    private async Task<T?> GetWithTimeoutAsync<T>(string path) where T : class
    {
        using var timeout = new CancellationTokenSource(ShortCallTimeout);
        try
        {
            return await client.GetAsync<T>(path, timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException ex)
        {
            throw new HttpRequestException($"MangaDex timed out for {path}", ex);
        }
    }

    private static bool HasNextPage(ChapterListResponse response, int offset) => HasNextPage(response.Limit, offset, response.Total);

    private static bool HasNextPage(MangaListResponse response, int offset) => HasNextPage(response.Limit, offset, response.Total);

    private static bool HasNextPage(int limit, int offset, int total) =>
        limit > 0 && offset + limit < total && offset + 2 * limit <= MaxOffsetWindow;

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
