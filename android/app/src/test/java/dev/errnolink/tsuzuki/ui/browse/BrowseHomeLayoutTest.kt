package dev.errnolink.tsuzuki.ui.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseHomeLayoutTest {

    @Test
    fun `browseShelfCardWidthDp scales proportionally on phones and caps on tablets`() {
        assertEquals(112f, browseShelfCardWidthDp(260f), 0.01f)
        val phoneWidth = browseShelfCardWidthDp(360f)
        assertTrue(phoneWidth in 140f..150f)
        val largePhoneWidth = browseShelfCardWidthDp(412f)
        assertTrue(largePhoneWidth > phoneWidth && largePhoneWidth < 180f)
        assertEquals(180f, browseShelfCardWidthDp(600f), 0.01f)
        assertEquals(180f, browseShelfCardWidthDp(840f), 0.01f)
        assertEquals(180f, browseShelfCardWidthDp(1200f), 0.01f)
    }

    @Test
    fun `browseListColumnWidthDp clamps split-screen minimum and tablet maximum`() {
        assertEquals(200f, browseListColumnWidthDp(260f), 0.01f)
        val phoneCol = browseListColumnWidthDp(360f)
        assertTrue(phoneCol in 220f..230f)
        assertEquals(320f, browseListColumnWidthDp(600f), 0.01f)
        assertEquals(320f, browseListColumnWidthDp(840f), 0.01f)
        assertEquals(320f, browseListColumnWidthDp(1200f), 0.01f)
    }
}
