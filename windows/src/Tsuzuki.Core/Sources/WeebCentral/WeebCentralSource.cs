using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using AngleSharp.Dom;
using AngleSharp.Html.Parser;

namespace Tsuzuki.Core.Sources.WeebCentral;

public sealed partial class WeebCentralSource : ISource
{
    public const string SourceName = "Weeb Central";
    public const string BaseUrl = "https://weebcentral.com";
    public const int FetchLimit = 32;
    public const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static readonly Uri Base = new(BaseUrl + "/");

    private static readonly FilterSpec SortFilter = SelectSpec("sort", "Sort", "Best Match", "Best Match", "Alphabet", "Popularity", "Subscribers", "Recently Added", "Latest Updates");
    private static readonly FilterSpec OrderFilter = SelectSpec("order", "Sort order", "Descending", "Descending", "Ascending");
    private static readonly FilterSpec OfficialFilter = SelectSpec("official", "Official translation", "Any", "Any", "True", "False");
    private static readonly FilterSpec AnimeFilter = SelectSpec("anime", "Anime adaptation", "Any", "Any", "True", "False");
    private static readonly FilterSpec AdultFilter = SelectSpec("adult", "Adult content", "Any", "Any", "True", "False");
    private static readonly FilterSpec AuthorFilter = new("author", "Author (case-sensitive)", FilterKind.Text, []);
    private static readonly FilterSpec StatusFilter = MultiSpec("included_status", "Series status", "Ongoing", "Complete", "Hiatus", "Canceled");
    private static readonly FilterSpec TypeFilter = MultiSpec("included_type", "Series type", "Manga", "Manhwa", "Manhua", "OEL");
    private static readonly FilterSpec TagFilter = new("included_tag", "Tags", FilterKind.TriState,
    [
        .. new[]
        {
            "Action", "Adult", "Adventure", "Comedy", "Doujinshi", "Drama", "Ecchi", "Fantasy", "Gender Bender", "Harem",
            "Hentai", "Historical", "Horror", "Isekai", "Josei", "Lolicon", "Martial Arts", "Mature", "Mecha", "Mystery",
            "Psychological", "Romance", "School Life", "Sci-fi", "Seinen", "Shotacon", "Shoujo", "Shoujo Ai", "Shounen",
            "Shounen Ai", "Slice of Life", "Smut", "Sports", "Supernatural", "Tragedy", "Yaoi", "Yuri", "Other",
        }.Select(tag => new FilterOption(tag, tag)),
    ]);

    private static readonly IReadOnlyList<FilterSpec> FilterList =
        [SortFilter, OrderFilter, OfficialFilter, AnimeFilter, AdultFilter, AuthorFilter, StatusFilter, TypeFilter, TagFilter];

    private readonly SourceHttp http;
    private readonly TokenBucket siteTokens = new(1, 0.5);

    public WeebCentralSource(HttpClient http, Func<int, TimeSpan>? retryDelay = null)
    {
        this.http = new SourceHttp(http, SourceName, retryDelay);
    }

    public long Id { get; } = SourceIds.Generate(SourceName, "en");

    public string Name => SourceName;

    public string Lang => "en";

    public bool SupportsLatest => true;

