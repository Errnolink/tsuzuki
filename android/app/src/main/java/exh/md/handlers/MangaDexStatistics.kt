package exh.md.handlers

data class MangaDexStatistics(
    val rating: Double?,
    val follows: Long?,
    val distribution: Map<Int, Int>,
    val threadId: Long?,
    val comments: Int,
    val estimatedMissing: Int?,
    val unavailable: Int,
)
