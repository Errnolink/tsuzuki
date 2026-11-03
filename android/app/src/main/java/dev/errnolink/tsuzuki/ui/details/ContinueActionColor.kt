package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Pure colour math for the Continue action: takes the cover's dominant colour and produces a
 * capsule colour whose label (white) reads at >= 4.5:1 contrast. Low-saturation or missing
 * dominants fall back to the app accent.
 */
object ContinueActionColor {

    const val MinContrast = 4.5f
    private const val LowSaturation = 0.15f

    fun forCover(dominant: Color?, accent: Color): Color {
        val base = dominant ?: return accent
        if (base.alpha < 0.5f) return accent
        val hsl = base.toHsl() ?: return accent
        if (hsl.saturation < LowSaturation) return accent
        if (hsl.lightness <= 0.04f || hsl.lightness >= 0.96f) return accent
        return darkenUntilReadable(base)
    }

    /** WCAG contrast ratio of white label text over [background], 1..21. */
    fun whiteContrast(background: Color): Float = contrast(Color.White, background)

    fun contrast(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return ((lighter + 0.05f) / (darker + 0.05f))
    }

    private fun darkenUntilReadable(base: Color): Color {
        var candidate = base
        // Bounded walk: each step scales RGB toward black; far cheaper than a solver and
        // monotonic for contrast against white.
        repeat(24) {
            if (whiteContrast(candidate) >= MinContrast) return candidate
            candidate = Color(
                red = candidate.red * 0.88f,
                green = candidate.green * 0.88f,
                blue = candidate.blue * 0.88f,
                alpha = 1f,
            )
        }
        return candidate
    }

    internal fun relativeLuminance(color: Color): Float {
        fun channel(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    private fun Color.toHsl(): Hsl? {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        val lightness = (max + min) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        return Hsl(saturation, lightness)
    }

    private data class Hsl(val saturation: Float, val lightness: Float)
}
