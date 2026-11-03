package eu.kanade.domain.manga.interactor

import exh.md.handlers.AutoAddTrackers
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        val added = mangaUpdate.favorite == true && !mangaRepository.getMangaById(mangaUpdate.id).favorite
        return mangaRepository.update(mangaUpdate).also { success ->
            if (success && added) AutoAddTrackers.onAddedToLibrary(mangaUpdate.id)
        }
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        val added = mangaUpdates.filter { it.favorite == true && !mangaRepository.getMangaById(it.id).favorite }
        return mangaRepository.updateAll(mangaUpdates).also { success ->
            if (success) added.forEach { AutoAddTrackers.onAddedToLibrary(it.id) }
        }
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime),
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.toMangaUpdate(manga, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return await(
            MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
