package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Stable
class CoverArrivalState internal constructor() {
    private val arrived = mutableSetOf<Any>()
    internal fun contains(key: Any) = key in arrived
    internal fun mark(key: Any) = arrived.add(key)
}
val coverRatioMutationCount = AtomicInteger(0)


@Composable
fun rememberCoverArrivalState(): CoverArrivalState = remember { CoverArrivalState() }

@Composable
fun CoverImage(
    imageKey: Any,
    aspectRatio: Float,
    loaded: Boolean,
    arrivals: CoverArrivalState,
    modifier: Modifier = Modifier,
    cached: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    require(aspectRatio.isFinite() && aspectRatio > 0f)
    val motion = TsuzukiTheme.motion
    val alpha = remember(imageKey) { Animatable(if (loaded && (cached || arrivals.contains(imageKey))) 1f else 0f) }
    val lastRatio = remember(imageKey) { floatArrayOf(aspectRatio) }
    SideEffect {
        if (lastRatio[0] != aspectRatio) {
            lastRatio[0] = aspectRatio
            coverRatioMutationCount.incrementAndGet()
        }
    }
    LaunchedEffect(imageKey, loaded, cached, motion.reduced) {
        if (loaded) {
            val first = arrivals.mark(imageKey)
            if (first && !cached && !motion.reduced) alpha.animateTo(1f, tween(motion.coverArrivalMillis)) else alpha.snapTo(1f)
        } else {
            alpha.snapTo(0f)
        }
    }
    val source = remember(imageKey) { MutableInteractionSource() }
    val press = remember(motion) { PressScaleIndication(motion, cover = true) }
    Box(
        modifier.aspectRatio(aspectRatio).clip(RoundedCornerShape(TsuzukiCorners.cover))
            .background(TsuzukiTheme.colors.grouped)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                    interactionSource = source, indication = null, role = Role.Button,
                    onClick = { onClick?.invoke() }, onLongClick = onLongClick,
                ) else Modifier,
            ),
    ) {
        Box(Modifier.matchParentSize().indication(source, press).graphicsLayer { this.alpha = alpha.value }, content = content)
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier) {
    val colors = TsuzukiTheme.colors
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val indeterminate = progress <= 0f
    val phase = if (indeterminate && !TsuzukiTheme.motion.reduced) {
        rememberInfiniteTransition(label = "Activity").animateFloat(
            initialValue = 0f, targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "Activity ticks",
        )
    } else {
        rememberUpdatedState(0f)
    }
    Canvas(
        modifier.size(28.dp).semantics {
            contentDescription = label
            progressBarRangeInfo = if (indeterminate) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(fraction, 0f..1f)
        },
    ) {
        if (indeterminate) {
            val step = phase.value.toInt()
            repeat(12) { index ->
                rotate(index * 30f) {
                    drawLine(
                        colors.secondary.copy(alpha = 0.18f + 0.82f * ((index + step) % 12) / 11f),
                        Offset(size.width / 2, size.height * 0.1f),
                        Offset(size.width / 2, size.height * 0.27f),
                        2.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        } else {
            val width = 3.dp.toPx()
            val inset = width / 2
            val ringSize = Size(size.width - width, size.height - width)
            drawArc(colors.separator, -90f, 360f, false, Offset(inset, inset), ringSize, style = Stroke(width))
            drawArc(colors.accent, -90f, 360f * fraction, false, Offset(inset, inset), ringSize, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

enum class NoticeTone { Neutral, Success, Error }

@Composable
fun Banner(
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    require((actionLabel == null) == (onAction == null))
    val colors = TsuzukiTheme.colors
    val foreground = when (tone) {
        NoticeTone.Neutral -> colors.text
        NoticeTone.Success -> colors.success
        NoticeTone.Error -> colors.destructive
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.field)).background(colors.grouped)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TsuzukiText(message, Modifier.weight(1f), TsuzukiTheme.typography.callout, foreground)
            onDismiss?.let { IconButton("Dismiss notice", it) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(16.dp)) } }
        }
        if (actionLabel != null) PillButton(actionLabel, onAction!!, prominent = false)
    }
}

@Composable
fun Toast(message: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Success, onDismiss: (() -> Unit)? = null) {
    Banner(message, modifier, tone = tone, onDismiss = onDismiss)
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
    secondaryActions: List<EmptyTextAction> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        icon()
        TsuzukiText(title, style = TsuzukiTheme.typography.title2)
        if (description.isNotEmpty()) {
            TsuzukiText(description, style = TsuzukiTheme.typography.callout, color = TsuzukiTheme.colors.secondary)
        }
        val colors = TsuzukiTheme.colors
        if (action != null || secondaryActions.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                action?.invoke()
                if (secondaryActions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        secondaryActions.fastForEach { item ->
                            val interaction = remember { MutableInteractionSource() }
                            TsuzukiText(
                                item.label,
                                Modifier
                                    .pressScale(interaction)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = item.onClick,
                                    )
                                    .padding(vertical = 6.dp),
                                TsuzukiTheme.typography.footnote,
                                colors.tabTint,
                            )
                        }
                    }
                }
            }
        }
    }
}

data class EmptyTextAction(val label: String, val onClick: () -> Unit)
