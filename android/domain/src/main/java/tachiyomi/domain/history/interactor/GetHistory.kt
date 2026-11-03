package tachiyomi.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

class GetHistory(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long): List<History> {
        return repository.getHistoryByMangaId(mangaId)
    }

    fun subscribe(
        query: String,
        // KMK -->
        unfinishedManga: Boolean?,
        unfinishedChapter: Boolean?,
        nonLibraryEntries: Boolean?,
        // KMK <--
    ): Flow<List<HistoryWithRelations>> {
        return repository.getHistory(
            query,
            // KMK -->
            unfinishedManga,
            unfinishedChapter,
            nonLibraryEntries,
            // KMK <--
        )
    }

    fun subscribeLatest(): Flow<HistoryWithRelations?> {
        return repository.subscribeToLastHistory()
    }

    fun subscribe(
        mangaIds: Collection<Long>,
    ): Flow<List<HistoryWithRelations>> {
        return repository.getLatestHistoryByMangaIds(mangaIds)
    }

    fun subscribeRecent(
        limit: Int,
        unfinishedChapter: Boolean? = true,
        nonLibraryEntries: Boolean? = false,
    ): Flow<List<HistoryWithRelations>> {
        return repository.getRecentHistory(
            query = "",
            unfinishedManga = null,
            unfinishedChapter = unfinishedChapter,
            nonLibraryEntries = nonLibraryEntries,
            limit = limit.toLong(),
        )
    }
}
