// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/SplitPageLayout.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import dev.errnolink.tsuzuki.reader.engine.decode.ColorEInk16Bit
import dev.errnolink.tsuzuki.reader.engine.decode.readerPageSizeLimits
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import kotlin.math.roundToInt
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * Pure stateless Composable rendering a split double-page half (firstHalf = true or false). In
 * single-page mode with split pages, crops the designated 50% half based on reading direction.
 */
@Composable
fun SplitPageLayout(
    page: ReaderPage,
    config: PagerViewerConfigUiModel,
    zoomableState: ZoomableState,
    doubleClickToZoomListener: DoubleClickToZoomListener,
    onImageError: (ReaderPage, Throwable) -> Unit,
    onImageSuccess: (ReaderPage) -> Unit,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // In Japanese manga reading (RTL), page reads from right to left:
    // firstHalf = true is the right half (start of reading), firstHalf = false is the left half.
    // In LTR: firstHalf = true is the left half, firstHalf = false is the right half.
    val isFirstHalf = page.firstHalf == true
    val showLeftHalf = shouldShowLeftHalf(firstHalf = isFirstHalf, isRtl = config.isRtl)

    var imageSize by remember(page) { mutableStateOf<ComposeSize?>(null) }

    // Outer border cropping is deliberately omitted for split double pages because asymmetrical
    // margin trimming shifts the geometric center (img.width / 2f), displacing the central fold
    // line
    // and cutting through artwork or speech bubbles.
    val model =
        remember(page, config.eInkMode) {
            ImageRequest.Builder(context)
                .data(page)
                .readerPageSizeLimits(GlLimits.maxTextureSize)
                .precision(Precision.EXACT)
                .crossfade(true)
                .apply {
                    if (config.eInkMode) {
                        transformations(ColorEInk16Bit())
                    }
                }
                .build()
        }

    val hasCenterMargin =
        (config.centerMarginType and 1) != 0 && config.doublePages && !config.cropBorders
    val spineMarginModifier =
        if (hasCenterMargin) {
            if (showLeftHalf) Modifier.padding(end = 12.dp) else Modifier.padding(start = 12.dp)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(spineMarginModifier)
                .zoomable(
                    state = zoomableState,
                    onDoubleClick = doubleClickToZoomListener,
                ),
        contentAlignment = Alignment.Center,
    ) {
        SplitPageHalfLayout(
            showLeftHalf = showLeftHalf,
            contentScale = contentScale,
            imageSize = imageSize,
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = config.colorFilter,
                onError = { state -> onImageError(page, state.result.throwable) },
                onSuccess = { state ->
                    onImageSuccess(page)
                    val img = state.result.image
                    if (img.width > 0 && img.height > 0) {
                        imageSize = ComposeSize(img.width.toFloat(), img.height.toFloat())
                        zoomableState.contentScale = contentScale
                        zoomableState.setContentLocation(
                            ZoomableContentLocation.scaledInsideAndCenterAligned(
                                ComposeSize(img.width / 2f, img.height.toFloat())
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SplitPageHalfLayout(
    showLeftHalf: Boolean,
    contentScale: ContentScale,
    imageSize: ComposeSize?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.clipToBounds(),
        content = content,
    ) { measurables, constraints ->
        val img = imageSize
        if (
            img == null ||
                img.width <= 0f ||
                img.height <= 0f ||
                constraints.maxWidth <= 0 ||
                constraints.maxHeight <= 0
        ) {
            val placeable = measurables.first().measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val srcHalfSize = ComposeSize(img.width / 2f, img.height)
            val dstSize =
                ComposeSize(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val scaleFactor = contentScale.computeScaleFactor(srcHalfSize, dstSize)
            val halfWidth = (srcHalfSize.width * scaleFactor.scaleX).roundToInt()
            val halfHeight = (srcHalfSize.height * scaleFactor.scaleY).roundToInt()
            val fullWidth = halfWidth * 2

            val placeable = measurables.first().measure(Constraints.fixed(fullWidth, halfHeight))

            layout(halfWidth, halfHeight) {
                val x = if (showLeftHalf) 0 else -halfWidth
                placeable.place(x, 0)
            }
        }
    }
}

internal fun shouldShowLeftHalf(firstHalf: Boolean, isRtl: Boolean): Boolean =
    if (isRtl) !firstHalf else firstHalf
