using Tsuzuki.Core.Proto;

namespace Tsuzuki.Core.Tracking;

public static class TrackerIds
{
    public const int MyAnimeList = 1;
    public const int AniList = 2;
    public const int Kitsu = 3;
    public const int MangaUpdates = 7;

    public static string Label(BackupTracking track) =>
        Known(track.SyncId) || !Uri.TryCreate(track.TrackingUrl, UriKind.Absolute, out var url)
            ? Name(track.SyncId)
            : url.Host.Contains("mangadex", StringComparison.OrdinalIgnoreCase) ? "MangaDex MDList" : url.Host;

    private static bool Known(int id) => id is MyAnimeList or AniList or Kitsu or MangaUpdates or 4 or 5 or 6 or 8 or 9 or 60;

    public static bool FollowsReading(int id) => id is MyAnimeList or AniList or Kitsu or MangaUpdates or 4 or 5 or 60;

    public static string Name(int id) => id switch
    {
        MyAnimeList => "MyAnimeList",
        AniList => "AniList",
        Kitsu => "Kitsu",
        MangaUpdates => "MangaUpdates",
        4 => "Shikimori",
        5 => "Bangumi",
        6 => "Komga",
        8 => "Kavita",
        9 => "Suwayomi",
        60 => "MangaDex MDList",
        _ => "Tracker " + id,
    };
}

public sealed record TrackStatus(int Value, string Label);

public sealed record TrackSearchResult(long RemoteId, string Title, string? Url, string? CoverUrl, int TotalChapters, string? Summary);

public interface ITrackerCredentials
{
    string? Read(int trackerId);
    void Write(int trackerId, string? secret);
}

public interface ITracker
{
    int Id { get; }
    string Name { get; }
    bool SignedIn { get; }
    IReadOnlyList<TrackStatus> Statuses { get; }
    int ReadingStatus { get; }
    int CompletedStatus { get; }
    int PlanStatus { get; }
    int MaxScore { get; }

    Task LoginAsync(string username, string password, CancellationToken ct = default);
    void Logout();
    Task<IReadOnlyList<TrackSearchResult>> SearchAsync(string query, CancellationToken ct = default);
    Task<BackupTracking> BindAsync(TrackSearchResult result, CancellationToken ct = default);
    Task<BackupTracking> RefreshAsync(BackupTracking track, CancellationToken ct = default);
    Task UpdateAsync(BackupTracking track, CancellationToken ct = default);
}

public sealed class TrackerSignInException(string message) : Exception(message);