    public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) => Task.FromResult(FilterList);

    public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) => SearchAsync(Sorted("Popularity"), page, ct);

    public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => SearchAsync(Sorted("Latest Updates"), page, ct);

    public Uri WebUrl(string mangaUrl) => new(BaseUrl + mangaUrl);

    public async Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default)
    {
        var document = await GetDocumentAsync(BaseUrl + mangaUrl, ct).ConfigureAwait(false);
        var sections = document.QuerySelectorAll("section[x-data] > section");
        if (sections.Length < 2)
        {
            throw new HttpRequestException($"Weeb Central returned no series page for {mangaUrl}");
        }
        var left = sections[0];
        var right = sections[1];
        var genres = Items(left, "Tag", "Type").SelectMany(li => li.QuerySelectorAll("a")).Select(Text).ToList();
        var status = Items(left, "Status").Select(li => li.Children.FirstOrDefault(c => c.LocalName == "a")).FirstOrDefault();
        return new SManga(mangaUrl, Text(right.QuerySelector("h1")))
        {
            ThumbnailUrl = SourceImage(left),
            Author = string.Join(", ", Items(left, "Author")
                .SelectMany(li => li.Children.Where(c => c.LocalName == "span"))
                .SelectMany(span => span.Children.Where(c => c.LocalName == "a"))
                .Select(Text)),
            Genres = genres,
            Status = ParseStatus(status),
            Description = Description(document, right),
            Initialized = true,
        };
    }

    public async Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default)
    {
        var document = await GetDocumentAsync($"{BaseUrl}/series/{SeriesId(mangaUrl)}/full-chapter-list", ct).ConfigureAwait(false);
        var rows = document.QuerySelectorAll("div[x-data] > a");
        var chapters = new List<SChapter>(rows.Length);
        for (var index = 0; index < rows.Length; index++)
        {
            var row = rows[index];
            var name = Text(row.QuerySelector("span.flex > span"));
            var official = row.QuerySelectorAll("img").Any(img => (img.GetAttribute("src") ?? "").Contains("official", StringComparison.OrdinalIgnoreCase));
            var date = row.QuerySelector("time[datetime]")?.GetAttribute("datetime");
            chapters.Add(new SChapter(WithoutDomain(row.GetAttribute("href") ?? ""), name)
            {
                DateUpload = DateTimeOffset.TryParse(date, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var uploaded)
                    ? uploaded.ToUnixTimeMilliseconds()
                    : 0,
                ChapterNumber = SeasonRegex().IsMatch(name) ? rows.Length - index : -1,
                Scanlator = official ? "Official" : "Unknown",
            });
        }
        return chapters;
    }

    public async Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default)
    {
        var document = await GetDocumentAsync($"{BaseUrl}{chapterUrl}/images?is_prev=False&reading_style=long_strip", ct).ConfigureAwait(false);
        return document.QuerySelectorAll("section[x-data*=scroll] > img")
            .Select(img => new Uri(Base, img.GetAttribute("src") ?? ""))
            .Select((url, index) => new PageRef(index, url, url.AbsoluteUri))
            .ToList();
    }

    public HttpRequestMessage CreateImageRequest(Uri url) => Request(url);

    public async Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default)
    {
        var url = new StringBuilder(BaseUrl).Append("/search/data")
            .Append("?text=").Append(Uri.EscapeDataString(ExcludedSearchCharacters().Replace(query.Text, " ").Trim()));
        foreach (var spec in new[] { SortFilter, OrderFilter, OfficialFilter, AnimeFilter, AdultFilter })
        {
            Append(url, spec.Key, query.Single(spec));
        }
        if (query.Get(AuthorFilter).FirstOrDefault() is { Length: > 0 } author)
        {
            Append(url, AuthorFilter.Key, author);
        }
        foreach (var spec in new[] { StatusFilter, TypeFilter })
        {
            foreach (var value in query.Get(spec))
            {
                Append(url, spec.Key, value);
            }
        }
        var included = query.Get(TagFilter);
        var excluded = query.Excluded(TagFilter);
        foreach (var option in TagFilter.Options)
        {
            if (included.Contains(option.Value))
            {
                Append(url, "included_tag", option.Value);
            }
            else if (excluded.Contains(option.Value))
            {
                Append(url, "excluded_tag", option.Value);
            }
        }
        url.Append("&limit=").Append(FetchLimit)
            .Append("&offset=").Append((page - 1) * FetchLimit)
            .Append("&display_mode=Full%20Display");
        var document = await GetDocumentAsync(url.ToString(), ct).ConfigureAwait(false);
        var mangas = document.QuerySelectorAll("article > section > a")
            .Select(a => new SManga(WithoutDomain(a.GetAttribute("href") ?? ""), Text(a.QuerySelector("div:not([class]):last-child")))
            {
                ThumbnailUrl = SourceImage(a),
            })
            .ToList();
        return new MangasPage(mangas, document.QuerySelector("button") is not null);
    }

    private static SearchQuery Sorted(string sort) => new(string.Empty, new Dictionary<string, IReadOnlyList<string>> { [SortFilter.Key] = [sort] });

    private static void Append(StringBuilder url, string key, string value)
        => url.Append('&').Append(key).Append('=').Append(Uri.EscapeDataString(value));

    private static FilterSpec SelectSpec(string key, string label, string fallback, params string[] values)
        => new(key, label, FilterKind.Select, [.. values.Select(value => new FilterOption(value, value))]) { Default = [fallback] };

    private static FilterSpec MultiSpec(string key, string label, params string[] values)
        => new(key, label, FilterKind.Multi, [.. values.Select(value => new FilterOption(value, value))]);

    private async Task<IDocument> GetDocumentAsync(string url, CancellationToken ct)
    {
        var html = await http.GetStringAsync(() => Request(new Uri(url)), siteTokens, ct).ConfigureAwait(false);
        return new HtmlParser().ParseDocument(html);
    }

    private static HttpRequestMessage Request(Uri url)
    {
        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Referrer = Base;
        request.Headers.UserAgent.ParseAdd(BrowserUserAgent);
        return request;
    }

    private static string? Description(IDocument document, IElement right)
    {
        var description = new StringBuilder();
        if (Items(right, "Description").Select(li => li.Children.FirstOrDefault(c => c.LocalName == "p")).FirstOrDefault(p => p is not null) is { } text)
        {
            description.Append(Text(text).Replace("NOTE: ", "\n\nNOTE: ", StringComparison.Ordinal));
        }
        var related = Items(right, "Related Series").SelectMany(li => li.QuerySelectorAll("li")).ToList();
        if (related.Count > 0)
        {
            description.Append("\n\nRelated Series(s):");
            foreach (var series in related)
            {
                var link = series.QuerySelector("a");
                if (link is null)
                {
                    continue;
                }
                var relation = Text(series.QuerySelector("span"));
                description.Append($"\n- [{Text(link)}]({Absolute(link.GetAttribute("href"))}) {relation}".TrimEnd());
            }
        }
        var alternates = Items(right, "Associated Name").SelectMany(li => li.QuerySelectorAll("li")).ToList();
        if (alternates.Count > 0)
        {
            description.Append("\n\nAssociated Name(s):");
            foreach (var name in alternates)
            {
                description.Append("\n- ").Append(Text(name));
            }
        }
        var trackers = Items(document.DocumentElement, "Track")
            .SelectMany(li => li.QuerySelectorAll("span[data-tip] > a"))
            .ToList();
        if (trackers.Count > 0)
        {
            description.Append("\n\nTracker(s):");
            foreach (var tracker in trackers)
            {
                description.Append($"\n- [{tracker.ParentElement!.GetAttribute("data-tip")}]({Absolute(tracker.GetAttribute("href"))})");
            }
        }
        return description.ToString();
    }

    private static IEnumerable<IElement> Items(IElement scope, params string[] labels)
        => scope.QuerySelectorAll("li").Where(li => li.QuerySelectorAll("strong")
            .Any(strong => labels.Any(label => strong.TextContent.Contains(label, StringComparison.OrdinalIgnoreCase))));

    private static MangaStatus ParseStatus(IElement? element) => Text(element).ToLowerInvariant() switch
    {
        "ongoing" => MangaStatus.Ongoing,
        "complete" => MangaStatus.Completed,
        "hiatus" => MangaStatus.OnHiatus,
        "canceled" => MangaStatus.Cancelled,
        _ => MangaStatus.Unknown,
    };

    private static string? SourceImage(IElement element)
    {
        if (element.QuerySelector("source") is { } source)
        {
            return (source.GetAttribute("srcset") ?? "").Replace("small", "normal", StringComparison.Ordinal);
        }
        return element.QuerySelector("img")?.GetAttribute("src") is { } src ? Absolute(src) : null;
    }

    private static string SeriesId(string mangaUrl)
    {
        var segments = mangaUrl.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segments.Length < 2 || segments[0] != "series")
        {
            throw new ArgumentException($"Not a Weeb Central series url: {mangaUrl}", nameof(mangaUrl));
        }
        return segments[1];
    }

    private static string Absolute(string? href) => new Uri(Base, href ?? "").AbsoluteUri;

    private static string WithoutDomain(string href)
    {
        var uri = new Uri(Base, href);
        return uri.PathAndQuery + uri.Fragment;
    }

    private static string Text(IElement? element)
        => element is null ? "" : Whitespace().Replace(element.TextContent, " ").Trim();

    [GeneratedRegex("[!#:(),-]")]
    private static partial Regex ExcludedSearchCharacters();

    [GeneratedRegex(@"(Season|S)\s*\d+", RegexOptions.IgnoreCase)]
    private static partial Regex SeasonRegex();

    [GeneratedRegex(@"[ \t\n\f\r]+")]
    private static partial Regex Whitespace();
}
