namespace Tsuzuki.Core.Downloads;

public enum DownloadState
{
    Queued = 0,
    Downloading = 1,
    Failed = 2,
}

public enum ChapterDownloadState
{
    None,
    Queued,
    Downloading,
    Failed,
    Downloaded,
}

public enum DownloadBlock
{
    None,
    Paused,
    Metered,
    LowSpace,
}

public sealed record DownloadItem(
    long Id,
    long MangaId,
    string ChapterUrl,
    long ChapterId,
    long Source,
    string MangaTitle,
    string ChapterName,
    double ChapterNumber,
    DownloadState State,
    int PagesDone,
    int PagesTotal,
    string? Error);

public sealed record DownloadedChapter(
    long MangaId,
    string ChapterUrl,
    string Path,
    int Pages,
    long Bytes,
    long CompletedAt);

public sealed record DownloadedEntry(
    long MangaId,
    string MangaTitle,
    long ChapterId,
    string ChapterUrl,
    string ChapterName,
    double ChapterNumber,
    string Path,
    int Pages,
    long Bytes,
    long CompletedAt);

public readonly record struct ChapterDownloadStatus(ChapterDownloadState State, int PagesDone, int PagesTotal)
{
    public static ChapterDownloadStatus None { get; } = new(ChapterDownloadState.None, 0, 0);

    public double Progress => PagesTotal > 0 ? (double)PagesDone / PagesTotal : 0;
}

public sealed record DownloadOptions
{
    public required string Root { get; init; }
    public bool UnmeteredOnly { get; init; }
    public int? DeleteAfterReadKeep { get; init; }
    public IReadOnlySet<long> AutoDownloadCategories { get; init; } = new HashSet<long>();
}

public sealed record DownloadEnvironment
{
    public Func<bool> IsMetered { get; init; } = () => false;
    public Func<string, long> FreeBytes { get; init; } = DefaultFreeBytes;
    public long MinimumFreeBytes { get; init; } = 1L << 30;
    public TimeSpan PageRetryDelay { get; init; } = TimeSpan.FromSeconds(2);

    public static long DefaultFreeBytes(string path)
    {
        try
        {
            var root = System.IO.Path.GetPathRoot(System.IO.Path.GetFullPath(path));
            return string.IsNullOrEmpty(root) ? long.MaxValue : new DriveInfo(root).AvailableFreeSpace;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or ArgumentException)
        {
            return long.MaxValue;
        }
    }
}
