package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Live scroll position and visible viewport span emitted by [ComposeWebtoonViewer]. */
data class WebtoonProgressState(
    val progress: Float = 0f,
    val viewportFraction: Float = 0.22f,
)

/** Compact centered pull-up/collapse handle pill for the reader filmstrip tray. */
@Composable
internal fun FilmstripPullHandle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val toggleLabel = stringResource(MR.strings.tsuzuki_reader_toggle_filmstrip)
    val stateDesc = if (expanded) stringResource(MR.strings.tsuzuki_expanded) else stringResource(MR.strings.tsuzuki_collapsed)
    val pullThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    Box(
        modifier
            .size(width = 64.dp, height = 14.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(role = Role.Button, onClickLabel = toggleLabel) { onExpandedChange(!expanded) }
            .pointerInput(expanded) {
                var cumulativeDy = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        cumulativeDy = 0f
                        triggered = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        cumulativeDy += dragAmount
                        if (!triggered) {
                            if (cumulativeDy < -pullThresholdPx && !expanded) {
                                triggered = true
                                onExpandedChange(true)
                            } else if (cumulativeDy > pullThresholdPx && expanded) {
                                triggered = true
                                onExpandedChange(false)
                            }
                        }
                    },
                )
            }
            .semantics {
                contentDescription = toggleLabel
                stateDescription = stateDesc
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (expanded) 40.dp else 32.dp, height = 3.5.dp)
                .clip(CircleShape)
                .background(if (expanded) colors.accent else colors.separator.copy(alpha = 0.72f)),
        )
    }
}

/**
 * Collapsible pull-up filmstrip (Paged) or storyboard ribbon (Webtoon) directly above the reader
 * scrubber. Collapsed by default behind a single centered pull-up bar pill with no side chevron;
 * tapping or dragging vertically expands/collapses the strip.
 */
