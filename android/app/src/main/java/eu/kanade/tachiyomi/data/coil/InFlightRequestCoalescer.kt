package eu.kanade.tachiyomi.data.coil

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

object InFlightRequestCoalescer {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    suspend fun <T> coalesce(
        key: String,
        block: suspend () -> T,
        onCoalesced: suspend () -> T,
    ): T {
        val deferred = CompletableDeferred<Unit>()
        val existing = inFlight.putIfAbsent(key, deferred)
        if (existing != null) {
            try {
                existing.await()
            } catch (e: Throwable) {
                if (!currentCoroutineContext().isActive) throw e
                return block()
            }
            return onCoalesced()
        }
        try {
            val result = block()
            deferred.complete(Unit)
            return result
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(key, deferred)
        }
    }
}
