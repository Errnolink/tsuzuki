package dev.errnolink.tsuzuki.mangadex

fun missingChapterEstimate(chapters: Sequence<String>, lastChapter: Int? = null): Int? {
    val numbers = chapters.mapNotNull { text ->
        text.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= Int.MAX_VALUE }
            ?.toInt()?.takeIf { it > 0 }
    }.toSet()
    if (numbers.isEmpty()) return if (lastChapter == null || lastChapter == 0) 0 else null
    val highest = maxOf(numbers.maxOrNull() ?: 0, lastChapter ?: 0)
    if (highest.toLong() > numbers.size.toLong() * 10) return null
    return (highest - numbers.size).coerceAtLeast(0)
}
