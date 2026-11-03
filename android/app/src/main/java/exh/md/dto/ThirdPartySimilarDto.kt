package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class ThirdPartySimilarRec(
    val title: String,
    val source: String,
    val scoreOrVotes: String,
    val mangadexId: String? = null,
    val originalId: String? = null,
    val originalUrl: String? = null,
    val coverUrl: String? = null,
)

@Serializable
data class AnilistMangaRecommendationsDto(val data: AnilistMediaWrapper)

@Serializable
data class AnilistMediaWrapper(val Media: AnilistMediaRecsWrapper?)

@Serializable
data class AnilistMediaRecsWrapper(val recommendations: AnilistEdgesWrapper?)

@Serializable
data class AnilistEdgesWrapper(val edges: List<AnilistEdgeDto> = emptyList())

@Serializable
data class AnilistEdgeDto(val node: AnilistNodeDto)

@Serializable
data class AnilistNodeDto(
    val mediaRecommendation: AnilistMediaRecDto,
    val rating: Int = 0,
)

@Serializable
data class AnilistMediaRecDto(
    val id: Long,
    val format: String = "",
    val title: AnilistTitleDto? = null,
    val coverImage: AnilistCoverImageDto? = null,
)

@Serializable
data class AnilistTitleDto(
    val english: String? = null,
    val romaji: String? = null,
)

@Serializable
data class AnilistCoverImageDto(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class MalMangaRecommendationsDto(
    val data: List<MalMangaRecommendationDto> = emptyList(),
)

@Serializable
data class MalMangaRecommendationDto(
    val entry: MalMangaDto,
    val url: String = "",
    val votes: Int = 0,
)

@Serializable
data class MalMangaDto(
    val mal_id: Long,
    val url: String = "",
    val title: String = "",
    val images: MalImagesDto? = null,
)

@Serializable
data class MalImagesDto(
    val jpg: MalImageDto? = null,
    val webp: MalImageDto? = null,
)

@Serializable
data class MalImageDto(
    val image_url: String? = null,
    val small_image_url: String? = null,
    val large_image_url: String? = null,
)

@Serializable
data class MUMangaDto(
    val recommendations: List<MURecommendationDto> = emptyList(),
    val category_recommendations: List<MUCategoryRecommendationDto> = emptyList(),
)

@Serializable
data class MURecommendationDto(
    val series_name: String,
    val series_id: Long,
    val weight: Long = 0,
)

@Serializable
data class MUCategoryRecommendationDto(
    val series_name: String,
    val series_id: Long,
    val weight: Long = 0,
)
