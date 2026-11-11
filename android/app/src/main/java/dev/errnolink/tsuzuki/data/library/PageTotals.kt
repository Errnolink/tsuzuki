package dev.errnolink.tsuzuki.data.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PageTotals(
    private val handler: DatabaseHandler = Injekt.get(),
) {

    suspend fun record(chapterId: Long, totalPages: Int) {
        if (chapterId <= 0L || totalPages <= 0) return
        handler.await {
            tsuzuki_page_totalsQueries.upsert(chapterId, totalPages.toLong())
        }
    }

    suspend fun totals(chapterIds: Collection<Long>): Map<Long, Int> {
        if (chapterIds.isEmpty()) return emptyMap()
        val result = HashMap<Long, Int>(chapterIds.size)
        for (chunk in chapterIds.chunked(QUERY_CHUNK)) {
            handler.awaitList {
                tsuzuki_page_totalsQueries.totalsByChapterIds(chunk)
            }.forEach { result[it.chapter_id] = it.total_pages.toInt() }
        }
        return result
    }

    fun inProgressTotals(): Flow<Map<Long, Int>> =
        handler.subscribeToList(debounceMillis = DEBOUNCE_MILLIS) {
            tsuzuki_page_totalsQueries.totalsForInProgress()
        }.map { rows ->
            val result = HashMap<Long, Int>(rows.size)
            rows.forEach { result[it.chapter_id] = it.total_pages.toInt() }
            result
        }

    suspend fun carryOver(legacyValue: String) {
        val parts = legacyValue.split(':')
        if (parts.size != 2) return
        val chapterId = parts[0].toLongOrNull() ?: return
        val totalPages = parts[1].toIntOrNull() ?: return
        record(chapterId, totalPages)
    }

    companion object {
        private const val QUERY_CHUNK = 900
        private const val DEBOUNCE_MILLIS = 300L
    }
}
