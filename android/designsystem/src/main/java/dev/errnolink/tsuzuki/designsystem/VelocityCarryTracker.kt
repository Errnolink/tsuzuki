package dev.errnolink.tsuzuki.designsystem

import kotlin.math.abs
import kotlin.math.sign

class VelocityCarryTracker(private val freshWindowMs: Float = 60f) {
    private var gestureActive = false
    private var startPosition = 0f
    private var lastPosition = 0f
    private var lastTimestamp = 0L
    private var gestureStartTimestamp = 0L
    private var smoothedVelocity = 0f
    private var hasSample = false

    fun onStart(position: Float) {
        gestureActive = true
        startPosition = position
        lastPosition = position
        lastTimestamp = System.nanoTime()
        gestureStartTimestamp = lastTimestamp
        smoothedVelocity = 0f
        hasSample = false
    }

    fun onMove(position: Float) {
        val now = System.nanoTime()
        if (!gestureActive) {
            gestureActive = true
            startPosition = position
            gestureStartTimestamp = now
            smoothedVelocity = 0f
            hasSample = false
        }
        val elapsedMs = (now - lastTimestamp) / 1_000_000.0
        if (elapsedMs > 0.0 && hasSample) {
            val instantaneous = (position - lastPosition) / (elapsedMs / 1_000.0)
            smoothedVelocity = SampleKeep * smoothedVelocity + SampleBlend * instantaneous.toFloat()
        }
        lastPosition = position
        lastTimestamp = now
        hasSample = true
    }

    fun recentSampledVelocity(): Float {
        val idleMs = (System.nanoTime() - lastTimestamp) / 1_000_000.0
        if (idleMs > freshWindowMs) return 0f
        return smoothedVelocity
    }

    fun resolveReleaseVelocity(trackedFingerVelocity: Float, currentPosition: Float): Float {
        val durationMs = (System.nanoTime() - gestureStartTimestamp) / 1_000_000.0
        val average = if (durationMs > 0.0 && gestureActive) {
            ((currentPosition - startPosition) / (durationMs / 1_000.0)).toFloat()
        } else {
            0f
        }
        val sampled = recentSampledVelocity()
        val winner = if (abs(sampled) >= abs(trackedFingerVelocity)) sampled else trackedFingerVelocity
        return when {
            abs(average) > abs(winner) -> average
            winner != 0f -> winner
            else -> average
        }
    }

    fun reset() {
        gestureActive = false
        startPosition = 0f
        lastPosition = 0f
        lastTimestamp = 0L
        gestureStartTimestamp = 0L
        smoothedVelocity = 0f
        hasSample = false
    }

    private companion object {
        const val SampleKeep = 0.3f
        const val SampleBlend = 0.7f
    }
}
