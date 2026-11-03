package dev.errnolink.tsuzuki.mangadex

import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.MangaDexPreferences
import exh.md.dto.AggregateDto
import exh.md.dto.MangaDto
import exh.md.service.MangaDexFeatureRequests
import exh.md.service.MangaDexService
import exh.md.utils.MdUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

internal object MangaDexSharedFetch {
    private const val TTL_MS = 15_000L
    private const val MAX_ENTRIES = 128
    internal var clock: () -> Long = { System.nanoTime() / 1_000_000L }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = ConcurrentHashMap<String, Call>()

    private class Call(val atMs: Long, val deferred: Deferred<*>)

    suspend fun viewManga(client: OkHttpClient, mangaId: String): MangaDto =
        shared("manga/$mangaId") {
            MangaDexService(client, MangaDexFeatureRequests.headers).viewManga(mangaId)
        }

    suspend fun aggregate(client: OkHttpClient, mangaId: String, sourceLanguage: String): AggregateDto =
        shared("aggregate/$mangaId/$sourceLanguage") {
            val languages = MangaDexPreferences().languages(sourceLanguage)
            with(MdUtil.jsonParser) {
                client.newCall(MangaDexFeatureRequests.aggregate(mangaId, languages))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    internal suspend fun <T : Any> shared(key: String, block: suspend () -> T): T = deferred(key, block).await()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> deferred(key: String, block: suspend () -> T): Deferred<T> {
        sweep()
        calls[key]?.takeIf { clock() - it.atMs <= TTL_MS }?.let { return it.deferred as Deferred<T> }
        val fresh = CompletableDeferred<T>()
        val winner = calls.compute(key) { _, existing ->
            if (existing != null && clock() - existing.atMs <= TTL_MS) existing else Call(clock(), fresh)
        }!!
        if (winner.deferred !== fresh) return winner.deferred as Deferred<T>
        scope.launch {
            try {
                fresh.complete(block())
            } catch (t: Throwable) {
                calls.compute(key) { _, held -> if (held?.deferred === fresh) null else held }
                fresh.completeExceptionally(t)
            }
        }
        return fresh
    }

    private fun sweep() {
        if (calls.size <= MAX_ENTRIES) return
        val now = clock()
        val iterator = calls.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.atMs > TTL_MS) iterator.remove()
        }
    }
}
