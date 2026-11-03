package exh.md.utils

import exh.md.dto.ListCallDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MdListCallTest {

    private class FakePage(
        override val offset: Int,
        override val limit: Int,
        override val total: Int,
    ) : ListCallDto<String> {
        override val data = listOf("page-$offset")
    }

    @Test
    fun `at most four page fetches run concurrently and every page is fetched`() = runTest {
        val totalPages = 12
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)

        suspend fun request(offset: Int): ListCallDto<String> {
            val now = inFlight.incrementAndGet()
            peakInFlight.updateAndGet { current -> maxOf(current, now) }
            delay(30)
            inFlight.decrementAndGet()
            return FakePage(offset = offset, limit = 1, total = totalPages)
        }

        val items = mdListCall { offset -> request(offset) }

        assertEquals(totalPages, items.size)
        assertEquals((0 until totalPages).map { "page-$it" }, items)
        assertTrue(peakInFlight.get() in 2..4, "peak concurrency was ${peakInFlight.get()}")
    }

    @Test
    fun `single page lists make no further calls`() = runTest {
        var calls = 0
        val items = mdListCall { offset ->
            calls++
            FakePage(offset = offset, limit = 500, total = 3)
        }

        assertEquals(listOf("page-0"), items)
        assertEquals(1, calls)
    }
}
