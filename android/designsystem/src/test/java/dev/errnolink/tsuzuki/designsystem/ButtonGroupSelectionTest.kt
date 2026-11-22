package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ButtonGroupSelectionTest {

    @Test
    fun `a selected item provides onAccent as the content colour`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            assertEquals(colors.onAccent, buttonGroupContentColor(true, colors))
        }
    }

    @Test
    fun `unselected and unmarked items keep the text content colour`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            assertEquals(colors.text, buttonGroupContentColor(false, colors))
            assertEquals(colors.text, buttonGroupContentColor(null, colors))
        }
    }
}
