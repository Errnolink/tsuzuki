package dev.errnolink.tsuzuki.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.ChevronLeft
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.GlassBackdropScrim
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private enum class OverviewFilter {
    All,
    Current,
}

/**
 * Full-screen blurred glass Page Overview grid (`LazyVerticalGrid`) with `All`/`Current` filter
 * pills, 2/3/4 column density toggle, tap-to-jump cards, and Prev/Next chapter footer pills.
 */
@Composable
internal fun ReaderOverviewOverlay(
    visible: Boolean,
    glass: GlassContext?,
    chapterTitle: String?,
    currentPage: Int,
    totalPages: Int,
    isRtl: Boolean,
    prevChapterShortName: String?,
    nextChapterShortName: String?,
    enabledPrevious: Boolean,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    pageProvider: (Int) -> ReaderPage?,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)

    val colors = TsuzukiTheme.colors
    val context = LocalContext.current
    var filter by remember { mutableStateOf(OverviewFilter.All) }
    var columns by remember { mutableIntStateOf(3) }
    val thumbs = remember { ScrubThumbCache(limit = 64) }

    val pageIndices = remember(totalPages, currentPage, filter) {
        if (totalPages <= 0) {
            emptyList()
        } else {
            when (filter) {
                OverviewFilter.All -> (0 until totalPages).toList()
                OverviewFilter.Current -> {
                    val startIdx = (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                    (startIdx until totalPages).toList()
                }
            }
        }
    }

    val reduced = TsuzukiTheme.motion.reduced
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            fadeIn()
        } else {
            fadeIn(tween(220)) + scaleIn(
                initialScale = 0.93f,
                animationSpec = KasaneSprings.nav(reduced),
                transformOrigin = TransformOrigin(0.5f, 1f),
            )
        },
        exit = if (reduced) {
            fadeOut()
        } else {
            fadeOut(tween(180)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = KasaneSprings.fastSpatial(reduced),
                transformOrigin = TransformOrigin(0.5f, 1f),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackdropScrim(glass, Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
            ) {
                // Header: All / Current SegmentedControl + Done button; chapter title + 2x/3x/4x SegmentedControl
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SegmentedControl(
                            options = listOf(
                                stringResource(MR.strings.tsuzuki_reader_all_count, totalPages.coerceAtLeast(0)),
                                stringResource(MR.strings.tsuzuki_reader_current),
                            ),
                            selectedIndex = if (filter == OverviewFilter.All) 0 else 1,
                            onSelect = { idx ->
                                filter = if (idx == 0) OverviewFilter.All else OverviewFilter.Current
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TsuzukiButton(
                            label = stringResource(MR.strings.tsuzuki_done),
                            onClick = onDismiss,
                            size = TsuzukiButtonSize.Sm,
                            variant = TsuzukiButtonVariant.Filled,
                            minWidth = 0.dp,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TsuzukiText(
                            chapterTitle.orEmpty(),
                            style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val densityChoices = listOf(2, 3, 4)
                        SegmentedControl(
                            options = densityChoices.map { stringResource(MR.strings.tsuzuki_reader_columns_n, it) },
                            selectedIndex = densityChoices.indexOf(columns).coerceAtLeast(1),
                            onSelect = { idx -> columns = densityChoices[idx] },
                            modifier = Modifier.width(172.dp),
                        )
                    }
                }

                // Grid of chapter pages
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(pageIndices, key = { it }) { pageIdx ->
                        val page = pageProvider(pageIdx)
                        val ready = page?.statusFlow?.collectAsState()?.value
                        var thumb by remember(pageIdx, page, ready) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(pageIdx, page, ready) {
                            thumb = thumbs.peek(pageIdx + 1) ?: thumbs.load(pageIdx + 1) {
                                decodePageThumb(context, page, targetMaxPx = 420, cropTallTop = true)
                            }
                        }
                        val isCurrent = (pageIdx + 1) == currentPage
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4.3f)
                                .clip(shape)
                                .background(colors.elevated.copy(alpha = 0.72f))
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) colors.accent else colors.separator.copy(alpha = 0.65f),
                                    shape = shape,
                                )
                                .clickable(role = Role.Button) {
                                    onSelectPage(pageIdx)
                                    onDismiss()
                                },
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb!!,
                                    contentDescription = stringResource(MR.strings.tsuzuki_page_n, pageIdx + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    TsuzukiText(
                                        "${pageIdx + 1}",
                                        style = TsuzukiTheme.typography.headline.copy(fontFeatureSettings = "tnum"),
                                        color = colors.secondary,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isCurrent) colors.accent else Color.Black.copy(alpha = 0.78f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                TsuzukiText(
                                    "${pageIdx + 1}",
                                    style = TsuzukiTheme.typography.caption2.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = if (isCurrent) colors.onAccent else Color.White,
                                )
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.size(8.dp))
                    }
                }

                // Bottom Prev / Next chapter navigation buttons (Kasane TsuzukiButton)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val leftArrow = resolveLeftArrow(
                        isRtl = isRtl,
                        enabledPrevious = enabledPrevious,
                        enabledNext = enabledNext,
                        previousLabel = prevChapterShortName ?: stringResource(MR.strings.action_previous_chapter),
                        nextLabel = nextChapterShortName ?: stringResource(MR.strings.action_next_chapter),
                    )
                    val rightArrow = resolveRightArrow(
                        isRtl = isRtl,
                        enabledPrevious = enabledPrevious,
                        enabledNext = enabledNext,
                        previousLabel = prevChapterShortName ?: stringResource(MR.strings.action_previous_chapter),
                        nextLabel = nextChapterShortName ?: stringResource(MR.strings.action_next_chapter),
                    )
                    TsuzukiButton(
                        label = leftArrow.contentDescription,
                        onClick = {
                            if (leftArrow.isNext) onNextChapter() else onPreviousChapter()
                            onDismiss()
                        },
                        enabled = leftArrow.enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Tonal,
                        icon = { Icon(TsuzukiIcons.ChevronLeft, null, Modifier.size(16.dp)) },
                        fillWidth = true,
                        minWidth = 0.dp,
                        modifier = Modifier.weight(1f),
                    )
                    TsuzukiButton(
                        label = rightArrow.contentDescription,
                        onClick = {
                            if (rightArrow.isNext) onNextChapter() else onPreviousChapter()
                            onDismiss()
                        },
                        enabled = rightArrow.enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Tonal,
                        trailingIcon = { Icon(TsuzukiIcons.ChevronRight, null, Modifier.size(16.dp)) },
                        fillWidth = true,
                        minWidth = 0.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
