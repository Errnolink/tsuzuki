package dev.errnolink.tsuzuki.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryHeaderOffsetTest {

    @Test
    fun `grid at top keeps the shelf in place`() {
        assertEquals(0, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, maxOffsetPx = 800))
    }

    @Test
    fun `partly scrolled grid moves the shelf with it one to one`() {
        assertEquals(123, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 123, maxOffsetPx = 800))
    }

    @Test
    fun `shelf never travels past its own height`() {
        assertEquals(800, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 900, maxOffsetPx = 800))
        assertEquals(800, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 100_000, maxOffsetPx = 800))
    }

    @Test
    fun `past the first grid item the shelf is fully scrolled away`() {
        assertEquals(800, libraryHeaderOffset(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0, maxOffsetPx = 800))
        assertEquals(800, libraryHeaderOffset(firstVisibleItemIndex = 12, firstVisibleItemScrollOffset = 240, maxOffsetPx = 800))
    }

    @Test
    fun `offset never goes negative`() {
        assertEquals(0, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = -5, maxOffsetPx = 800))
        assertEquals(0, libraryHeaderOffset(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, maxOffsetPx = 0))
    }

    @Test
    fun `shelf stays put when both categories are at the top`() {
        assertEquals(0f, libraryHeaderOffsetDuringSwipe(0, 0, 0.3f))
    }

    @Test
    fun `shelf travels with the swipe toward a scrolled category`() {
        assertEquals(0f, libraryHeaderOffsetDuringSwipe(0, 800, 0f))
        assertEquals(200f, libraryHeaderOffsetDuringSwipe(0, 800, 0.25f))
        assertEquals(400f, libraryHeaderOffsetDuringSwipe(0, 800, 0.5f))
    }

    @Test
    fun `shelf position is continuous where the current page flips at half way`() {
        val beforeFlip = libraryHeaderOffsetDuringSwipe(0, 800, 0.5f)
        val afterFlip = libraryHeaderOffsetDuringSwipe(800, 0, -0.5f)
        assertEquals(beforeFlip, afterFlip)
    }

    @Test
    fun `shelf has fully left when the scrolled category settles`() {
        assertEquals(800f, libraryHeaderOffsetDuringSwipe(800, 0, 0f))
    }
}
