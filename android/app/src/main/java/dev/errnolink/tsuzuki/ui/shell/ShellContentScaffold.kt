package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar
import dev.errnolink.tsuzuki.designsystem.floatingNavigationScroll
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.components.AppBar

@Composable
fun ShellContentScaffold(
    title: String,
    navigateUp: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: List<ShellTopAction> = emptyList(),
    search: TopBarSearch? = null,
    listState: LazyListState? = null,
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
    val scrolledUnderChrome = if (listState == null) {
        false
    } else {
        remember(listState) {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            }
        }.value
    }
    val edgeAlpha by animateFloatAsState(
        targetValue = if (scrolledUnderChrome) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else tween(motion.chromeFadeMillis),
        label = "Shell scroll edge",
    )
    val glass: GlassContext = LocalShellGlass.current ?: rememberGlassContext()
    DeclareShellTop(title = title, glass = glass, navigateUp = navigateUp, actions = actions, search = search)
    Box(Modifier.fillMaxSize().background(colors.background).floatingNavigationScroll(glass)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(topBodyDp.floatValue.dp))
            header()
            Box(Modifier.weight(1f).glassSource(glass)) {
                content(PaddingValues(top = 8.dp, bottom = clearance.value + 24.dp))
                ShellSnackbar(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance.value + 12.dp))
            }
        }
        ShellScrollEdge(
            edgeAlpha,
            Modifier
                .fillMaxWidth()
                .height(topBodyDp.floatValue.dp + 16.dp),
        )
    }
}

private val OverflowPlaceholder: ImageVector = Icons.Outlined.MoreHoriz

fun shellToolbarActions(actions: List<AppBar.AppBarAction>): List<ShellTopAction> {
    val overflow = actions.filterIsInstance<AppBar.OverflowAction>()
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
                    label = "More actions",
                    icon = TsuzukiIcons.Ellipsis,
                    onClick = {},
                    menuTitle = "Actions",
                    menu = overflow.map { PullDownAction(it.title, onClick = it.onClick) },
                ),
            )
        }
    }
}
