package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class SimilarMangaDto(
    val id: String,
    val title: Map<String, String>,
    val contentRating: String,
    val matches: List<SimilarMangaMatchListDto>,
    val updatedAt: String,
)

@Serializable
data class SimilarMangaMatchListDto(
    val id: String,
    val title: Map<String, String> = emptyMap(),
    val contentRating: String = "",
    val score: Double = 0.0,
    val languages: List<String> = emptyList(),
)

@Serializable
data class RelationListDto(
    val response: String,
    val data: List<RelationDto>,
)

@Serializable
data class RelationDto(
    val attributes: RelationAttributesDto,
    val relationships: List<RelationMangaDto>,
)

@Serializable
data class RelationMangaDto(
    val id: String,
)

@Serializable
data class RelationAttributesDto(
    val relation: String,
)
