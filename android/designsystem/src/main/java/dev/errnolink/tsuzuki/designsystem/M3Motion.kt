package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Material 3 transition tokens (m3.material.io/styles/motion/easing-and-duration/tokens-specs and
 * /styles/motion/transitions). M3 transitions use the easing-and-duration system, not springs.
 */
object M3Motion {
    val Emphasized: Easing = PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val Short1 = 50
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600

    /** Shared axis / forward-backward slide distance. */
    val SharedAxisSlide = 30.dp

    /** Fade-through split: outgoing content is fully gone at this fraction, incoming starts here. */
    const val FadeThroughThreshold = 0.35f

    /** Incoming scale for the top-level fade-through pattern. */
    const val FadeThroughScale = 0.92f

    /** Scrim over the stationary backdrop of a container transform. */
    const val ContainerScrimAlpha = 0.32f

    /** Reduced motion: subtle fades replace slides, scales, parallax and shape morphing. */
    const val ReducedFade = Short3

    /** Begin and end on screen. */
    fun <T> emphasized(durationMillis: Int = Long2, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Emphasized)

    /** Enter the screen. */
    fun <T> enter(durationMillis: Int = Medium4, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, EmphasizedDecelerate)

    /** Exit the screen permanently. */
    fun <T> exit(durationMillis: Int = Short4, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, EmphasizedAccelerate)

    /** Small utility transitions. */
    fun <T> standard(durationMillis: Int = Medium2, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Standard)

    fun <T> reducedFade(): FiniteAnimationSpec<T> = tween(ReducedFade, easing = Standard)

    /** Outgoing alpha for fade-through at transition [fraction] (0..1). */
    fun fadeThroughOut(fraction: Float): Float =
        (1f - fraction / FadeThroughThreshold).coerceIn(0f, 1f)

    /** Incoming alpha for fade-through at transition [fraction] (0..1). */
    fun fadeThroughIn(fraction: Float): Float =
        ((fraction - FadeThroughThreshold) / (1f - FadeThroughThreshold)).coerceIn(0f, 1f)
}
