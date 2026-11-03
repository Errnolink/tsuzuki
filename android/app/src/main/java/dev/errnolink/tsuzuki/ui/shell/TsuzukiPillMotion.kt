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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal val PillLiftGrowth = 9.dp

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
    const val DeviationSnap = 5e-4f
    const val SubstepSeconds = 1f / 200f
    const val SquashWindowSeconds = 0.32f
    const val SquashScale = 5e-3f
    const val SquashClamp = 0.10f
    const val SquashEaseSeconds = 0.2f
    const val SignEaseSeconds = 0.24f
    const val DragFollowSeconds = 0.03f
    const val HistoryCapacity = 96
}

internal class PillPhysics(initialIndex: Int) {
    var reduceMotion = false
    var squashEnabled = true

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
    var deviation = 0f
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
    private var clock = 0f
    private var signKey = 0f
    private var rawDeviation = 0f
    private val historyTimes = FloatArray(PillTuning.HistoryCapacity)
    private val historyPositions = FloatArray(PillTuning.HistoryCapacity)
    private var historyCount = 0
    private var historyHead = 0

    fun travelProgress(): Float {
        if (!travelActive) return 1f
        val span = travelTarget - travelStart
        if (abs(span) < 1e-6f) return 1f
        return ((position - travelStart) / span).coerceIn(0f, 1f)
    }

    val isFlat: Boolean
        get() = !travelActive && !dragging && !liftEngaged &&
            abs(liftWidth) < PillTuning.LiftSnap && abs(liftWidthVelocity) < PillTuning.LiftVelocitySnap &&
            abs(liftHeight) < PillTuning.LiftSnap && abs(liftHeightVelocity) < PillTuning.LiftVelocitySnap &&
            abs(deviation) < PillTuning.DeviationSnap &&
            abs(position - travelTarget) < PillTuning.PositionSnap &&
            abs(velocity) < PillTuning.VelocitySnap

    val active: Boolean
        get() = travelActive || dragging || liftEngaged ||
            abs(liftWidth) >= PillTuning.LiftSnap || abs(liftWidthVelocity) >= PillTuning.LiftVelocitySnap ||
            abs(liftHeight) >= PillTuning.LiftSnap || abs(liftHeightVelocity) >= PillTuning.LiftVelocitySnap ||
            abs(deviation) >= PillTuning.DeviationSnap ||
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
            clearHistory()
            return
        }
        travelStart = position
        travelTarget = target.toFloat()
        travelActive = abs(travelTarget - position) > 0f
        if (travelActive) {
            handoverDone = false
            liftEngaged = true
            liftTarget = 1f
            clearHistory()
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
        deviation = 0f
        clearHistory()
    }

    fun startDrag() {
        dragging = true
        travelActive = false
        velocity = 0f
        dragTarget = position
        signKey = 0f
        clearHistory()
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
            deviation = 0f
            clearHistory()
            return
        }
        travelActive = abs(travelTarget - position) > 0f
        if (travelActive) {
            velocity = velocityStepsPerSecond.coerceIn(-PillTuning.MaxReleaseVelocity, PillTuning.MaxReleaseVelocity)
            handoverDone = false
            liftEngaged = true
            liftTarget = 1f
            signKey = sign(travelTarget - position)
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
            clock += dtSeconds
            sampleHistory()
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
        stepSquash(dt)
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

    private fun stepSquash(dt: Float) {
        if (!squashEnabled) {
            deviation = 0f
            return
        }
        val motion = travelActive || dragging
        val raw = if (motion) {
            (averageAcceleration() * PillTuning.SquashScale).coerceIn(-PillTuning.SquashClamp, PillTuning.SquashClamp)
        } else {
            0f
        }
        rawDeviation = raw
        val targetKey = when {
            raw > 0f -> 1f
            raw < 0f -> -1f
            else -> signKey
        }
        signKey += (targetKey - signKey) * (1f - exp(-dt / PillTuning.SignEaseSeconds))
        deviation += (abs(raw) * signKey - deviation) * (1f - exp(-dt / PillTuning.SquashEaseSeconds))
        if (abs(deviation) < PillTuning.DeviationSnap && abs(raw) < PillTuning.DeviationSnap) deviation = 0f
    }

    private fun snapSettled() {
        if (!travelActive && !dragging && abs(position - travelTarget) < PillTuning.PositionSnap &&
            abs(velocity) < PillTuning.VelocitySnap
        ) {
            position = travelTarget
            velocity = 0f
        }
        if (abs(deviation) < PillTuning.DeviationSnap && abs(rawDeviation) < PillTuning.DeviationSnap) deviation = 0f
    }

    private fun sampleHistory() {
        historyTimes[historyHead] = clock
        historyPositions[historyHead] = position
        historyHead = (historyHead + 1) % PillTuning.HistoryCapacity
        historyCount = minOf(historyCount + 1, PillTuning.HistoryCapacity)
        val cutoff = clock - PillTuning.SquashWindowSeconds
        while (historyCount > 0) {
            val oldest = (historyHead - historyCount + PillTuning.HistoryCapacity) % PillTuning.HistoryCapacity
            if (historyTimes[oldest] >= cutoff) break
            historyCount--
        }
    }

    private fun averageAcceleration(): Float {
        if (historyCount < 4) return 0f
        val n = historyCount
        val capacity = PillTuning.HistoryCapacity
        fun slot(i: Int): Int = (historyHead - n + i + capacity) % capacity
        val first = slot(0)
        val second = slot(1)
        val penultimate = slot(n - 2)
        val last = slot(n - 1)
        val firstSpan = historyTimes[second] - historyTimes[first]
        val lastSpan = historyTimes[last] - historyTimes[penultimate]
        val span = historyTimes[last] - historyTimes[first]
        if (firstSpan <= 0f || lastSpan <= 0f || span <= 0f) return 0f
        val firstVelocity = (historyPositions[second] - historyPositions[first]) / firstSpan
        val lastVelocity = (historyPositions[last] - historyPositions[penultimate]) / lastSpan
        return (lastVelocity - firstVelocity) / span
    }

    private fun clearHistory() {
        historyCount = 0
        historyHead = 0
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
        set(value) {
            field = value
            physics.squashEnabled = value > 0f
        }

    private val positionState = mutableFloatStateOf(physics.position)
    private val liftXState = mutableFloatStateOf(0f)
    private val liftYState = mutableFloatStateOf(0f)
    private val deviationState = mutableFloatStateOf(0f)
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

    val deviation: Float
        get() = deviationState.floatValue

    val isFlat: Boolean
        get() = flatState.value

    val isLifted: Boolean
        get() = liftedState.value

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

    fun liveSize(rest: Size, lifted: Size): Size {
        val lift = (liftX + liftY) / 2f
        val width = rest.width + (lifted.width - rest.width) * lift
        val height = rest.height + (lifted.height - rest.height) * lift
        return Size(width * (1f + deviation), height * (1f - deviation))
    }

    fun placement(restSize: Size, liftedSize: Size, centerPx: () -> Offset): Modifier = Modifier.layout { measurable, constraints ->
        val width = restSize.width.roundToInt().coerceAtLeast(1)
        val height = restSize.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(x = 0, y = 0) {
                val center = centerPx()
                translationX = center.x - width / 2f
                translationY = center.y - height / 2f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleX = liveSize(restSize, liftedSize).width / width
                scaleY = liveSize(restSize, liftedSize).height / height
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
        deviationState.floatValue = physics.deviation
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
