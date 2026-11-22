package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reader chrome flips its scheme with the canvas (U2), so its labels must keep 4.5:1 over
 * the glass fill — the tint composited over an extreme page — whatever tint strength the glass
 * material runs at. The invariant must hold for the static palettes and for cover seeds.
 */
class GlassInkContrastTest {
    private val pages = mapOf(
        "white page" to Color.White,
        "black page" to Color.Black,
    )

    @Test
    fun `static palette labels keep 4_5 to 1 over the glass on extreme pages`() {
        for (dark in listOf(false, true)) {
            val colors = tsuzukiColors(dark)
            for ((name, page) in pages) {
                if (dark != (page == Color.Black)) continue // the scheme follows the canvas
                for (alpha in listOf(0.3f, 0.6f, 0.95f)) {
                    val fill = lerpGlass(colors.glassTint, page, alpha)
                    val ratio = contrastRatio(colors.text, fill)
                    assertTrue(ratio >= 4.5f, "$name, dark=$dark, alpha=$alpha: ink ${colors.text} on $fill reads $ratio")
                }
            }
        }
    }

    @Test
    fun `cover-seeded schemes keep the same rule`() {
        for (seed in listOf(Color(0xFF7E2A33), Color(0xFFE5C07B), Color(0xFF2F6F8F), Color(0xFF3E3A39))) {
            for (dark in listOf(false, true)) {
                val colors = expressiveCoverColors(seed, dark)
                val page = if (dark) Color.Black else Color.White
                for (alpha in listOf(0.3f, 0.6f, 0.95f)) {
                    val fill = lerpGlass(colors.glassTint, page, alpha)
                    assertTrue(
                        contrastRatio(colors.text, fill) >= 4.5f,
                        "seed $seed dark=$dark alpha=$alpha",
                    )
                }
            }
        }
    }

    @Test
    fun `chrome over content keeps 4_5 to 1 on a page of the opposite scheme at any slider value`() {
        // RDR-02: the "auto" canvas follows system dark, so dark chrome floats over white pages
        // (and light chrome over black ones). The minimum strength must hold for every material.
        for (material in listOf(GlassMaterial.Regular, GlassMaterial.Thick)) {
            for (dark in listOf(false, true)) {
                val colors = tsuzukiColors(dark)
                for ((name, page) in pages) {
                    for (slider in listOf(0f, 0.3f, GlassMaterial.DefaultTintStrength, 1f)) {
                        val alpha = material.tintAlpha(dark, GlassMaterial.chromeOverContentStrength(slider))
                        val fill = lerpGlass(colors.glassTint, page, alpha)
                        val ratio = contrastRatio(colors.text, fill)
                        assertTrue(ratio >= 4.5f, "$material $name dark=$dark slider=$slider: ink on $fill reads $ratio")
                    }
                }
            }
        }
    }

    private fun lerpGlass(tint: Color, page: Color, alpha: Float) = androidx.compose.ui.graphics.lerp(page, tint, alpha)
}
