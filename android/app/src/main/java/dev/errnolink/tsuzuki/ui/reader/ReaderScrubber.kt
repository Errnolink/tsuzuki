package dev.errnolink.tsuzuki.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.LocalOpaqueGlass
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.drawSliderBarHandle
import dev.errnolink.tsuzuki.designsystem.drawSliderLens
import dev.errnolink.tsuzuki.designsystem.drawSliderThumbGap
import dev.errnolink.tsuzuki.designsystem.sliderKnobFill
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.decoder.ImageDecoder
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The track's bounds in window coordinates, shared with [ReaderScrubBubble] so the bubble stands over the thumb. */
@Stable
internal class ScrubTrack {
    var bounds by mutableStateOf(Rect.Zero)
}

private val ScrubberPad = 8.dp

/** How hard the scrubber ticks when the dragged page changes. */
internal enum class ScrubTick { None, Page, Edge }

/**
 * The scrubber tick: a light tick on every page, a stronger one at the first and last page
 * (Kasane's per-page buzz plus the end pulse). Pages are 1-based.
 */
internal fun scrubberTick(page: Int, lastPage: Int?, totalPages: Int): ScrubTick = when {
    totalPages <= 0 || page == lastPage -> ScrubTick.None
    page == 1 || page == totalPages -> ScrubTick.Edge
    else -> ScrubTick.Page
}

/**
 * Kasane page scrubber track: snaps to whole pages and turns the thumb into the shared glass
 * lens while dragging, ticking per page and harder at the ends. The preview bubble is drawn by
 * the bar ([ReaderScrubBubble]) — glass surfaces clip, so the bubble must float above them.
 */
internal val ReturnOriginPinColor = Color(0xFFF5B461)

