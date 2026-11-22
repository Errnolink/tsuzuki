package dev.errnolink.tsuzuki.ui.motion

import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.M3Motion

object StackSpec {
    const val UnderlayShift = 0.25f
    const val GestureTravel = 0.5f
    val LeadingCorner = 28.dp
    val Elevation = 16.dp
    val ScrimAlpha = M3Motion.ContainerScrimAlpha
}

data class StackPose(val frontX: Float, val backX: Float, val scrim: Float)

fun stackPose(progress: Float, widthPx: Float, rtl: Boolean): StackPose {
    val dir = if (rtl) -1f else 1f
    return StackPose(
        frontX = dir * widthPx * (1f - progress),
        backX = -dir * StackSpec.UnderlayShift * widthPx * progress,
        scrim = StackSpec.ScrimAlpha * progress,
    )
}

fun stackGestureProgress(gestureBase: Float, easedGesture: Float): Float =
    gestureBase * (1f - StackSpec.GestureTravel * easedGesture)
