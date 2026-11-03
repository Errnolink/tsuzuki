namespace Tsuzuki.Core.Sources.MangaDex;

public enum MangaDexCoverQuality
{
    Original = 0,
    Medium = 1,
    Low = 2,
}

public sealed record MangaDexOptions
{
    public IReadOnlyList<string> Languages { get; init; } = ["en"];
    public bool DataSaver { get; init; }
    public MangaDexCoverQuality CoverQuality { get; init; } = MangaDexCoverQuality.Original;
    public IReadOnlyList<string> ContentRatings { get; init; } = ["safe", "suggestive"];
}