@Composable
internal fun ReaderScrubber(
    page: Int,
    totalPages: Int,
    rtl: Boolean,
    enabled: Boolean,
    onPageIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    originPage: Int? = null,
    onPreview: (Int?) -> Unit = {},
    track: ScrubTrack? = null,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    val opaqueGlass = LocalOpaqueGlass.current
    val range = 1..totalPages.coerceAtLeast(1)
    val available = enabled && totalPages > 1
    var preview by remember { mutableStateOf<Int?>(null) }
    var dragFraction by remember(totalPages, rtl) { mutableStateOf<Float?>(null) }
    val restPage = scrubberRestPage(page, totalPages)
    val shown = preview ?: restPage
    val pageLabel = stringResource(MR.strings.tsuzuki_page)
    val pageStatePattern = stringResource(MR.strings.tsuzuki_page_n_of_m)
    val dragging = preview != null
    val scope = rememberCoroutineScope()
    val restFraction = calculateSliderFraction(restPage, range, rtl)
    val visualFraction = remember(totalPages, rtl) { Animatable(restFraction) }
    LaunchedEffect(restFraction, dragFraction) {
        if (dragFraction == null) {
            if (motion.reduced) {
                visualFraction.snapTo(restFraction)
            } else {
                visualFraction.animateTo(restFraction, KasaneSprings.fastSpatial(motion.reduced))
            }
        }
    }
    val fraction = dragFraction ?: visualFraction.value
    val pressedFraction by animateFloatAsState(
        if (dragging && available) 1f else 0f,
        KasaneSprings.fastSpatial(motion.reduced),
        label = "Scrub press",
    )
    val lensFraction by animateFloatAsState(
        if (dragging && available && !motion.reduced) 1f else 0f,
        KasaneSprings.fastSpatial(motion.reduced),
        label = "Scrub lens",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (track != null) Modifier.onGloballyPositioned { track.bounds = it.boundsInWindow() } else Modifier)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics {
                contentDescription = pageLabel
                stateDescription = pageStatePattern.format(shown, range.last)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    shown.toFloat(),
                    range.first.toFloat()..range.last.toFloat(),
                    (range.last - range.first - 1).coerceAtLeast(0),
                )
                if (!available) disabled()
                setProgress { target ->
                    if (available) {
                        onPageIndexChange(target.roundToInt().coerceIn(range) - 1)
                        true
                    } else {
                        false
                    }
                }
            }
            .onKeyEvent { event ->
                if (!available || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> if (rtl) 1 else -1
                    Key.DirectionRight -> if (rtl) -1 else 1
                    else -> return@onKeyEvent false
                }
                onPageIndexChange((shown - 1 + delta).coerceIn(0, totalPages - 1))
                true
            }
            .focusable(available)
            .pointerInput(range, rtl, available) {
                if (!available) return@pointerInput
                val padPx = ScrubberPad.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun rawFractionAtX(x: Float): Float =
                        ((x - padPx) / (size.width.toFloat() - padPx * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)

                    fun pageAtX(x: Float): Int =
                        calculateSliderValueAt(x, size.width.toFloat(), padPx, range, rtl)

                    fun setPreview(next: Int) {
                        if (next == preview) return
                        when (scrubberTick(next, preview, totalPages)) {
                            ScrubTick.Page -> haptics.detent()
                            ScrubTick.Edge -> haptics.pageTurn()
                            ScrubTick.None -> Unit
                        }
                        preview = next
                        onPreview(next)
                    }

                    dragFraction = rawFractionAtX(down.position.x)
                    setPreview(pageAtX(down.position.x))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            if (!change.pressed) {
                                dragFraction = rawFractionAtX(change.position.x)
                                val targetPage = pageAtX(change.position.x)
                                setPreview(targetPage)
                                change.consume()
                                onPageIndexChange(targetPage - 1)
                                break
                            }
                            dragFraction = rawFractionAtX(change.position.x)
                            setPreview(pageAtX(change.position.x))
                            change.consume()
                        }
                    } finally {
                        val released = dragFraction
                        scope.launch {
                            if (released != null) visualFraction.snapTo(released)
                            dragFraction = null
                        }
                        preview = null
                        onPreview(null)
                    }
                }
            },
    ) {
        val trackH = 6.dp.toPx()
        val padPx = ScrubberPad.toPx()
        val centerY = size.height / 2f
        val fill = if (available) inkOnGlass(colors.accent, colors.text, colors.glassTint) else colors.secondary
        val x = padPx + fraction * (size.width - padPx * 2)
        drawRoundRect(
            colors.separator,
            topLeft = Offset(padPx, centerY - trackH / 2f),
            size = Size(size.width - padPx * 2, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val fillLeft = if (rtl) x else padPx
        val fillWidth = if (rtl) (size.width - padPx - x) else (x - padPx)
        drawRoundRect(
            fill,
            topLeft = Offset(fillLeft, centerY - trackH / 2f),
            size = Size(fillWidth.coerceAtLeast(0f), trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        if (shown < range.last) {
            drawCircle(
                fill,
                2.dp.toPx(),
                Offset(if (rtl) 6.dp.toPx() else size.width - 6.dp.toPx(), centerY),
            )
        }

        if (originPage != null && originPage in range && range.last > 1 && originPage != shown) {
            val originFraction = calculateSliderFraction(originPage, range, rtl)
            val originCenter = Offset(padPx + originFraction * (size.width - padPx * 2), centerY)
            drawCircle(colors.background.copy(alpha = 0.72f), 4.5.dp.toPx(), originCenter)
            drawCircle(ReturnOriginPinColor, 3.2.dp.toPx(), originCenter)
        }

        val centre = Offset(x, centerY)
        drawSliderThumbGap(centre, pressedFraction, lensFraction)
        drawSliderLens(
            centre,
            lensFraction,
            rtl,
            fill = fill,
            rest = colors.separator,
            knob = sliderKnobFill(colors, lensFraction, opaqueGlass),
            darkGlass = colors.isDark,
        )
        drawSliderBarHandle(fill, centre, pressedFraction, lensFraction)
    }
}

/**
 * The scrubber preview bubble: floats 12 dp above the bar glass with the page thumbnail (only
 * when the page image is already loaded locally), the page number and "of N" (Kasane scrubber
 * bubble). [page] is the held preview; the bubble rises while [visible] and dissolves otherwise.
 * [track] is the scrubber's track, so the bubble centres on the thumb. The bubble takes no layout
 * height: holding the scrubber must never push the bar it floats over.
 */
@Composable
internal fun ReaderScrubBubble(
    visible: Boolean,
    page: Int?,
    totalPages: Int,
    rtl: Boolean,
    pageAt: (Int) -> ReaderPage?,
    modifier: Modifier = Modifier,
    track: ScrubTrack,
) {
    if (page == null || totalPages <= 0) return
    val context = LocalContext.current
    val thumbs = remember { ScrubThumbCache() }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(page) {
        thumb = thumbs.peek(page) ?: thumbs.load(page) { decodePageThumb(context, pageAt(page - 1)) }
    }
    val fraction = calculateSliderFraction(page.coerceIn(1, totalPages), 1..totalPages, rtl)
    val motion = TsuzukiTheme.motion
    val bubbleFraction by animateFloatAsState(
        if (visible) 1f else 0f,
        KasaneSprings.fastSpatial(motion.reduced),
        label = "Scrub bubble",
    )
    if (!visible && bubbleFraction <= 0f) return

    var host by remember { mutableStateOf(Rect.Zero) }
    var bubbleWidthPx by remember { mutableStateOf(0) }
    var bubbleHeightPx by remember { mutableStateOf(0) }
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                layout(placeable.width, 0) { placeable.place(0, 0) }
            }
            .onGloballyPositioned { host = it.boundsInWindow() },
    ) {
        val leftPx = with(LocalDensity.current) {
            scrubBubbleLeftPx(
                trackLeftPx = track.bounds.left - host.left,
                trackWidthPx = track.bounds.width,
                padPx = ScrubberPad.toPx(),
                fraction = fraction,
                containerWidthPx = host.width,
                bubbleWidthPx = bubbleWidthPx.toFloat(),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(leftPx.roundToInt(), -(bubbleHeightPx + 12.dp.roundToPx())) }
                .graphicsLayer {
                    val f = bubbleFraction.coerceIn(0f, 1f)
                    alpha = f
                    scaleX = 0.88f + 0.12f * f
                    scaleY = 0.88f + 0.12f * f
                    translationY = (1f - f) * 10.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .onSizeChanged {
                    bubbleWidthPx = it.width
                    bubbleHeightPx = it.height
                },
        ) {
            GlassSurface(null, shape = RoundedCornerShape(TsuzukiCorners.group)) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    thumb?.let { bitmap ->
                        Image(
                            bitmap,
                            null,
                            Modifier
                                .size(width = 84.dp, height = 126.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(top = if (thumb != null) 6.dp else 0.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        TsuzukiText(
                            page.toString(),
                            style = TsuzukiTheme.typography.headline.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                        TsuzukiText(
                            " / $totalPages",
                            style = TsuzukiTheme.typography.subhead.copy(fontFeatureSettings = "tnum"),
                            color = TsuzukiTheme.colors.secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Small per-reader-session cache of scrub-bubble/filmstrip/overview thumbnails, decoded from already-loaded pages. */
internal class ScrubThumbCache(private val limit: Int = 16) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<Int, ImageBitmap?>()

    suspend fun peek(page: Int): ImageBitmap? = mutex.withLock { entries[page] }

    suspend fun load(page: Int, decode: suspend () -> ImageBitmap?): ImageBitmap? {
        peek(page)?.let { return it }
        val value = decode()
        mutex.withLock {
            if (value != null) {
                entries[page] = value
                while (entries.size > limit) {
                    entries.remove(entries.keys.first())
                }
            }
        }
        return value
    }
}

/**
 * Decodes a small thumbnail from a page whose image is already loaded locally (the reader stream
 * reads the cached bytes; this never starts a network load). Null when the page is not ready or
 * the decode fails — callers fall back to clean page numbers or placeholders.
 */
internal suspend fun decodePageThumb(
    context: Context,
    readerPage: ReaderPage?,
    targetMaxPx: Int = 400,
    cropTallTop: Boolean = true,
): ImageBitmap? = withContext(Dispatchers.IO) {
    decodePageSoftwareBitmap(readerPage, targetMaxPx, cropTallTop)?.asImageBitmap()
}

/**
 * Decodes a software `ARGB_8888` bitmap from a ready [ReaderPage]'s cached stream on `Dispatchers.IO`
 * (never triggers network fetches). Supports both standard image codecs and `tachiyomi.decoder.ImageDecoder`
 * (AVIF/HEIF/JXL) and ensures no `Bitmap.Config.HARDWARE` instance is returned. When [cropTallTop]
 * is enabled, long Webtoon strips are sliced to their top frame via `BitmapRegionDecoder` so grid
 * thumbnails remain crisp instead of blurry low-res slivers.
 */
internal suspend fun decodePageSoftwareBitmap(
    readerPage: ReaderPage?,
    targetMaxPx: Int = 2048,
    cropTallTop: Boolean = false,
): Bitmap? = withContext(Dispatchers.IO) {
    readerPage ?: return@withContext null
    if (readerPage.statusFlow.value !is Page.State.Ready) return@withContext null
    val streamFn = readerPage.stream ?: return@withContext null
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        streamFn().use { source -> BitmapFactory.decodeStream(source, null, bounds) }
        val maxHeightPx = if (targetMaxPx < 1024) 2400 else 0
        val region = if (cropTallTop) computeTallThumbRegion(bounds.outWidth, bounds.outHeight) else null
        val sample = if (region != null) {
            computeThumbSampleSize(region.width, region.height, targetWidthPx = targetMaxPx, maxHeightPx = maxHeightPx)
        } else {
            computeThumbSampleSize(bounds.outWidth, bounds.outHeight, targetWidthPx = targetMaxPx, maxHeightPx = maxHeightPx)
        }
        val raw: Bitmap = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val regionDecoded = if (region != null) {
                runCatching {
                    streamFn().use { source ->
                        val regionDecoder = if (Build.VERSION.SDK_INT >= 31) {
                            BitmapRegionDecoder.newInstance(source)
                        } else {
                            @Suppress("DEPRECATION")
                            BitmapRegionDecoder.newInstance(source, false)
                        } ?: return@runCatching null
                        try {
                            regionDecoder.decodeRegion(
                                AndroidRect(region.x, region.y, region.x + region.width, region.y + region.height),
                                options,
                            )
                        } finally {
                            regionDecoder.recycle()
                        }
                    }
                }.getOrNull()
            } else {
                null
            }
            regionDecoded ?: streamFn().use { source -> BitmapFactory.decodeStream(source, null, options) }
        } else {
            streamFn().use { source ->
                val decoder = ImageDecoder.newInstance(source) ?: return@use null
                try {
                    val decSample = computeThumbSampleSize(
                        outWidth = decoder.width,
                        outHeight = decoder.height,
                        targetWidthPx = targetMaxPx,
                        maxHeightPx = maxHeightPx,
                    )
                    decoder.decode(sampleSize = decSample)
                } finally {
                    decoder.recycle()
                }
            }
        } ?: return@runCatching null
        val croppedRaw = if (cropTallTop) {
            val cropBounds = computeTallThumbRegion(raw.width, raw.height)
            if (cropBounds != null && cropBounds.height < raw.height) {
                try {
                    Bitmap.createBitmap(raw, cropBounds.x, cropBounds.y, cropBounds.width, cropBounds.height)
                } finally {
                    if (!raw.isRecycled) raw.recycle()
                }
            } else {
                raw
            }
        } else {
            raw
        }
        if (croppedRaw.config == Bitmap.Config.HARDWARE) {
            try {
                croppedRaw.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                if (!croppedRaw.isRecycled) croppedRaw.recycle()
            }
        } else {
            croppedRaw
        }
    }.getOrNull()
}
