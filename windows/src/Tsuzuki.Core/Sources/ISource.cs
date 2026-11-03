namespace Tsuzuki.Core.Sources;

public interface ISource
{
    long Id { get; }
    string Name { get; }
    string Lang { get; }
    bool SupportsLatest { get; }

    Task<MangasPage> GetPopularAsync(int page, CancellationToken ct = default);
    Task<MangasPage> GetLatestAsync(int page, CancellationToken ct = default);
    Task<MangasPage> SearchAsync(string query, int page, CancellationToken ct = default);
    Task<SManga> GetMangaDetailsAsync(string mangaUrl, CancellationToken ct = default);
    Task<IReadOnlyList<SChapter>> GetChapterListAsync(string mangaUrl, CancellationToken ct = default);
    Task<IReadOnlyList<PageRef>> GetPageListAsync(string chapterUrl, CancellationToken ct = default);
}

public interface ISourceRegistry
{
    IReadOnlyList<ISource> All { get; }
    ISource? Get(long sourceId);
    string DisplayName(long sourceId);
}
