package dev.errnolink.tsuzuki.mangadex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TrackingSyncJobTest {
    @Test
    fun `refresh continues after per-title failures and reports exact progress`() = runTest {
        val attempted = mutableListOf<Long>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = refreshTrackingMetadata(
            listOf(1, 2, 3, 4),
            refresh = {
                attempted += it
                if (it == 2L) error("service unavailable")
                it != 3L
            },
            progress = { done, failed -> progress += done to failed },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), attempted)
        assertEquals(TrackingRefreshSummary(4, 2), result)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 1, 3 to 2, 4 to 2), progress)
    }

    @Test
    fun `cancellation does not start another title or report a false success`() {
        val attempted = mutableListOf<Long>()
        assertThrows(CancellationException::class.java) {
            runTest {
                refreshTrackingMetadata(
                    listOf(1, 2, 3),
                    refresh = {
                        attempted += it
                        if (it == 2L) throw CancellationException("cancelled")
                        true
                    },
                    progress = { _, _ -> },
                )
            }
        }
        assertEquals(listOf(1L, 2L), attempted)
    }

    @Test
    fun `empty tracked library completes without remote calls`() = runTest {
        val result = refreshTrackingMetadata(emptyList(), { error("unexpected request") }, { _, _ -> })
        assertEquals(TrackingRefreshSummary(0, 0), result)
    }
}
