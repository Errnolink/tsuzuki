package dev.errnolink.tsuzuki.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** One loaded page of the capture document: its source size and an on-screen preview. */
private class CapturePage(
    val page: ReaderPage,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val preview: ImageBitmap,
) {
    val aspect: Float get() = nativeHeight.toFloat() / nativeWidth.coerceAtLeast(1)
}

/** Pages around the current one a Webtoon capture can reach. */
private const val CaptureWebtoonPagesBefore = 2
private const val CaptureWebtoonPagesAfter = 3
private const val CapturePreviewMinWidthPx = 540
private const val CaptureMaxOutputWidthPx = 1440

/**
 * Reader capture as a long screenshot (Chrome-style): the current chapter's loaded pages around
 * the current one, stacked and scrollable, under a selection that opens on what the reader
 * showed. The top and bottom edges drag past the screen edge and the strip scrolls with them, so
 * one capture can run across pages; the side edges crop the width. "Visible" and "This page" are
 * quick selections. Saving decodes only the selected rows of each page at source resolution.
 */
@Composable
internal fun ReaderCaptureOverlay(
    visible: Boolean,
    glass: GlassContext?,
    currentPage: Int,
    totalPages: Int,
    isWebtoon: Boolean,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>?,
    pageProvider: (Int) -> ReaderPage?,
    onSavePng: (ReaderPage, ByteArray) -> Unit,
    onSharePng: (ReaderPage, ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)

    val scope = rememberCoroutineScope()
    val currentIndex = (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val progress = webtoonProgressFlow?.collectAsState()?.value
    val inPageFraction = remember {
        if (isWebtoon && progress != null) {
            resolveWebtoonMinimapPosition(currentPage, totalPages, progress.progress)
                .takeIf { it.centerPageIndex == currentIndex }?.inPageFraction ?: 0f
        } else {
            0f
        }
    }

    var pages by remember { mutableStateOf<List<CapturePage>?>(null) }
    var currentInDoc by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val range = if (isWebtoon) {
            (currentIndex - CaptureWebtoonPagesBefore).coerceAtLeast(0)..
                (currentIndex + CaptureWebtoonPagesAfter).coerceAtMost(totalPages - 1)
        } else {
            currentIndex..currentIndex
        }
        val loaded = withContext(Dispatchers.IO) {
            range.map { idx -> idx to pageProvider(idx)?.let { loadCapturePage(it) } }
        }
        val currentSlot = loaded.indexOfFirst { it.first == currentIndex }
        val contiguous = if (currentSlot < 0 || loaded[currentSlot].second == null) {
            emptyList()
        } else {
            val first = (currentSlot downTo 0).takeWhile { loaded[it].second != null }.last()
            val last = (currentSlot until loaded.size).takeWhile { loaded[it].second != null }.last()
            currentInDoc = currentSlot - first
            loaded.subList(first, last + 1).mapNotNull { it.second }
        }
        pages = contiguous
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
        ) {
            val doc = pages
            val tops = remember(doc) { doc?.let { captureDocumentTops(it.map(CapturePage::aspect)) } }
            var selection by remember(doc) { mutableStateOf<CaptureSelection?>(null) }
            var scrollDoc by remember(doc) { mutableFloatStateOf(0f) }
            var viewDoc by remember { mutableFloatStateOf(1f) }

            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (doc == null || tops == null) {
                        ProgressRing(0f, stringResource(MR.strings.tsuzuki_reader_loading_page), Modifier.size(32.dp))
                    } else if (doc.isEmpty()) {
                        TsuzukiText(
                            stringResource(MR.strings.tsuzuki_reader_page_unavailable),
                            style = TsuzukiTheme.typography.callout,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    } else {
                        val stageW = with(LocalDensity.current) { maxWidth.toPx() }
                        val stageH = with(LocalDensity.current) { maxHeight.toPx() }
                        viewDoc = viewDocOf(stageW, stageH)
                        CaptureStage(
                            doc = doc,
                            tops = tops,
                            currentInDoc = currentInDoc,
                            isWebtoon = isWebtoon,
                            inPageFraction = inPageFraction,
                            stageWidthPx = stageW,
                            stageHeightPx = stageH,
                            selection = selection,
                            onSelection = { selection = it },
                            scrollDoc = scrollDoc,
                            onScroll = { scrollDoc = it },
                        )
                    }
                }

                CaptureBar(
                    glass = glass,
                    enabled = doc != null && doc.isNotEmpty() && selection != null && !exporting,
                    onVisible = {
                        val t = tops ?: return@CaptureBar
                        selection = CaptureSelection(top = scrollDoc, bottom = (scrollDoc + viewDoc).coerceAtMost(t.last()))
                    },
                    onThisPage = {
                        val t = tops ?: return@CaptureBar
                        val mid = scrollDoc + viewDoc / 2f
                        val i = (t.indexOfLast { it <= mid }).coerceIn(0, t.size - 2)
                        selection = CaptureSelection(top = t[i], bottom = t[i + 1])
                        scrollDoc = t[i].coerceIn(0f, (t.last() - viewDoc).coerceAtLeast(0f))
                    },
                    onCancel = onDismiss,
                    onExport = { share ->
                        val d = doc ?: return@CaptureBar
                        val t = tops ?: return@CaptureBar
                        val sel = selection ?: return@CaptureBar
                        if (exporting) return@CaptureBar
                        exporting = true
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { renderCapture(d, t, sel) }
                            exporting = false
                            val target = d.getOrNull(captureSlices(sel, t).firstOrNull()?.pageIndex ?: 0)?.page
                            if (bytes != null && target != null) {
                                if (share) onSharePng(target, bytes) else onSavePng(target, bytes)
                            }
                            onDismiss()
                        }
                    },
                )
            }
        }
    }
}

