package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuScrubTest {

    // Three 44 dp rows (0-44, 44-88, 88-132) in panel space; the middle one is disabled.
    private val rows = listOf(
        MenuScrubRow(top = 0f, bottom = 44f, enabled = true),
        MenuScrubRow(top = 44f, bottom = 88f, enabled = false),
        MenuScrubRow(top = 88f, bottom = 132f, enabled = true),
    )
    private val panelTop = 0f
    private val panelBottom = 132f

    @Test
    fun `a finger on a row hits that row`() {
        assertEquals(0, menuScrubRow(20f, panelTop, panelBottom, rows))
        assertEquals(2, menuScrubRow(100f, panelTop, panelBottom, rows))
    }

    @Test
    fun `a disabled row is skipped and the nearest enabled row wins`() {
        // Straight on the disabled row: the highlight falls to a neighbour (closer edge: row 0).
        assertEquals(0, menuScrubRow(60f, panelTop, panelBottom, rows))
        // Just past the disabled row's centre, row 2 is nearer.
        assertEquals(2, menuScrubRow(80f, panelTop, panelBottom, rows))
    }

    @Test
    fun `a gap between rows resolves to the nearest enabled row`() {
        assertEquals(2, menuScrubRow(89f, panelTop, panelBottom, rows))
    }

    @Test
    fun `beyond the first or last row clamps inside the panel`() {
        // Above the first row, still inside the panel: the first row is the nearest enabled one.
        assertEquals(0, menuScrubRow(2f, panelTop, panelBottom, rows))
        assertEquals(2, menuScrubRow(131f, panelTop, panelBottom, rows))
    }

    @Test
    fun `a finger outside the menu vertical range hits nothing`() {
        assertNull(menuScrubRow(-1f, panelTop, panelBottom, rows))
        assertNull(menuScrubRow(132.5f, panelTop, panelBottom, rows))
    }

    @Test
    fun `a menu with only disabled rows hits nothing`() {
        val disabled = rows.map { it.copy(enabled = false) }
        assertNull(menuScrubRow(20f, panelTop, panelBottom, disabled))
        assertNull(menuScrubRow(20f, panelTop, panelBottom, emptyList()))
    }

    @Test
    fun `a finger over the menu does not stretch it`() {
        val panel = Rect(0f, 0f, 200f, 300f)
        assertEquals(Offset.Zero, menuPullOffset(Offset(10f, 290f), panel, maxPx = 20f))
    }

    @Test
    fun `a finger outside stretches the menu toward it, eased and capped`() {
        val panel = Rect(0f, 0f, 200f, 300f)
        val near = menuPullOffset(Offset(210f, 320f), panel, maxPx = 20f)
        assertTrue(near.x > 0f && near.y > 0f)
        assertTrue(near.x < 10f && near.y < 20f)
        val far = menuPullOffset(Offset(5000f, -5000f), panel, maxPx = 20f)
        assertEquals(20f, far.x, 0.01f)
        assertEquals(-20f, far.y, 0.01f)
    }

    @Test
    fun `an empty panel never stretches`() {
        assertEquals(Offset.Zero, menuPullOffset(Offset(50f, 50f), Rect.Zero, maxPx = 20f))
    }
}
