using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
using Tsuzuki.Core.Proto;

namespace Tsuzuki.Core.Tracking;

public sealed class MangaUpdatesTracker(HttpClient http, ITrackerCredentials credentials) : ITracker
{
    private const string BaseUrl = "https://api.mangaupdates.com/v1";
    public const int Reading = 0;
    public const int Wish = 1;
    public const int Complete = 2;
    public const int Unfinished = 3;
    public const int OnHold = 4;

    public int Id => TrackerIds.MangaUpdates;
    public string Name => "MangaUpdates";
    public bool SignedIn => credentials.Read(Id) is { Length: > 0 };
    public int ReadingStatus => Reading;
    public int CompletedStatus => Complete;
    public int PlanStatus => Wish;
    public int MaxScore => 10;

    public IReadOnlyList<TrackStatus> Statuses { get; } =
    [
        new(Reading, "Reading"),
        new(Complete, "Complete"),
        new(OnHold, "On hold"),
        new(Unfinished, "Unfinished"),
        new(Wish, "Wish list"),
    ];

    public async Task LoginAsync(string username, string password, CancellationToken ct = default)
    {
        using var response = await http.PutAsJsonAsync(BaseUrl + "/account/login", new JsonObject { ["username"] = username, ["password"] = password }, ct).ConfigureAwait(false);
        var body = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var token = body?["context"]?["session_token"]?.GetValue<string>();
        if (!response.IsSuccessStatusCode || string.IsNullOrEmpty(token))
        {
            throw new TrackerSignInException(body?["reason"]?.GetValue<string>() ?? "MangaUpdates didn't accept that username and password.");
        }
        credentials.Write(Id, token);
    }

    public void Logout() => credentials.Write(Id, null);

    public async Task<IReadOnlyList<TrackSearchResult>> SearchAsync(string query, CancellationToken ct = default)
    {
        var body = new JsonObject { ["search"] = query, ["filter_types"] = new JsonArray("drama cd", "novel") };
        using var response = await http.PostAsJsonAsync(BaseUrl + "/series/search", body, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var results = new List<TrackSearchResult>();
        foreach (var node in json?["results"]?.AsArray() ?? [])
        {
            if (node?["record"] is not JsonObject record || record["series_id"] is null)
            {
                continue;
            }
            results.Add(new TrackSearchResult(
                record["series_id"]!.GetValue<long>(),
                record["title"]?.GetValue<string>() ?? string.Empty,
                record["url"]?.GetValue<string>(),
                record["image"]?["url"]?["original"]?.GetValue<string>(),
                0,
                record["description"]?.GetValue<string>()));
        }
        return results;
    }

    public async Task<BackupTracking> BindAsync(TrackSearchResult result, CancellationToken ct = default)
    {
        var track = new BackupTracking { SyncId = Id, MediaId = result.RemoteId, Title = result.Title, TrackingUrl = result.Url ?? string.Empty, Status = Wish };
        try
        {
            return await RefreshAsync(track, ct).ConfigureAwait(false);
        }
        catch (HttpRequestException e) when (e.StatusCode == HttpStatusCode.NotFound)
        {
            var body = new JsonArray(new JsonObject { ["series"] = new JsonObject { ["id"] = result.RemoteId }, ["list_id"] = Wish });
            using var request = Authorized(HttpMethod.Post, "/lists/series", body);
            using var response = await http.SendAsync(request, ct).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();
            return track;
        }
    }

    public async Task<BackupTracking> RefreshAsync(BackupTracking track, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, $"/lists/series/{RemoteId(track)}", null);
        using var response = await http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var copy = track.Clone();
        copy.Status = json?["list_id"]?.GetValue<int>() ?? copy.Status;
        copy.LastChapterRead = json?["status"]?["chapter"]?.GetValue<int>() ?? (int)copy.LastChapterRead;
        if (json?["series"]?["title"]?.GetValue<string>() is { Length: > 0 } title)
        {
            copy.Title = title;
        }
        return copy;
    }

    public async Task UpdateAsync(BackupTracking track, CancellationToken ct = default)
    {
        var body = new JsonArray(new JsonObject
        {
            ["series"] = new JsonObject { ["id"] = RemoteId(track) },
            ["list_id"] = track.Status,
            ["status"] = new JsonObject { ["chapter"] = (int)track.LastChapterRead },
        });
        using var request = Authorized(HttpMethod.Post, "/lists/series/update", body);
        using var response = await http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        if (track.Score > 0)
        {
            using var rating = Authorized(HttpMethod.Put, $"/series/{RemoteId(track)}/rating", new JsonObject { ["rating"] = track.Score });
            using var rated = await http.SendAsync(rating, ct).ConfigureAwait(false);
            rated.EnsureSuccessStatusCode();
        }
    }

    private static long RemoteId(BackupTracking track) => track.MediaId != 0 ? track.MediaId : track.MediaIdInt;

    private HttpRequestMessage Authorized(HttpMethod method, string path, JsonNode? body)
    {
        var token = credentials.Read(Id) ?? throw new TrackerSignInException("Sign in to MangaUpdates first.");
        var request = new HttpRequestMessage(method, BaseUrl + path);
        request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        if (body is not null)
        {
            request.Content = JsonContent.Create(body);
        }
        return request;
    }
}
