package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal val PillLiftGrowth = 9.dp

/** The live glass lens grows from the 46 dp pill to 64 dp: past the 54 dp bar, as in iOS 26. */
internal val LensLiftGrowth = 18.dp

internal object PillTuning {
    const val TravelStiffness = 360f
    const val TravelDampingRatio = 0.9f
    const val LiftStiffness = 300f
    const val LiftDampingWidth = 0.55f
    const val LiftDampingHeight = 0.65f
    const val LiftReleaseProgress = 0.95f
    const val MaxReleaseVelocity = 25f
    const val PositionSnap = 0.002f
    const val VelocitySnap = 0.03f
    const val LiftSnap = 0.001f
    const val LiftVelocitySnap = 0.02f
    const val SubstepSeconds = 1f / 200f
    const val DragFollowSeconds = 0.03f
}

/**
 * Kasane lens.js: the pill stretches along the move (at most 1.15×), peaking mid-flight and
 * settling round at the end. Pure; behaviour-tested.
 */
internal const val PillStretchCap = 0.15f
internal const val PillStretchRefPx = 600f

/**
 * NAV-05: the pill's stretch axis (lift × travel stretch) never exceeds this — a wider lens reads
 * as a bounce, not a stretch. Pure; behaviour-tested.
 */
internal const val PillStretchScaleCap = 1.08f

internal fun pillStretchScale(liftScale: Float, stretchFraction: Float): Float =
    (liftScale * (1f + stretchFraction)).coerceAtMost(PillStretchScaleCap)

internal fun pillTravelStretch(travelProgress: Float, travelDistancePx: Float): Float {
    val amplitude = minOf(PillStretchCap, abs(travelDistancePx) / PillStretchRefPx)
    if (amplitude == 0f) return 0f
    return amplitude * sin(PI.toFloat() * travelProgress.coerceIn(0f, 1f))
}

internal class PillPhysics(initialIndex: Int) {
    var reduceMotion = false

    var index = initialIndex
        private set
    var position = initialIndex.toFloat()
        private set
    var velocity = 0f
        private set
    var liftWidth = 0f
        private set
    var liftWidthVelocity = 0f
        private set
    var liftHeight = 0f
        private set
    var liftHeightVelocity = 0f
        private set
    var liftEngaged = false
        private set
    var dragging = false
        private set

    private var travelActive = false
    private var travelStart = initialIndex.toFloat()
    private var travelTarget = initialIndex.toFloat()
    private var handoverDone = true
    private var liftTarget = 0f
    private var dragTarget = initialIndex.toFloat()

    fun travelProgress(): Float {
        if (!travelActive) return 1f
        val span = travelTarget - travelStart
        if (abs(span) < 1e-6f) return 1f
        return ((position - travelStart) / span).coerceIn(0f, 1f)
    }

    /** Distance the current travel covers, in steps (0 when resting or dragging). */
    fun travelSpanSteps(): Float = if (travelActive) abs(travelTarget - travelStart) else 0f

    val isFlat: Boolean
        get() = !travelActive && !dragging && !liftEngaged &&
            abs(liftWidth) < PillTuning.LiftSnap && abs(liftWidthVelocity) < PillTuning.LiftVelocitySnap &&
            abs(liftHeight) < PillTuning.LiftSnap && abs(liftHeightVelocity) < PillTuning.LiftVelocitySnap &&
            abs(position - travelTarget) < PillTuning.PositionSnap &&
            abs(velocity) < PillTuning.VelocitySnap

    val active: Boolean
        get() = travelActive || dragging || liftEngaged ||
            abs(liftWidth) >= PillTuning.LiftSnap || abs(liftWidthVelocity) >= PillTuning.LiftVelocitySnap ||
            abs(liftHeight) >= PillTuning.LiftSnap || abs(liftHeightVelocity) >= PillTuning.LiftVelocitySnap ||
            abs(position - travelTarget) >= PillTuning.PositionSnap || abs(velocity) >= PillTuning.VelocitySnap