@Composable
internal fun ReaderFilmstripTray(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isWebtoon: Boolean,
    isRtl: Boolean,
    currentPage: Int,
    totalPages: Int,
    pageProvider: (Int) -> ReaderPage?,
    onSelectPage: (Int) -> Unit,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>?,
    onScrubWebtoonFraction: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
    showHandle: Boolean = true,
    trayTopPadding: Dp = 0.dp,
) {
    if (totalPages <= 1) return
    val reduced = TsuzukiTheme.motion.reduced
    val thumbs = remember { ScrubThumbCache(limit = 48) }

    Column(modifier.fillMaxWidth()) {
        if (showHandle) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilmstripPullHandle(
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = KasaneSprings.nav(reduced),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = KasaneSprings.nav(reduced),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(),
        ) {
            if (isWebtoon) {
                val progressState = webtoonProgressFlow?.collectAsState()
                val progress = progressState?.value ?: WebtoonProgressState(
                    progress = ((currentPage - 1).toFloat() / (totalPages - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
                )
                WebtoonStoryboardRibbon(
                    totalPages = totalPages,
                    progress = progress,
                    pageProvider = pageProvider,
                    thumbs = thumbs,
                    onJumpFraction = { frac ->
                        if (onScrubWebtoonFraction != null) {
                            onScrubWebtoonFraction(frac)
                        } else {
                            onSelectPage(resolveWebtoonTargetPageFromFraction(frac, totalPages))
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = trayTopPadding, bottom = 6.dp),
                )
            } else {
                PagedFilmstripRow(
                    isRtl = isRtl,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    pageProvider = pageProvider,
                    thumbs = thumbs,
                    onSelectPage = onSelectPage,
                    modifier = Modifier.padding(top = trayTopPadding, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PagedFilmstripRow(
    isRtl: Boolean,
    currentPage: Int,
    totalPages: Int,
    pageProvider: (Int) -> ReaderPage?,
    thumbs: ScrubThumbCache,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val activeIdx = (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIdx)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val centerOffsetPx = with(density) { ((maxWidth - 44.dp) / 2f).roundToPx().coerceAtLeast(0) }
        LaunchedEffect(activeIdx, totalPages, centerOffsetPx) {
            if (activeIdx in 0 until totalPages) {
                listState.animateScrollToItem(activeIdx, scrollOffset = -centerOffsetPx)
            }
        }

        LazyRow(
            state = listState,
            reverseLayout = isRtl,
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            items(totalPages, key = { it }) { pageIdx ->
                val page = pageProvider(pageIdx)
                val ready = page?.statusFlow?.collectAsState()?.value
                var thumb by remember(pageIdx) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(pageIdx, page, ready) {
                    thumb = thumbs.peek(pageIdx + 1) ?: thumbs.load(pageIdx + 1) {
                        decodePageThumb(context, page, targetMaxPx = 260)
                    }
                }
                val active = pageIdx == activeIdx
                val shape = RoundedCornerShape(7.dp)
                Box(
                    Modifier
                        .size(width = if (active) 44.dp else 36.dp, height = if (active) 62.dp else 52.dp)
                        .clip(shape)
                        .background(colors.background.copy(alpha = 0.55f))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) colors.accent else colors.separator.copy(alpha = 0.65f),
                            shape = shape,
                        )
                        .clickable(role = Role.Button) { onSelectPage(pageIdx) },
                ) {
                    thumb?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) colors.accent else Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                    ) {
                        TsuzukiText(
                            "${pageIdx + 1}",
                            style = TsuzukiTheme.typography.caption2.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (active) colors.onAccent else Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebtoonStoryboardRibbon(
    totalPages: Int,
    progress: WebtoonProgressState,
    pageProvider: (Int) -> ReaderPage?,
    thumbs: ScrubThumbCache,
    onJumpFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    val sampleIndices = remember(totalPages) { computeStoryboardSampleIndices(totalPages, maxSamples = 12) }
    val window = computeStoryboardViewportWindow(progress.progress, progress.viewportFraction)
    val shape = RoundedCornerShape(10.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .border(1.dp, colors.separator.copy(alpha = 0.7f), shape)
            .background(colors.background.copy(alpha = 0.5f))
            .pointerInput(totalPages) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onJumpFraction(frac)
                }
            }
            .pointerInput(totalPages) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onJumpFraction(frac)
                }
            },
    ) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Row(Modifier.fillMaxSize()) {
            sampleIndices.forEach { pageIdx ->
                val page = pageProvider(pageIdx)
                val ready = page?.statusFlow?.collectAsState()?.value
                var thumb by remember(pageIdx, page, ready) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(pageIdx, page, ready) {
                    thumb = thumbs.peek(pageIdx + 1) ?: thumbs.load(pageIdx + 1) {
                        decodePageThumb(context, page, targetMaxPx = 220, cropTallTop = false)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (pageIdx % 2 == 0) colors.elevated.copy(alpha = 0.6f)
                                    else colors.background.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
            }
        }
        // Subtle curtains outside the viewport indicator window
        val winStartPx = (window.startFraction * totalWidthPx).roundToInt()
        val winWidthPx = (window.widthFraction * totalWidthPx).roundToInt().coerceAtLeast(12)
        val winStartDp = with(LocalDensity.current) { winStartPx.toDp() }
        val winWidthDp = with(LocalDensity.current) { winWidthPx.toDp() }
        if (winStartDp > 0.dp) {
            Box(Modifier.width(winStartDp).fillMaxHeight().background(Color.Black.copy(alpha = 0.42f)))
        }
        val rightEdgeDp = winStartDp + winWidthDp
        if (rightEdgeDp < maxWidth) {
            Box(
                Modifier
                    .offset(x = rightEdgeDp)
                    .width((maxWidth - rightEdgeDp).coerceAtLeast(0.dp))
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.42f)),
            )
        }
        // Highlighted viewport indicator lens
        Box(
            Modifier
                .offset { IntOffset(winStartPx, 0) }
                .width(winWidthDp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent.copy(alpha = 0.16f))
                .border(2.dp, colors.accent, RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * Vertical zoomed Webtoon minimap docked right beside the vertical page scrubber: shows the
 * current page and its immediate neighbours (`prev`, `curr`, `next`) scaled to full panel width
 * so readers see the actual zoomed content visible on screen inside the highlighted lens.
 */
/**
 * Compact pull handle pill for the unified vertical Webtoon minimap, tucked quietly along the
 * inner capsule edge (mirrors horizontal [FilmstripPullHandle]) without pushing [ChapterNavigator]
 * off-center when collapsed.
 */
@Composable
internal fun VerticalMinimapPullHandle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    handleOnLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val toggleLabel = stringResource(MR.strings.tsuzuki_reader_toggle_filmstrip)
    val stateDesc = if (expanded) stringResource(MR.strings.tsuzuki_expanded) else stringResource(MR.strings.tsuzuki_collapsed)
    val pullThresholdPx = with(LocalDensity.current) { 14.dp.toPx() }
    Box(
        modifier
            .width(16.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = toggleLabel) { onExpandedChange(!expanded) }
            .pointerInput(expanded, handleOnLeft) {
                var cumulativeDx = 0f
                var triggered = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        cumulativeDx = 0f
                        triggered = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        cumulativeDx += dragAmount
                        if (!triggered) {
                            val openingSwipe = if (handleOnLeft) cumulativeDx < -pullThresholdPx else cumulativeDx > pullThresholdPx
                            val closingSwipe = if (handleOnLeft) cumulativeDx > pullThresholdPx else cumulativeDx < -pullThresholdPx
                            if (openingSwipe && !expanded) {
                                triggered = true
                                onExpandedChange(true)
                            } else if (closingSwipe && expanded) {
                                triggered = true
                                onExpandedChange(false)
                            }
                        }
                    },
                )
            }
            .semantics {
                contentDescription = toggleLabel
                stateDescription = stateDesc
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 3.5.dp, height = if (expanded) 44.dp else 36.dp)
                .clip(CircleShape)
                .background(if (expanded) colors.accent else colors.text.copy(alpha = 0.34f)),
        )
    }
}

/**
 * Expandable Webtoon 7-page viewport strip + hairline divider hosted inside the unified side
 * [GlassSurface] alongside [ChapterNavigator]. Occupies 0 width when collapsed.
 */
@Composable
internal fun VerticalWebtoonMinimapStripContainer(
    expanded: Boolean,
    handleOnLeft: Boolean,
    currentPage: Int,
    totalPages: Int,
    pageProvider: (Int) -> ReaderPage?,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>?,
    onScrubWebtoonFraction: ((Float) -> Unit)?,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 1) return
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val thumbs = remember { ScrubThumbCache(limit = 32) }
    val progressState = webtoonProgressFlow?.collectAsState()
    val progress = progressState?.value ?: WebtoonProgressState(
        progress = ((currentPage - 1).toFloat() / (totalPages - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
    )

    AnimatedVisibility(
        visible = expanded,
        enter = expandHorizontally(
            animationSpec = KasaneSprings.nav(reduced),
            expandFrom = if (handleOnLeft) Alignment.End else Alignment.Start,
        ) + fadeIn(),
        exit = shrinkHorizontally(
            animationSpec = KasaneSprings.nav(reduced),
            shrinkTowards = if (handleOnLeft) Alignment.End else Alignment.Start,
        ) + fadeOut(),
        modifier = modifier.fillMaxHeight(),
    ) {
        Row(
            Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val strip: @Composable () -> Unit = {
                VerticalWebtoonViewportStrip(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    progress = progress,
                    pageProvider = pageProvider,
                    thumbs = thumbs,
                    onJumpFraction = { frac ->
                        if (onScrubWebtoonFraction != null) {
                            onScrubWebtoonFraction(frac)
                        } else {
                            onSelectPage(resolveWebtoonTargetPageFromFraction(frac, totalPages))
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .padding(
                            start = if (handleOnLeft) 18.dp else 6.dp,
                            end = if (handleOnLeft) 6.dp else 18.dp,
                        ),
                )
            }
            val divider: @Composable () -> Unit = {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp)
                        .background(colors.separator.copy(alpha = 0.25f)),
                )
            }
            if (handleOnLeft) {
                strip()
                divider()
            } else {
                divider()
                strip()
            }
        }
    }
}

@Composable
private fun VerticalWebtoonViewportStrip(
    currentPage: Int,
    totalPages: Int,
    progress: WebtoonProgressState,
    pageProvider: (Int) -> ReaderPage?,
    thumbs: ScrubThumbCache,
    onJumpFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val pos = remember(currentPage, totalPages, progress.progress) {
        resolveWebtoonMinimapPosition(currentPage, totalPages, progress.progress)
    }
    val currIdx = pos.centerPageIndex
    val windowStart = (currIdx - 3).coerceIn(0, (totalPages - 7).coerceAtLeast(0))
    val windowEnd = (windowStart + 6).coerceAtMost(totalPages - 1)
    val windowIndices = remember(windowStart, windowEnd) { (windowStart..windowEnd).toList() }
    val currentSlotIndex = (currIdx - windowStart).coerceIn(0, windowIndices.lastIndex.coerceAtLeast(0))
    val shape = RoundedCornerShape(14.dp)

    @Composable
    fun rememberPageThumb(pageIdx: Int): ImageBitmap? {
        val page = pageProvider(pageIdx)
        val status = page?.statusFlow?.collectAsState()?.value
        var thumb by remember(pageIdx) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(pageIdx, page, status) {
            thumb = thumbs.peek(pageIdx + 1) ?: thumbs.load(pageIdx + 1) {
                decodePageThumb(context, page, targetMaxPx = 220, cropTallTop = false)
            }
        }
        return thumb
    }

    val windowBmps = windowIndices.map { idx ->
        key(idx) { idx to rememberPageThumb(idx) }
    }

    BoxWithConstraints(
        modifier
            .width(62.dp)
            .fillMaxHeight()
            .clip(shape)
            .border(1.dp, colors.separator.copy(alpha = 0.65f), shape)
            .background(colors.background.copy(alpha = 0.55f))
            .pointerInput(totalPages) {
                detectTapGestures { offset ->
                    val frac = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onJumpFraction(frac)
                }
            }
            .pointerInput(totalPages) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onJumpFraction(frac)
                }
            },
    ) {
        val stripWidthPx = with(density) { maxWidth.toPx() }
        val stripHeightDp = maxHeight
        val stripHeightPx = with(density) { stripHeightDp.toPx() }.coerceAtLeast(1f)
        fun estimateHeightPx(bmp: ImageBitmap?): Float =
            if (bmp != null && bmp.width > 0) {
                (stripWidthPx * (bmp.height.toFloat() / bmp.width.toFloat())).coerceIn(stripWidthPx * 0.55f, stripHeightPx * 1.45f)
            } else {
                stripWidthPx * 1.65f
            }

        val slotHeightsPx = windowBmps.map { (_, bmp) -> estimateHeightPx(bmp) }
        val layout = computeMultiPageVerticalRibbonLayout(
            pageHeightsPx = slotHeightsPx,
            currentSlotIndex = currentSlotIndex,
            inPageFraction = pos.inPageFraction,
            stripHeightPx = stripHeightPx,
            lensHeightPx = stripHeightPx * 0.34f,
        )

        Box(Modifier.fillMaxSize().clipToBounds()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .graphicsLayer { translationY = layout.stackOffsetYPx },
            ) {
                windowBmps.forEachIndexed { slot, (idx, bmp) ->
                    val slotHeightDp = with(density) { slotHeightsPx[slot].toDp() }
                    Box(Modifier.fillMaxWidth().height(slotHeightDp)) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (idx % 2 == 0) colors.elevated.copy(alpha = 0.6f)
                                        else colors.background.copy(alpha = 0.4f),
                                    ),
                            )
                        }
                    }
                }
            }

            // Subtle curtains outside the zoomed current-view lens
            val lensTopDp = with(density) { layout.lensTopPx.toDp() }
            val lensHeightDp = with(density) { layout.lensHeightPx.toDp() }
            if (lensTopDp > 0.dp) {
                Box(Modifier.fillMaxWidth().height(lensTopDp).background(Color.Black.copy(alpha = 0.44f)))
            }
            val bottomStartDp = lensTopDp + lensHeightDp
            if (bottomStartDp < stripHeightDp) {
                Box(
                    Modifier
                        .offset(y = bottomStartDp)
                        .fillMaxWidth()
                        .height((stripHeightDp - bottomStartDp).coerceAtLeast(0.dp))
                        .background(Color.Black.copy(alpha = 0.44f)),
                )
            }

            // Highlighted current-view lens frame
            Box(
                Modifier
                    .offset { IntOffset(0, layout.lensTopPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(lensHeightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.10f))
                    .border(2.dp, colors.accent, RoundedCornerShape(8.dp)),
            )
        }
    }
}
