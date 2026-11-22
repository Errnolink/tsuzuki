package dev.errnolink.tsuzuki.ui.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadSwipeActionsTest {

    @Test
    fun `actions bind the callbacks they were built with`() {
        var movedToTop = 0
        var cancelled = 0
        val specs = downloadSwipeSpecs(moveToTop = { movedToTop++ }, cancelChapter = { cancelled++ })
        specs.first { it.label == "Move to top" }.onClick()
        specs.first { it.label == "Cancel chapter" }.onClick()
        assertEquals(1, movedToTop)
        assertEquals(1, cancelled)
    }

    @Test
    fun `rebuilding with fresh callbacks rebinds instead of replaying stale queues`() {
        var staleHits = 0
        var freshHits = 0
        // A row built before the queue changed holds the callbacks it was built with...
        val cached = downloadSwipeSpecs(moveToTop = { staleHits++ }, cancelChapter = {})
        // ...so a rebuild must produce specs bound to the fresh callbacks instead.
        val fresh = downloadSwipeSpecs(moveToTop = { freshHits++ }, cancelChapter = {})
        cached.first().onClick()
        fresh.first().onClick()
        assertEquals(1, staleHits, "the cached spec must fire only its own original target")
        assertEquals(1, freshHits)
        assertNotEquals(cached, fresh)
    }
}
