package dev.errnolink.tsuzuki.designsystem

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TsuzukiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalTsuzukiContentColor.current,
) {
    Icon(imageVector, contentDescription, modifier = modifier, tint = tint)
}
