package dev.errnolink.tsuzuki.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailsFreshnessTest {

    private val hour = 60 * 60_000L
    private val now = 1_000 * hour
    private val saved = HashMap<Long, Long>()
    private val freshness = DetailsFreshness(
        object : DetailsFreshness.Store {
            override fun get(mangaId: Long) = saved[mangaId] ?: 0L
            override fun put(mangaId: Long, at: Long) {
                saved[mangaId] = at
            }
        },
    )

    @Test
    fun `a title with no chapters is always checked`() {
        assertTrue(freshness.chaptersDue(1L, hasChapters = false, lastUpdate = now, now = now))
    }

    @Test
    fun `reopening a title updated an hour ago makes no request`() {
        assertFalse(freshness.chaptersDue(2L, hasChapters = true, lastUpdate = now - hour, now = now))
    }

    @Test
    fun `a title not checked for more than six hours is checked on open`() {
        assertTrue(freshness.chaptersDue(3L, hasChapters = true, lastUpdate = now - 7 * hour, now = now))
    }

    @Test
    fun `a check that found nothing new still counts as fresh`() {
        freshness.markChecked(4L, now = now - hour)
        assertFalse(freshness.chaptersDue(4L, hasChapters = true, lastUpdate = now - 30 * hour, now = now))
    }
}
