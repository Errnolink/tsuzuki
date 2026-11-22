package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** C-follows: the compact follows formatter divides by the unit it prints. */
class MangaInfoHeaderFollowsTest {

    @Test
    fun `millions divide by a million`() {
        // The old 100_000f divisor printed this as "12.3M".
        assertEquals("1.1M", formatFollowsCompact(1_140_000))
        assertEquals("2M", formatFollowsCompact(2_000_000))
    }

    @Test
    fun `thousands and small counts keep their form`() {
        assertEquals("12K", formatFollowsCompact(12_345))
        assertEquals("1K", formatFollowsCompact(1_000))
        assertEquals("999", formatFollowsCompact(999))
        assertEquals("0", formatFollowsCompact(0))
    }

    @Test
    fun `exactly one million prints whole`() {
        assertEquals("1M", formatFollowsCompact(1_000_000))
    }
}
