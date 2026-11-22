package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar
import dev.errnolink.tsuzuki.designsystem.floatingNavigationScroll
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import eu.kanade.presentation.components.AppBar
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ShellContentScaffold(
    title: String,
    navigateUp: (() -> Unit)? = null,
    toasts: ToastHostState? = null,
    actions: List<ShellTopAction> = emptyList(),
    search: TopBarSearch? = null,
    listState: LazyListState? = null,
    capContent: Boolean = true,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val liveClearance = shellBottomPadding()
    val clearance = remember { mutableStateOf(liveClearance) }
    SideEffect {
        if (liveClearance > clearance.value) clearance.value = liveClearance
    }
    val topBodyDp = remember { mutableFloatStateOf(TsuzukiTopBar.bodyHeightDp) }
    if (TsuzukiTopBar.bodyHeightDp > topBodyDp.floatValue) {
        topBodyDp.floatValue = TsuzukiTopBar.bodyHeightDp
    }
    val widthClass = LocalWindowWidthClass.current
    val capWidth = when (widthClass) {
        WindowWidthClass.Medium -> 640.dp
        WindowWidthClass.Expanded -> 720.dp
        WindowWidthClass.Compact -> null
    }
    val density = LocalDensity.current
    // TOP-02: the scrolled-under progress is continuous over the first 24 dp of offset; the edge
    // and the band read it in draw, so no boolean flips and no tween chases the scroll.
    val scrolledUnderProvider = listState?.let { list ->
        remember(list, density) {
            val rampPx = with(density) { 24.dp.toPx() }
            derivedStateOf {
                scrolledUnderFraction(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset.toFloat(), rampPx)
            }
        }
    }
    val edgeAlpha = { scrolledUnderProvider?.value ?: 0f }
    val glass: GlassContext = LocalShellGlass.current ?: rememberGlassContext()
    DeclareShellTop(
        title = title,
        glass = glass,
        navigateUp = navigateUp,
        actions = actions,
        search = search,
        scrolledUnderFraction = scrolledUnderProvider?.let { provider -> { provider.value } },
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).floatingNavigationScroll(glass)) {
        val capInset = if (!capContent || capWidth == null) {
            0.dp
        } else {
            ((maxWidth - capWidth) / 2).coerceAtLeast(0.dp)
        }
        val statusTop = statusBarTopInset()
        var headerHeight by remember { mutableStateOf(0.dp) }
        // TOP-01: the scroll viewport fills the screen (the status-bar inset and the header ride
        // its content padding), so content scrolls under the band and blurs instead of being cut
        // at the container's top edge; the header overlays on the scaffold background it always
        // sat on.
        Box(Modifier.fillMaxSize().glassSource(glass)) {
            androidx.compose.runtime.CompositionLocalProvider(dev.errnolink.tsuzuki.designsystem.LocalGlassContext provides glass) {
                content(
                    PaddingValues(
                        top = statusTop + topBodyDp.floatValue.dp + 8.dp + headerHeight,
                        bottom = clearance.value + 24.dp,
                        start = capInset,
                        end = capInset,
                    ),
                )
            }
            ShellToasts(toasts, Modifier.align(Alignment.BottomCenter).statusToastPadding())
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            // The row's body height excludes its 4 dp top and bottom padding; the header starts below all of it.
            Spacer(Modifier.height(statusTop + topBodyDp.floatValue.dp + 8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
            ) {
                with(this@Column) { header() }
            }
        }
        // TOP-01: the edge covers the whole band — status bar, row body and the 44 dp fade tail.
        ShellScrollEdge(
            edgeAlpha,
            Modifier
                .fillMaxWidth()
                .height(topEdgeHeight(statusTop, topBodyDp.floatValue.dp)),
        )
    }
}

private val OverflowPlaceholder: ImageVector = TsuzukiIcons.Ellipsis

@Composable
fun shellToolbarActions(actions: List<AppBar.AppBarAction>): List<ShellTopAction> {
    val overflow = actions.filterIsInstance<AppBar.OverflowAction>()
    val moreActionsLabel = stringResource(MR.strings.tsuzuki_more_actions)
    val actionsLabel = stringResource(MR.strings.tsuzuki_actions)
    return buildList {
        actions.forEach { action ->
            when (action) {
                is AppBar.Action -> add(
                    ShellTopAction(
                        label = action.title,
                        icon = action.icon,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        active = action.iconTint != null,
                    ),
                )
                is AppBar.ActionCompose -> add(
                    ShellTopAction(
                        label = action.title,
                        icon = OverflowPlaceholder,
                        onClick = {},
                        content = action.content,
                    ),
                )
                is AppBar.OverflowAction -> Unit
            }
        }
        if (overflow.isNotEmpty()) {
            add(
                ShellTopAction(
                    label = moreActionsLabel,
                    icon = TsuzukiIcons.Ellipsis,
                    onClick = {},
                    menuTitle = actionsLabel,
                    menu = overflow.map { PullDownAction(it.title, onClick = it.onClick) },
                ),
            )
        }
    }
}
