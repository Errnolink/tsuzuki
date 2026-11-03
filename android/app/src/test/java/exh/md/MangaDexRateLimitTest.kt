package exh.md

import exh.md.network.MangaDexRateLimit
import exh.md.network.PriorityRateLimitInterceptor
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaDexRateLimitTest {
    private fun clientWith(interceptor: PriorityRateLimitInterceptor, recorder: okhttp3.Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(recorder)
            .build()

    private fun ok(request: Request): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("".toResponseBody()).build()

    @Test
    fun `a burst of twenty calls never exceeds five starts in any one second window`() {
        val starts = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val recorder = okhttp3.Interceptor { chain ->
            starts.add(System.nanoTime())
            ok(chain.request())
        }
        val limiter = PriorityRateLimitInterceptor(
            permits = MangaDexRateLimit.API_PERMITS_PER_SECOND,
            period = 1,
            unit = TimeUnit.SECONDS,
            prioritySelector = { 1 },
        )
        val client = clientWith(limiter, recorder)
        val executor = Executors.newFixedThreadPool(20)
        try {
            val futures = (1..20).map {
                executor.submit<Void> {
                    client.newCall(Request.Builder().url("https://api.mangadex.org/manga/status").build())
                        .execute().close()
                    null
                }
            }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }

        assertEquals(20, starts.size)
        val sorted = starts.sorted()
        val windowNanos = TimeUnit.SECONDS.toNanos(1)
        for (i in sorted.indices) {
            val inWindow = sorted.count { it >= sorted[i] && it - sorted[i] < windowNanos }
            assertTrue(inWindow <= 5, "window starting at call ${i + 1} saw $inWindow starts")
        }
    }

    @Test
    fun `a user priority call queued behind ten background calls starts before the queued background calls`() {
        val release = CountDownLatch(1)
        val startOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
        val recorder = okhttp3.Interceptor { chain ->
            startOrder.add(chain.request().header("X-Call") ?: "unknown")
            release.await(10, TimeUnit.SECONDS)
            ok(chain.request())
        }
        val limiter = PriorityRateLimitInterceptor(
            permits = MangaDexRateLimit.API_PERMITS_PER_SECOND,
            period = 1,
            unit = TimeUnit.SECONDS,
            prioritySelector = MangaDexRateLimit::mangaDexRequestPriority,
        )
        val client = clientWith(limiter, recorder)
        val executor = Executors.newFixedThreadPool(12)
        try {
            repeat(10) { index ->
                executor.submit<Void> {
                    client.newCall(
                        Request.Builder().url("https://api.mangadex.org/at-home/server/chapter-$index")
                            .header("X-Call", "background-$index").build(),
                    ).execute().close()
                    null
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (startOrder.size < 5 && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(5, startOrder.size)

            val user = executor.submit<Void> {
                client.newCall(
                    Request.Builder().url("https://api.mangadex.org/manga")
                        .header("X-Call", "user").build(),
                ).execute().close()
                null
            }
            val allStarted = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (startOrder.size < 11 && System.nanoTime() < allStarted) Thread.sleep(10)
            release.countDown()
            user.get(10, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }

        val userPosition = startOrder.indexOf("user")
        assertTrue(userPosition >= 0, "user call never started")
        val backgroundAfterUser = startOrder.drop(userPosition + 1).count { it.startsWith("background") }
        assertTrue(
            backgroundAfterUser >= 5,
            "user started at position $userPosition of ${startOrder.size}; $backgroundAfterUser background calls started after it",
        )
    }

    @Test
    fun `mangadex request priorities rank user actions above covers above background`() {
        fun priority(url: String) = MangaDexRateLimit.mangaDexRequestPriority(
            Request.Builder().url(url).build().url,
        )
        assertEquals(10, priority("https://api.mangadex.org/manga"))
        assertEquals(10, priority("https://api.mangadex.org/manga?title=one%20piece"))
        assertEquals(10, priority("https://api.mangadex.org/chapter/01234567-89ab-cdef-0123-456789abcdef"))
        assertEquals(10, priority("https://api.mangadex.org/user/follows/manga"))
        assertEquals(10, priority("https://api.mangadex.org/list/805ba886-dd99-4aa4-b460-4bd7c7b71352"))
        assertEquals(10, priority("https://antsylich.github.io/mangadex-seasonal/seasonal-list.min.json"))
        assertEquals(5, priority("https://uploads.mangadex.org/covers/abc/cover.jpg"))
        assertEquals(1, priority("https://api.mangadex.org/at-home/server/01234567-89ab-cdef-0123-456789abcdef"))
        assertEquals(1, priority("https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"))
    }
}
