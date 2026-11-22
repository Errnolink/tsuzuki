package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuLiquidTest {

    private val panel = Size(750f, 900f)
    private val trigger = Rect(618f, 0f, 750f, 132f)

    private fun frame(
        morph: Float,
        drop: Float = 0f,
        corner: Float = morph,
        presence: Float = 1f - morph,
        absorb: Float = morph,
        content: Float = morph,
        label: Float = 1f - morph,
        borderless: Boolean = false,
        closing: Boolean = false,
    ) = menuLiquidFrame(
        trigger = trigger,
        panel = panel,
        menuRadius = 84f,
        morph = morph,
        drop = drop,
        corner = corner,
        presence = presence,
        absorb = absorb,
        content = content,
        label = label,
        press = 0f,
        pressGrowPx = 18f,
        mergePx = 42f,
        borderless = borderless,
        closing = closing,
    )

    @Test
    fun `at rest the liquid is exactly the button, with no bulge from the merge`() {
        val f = frame(0f)
        assertEquals(GlassBox.of(trigger, 66f), f.glass.a)
        assertEquals(0f, f.glass.merge)
        assertEquals(1f, f.labelAlpha)
        assertEquals(0f, f.contentAlpha)
    }

    @Test
    fun `landed, the menu is the drop and the button is gone`() {
        val f = frame(1f)
        assertTrue(f.glass.a.isEmpty, "button absorbed: ${f.glass.a}")
        assertEquals(GlassBox(panel.width / 2f, panel.height / 2f, panel.width / 2f, panel.height / 2f, 84f), f.blob)
        assertEquals(1f, f.contentScale)
        assertEquals(1f, f.contentAlpha)
        assertEquals(0f, f.contentBlur)
    }

    @Test
    fun `a drop that leaves the button grows a neck between them`() {
        val f = frame(0.3f, drop = 60f, presence = 0.6f, absorb = 0.2f)
        assertTrue(f.blob.bottom > f.glass.a.bottom)
        assertTrue(f.glass.merge > 0f)
    }

    @Test
    fun `the sag pulls the drop below where the morph puts it`() {
        assertEquals(frame(0.4f).blob.cy + 50f, frame(0.4f, drop = 50f).blob.cy, 0.01f)
    }

    @Test
    fun `content rides the drop but never shrinks under half size`() {
        assertEquals(MenuLiquidMotion.MinContentScale, frame(0.05f).contentScale)
    }

    @Test
    fun `borderless closing dissolves glass alpha to zero near rest while non-borderless stays full`() {
        assertEquals(1f, frame(1f, borderless = true, closing = true).glassAlpha, 1e-4f)
        assertTrue(frame(0.25f, borderless = true, closing = true).glassAlpha in 0.01f..0.99f)
        assertEquals(0f, frame(0.05f, borderless = true, closing = true).glassAlpha, 1e-4f)
        assertEquals(1f, frame(0.05f, borderless = false, closing = true).glassAlpha, 1e-4f)
        assertEquals(1f, frame(0.05f, borderless = true, closing = false).glassAlpha, 1e-4f)
    }

    @Test
    fun `morph placement respects status bar safeTopPx and caps tall menu inside bounds`() {
        val safeTop = 96
        val bottomEdge = 2100
        val margin = 16
        val placement = computeMorphPlacement(
            anchor = IntRect(900, 120, 1020, 240),
            bounds = IntRect(0, safeTop, 1080, bottomEdge),
            viewport = IntSize(1080, bottomEdge),
            popupSize = IntSize(700, 2500),
            marginPx = margin,
        )
        assertTrue(placement.offset.y >= safeTop + margin, "offset.y=${placement.offset.y}")
        assertEquals(bottomEdge - safeTop - 2 * margin, placement.maxHeightPx)
    }

    @Test
    fun `inline submenu opening from a bottom row shifts up just enough to stay inside menu insets`() {
        val menuHeight = 900f
        val inset = 12f
        val frame = inlineSubmenuFrame(rowTop = 760f, naturalHeight = 400f, menuHeight = menuHeight, insetPx = inset)
        assertTrue(frame.top >= inset)
        assertEquals(menuHeight - inset, frame.top + frame.height, 0.01f)
    }

    @Test
    fun `menu row leading skips dead check gutter for icon rows and aligns icon-less checkable rows`() {
        assertEquals(48.dp, menuRowTextStartDp(hasIcon = true, reserveCheck = false))
        assertEquals(48.dp, menuRowTextStartDp(hasIcon = true, reserveCheck = true))
        assertEquals(16.dp, menuRowTextStartDp(hasIcon = false, reserveCheck = false))
        assertEquals(40.dp, menuRowTextStartDp(hasIcon = false, reserveCheck = true))
    }
}
