namespace Tsuzuki.Core.Sources;

public interface ISource
{
    long Id { get; }
    string Name { get; }
    string Lang { get; }
    bool SupportsLatest { get; }

    Task<IReadOnlyList<FilterSpec>> GetFiltersAsync(CancellationToken ct = default);
    Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default);
    Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default);
    Task<MangasPage> SearchAsync(SearchQuery query, int page, CancellationToken ct = default);
    Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default);
    Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default);
    Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, bool refresh = false, CancellationToken ct = default);
    HttpRequestMessage CreateImageRequest(Uri url);
    Uri WebUrl(string mangaUrl);
}

public interface ISourceRegistry
{
    IReadOnlyList<ISource> All { get; }
    ISource? Get(long sourceId);
    bool IsReadable(long sourceId);
    string DisplayName(long sourceId);
}
