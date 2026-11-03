using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Library;

public interface ILibraryStore
{
    Task<IReadOnlyList<LibraryEntry>> GetLibraryAsync(CancellationToken ct = default);
    Task<IReadOnlySet<string>> GetFavouriteKeysAsync(CancellationToken ct = default);
    Task<IReadOnlyList<CategoryInfo>> GetCategoriesAsync(CancellationToken ct = default);
    Task<IReadOnlyList<ContinueEntry>> GetContinueReadingAsync(int limit, CancellationToken ct = default);
    Task<MangaDetails?> GetMangaAsync(long mangaId, CancellationToken ct = default);
    Task<long?> FindMangaIdAsync(long source, string url, CancellationToken ct = default);
    Task<IReadOnlyList<ChapterRow>> GetChaptersAsync(long mangaId, CancellationToken ct = default);
    Task<ChapterRow?> GetChapterAsync(long chapterId, CancellationToken ct = default);
    Task<IReadOnlyList<HistoryEntry>> GetHistoryAsync(int limit, CancellationToken ct = default);
    Task<IReadOnlyList<UpdateEntry>> GetUpdatesAsync(int limit, CancellationToken ct = default);

    Task<long> UpsertFromSourceAsync(long source, SManga manga, CancellationToken ct = default);
    Task<ChapterSyncResult> SyncChaptersFromSourceAsync(long mangaId, IReadOnlyList<SChapter> chapters, CancellationToken ct = default);

    Task SetFavoriteAsync(long mangaId, bool favorite, CancellationToken ct = default);
    Task SetChapterProgressAsync(long chapterId, int lastPageRead, bool read, CancellationToken ct = default);
    Task SetChaptersReadAsync(IReadOnlyList<long> chapterIds, bool read, CancellationToken ct = default);
    Task SetMangaCategoriesAsync(long mangaId, IReadOnlyList<long> categoryUids, CancellationToken ct = default);
    Task<CategoryInfo> CreateCategoryAsync(string name, CancellationToken ct = default);
    Task FlushAsync(CancellationToken ct = default);

    event EventHandler<LibraryChangedEventArgs>? Changed;
}
