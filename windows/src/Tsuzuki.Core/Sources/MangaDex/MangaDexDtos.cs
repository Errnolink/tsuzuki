using System.Text.Json;
using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Sources.MangaDex;

public sealed record MangaListResponse(
    [property: JsonPropertyName("result")] string Result,
    [property: JsonPropertyName("data")] IReadOnlyList<MangaData> Data,
    [property: JsonPropertyName("limit")] int Limit,
    [property: JsonPropertyName("offset")] int Offset,
    [property: JsonPropertyName("total")] int Total);

public sealed record MangaData(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("attributes")] MangaAttributes? Attributes,
    [property: JsonPropertyName("relationships")] IReadOnlyList<MangaRelationship> Relationships);

public sealed record MangaAttributes(
    [property: JsonPropertyName("title")] IReadOnlyDictionary<string, string> Title,
    [property: JsonPropertyName("altTitles")] IReadOnlyList<IReadOnlyDictionary<string, string>> AltTitles,
    [property: JsonPropertyName("description")] IReadOnlyDictionary<string, string> Description,
    [property: JsonPropertyName("originalLanguage")] string? OriginalLanguage,
    [property: JsonPropertyName("lastVolume")] string? LastVolume,
    [property: JsonPropertyName("lastChapter")] string? LastChapter,
    [property: JsonPropertyName("contentRating")] string? ContentRating,
    [property: JsonPropertyName("publicationDemographic")] string? PublicationDemographic,
    [property: JsonPropertyName("status")] string? Status,
    [property: JsonPropertyName("tags")] IReadOnlyList<MangaRelationship> Tags);

public sealed record MangaRelationship(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("attributes")] MangaRelationshipAttributes? Attributes);

public sealed record MangaRelationshipAttributes(
    [property: JsonPropertyName("fileName")] string? FileName,
    [property: JsonPropertyName("name"), JsonConverter(typeof(LocalizedNameConverter))] string? Name,
    [property: JsonPropertyName("username")] string? Username,
    [property: JsonPropertyName("group")] string? Group);

public sealed class LocalizedNameConverter : JsonConverter<string?>
{
    public override string? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        => reader.TokenType switch
        {
            JsonTokenType.String => reader.GetString(),
            JsonTokenType.Null => null,
            JsonTokenType.StartObject => ReadLocalized(ref reader),
            _ => throw new JsonException($"Unexpected token {reader.TokenType} for a relationship name"),
        };

    private static string? ReadLocalized(ref Utf8JsonReader reader)
    {
        string? first = null;
        while (reader.Read())
        {
            if (reader.TokenType == JsonTokenType.EndObject)
            {
                return first;
            }
            if (reader.TokenType == JsonTokenType.PropertyName)
            {
                reader.Read();
                if (reader.TokenType == JsonTokenType.String && first is null)
                {
                    first = reader.GetString();
                }
            }
        }
        throw new JsonException("Unterminated localized name object");
    }

    public override void Write(Utf8JsonWriter writer, string? value, JsonSerializerOptions options)
    {
        if (value is null)
        {
            writer.WriteNullValue();
        }
        else
        {
            writer.WriteStringValue(value);
        }
    }
}

public sealed record MangaResponse(
    [property: JsonPropertyName("result")] string Result,
    [property: JsonPropertyName("data")] MangaData? Data);

public sealed record ChapterListResponse(
    [property: JsonPropertyName("result")] string Result,
    [property: JsonPropertyName("data")] IReadOnlyList<ChapterData> Data,
    [property: JsonPropertyName("limit")] int Limit,
    [property: JsonPropertyName("offset")] int Offset,
    [property: JsonPropertyName("total")] int Total);

public sealed record ChapterData(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("attributes")] ChapterAttributes? Attributes,
    [property: JsonPropertyName("relationships")] IReadOnlyList<MangaRelationship> Relationships);

public sealed record ChapterAttributes(
    [property: JsonPropertyName("title")] string? Title,
    [property: JsonPropertyName("volume")] string? Volume,
    [property: JsonPropertyName("chapter")] string? Chapter,
    [property: JsonPropertyName("pages")] int Pages,
    [property: JsonPropertyName("publishAt")] string PublishAt,
    [property: JsonPropertyName("updatedAt")] string UpdatedAt,
    [property: JsonPropertyName("readableAt")] string ReadableAt,
    [property: JsonPropertyName("externalUrl")] string? ExternalUrl,
    [property: JsonPropertyName("isUnavailable")] bool? IsUnavailable);

public sealed record AtHomeResponse(
    [property: JsonPropertyName("baseUrl")] string BaseUrl,
    [property: JsonPropertyName("chapter")] AtHomeChapter Chapter);

public sealed record AtHomeChapter(
    [property: JsonPropertyName("hash")] string Hash,
    [property: JsonPropertyName("data")] IReadOnlyList<string> Data,
    [property: JsonPropertyName("dataSaver")] IReadOnlyList<string> DataSaver);

public sealed record AggregateResponse(
    [property: JsonPropertyName("volumes")] IReadOnlyDictionary<string, AggregateVolume> Volumes);

public sealed record AggregateVolume(
    [property: JsonPropertyName("chapters")] IReadOnlyDictionary<string, AggregateChapter> Chapters);

public sealed record AggregateChapter(
    [property: JsonPropertyName("chapter")] string? Chapter);

public sealed record MangaStatisticsResponse(
    [property: JsonPropertyName("statistics")] IReadOnlyDictionary<string, MangaStatisticsEntry> Statistics);

public sealed record MangaStatisticsEntry(
    [property: JsonPropertyName("rating")] MangaRating? Rating,
    [property: JsonPropertyName("follows")] long Follows,
    [property: JsonPropertyName("comments")] MangaComments? Comments);

public sealed record MangaRating(
    [property: JsonPropertyName("bayesian")] double Bayesian);

public sealed record MangaComments(
    [property: JsonPropertyName("chapter")] long? Chapter,
    [property: JsonPropertyName("repliesCount")] long? RepliesCount);

public sealed record ImageReport(
    [property: JsonPropertyName("url")] string Url,
    [property: JsonPropertyName("success")] bool Success,
    [property: JsonPropertyName("bytes")] int Bytes,
    [property: JsonPropertyName("cached")] bool Cached,
    [property: JsonPropertyName("duration")] long Duration);
