package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SliderKnobFillTest {

    @Test
    fun `knob is white at rest in both themes`() {
        for (dark in listOf(false, true)) {
            assertEquals(Color.White, sliderKnobFill(tsuzukiColors(dark), lensTint = 0f, opaqueGlass = false))
            assertEquals(Color.White, sliderKnobFill(tsuzukiColors(dark), lensTint = 0.01f, opaqueGlass = false))
        }
    }

    @Test
    fun `knob is white when glass is opaque`() {
        for (dark in listOf(false, true)) {
            assertEquals(Color.White, sliderKnobFill(tsuzukiColors(dark), lensTint = 1f, opaqueGlass = true))
        }
    }

    @Test
    fun `lens tint leaves pure white`() {
        for (dark in listOf(false, true)) {
            assertNotEquals(Color.White, sliderKnobFill(tsuzukiColors(dark), lensTint = 1f, opaqueGlass = false))
            assertNotEquals(Color.White, sliderKnobFill(tsuzukiColors(dark), lensTint = 0.5f, opaqueGlass = false))
        }
    }

    @Test
    fun `lens tint interpolates toward translucent white`() {
        val dark = tsuzukiColors(true)
        val tinted = sliderKnobFill(dark, lensTint = 1f, opaqueGlass = false)
        assertTrue(tinted.alpha < 1f, "lens knob must turn translucent, got $tinted")
        assertEquals(Color.White.copy(alpha = 0.22f).alpha, tinted.alpha)
    }

    @Test
    fun `lens tint is ignored while the knob stays white until the threshold`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            assertEquals(sliderKnobFill(colors, lensTint = 0.02f, opaqueGlass = false), Color.White)
            assertNotEquals(Color.White, sliderKnobFill(colors, lensTint = 0.03f, opaqueGlass = false))
        }
    }
}
