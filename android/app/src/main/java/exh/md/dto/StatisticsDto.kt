package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatisticsDto(
    val statistics: Map<String, StatisticsMangaDto>,
)

@Serializable
data class StatisticsMangaDto(
    val rating: StatisticsMangaRatingDto? = null,
    val follows: Long? = null,
    val comments: StatisticsCommentsDto? = null,
    val unavailableChaptersCount: Int = 0,
)

@Serializable
data class StatisticsMangaRatingDto(
    val average: Double? = null,
    val bayesian: Double? = null,
    val distribution: Map<String, Int> = emptyMap(),
)

@Serializable
data class StatisticsCommentsDto(val threadId: Long, val repliesCount: Int)
