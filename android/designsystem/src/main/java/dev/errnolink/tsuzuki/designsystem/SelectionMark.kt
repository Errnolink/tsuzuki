package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(colors.accent)
                } else {
                    Modifier.border(1.5.dp, colors.secondary.copy(alpha = 0.6f), CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            TsuzukiIcon(TsuzukiIcons.Check, null, Modifier.size(size * 0.65f), tint = colors.onAccent)
        }
    }
}

fun Modifier.selectionFill(selected: Boolean): Modifier = composed {
    if (!selected) {
        Modifier
    } else {
        val colors = TsuzukiTheme.colors
        background(colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
    }
}
