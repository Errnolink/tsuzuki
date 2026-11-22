package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SheetGrabberTest {

    @Test
    fun `the confirm kind shows no grabber`() {
        assertFalse(sheetShowsGrabber(SheetKind.Confirm))
    }

    @Test
    fun `every other sheet kind keeps its grabber`() {
        assertTrue(sheetShowsGrabber(SheetKind.Picker))
        assertTrue(sheetShowsGrabber(SheetKind.LiveSettings))
        assertTrue(sheetShowsGrabber(SheetKind.BrowseList))
        assertTrue(sheetShowsGrabber(SheetKind.Free))
    }
}
