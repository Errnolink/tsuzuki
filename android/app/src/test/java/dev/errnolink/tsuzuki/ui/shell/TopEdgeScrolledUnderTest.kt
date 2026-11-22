package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopEdgeScrolledUnderTest {

    @Test
    fun `a list resting at its start is not scrolled under`() {
        assertFalse(scrolledUnder(0, 0, 0f))
        assertFalse(scrolledUnder(0, 0, 12f))
    }

    @Test
    fun `any offset past the start counts with a zero threshold`() {
        assertTrue(scrolledUnder(0, 1, 0f))
        assertTrue(scrolledUnder(0, 48, 0f))
    }

    @Test
    fun `an offset within or at the threshold does not count`() {
        assertFalse(scrolledUnder(0, 4, 12f))
        assertFalse(scrolledUnder(0, 12, 12f))
    }

    @Test
    fun `an offset beyond the threshold counts`() {
        assertTrue(scrolledUnder(0, 13, 12f))
    }

    @Test
    fun `a later first item is always scrolled under`() {
        assertTrue(scrolledUnder(1, 0, 0f))
        assertTrue(scrolledUnder(3, 0, 12f))
        assertTrue(scrolledUnder(2, 2, 12f))
    }
}
