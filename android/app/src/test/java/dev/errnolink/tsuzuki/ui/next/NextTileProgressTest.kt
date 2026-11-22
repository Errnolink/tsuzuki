package dev.errnolink.tsuzuki.ui.next

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NextTileProgressTest {

    private val words = NextTileWords(
        noChaptersYet = "No chapters yet",
        caughtUp = "Caught up",
        readOfTotal = { read, total -> "$read of $total read" },
        chapters = { count -> if (count == 1L) "1 chapter" else "$count chapters" },
    )

    @Test
    fun `a title being read shows chapters read of the total`() {
        assertEquals("28 of 40 read", NextTileProgress.of(read = 28, total = 40, unread = 12, words).caption)
    }

    @Test
    fun `a caught up title says so`() {
        assertEquals("Caught up", NextTileProgress.of(read = 85, total = 85, unread = 0, words).caption)
    }

    @Test
    fun `an unstarted title shows its size`() {
        assertEquals("1 chapter", NextTileProgress.of(read = 0, total = 1, unread = 1, words).caption)
        assertEquals("40 chapters", NextTileProgress.of(read = 0, total = 40, unread = 40, words).caption)
    }

    @Test
    fun `a title without chapters never divides by zero`() {
        assertEquals("No chapters yet", NextTileProgress.of(read = 3, total = 0, unread = 0, words).caption)
    }

    @Test
    fun `captions fit one line of a three-column tile`() {
        // ~115 dp cell at 12 sp holds about 16 characters (LIB-02 truncated every caption).
        val worst = NextTileProgress.of(read = 812, total = 1240, unread = 428, words).caption
        assertTrue(worst.length <= 16, "\"$worst\" is ${worst.length} characters")
    }

    @Test
    fun `translated words flow into the caption`() {
        val translated = NextTileWords(
            noChaptersYet = "Noch keine Kapitel",
            caughtUp = "Alles gelesen",
            readOfTotal = { read, total -> "$read von $total gelesen" },
            chapters = { count -> if (count == 1L) "1 Kapitel" else "$count Kapitel" },
        )
        assertEquals("28 von 40 gelesen", NextTileProgress.of(read = 28, total = 40, unread = 12, translated).caption)
        assertEquals("Alles gelesen", NextTileProgress.of(read = 85, total = 85, unread = 0, translated).caption)
    }
}
