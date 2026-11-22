// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/WebtoonPageItem.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import coil3.transform.Transformation
import dev.errnolink.tsuzuki.reader.engine.decode.ColorEInk16Bit
import dev.errnolink.tsuzuki.reader.engine.decode.CropBordersTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.RotateWidePageTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.SplitDoublePageVerticalTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.chooseSmartBackgroundColor
import dev.errnolink.tsuzuki.reader.engine.decode.readerPageSizeLimits
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPageSplit
import eu.kanade.tachiyomi.source.model.Page
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import logcat.logcat

/** Strongly typed target for webtoon rendering to avoid untyped Any? smuggling. */
sealed interface WebtoonImageTarget {
    data class Page(val page: ReaderPage) : WebtoonImageTarget

    data class Slice(val split: ReaderPageSplit) : WebtoonImageTarget
}

@Composable
fun WebtoonPageItem(
    page: ReaderPage,
    backgroundColor: Color,
    cropBorders: Boolean = false,
    colorFilter: ColorFilter? = null,
    eInkMode: Boolean = false,
    automaticBackground: Boolean = false,
    dualPageSplit: Boolean = false,
    dualPageInvert: Boolean = false,
    dualPageRotate: Boolean = false,
    dualPageRotateReverse: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    tapClaim: TapNavigationClaim? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(page) {
        if (page.status == Page.State.Queue) {
            page.chapter.pageLoader?.loadPage(page)
        }
    }

    val pageStatus by page.statusFlow.collectAsStateWithLifecycle(Page.State.Queue)
    val pageProgress by page.progressFlow.collectAsStateWithLifecycle(0)

    WebtoonPageContent(
        page = page,
        initialRatio = page.aspectRatio,
        onRatioCalculated = { ratio, _ -> page.aspectRatio = ratio },
        target = WebtoonImageTarget.Page(page),
        pageStatus = pageStatus,
        pageProgress = pageProgress,
        backgroundColor = backgroundColor,
        cropBorders = cropBorders,
        colorFilter = colorFilter,
        eInkMode = eInkMode,
        automaticBackground = automaticBackground,
        dualPageSplit = dualPageSplit,
        dualPageInvert = dualPageInvert,
        dualPageRotate = dualPageRotate,
        dualPageRotateReverse = dualPageRotateReverse,
        onLongClick = onLongClick,
        tapClaim = tapClaim,
        modifier = modifier,
    )
}

@Composable
fun WebtoonPageItem(
    split: ReaderPageSplit,
    backgroundColor: Color,
    cropBorders: Boolean = false,
    colorFilter: ColorFilter? = null,
    eInkMode: Boolean = false,
    automaticBackground: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    tapClaim: TapNavigationClaim? = null,
    modifier: Modifier = Modifier,
) {
    val page = split.page
    LaunchedEffect(page) {
        if (page.status == Page.State.Queue) {
            page.chapter.pageLoader?.loadPage(page)
        }
    }

    val pageStatus by page.statusFlow.collectAsStateWithLifecycle(Page.State.Queue)
    val pageProgress by page.progressFlow.collectAsStateWithLifecycle(0)

    WebtoonPageContent(
        page = page,
        initialRatio = split.aspectRatio,
        onRatioCalculated = { ratio, height ->
            split.aspectRatio = ratio
            split.displayedHeight = height
        },
        target = WebtoonImageTarget.Slice(split),
        pageStatus = pageStatus,
        pageProgress = pageProgress,
        backgroundColor = backgroundColor,
        // Sliced vertical tiles must NOT be independently cropped horizontally,
        // otherwise differing crop amounts across slices would cause horizontal step/seam
        // discontinuities when scaled to fillMaxWidth.
        cropBorders = false,
        colorFilter = colorFilter,
        eInkMode = eInkMode,
        automaticBackground = automaticBackground,
        dualPageSplit = false,
        dualPageInvert = false,
        dualPageRotate = false,
        dualPageRotateReverse = false,
        onLongClick = onLongClick,
        tapClaim = tapClaim,
        modifier = modifier,
    )
}

