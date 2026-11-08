using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json.Nodes;
using Tsuzuki.Core.Proto;

namespace Tsuzuki.Core.Tracking;

public sealed class KitsuTracker(HttpClient http, ITrackerCredentials credentials) : ITracker
{
    private const string ClientId = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd";
    private const string ClientSecret = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151";
    private const string BaseUrl = "https://kitsu.app/api/edge/";
    private const string LoginUrl = "https://kitsu.app/api/oauth/token";
    private const string JsonApi = "application/vnd.api+json";
    public const int Reading = 1;
    public const int Completed = 2;
    public const int OnHold = 3;
    public const int Dropped = 4;
    public const int Planned = 5;

    public int Id => TrackerIds.Kitsu;
    public string Name => "Kitsu";
    public bool SignedIn => Session() is not null;
    public int ReadingStatus => Reading;
    public int CompletedStatus => Completed;
    public int PlanStatus => Planned;
    public int MaxScore => 10;

    public IReadOnlyList<TrackStatus> Statuses { get; } =
    [
        new(Reading, "Reading"),
        new(Completed, "Completed"),
        new(OnHold, "On hold"),
        new(Dropped, "Dropped"),
        new(Planned, "Plan to read"),
    ];

    public async Task LoginAsync(string username, string password, CancellationToken ct = default)
    {
        using var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["username"] = username,
            ["password"] = password,
            ["grant_type"] = "password",
            ["client_id"] = ClientId,
            ["client_secret"] = ClientSecret,
        });
        using var response = await http.PostAsync(LoginUrl, form, ct).ConfigureAwait(false);
        var body = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var token = body?["access_token"]?.GetValue<string>();
        if (!response.IsSuccessStatusCode || string.IsNullOrEmpty(token))
        {
            throw new TrackerSignInException(body?["error_description"]?.GetValue<string>() ?? "Kitsu didn't accept that email and password.");
        }
        using var me = new HttpRequestMessage(HttpMethod.Get, BaseUrl + "users?filter[self]=true");
        me.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        using var user = await http.SendAsync(me, ct).ConfigureAwait(false);
        user.EnsureSuccessStatusCode();
        var userId = (await user.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false))?["data"]?[0]?["id"]?.GetValue<string>()
            ?? throw new TrackerSignInException("Kitsu didn't return your account.");
        credentials.Write(Id, token + "\n" + userId);
    }

    public void Logout() => credentials.Write(Id, null);

    public async Task<IReadOnlyList<TrackSearchResult>> SearchAsync(string query, CancellationToken ct = default)
    {
        using var response = await http.GetAsync(BaseUrl + "manga?page[limit]=20&filter[text]=" + Uri.EscapeDataString(query), ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var results = new List<TrackSearchResult>();
        foreach (var node in json?["data"]?.AsArray() ?? [])
        {
            if (node is not JsonObject item || !long.TryParse(item["id"]?.GetValue<string>(), out var id))
            {
                continue;
            }
            var attributes = item["attributes"];
            results.Add(new TrackSearchResult(
                id,
                attributes?["canonicalTitle"]?.GetValue<string>() ?? string.Empty,
                "https://kitsu.app/manga/" + id,
                attributes?["posterImage"]?["small"]?.GetValue<string>(),
                attributes?["chapterCount"] is JsonValue count && count.TryGetValue<int>(out var chapters) ? chapters : 0,
                attributes?["synopsis"]?.GetValue<string>()));
        }
        return results;
    }

    public async Task<BackupTracking> BindAsync(TrackSearchResult result, CancellationToken ct = default)
    {
        var (_, userId) = Session() ?? throw new TrackerSignInException("Sign in to Kitsu first.");
        var track = new BackupTracking { SyncId = Id, MediaId = result.RemoteId, Title = result.Title, TrackingUrl = result.Url ?? string.Empty, TotalChapters = result.TotalChapters, Status = Planned };
        using (var find = Authorized(HttpMethod.Get, $"library-entries?filter[manga_id]={result.RemoteId}&filter[user_id]={userId}", null))
        using (var found = await http.SendAsync(find, ct).ConfigureAwait(false))
        {
            found.EnsureSuccessStatusCode();
            var json = await found.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
            if (json?["data"]?.AsArray() is { Count: > 0 } entries && entries[0] is JsonObject entry)
            {
                Apply(track, entry);
                return track;
            }
        }
        var body = new JsonObject
        {
            ["data"] = new JsonObject
            {
                ["type"] = "libraryEntries",
                ["attributes"] = new JsonObject { ["status"] = ApiStatus(Planned), ["progress"] = 0 },
                ["relationships"] = new JsonObject
                {
                    ["user"] = new JsonObject { ["data"] = new JsonObject { ["id"] = userId, ["type"] = "users" } },
                    ["media"] = new JsonObject { ["data"] = new JsonObject { ["id"] = result.RemoteId.ToString(), ["type"] = "manga" } },
                },
            },
        };
        using var create = Authorized(HttpMethod.Post, "library-entries", body);
        using var created = await http.SendAsync(create, ct).ConfigureAwait(false);
        created.EnsureSuccessStatusCode();
        var made = await created.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        track.LibraryId = long.TryParse(made?["data"]?["id"]?.GetValue<string>(), out var libraryId) ? libraryId : 0;
        return track;
    }

    public async Task<BackupTracking> RefreshAsync(BackupTracking track, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, $"library-entries?filter[id]={track.LibraryId}", null);
        using var response = await http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(ct).ConfigureAwait(false);
        var copy = track.Clone();
        if (json?["data"]?.AsArray() is { Count: > 0 } entries && entries[0] is JsonObject entry)
        {
            Apply(copy, entry);
        }
        return copy;
    }

    public async Task UpdateAsync(BackupTracking track, CancellationToken ct = default)
    {
        var attributes = new JsonObject
        {
            ["status"] = ApiStatus(track.Status),
            ["progress"] = (int)track.LastChapterRead,
            ["ratingTwenty"] = track.Score > 0 ? ((int)(track.Score * 2)).ToString() : null,
            ["startedAt"] = Date(track.StartedReadingDate),
            ["finishedAt"] = Date(track.FinishedReadingDate),
        };
        var body = new JsonObject { ["data"] = new JsonObject { ["type"] = "libraryEntries", ["id"] = track.LibraryId.ToString(), ["attributes"] = attributes } };
        using var request = Authorized(HttpMethod.Patch, $"library-entries/{track.LibraryId}", body);
        using var response = await http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
    }

    private static void Apply(BackupTracking track, JsonObject entry)
    {
        track.LibraryId = long.TryParse(entry["id"]?.GetValue<string>(), out var id) ? id : track.LibraryId;
        var attributes = entry["attributes"];
        track.Status = attributes?["status"]?.GetValue<string>() switch
        {
            "current" => Reading,
            "completed" => Completed,
            "on_hold" => OnHold,
            "dropped" => Dropped,
            _ => Planned,
        };
        track.LastChapterRead = attributes?["progress"]?.GetValue<int>() ?? 0;
        track.Score = attributes?["ratingTwenty"] is JsonValue rating && rating.TryGetValue<int>(out var twenty) ? twenty / 2f : 0;
    }

    private static string ApiStatus(int status) => status switch
    {
        Reading => "current",
        Completed => "completed",
        OnHold => "on_hold",
        Dropped => "dropped",
        _ => "planned",
    };

    private static string? Date(long epochMs) => epochMs > 0 ? DateTimeOffset.FromUnixTimeMilliseconds(epochMs).ToString("yyyy-MM-dd") : null;

    private (string Token, string UserId)? Session()
    {
        var stored = credentials.Read(Id);
        var parts = stored?.Split('\n');
        return parts is { Length: 2 } ? (parts[0], parts[1]) : null;
    }

    private HttpRequestMessage Authorized(HttpMethod method, string path, JsonNode? body)
    {
        var (token, _) = Session() ?? throw new TrackerSignInException("Sign in to Kitsu first.");
        var request = new HttpRequestMessage(method, BaseUrl + path);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (body is not null)
        {
            request.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, JsonApi);
            request.Content.Headers.ContentType = new MediaTypeHeaderValue(JsonApi);
        }
        return request;
    }
}
