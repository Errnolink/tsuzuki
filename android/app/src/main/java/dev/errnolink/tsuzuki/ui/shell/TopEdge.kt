package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassBand
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.LocalGlassTintStrength
import dev.errnolink.tsuzuki.designsystem.bandBlurEnabled
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

fun scrolledUnder(firstVisibleIndex: Int, firstVisibleOffset: Int, thresholdPx: Float): Boolean =
    firstVisibleIndex > 0 || firstVisibleOffset > thresholdPx

/**
 * Pure (TOP-02): how much the top band should rise as the list first scrolls under the row — 0 at
 * rest, 1 after [rampPx] of offset (the fold distance), so the band tracks the scroll instead of
 * flipping a boolean at one pixel and chasing it with a tween.
 */
fun scrolledUnderFraction(firstVisibleIndex: Int, firstVisibleOffsetPx: Float, rampPx: Float): Float = when {
    firstVisibleIndex > 0 -> 1f
    rampPx <= 0f -> if (firstVisibleOffsetPx > 0f) 1f else 0f
    else -> (firstVisibleOffsetPx / rampPx).coerceIn(0f, 1f)
}

/** How far below the row the band's progressive blur and dim fade out. */
internal val BandFade = 44.dp

/**
 * Pure (TOP-01): the height the top-edge treatment must cover — the status bar, the row's body
 * (plus its 4 dp top and bottom padding) and the [BandFade] tail under it. `ShellScrollEdge` and
 * the fallback gradient use it so neither ends short of the band.
 */
fun topEdgeHeight(statusBar: Dp, rowBody: Dp): Dp = statusBar + rowBody + 8.dp + BandFade

/** The status-bar top inset as a composable value. */
@Composable
fun statusBarTopInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** N18: the dark gradient over the progressive blur — strongest at the screen top, gone just
 *  below the buttons, so content passing under them is dimmed as well as blurred. */
private val BandUnderBar = 0.96f to 0.92f
private val BandTail = listOf(
    0.25f to 0.72f,
    0.45f to 0.5f,
    0.62f to 0.3f,
    0.78f to 0.14f,
    0.9f to 0.05f,
    1f to 0f,
)

/**
 * The band behind the status bar and the top row on scrolled pages. With live glass it is a
 * progressive blur (iOS Photos): full blur over the row, fading to zero over [BandFade] below it,
 * plus the background-colour gradient for legibility and — N18 — a dark gradient that dims
 * content passing under the floating buttons. Where glass is off (opaque fallback, no haze
 * source) the colour gradient carries the same dim.
 *
 * TOP-02: [edgeAlpha] and [band] are progress providers read in draw, so a scrolling list moves
 * the band without recomposing the row; [motionFraction], when a transition is in flight, drives
 * the glass input scale continuously instead of a boolean snap (WP6.6).
 */
@Composable
fun TopEdge(
    edgeAlpha: () -> Float,
    band: () -> Float,
    glass: GlassContext?,
    modifier: Modifier = Modifier,
    motionFraction: (() -> Float)? = null,
) {
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val edgeBrush = remember(colors.background) {
        Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.96f), Color.Transparent))
    }
    val bandFadePx = with(density) { BandFade.toPx() }
    var rowPx by remember { mutableIntStateOf(0) }
    val blur = bandBlurEnabled(glass)
    val tintStrength = LocalGlassTintStrength.current
    // The shell's top blur is locked to the progressive band; the temporary picker is gone.
    val extraHeightPx = bandFadePx
    val fadeFromPxProvider: () -> Float = remember { { rowPx * 0.72f } }
    val fadeToPxProvider: () -> Float = remember(bandFadePx) {
        { rowPx + bandFadePx }
    }
    val bgTintTop = if (colors.isDark) 0.30f + 0.24f * tintStrength else 0.38f + 0.24f * tintStrength
    val dimAlpha = if (colors.isDark) 0.10f else 0f
    // The composition gate flips only when the band appears or vanishes; every per-frame value is
    // read inside draw lambdas. Keeping GlassBand mounted (inactive at rest) pre-attaches the probe
    // and avoids a 1-frame Haze→Kasane swap flash when scrolling starts.
    val bandActive = remember(band) { androidx.compose.runtime.derivedStateOf { band() > 0.001f } }.value
    if (blur) {
        Box(
            modifier.onSizeChanged { if (it.height > 0 && rowPx != it.height) rowPx = it.height },
        ) {
            GlassBand(
                context = glass,
                active = bandActive,
                backgroundTintTop = bgTintTop,
                fadeFromPxProvider = fadeFromPxProvider,
                fadeToPxProvider = fadeToPxProvider,
                motionFraction = motionFraction,
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        if (rowPx != constraints.maxHeight && constraints.maxHeight > 0) {
                            rowPx = constraints.maxHeight
                        }
                        val h = (constraints.maxHeight + extraHeightPx).toInt()
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth.coerceAtLeast(1), h.coerceAtLeast(1)),
                        )
                        layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = band().coerceIn(0f, 1f) },
            )
            // N18: the dim rides the same extended node, fading with the band.
            if (bandActive && dimAlpha > 0f) {
                Spacer(
                    Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val h = (constraints.maxHeight + extraHeightPx).toInt()
                            val placeable = measurable.measure(
                                Constraints.fixed(constraints.maxWidth.coerceAtLeast(1), h.coerceAtLeast(1)),
                            )
                            layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                        }
                        .drawWithCache {
                            val dim = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = dimAlpha),
                                1f to Color.Transparent,
                            )
                            onDrawBehind { drawRect(brush = dim, alpha = band().coerceIn(0f, 1f)) }
                        },
                )
            }
            Spacer(
                Modifier.matchParentSize().drawWithCache {
                    onDrawBehind {
                        val b = band().coerceIn(0f, 1f)
                        val edge = edgeAlpha().coerceIn(0f, 1f)
                        if (b < 1f && edge > 0f) drawRect(brush = edgeBrush, alpha = (1f - b) * edge)
                    }
                },
            )
        }
        return
    }
    Spacer(
        modifier.drawWithCache {
            val total = size.height + bandFadePx
            val bar = (size.height / total).coerceIn(0f, 1f)
            val bandBrush = Brush.verticalGradient(
                *(
                    listOf(0f to BandUnderBar.first, bar to BandUnderBar.second) +
                        BandTail.map { (at, alpha) -> (bar + (1f - bar) * at) to alpha }
                    ).map { (at, alpha) -> at to colors.background.copy(alpha = alpha) }.toTypedArray(),
                startY = 0f,
                endY = total,
            )
            val dim = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = dimAlpha),
                bar to Color.Black.copy(alpha = dimAlpha * 0.4f),
                1f to Color.Transparent,
            )
            onDrawBehind {
                val b = band().coerceIn(0f, 1f)
                val edge = edgeAlpha().coerceIn(0f, 1f)
                if (b < 1f && edge > 0f) drawRect(brush = edgeBrush, alpha = (1f - b) * edge)
                if (b > 0f) {
                    drawRect(brush = bandBrush, size = Size(size.width, total), alpha = b)
                    if (dimAlpha > 0f) drawRect(brush = dim, size = Size(size.width, total), alpha = b)
                }
            }
        },
    )
}
