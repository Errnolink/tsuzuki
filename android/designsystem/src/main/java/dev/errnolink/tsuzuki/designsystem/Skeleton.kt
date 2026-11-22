package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton (Kasane `skeleton`): placeholders shaped like the content they stand for. The block is a
 * tone one step off the surface it sits on in both themes, a lifted shimmer sweeps across in reading
 * direction over 1.6 s, and reduce motion becomes a slow opacity pulse. Shapes: text, title, cover,
 * circle and card. Show after a beat and keep for at least 300 ms so quick loads don't flash.
 */
object SkeletonSpec {
    /** Kasane: tone = on-surface 11 % over the surface the block sits on. Pure, so tests can pin it. */
    fun tone(text: Color, surface: Color): Color = text.copy(alpha = 0.11f).compositeOver(surface)

    /** The lifted sweep band, raised from the base tone. */
    fun highlight(tone: Color): Color = lerp(tone, Color.White, 0.35f).copy(alpha = 0.9f)

    const val SWEEP_MS = 1_600
    const val PULSE_MS = 1_800
}

/** Shared sweep clock: skeletons under one clock shimmer in step, like Kasane's CSS animations. */
val LocalSkeletonPhase = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun SkeletonClock(content: @Composable () -> Unit) {
    if (TsuzukiTheme.motion.reduced) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSkeletonPhase provides null, content = content)
        return
    }
    val phase = rememberInfiniteTransition(label = "skeleton sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SkeletonSpec.SWEEP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton sweep phase",
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalSkeletonPhase provides phase, content = content)
}

/**
 * The base block. [shape] sets the placeholder's outline; width and height come from the modifier
 * (see [SkeletonText], [SkeletonTitle], [SkeletonCover], [SkeletonCircle], [SkeletonCard] for the
 * content shapes).
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val colors = TsuzukiTheme.colors
    val tone = SkeletonSpec.tone(colors.text, colors.grouped)
    val highlight = SkeletonSpec.highlight(tone)
    val reduced = TsuzukiTheme.motion.reduced
    // The clock is read in draw, never in composition: a block redraws each frame, it does not
    // recompose. A block under a SkeletonClock runs no animator of its own.
    val shared = LocalSkeletonPhase.current
    val sweep: State<Float>? = when {
        reduced -> null
        shared != null -> shared
        else -> ownClock(0f, 1f, SkeletonSpec.SWEEP_MS, LinearEasing, RepeatMode.Restart)
    }
    val pulse: State<Float>? = if (reduced) {
        ownClock(1f, 0.5f, SkeletonSpec.PULSE_MS, androidx.compose.animation.core.FastOutSlowInEasing, RepeatMode.Reverse)
    } else {
        null
    }

    Canvas(modifier.clip(shape)) {
        val phase: Float? = sweep?.value
        val alpha = pulse?.value ?: 1f
        drawRect(tone, alpha = alpha)
        val clock = phase
        if (clock != null) {
            // Reading direction: the band enters from the leading edge and leaves at the trailing one.
            val travel = (clock * 2f - 1f) * (size.width * 1.25f)
            val band = size.width * 0.75f
            val start = Offset(travel - band / 2f, 0f)
            val end = Offset(travel + band / 2f, size.height * 0.35f)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to highlight,
                    1f to Color.Transparent,
                    start = start,
                    end = end,
                ),
                alpha = alpha,
            )
        }
    }
}

@Composable
private fun ownClock(
    from: Float,
    to: Float,
    durationMs: Int,
    easing: androidx.compose.animation.core.Easing,
    repeat: RepeatMode,
): State<Float> = rememberInfiniteTransition(label = "skeleton own").animateFloat(
    from, to,
    infiniteRepeatable(tween(durationMs, easing = easing), repeat),
    label = "skeleton own clock",
)

/** A line of body text at [height] tall and [widthFraction] of the row. */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    widthFraction: Float? = null,
    height: Dp = 12.dp,
) {
    Skeleton(
        modifier
            .then(if (widthFraction != null) Modifier.fillMaxWidth(widthFraction) else Modifier)
            .height(height),
        shape = RoundedCornerShape(4.dp),
    )
}

/** A heading line: 60 % width by default, a step taller than body text. */
@Composable
fun SkeletonTitle(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    height: Dp = 16.dp,
) {
    SkeletonText(modifier.fillMaxWidth(widthFraction).then(Modifier), widthFraction = null, height = height)
}

/** A cover placeholder: 2:3 with the app's cover corners. */
@Composable
fun SkeletonCover(modifier: Modifier = Modifier) {
    Skeleton(modifier.aspectRatio(2f / 3f), shape = coverShape())
}

/** A round placeholder (avatar, trailing dot). */
@Composable
fun SkeletonCircle(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Skeleton(modifier.size(size), shape = RoundedCornerShape(TsuzukiCorners.capsulePercent))
}

/** A wide card placeholder, 16:10 with container corners. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Skeleton(modifier.aspectRatio(16f / 10f), shape = RoundedCornerShape(TsuzukiCorners.group))
}
