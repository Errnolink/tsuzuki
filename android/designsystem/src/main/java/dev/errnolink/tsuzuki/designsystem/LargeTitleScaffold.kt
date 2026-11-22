package dev.errnolink.tsuzuki.designsystem
import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun largeTitlesEnabled(): Boolean =
    LocalDensity.current.fontScale < 1.5f

fun headerScrolledAway(headerTop: Int?, headerBottom: Int, firstVisibleIndex: Int, headerIndex: Int): Boolean =
    if (headerTop == null) firstVisibleIndex > headerIndex else headerTop + headerBottom <= 0

/**
 * Pure (TOP-02): the collapse fraction from how far the large-title item has scrolled. It ends
 * once only [collapsedAtPx] of the title remains and runs over [rangePx] before that; it never
 * starts before the list has moved (a title shorter than collapsedAt + range used to rest
 * partly collapsed: blurred large title, dim bar title, on an unscrolled page).
 * [titleOffsetPx] is the item's offset in the list (0 at rest, negative once scrolled).
 */
fun largeTitleCollapseFraction(titleOffsetPx: Float?, titleSizePx: Float, collapsedAtPx: Float, rangePx: Float): Float {
    if (titleOffsetPx == null) return 1f
    val travelled = 0f - titleOffsetPx
    val start = (titleSizePx - collapsedAtPx - rangePx).coerceAtLeast(0f)
    return ((travelled - start) / rangePx).coerceIn(0f, 1f)
}

@Composable
fun rememberLargeTitleCollapse(state: LazyListState, enabled: Boolean): State<Float> {
    val density = LocalDensity.current
    val collapsedAtPx = with(density) { 24.dp.toPx() }
    val rangePx = with(density) { 48.dp.toPx() }
    return remember(state, enabled, collapsedAtPx, rangePx) {
        derivedStateOf {
            if (!enabled) return@derivedStateOf 1f
            val title = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == LargeTitleKey }
            largeTitleCollapseFraction(title?.offset?.toFloat(), title?.size?.toFloat() ?: 0f, collapsedAtPx, rangePx)
        }
    }
}

private const val LargeTitleKey = "tsuzuki-large-title"

private val LargeTitleBlur = 10.dp

@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    glassContext: GlassContext = rememberGlassContext(),
    largeTitles: Boolean = largeTitlesEnabled(),
    collapse: State<Float> = rememberLargeTitleCollapse(state, largeTitles),
    header: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    bottomPadding: Dp = 0.dp,
    itemSpacing: Dp = TsuzukiSpacing.section,
    capContent: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val reduced = TsuzukiTheme.motion.reduced
    var bottomHeight by remember { mutableIntStateOf(0) }
    val topBodyDp = remember { mutableFloatStateOf(TsuzukiTopBar.bodyHeightDp) }
    if (TsuzukiTopBar.bodyHeightDp > topBodyDp.floatValue) {
        topBodyDp.floatValue = TsuzukiTopBar.bodyHeightDp
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassContext provides glassContext) {
    // TOP-01: the viewport fills the screen — no statusBarsPadding on the container — so rows
    // scroll under the band and get blurred instead of being cut at the status-bar line.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val capInset = if (!capContent) {
            0.dp
        } else {
            when (LocalWindowWidthClass.current) {
                WindowWidthClass.Medium -> ((maxWidth - 640.dp) / 2).coerceAtLeast(0.dp)
                WindowWidthClass.Expanded -> ((maxWidth - 720.dp) / 2).coerceAtLeast(0.dp)
                WindowWidthClass.Compact -> 0.dp
            }
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().floatingNavigationScroll(glassContext).glassSource(glassContext),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            flingBehavior = tsuzukiFlingBehavior(),
            contentPadding = PaddingValues(
                start = capInset,
                end = capInset,
                top = statusTop + topBodyDp.floatValue.dp,
                bottom = max(with(density) { bottomHeight.toDp().value }, bottomPadding.value).dp + TsuzukiSpacing.section,
            ),
        ) {
            if (largeTitles) {
                item(key = LargeTitleKey) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TsuzukiSpacing.gutter, vertical = TsuzukiSpacing.small)
                            .graphicsLayer {
                                // TOP-02: the collapse is read here in the layer block, so the
                                // scroll drives the title without recomposing the scaffold.
                                val collapseProgress = collapse.value
                                val fade = M3Motion.fadeThroughOut(collapseProgress)
                                alpha = fade
                                translationY = -8.dp.toPx() * collapseProgress
                                val hold = LargeTitleBlur.toPx() * (1f - fade)
                                renderEffect = if (!reduced && hold > 0.25f && Build.VERSION.SDK_INT >= 31) {
                                    androidx.compose.ui.graphics.BlurEffect(hold, hold)
                                } else {
                                    null
                                }
                            },
                    ) {
                        TsuzukiText(
                            text = title,
                            style = TsuzukiTheme.typography.largeTitle,
                            color = colors.text,
                        )
                    }
                }
            }
            if (header != null) {
                item(key = "tsuzuki-scaffold-header") { Box(Modifier.fillMaxWidth()) { header() } }
            }
            content()
        }
        Box(
            Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = it.height }
                .navigationBarsPadding()
                .then(if (bottomBar != null) Modifier.padding(horizontal = 20.dp, vertical = 8.dp) else Modifier),
        ) {
            bottomBar?.invoke()
        }
    }
    }
}
