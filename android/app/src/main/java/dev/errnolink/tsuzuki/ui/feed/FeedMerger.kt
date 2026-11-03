package dev.errnolink.tsuzuki.ui.feed

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class FeedChip {
    ALL,
    LIBRARY,
    MANGADEX,
    UNREAD,
}

data class ChapterIdentity(
    val sourceId: Long,
    val chapterUrl: String,
)

data class FeedChapterItem(
    val id: String,
    val mangaId: Long,
    val chapterId: Long?,
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Double,
    val dateFetch: Long,
    val coverUrl: String?,
    val isLibrary: Boolean,
    val isRead: Boolean,
    val scanlator: String? = null,
    val pageCount: Int? = null,
    val mangaDexId: String? = null,
    val sourceId: Long = 0L,
    val chapterUrl: String = "",
    val coverLastModified: Long = 0L,
) {
    val identity: ChapterIdentity get() = ChapterIdentity(sourceId, chapterUrl)
}

data class FeedTitleGroup(
    val mangaId: Long,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapters: List<FeedChapterItem>,
    val isLibrary: Boolean,
    val mangaDexId: String? = null,
    val sourceId: Long = 0L,
    val coverLastModified: Long = 0L,
) {
    val groupKey: String = groupKeyOf(mangaId, mangaDexId, mangaTitle)
    val unreadCount: Int = chapters.count { !it.isRead }
    val newCount: Int = chapters.size
    val oldestUnreadChapter: FeedChapterItem? =
        chapters.filter { !it.isRead }.minByOrNull { it.chapterNumber.takeIf { n -> n >= 0 } ?: Double.MAX_VALUE }
            ?: chapters.minByOrNull { it.chapterNumber.takeIf { n -> n >= 0 } ?: Double.MAX_VALUE }
}

object FeedMerger {

    fun normalizeChapterNumber(num: Double): Double =
        if (num >= 0.0) num else -1.0

    fun identityKey(sourceId: Long, chapterUrl: String): ChapterIdentity =
        ChapterIdentity(sourceId, chapterUrl)

    fun merge(
        libraryItems: List<FeedChapterItem>,
        followsItems: List<FeedChapterItem>,
        libraryMangaIds: Set<Long> = emptySet(),
        libraryMangaDexUuids: Set<String> = emptySet(),
    ): List<FeedChapterItem> {
        val seen = mutableSetOf<ChapterIdentity>()
        val result = mutableListOf<FeedChapterItem>()

        for (item in libraryItems) {
            val key = item.identity
            if (seen.add(key)) {
                result.add(item)
            }
        }

        for (item in followsItems) {
            val key = item.identity
            if (seen.add(key)) {
                val isFav = item.isLibrary ||
                    (item.mangaId > 0 && item.mangaId in libraryMangaIds) ||
                    (item.mangaDexId != null && item.mangaDexId in libraryMangaDexUuids)
                result.add(if (isFav != item.isLibrary) item.copy(isLibrary = isFav) else item)
            }
        }

        return result
    }

    fun filter(
        items: List<FeedChapterItem>,
        chip: FeedChip,
        isLoggedIn: Boolean,
        hideInLibrary: Boolean = false,
    ): List<FeedChapterItem> {
        val effectiveChip = if (!isLoggedIn && chip == FeedChip.MANGADEX) FeedChip.ALL else chip
        return when (effectiveChip) {
            FeedChip.ALL -> if (hideInLibrary) items.filterNot { it.isLibrary } else items
            FeedChip.MANGADEX -> items.filter { it.mangaDexId != null }
                .let { if (hideInLibrary) it.filterNot { item -> item.isLibrary } else it }
            FeedChip.LIBRARY -> items.filter { it.isLibrary }
            FeedChip.UNREAD -> items.filter { !it.isRead }
        }
    }

    fun groupByDayAndTitle(
        items: List<FeedChapterItem>,
        zone: ZoneId = ZoneOffset.UTC,
    ): Map<LocalDate, List<FeedTitleGroup>> {
        val byDay = items.groupBy { item -> Instant.ofEpochMilli(item.dateFetch).atZone(zone).toLocalDate() }

        return byDay.mapValues { (_, dayItems) ->
            dayItems.groupBy { item -> groupKeyOf(item.mangaId, item.mangaDexId, item.mangaTitle) }
                .map { (_, titleChapters) ->
                    val first = titleChapters.first()
                    FeedTitleGroup(
                        mangaId = first.mangaId,
                        mangaTitle = first.mangaTitle,
                        coverUrl = first.coverUrl,
                        chapters = titleChapters.sortedByDescending { it.chapterNumber },
                        isLibrary = titleChapters.any { it.isLibrary },
                        mangaDexId = titleChapters.mapNotNull { it.mangaDexId }.firstOrNull(),
                        sourceId = first.sourceId,
                        coverLastModified = first.coverLastModified,
                    )
                }
        }
    }
}

fun groupKeyOf(mangaId: Long, mangaDexId: String?, mangaTitle: String): String =
    if (mangaId > 0) "id:$mangaId" else "md:${mangaDexId ?: mangaTitle}"
