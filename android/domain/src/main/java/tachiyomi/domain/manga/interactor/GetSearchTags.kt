package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import exh.metadata.sql.models.SearchTag
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetSearchTags(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTag> {
        return mangaMetadataRepository.getTagsById(mangaId)
    }

    suspend fun awaitFavorites(): Map<Long, List<SearchTag>> {
        return mangaMetadataRepository.getFavoriteTags()
    }

    fun subscribeFavorites(): Flow<Map<Long, List<SearchTag>>> {
        return mangaMetadataRepository.subscribeFavoriteTags()
    }
}