/** Height of the stage in document units (the stage is one document unit wide). */
private fun viewDocOf(stageWidthPx: Float, stageHeightPx: Float): Float = stageHeightPx / stageWidthPx.coerceAtLeast(1f)

@Composable
private fun CaptureStage(
    doc: List<CapturePage>,
    tops: List<Float>,
    currentInDoc: Int,
    isWebtoon: Boolean,
    inPageFraction: Float,
    stageWidthPx: Float,
    stageHeightPx: Float,
    selection: CaptureSelection?,
    onSelection: (CaptureSelection) -> Unit,
    scrollDoc: Float,
    onScroll: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val unitPx = stageWidthPx.coerceAtLeast(1f)
    val docHeight = tops.last()
    val viewDoc = stageHeightPx / unitPx
    val maxScroll = (docHeight - viewDoc).coerceAtLeast(0f)

    LaunchedEffect(doc) {
        val pageTop = tops[currentInDoc]
        val pageH = tops[currentInDoc + 1] - pageTop
        if (isWebtoon) {
            val start = (pageTop + inPageFraction * pageH).coerceIn(0f, maxScroll)
            onScroll(start)
            onSelection(CaptureSelection(top = start, bottom = (start + viewDoc).coerceAtMost(docHeight)))
        } else {
            onScroll(pageTop.coerceIn(0f, maxScroll))
            onSelection(CaptureSelection(top = pageTop, bottom = pageTop + pageH))
        }
    }
    val sel = selection ?: return

    val currentSelection by rememberUpdatedState(sel)
    val currentScroll by rememberUpdatedState(scrollDoc)
    var autoDir by remember { mutableIntStateOf(0) }
    var heldEdge by remember { mutableStateOf<CaptureEdge?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    val edgeZonePx = with(density) { 48.dp.toPx() }
    val hitRadiusPx = with(density) { 28.dp.toPx() }
    val minHeightDoc = with(density) { 64.dp.toPx() } / unitPx

    fun applyEdge(scroll: Float) {
        val edge = heldEdge ?: return
        onSelection(
            moveCaptureEdge(
                currentSelection,
                edge,
                docY = scroll + fingerY / unitPx,
                fraction = fingerX / unitPx,
                documentHeight = docHeight,
                minHeight = minHeightDoc,
            ),
        )
    }

    LaunchedEffect(autoDir) {
        if (autoDir == 0) return@LaunchedEffect
        val speedDocPerFrame = with(density) { 10.dp.toPx() } / unitPx
        while (true) {
            withFrameNanos { }
            val next = (currentScroll + autoDir * speedDocPerFrame).coerceIn(0f, maxScroll)
            onScroll(next)
            applyEdge(next)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(doc) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val s = currentSelection
                    val scroll = currentScroll
                    val edge = hitTestCaptureEdge(
                        x = down.position.x,
                        y = down.position.y,
                        leftPx = s.left * unitPx,
                        topPx = (s.top - scroll) * unitPx,
                        rightPx = s.right * unitPx,
                        bottomPx = (s.bottom - scroll) * unitPx,
                        radiusPx = hitRadiusPx,
                    )
                    heldEdge = edge
                    down.consume()
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val pos = change.position
                        if (edge == null) {
                            onScroll((currentScroll - (pos.y - last.y) / unitPx).coerceIn(0f, maxScroll))
                        } else {
                            fingerX = pos.x
                            fingerY = pos.y
                            applyEdge(currentScroll)
                            autoDir = if (edge == CaptureEdge.Top || edge == CaptureEdge.Bottom) {
                                when {
                                    pos.y < edgeZonePx -> -1
                                    pos.y > stageHeightPx - edgeZonePx -> 1
                                    else -> 0
                                }
                            } else {
                                0
                            }
                        }
                        last = pos
                    }
                    autoDir = 0
                    heldEdge = null
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true, align = Alignment.Top)
                .graphicsLayer { translationY = -scrollDoc * unitPx },
        ) {
            doc.forEach { page ->
                Image(
                    bitmap = page.preview,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (page.aspect * unitPx).toDp() }),
                )
            }
        }

        val selTopPx = (sel.top - scrollDoc) * unitPx
        val selBottomPx = (sel.bottom - scrollDoc) * unitPx
        val selLeftPx = sel.left * unitPx
        val selRightPx = sel.right * unitPx
        Canvas(Modifier.fillMaxSize()) {
            val mask = Color.Black.copy(alpha = 0.58f)
            if (selTopPx > 0f) drawRect(mask, Offset.Zero, Size(size.width, selTopPx))
            if (selBottomPx < size.height) drawRect(mask, Offset(0f, selBottomPx.coerceAtLeast(0f)), Size(size.width, size.height - selBottomPx.coerceAtLeast(0f)))
            val bandTop = selTopPx.coerceAtLeast(0f)
            val bandH = (selBottomPx.coerceAtMost(size.height) - bandTop).coerceAtLeast(0f)
            if (selLeftPx > 0f) drawRect(mask, Offset(0f, bandTop), Size(selLeftPx, bandH))
            if (selRightPx < size.width) drawRect(mask, Offset(selRightPx, bandTop), Size(size.width - selRightPx, bandH))
            val stroke = 2.dp.toPx()
            drawRoundRect(
                Color.White,
                topLeft = Offset(selLeftPx, selTopPx),
                size = Size(selRightPx - selLeftPx, selBottomPx - selTopPx),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(stroke),
            )
            val pillLong = 40.dp.toPx()
            val pillShort = 5.dp.toPx()
            val midX = (selLeftPx + selRightPx) / 2f
            val midY = (selTopPx + selBottomPx).coerceIn(0f, size.height * 2f) / 2f
            fun pill(x: Float, y: Float, w: Float, h: Float) =
                drawRoundRect(Color.White, Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(h / 2f, h / 2f))
            pill(midX, selTopPx, pillLong, pillShort)
            pill(midX, selBottomPx, pillLong, pillShort)
            val sideY = midY.coerceIn(bandTop + pillLong / 2f, (bandTop + bandH - pillLong / 2f).coerceAtLeast(bandTop + pillLong / 2f))
            pill(selLeftPx, sideY, pillShort, pillLong)
            pill(selRightPx, sideY, pillShort, pillLong)
            val railX = size.width - 3.dp.toPx()
            val railW = 3.dp.toPx()
            drawRect(Color.White.copy(alpha = 0.12f), Offset(railX, 0f), Size(railW, size.height))
            drawRect(
                Color.White.copy(alpha = 0.45f),
                Offset(railX, scrollDoc / docHeight * size.height),
                Size(railW, (viewDoc / docHeight).coerceAtMost(1f) * size.height),
            )
            drawRect(
                Color(0xFF5AA2FF),
                Offset(railX, sel.top / docHeight * size.height),
                Size(railW, sel.height / docHeight * size.height),
            )
        }

        val (outW, outH) = captureOutputSize(sel, preferredCaptureWidth(doc, tops, sel))
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((selLeftPx + 8.dp.toPx()).roundToInt(), (selTopPx.coerceAtLeast(0f) + 8.dp.toPx()).roundToInt()) }
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TsuzukiText(
                "$outW × $outH",
                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CaptureBar(
    glass: GlassContext?,
    enabled: Boolean,
    onVisible: () -> Unit,
    onThisPage: () -> Unit,
    onCancel: () -> Unit,
    onExport: (share: Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        GlassSurface(glass, Modifier.fillMaxWidth(), shape = RoundedCornerShape(TsuzukiCorners.menu)) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsuzukiButton(
                        label = stringResource(MR.strings.tsuzuki_reader_capture_visible),
                        onClick = onVisible,
                        enabled = enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Soft,
                        modifier = Modifier.weight(1f),
                    )
                    TsuzukiButton(
                        label = stringResource(MR.strings.tsuzuki_reader_capture_this_page),
                        onClick = onThisPage,
                        enabled = enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Soft,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TsuzukiButton(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = onCancel,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Soft,
                        minWidth = 0.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    TsuzukiButton(
                        label = null,
                        onClick = { onExport(true) },
                        enabled = enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Tonal,
                        icon = { Icon(TsuzukiIcons.Share, stringResource(MR.strings.action_share), Modifier.size(18.dp)) },
                        minWidth = 44.dp,
                    )
                    TsuzukiButton(
                        label = stringResource(MR.strings.action_save),
                        onClick = { onExport(false) },
                        enabled = enabled,
                        size = TsuzukiButtonSize.Sm,
                        variant = TsuzukiButtonVariant.Filled,
                        minWidth = 0.dp,
                    )
                }
            }
        }
    }
}

