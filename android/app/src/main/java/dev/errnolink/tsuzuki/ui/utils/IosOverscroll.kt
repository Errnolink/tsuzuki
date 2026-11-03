package dev.errnolink.tsuzuki.ui.utils

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

internal const val RubberBandSlope = 0.55f
internal const val FallbackContainerPx = 1080f
internal const val BounceVelocityClamp = 8000f
internal const val BounceCapFraction = 0.12f
internal const val BounceStiffness = 110f
internal const val SettleMaxFrameSeconds = 1f / 30f
internal const val SettleSubstepSeconds = 1f / 240f
internal const val SettleSnapPx = 0.25f
internal const val SettleSnapVelocity = 5f

internal fun rubberBand(rawDistance: Float, containerPx: Float): Float {
    if (containerPx <= 0f || rawDistance == 0f) return 0f
    val scaled = RubberBandSlope * abs(rawDistance) / containerPx
    val magnitude = containerPx * (1f - 1f / (1f + scaled))
    return sign(rawDistance) * magnitude
}

internal fun rubberBandInverse(displayed: Float, containerPx: Float): Float {
    if (containerPx <= 0f || displayed == 0f) return 0f
    val denom = 1f - abs(displayed) / containerPx
    if (denom <= 1e-6f) return sign(displayed) * Float.MAX_VALUE
    return displayed / (RubberBandSlope * denom)
}

internal fun clampBounceVelocity(velocity: Float): Float = velocity.coerceIn(-BounceVelocityClamp, BounceVelocityClamp)

internal fun boundedBounceVelocity(velocity: Float, containerPx: Float): Float {
    val clamped = clampBounceVelocity(velocity)
    val cap = containerPx * BounceCapFraction
    val maxLaunch = cap * sqrt(BounceStiffness) * E.toFloat()
    return clamped.coerceIn(-maxLaunch, maxLaunch)
}

internal class CriticalSpring(private val stiffness: Float) {
    var position = 0f
    var velocity = 0f
    private var launchSign = 0f

    val settled: Boolean
        get() = abs(position) < SettleSnapPx && abs(velocity) < SettleSnapVelocity

    fun start(startPosition: Float, startVelocity: Float) {
        position = startPosition
        velocity = startVelocity
        launchSign = if (startPosition != 0f) sign(startPosition) else sign(startVelocity)
        if (settled) {
            position = 0f
            velocity = 0f
        }
    }

    fun advance(dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        val damping = 2f * sqrt(stiffness)
        var remaining = dtSeconds
        while (remaining > 0f) {
            val dt = minOf(SettleSubstepSeconds, remaining)
            remaining -= dt
            velocity += (-stiffness * position - damping * velocity) * dt
            position += velocity * dt
            if (launchSign != 0f && position != 0f && sign(position) != launchSign) {
                position = 0f
                velocity = 0f
                return
            }
        }
        if (settled) {
            position = 0f
            velocity = 0f
        }
    }
}

internal class OverscrollAxis {
    val display = mutableFloatStateOf(0f)
    var raw = 0f
        private set
    var settleJob: Job? = null

    fun relax(delta: Float, containerPx: Float): Float {
        if (raw == 0f || delta == 0f || sign(delta) == sign(raw)) return 0f
        val before = raw
        val next = raw + delta
        val crossed = (before > 0f && next <= 0f) || (before < 0f && next >= 0f)
        raw = if (crossed) 0f else next
        display.floatValue = rubberBand(raw, containerPx)
        return if (crossed) -before else delta
    }

    fun accumulate(delta: Float, containerPx: Float) {
        if (delta == 0f) return
        raw += delta
        display.floatValue = rubberBand(raw, containerPx)
    }

    fun resumeFromDisplay(containerPx: Float) {
        raw = rubberBandInverse(display.floatValue, containerPx)
    }

