package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ContinueButtonSize(val target: Dp, val disc: Dp, val ring: Dp, val stroke: Dp, val icon: Dp) {
    Regular(target = 48.dp, disc = 36.dp, ring = 44.dp, stroke = 2.5.dp, icon = 20.dp),
    Compact(target = 40.dp, disc = 30.dp, ring = 38.dp, stroke = 2.dp, icon = 16.dp),
}

@Composable
fun ContinueButton(
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ContinueButtonSize = ContinueButtonSize.Regular,
    onImage: Boolean = false,
    contentDescription: String,
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val animatedFraction by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = if (reduced) snap() else tween(M3Motion.Medium2, easing = M3Motion.Standard),
        label = "ContinueButtonProgress",
    )
    val description = if (progress != null) {
        "${contentDescription}, ${(progress.coerceIn(0f, 1f) * 100).toInt()} percent read"
    } else {
        contentDescription
    }
    Box(
        modifier
            .size(size.target)
            .semantics { this.contentDescription = description }
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        if (onImage) {
            Box(Modifier.size(size.ring).background(Color.Black.copy(alpha = 0.45f), CircleShape))
        }
        if (progress != null) {
            val dims = size
            Canvas(Modifier.size(dims.ring)) {
                val strokePx = dims.stroke.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = if (onImage) Color.White.copy(alpha = 0.28f) else colors.separator,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx),
                )
                drawArc(
                    color = if (onImage) Color.White else colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(size.disc)
                .background(if (onImage) Color.White else colors.text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiIcon(
                TsuzukiIcons.Play,
                null,
                Modifier.size(size.icon),
                tint = if (onImage) Color.Black else colors.background,
            )
        }
    }
}
