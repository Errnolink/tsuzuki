package dev.errnolink.tsuzuki.ui.details

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailsEarlyStateTest {

    @Test
    fun `a database read stuck behind a busy pool does not hold the caller past the budget`() {
        val read = CoroutineScope(Dispatchers.IO).async<String?> {
            Thread.sleep(1_500)
            "late"
        }
        val started = System.nanoTime()
        val result = DetailsEarlyState.awaitBounded(read, budgetMs = 24)
        val waitedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue(waitedMs < 300, "caller blocked for $waitedMs ms")
    }

    @Test
    fun `a fast read is returned`() {
        val read = CompletableDeferred<String?>("manga")
        assertEquals("manga", DetailsEarlyState.awaitBounded(read, budgetMs = 24))
    }
}
