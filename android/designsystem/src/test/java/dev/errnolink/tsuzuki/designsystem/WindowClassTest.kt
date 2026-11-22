package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowClassTest {

    @Test
    fun below600IsCompact() {
        assertEquals(WindowWidthClass.Compact, windowWidthClass(0f))
        assertEquals(WindowWidthClass.Compact, windowWidthClass(359f))
        assertEquals(WindowWidthClass.Compact, windowWidthClass(599f))
    }

    @Test
    fun at600IsMedium() {
        assertEquals(WindowWidthClass.Medium, windowWidthClass(600f))
        assertEquals(WindowWidthClass.Medium, windowWidthClass(839f))
    }

    @Test
    fun at840IsExpanded() {
        assertEquals(WindowWidthClass.Expanded, windowWidthClass(840f))
        assertEquals(WindowWidthClass.Expanded, windowWidthClass(1280f))
    }
}
