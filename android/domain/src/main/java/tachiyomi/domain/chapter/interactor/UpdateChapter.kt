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
            val changed = chapterUpdate.lastPageRead != null || (chapterUpdate.read != null &&
                chapterRepository.getChapterById(chapterUpdate.id)?.read != chapterUpdate.read)
            chapterRepository.update(chapterUpdate)
            if (changed) readSync.onReadStatusChanged(listOf(chapterUpdate))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(chapterUpdates: List<ChapterUpdate>) {
        try {
            val changed = chapterUpdates.filter {
                it.lastPageRead != null || (it.read != null && chapterRepository.getChapterById(it.id)?.read != it.read)
            }
            chapterRepository.updateAll(chapterUpdates)
            if (changed.isNotEmpty()) readSync.onReadStatusChanged(changed)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
