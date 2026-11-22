package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CoverShapeSpec(val radius: Dp)

val LocalCoverShape = staticCompositionLocalOf { CoverShapeSpec(12.dp) }

@Composable
fun coverShape(): Shape {
    val spec = LocalCoverShape.current
    return RoundedCornerShape(spec.radius)
}
