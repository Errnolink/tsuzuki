package dev.errnolink.tsuzuki.mangadex

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MangaDexSharedFetchTest {
    @AfterEach
    fun restoreClock() {
        MangaDexSharedFetch.clock = { System.nanoTime() / 1_000_000L }
    }

    @Test
    fun `concurrent callers with the same key issue one shared call`() {
        val issues = AtomicInteger(0)
        val results = runBlocking {
            coroutineScope {
                val a = async { MangaDexSharedFetch.shared("test/one") { issues.incrementAndGet(); delay(200); "value" } }
                val b = async { MangaDexSharedFetch.shared("test/one") { issues.incrementAndGet(); delay(200); "value" } }
                listOf(a.await(), b.await())
            }
        }
        assertEquals(listOf("value", "value"), results)
        assertEquals(1, issues.get())
    }

    @Test
    fun `a failed shared call is evicted so the next caller retries`() = runBlocking<Unit> {
        val issues = AtomicInteger(0)
        val failure = runCatching {
            MangaDexSharedFetch.shared("test/failure") { issues.incrementAndGet(); error("down") }
        }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertEquals("recovered", MangaDexSharedFetch.shared("test/failure") { issues.incrementAndGet(); "recovered" })
        assertEquals(2, issues.get())
    }

    @Test
    fun `entries expire after the ttl and refetch`() {
        var now = 0L
        MangaDexSharedFetch.clock = { now }
        val issues = AtomicInteger(0)
        runBlocking<Unit> {
            assertEquals("first", MangaDexSharedFetch.shared("test/expiring") { issues.incrementAndGet(); "first" })
            now = 16_000L
            assertEquals("second", MangaDexSharedFetch.shared("test/expiring") { issues.incrementAndGet(); "second" })
        }
        assertEquals(2, issues.get())
    }

    @Test
    fun `different keys do not share a call`() {
        val issues = AtomicInteger(0)
        runBlocking<Unit> {
            coroutineScope {
                val a = async { MangaDexSharedFetch.shared("test/k1") { issues.incrementAndGet(); 1 } }
                val b = async { MangaDexSharedFetch.shared("test/k2") { issues.incrementAndGet(); 2 } }
                assertEquals(1, a.await())
                assertEquals(2, b.await())
            }
        }
        assertEquals(2, issues.get())
    }
}
