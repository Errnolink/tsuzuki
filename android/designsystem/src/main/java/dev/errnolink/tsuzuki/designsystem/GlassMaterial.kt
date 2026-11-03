package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassMaterial(
    internal val blurRadius: Dp,
    private val lightTint: Float,
    private val darkTint: Float,
    internal val fallbackAlpha: Float,
) {
    Regular(32.dp, 0.78f, 0.74f, 0.98f),
    Thick(32.dp, 0.86f, 0.82f, 0.99f),
    Opaque(0.dp, 1f, 1f, 1f),
    ;

    internal fun tintAlpha(dark: Boolean, strength: Float): Float {
        if (this == Opaque) return 1f
        val base = if (dark) darkTint else lightTint
        val floor = base - 0.4f
        val limit = if (dark) 0.87f else 0.90f
        return floor + (limit - floor) * strength
    }

    companion object {
        const val DefaultTintStrength = 0.7f
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
