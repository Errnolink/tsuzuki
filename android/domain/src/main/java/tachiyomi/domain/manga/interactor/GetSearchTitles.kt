package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import exh.metadata.sql.models.SearchTitle
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetSearchTitles(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTitle> {
        return mangaMetadataRepository.getTitlesById(mangaId)
    }

    suspend fun awaitFavorites(): Map<Long, List<SearchTitle>> {
        return mangaMetadataRepository.getFavoriteTitles()
    }

    fun subscribeFavorites(): Flow<Map<Long, List<SearchTitle>>> {
        return mangaMetadataRepository.subscribeFavoriteTitles()
    }
}
