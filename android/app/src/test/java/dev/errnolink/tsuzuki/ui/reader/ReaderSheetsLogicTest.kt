package dev.errnolink.tsuzuki.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderSheetsLogicTest {

    @Test
    fun `three short labels fit a segmented control`() {
        val measure: (String) -> Float = { it.length * 8f }
        assertEquals(SettingControl.Segmented, settingControl(listOf("A", "B", "C"), 328f, measure))
        assertTrue(segmentedFits(listOf("On", "Off"), 328f, measure))
    }

    @Test
    fun `four options never use a segmented control`() {
        val measure: (String) -> Float = { it.length * 8f }
        assertEquals(SettingControl.MenuRow, settingControl(listOf("None", "Horizontal", "Vertical", "Both"), 328f, measure))
        assertFalse(segmentedFits(listOf("None", "Horizontal", "Vertical", "Both"), 328f, measure))
    }

    @Test
    fun `wide labels fall back to a menu row`() {
        val measure: (String) -> Float = { it.length * 9f }
        assertEquals(SettingControl.MenuRow, settingControl(listOf("Double pages", "Automatic", "Single page"), 328f, measure))
    }

    @Test
    fun `one very long label never fits`() {
        val measure: (String) -> Float = { it.length * 8f }
        assertFalse(segmentedFits(listOf("An extraordinarily long setting label", "B"), 328f, measure))
        assertFalse(segmentedFits(listOf("An extraordinarily long setting label"), 328f, measure))
    }

    @Test
    fun `chapter list scroll puts the current row second from the top`() {
        assertEquals(4, chapterListInitialScroll(5, 20))
    }

    @Test
    fun `chapter list scroll keeps zero and one at the top`() {
        assertEquals(0, chapterListInitialScroll(0, 20))
        assertEquals(0, chapterListInitialScroll(1, 20))
    }

    @Test
    fun `chapter list scroll clamps to the last row`() {
        assertEquals(9, chapterListInitialScroll(11, 10))
    }

    @Test
    fun `chapter list scroll handles empty and missing current`() {
        assertEquals(0, chapterListInitialScroll(-1, 10))
        assertEquals(0, chapterListInitialScroll(0, 0))
    }
}
