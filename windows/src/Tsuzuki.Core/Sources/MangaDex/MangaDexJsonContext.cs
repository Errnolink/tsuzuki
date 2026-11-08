using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Sources.MangaDex;

[JsonSourceGenerationOptions(GenerationMode = JsonSourceGenerationMode.Metadata)]
[JsonSerializable(typeof(MangaListResponse))]
[JsonSerializable(typeof(MangaResponse))]
[JsonSerializable(typeof(TagListResponse))]
[JsonSerializable(typeof(ChapterListResponse))]
[JsonSerializable(typeof(AtHomeResponse))]
[JsonSerializable(typeof(AggregateResponse))]
[JsonSerializable(typeof(MangaStatisticsResponse))]
[JsonSerializable(typeof(ImageReport))]
[JsonSerializable(typeof(Dictionary<string, string>))]
[JsonSerializable(typeof(Dictionary<string, AggregateVolume>))]
[JsonSerializable(typeof(Dictionary<string, AggregateChapter>))]
internal sealed partial class MangaDexJsonContext : JsonSerializerContext;
