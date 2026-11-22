package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InsetDividerTest {

    @Test
    fun `divider leaves the leading inset open in both reading directions`() {
        assertEquals(16f to 300f, insetDividerSpan(300f, 16f, fromEnd = false, rtl = false))
        assertEquals(0f to 284f, insetDividerSpan(300f, 16f, fromEnd = false, rtl = true))
    }

    @Test
    fun `an end-inset divider leaves the trailing edge open in both reading directions`() {
        assertEquals(0f to 284f, insetDividerSpan(300f, 16f, fromEnd = true, rtl = false))
        assertEquals(16f to 300f, insetDividerSpan(300f, 16f, fromEnd = true, rtl = true))
    }

    @Test
    fun `a grouped-row divider starts at the text edge of its leading`() {
        // no leading, 24 dp icon, 29 dp badge, 32 dp image
        assertEquals(16.dp, groupDividerStart())
        assertEquals(16.dp, groupDividerStart(0.dp))
        assertEquals(52.dp, groupDividerStart(24.dp))
        assertEquals(57.dp, groupDividerStart(29.dp))
        assertEquals(60.dp, groupDividerStart(32.dp))
    }
}
