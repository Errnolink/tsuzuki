package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.MergedMangaReference

class GetMergedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
    private val getMergedReferencesById: GetMergedReferencesById,
) {

    suspend fun await(
        mangaId: Long,
        dedupe: Boolean = true,
        /** Filter excluded scanlators & bookmarked/unbookmarked filter */
        applyFilter: Boolean = false,
    ): List<Chapter> {
        return transformMergedChapters(
            getMergedReferencesById.await(mangaId),
            getFromDatabase(mangaId, applyFilter),
            dedupe,
        )
    }

    suspend fun subscribe(
        mangaId: Long,
        dedupe: Boolean = true,
        /** Filter excluded scanlators & bookmarked/unbookmarked filter */
        applyFilter: Boolean = false,
    ): Flow<List<Chapter>> {
        return try {
            chapterRepository.getMergedChapterByMangaIdAsFlow(mangaId, applyFilter)
                .combine(getMergedReferencesById.subscribe(mangaId)) { chapters, references ->
                    transformMergedChapters(references, chapters, dedupe)
                }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            flowOf(emptyList())
        }
    }

    private suspend fun getFromDatabase(
        mangaId: Long,
        /** Filter excluded scanlators & bookmarked/unbookmarked filter */
        applyFilter: Boolean = false,
    ): List<Chapter> {
        return try {
            chapterRepository.getMergedChapterByMangaId(mangaId, applyFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    private fun transformMergedChapters(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
        dedupe: Boolean,
    ): List<Chapter> {
        return if (dedupe) dedupeChapterList(mangaReferences, chapterList) else chapterList
    }

    private fun dedupeChapterList(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
    ): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }?.chapterSortMode) {
            MergedMangaReference.CHAPTER_SORT_NONE -> chapterList
            MergedMangaReference.CHAPTER_SORT_PRIORITY -> dedupeByPriority(mangaReferences, chapterList)
            MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> {
                findSourceWithMostChapters(mangaReferences, chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> {
                findSourceWithHighestChapterNumber(mangaReferences, chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            else -> chapterList
        }
    }

    // KMK -->
    private fun referenceFor(
        mangaReferences: List<MergedMangaReference>,
        mangaId: Long,
    ): MergedMangaReference? = mangaReferences.firstOrNull { it.mangaId == mangaId }
    // KMK <--

    private fun findSourceWithMostChapters(
        // KMK -->
        mangaReferences: List<MergedMangaReference>,
        // KMK <--
        chapterList: List<Chapter>,
    ): Long? {
        // KMK -->
        // Stable portable tie-breaks: child source id, then manga URL.
        return chapterList.groupBy { it.mangaId }.entries
            .sortedWith(
                compareByDescending<Map.Entry<Long, List<Chapter>>> { it.value.size }
                    .thenBy { referenceFor(mangaReferences, it.key)?.mangaSourceId ?: Long.MAX_VALUE }
                    .thenBy { referenceFor(mangaReferences, it.key)?.mangaUrl.orEmpty() },
            )
            .firstOrNull()?.key
        // KMK <--
    }

    private fun findSourceWithHighestChapterNumber(
        // KMK -->
        mangaReferences: List<MergedMangaReference>,
        // KMK <--
        chapterList: List<Chapter>,
    ): Long? {
        // KMK -->
        // Stable portable tie-breaks: child source id, then manga URL.
        return chapterList
            .sortedWith(
                compareByDescending<Chapter> { it.chapterNumber }
                    .thenBy { referenceFor(mangaReferences, it.mangaId)?.mangaSourceId ?: Long.MAX_VALUE }
                    .thenBy { referenceFor(mangaReferences, it.mangaId)?.mangaUrl.orEmpty() },
            )
            .firstOrNull()?.mangaId
        // KMK <--
    }

    private fun dedupeByPriority(
        mangaReferences: List<MergedMangaReference>,
        chapterList: List<Chapter>,
    ): List<Chapter> {
        val sortedChapterList = mutableListOf<Chapter>()

        var existingChapterIndex: Int
        chapterList.groupBy { it.mangaId }
            .entries
            // KMK -->
            .sortedWith(
                compareBy(
                    { (mangaId) -> mangaReferences.find { it.mangaId == mangaId }?.chapterPriority ?: Int.MAX_VALUE },
                    { (mangaId) -> mangaReferences.find { it.mangaId == mangaId }?.mangaSourceId ?: Long.MAX_VALUE },
                    { (mangaId) -> mangaReferences.find { it.mangaId == mangaId }?.mangaUrl.orEmpty() },
                ),
            )
            // KMK <--
            .forEach { (_, chapters) ->
                existingChapterIndex = -1
                chapters.forEach { chapter ->
                    val oldChapterIndex = existingChapterIndex
                    if (chapter.isRecognizedNumber) {
                        existingChapterIndex = sortedChapterList.indexOfFirst {
                            // check if the chapter is not already there
                            it.isRecognizedNumber &&
                                it.chapterNumber == chapter.chapterNumber &&
                                // allow multiple chapters of the same number from the same source
                                it.mangaId != chapter.mangaId
                        }
                        if (existingChapterIndex == -1) {
                            sortedChapterList.add(oldChapterIndex + 1, chapter)
                            existingChapterIndex = oldChapterIndex + 1
                        }
                    } else {
                        sortedChapterList.add(oldChapterIndex + 1, chapter)
                        existingChapterIndex = oldChapterIndex + 1
                    }
                }
            }

        return sortedChapterList.mapIndexed { index, chapter ->
            chapter.copy(sourceOrder = index.toLong())
        }
    }
}
