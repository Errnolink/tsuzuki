using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Library;

public sealed record CategoryInfo(long Uid, string Name, int Order);

public sealed record LibraryEntry(
    long MangaId,
    long Source,
    string Url,
    string Title,
    string? ThumbnailUrl,
    int UnreadCount,
    int ChapterCount,
    long LastReadAt,
    long DateAdded,
    IReadOnlyList<long> CategoryUids,
    bool IsMerged,
    bool Readable);

public sealed record ContinueEntry(
    long MangaId,
    string Title,
    string? ThumbnailUrl,
    long ChapterId,
    string ChapterName,
    double ChapterNumber,
    int LastPageRead,
    int? PageCount,
    long LastReadAt,
    string? LastDevice,
    long Source);

public sealed record MergedRef(long Source, string Url, bool IsInfoManga, int Priority);

public sealed record MangaDetails(
    long MangaId,
    long Source,
    string Url,
    string Title,
    string? Author,
    string? Artist,
    string? Description,
    IReadOnlyList<string> Genres,
    MangaStatus Status,
    string? ThumbnailUrl,
    bool Favorite,
    long DateAdded,
    IReadOnlyList<long> CategoryUids,
    IReadOnlyList<MergedRef> MergedRefs,
    long LastRefreshed)
{
    public static readonly TimeSpan RefreshLifetime = TimeSpan.FromHours(6);

    public bool RefreshDue(DateTimeOffset now) => now.ToUnixTimeMilliseconds() - LastRefreshed > (long)RefreshLifetime.TotalMilliseconds;
}

public sealed record ChapterRow(
    long ChapterId,
    long OwnerMangaId,
    long Source,
    string Url,
    string Name,
    string? Scanlator,
    double ChapterNumber,
    long DateUpload,
    long SourceOrder,
    bool Read,
    bool Bookmark,
    int LastPageRead,
    int? PageCount,
    long LastReadAt,
    string? ExternalUrl,
    bool Readable);

public sealed record HistoryEntry(
    long MangaId,
    string Title,
    string? ThumbnailUrl,
    long ChapterId,
    string ChapterName,
    double ChapterNumber,
    int LastPageRead,
    long LastReadAt,
    long Source,
    int? PageCount);

public sealed record UpdateEntry(
    long MangaId,
    string Title,
    string? ThumbnailUrl,
    long ChapterId,
    string ChapterName,
    double ChapterNumber,
    string? Scanlator,
    long DateFetch,
    long DateUpload,
    bool Read,
    int LastPageRead,
    long Source);

public sealed record ChapterSyncResult(int Added, int Updated, int Removed);

public enum ChangeOrigin
{
    Local,
    Remote,
    Source,
}

public sealed record ProgressHandoff(long MangaId, long ChapterId, int LastPageRead, bool Read, string DeviceName);

public sealed class LibraryChangedEventArgs(IReadOnlyList<long> mangaIds, ChangeOrigin origin, string? deviceName, IReadOnlyList<ProgressHandoff> handoffs, bool isProgressOnly = false) : EventArgs
{
    public IReadOnlyList<long> MangaIds { get; } = mangaIds;
    public ChangeOrigin Origin { get; } = origin;
    public string? DeviceName { get; } = deviceName;
    public IReadOnlyList<ProgressHandoff> Handoffs { get; } = handoffs;
    public bool IsProgressOnly { get; } = isProgressOnly;
}
