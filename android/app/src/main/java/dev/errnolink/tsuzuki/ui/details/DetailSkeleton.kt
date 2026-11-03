package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import kotlin.math.PI
import kotlin.math.cos

internal val DetailSkeletonCoverWidth: Dp = 114.dp
internal const val DetailSkeletonCoverAspectRatio: Float = 2f / 3f
internal val DetailSkeletonCoverHeight: Dp = 171.dp
internal const val DetailSkeletonChapterRowCount: Int = 8

/** One full base-to-light-to-base pulse cycle. */
internal const val SkeletonPulsePeriodMs: Int = 1800

/** Shared pulse clock for one skeleton screen, provided via [LocalSkeletonPulse]. */
class SkeletonPulse internal constructor(internal val phase: State<Float>)

val LocalSkeletonPulse = staticCompositionLocalOf<SkeletonPulse?> { null }

/** Normalised placeholder position along the top-left to bottom-right diagonal (0..1). */
internal fun SkeletonPulsePositionOffset(x: Float, y: Float, span: Float): Float =
    if (span > 0f) ((x + y) / span).coerceIn(0f, 1f) else 0f

/** Pulse fraction in 0..1 for a placeholder at [offset] when the shared clock reads [phase]. */
internal fun SkeletonPulseFraction(phase: Float, offset: Float): Float {
    val raw = (phase - offset).mod(1f)
    return (1f - cos(2.0 * PI * raw).toFloat()) / 2f
}

/** Runs [content] with a single pulse clock shared by every ShimmerBox below it. */
@Composable
fun SkeletonPulseClock(content: @Composable () -> Unit) {
    if (TsuzukiTheme.motion.reduced) {
        content()
    } else {
        val phase = rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SkeletonPulsePeriodMs, easing = LinearEasing)),
            label = "skeletonPulsePhase",
        )
        CompositionLocalProvider(LocalSkeletonPulse provides SkeletonPulse(phase)) { content() }
    }
}

internal fun DetailSkeletonHighlight(base: Color, secondary: Color): Color =
    secondary.copy(alpha = 0.2f).compositeOver(base)

internal fun DetailSkeletonChapterTitleFraction(index: Int): Float = when (index % 4) {
    0 -> 0.72f
    1 -> 0.54f
    2 -> 0.65f
    else -> 0.45f
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val base = TsuzukiTheme.colors.elevated
    val highlight = DetailSkeletonHighlight(base = base, secondary = TsuzukiTheme.colors.secondary)
    val reduced = TsuzukiTheme.motion.reduced
    val shared = LocalSkeletonPulse.current?.phase
    val phase: State<Float>? = when {
        reduced -> null
        shared != null -> shared
        else -> rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SkeletonPulsePeriodMs, easing = LinearEasing)),
            label = "skeletonPulsePhase",
        )
    }
    val span = LocalWindowInfo.current.containerSize.let { it.width + it.height }.toFloat()
    val origin = remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { origin.value = it.boundsInWindow().topLeft }
            .drawWithCache {
                onDrawBehind {
                    val clock = phase
                    if (clock == null) {
                        drawRect(base)
                    } else {
                        val position = origin.value
                        val offset = SkeletonPulsePositionOffset(position.x, position.y, span)
                        drawRect(lerp(base, highlight, SkeletonPulseFraction(clock.value, offset)))
                    }
                }
            },
    )
}

@Composable
fun DetailSkeleton(
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
    val lineShape = RoundedCornerShape(4.dp)

    LazyColumn(
        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
        modifier = modifier
            .fillMaxSize()
            .background(TsuzukiTheme.colors.background),
        contentPadding = PaddingValues(top = topPadding, bottom = TsuzukiSpacing.touchTarget),
    ) {
        item(key = "skeleton:hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TsuzukiSpacing.gutter,
                        end = TsuzukiSpacing.gutter,
                        top = TsuzukiSpacing.small,
                    ),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.row),
            ) {
                ShimmerBox(
                    modifier = coverModifier
                        .width(DetailSkeletonCoverWidth)
                        .height(DetailSkeletonCoverHeight),
                    shape = RoundedCornerShape(TsuzukiCorners.cover),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(24.dp),
                        shape = lineShape,
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.60f)
                            .height(20.dp),
                        shape = lineShape,
                    )
                    Spacer(Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(14.dp),
                        shape = lineShape,
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp),
                        shape = lineShape,
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp),
                        shape = lineShape,
                    )
                }
            }
        }

        item(key = "skeleton:actions") {
            Column(
                modifier = Modifier.padding(
                    horizontal = TsuzukiSpacing.gutter,
                    vertical = TsuzukiSpacing.row,
                ),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(TsuzukiCorners.field),
                )
            }
        }

        item(key = "skeleton:description") {
            Column(
                modifier = Modifier.padding(
                    horizontal = TsuzukiSpacing.gutter,
                    vertical = TsuzukiSpacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.98f)
                        .height(14.dp),
                    shape = lineShape,
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp),
                    shape = lineShape,
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp),
                    shape = lineShape,
                )
            }
        }

        item(key = "skeleton:chapters-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TsuzukiSpacing.gutter,
                        end = TsuzukiSpacing.row,
                        top = TsuzukiSpacing.gutter,
                        bottom = 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.tiny),
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(100.dp)
                            .height(20.dp),
                        shape = lineShape,
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .width(72.dp)
                            .height(12.dp),
                        shape = lineShape,
                    )
                }
            }
        }

        items(
            count = DetailSkeletonChapterRowCount,
            key = { "skeleton:chapter:$it" },
        ) { index ->
            DetailChapterRowSkeleton(index)
        }
    }
}

@Composable
fun DetailChapterRowSkeleton(index: Int, modifier: Modifier = Modifier) {
    val lineShape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TsuzukiTheme.colors.grouped),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TsuzukiSpacing.row,
                    end = TsuzukiSpacing.row,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(DetailSkeletonChapterTitleFraction(index))
                        .height(16.dp),
                    shape = lineShape,
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(12.dp),
                    shape = lineShape,
                )
            }
            Spacer(Modifier.width(TsuzukiSpacing.medium))
            ShimmerBox(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
            )
        }
        Spacer(
            modifier = Modifier
                .padding(start = TsuzukiSpacing.row)
                .fillMaxWidth()
                .height(TsuzukiSpacing.hairline)
                .background(TsuzukiTheme.colors.separator),
        )
    }
}
