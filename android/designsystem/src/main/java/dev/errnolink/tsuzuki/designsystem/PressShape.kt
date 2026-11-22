package dev.errnolink.tsuzuki.designsystem

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

val CapsuleRestRadius = 999.dp

const val PressInMs = 70L
const val PressMinHoldMs = 120L
const val PressedRadiusFraction = 0.30f

data class PressPose(val showPressed: Boolean, val pressFraction: Float)

fun pressPose(
    pressedAtMs: Long,
    releasedAtMs: Long?,
    nowMs: Long,
    pressInMs: Long = PressInMs,
    minHoldMs: Long = PressMinHoldMs,
): PressPose {
    val effectiveRelease = releasedAtMs?.let { maxOf(it, pressedAtMs + minHoldMs) } ?: Long.MAX_VALUE
    return if (nowMs < effectiveRelease) {
        val fraction = ((nowMs - pressedAtMs).toFloat() / pressInMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        PressPose(showPressed = true, pressFraction = fraction)
    } else {
        PressPose(showPressed = false, pressFraction = 0f)
    }
}

@Stable
class PressShapeState internal constructor(
    internal val interactionSource: InteractionSource,
    private val fraction: () -> Float,
    private val restRadius: Dp,
    private val pressedScale: Float,
    val enabled: Boolean,
    private val pressedRadius: Dp?,
    private val densityPxPerDp: Float,
    private val large: MutableState<Boolean>,
    private val minDimensionPx: MutableState<Float>,
) {
    val pressFraction: Float
        get() = if (large.value) fraction() else 0f

    val shape: RoundedCornerShape
        get() {
            val f = pressFraction
            val restPx = restRadius.value * densityPxPerDp
            val pressedPx = pressedRadius?.value?.times(densityPxPerDp)
                ?: minDimensionPx.value.coerceAtLeast(0f) * PressedRadiusFraction
            val radiusPx = (restPx + (pressedPx - restPx) * f).coerceAtLeast(0f)
            val unit = Density(densityPxPerDp, densityPxPerDp)
            return RoundedCornerShape(with(unit) { radiusPx.toDp() })
        }

    val scale: Float
        get() = 1f + (pressedScale - 1f) * fraction()

    internal fun measure(minDimension: Int) {
        minDimensionPx.value = minDimension.toFloat()
        large.value = minDimension >= (32.dp.value * densityPxPerDp).toInt()
    }
}

/**
 * Press fraction shared by Kasane controls: 0 at rest, 1 fully pressed. [pressIn] runs while the
 * finger is down (effects timing: no bounce), [release] runs when it lifts (a spatial spring, so it
 * may overshoot below 0 — callers clamp where overshoot would break a rule).
 */
@Composable
internal fun rememberPressFraction(
    interactionSource: InteractionSource,
    pressIn: FiniteAnimationSpec<Float>,
    release: FiniteAnimationSpec<Float>,
): State<Float> {
    val fraction = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) fraction.animateTo(1f, pressIn) else fraction.animateTo(0f, release)
    }
    return remember { derivedStateOf { fraction.value } }
}

@Composable
fun rememberPressShapeState(
    interactionSource: InteractionSource,
    restRadius: Dp,
    pressedScale: Float = 0.93f,
    pressedRadius: Dp? = null,
    forcedEnabled: Boolean? = null,
): PressShapeState {
    val reduced = TsuzukiTheme.motion.reduced
    val enabled = forcedEnabled ?: false
    val densityPxPerDp = androidx.compose.ui.platform.LocalDensity.current.density
    val fraction = remember { Animatable(0f) }
    val large = remember { mutableStateOf(true) }
    val minDimensionPx = remember { mutableFloatStateOf(0f) }
    var pressedAt by remember { mutableStateOf<Long?>(null) }
    var releasedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressedAt = SystemClock.uptimeMillis()
                    releasedAt = null
                }
                is PressInteraction.Release -> releasedAt = SystemClock.uptimeMillis()
                is PressInteraction.Cancel -> releasedAt = SystemClock.uptimeMillis()
            }
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) fraction.snapTo(0f)
    }
    LaunchedEffect(pressedAt, enabled) {
        val start = pressedAt ?: return@LaunchedEffect
        if (!enabled) {
            fraction.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            val pose = pressPose(start, releasedAt, SystemClock.uptimeMillis())
            if (pose.showPressed) {
                fraction.animateTo(1f, tween(PressInMs.toInt()))
                withFrameNanos { }
            } else {
                fraction.animateTo(0f, KasaneSprings.pressRelease(reduced))
                break
            }
        }
    }
    return remember(interactionSource, restRadius, pressedScale, pressedRadius, enabled, densityPxPerDp) {
        PressShapeState(interactionSource, { fraction.value }, restRadius, pressedScale, enabled, pressedRadius, densityPxPerDp, large, minDimensionPx)
    }
}

@Composable
fun rememberPressShape(
    interactionSource: InteractionSource,
    restRadius: Dp,
    pressedScale: Float = 0.93f,
): RoundedCornerShape =
    rememberPressShapeState(interactionSource, restRadius, pressedScale).shape

@Composable
fun Modifier.pressShape(
    interactionSource: InteractionSource,
    restRadius: Dp,
    pressedScale: Float = 0.93f,
): Modifier {
    val state = rememberPressShapeState(interactionSource, restRadius, pressedScale)
    if (!state.enabled) {
        return this.clip(RoundedCornerShape(restRadius)).pressScale(interactionSource)
    }
    return pressShapeScale(state).graphicsLayer {
        shape = state.shape
        clip = true
    }
}

@Composable
fun Modifier.pressShapeScale(state: PressShapeState): Modifier {
    if (!state.enabled) return this
    return onSizeChanged { state.measure(minOf(it.width, it.height)) }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
        }
}
