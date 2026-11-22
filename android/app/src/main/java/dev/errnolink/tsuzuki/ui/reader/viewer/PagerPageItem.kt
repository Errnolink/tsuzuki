// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/PagerPageItem.kt (Neko #3486 #3491)
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.Transformation
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import dev.errnolink.tsuzuki.reader.engine.decode.ColorEInk16Bit
import dev.errnolink.tsuzuki.reader.engine.decode.CropBordersTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.RotateWidePageTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.WidePageCenterMarginTransformation
import dev.errnolink.tsuzuki.reader.engine.decode.chooseSmartBackgroundColor
import dev.errnolink.tsuzuki.reader.engine.decode.readerPageSizeLimits
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Pure stateless Composable rendering an individual paginated reader item (single page, paired
 * spread, or split double page). Completely decoupled from Service Locators and View hierarchies.
 */
@Composable
fun PagerPageItem(
    page: ReaderPage,
    config: PagerViewerConfigUiModel,
    extraPage: ReaderPage? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    val context = LocalContext.current

    // Trigger page loading
    LaunchedEffect(page) {
        if (page.status == Page.State.Queue) {
            page.chapter.pageLoader?.loadPage(page)
        }
    }
    LaunchedEffect(extraPage) {
        if (extraPage != null && extraPage.status == Page.State.Queue) {
            extraPage.chapter.pageLoader?.loadPage(extraPage)
        }
    }

    val pageStatus by page.statusFlow.collectAsStateWithLifecycle(Page.State.Queue)
    val pageProgress by page.progressFlow.collectAsStateWithLifecycle(0)

    val extraPageStatus by
        (extraPage?.statusFlow ?: emptyFlow()).collectAsStateWithLifecycle(Page.State.Ready)
    val extraPageProgress by (extraPage?.progressFlow ?: emptyFlow()).collectAsStateWithLifecycle(0)

    val retryGeneration by page.retryGenerationFlow.collectAsStateWithLifecycle()
    val loadErrors = remember(page, extraPage) { PagerImageLoadErrors() }
    val tapClaim = remember { TapNavigationClaim() }

    val isError =
        pageStatus is Page.State.Error ||
            (extraPage != null && extraPageStatus is Page.State.Error) ||
            loadErrors.hasError
    val isReady =
        pageStatus == Page.State.Ready && (extraPage == null || extraPageStatus == Page.State.Ready)

    val combinedStatus =
        when {
            isError -> Page.State.Error(IllegalStateException())
            isReady -> Page.State.Ready
            pageStatus == Page.State.DownloadImage ||
                extraPageStatus == Page.State.DownloadImage -> Page.State.DownloadImage
            pageStatus == Page.State.LoadPage || extraPageStatus == Page.State.LoadPage ->
                Page.State.LoadPage
            else -> Page.State.Queue
        }
    val combinedProgress =
        if (extraPage == null) pageProgress else (pageProgress + extraPageProgress) / 2

    val imageAlignment =
        remember(config.zoomStart, config.isRtl) {
            val zoomType =
                DoublePageLayoutPolicy.resolveZoomType(config.zoomStart, config.isRtl)

            Alignment { size, space, _ ->
                val x =
                    if (size.width > space.width) {
                        when (zoomType) {
                            DoublePageLayoutPolicy.ZoomType.Left -> 0
                            DoublePageLayoutPolicy.ZoomType.Right -> space.width - size.width
                            DoublePageLayoutPolicy.ZoomType.Center -> (space.width - size.width) / 2
                        }
                    } else {
                        (space.width - size.width) / 2
                    }

                val y =
                    if (size.height > space.height) {
                        0
                    } else {
                        (space.height - size.height) / 2
                    }

                IntOffset(x, y)
            }
        }

    val contentScale =
        remember(config.imageScaleType) {
            when (config.imageScaleType) {
                1 -> ContentScale.Fit
                2 -> ContentScale.FillBounds
                3 -> ContentScale.FillWidth
                4 -> ContentScale.FillHeight
                5 -> ContentScale.None
                6 -> SmartFitContentScale
                else -> ContentScale.Fit
            }
        }

    val onRetry: () -> Unit = {
        loadErrors.clear()
        page.retry()
        page.chapter.pageLoader?.retryPage(page)
        extraPage?.chapter?.pageLoader?.retryPage(extraPage)
    }

    val allowDoubleTapZoom =
        config.doubleTapZoomEnabled && !config.disableZoomIn && config.doubleTapAnimDuration > 0
    val doubleClickToZoomListener =
        remember(allowDoubleTapZoom) {
            if (allowDoubleTapZoom) {
                DoubleClickToZoomListener.cycle(maxZoomFactor = 2.5f)
            } else {
                DoubleClickToZoomListener { _, _ -> }
            }
        }

    var smartBackgroundColor by
        remember(page, extraPage, config.automaticBackground, config.backgroundColor) {
            mutableStateOf(config.backgroundColor)
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(smartBackgroundColor)
                .pagerTapNavigation(
                    config = config,
                    page = page,
                    extraPage = extraPage,
                    tapClaim = tapClaim,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val shouldRotateWide =
            RotateWidePageTransformation.shouldRotateForViewport(
                rotateWide = config.doublePageRotate,
                viewportWidth = viewportWidthPx,
                viewportHeight = viewportHeightPx,
            )

        var autoZoomApplied by
            rememberSaveable(
                page.chapter.chapter.id,
                page.index,
                constraints.maxWidth,
                constraints.maxHeight,
                shouldRotateWide,
                config.doublePageRotateReverse,
            ) {
                mutableStateOf(false)
            }

        val maxZoomFactor = if (config.disableZoomIn) 1f else 5f
        val zoomSpec = remember(maxZoomFactor) { ZoomSpec(maxZoomFactor = maxZoomFactor) }
        val zoomableState =
            key(constraints.maxWidth, constraints.maxHeight, maxZoomFactor) {
                rememberZoomableState(zoomSpec = zoomSpec)
            }

        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val panEpsilonPx = remember(density) { with(density) { 2.dp.toPx() } }
        val panDelegate =
            remember(
                zoomableState,
                viewportWidthPx,
                viewportHeightPx,
                config.animatedTransitions,
                config.doubleTapAnimDuration,
                panEpsilonPx,
            ) {
                ZoomablePanDelegate(
                    zoomableState = zoomableState,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    scope = coroutineScope,
                    animated = config.animatedTransitions,
                    animationDurationMillis = config.doubleTapAnimDuration,
                    panEpsilonPx = panEpsilonPx,
                )
            }

        if (config.navigateToPan) {
            DisposableEffect(panDelegate, isActive) {
                if (isActive) {
                    config.onActivePanDelegateChanged?.invoke(panDelegate, true)
                }
                onDispose {
                    if (isActive) {
                        config.onActivePanDelegateChanged?.invoke(panDelegate, false)
                    }
                }
            }
        }

        // A retry does not change the image requests, and the images load again only for a
        // changed request, so each retry gets new images.
        key(retryGeneration) {
            if (extraPage != null) {
                DoublePageLayout(
                    page = page,
                    extraPage = extraPage,
                    config = config,
                    zoomableState = zoomableState,
                    contentScale = contentScale,
                    doubleClickToZoomListener = doubleClickToZoomListener,
                    constraints = constraints,
                    isReady = isReady,
                    onImageError = loadErrors::onError,
                    onImageSuccess = loadErrors::onSuccess,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (page.firstHalf != null) {
                SplitPageLayout(
                    page = page,
                    config = config,
                    zoomableState = zoomableState,
                    doubleClickToZoomListener = doubleClickToZoomListener,
                    onImageError = loadErrors::onError,
                    onImageSuccess = loadErrors::onSuccess,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val imageState = rememberZoomableImageState(zoomableState)

                val singlePageZoomType =
                    remember(config.zoomStart, config.isRtl) {
                        DoublePageLayoutPolicy.resolveZoomType(config.zoomStart, config.isRtl)
                    }

                LaunchedEffect(
                    isReady,
                    config.landscapeZoom,
                    config.landscapeZoomScaleDouble,
                    config.imageScaleType,
                    config.disableZoomIn,
                    shouldRotateWide,
                ) {
                    if (
                        !autoZoomApplied &&
                            isReady &&
                            config.landscapeZoom &&
                            !config.disableZoomIn &&
                            config.imageScaleType == 1 &&
                            viewportWidthPx > 0f &&
                            viewportHeightPx > 0f
                    ) {
                        @Suppress("DEPRECATION")
                        val bounds =
                            withTimeoutOrNull(2000L) {
                                snapshotFlow { zoomableState.transformedContentBounds }
                                    .filter { !it.isEmpty }
                                    .first()
                            }

                        if (bounds != null) {
                            val isLandscape = bounds.width > bounds.height
                            if (isLandscape && bounds.height < viewportHeightPx) {
                                val targetScale =
                                    calculateLandscapeZoomScale(
                                        scaleDouble = config.landscapeZoomScaleDouble,
                                        boundsHeight = bounds.height,
                                        viewportHeight = viewportHeightPx,
                                    )
                                if (targetScale > 1.05f) {
                                    val centroid =
                                        when (singlePageZoomType) {
                                            DoublePageLayoutPolicy.ZoomType.Right ->
                                                Offset(viewportWidthPx, viewportHeightPx / 2f)
                                            DoublePageLayoutPolicy.ZoomType.Left ->
                                                Offset(0f, viewportHeightPx / 2f)
                                            DoublePageLayoutPolicy.ZoomType.Center ->
                                                Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                                        }
                                    zoomableState.zoomTo(
                                        zoomFactor = targetScale,
                                        centroid = centroid,
                                    )
                                }
                            }
                        }
                        autoZoomApplied = true
                    }
                }

                val model =
                    remember(
                        page,
                        loadErrors,
                        config.cropBorders,
                        shouldRotateWide,
                        config.doublePageRotateReverse,
                        config.centerMarginType,
                        config.eInkMode,
                        config.backgroundColor,
                    ) {
                        ImageRequest.Builder(context)
                            .data(page)
                            // ReaderPageImageSource runs this request as built. The caps come from
                            // readerPageSizeLimits, the same box and byte cap the tier-2 warm
                            // requests use, so a warmed page is drawn from the memory cache
                            // instead of decoded again (Neko #3491). Precision matches the warm
                            // request so the memory cache keys are equal.
                            .readerPageSizeLimits(GlLimits.maxTextureSize)
                            .scale(Scale.FIT)
                            .precision(Precision.EXACT)
                            .crossfade(true)
                            .listener(
                                onError = { _, result ->
                                    loadErrors.onError(page, result.throwable)
                                },
                                onSuccess = { _, _ -> loadErrors.onSuccess(page) },
                            )
                            .apply {
                                val transformations = mutableListOf<Transformation>()
                                if (config.cropBorders) {
                                    transformations.add(
                                        CropBordersTransformation(cropTopBottom = true)
                                    )
                                }
                                if (shouldRotateWide) {
                                    transformations.add(
                                        RotateWidePageTransformation(
                                            rotateWide = true,
                                            reverse = config.doublePageRotateReverse,
                                        )
                                    )
                                } else if ((config.centerMarginType and 2) != 0 && !config.cropBorders) {
                                    transformations.add(
                                        WidePageCenterMarginTransformation(
                                            marginPx = 48,
                                            gutterColor = config.backgroundColor.toArgb(),
                                        )
                                    )
                                }
                                if (config.eInkMode) {
                                    transformations.add(ColorEInk16Bit())
                                }
                                if (transformations.isNotEmpty()) {
                                    transformations(transformations)
                                }
                            }
                            .build()
                    }

                val imageSource =
                    remember(model, config.automaticBackground, config.backgroundColor) {
                        ReaderPageImageSource(
                            request = model,
                            imageLoader = context.imageLoader,
                            onBitmapLoaded =
                                if (config.automaticBackground) {
                                    { bitmap ->
                                        coroutineScope.launch {
                                            smartBackgroundColor =
                                                withContext(Dispatchers.Default) {
                                                    chooseSmartBackgroundColor(
                                                        bitmap,
                                                        config.backgroundColor,
                                                    )
                                                }
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }

                ZoomableImage(
                    image = imageSource,
                    contentDescription = null,
                    contentScale = contentScale,
                    alignment = imageAlignment,
                    state = imageState,
                    colorFilter = config.colorFilter,
                    onDoubleClick = doubleClickToZoomListener,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        ReaderPageLoadingOverlay(status = combinedStatus, progress = combinedProgress)

        ReaderPageErrorOverlay(
            visible = isError,
            onRetry = onRetry,
            message =
                (pageStatus as? Page.State.Error)?.error?.message
                    ?: (extraPageStatus as? Page.State.Error)?.error?.message
                    ?: loadErrors.message,
            tapClaim = tapClaim,
        )
    }
}

private object SmartFitContentScale : ContentScale {
    override fun computeScaleFactor(
        srcSize: ComposeSize,
        dstSize: ComposeSize,
    ): ScaleFactor {
        return if (
            srcSize.isSpecified && !srcSize.isEmpty() && dstSize.isSpecified && !dstSize.isEmpty()
        ) {
            val contentRatio = srcSize.width / srcSize.height
            val screenRatio = dstSize.width / dstSize.height
            if (contentRatio > screenRatio) {
                ContentScale.FillWidth.computeScaleFactor(srcSize, dstSize)
            } else {
                ContentScale.FillHeight.computeScaleFactor(srcSize, dstSize)
            }
        } else {
            ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
        }
    }
}

internal fun calculateLandscapeZoomScale(
    scaleDouble: Boolean,
    boundsHeight: Float,
    viewportHeight: Float,
): Float {
    if (scaleDouble) return 2f
    if (boundsHeight <= 0f || viewportHeight <= 0f) return 1f
    return (viewportHeight / boundsHeight).coerceIn(1f, 3f)
}
