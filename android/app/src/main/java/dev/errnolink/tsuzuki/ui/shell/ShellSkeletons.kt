package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.details.ShimmerBox
import dev.errnolink.tsuzuki.ui.details.SkeletonPulseClock
import tachiyomi.domain.library.model.LibraryDisplayMode

private val skeletonLineFractions = floatArrayOf(0.85f, 0.62f, 0.74f, 0.5f)

private fun skeletonLineFraction(index: Int): Float = skeletonLineFractions[index % skeletonLineFractions.size]

/**
 * Loading-to-content swap: the skeleton stays composed below while [content] fades in on top of it
 * (Short4, StandardDecelerate; reducedFade under reduced motion). The skeleton is removed only
 * once the content is opaque. Both slots fill the same box, so nothing shifts.
 */
@Composable
fun SkeletonCrossfade(
    loading: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val reduced = TsuzukiTheme.motion.reduced
    var showContent by remember { mutableStateOf(!loading) }
    var showSkeleton by remember { mutableStateOf(loading) }
    val contentAlpha = remember { Animatable(if (showContent && !showSkeleton) 1f else 0f) }

    if (!loading && !showContent) {
        SideEffect { showContent = true }
    }

    LaunchedEffect(showContent, reduced) {
        if (!showContent || contentAlpha.value >= 1f) return@LaunchedEffect
        val spec = if (reduced) {
            M3Motion.reducedFade<Float>()
        } else {
            tween<Float>(M3Motion.Short4, easing = M3Motion.StandardDecelerate)
        }
        contentAlpha.animateTo(1f, spec)
        showSkeleton = false
    }

    Box(modifier) {
        if (showSkeleton) {
            SkeletonPulseClock {
                Box(Modifier.fillMaxSize()) { skeleton() }
            }
        }
        if (showContent) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha.value },
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SkeletonLine(widthFraction: Float, lineHeight: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier.fillMaxWidth(widthFraction).height(lineHeight),
        shape = RoundedCornerShape(4.dp),
    )
}

@Composable
private fun SkeletonCover(width: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier.width(width).aspectRatio(2f / 3f),
        shape = RoundedCornerShape(TsuzukiCorners.cover),
    )
}

@Composable
private fun SkeletonGroupRow(
    shape: CornerBasedShape,
    dividerInset: Dp,
    divider: Boolean,
    verticalPadding: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(shape)
            .background(TsuzukiTheme.colors.grouped)
            .then(if (divider) Modifier.insetDivider(dividerInset) else Modifier)
            .padding(horizontal = 12.dp, vertical = verticalPadding)
            .heightIn(min = TsuzukiTheme.spacing.touchTarget),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun SourceRowSkeleton(
    shape: CornerBasedShape,
    divider: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
) {
    SkeletonGroupRow(shape, 56.dp, divider, 8.dp, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(32.dp), shape = RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(skeletonLineFraction(index), 14.dp)
                SkeletonLine(0.4f, 11.dp)
            }
        }
    }
}

@Composable
fun HistoryRowSkeleton(
    shape: CornerBasedShape,
    divider: Boolean,
    modifier: Modifier = Modifier,
) {
    SkeletonGroupRow(shape, 72.dp, divider, 10.dp, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            SkeletonCover(48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(0.82f, 15.dp)
                SkeletonLine(0.45f, 11.dp)
            }
            ShimmerBox(
                Modifier.width(64.dp).height(28.dp),
                shape = RoundedCornerShape(50),
            )
        }
    }
}

@Composable
fun UpdateRowSkeleton(
    shape: CornerBasedShape,
    divider: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
) {
    SkeletonGroupRow(shape, 64.dp, divider, 8.dp, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonCover(40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(0.55f, 15.dp)
                SkeletonLine(skeletonLineFraction(index), 12.dp)
            }
            ShimmerBox(Modifier.size(24.dp), shape = CircleShape)
        }
    }
}

@Composable
fun DownloadRowSkeleton(
    shape: CornerBasedShape,
    divider: Boolean,
    modifier: Modifier = Modifier,
) {
    SkeletonGroupRow(shape, 56.dp, divider, 12.dp, modifier, horizontalPadding = TsuzukiTheme.spacing.gutter) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(Modifier.size(24.dp), shape = RoundedCornerShape(6.dp))
            SkeletonCover(40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(0.78f, 13.dp)
                SkeletonLine(0.58f, 11.dp)
                SkeletonLine(0.35f, 10.dp)
            }
        }
    }
}

@Composable
fun MangaCellSkeleton(
    displayMode: LibraryDisplayMode,
    index: Int,
    modifier: Modifier = Modifier,
) {
    when (displayMode) {
        LibraryDisplayMode.List -> Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TsuzukiTheme.colors.grouped)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCover(48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(skeletonLineFraction(index), 14.dp)
                SkeletonLine(0.5f, 11.dp)
            }
        }
        LibraryDisplayMode.CoverOnlyGrid -> ShimmerBox(
            modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = RoundedCornerShape(TsuzukiCorners.cover),
        )
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ShimmerBox(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                shape = RoundedCornerShape(TsuzukiCorners.cover),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonLine(skeletonLineFraction(index), 12.dp)
                SkeletonLine(0.55f, 12.dp)
            }
        }
    }
}

@Composable
fun FeedSectionSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(0.6f, 17.dp)
                SkeletonLine(0.4f, 11.dp)
            }
            ShimmerBox(Modifier.size(22.dp), shape = CircleShape)
        }
        CoverRowSkeleton()
    }
}

@Composable
fun CoverRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { card ->
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(TsuzukiCorners.cover),
                )
                SkeletonLine(if (card == 0) 0.9f else 0.65f, 12.dp)
            }
        }
    }
}

@Composable
fun SkeletonCaption(start: Dp, widthFraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = start, top = 16.dp, bottom = 8.dp)) {
        SkeletonLine(widthFraction, 11.dp)
    }
}

@Composable
fun SourceAppendSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        ShimmerBox(
            Modifier.width(160.dp).height(24.dp),
            shape = RoundedCornerShape(50),
        )
    }
}
