package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarkStatusDto(
    val chapterIdsRead: List<String>,
    val chapterIdsUnread: List<String>,
)

@Serializable
data class CustomListPageDto(
    val data: List<CustomListDto>,
    val limit: Int,
    val offset: Int,
    val total: Int,
)

@Serializable
data class CustomListResponseDto(val data: CustomListDto)

@Serializable
data class CustomListDto(
    val id: String,
    val attributes: CustomListAttributesDto,
    val relationships: List<RelationshipDto> = emptyList(),
) {
    val mangaIds: List<String>
        get() = relationships.filter { it.type == "manga" }.map { it.id }.distinct()
}

@Serializable
data class CustomListAttributesDto(
    val name: String,
    val visibility: String,
)

@Serializable
data class ForumThreadDto(val id: String, val type: String)

@Serializable
data class ForumThreadResponseDto(val data: ForumThreadIdDto)

@Serializable
data class ForumThreadIdDto(val id: Long)

@Serializable
data class RecommendationListDto(
    val data: List<RecommendationDto>,
    val offset: Int,
    val total: Int,
)

@Serializable
data class RecommendationDto(
    val attributes: RecommendationAttributesDto,
    val relationships: List<RelationshipDto>,
)

@Serializable
data class RecommendationAttributesDto(val score: Double)

@Serializable
data class LookupPageDto(
    val data: List<LookupEntryDto>,
    val offset: Int,
    val total: Int,
)

@Serializable
data class LookupEntryDto(val id: String, val attributes: IncludesAttributesDto)

@Serializable
data class SeasonalDto(val id: String, val name: String)
