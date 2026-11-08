package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterReadSync

class UpdateChapter(
    private val chapterRepository: ChapterRepository,
    private val readSync: ChapterReadSync,
) {

    suspend fun await(chapterUpdate: ChapterUpdate) {
        try {
            val forwarded = forReadSync(chapterUpdate)
            chapterRepository.update(chapterUpdate)
            if (forwarded != null) readSync.onReadStatusChanged(listOf(forwarded))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(chapterUpdates: List<ChapterUpdate>) {
        try {
            val forwarded = chapterUpdates.mapNotNull { forReadSync(it) }
            chapterRepository.updateAll(chapterUpdates)
            if (forwarded.isNotEmpty()) readSync.onReadStatusChanged(forwarded)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun forReadSync(update: ChapterUpdate): ChapterUpdate? {
        if (update.read == null && update.lastPageRead == null) return null
        val readChanged = update.read != null && chapterRepository.getChapterById(update.id)?.read != update.read
        if (!readChanged && update.lastPageRead == null) return null
        return update.copy(read = update.read.takeIf { readChanged })
    }
}
