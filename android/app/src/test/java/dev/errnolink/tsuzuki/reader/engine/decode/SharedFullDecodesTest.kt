// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/data/coil/SharedFullDecodesTest.kt
package dev.errnolink.tsuzuki.reader.engine.decode

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private typealias Decode = SharedWork<String?>.Run.() -> String?

class SharedFullDecodesTest {

    private val decodes = SharedFullDecodes<String>()
    private val callers = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runs = AtomicInteger()
    private val key = "1_0"

    @AfterEach
    fun tearDown() {
        callers.cancel()
    }

    private fun decoded(image: String): Decode = {
        runs.incrementAndGet()
        image
    }

    private val failed: Decode = {
        runs.incrementAndGet()
        null
    }

    @Test
    fun `a decoded page is returned`() = runBlocking {
        assertEquals("page", decodes.await(key, retryGeneration = 0, decode = decoded("page")))
    }

    @Test
    fun `a page whose decode failed is not decoded again`() = runBlocking {
        assertNull(decodes.await(key, retryGeneration = 0, decode = failed))

        assertNull(decodes.await(key, retryGeneration = 0, decode = decoded("page")))
        assertEquals(1, runs.get())
    }

    @Test
    fun `a failed page does not stop other pages from decoding`() = runBlocking {
        decodes.await(key, retryGeneration = 0, decode = failed)

        assertEquals(
            "other page",
            decodes.await("1_1", retryGeneration = 0, decode = decoded("other page")),
        )
    }

    @Test
    fun `a retry decodes a failed page again`() = runBlocking {
        decodes.await(key, retryGeneration = 0, decode = failed)

        assertEquals("page", decodes.await(key, retryGeneration = 1, decode = decoded("page")))
        assertEquals(2, runs.get())
    }

    @Test
    fun `a retry that fails again marks the page again`() = runBlocking {
        decodes.await(key, retryGeneration = 0, decode = failed)
        decodes.await(key, retryGeneration = 1, decode = failed)

        assertNull(decodes.await(key, retryGeneration = 1, decode = decoded("page")))
        assertEquals(2, runs.get())
    }

    @Test
    fun `a page asked for below its failed retry stays failed`() = runBlocking {
        decodes.await(key, retryGeneration = 3, decode = failed)

        assertNull(decodes.await(key, retryGeneration = 0, decode = decoded("page")))
        assertEquals(1, runs.get())
    }

    @Test
    fun `a page that decodes after a retry is no longer marked failed`() = runBlocking {
        decodes.await(key, retryGeneration = 0, decode = failed)
        decodes.await(key, retryGeneration = 1, decode = decoded("page"))

        assertEquals("page", decodes.await(key, retryGeneration = 0, decode = decoded("page")))
        assertEquals(3, runs.get())
    }

    @Test
    fun `slices of one retry share one decode`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first =
            callers.async(start = CoroutineStart.UNDISPATCHED) {
                decodes.await(key, retryGeneration = 1) {
                    runs.incrementAndGet()
                    started.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    "page"
                }
            }
        assertTrue(started.await(5, TimeUnit.SECONDS)) { "decode did not start" }
        val second =
            callers.async(start = CoroutineStart.UNDISPATCHED) {
                decodes.await(key, retryGeneration = 1, decode = decoded("second decode"))
            }

        release.countDown()

        assertEquals("page", withTimeout(5_000) { first.await() })
        assertEquals("page", withTimeout(5_000) { second.await() })
        assertEquals(1, runs.get())
    }

    @Test
    fun `a retry does not wait for a decode started before it`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val before =
            callers.async(start = CoroutineStart.UNDISPATCHED) {
                decodes.await(key, retryGeneration = 0) {
                    started.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    null
                }
            }
        assertTrue(started.await(5, TimeUnit.SECONDS)) { "decode did not start" }

        assertEquals(
            "page",
            withTimeout(5_000) {
                decodes.await(key, retryGeneration = 1, decode = decoded("page"))
            },
        )

        release.countDown()
        assertNull(withTimeout(5_000) { before.await() })
        assertEquals("page", decodes.await(key, retryGeneration = 1, decode = decoded("page")))
    }

    @Test
    fun `clear forgets pages whose decode failed`() = runBlocking {
        decodes.await(key, retryGeneration = 0, decode = failed)

        decodes.clear()

        assertEquals("page", decodes.await(key, retryGeneration = 0, decode = decoded("page")))
    }

    @Test
    fun `a decode that fails after clear does not mark the page`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller =
            callers.async(start = CoroutineStart.UNDISPATCHED) {
                decodes.await(key, retryGeneration = 0) {
                    started.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    null
                }
            }
        assertTrue(started.await(5, TimeUnit.SECONDS)) { "decode did not start" }

        decodes.clear()
        release.countDown()

        assertNull(withTimeout(5_000) { caller.await() })
        assertEquals("page", decodes.await(key, retryGeneration = 0, decode = decoded("page")))
    }
}
