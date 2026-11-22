package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import java.util.concurrent.atomic.AtomicInteger
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        modifier.aspectRatio(aspectRatio).clip(coverShape())
            // N19: a visible surface tint while the cover decodes — `grouped` matched the page
            // background in dark mode and read as an empty slot.
            .background(TsuzukiTheme.colors.elevated)
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
    val labels = LocalTsuzukiComponentLabels.current
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
            onDismiss?.let { PlainIconButton(labels.dismissNotice, it) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(16.dp)) } }
        }
        if (actionLabel != null) PillButton(actionLabel, onAction!!, tinted = true)
    }
}

/**
 * Empty state (Kasane `empty-state`): a seal with a slowly turning cookie behind it, one sentence
 * about what will appear here and why, and one action. On appear the seal turns in and the title,
 * body and action rise in sequence; reduce motion shows everything at once.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
    secondaryActions: List<EmptyTextAction> = emptyList(),
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            appear.animateTo(1f, spring(dampingRatio = KasaneSprings.NavDamping, stiffness = KasaneSprings.NavStiffness))
        } else {
            appear.snapTo(1f)
        }
    }
    // The cookie behind the seal turns once every 48 s (Kasane k-empty-turn); reduced motion stops
    // it, and then no clock runs at all.
    val cookieAngle: androidx.compose.runtime.State<Float>? = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "empty cookie").animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
            label = "cookie angle",
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    // The seal turns in: slightly small and rotated, settling onto the spring.
                    scaleX = 0.7f + 0.3f * EmptyStateStagger.appear(appear.value, 0f, 0.5f)
                    scaleY = scaleX
                    rotationZ = -24f * (1f - EmptyStateStagger.appear(appear.value, 0f, 0.5f))
                    alpha = EmptyStateStagger.appear(appear.value, 0f, 0.4f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(124.dp)
                    .graphicsLayer { rotationZ = 14f + (cookieAngle?.value ?: 0f) }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(colors.selectedFill),
            )
            Box(
                Modifier
                    .size(112.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(colors.accent.copy(alpha = if (colors.isDark) 0.28f else 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(TsuzukiSize.control)) { icon() }
            }
        }
        TsuzukiText(
            title,
            style = TsuzukiTheme.typography.title2,
            modifier = Modifier.graphicsLayer {
                alpha = EmptyStateStagger.appear(appear.value, 0.2f, 0.55f)
                translationY = 10.dp.toPx() * (1f - EmptyStateStagger.appear(appear.value, 0.2f, 0.55f))
            },
        )
        if (description.isNotEmpty()) {
            TsuzukiText(
                description,
                style = TsuzukiTheme.typography.callout,
                color = colors.secondary,
                modifier = Modifier.graphicsLayer {
                    alpha = EmptyStateStagger.appear(appear.value, 0.35f, 0.7f)
                    translationY = 10.dp.toPx() * (1f - EmptyStateStagger.appear(appear.value, 0.35f, 0.7f))
                },
            )
        }
        if (action != null || secondaryActions.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer {
                        alpha = EmptyStateStagger.appear(appear.value, 0.5f, 0.85f)
                        translationY = 10.dp.toPx() * (1f - EmptyStateStagger.appear(appear.value, 0.5f, 0.85f))
                    },
            ) {
                action?.invoke()
                if (secondaryActions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        secondaryActions.fastForEach { item ->
                            val interaction = remember { MutableInteractionSource() }
                            val textPressState = rememberPressShapeState(interaction, restRadius = 0.dp)
                            TsuzukiText(
                                item.label,
                                Modifier
                                    .then(
                                        if (textPressState.enabled) {
                                            Modifier.pressShapeScale(textPressState)
                                        } else {
                                            Modifier.pressScale(interaction)
                                        },
                                    )
                                    .heightIn(min = 48.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = item.onClick,
                                    )
                                    .padding(horizontal = 12.dp),
                                TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stagger windows of the empty-state entrance; pure so the sequence stays testable. */
object EmptyStateStagger {
    /** Local 0..1 progress of a stage between [start] and [end] of the master progress. */
    fun appear(progress: Float, start: Float, end: Float): Float =
        ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

data class EmptyTextAction(val label: String, val onClick: () -> Unit)
