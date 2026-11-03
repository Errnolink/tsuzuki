namespace Tsuzuki.Core.Sources;

public enum MangaStatus
{
    Unknown = 0,
    Ongoing = 1,
    Completed = 2,
    Licensed = 3,
    PublishingFinished = 4,
    Cancelled = 5,
    OnHiatus = 6,
}

public sealed record SManga(string Url, string Title)
{
    public string? Artist { get; init; }
    public string? Author { get; init; }
    public string? Description { get; init; }
    public IReadOnlyList<string> Genres { get; init; } = [];
    public MangaStatus Status { get; init; }
    public string? ThumbnailUrl { get; init; }
    public bool Initialized { get; init; }
}

public sealed record SChapter(string Url, string Name)
{
    public string? Scanlator { get; init; }
    public double ChapterNumber { get; init; } = -1;
    public long DateUpload { get; init; }
    public int? PageCount { get; init; }
    public string? ExternalUrl { get; init; }
}

public sealed record PageRef(int Index, Uri ImageUrl);

public sealed record MangasPage(IReadOnlyList<SManga> Mangas, bool HasNextPage);
