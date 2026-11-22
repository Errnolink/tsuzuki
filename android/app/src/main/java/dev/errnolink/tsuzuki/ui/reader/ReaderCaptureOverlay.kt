package dev.errnolink.tsuzuki.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Captures the live rendered reader viewport [targetView] from [activity]'s window via `PixelCopy`
 * (falling back to software `View.draw` below API 26) so Webtoon cross-seam panels, zoomed
 * regions, and dual-page spreads are captured exactly as displayed on screen.
 */
internal fun captureReaderViewport(
    activity: Activity,
    targetView: View?,
    onCaptured: (Bitmap?) -> Unit,
) {
    if (targetView == null || targetView.width <= 0 || targetView.height <= 0 || activity.isFinishing || activity.isDestroyed) {
        onCaptured(null)
        return
    }
    val loc = IntArray(2)
    targetView.getLocationInWindow(loc)
    val decor = activity.window?.decorView
    val maxW = decor?.width ?: (loc[0] + targetView.width)
    val maxH = decor?.height ?: (loc[1] + targetView.height)
    val left = loc[0].coerceIn(0, maxW)
    val top = loc[1].coerceIn(0, maxH)
    val right = (loc[0] + targetView.width).coerceIn(left, maxW)
    val bottom = (loc[1] + targetView.height).coerceIn(top, maxH)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) {
        onCaptured(null)
        return
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val srcRect = AndroidRect(left, top, right, bottom)
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        fun finish(ok: Boolean) {
            if (completed) return
            completed = true
            if (ok) {
                onCaptured(bitmap)
            } else {
                if (!bitmap.isRecycled) bitmap.recycle()
                onCaptured(null)
            }
        }
        try {
            PixelCopy.request(
                activity.window,
                srcRect,
                bitmap,
                { result -> finish(result == PixelCopy.SUCCESS) },
                handler,
            )
            handler.postDelayed({ finish(false) }, 300L)
        } catch (_: Throwable) {
            finish(false)
        }
    } else {
        try {
            val canvas = AndroidCanvas(bitmap)
            targetView.draw(canvas)
            onCaptured(bitmap)
        } catch (_: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            onCaptured(null)
        }
    }
}

/**
 * Reader UI v3 Capture / Crop overlay:
 * Uses the live [viewportBitmap] by default in `Crop` mode (capturing exactly what is visible on
 * screen across Webtoon seams, zoom, and spreads) and decodes the full [ReaderPage] software
 * bitmap on demand for `Full page` mode (or fallback). All crop/resize gestures run on the
 * stationary outer image container so handles never oscillate.
 */
