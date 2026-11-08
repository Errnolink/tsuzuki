using System.Text.Json.Nodes;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Extensions;

public sealed class JvmSource : ISource, ISourceImageStreams
{
    private const string ImageScheme = "tsuzuki-ext";

    private readonly ExtensionManager manager;
    private readonly string package;
    private readonly string baseUrl;
    private readonly MemoStore memos;

    public JvmSource(ExtensionManager manager, string package, long id, string name, string lang, bool supportsLatest, string baseUrl, MemoStore memos)
    {
        this.manager = manager;
        this.package = package;
        Id = id;
        Name = name;
        Lang = lang;
        SupportsLatest = supportsLatest;
        this.baseUrl = baseUrl;
        this.memos = memos;
    }

    public long Id { get; }
    public string Name { get; }
    public string Lang { get; }
    public bool SupportsLatest { get; }
    public string Package => package;

    public Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<FilterSpec>>([]);

    public Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default) => ListAsync("popular", page, null, ct);

    public Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default) => ListAsync("latest", page, null, ct);

    public Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default) => ListAsync("search", page, query.Text, ct);

    public async Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default)
    {
        var result = await UpdateAsync(mangaUrl, details: true, chapters: false, ct).ConfigureAwait(false);
        return ToManga(result["manga"]!.AsObject(), initialized: true);
    }

    public async Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default)
    {
        var result = await UpdateAsync(mangaUrl, details: false, chapters: true, ct).ConfigureAwait(false);
        var list = new List<SChapter>();
        foreach (var node in result["chapters"]?.AsArray() ?? [])
        {
            var chapter = node!.AsObject();
            var url = chapter["url"]?.GetValue<string>() ?? string.Empty;
            if (chapter["memo"]?.GetValue<string>() is { Length: > 0 } memo)
            {
                memos.Set("c:" + url, memo);
            }
            list.Add(new SChapter(url, chapter["name"]?.GetValue<string>() ?? string.Empty)
            {
                Scanlator = chapter["scanlator"]?.GetValue<string>() is { Length: > 0 } group ? group : null,
                ChapterNumber = chapter["number"]?.GetValue<double>() ?? -1,
                DateUpload = chapter["date"]?.GetValue<long>() ?? 0,
            });
        }
        memos.Flush();
        return list;
    }

    public async Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default)
    {
        var args = new JsonObject { ["source"] = Id.ToString(), ["url"] = chapterUrl };
        if (memos.Get("c:" + chapterUrl) is { } memo)
        {
            args["memo"] = memo;
        }
        var result = await manager.CallSourceAsync(package, "pages", args, ct).ConfigureAwait(false);
        var pages = new List<PageRef>();
        foreach (var node in result?.AsArray() ?? [])
        {
            var page = node!.AsObject();
            var index = page["index"]?.GetValue<int>() ?? pages.Count;
            var image = page["imageUrl"]?.GetValue<string>() ?? string.Empty;
            var pageUrl = page["url"]?.GetValue<string>() ?? string.Empty;
            var encoded = new UriBuilder(ImageScheme, Id.ToString())
            {
                Path = "/page/" + index,
                Query = "u=" + Uri.EscapeDataString(image) + "&p=" + Uri.EscapeDataString(pageUrl),
            }.Uri;
            pages.Add(new PageRef(index, encoded, $"ext:{Id}:{chapterUrl}:{index}"));
        }
        return pages;
    }

    public async Task<Stream> OpenImageAsync(Uri url, CancellationToken ct)
    {
        var temp = Path.Combine(manager.TempRoot, Guid.NewGuid().ToString("N") + ".img");
        JsonObject args;
        if (url.Scheme == ImageScheme)
        {
            var query = System.Web.HttpUtility.ParseQueryString(url.Query);
            args = new JsonObject
            {
                ["source"] = Id.ToString(),
                ["url"] = query["u"] ?? string.Empty,
                ["pageUrl"] = query["p"] ?? string.Empty,
                ["index"] = int.TryParse(url.Segments.LastOrDefault(), out var index) ? index : 0,
                ["out"] = temp,
            };
        }
        else
        {
            args = new JsonObject { ["source"] = Id.ToString(), ["url"] = url.ToString(), ["pageUrl"] = string.Empty, ["index"] = 0, ["out"] = temp };
        }
        await manager.CallSourceAsync(package, "image", args, ct).ConfigureAwait(false);
        return new FileStream(temp, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete, 1 << 16, FileOptions.DeleteOnClose | FileOptions.Asynchronous);
    }

    public HttpRequestMessage CreateImageRequest(Uri url) => new(HttpMethod.Get, url);

    public Uri WebUrl(string mangaUrl) =>
        Uri.TryCreate(mangaUrl, UriKind.Absolute, out var absolute) ? absolute : new Uri(new Uri(baseUrl.TrimEnd('/') + "/"), mangaUrl.TrimStart('/'));

    private async Task<MangasPage> ListAsync(string op, int page, string? query, CancellationToken ct)
    {
        var args = new JsonObject { ["source"] = Id.ToString(), ["page"] = page };
        if (query is not null)
        {
            args["query"] = query;
        }
        var result = await manager.CallSourceAsync(package, op, args, ct).ConfigureAwait(false);
        var mangas = new List<SManga>();
        foreach (var node in result?["mangas"]?.AsArray() ?? [])
        {
            mangas.Add(ToManga(node!.AsObject(), initialized: false));
        }
        memos.Flush();
        return new MangasPage(mangas, result?["hasNext"]?.GetValue<bool>() ?? false);
    }

    private async Task<JsonObject> UpdateAsync(string mangaUrl, bool details, bool chapters, CancellationToken ct)
    {
        var args = new JsonObject { ["source"] = Id.ToString(), ["url"] = mangaUrl, ["details"] = details, ["chapters"] = chapters };
        if (memos.Get("m:" + mangaUrl) is { } memo)
        {
            args["memo"] = memo;
        }
        var result = await manager.CallSourceAsync(package, "update", args, ct).ConfigureAwait(false);
        return result?.AsObject() ?? throw new HttpRequestException("The extension returned nothing.");
    }

    private SManga ToManga(JsonObject json, bool initialized)
    {
        var url = json["url"]?.GetValue<string>() ?? string.Empty;
        if (json["memo"]?.GetValue<string>() is { Length: > 0 } memo)
        {
            memos.Set("m:" + url, memo);
        }
        var genre = json["genre"]?.GetValue<string>();
        return new SManga(url, json["title"]?.GetValue<string>() ?? string.Empty)
        {
            Author = Blank(json["author"]?.GetValue<string>()),
            Artist = Blank(json["artist"]?.GetValue<string>()),
            Description = Blank(json["description"]?.GetValue<string>()),
            Genres = string.IsNullOrWhiteSpace(genre) ? [] : genre.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries),
            Status = (MangaStatus)(json["status"]?.GetValue<int>() ?? 0),
            ThumbnailUrl = Blank(json["thumbnail"]?.GetValue<string>()),
            Initialized = initialized,
        };
    }

    private static string? Blank(string? value) => string.IsNullOrWhiteSpace(value) ? null : value;
}
