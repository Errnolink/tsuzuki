package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.components.AppBar
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR

@Composable
fun TabbedScreen(
    titleRes: StringResource,
    tabs: ImmutableList<TabContent>,
    state: PagerState = rememberPagerState { tabs.size },
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    feedScreenModel: FeedScreenModel,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    onGlobalSearch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    LaunchedEffect(state) {
        var lastSettled = state.currentPage
        snapshotFlow { state.settledPage }.collect { page ->
            if (page != lastSettled) {
                haptics.select()
                lastSettled = page
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val feedState by feedScreenModel.state.collectAsState()
    val selection by bulkFavoriteScreenModel.state.collectAsState()
    var globalQuery by rememberSaveable { mutableStateOf("") }
    val tab = tabs[state.currentPage.coerceIn(tabs.indices)]
    val search: TopBarSearch? = if (selection.selectionMode) {
        null
    } else {
        rememberTopBarSearch(
            query = when {
                tab.onSearchQueryChange != null -> tab.searchQuery?.invoke().orEmpty()
                tab.searchEnabled -> searchQuery.orEmpty()
                else -> globalQuery
            },
            onQueryChange = { query ->
                when {
                    tab.onSearchQueryChange != null -> tab.onSearchQueryChange!!(query)
                    tab.searchEnabled -> onChangeSearchQuery(query)
                    else -> globalQuery = query
                }
            },
            placeholder = tab.searchPlaceholder
                ?: if (tab.searchEnabled) "Search extensions" else "Title or MangaDex link",
            onSearch = {
                if (tab.onSearchQueryChange == null && !tab.searchEnabled && globalQuery.isNotBlank()) onGlobalSearch(globalQuery)
            },
            onClose = {
                when {
                    tab.onSearchQueryChange != null -> tab.onSearchQueryChange!!(null)
                    tab.searchEnabled -> onChangeSearchQuery(null)
                    else -> globalQuery = ""
                }
            },
            trailing = tab.searchTrailing,
        )
    }
    LaunchedEffect(selection.selectionMode) {
        if (selection.selectionMode) {
            search?.state?.collapse()
            search?.onClose?.invoke()
        }
    }
    ShellContentScaffold(
        title = if (selection.selectionMode) "${selection.selection.size} selected" else stringResource(titleRes),
        snackbar = snackbar,
        actions = if (selection.selectionMode) {
            listOf(
                ShellTopAction(
                    stringResource(MR.strings.action_select_all),
                    TsuzukiIcons.ListChecks,
                    { feedState.items.orEmpty().flatMap { it.results.orEmpty() }.forEach(bulkFavoriteScreenModel::select) },
                ),
                ShellTopAction(
                    stringResource(MR.strings.action_select_inverse),
                    TsuzukiIcons.ArrowUpDown,
                    { bulkFavoriteScreenModel.reverseSelection(feedState.items.orEmpty().flatMap { it.results.orEmpty() }) },
                ),
                ShellTopAction(stringResource(MR.strings.action_close), TsuzukiIcons.X, bulkFavoriteScreenModel::toggleSelectionMode),
            )
        } else {
            buildList {
                add(
                    ShellTopAction(
                        label = tab.searchPlaceholder
                            ?: stringResource(MR.strings.action_search),
                        icon = TsuzukiIcons.Search,
                        onClick = { search?.state?.expand() },
                    ),
                )
                addAll(shellToolbarActions(tab.actions))
            }
        },
        search = search,
        header = {
            AnimatedContent(
                targetState = selection.selectionMode,
                transitionSpec = { selectionSwapTransform(motion.reduced) },
                label = "SelectionHeaderSwap",
            ) { selectionMode ->
                if (selectionMode) {
                    PillButton("Add to library", bulkFavoriteScreenModel::addFavorite, Modifier.padding(horizontal = 20.dp))
                } else {
                    SegmentedControl(
                        options = tabs.map { stringResource(it.titleRes) + (it.badgeNumber?.takeIf { count -> count > 0 }?.let { count -> " ($count)" } ?: "") },
                        selectedIndex = state.currentPage,
                        onSelect = { target ->
                            scope.launch {
                                state.lateralScrollTo(target, motion.reduced)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
            tabs[page].content(padding, snackbar)
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val searchPlaceholder: String? = null,
    val searchQuery: (() -> String?)? = null,
    val onSearchQueryChange: ((String?) -> Unit)? = null,
    val searchTrailing: (@Composable RowScope.() -> Unit)? = null,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)

internal fun selectionSwapTransform(reduced: Boolean): ContentTransform =
    if (reduced) {
        ContentTransform(
            fadeIn(M3Motion.reducedFade()),
            fadeOut(M3Motion.reducedFade()),
            0f,
            SizeTransform(clip = false),
        )
    } else {
        ContentTransform(
            fadeIn(tween(130, delayMillis = 70, easing = M3Motion.EmphasizedDecelerate)),
            fadeOut(tween(70, easing = M3Motion.EmphasizedAccelerate)),
            0f,
            SizeTransform(clip = false),
        )
    }
