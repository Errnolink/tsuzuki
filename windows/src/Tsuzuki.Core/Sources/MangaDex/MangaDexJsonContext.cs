using System.Text.Json.Serialization;

namespace Tsuzuki.Core.Sources.MangaDex;

[JsonSourceGenerationOptions(GenerationMode = JsonSourceGenerationMode.Metadata)]
[JsonSerializable(typeof(MangaListResponse))]
[JsonSerializable(typeof(MangaResponse))]
[JsonSerializable(typeof(ChapterListResponse))]
[JsonSerializable(typeof(AtHomeResponse))]
[JsonSerializable(typeof(AggregateResponse))]
[JsonSerializable(typeof(MangaStatisticsResponse))]
[JsonSerializable(typeof(ImageReport))]
internal sealed partial class MangaDexJsonContext : JsonSerializerContext;