@Composable
private fun WebtoonPageContent(
    page: ReaderPage,
    initialRatio: Float,
    onRatioCalculated: (Float, Int) -> Unit,
    target: WebtoonImageTarget?,
    pageStatus: Page.State,
    pageProgress: Int,
    backgroundColor: Color,
    cropBorders: Boolean,
    colorFilter: ColorFilter? = null,
    eInkMode: Boolean = false,
    automaticBackground: Boolean = false,
    dualPageSplit: Boolean = false,
    dualPageInvert: Boolean = false,
    dualPageRotate: Boolean = false,
    dualPageRotateReverse: Boolean = false,
    onLongClick: (() -> Unit)?,
    tapClaim: TapNavigationClaim?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val retryGeneration by page.retryGenerationFlow.collectAsStateWithLifecycle()
    var loadError by remember(target, retryGeneration) { mutableStateOf(false) }
    var loadErrorMessage by remember(target, retryGeneration) { mutableStateOf<String?>(null) }
    val isError = pageStatus is Page.State.Error || loadError
    var intrinsicRatio by remember(target, cropBorders, dualPageSplit, dualPageRotate) { mutableFloatStateOf(initialRatio) }
    var smartBackgroundColor by remember(target, automaticBackground, backgroundColor) { mutableStateOf(backgroundColor) }

    // Every slice of the page collects its retry generation, so all of them load again.
    val onRetry: () -> Unit = {
        page.retry()
        page.chapter.pageLoader?.retryPage(page)
    }

    val model =
        remember(
            target,
            cropBorders,
            eInkMode,
            dualPageSplit,
            dualPageInvert,
            dualPageRotate,
            dualPageRotateReverse,
        ) {
            val modelData =
                when (target) {
                    is WebtoonImageTarget.Page -> target.page
                    is WebtoonImageTarget.Slice -> target.split
                    null -> null
                }
            ImageRequest.Builder(context)
                .data(modelData)
                .readerPageSizeLimits(GlLimits.maxTextureSize)
                .precision(Precision.EXACT)
                .crossfade(true)
                .apply {
                    val transforms = mutableListOf<Transformation>()
                    if (cropBorders) {
                        transforms.add(CropBordersTransformation(cropTopBottom = false))
                    }
                    if (target is WebtoonImageTarget.Page) {
                        if (dualPageRotate) {
                            transforms.add(
                                RotateWidePageTransformation(
                                    rotateWide = true,
                                    reverse = dualPageRotateReverse,
                                )
                            )
                        } else if (dualPageSplit) {
                            transforms.add(SplitDoublePageVerticalTransformation(invert = dualPageInvert))
                        }
                    }
                    if (eInkMode) {
                        transforms.add(ColorEInk16Bit())
                    }
                    if (transforms.isNotEmpty()) {
                        transformations(transforms)
                    }
                }
                .build()
        }

    val sizeModifier =
        if (intrinsicRatio > 0f) {
            Modifier.aspectRatio(intrinsicRatio)
        } else {
            Modifier.heightIn(min = 320.dp)
        }

    val gestureModifier =
        if (onLongClick != null) {
            Modifier.pointerInput(page, onLongClick) {
                val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val downPos = down.position
                    var triggered = false
                    try {
                        withTimeout(longPressTimeout) {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val distance =
                                    hypot(
                                        (change.position.x - downPos.x).toDouble(),
                                        (change.position.y - downPos.y).toDouble(),
                                    )
                                if (distance > touchSlop) break
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        onLongClick()
                        triggered = true
                    }
                    if (triggered) {
                        down.consume()
                    }
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(sizeModifier)
                .background(smartBackgroundColor)
                .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        // A retry does not change the request, and AsyncImage loads again only for a changed
        // request, so each retry gets a new AsyncImage.
        key(retryGeneration) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                filterQuality = FilterQuality.High,
                colorFilter = colorFilter,
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (intrinsicRatio > 0f) Modifier.aspectRatio(intrinsicRatio)
                            else Modifier
                        ),
                onSuccess = { state ->
                    loadError = false
                    val img = state.result.image
                    if (img.width > 0 && img.height > 0) {
                        val ratio = img.width.toFloat() / img.height.toFloat()
                        intrinsicRatio = ratio
                        onRatioCalculated(ratio, img.height)
                    }
                    if (automaticBackground && img is BitmapImage) {
                        coroutineScope.launch {
                            smartBackgroundColor =
                                withContext(Dispatchers.Default) {
                                    chooseSmartBackgroundColor(img.bitmap, backgroundColor)
                                }
                        }
                    }
                },
                onError = { state ->
                    logcat(LogPriority.ERROR) {
                        "Failed to load webtoon image for page ${page.number}"
                    }
                    loadErrorMessage =
                        state.result.throwable.message
                            ?: state.result.throwable.javaClass.simpleName
                    loadError = true
                },
            )
        }

        ReaderPageLoadingOverlay(status = pageStatus, progress = pageProgress)

        ReaderPageErrorOverlay(
            visible = isError,
            onRetry = onRetry,
            message =
                (pageStatus as? Page.State.Error)?.error?.message ?: loadErrorMessage,
            tapClaim = tapClaim,
        )
    }
}
