package dev.errnolink.tsuzuki.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

private fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + 0.05f) / (low + 0.05f)
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = (h.mod(360f)) / 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    if (sat == 0f) return Color(light, light, light)
    val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
    val p = 2f * light - q
    fun channel(t: Float): Float {
        val tt = t.mod(1f)
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    return Color(channel(hue + 1f / 3f), channel(hue), channel(hue - 1f / 3f))
}

/** A colour at hue/chroma whose relative luminance matches the M3 tone (CIELAB L*), like real Material You roles. */
private fun tonal(hue: Float, chroma: Float, tone: Int): Color {
    val target = ((tone + 16) / 116.0).pow(3).toFloat().coerceIn(0.005f, 0.995f)
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (hsl(hue, chroma, mid).luminance() < target) lo = mid else hi = mid
    }
    return hsl(hue, chroma, (lo + hi) / 2f)
}

/** A fake dynamic scheme with the tonal relationships a real Material You scheme has, at hue/chroma. */
private fun fakeDynamicScheme(dark: Boolean, hue: Float, chroma: Float) = if (dark) {
    darkColorScheme(
        background = tonal(hue, chroma, 6),
        onBackground = tonal(hue, chroma, 90),
        onSurfaceVariant = tonal(hue, chroma, 80),
        outlineVariant = tonal(hue, chroma, 30),
        surfaceContainer = tonal(hue, chroma, 12),
        surfaceContainerHigh = tonal(hue, chroma, 17),
        primary = tonal(hue, chroma, 80),
        onPrimary = tonal(hue, chroma, 20),
        primaryContainer = tonal(hue, chroma, 30),
    )
} else {
    lightColorScheme(
        background = tonal(hue, chroma, 98),
        onBackground = tonal(hue, chroma, 10),
        onSurfaceVariant = tonal(hue, chroma, 30),
        outlineVariant = tonal(hue, chroma, 80),
        surfaceContainer = tonal(hue, chroma, 94),
        surfaceContainerHigh = tonal(hue, chroma, 92),
        primary = tonal(hue, chroma, 40),
        onPrimary = tonal(hue, chroma, 100),
        primaryContainer = tonal(hue, chroma, 90),
    )
}

class WallpaperColorsTest {

    @Test
    fun `extreme seeds keep readable text and findable controls in both themes`() {
        val hues = listOf(0f, 45f, 90f, 137f, 180f, 225f, 270f, 315f, 359.9f)
        val chromas = listOf(0f, 0.2f, 0.55f, 1f)
        for (dark in listOf(false, true)) {
            for (hue in hues) {
                for (chroma in chromas) {
                    val colors = wallpaperColors(fakeDynamicScheme(dark, hue, chroma), dark)
                    assertTrue(contrastRatio(colors.text, colors.background) >= 4.5f, "text on background: h=$hue c=$chroma dark=$dark")
                    assertTrue(contrastRatio(colors.secondary, colors.background) >= 4.5f, "secondary on background: h=$hue c=$chroma dark=$dark")
                    assertTrue(contrastRatio(colors.accent, colors.background) >= 3f, "accent on background: h=$hue c=$chroma dark=$dark")
                    assertTrue(contrastRatio(colors.onAccent, colors.accent) >= 4.5f, "onAccent on accent: h=$hue c=$chroma dark=$dark")
                }
            }
        }
    }

    @Test
    fun `mapped roles come from the dynamic scheme`() {
        val scheme = fakeDynamicScheme(dark = false, hue = 210f, chroma = 0.6f)
        val colors = wallpaperColors(scheme, dark = false)
        assertEquals(scheme.background, colors.background)
        assertEquals(scheme.surfaceContainer, colors.grouped)
        assertEquals(scheme.surfaceContainerHigh, colors.elevated)
        assertEquals(scheme.onSurfaceVariant, colors.secondary)
        assertEquals(scheme.outlineVariant, colors.separator)
        assertEquals(scheme.primaryContainer, colors.selectedFill)
        assertEquals(scheme.surfaceContainer, colors.glassTint)
        assertEquals(scheme.primary, colors.accent)
        assertEquals(scheme.onPrimary, colors.onAccent)
    }

    @Test
    fun `a failing pair falls back to Tsuzuki's role`() {
        val scheme = fakeDynamicScheme(dark = false, hue = 210f, chroma = 0.6f)
        val base = tsuzukiColors(false)
        val unreadable = scheme.copy(
            onBackground = scheme.background, // 1:1 text
            primary = scheme.background, // 1:1 control; the fallback accent applies
            onPrimary = base.accent, // 1:1 against the fallback accent too
        )
        val colors = wallpaperColors(unreadable, dark = false, base = base)
        assertEquals(base.text, colors.text)
        assertEquals(base.accent, colors.accent)
        assertEquals(base.onAccent, colors.onAccent)
        // untouched roles stay mapped
        assertEquals(scheme.surfaceContainer, colors.grouped)
    }

    @Test
    fun `interpolation endpoints return the exact schemes`() {
        val from = tsuzukiColors(false)
        val to = tsuzukiColors(true)
        assertEquals(from, lerpColors(from, to, 0f))
        assertEquals(to, lerpColors(from, to, 1f))
        assertEquals(from, lerpColors(from, to, -0.3f))
        assertEquals(to, lerpColors(from, to, 1.4f))
    }
}
