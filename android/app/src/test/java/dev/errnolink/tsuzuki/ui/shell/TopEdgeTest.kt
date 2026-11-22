package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
/** TOP-01/02: the band's scrolled-under progress and the top-edge height it must cover. */
class TopEdgeTest {

    @Test
    fun `scrolled under ramps over the first 24 dp of offset`() {
        assertEquals(0f, scrolledUnderFraction(0, 0f, 96f))
        assertEquals(0.5f, scrolledUnderFraction(0, 48f, 96f), 0.001f)
        assertEquals(1f, scrolledUnderFraction(0, 96f, 96f))
        // Past the first item entirely: fully under regardless of offset.
        assertEquals(1f, scrolledUnderFraction(2, 0f, 96f))
    }

    @Test
    fun `any first-pixel scroll already starts the band`() {
        assert(scrolledUnderFraction(0, 1f, 96f) > 0f)
    }

    @Test
    fun `the edge covers status bar, row body and the fade tail`() {
        assertEquals(176.dp, topEdgeHeight(48.dp, 76.dp))
    }
}
