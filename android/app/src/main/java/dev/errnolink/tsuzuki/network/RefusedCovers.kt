package dev.errnolink.tsuzuki.network

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

class RefusedCovers(
    private val holdMillis: Long = 30 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refusedAt = ConcurrentHashMap<String, Long>()

    fun check(url: String) {
        val at = refusedAt[url] ?: return
        if (now() - at < holdMillis) throw IOException("Cover refused recently, not asking again yet")
        refusedAt.remove(url, at)
    }

    fun onResponse(url: String, code: Int) {
        if (code in RefusingCodes) refusedAt[url] = now() else if (code in 200..399) refusedAt.remove(url)
    }

    fun onFailure(url: String, error: Throwable) {
        if (error is SSLException) refusedAt[url] = now()
    }

    fun forget(url: String) {
        refusedAt.keys.removeAll { it == url || it.startsWith("$url.") }
    }

    companion object {
        private val RefusingCodes = setOf(403, 404, 410)
        val shared = RefusedCovers()
    }
}
