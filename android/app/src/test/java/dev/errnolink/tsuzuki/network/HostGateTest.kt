package dev.errnolink.tsuzuki.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HostGateTest {

    @Test
    fun `a site never sees more than two page downloads at once`() = runTest {
        val gate = HostGate(permits = 2, spacingMillis = 350, now = { testScheduler.currentTime })
        val open = AtomicInteger()
        var peak = 0
        val release = CompletableDeferred<Unit>()
        repeat(6) { i ->
            launch {
                gate.pass("https://img.example.org/p$i.jpg") {
                    peak = maxOf(peak, open.incrementAndGet())
                    release.await()
                    open.decrementAndGet()
                }
            }
        }
        advanceUntilIdle()
        assertEquals(2, peak)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, open.get())
    }

    @Test
    fun `requests to one site start at least the spacing apart`() = runTest {
        val gate = HostGate(permits = 4, spacingMillis = 120, now = { testScheduler.currentTime })
        val starts = mutableListOf<Long>()
        repeat(5) { i -> launch { gate.pass("https://covers.example.org/$i.jpg") { starts += testScheduler.currentTime } } }
        advanceUntilIdle()
        assertEquals(5, starts.size)
        starts.zipWithNext { a, b -> assertTrue(b - a >= 120, "starts $starts") }
    }

    @Test
    fun `different sites do not wait for each other`() = runTest {
        val gate = HostGate(permits = 1, spacingMillis = 10_000, now = { testScheduler.currentTime })
        val starts = mutableListOf<Long>()
        listOf("https://a.example.org/1", "https://b.example.org/1", "https://c.example.org/1").forEach { url ->
            launch { gate.pass(url) { starts += testScheduler.currentTime } }
        }
        advanceUntilIdle()
        assertEquals(listOf(0L, 0L, 0L), starts)
    }

    @Test
    fun `a failed download frees its slot for the next page`() = runTest {
        val gate = HostGate(permits = 1, spacingMillis = 0, now = { testScheduler.currentTime })
        runCatching { gate.pass("https://img.example.org/bad") { error("403") } }
        var ran = false
        gate.pass("https://img.example.org/good") { ran = true }
        assertTrue(ran)
    }
}