    fun animateTo(target: Int) {
        if (target == index) return
        index = target
        if (dragging) return
        if (reduceMotion) {
            position = target.toFloat()
            velocity = 0f
            travelActive = false
            travelStart = position
            travelTarget = position
            handoverDone = true
            liftEngaged = false
            liftTarget = 0f
            return
        }
        travelStart = position
        travelTarget = target.toFloat()
        travelActive = abs(travelTarget - position) > 0f
        if (travelActive) {
            handoverDone = false
            liftEngaged = true
            liftTarget = 1f
        }
    }

    fun snapTo(target: Int) {
        index = target
        position = target.toFloat()
        velocity = 0f
        travelActive = false
        travelStart = position
        travelTarget = position
        handoverDone = true
        liftEngaged = false
        liftTarget = 0f
        dragging = false
    }

    fun startDrag() {
        dragging = true
        travelActive = false
        velocity = 0f
        dragTarget = position
        if (!reduceMotion) {
            liftEngaged = true
            liftTarget = 1f
        }
    }

    fun dragTo(target: Float) {
        if (!dragging) return
        dragTarget = target
        if (reduceMotion) position = target
    }

    fun release(nextIndex: Int, velocityStepsPerSecond: Float) {
        dragging = false
        index = nextIndex
        travelTarget = nextIndex.toFloat()
        travelStart = position
        if (reduceMotion) {
            position = travelTarget
            velocity = 0f
            travelActive = false
            handoverDone = true
            liftEngaged = false
            liftTarget = 0f
            return
        }
        travelActive = abs(travelTarget - position) > 0f
        if (travelActive) {
            velocity = velocityStepsPerSecond.coerceIn(-PillTuning.MaxReleaseVelocity, PillTuning.MaxReleaseVelocity)
            handoverDone = false
            liftEngaged = true
            liftTarget = 1f
        } else {
            velocity = 0f
            handoverDone = true
            liftTarget = 0f
        }
    }

    fun tick(dtSeconds: Float): Boolean {
        if (dtSeconds > 0f) {
            var remaining = dtSeconds
            while (remaining > 0f) {
                val dt = minOf(PillTuning.SubstepSeconds, remaining)
                remaining -= dt
                step(dt)
            }
        }
        snapSettled()
        return active
    }

    private fun step(dt: Float) {
        if (dragging) {
            if (abs(dragTarget - position) > 1e-4f) {
                position += (dragTarget - position) * (1f - exp(-dt / PillTuning.DragFollowSeconds))
                if (abs(dragTarget - position) < 1e-3f) position = dragTarget
            }
            velocity = 0f
        } else if (travelActive) {
            val damping = 2f * PillTuning.TravelDampingRatio * sqrt(PillTuning.TravelStiffness)
            val acceleration = PillTuning.TravelStiffness * (travelTarget - position) - damping * velocity
            velocity += acceleration * dt
            position += velocity * dt
            if (abs(travelTarget - position) < PillTuning.PositionSnap && abs(velocity) < PillTuning.VelocitySnap) {
                position = travelTarget
                velocity = 0f
                travelActive = false
                handoverDone = true
                liftTarget = 0f
            }
        }
        if (!handoverDone && !dragging && (travelProgress() >= PillTuning.LiftReleaseProgress || !travelActive)) {
            handoverDone = true
            liftTarget = 0f
        }
        stepLift(dt)
    }

    private fun stepLift(dt: Float) {
        val dampingBase = 2f * sqrt(PillTuning.LiftStiffness)
        val widthAcceleration = PillTuning.LiftStiffness * (liftTarget - liftWidth) -
            dampingBase * PillTuning.LiftDampingWidth * liftWidthVelocity
        liftWidthVelocity += widthAcceleration * dt
        liftWidth += liftWidthVelocity * dt
        val heightAcceleration = PillTuning.LiftStiffness * (liftTarget - liftHeight) -
            dampingBase * PillTuning.LiftDampingHeight * liftHeightVelocity
        liftHeightVelocity += heightAcceleration * dt
        liftHeight += liftHeightVelocity * dt
        if (abs(liftWidth - liftTarget) < PillTuning.LiftSnap && abs(liftWidthVelocity) < PillTuning.LiftVelocitySnap) {
            liftWidth = liftTarget
            liftWidthVelocity = 0f
        }
        if (abs(liftHeight - liftTarget) < PillTuning.LiftSnap && abs(liftHeightVelocity) < PillTuning.LiftVelocitySnap) {
            liftHeight = liftTarget
            liftHeightVelocity = 0f
        }
        if (liftTarget == 0f && liftWidth == 0f && liftHeight == 0f) liftEngaged = false
    }

