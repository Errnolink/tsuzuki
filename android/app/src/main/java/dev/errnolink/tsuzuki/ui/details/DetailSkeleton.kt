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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import dev.errnolink.tsuzuki.designsystem.Skeleton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import dev.errnolink.tsuzuki.designsystem.coverShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.cos
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.api.get

internal const val DetailSkeletonChapterRowCount: Int = 8

internal fun DetailSkeletonChapterTitleFraction(index: Int): Float = when (index % 4) {
    0 -> 0.72f
    1 -> 0.54f
    2 -> 0.65f
    else -> 0.45f
}

@Composable
fun MangaDetailSkeleton(mangaId: Long, coverModifier: Modifier = Modifier) {
    val preferences = remember { uy.kohesive.injekt.Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>() }
    val header by preferences.detailsHeader().collectAsState()
    val ratio = remember(mangaId) { detailsCoverRatio(knownCoverRatio(mangaId)) }
    val twoPane = LocalWindowWidthClass.current == WindowWidthClass.Expanded
    DetailsCardTheme(mangaId) {
        DetailSkeleton(
            centered = twoPane || header == dev.errnolink.tsuzuki.preference.DetailsHeader.CENTERED,
            twoPane = twoPane,
            coverRatio = ratio,
            coverModifier = coverModifier,
            coverImage = remember(mangaId) { dev.errnolink.tsuzuki.ui.motion.MangaHero.source(mangaId)?.image },
        )
    }
}

@Composable
fun DetailSkeleton(
    centered: Boolean,
    coverRatio: Float,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    coverImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    twoPane: Boolean = false,
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar.bodyHeightDp.dp
    val lineShape = RoundedCornerShape(4.dp)
    var taps by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(taps) {
        if (taps > 0) {
            hintVisible = true
            kotlinx.coroutines.delay(LoadingHintMillis)
            hintVisible = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { taps++ } },
    ) {
    if (twoPane) {
        DetailSkeletonTwoPane(
            coverRatio = coverRatio,
            coverModifier = coverModifier,
            coverImage = coverImage,
            topPadding = topPadding,
        )
    } else {
    LazyColumn(
        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
        modifier = Modifier
            .fillMaxSize()
            .background(TsuzukiTheme.colors.background),
        contentPadding = PaddingValues(top = topPadding, bottom = TsuzukiSpacing.touchTarget),
    ) {
        item(key = "skeleton:hero") {
            if (centered) {
                SkeletonCenteredHeader(coverRatio, coverModifier, coverImage)
                return@item
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailsCoverSlot(centered = false, ratio = coverRatio) { slot ->
                    SkeletonCover(coverImage, coverModifier.then(slot))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Skeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(24.dp),
                        shape = lineShape,
                    )
                    Skeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.60f)
                            .height(20.dp),
                        shape = lineShape,
                    )
                    Spacer(Modifier.height(2.dp))
                    Skeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(14.dp),
                        shape = lineShape,
                    )
                    Skeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp),
                        shape = lineShape,
                    )
                    Skeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp),
                        shape = lineShape,
                    )
                }
            }
        }

        item(key = "skeleton:actions") {
            SkeletonActionsBlock()
        }

        item(key = "skeleton:description") {
            SkeletonDescriptionBlock()
        }

        item(key = "skeleton:chapters-header") {
            SkeletonChaptersHeaderBlock()
        }

        items(
            count = DetailSkeletonChapterRowCount,
            key = { "skeleton:chapter:$it" },
        ) { index ->
            DetailChapterRowSkeleton(index)
        }
    }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = hintVisible,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        enter = androidx.compose.animation.fadeIn(
            if (TsuzukiTheme.motion.reduced) {
                dev.errnolink.tsuzuki.designsystem.M3Motion.reducedFade()
            } else {
                androidx.compose.animation.core.tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3)
            },
        ) +
            if (TsuzukiTheme.motion.reduced) {
                androidx.compose.animation.EnterTransition.None
            } else {
                androidx.compose.animation.scaleIn(
                    androidx.compose.animation.core.tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3),
                    initialScale = 0.92f,
                )
            },
        exit = androidx.compose.animation.fadeOut(
            if (TsuzukiTheme.motion.reduced) {
                dev.errnolink.tsuzuki.designsystem.M3Motion.reducedFade()
            } else {
                androidx.compose.animation.core.tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3)
            },
        ),
    ) {
        dev.errnolink.tsuzuki.designsystem.GlassSurface(context = null, shape = CircleShape) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dev.errnolink.tsuzuki.designsystem.ProgressMark(0f, stringResource(MR.strings.tsuzuki_loading), Modifier.size(16.dp))
                dev.errnolink.tsuzuki.designsystem.TsuzukiText(
                    stringResource(MR.strings.tsuzuki_still_loading_title),
                    style = TsuzukiTheme.typography.footnote,
                    color = TsuzukiTheme.colors.text,
                )
            }
        }
    }
    }
}

