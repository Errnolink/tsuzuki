package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + 0.05f) / (low + 0.05f)
}

class PillButtonColorsTest {

    @Test
    fun `prominent fills accent with onAccent in both themes`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            val (fill, label) = pillButtonColors(prominent = true, tinted = false, destructive = false, colors = colors)
            assertEquals(colors.accent, fill)
            assertEquals(colors.onAccent, label)
        }
    }

    @Test
    fun `tinted fills accent at 0_20 dark and 0_14 light with accent label`() {
        val dark = tsuzukiColors(true)
        val light = tsuzukiColors(false)
        assertEquals(dark.accent.copy(alpha = 0.20f), pillButtonColors(false, true, false, dark).first)
        assertEquals(dark.accent, pillButtonColors(false, true, false, dark).second)
        assertEquals(light.accent.copy(alpha = 0.14f), pillButtonColors(false, true, false, light).first)
        assertEquals(light.accent, pillButtonColors(false, true, false, light).second)
    }

    @Test
    fun `destructive fills destructive at 0_16 dark and 0_12 light with destructive label`() {
        val dark = tsuzukiColors(true)
        val light = tsuzukiColors(false)
        assertEquals(dark.destructive.copy(alpha = 0.16f), pillButtonColors(false, false, true, dark).first)
        assertEquals(dark.destructive, pillButtonColors(false, false, true, dark).second)
        assertEquals(light.destructive.copy(alpha = 0.12f), pillButtonColors(false, false, true, light).first)
        assertEquals(light.destructive, pillButtonColors(false, false, true, light).second)
    }

    @Test
    fun `neutral non-prominent fills text tint with accent label`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            val (fill, label) = pillButtonColors(prominent = false, tinted = false, destructive = false, colors = colors)
            assertEquals(colors.text.copy(alpha = if (dark) 0.10f else 0.06f), fill)
            assertEquals(colors.accent, label)
        }
    }

    @Test
    fun `destructive wins over tinted and tinted wins over prominent`() {
        val colors = tsuzukiColors(true)
        assertEquals(
            pillButtonColors(false, false, true, colors),
            pillButtonColors(true, true, true, colors),
        )
        assertEquals(
            pillButtonColors(false, true, false, colors),
            pillButtonColors(true, true, false, colors),
        )
    }

    @Test
    fun `tinted fill stays translucent over any backdrop`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            val (fill, _) = pillButtonColors(false, true, false, colors)
            assertTrue(fill.alpha < 1f, "tinted fill must be translucent dark=$dark")
        }
    }

    @Test
    fun `onAccent on accent meets 4_5 to 1 contrast in both themes`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            val ratio = contrastRatio(colors.onAccent, colors.accent)
            assertTrue(ratio >= 4.5f, "onAccent ${colors.onAccent} on accent ${colors.accent} is $ratio dark=$dark")
        }
    }

    @Test
    fun `destructive label on its own tint stays readable against grouped surfaces`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            val (fill, label) = pillButtonColors(false, false, true, colors)
            val overGrouped = lerp(colors.grouped, label, fill.alpha)
            assertTrue(contrastRatio(label, overGrouped) >= 4.5f, "destructive label over grouped dark=$dark ratio=${contrastRatio(label, overGrouped)}")
        }
    }
}