@Composable
internal fun ReaderCaptureOverlay(
    visible: Boolean,
    glass: GlassContext?,
    currentPage: Int,
    viewportBitmap: Bitmap?,
    pageProvider: (Int) -> ReaderPage?,
    onSavePng: (ReaderPage, ByteArray) -> Unit,
    onSharePng: (ReaderPage, ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)

    val colors = TsuzukiTheme.colors
    val scope = rememberCoroutineScope()
    val page = remember(currentPage) { pageProvider((currentPage - 1).coerceAtLeast(0)) }

    var fullPageBitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    var fullPageDisplayBitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    var fullPageLoadFinished by remember(page) { mutableStateOf(false) }
    var fullPageMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(NormalizedCropRect.Default) }
    var exporting by remember { mutableStateOf(false) }

    val hasViewport = viewportBitmap != null && !viewportBitmap.isRecycled
    val viewportDisplayBitmap = remember(viewportBitmap) {
        viewportBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
    }

    LaunchedEffect(page, fullPageMode, hasViewport) {
        if (!fullPageMode && hasViewport) return@LaunchedEffect
        if (fullPageBitmap != null || fullPageLoadFinished) return@LaunchedEffect
        val decoded = decodePageSoftwareBitmap(page, targetMaxPx = 2048)
        fullPageBitmap = decoded
        fullPageDisplayBitmap = decoded?.asImageBitmap()
        fullPageLoadFinished = true
    }

    DisposableEffect(fullPageBitmap) {
        val target = fullPageBitmap
        onDispose {
            if (target != null && !target.isRecycled) {
                target.recycle()
            }
        }
    }

    val activeBitmap = if (!fullPageMode && hasViewport) viewportBitmap else fullPageBitmap
    val activeDisplayBitmap = if (!fullPageMode && hasViewport) viewportDisplayBitmap else fullPageDisplayBitmap
    val loadFinished = (!fullPageMode && hasViewport) || fullPageLoadFinished

    fun exportAndFinish(share: Boolean) {
        val bmp = activeBitmap ?: return
        val targetPage = page ?: return
        if (exporting || bmp.isRecycled) return
        exporting = true
        val rect = if (fullPageMode) NormalizedCropRect.FullPage else cropRect
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                val cropped = cropBitmapSafely(bmp, rect) ?: return@withContext null
                try {
                    ByteArrayOutputStream().use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                } finally {
                    if (cropped !== bmp && !cropped.isRecycled) {
                        cropped.recycle()
                    }
                }
            }
            exporting = false
            if (bytes != null) {
                if (share) onSharePng(targetPage, bytes) else onSavePng(targetPage, bytes)
            }
            onDismiss()
        }
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
                .background(Color.Black.copy(alpha = 0.92f))
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Stage displaying fitted image + interactive crop box
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = activeBitmap
                    val img = activeDisplayBitmap
                    if (!loadFinished) {
                        ProgressRing(0f, stringResource(MR.strings.tsuzuki_reader_loading_page), Modifier.size(32.dp))
                    } else if (bmp == null || img == null) {
                        TsuzukiText(
                            stringResource(MR.strings.tsuzuki_reader_page_unavailable),
                            style = TsuzukiTheme.typography.callout,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    } else {
                        val density = LocalDensity.current
                        val containerW = with(density) { maxWidth.toPx() }
                        val containerH = with(density) { maxHeight.toPx() }
                        val fitScale = min(containerW / bmp.width.coerceAtLeast(1), containerH / bmp.height.coerceAtLeast(1))
                        val fittedW = (bmp.width * fitScale).coerceAtLeast(1f)
                        val fittedH = (bmp.height * fitScale).coerceAtLeast(1f)
                        val fittedWDp = with(density) { fittedW.toDp() }
                        val fittedHDp = with(density) { fittedH.toDp() }
                        val hitRadiusPx = with(density) { 28.dp.toPx() }
                        val currentCropRect by rememberUpdatedState(cropRect)

                        val activeRect = if (fullPageMode) NormalizedCropRect.FullPage else cropRect
                        val pixelBounds = mapCropRectToPixels(activeRect, bmp.width, bmp.height)

                        Box(
                            Modifier
                                .size(fittedWDp, fittedHDp)
                                .then(
                                    if (!fullPageMode) {
                                        Modifier.pointerInput(fittedW, fittedH) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                val handle = hitTestCropHandle(
                                                    touchX = down.position.x,
                                                    touchY = down.position.y,
                                                    rect = currentCropRect,
                                                    containerWidth = fittedW,
                                                    containerHeight = fittedH,
                                                    hitRadiusPx = hitRadiusPx,
                                                ) ?: return@awaitEachGesture
                                                down.consume()
                                                var prevPos = down.position
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) {
                                                        change.consume()
                                                        break
                                                    }
                                                    val delta = change.position - prevPos
                                                    prevPos = change.position
                                                    change.consume()
                                                    if (delta != Offset.Zero) {
                                                        cropRect = dragCropRect(
                                                            currentCropRect,
                                                            handle,
                                                            delta.x / fittedW,
                                                            delta.y / fittedH,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Image(
                                bitmap = img,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize(),
                            )

                            val boxLeftPx = activeRect.left * fittedW
                            val boxTopPx = activeRect.top * fittedH
                            val boxWidthPx = activeRect.width * fittedW
                            val boxHeightPx = activeRect.height * fittedH

                            // Outer dim mask outside crop bounds
                            if (!fullPageMode) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val maskColor = Color.Black.copy(alpha = 0.56f)
                                    if (boxTopPx > 0f) {
                                        drawRect(maskColor, Offset.Zero, Size(fittedW, boxTopPx))
                                    }
                                    val bottomTop = boxTopPx + boxHeightPx
                                    if (bottomTop < fittedH) {
                                        drawRect(maskColor, Offset(0f, bottomTop), Size(fittedW, fittedH - bottomTop))
                                    }
                                    if (boxLeftPx > 0f) {
                                        drawRect(maskColor, Offset(0f, boxTopPx), Size(boxLeftPx, boxHeightPx))
                                    }
                                    val rightLeft = boxLeftPx + boxWidthPx
                                    if (rightLeft < fittedW) {
                                        drawRect(maskColor, Offset(rightLeft, boxTopPx), Size(fittedW - rightLeft, boxHeightPx))
                                    }
                                }
                            }

                            // Non-interactive visual crop frame & handle indicators
                            val frameWDp = with(density) { boxWidthPx.toDp() }
                            val frameHDp = with(density) { boxHeightPx.toDp() }
                            Box(
                                Modifier
                                    .offset { IntOffset(boxLeftPx.roundToInt(), boxTopPx.roundToInt()) }
                                    .size(frameWDp, frameHDp)
                                    .border(2.dp, Color.White, RoundedCornerShape(10.dp)),
                            ) {
                                // Live pixel dimension chip
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.78f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    TsuzukiText(
                                        "${pixelBounds.width} × ${pixelBounds.height}",
                                        style = TsuzukiTheme.typography.caption2.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = Color.White,
                                    )
                                }

                                if (!fullPageMode) {
                                    CropHorizontalEdgePill(Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                                    CropHorizontalEdgePill(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                                    CropVerticalEdgePill(Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                                    CropVerticalEdgePill(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
                                    CropCornerDot(Modifier.align(Alignment.TopStart))
                                    CropCornerDot(Modifier.align(Alignment.TopEnd))
                                    CropCornerDot(Modifier.align(Alignment.BottomStart))
                                    CropCornerDot(Modifier.align(Alignment.BottomEnd))
                                }
                            }
                        }
                    }
                }

                // Bottom action bar: Kasane SegmentedControl (Crop / Full page) + TsuzukiButton actions
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    GlassSurface(glass, Modifier.fillMaxWidth(), shape = RoundedCornerShape(TsuzukiCorners.menu)) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SegmentedControl(
                                options = listOf(
                                    stringResource(MR.strings.tsuzuki_reader_crop),
                                    stringResource(MR.strings.tsuzuki_reader_full_page),
                                ),
                                selectedIndex = if (fullPageMode) 1 else 0,
                                onSelect = { idx -> fullPageMode = idx == 1 },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TsuzukiButton(
                                    label = stringResource(MR.strings.action_cancel),
                                    onClick = onDismiss,
                                    size = TsuzukiButtonSize.Sm,
                                    variant = TsuzukiButtonVariant.Soft,
                                    minWidth = 0.dp,
                                )
                                Spacer(Modifier.weight(1f))
                                TsuzukiButton(
                                    label = null,
                                    onClick = { exportAndFinish(share = true) },
                                    enabled = activeBitmap != null && !exporting,
                                    size = TsuzukiButtonSize.Sm,
                                    variant = TsuzukiButtonVariant.Tonal,
                                    icon = {
                                        Icon(
                                            TsuzukiIcons.Share,
                                            stringResource(MR.strings.action_share),
                                            Modifier.size(18.dp),
                                        )
                                    },
                                    minWidth = 44.dp,
                                )
                                TsuzukiButton(
                                    label = stringResource(MR.strings.action_save),
                                    onClick = { exportAndFinish(share = false) },
                                    enabled = activeBitmap != null && !exporting,
                                    size = TsuzukiButtonSize.Sm,
                                    variant = TsuzukiButtonVariant.Filled,
                                    minWidth = 0.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropHorizontalEdgePill(modifier: Modifier) {
    Box(
        modifier
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
}

@Composable
private fun CropVerticalEdgePill(modifier: Modifier) {
    Box(
        modifier
            .size(width = 4.dp, height = 36.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
}

@Composable
private fun CropCornerDot(modifier: Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
}
