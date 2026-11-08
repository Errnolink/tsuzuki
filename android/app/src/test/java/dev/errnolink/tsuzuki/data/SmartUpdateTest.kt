package dev.errnolink.tsuzuki.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartUpdateTest {
    private val hour = 60 * 60_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L
    private val id = 7L

    private fun smart(): SmartUpdate {
        val checked = mutableMapOf<Long, Long>()
        return SmartUpdate(
            object : DetailsFreshness.Store {
                override fun get(mangaId: Long) = checked[mangaId] ?: 0L
                override fun put(mangaId: Long, at: Long) {
                    checked[mangaId] = at
                }
            },
        )
    }

    @Test
    fun `a title is left alone until its predicted release and checked once it arrives`() {
        val smart = smart()
        val release = now + 3 * hour
        assertFalse(smart.isDue(id, release, now))
        assertTrue(smart.isDue(id, release, release + hour))
    }

    @Test
    fun `a late chapter is rechecked every twelve hours and not in between`() {
        val smart = smart()
        val release = now
        smart.markChecked(id, release + hour)
        assertFalse(smart.isDue(id, release, release + 6 * hour))
        assertTrue(smart.isDue(id, release, release + 13 * hour))
        smart.markChecked(id, release + 13 * hour)
        assertFalse(smart.isDue(id, release, release + 20 * hour))
    }

    @Test
    fun `a new prediction after a check makes the title due again at the new release`() {
        val smart = smart()
        smart.markChecked(id, now)
        val next = now + 7 * day
        assertFalse(smart.isDue(id, next, now + 2 * day))
        assertTrue(smart.isDue(id, next, next))
    }

    @Test
    fun `a title without a prediction is checked once and then weekly`() {
        val smart = smart()
        assertTrue(smart.isDue(id, 0L, now))
        smart.markChecked(id, now)
        assertFalse(smart.isDue(id, 0L, now + 6 * day))
        assertTrue(smart.isDue(id, 0L, now + 7 * day))
    }
}
