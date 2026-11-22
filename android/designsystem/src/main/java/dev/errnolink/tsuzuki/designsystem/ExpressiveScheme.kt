package dev.errnolink.tsuzuki.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

@Immutable
data class ExpressiveScheme(
    val surface: Color,
    val container: Color,
    val containerHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
)

val LocalExpressiveScheme = staticCompositionLocalOf<ExpressiveScheme?> { null }

private const val MinInkContrast = 4.5f
private const val MaxSeedChroma = 0.55f
private const val TertiaryHueShift = 40f

fun expressiveScheme(seedArgb: Int, dark: Boolean): ExpressiveScheme {
    val seed = Color(seedArgb).toHsl()
    val chroma = seed.s.coerceIn(0f, MaxSeedChroma)
    val hue = seed.h
    val tertiaryHue = hue + TertiaryHueShift
    return if (dark) {
        val surface = hslColor(hue, chroma * 0.35f, 0.09f)
        val container = hslColor(hue, chroma * 0.5f, 0.14f)
        val containerHigh = hslColor(hue, chroma * 0.6f, 0.19f)
        val primary = legibleOn(hslColor(hue, chroma, 0.84f), surface, lighten = true)
        val primaryContainer = hslColor(hue, chroma * 0.85f, 0.32f)
        val tertiary = hslColor(tertiaryHue, chroma * 0.75f, 0.8f)
        ExpressiveScheme(
            surface = surface,
            container = container,
            containerHigh = containerHigh,
            primary = primary,
            onPrimary = readableInk(primary, hue, chroma),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableInk(primaryContainer, hue, chroma * 0.85f),
            tertiary = tertiary,
            onTertiary = readableInk(tertiary, tertiaryHue, chroma * 0.75f),
        )
    } else {
        val surface = hslColor(hue, chroma * 0.35f, 0.97f)
        val container = hslColor(hue, chroma * 0.5f, 0.93f)
        val containerHigh = hslColor(hue, chroma * 0.6f, 0.89f)
        val primary = legibleOn(hslColor(hue, chroma, 0.38f), surface, lighten = false)
        val primaryContainer = hslColor(hue, chroma * 0.85f, 0.88f)
        val tertiary = hslColor(tertiaryHue, chroma * 0.75f, 0.42f)
        ExpressiveScheme(
            surface = surface,
            container = container,
            containerHigh = containerHigh,
            primary = primary,
            onPrimary = readableInk(primary, hue, chroma),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableInk(primaryContainer, hue, chroma * 0.85f),
            tertiary = tertiary,
            onTertiary = readableInk(tertiary, tertiaryHue, chroma * 0.75f),
        )
    }
}

fun expressiveColors(base: TsuzukiColors, scheme: ExpressiveScheme): TsuzukiColors = base.copy(
    background = scheme.surface,
    grouped = scheme.container,
    elevated = scheme.containerHigh,
    accent = scheme.primary,
    onAccent = scheme.onPrimary,
    selectedFill = scheme.primaryContainer,
    glassTint = scheme.container,
)

fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + 0.05f) / (low + 0.05f)
}

/** The accent is also link and caption text: walk its lightness away from the surface until it reads. */
private fun legibleOn(color: Color, surface: Color, lighten: Boolean): Color {
    var candidate = color
    repeat(24) {
        if (contrastRatio(candidate, surface) >= MinInkContrast) return candidate
        val hsl = candidate.toHsl()
        candidate = hslColor(hsl.h, hsl.s, if (lighten) hsl.l + 0.03f else hsl.l - 0.03f)
    }
    return candidate
}

private fun readableInk(fill: Color, hue: Float, chroma: Float): Color {
    val startDark = fill.luminance() > 0.18f
    var candidate = if (startDark) {
        hslColor(hue, chroma * 0.7f, 0.16f)
    } else {
        hslColor(hue, chroma * 0.4f, 0.93f)
    }
    repeat(24) {
        if (contrastRatio(candidate, fill) >= MinInkContrast) return candidate
        val ink = candidate.toHsl()
        candidate = hslColor(ink.h, ink.s, if (startDark) ink.l - 0.04f else ink.l + 0.04f)
    }
    return candidate
}

data class Hsl(val h: Float, val s: Float, val l: Float)

fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f || l == 0f || l == 1f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta).mod(6f))
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return Hsl(h.mod(360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

fun hslColor(h: Float, s: Float, l: Float): Color {
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

/** Full Tsuzuki colours for a cover seed: fixed-role lightness keeps contrast for any hue. */
fun expressiveCoverColors(seed: Color, dark: Boolean): TsuzukiColors =
    expressiveColors(tsuzukiColors(dark), expressiveScheme(seed.toArgb(), dark))
