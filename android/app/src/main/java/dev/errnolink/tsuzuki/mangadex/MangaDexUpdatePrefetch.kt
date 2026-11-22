package dev.errnolink.tsuzuki.mangadex

import exh.md.dto.MangaDto
import exh.md.dto.StatisticsMangaDto
import exh.md.service.MangaDexService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A library update refreshes every title's details. One by one that is three API requests per title
 * (details, statistics, chapter aggregate) on top of its chapter feed. Before the update, [around]
 * fetches details and statistics for 100 titles per request; each title's refresh then takes its
 * entry once ([takeManga], [takeStatistics]) and falls back to its own request when a batch failed.
 * Entries never outlive the update.
 */
object MangaDexUpdatePrefetch {
    private const val BATCH = 100

    private val manga = ConcurrentHashMap<String, MangaDto>()
    private val statistics = ConcurrentHashMap<String, StatisticsMangaDto>()

    suspend fun <T> around(service: MangaDexService, mangaIds: List<String>, block: suspend () -> T): T {
        val ids = mangaIds.distinct()
        try {
            prime(service, ids)
            return block()
        } finally {
            ids.forEach {
                manga.remove(it)
                statistics.remove(it)
            }
        }
    }

    fun takeManga(mangaId: String): MangaDto? = manga.remove(mangaId)

    fun takeStatistics(mangaId: String): StatisticsMangaDto? = statistics.remove(mangaId)

    private suspend fun prime(service: MangaDexService, ids: List<String>) = coroutineScope {
        ids.chunked(BATCH).map { batch ->
            async {
                orNull { service.viewMangasDetailed(batch) }?.data?.forEach { manga[it.id] = MangaDto("ok", it) }
                orNull { service.mangasRating(*batch.toTypedArray()) }?.statistics?.forEach { (id, stats) -> statistics[id] = stats }
            }
        }.awaitAll()
    }

    private inline fun <T> orNull(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
