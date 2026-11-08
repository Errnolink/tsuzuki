package tachiyomi.domain.chapter.service

import exh.source.MERGED_SOURCE_ID
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

fun getChapterSort(
    manga: Manga,
    sortDescending: Boolean = manga.sortDescending(),
): (
    Chapter,
    Chapter,
) -> Int {
    return when (manga.sorting) {
        Manga.CHAPTER_SORTING_SOURCE -> when {
            manga.source == MERGED_SOURCE_ID -> mergedSourceSort(sortDescending)
            sortDescending -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            else -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapterNumber.compareTo(c1.chapterNumber) }
            false -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
        }
        Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        Manga.CHAPTER_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c1, c2 -> c2.name.compareToWithCollator(c1.name) }
            false -> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        }
        else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
    }
}

private fun mergedSourceSort(sortDescending: Boolean): (Chapter, Chapter) -> Int {
    val oldestFirst = compareBy<Chapter>({ it.chapterNumber }, { it.dateUpload }, { -it.sourceOrder })
    val comparator = if (sortDescending) oldestFirst.reversed() else oldestFirst
    return comparator::compare
}
