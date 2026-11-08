package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import dev.errnolink.tsuzuki.designsystem.tsuzukiCardColors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DetailsCardColorTest {

    private val covers: List<Color> = buildList {
        for (hue in 0 until 360 step 15) {
            for (saturation in listOf(0.15f, 0.5f, 0.9f)) {
                for (value in listOf(0.25f, 0.6f, 1f)) add(Color.hsv(hue.toFloat(), saturation, value))
            }
        }
        add(Color.White)
        add(Color.Black)
        add(Color(0xFF808080))
    }

    private fun hueOf(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val d = max - min
        return when {
            d == 0f -> 0f
            max == color.red -> 60f * (((color.green - color.blue) / d).mod(6f))
            max == color.green -> 60f * ((color.blue - color.red) / d + 2f)
            else -> 60f * ((color.red - color.green) / d + 4f)
        }
    }

    @Test
    fun `white title text reads on every card`() {
        covers.forEach { cover ->
            val card = DetailsCardColor.forDominant(cover)
            val contrast = ContinueActionColor.whiteContrast(card)
            assertTrue(contrast >= 4.5f, "white on card from $cover is $contrast")
        }
    }

    @Test
    fun `secondary lines and links stay readable on the card`() {
        covers.forEach { cover ->
            val colors = tsuzukiCardColors(DetailsCardColor.forDominant(cover))
            val text = ContinueActionColor.contrast(colors.text, colors.background)
            val secondary = ContinueActionColor.contrast(colors.secondary, colors.background)
            val accent = ContinueActionColor.contrast(colors.accent, colors.background)
            assertTrue(text >= 4.5f, "text $text for $cover")
            assertTrue(secondary >= 4.5f, "secondary $secondary for $cover")
            assertTrue(accent >= 4.5f, "accent $accent for $cover")
            val selected = ContinueActionColor.contrast(colors.text, colors.selectedFill)
            assertTrue(selected >= 4.5f, "selected segment label $selected for $cover")
        }
    }

    @Test
    fun `the card keeps the cover's hue instead of going grey`() {
        val orange = Color(0xFFF28C28)
        val green = Color(0xFF2FA84F)
        listOf(orange, green).forEach { cover ->
            val card = DetailsCardColor.forDominant(cover)
            val drift = abs(hueOf(card) - hueOf(cover)).let { minOf(it, 360f - it) }
            assertTrue(drift < 6f, "hue drifted $drift for $cover")
            val saturation = (maxOf(card.red, card.green, card.blue) - minOf(card.red, card.green, card.blue)) /
                maxOf(card.red, card.green, card.blue)
            assertTrue(saturation > 0.5f, "card from $cover lost its colour: $card")
        }
    }

    @Test
    fun `an already dark cover is used as it is`() {
        val navy = Color(0xFF14213D)
        val card = DetailsCardColor.forDominant(navy)
        assertTrue(abs(card.red - navy.red) < 1e-3f && abs(card.blue - navy.blue) < 1e-3f, "navy was darkened to $card")
    }
}
