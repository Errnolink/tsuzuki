package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun tsuzukiRounded(radius: Dp): Shape {
    return RoundedCornerShape(radius)
}

@Composable
fun tsuzukiRounded(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape {
    return RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}
