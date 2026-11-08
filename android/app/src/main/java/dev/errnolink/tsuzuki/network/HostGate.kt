package dev.errnolink.tsuzuki.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

class HostGate(
    private val permits: Int,
    private val spacingMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Lane(permits: Int) {
        val slots = Semaphore(permits)
        val start = Mutex()
        var nextStart = 0L
    }

    private val lanes = ConcurrentHashMap<String, Lane>()

    suspend fun <T> pass(url: String?, block: suspend () -> T): T {
        val host = url?.toHttpUrlOrNull()?.host ?: return block()
        val lane = lanes.getOrPut(host) { Lane(permits) }
        return lane.slots.withPermit {
            lane.start.withLock {
                val wait = lane.nextStart - now()
                if (wait > 0) delay(wait)
                lane.nextStart = now() + spacingMillis
            }
            block()
        }
    }

    companion object {
        val pages = HostGate(permits = 2, spacingMillis = 350)
        val covers = HostGate(permits = 4, spacingMillis = 120)
    }
}
