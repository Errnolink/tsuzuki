package eu.kanade.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler

class GetExcludedScanlators(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .flatMap { it.split(" & ") }.filter(String::isNotBlank).toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return handler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .map { names -> names.flatMap { it.split(" & ") }.filter(String::isNotBlank).toSet() }
    }

    suspend fun awaitFilteredCombinations(mangaId: Long): Set<String> =
        handler.awaitList { excludedScanlatorsViewQueries.getExcludedCombinations(mangaId) }
            .mapNotNullTo(HashSet()) { it.scanlator }
}