/** The source width under the selection: the widest page it covers, capped for sharing. */
private fun preferredCaptureWidth(doc: List<CapturePage>, tops: List<Float>, sel: CaptureSelection): Int {
    val widest = captureSlices(sel, tops).maxOfOrNull { doc[it.pageIndex].nativeWidth } ?: doc.first().nativeWidth
    return (widest * sel.width).roundToInt().coerceIn(1, CaptureMaxOutputWidthPx)
}

/** Reads a ready page's size and decodes a screen-width preview; null when the page is not loaded. */
private suspend fun loadCapturePage(page: ReaderPage): CapturePage? {
    if (page.statusFlow.value !is Page.State.Ready) return null
    val stream = page.stream ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { stream().use { BitmapFactory.decodeStream(it, null, bounds) } }
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CapturePreviewMinWidthPx) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val preview = runCatching { stream().use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
        if (preview != null) return CapturePage(page, bounds.outWidth, bounds.outHeight, preview.asImageBitmap())
    }
    val decoded = decodePageSoftwareBitmap(page, targetMaxPx = 1080) ?: return null
    return CapturePage(page, decoded.width, decoded.height, decoded.asImageBitmap())
}

/** Stitches the selected rows of each covered page, decoded at source resolution, into a PNG. */
private suspend fun renderCapture(doc: List<CapturePage>, tops: List<Float>, sel: CaptureSelection): ByteArray? {
    val slices = captureSlices(sel, tops)
    if (slices.isEmpty()) return null
    val (outW, outH) = captureOutputSize(sel, preferredCaptureWidth(doc, tops, sel))
    if (outW <= 0 || outH <= 0) return null
    val out = runCatching { Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
    val canvas = AndroidCanvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val pxPerDoc = outW / sel.width
    var y = 0f
    try {
        for (slice in slices) {
            val cp = doc[slice.pageIndex]
            val region = AndroidRect(
                (sel.left * cp.nativeWidth).roundToInt(),
                (slice.fromFraction * cp.nativeHeight).roundToInt(),
                (sel.right * cp.nativeWidth).roundToInt(),
                (slice.toFraction * cp.nativeHeight).roundToInt().coerceAtLeast((slice.fromFraction * cp.nativeHeight).roundToInt() + 1),
            )
            val dstH = slice.docHeight * pxPerDoc
            val piece = decodeRegion(cp, region, (outW).coerceAtLeast(1))
            if (piece != null) {
                canvas.drawBitmap(piece, null, RectF(0f, y, outW.toFloat(), y + dstH), paint)
                piece.recycle()
            }
            y += dstH
        }
        return ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    } finally {
        out.recycle()
    }
}

/** Decodes [region] of a page in source pixels, sampled down to roughly [targetWidth]. */
private suspend fun decodeRegion(cp: CapturePage, region: AndroidRect, targetWidth: Int): Bitmap? {
    var sample = 1
    while (region.width() / (sample * 2) >= targetWidth) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val stream = cp.page.stream
    val viaRegion = stream?.let {
        runCatching {
            it().use { source ->
                val decoder = if (Build.VERSION.SDK_INT >= 31) {
                    BitmapRegionDecoder.newInstance(source)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(source, false)
                } ?: return@use null
                try {
                    decoder.decodeRegion(region, options)
                } finally {
                    decoder.recycle()
                }
            }
        }.getOrNull()
    }
    if (viaRegion != null) return viaRegion
    val full = decodePageSoftwareBitmap(cp.page, targetMaxPx = CaptureMaxOutputWidthPx) ?: return null
    val sx = full.width.toFloat() / cp.nativeWidth
    val sy = full.height.toFloat() / cp.nativeHeight
    val x = (region.left * sx).roundToInt().coerceIn(0, full.width - 1)
    val yTop = (region.top * sy).roundToInt().coerceIn(0, full.height - 1)
    val w = (region.width() * sx).roundToInt().coerceIn(1, full.width - x)
    val h = (region.height() * sy).roundToInt().coerceIn(1, full.height - yTop)
    if (x == 0 && yTop == 0 && w == full.width && h == full.height) return full
    return try {
        Bitmap.createBitmap(full, x, yTop, w, h)
    } finally {
        full.recycle()
    }
}