@Composable
private fun SkeletonCenteredHeader(
    coverRatio: Float,
    coverModifier: Modifier,
    coverImage: androidx.compose.ui.graphics.ImageBitmap?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
    ) {
        DetailsCoverSlot(centered = true, ratio = coverRatio) { slot ->
            SkeletonCover(coverImage, coverModifier.then(slot))
        }
        Skeleton(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp), shape = RoundedCornerShape(4.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp), shape = RoundedCornerShape(4.dp))
        // DET-01: the centered header hosts the same action row as the classic one — its
        // skeleton mirrors that block, not a lone capsule.
        SkeletonActionsBlock()
    }
}

@Composable
private fun SkeletonActionsBlock() {
    Column(
        modifier = Modifier.padding(
            horizontal = TsuzukiSpacing.gutter,
            vertical = TsuzukiSpacing.row,
        ),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        // DET-01: the skeleton mirrors the real action row's geometry — the continue capsule,
        // then the circle row with its labels — so the loaded page does not jump ~20dp.
        Skeleton(
            modifier = Modifier
                .fillMaxWidth()
                .height(CapsuleHeight),
            shape = RoundedCornerShape(CapsuleHeight / 2),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ActionGap),
        ) {
            repeat(5) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Skeleton(
                        modifier = Modifier.size(CircleSize),
                        shape = RoundedCornerShape(CircleSize / 2),
                    )
                    Skeleton(
                        modifier = Modifier.size(width = 40.dp, height = 14.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonDescriptionBlock() {
    Column(
        modifier = Modifier.padding(
            horizontal = TsuzukiSpacing.gutter,
            vertical = TsuzukiSpacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
    ) {
        Skeleton(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Skeleton(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Skeleton(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Composable
private fun SkeletonChaptersHeaderBlock() {
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
            Skeleton(
                modifier = Modifier
                    .width(100.dp)
                    .height(20.dp),
                shape = RoundedCornerShape(4.dp),
            )
            Skeleton(
                modifier = Modifier
                    .width(72.dp)
                    .height(12.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}

@Composable
private fun DetailSkeletonTwoPane(
    coverRatio: Float,
    coverModifier: Modifier,
    coverImage: androidx.compose.ui.graphics.ImageBitmap?,
    topPadding: Dp,
) {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            modifier = Modifier.width(DetailsStartPaneWidth).background(TsuzukiTheme.colors.background),
            contentPadding = PaddingValues(top = topPadding, bottom = TsuzukiSpacing.touchTarget),
        ) {
            item(key = "skeleton:hero") {
                SkeletonCenteredHeader(coverRatio, coverModifier, coverImage)
            }
            item(key = "skeleton:actions") {
                SkeletonActionsBlock()
            }
            item(key = "skeleton:description") {
                SkeletonDescriptionBlock()
            }
        }
        Spacer(Modifier.width(DetailsPaneGap))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                modifier = Modifier.fillMaxSize().widthIn(max = DetailsEndPaneMaxWidth).background(TsuzukiTheme.colors.background),
                contentPadding = PaddingValues(top = topPadding, bottom = TsuzukiSpacing.touchTarget),
            ) {
                item(key = "skeleton:chapters-header") {
                    SkeletonChaptersHeaderBlock()
                }
                items(
                    count = DetailSkeletonChapterRowCount,
                    key = { "skeleton:chapter:$it" },
                ) { index ->
                    DetailChapterRowSkeleton(index)
                }
            }
        }
    }
}

private const val LoadingHintMillis = 1600L

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
                Skeleton(
                    modifier = Modifier
                        .fillMaxWidth(DetailSkeletonChapterTitleFraction(index))
                        .height(16.dp),
                    shape = lineShape,
                )
                Skeleton(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(12.dp),
                    shape = lineShape,
                )
            }
            Spacer(Modifier.width(TsuzukiSpacing.medium))
            Skeleton(
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

@Composable
private fun SkeletonCover(image: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier) {
    if (image == null) {
        Skeleton(modifier = modifier, shape = coverShape())
    } else {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.clip(coverShape()),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}
