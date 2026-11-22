package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.semantics.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MenuHelpersTest {

    @Test
    fun `a plain action row is a button`() {
        assertEquals(Role.Button, menuRowRole(selected = null, toggle = false))
    }

    @Test
    fun `a chosen value row is a radio button`() {
        assertEquals(Role.RadioButton, menuRowRole(selected = true, toggle = false))
        assertEquals(Role.RadioButton, menuRowRole(selected = false, toggle = false))
    }

    @Test
    fun `a toggle row is a checkbox regardless of state`() {
        assertEquals(Role.Checkbox, menuRowRole(selected = true, toggle = true))
        assertEquals(Role.Checkbox, menuRowRole(selected = false, toggle = true))
        assertEquals(Role.Checkbox, menuRowRole(selected = null, toggle = true))
    }

    @Test
    fun `the off tri-state shows no glyph`() {
        val (glyph, role) = triStateGlyph(PullDownTriState.Off)
        assertNull(glyph)
        assertEquals(ColorRole.None, role)
    }

    @Test
    fun `include shows the accent check`() {
        val (glyph, role) = triStateGlyph(PullDownTriState.Include)
        assertSame(TsuzukiIcons.Check, glyph)
        assertEquals(ColorRole.Accent, role)
    }

    @Test
    fun `exclude shows the destructive X`() {
        val (glyph, role) = triStateGlyph(PullDownTriState.Exclude)
        assertSame(TsuzukiIcons.X, glyph)
        assertEquals(ColorRole.Destructive, role)
    }

    @Test
    fun `tri-state descriptions match the tri-state chip strings`() {
        assertEquals("Any", triStateDescription(PullDownTriState.Off))
        assertEquals("Only these", triStateDescription(PullDownTriState.Include))
        assertEquals("Hidden", triStateDescription(PullDownTriState.Exclude))
    }

    @Test
    fun `tri-state descriptions follow custom component labels`() {
        val custom = TsuzukiComponentLabels(
            triStateOff = "Alles",
            triStateInclude = "Nur diese",
            triStateExclude = "Ausgeblendet",
        )
        assertEquals("Alles", triStateDescription(PullDownTriState.Off, custom))
        assertEquals("Nur diese", triStateDescription(PullDownTriState.Include, custom))
        assertEquals("Ausgeblendet", triStateDescription(PullDownTriState.Exclude, custom))
    }
}