    private fun snapSettled() {
        if (!travelActive && !dragging && abs(position - travelTarget) < PillTuning.PositionSnap &&
            abs(velocity) < PillTuning.VelocitySnap
        ) {
            position = travelTarget
            velocity = 0f
        }
    }
}

class TsuzukiPillMotion(scope: CoroutineScope?, initialIndex: Int) {
    private val frameScope: CoroutineScope? = scope
    private val physics = PillPhysics(initialIndex)
    private var loopJob: Job? = null

    var reduceMotion: Boolean
        get() = physics.reduceMotion
        set(value) {
            physics.reduceMotion = value
        }

    var stepDp: Float = 0f

    private val positionState = mutableFloatStateOf(physics.position)
    private val liftXState = mutableFloatStateOf(0f)
    private val liftYState = mutableFloatStateOf(0f)
    private val flatState = mutableStateOf(true)
    private val liftedState = mutableStateOf(false)

    val index: Int
        get() = physics.index

    val position: Float
        get() = positionState.floatValue

    val liftX: Float
        get() = liftXState.floatValue

    val liftY: Float
        get() = liftYState.floatValue

    val isFlat: Boolean
        get() = flatState.value

    val isLifted: Boolean
        get() = liftedState.value

    val dragging: Boolean
        get() = physics.dragging

    fun animateTo(index: Int) {
        physics.animateTo(index)
        sync()
        requestFrame()
    }

    fun snapTo(index: Int) {
        physics.snapTo(index)
        sync()
    }

    fun startDrag() {
        physics.startDrag()
        sync()
        requestFrame()
    }

    fun dragTo(positionInSteps: Float) {
        physics.dragTo(positionInSteps)
        sync()
        requestFrame()
    }

    fun release(nextIndex: Int, velocityInStepsPerSecond: Float = 0f) {
        physics.release(nextIndex, velocityInStepsPerSecond)
        sync()
        requestFrame()
    }

    fun travelProgress(): Float = physics.travelProgress()

    fun travelSpanSteps(): Float = physics.travelSpanSteps()

    fun liveSize(rest: Size, lifted: Size): Size {
        val lift = (liftX + liftY) / 2f
        return Size(
            rest.width + (lifted.width - rest.width) * lift,
            rest.height + (lifted.height - rest.height) * lift,
        )
    }

    fun placement(
        /** Read at measure and inside the layer (WP6.3): the caller's width can animate. */
        rest: () -> Size,
        lifted: () -> Size,
        centerPx: () -> Offset,
        stretch: () -> Float = { 0f },
    ): Modifier = Modifier.layout { measurable, constraints ->
        val restSize = rest()
        val width = restSize.width.roundToInt().coerceAtLeast(1)
        val height = restSize.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(x = 0, y = 0) {
                val center = centerPx()
                translationX = center.x - width / 2f
                translationY = center.y - height / 2f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                val live = liveSize(restSize, lifted())
                scaleX = pillStretchScale(live.width / width, stretch())
                scaleY = live.height / height
            }
        }
    }

    internal fun tick(dtSeconds: Float, timeSeconds: Double): Boolean {
        val stillActive = physics.tick(dtSeconds)
        sync()
        return stillActive
    }

    private fun sync() {
        positionState.floatValue = physics.position
        liftXState.floatValue = physics.liftWidth
        liftYState.floatValue = physics.liftHeight
        flatState.value = physics.isFlat
        liftedState.value = physics.liftEngaged
    }

    private fun requestFrame() {
        val target = frameScope ?: return
        if (loopJob?.isActive == true) return
        if (!physics.active) return
        loopJob = target.launch {
            var lastFrame = -1L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame < 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
                lastFrame = now
                if (!tick(dt, now / 1e9)) break
            }
            loopJob = null
        }
    }
}
