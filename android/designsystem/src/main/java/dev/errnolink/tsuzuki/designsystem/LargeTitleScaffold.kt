package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.blur
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

@Composable
fun rememberLargeTitleCollapse(state: LazyListState, enabled: Boolean): State<Float> {
    val motion = TsuzukiTheme.motion
    val collapsed = remember(state, enabled) {
        derivedStateOf {
            if (!enabled) return@derivedStateOf true
            val title = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == LargeTitleKey }
            headerScrolledAway(title?.offset, title?.size ?: 0, state.firstVisibleItemIndex, 0)
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed.value) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else tween(M3Motion.Short4, easing = M3Motion.Standard),
        label = "large-title-collapse",
    )
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
    content: LazyListScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val motion = TsuzukiTheme.motion
    var bottomHeight by remember { mutableIntStateOf(0) }
    val topBodyDp = remember { mutableFloatStateOf(TsuzukiTopBar.bodyHeightDp) }
    if (TsuzukiTopBar.bodyHeightDp > topBodyDp.floatValue) {
        topBodyDp.floatValue = TsuzukiTopBar.bodyHeightDp
    }
    val collapseProgress = collapse.value
    Box(
        modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).statusBarsPadding(),
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().floatingNavigationScroll(glassContext).glassSource(glassContext),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            flingBehavior = tsuzukiFlingBehavior(),
            contentPadding = PaddingValues(
                top = topBodyDp.floatValue.dp,
                bottom = max(with(density) { bottomHeight.toDp().value }, bottomPadding.value).dp + TsuzukiSpacing.section,
            ),
        ) {
            if (largeTitles) {
                item(key = LargeTitleKey) {
                    val fade = M3Motion.fadeThroughOut(collapseProgress)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TsuzukiSpacing.gutter, vertical = TsuzukiSpacing.small)
                            .graphicsLayer {
                                alpha = fade
                                translationY = with(density) { -8.dp.toPx() } * collapseProgress
                            }
                            .then(
                                if (motion.reduced) {
                                    Modifier
                                } else {
                                    Modifier.blur(LargeTitleBlur * (1f - fade))
                                },
                            ),
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
