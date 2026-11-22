package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import dev.chrisbanes.haze.HazeTint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GlassTintPreset(val label: String, val strength: Float) {
    Clear("Clear", 0f),
    Tinted("Tinted", 0.45f),
    Frosted("Frosted", 0.75f),
    Opaque("Opaque", 1f);

    companion object {
        fun nearestIndex(strength: Float): Int = when {
            strength < 0.23f -> 0
            strength < 0.60f -> 1
            strength < 0.88f -> 2
            else -> 3
        }

        fun summaryLabel(strength: Float): String {
            val s = strength.coerceIn(0f, 1f)
            val pct = (s * 100f).roundToInt()
            if (s >= 0.995f) return Opaque.label
            val nearest = entries[nearestIndex(s)]
            return if (abs(s - nearest.strength) <= 0.02f) {
                "${nearest.label} · $pct%"
            } else {
                "Custom · $pct%"
            }
        }
    }
}

enum class GlassMaterial(
    internal val blurRadius: Dp,
    private val lightTint: Float,
    private val darkTint: Float,
    internal val fallbackAlpha: Float,
    internal val saturationLoss: Float,
) {
    Regular(32.dp, 0.78f, 0.74f, 0.98f, 0f),
    Thick(96.dp, 0.86f, 0.82f, 0.99f, 0.25f),
    Opaque(0.dp, 1f, 1f, 1f, 0f),
    ;

    internal fun tintAlpha(dark: Boolean, strength: Float): Float {
        if (this == Opaque) return 1f
        val base = if (dark) darkTint else lightTint
        val floor = base - 0.4f
        val limit = if (dark) 0.87f else 0.90f
        return floor + (limit - floor) * strength
    }

    /**
     * Kasane glass legibility tint over the lifted, refracted backdrop. iOS Regular glass has no
     * tint at all; we keep a small one that the glass slider ([strength]) scales, more for Thick
     * (bars, menus) than for Regular. At chrome-over-content strength (the reader over a page of
     * either scheme) the full [tintAlpha] applies, so contrast holds over white and black pages.
     */
    internal fun kasaneTintAlpha(dark: Boolean, strength: Float): Float = when {
        this == Opaque -> 1f
        strength >= ChromeOverContentMinStrength -> tintAlpha(dark, strength)
        this == Regular -> (if (dark) 0.14f else 0.20f) + 0.38f * strength
        else -> (if (dark) 0.28f else 0.36f) + 0.44f * strength
    }

    /** The backdrop loses [saturationLoss] of its colour first, then takes the tint. */
    internal fun hazeTints(tint: Color): List<HazeTint> = buildList {
        if (saturationLoss > 0f) add(HazeTint(Color.Black.copy(alpha = saturationLoss), BlendMode.Saturation))
        add(HazeTint(tint))
    }

    companion object {
        val DefaultTintStrength = GlassTintPreset.Tinted.strength

        /**
         * Chrome over arbitrary content (reader pages) can sit on a page of the opposite scheme
         * (dark chrome over a white page), so its tint never drops below this strength, whatever
         * the glass slider says.
         */
        const val ChromeOverContentMinStrength = 0.85f

        fun chromeOverContentStrength(userStrength: Float): Float = userStrength.coerceIn(ChromeOverContentMinStrength, 1f)

        internal const val InputScale = 0.33f
        internal const val MotionInputScale = 0.2f
        internal const val NoiseFactor = 0.02f
        internal val RingWidth = 0.5.dp
        internal val HighlightInset = 1.dp
        internal val HighlightWidth = 0.5.dp
        internal val LightRing = Color(0xFFDBDBDB)
        internal val DarkRing = Color(0xFFA6A6A6)
        internal val LightHighlight = Color.White.copy(alpha = 0.70f)
        internal val DarkHighlight = Color.White.copy(alpha = 0.25f)
    }
}

/**
 * The GlassSurface rim recipe for controls that draw their own lens (switch thumb, slider handle,
 * segmented pill): a hairline ring plus a top highlight that fades down, at [alpha] strength.
 */
internal fun DrawScope.drawGlassRim(
    dark: Boolean,
    alpha: Float,
    topLeft: Offset = Offset.Zero,
    rimSize: Size = size,
) {
    if (alpha <= 0.02f) return
    val radius = CornerRadius(rimSize.minDimension / 2f)
    val inset = GlassMaterial.HighlightInset.toPx()
    drawRoundRect(
        (if (dark) GlassMaterial.DarkRing else GlassMaterial.LightRing).copy(alpha = alpha),
        topLeft = topLeft,
        size = rimSize,
        cornerRadius = radius,
        style = Stroke(GlassMaterial.RingWidth.toPx()),
    )
    if (rimSize.minDimension > inset * 2f) {
        drawRoundRect(
            Brush.verticalGradient(
                listOf((if (dark) GlassMaterial.DarkHighlight else GlassMaterial.LightHighlight).copy(alpha = alpha), Color.Transparent),
                startY = topLeft.y,
                endY = topLeft.y + rimSize.height,
            ),
            topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
            size = Size((rimSize.width - inset * 2f).coerceAtLeast(0f), (rimSize.height - inset * 2f).coerceAtLeast(0f)),
            cornerRadius = radius,
            style = Stroke(GlassMaterial.HighlightWidth.toPx()),
        )
    }
}