    fun createSettle(leftoverVelocity: Float, containerPx: Float): CriticalSpring? {
        val fromDisplay = display.floatValue
        if (fromDisplay == 0f && leftoverVelocity == 0f) {
            raw = 0f
            return null
        }
        raw = 0f
        val initialVelocity = if (fromDisplay == 0f) {
            boundedBounceVelocity(leftoverVelocity, containerPx)
        } else {
            clampBounceVelocity(leftoverVelocity)
        }
        if (initialVelocity == 0f && fromDisplay == 0f) return null
        return CriticalSpring(BounceStiffness).apply { start(fromDisplay, initialVelocity) }
    }
}

internal class IosOverscrollEffect(private val scope: CoroutineScope) : OverscrollEffect {
    val xAxis = OverscrollAxis()
    val yAxis = OverscrollAxis()

    var containerWidth = FallbackContainerPx
    var containerHeight = FallbackContainerPx

    override val isInProgress: Boolean
        get() = xAxis.display.floatValue != 0f || yAxis.display.floatValue != 0f

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        val relax = Offset(
            xAxis.relax(delta.x, containerWidth),
            yAxis.relax(delta.y, containerHeight),
        )
        val left = delta - relax
        val consumed = performScroll(left)
        val leftover = left - consumed
        if (source == NestedScrollSource.UserInput) {
            if (xAxis.cancelSettle()) xAxis.resumeFromDisplay(containerWidth)
            if (yAxis.cancelSettle()) yAxis.resumeFromDisplay(containerHeight)
            xAxis.accumulate(leftover.x, containerWidth)
            yAxis.accumulate(leftover.y, containerHeight)
        }
        return relax + consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val leftover = velocity - performFling(velocity)
        startSettle(xAxis, leftover.x, containerWidth)
        startSettle(yAxis, leftover.y, containerHeight)
    }

    private fun startSettle(axis: OverscrollAxis, leftoverVelocity: Float, containerPx: Float) {
        axis.settleJob?.cancel()
        axis.settleJob = null
        val spring = axis.createSettle(leftoverVelocity, containerPx) ?: return
        axis.settleJob = scope.launch { runSettle(axis, spring) }
    }

    private suspend fun runSettle(axis: OverscrollAxis, spring: CriticalSpring) {
        var lastFrame = -1L
        while (!spring.settled) {
            val now = withFrameNanos { it }
            val dt = if (lastFrame < 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, SettleMaxFrameSeconds)
            lastFrame = now
            spring.advance(dt)
            axis.display.floatValue = spring.position
        }
        axis.display.floatValue = 0f
    }

    override val node: DelegatableNode = IosOverscrollNode(this)

    private class IosOverscrollNode(private val effect: IosOverscrollEffect) : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            effect.containerWidth = when {
                constraints.hasBoundedWidth && constraints.maxWidth > 0 -> constraints.maxWidth.toFloat()
                placeable.width > 0 -> placeable.width.toFloat()
                else -> FallbackContainerPx
            }
            effect.containerHeight = when {
                constraints.hasBoundedHeight && constraints.maxHeight > 0 -> constraints.maxHeight.toFloat()
                placeable.height > 0 -> placeable.height.toFloat()
                else -> FallbackContainerPx
            }
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(x = 0, y = 0) {
                    translationX = effect.xAxis.display.floatValue
                    translationY = effect.yAxis.display.floatValue
                }
            }
        }
    }
}

private fun OverscrollAxis.cancelSettle(): Boolean {
    val job = settleJob ?: return false
    settleJob = null
    job.cancel()
    return true
}

internal class IosOverscrollFactory(private val scope: CoroutineScope) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = IosOverscrollEffect(scope)

    override fun equals(other: Any?): Boolean = other is IosOverscrollFactory && other.scope == scope

    override fun hashCode(): Int = scope.hashCode()
}

@Composable
fun rememberIosOverscrollFactory(): OverscrollFactory {
    val scope = rememberCoroutineScope()
    return remember { IosOverscrollFactory(scope) }
}
