package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetIdsOfFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Long> {
        return mangaMetadataRepository.getIdsOfFavoriteMangaWithMetadata()
    }

    fun subscribe(): Flow<List<Long>> {
        return mangaMetadataRepository.subscribeIdsOfFavoriteMangaWithMetadata()
    }
}
