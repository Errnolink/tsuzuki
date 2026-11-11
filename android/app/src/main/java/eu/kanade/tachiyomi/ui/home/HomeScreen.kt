package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.errnolink.tsuzuki.designsystem.*
import dev.errnolink.tsuzuki.ui.shell.LocalShellArrivals
import dev.errnolink.tsuzuki.ui.shell.LocalShellBottomPadding
import dev.errnolink.tsuzuki.ui.shell.LocalShellGlass
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import dev.errnolink.tsuzuki.ui.shell.TsuzukiTab
import dev.errnolink.tsuzuki.ui.shell.TsuzukiTabBar
import dev.errnolink.tsuzuki.ui.library.LocalShellTabScrollStates
import dev.errnolink.tsuzuki.ui.library.ShellTabScrollStates
import dev.errnolink.tsuzuki.ui.library.calculateActiveFilterCount
import dev.errnolink.tsuzuki.ui.shell.ShellSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavigationStyle
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object HomeScreen : Screen() {
    private fun readResolve(): Any = HomeScreen
    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()
    private val reselect = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reselectEvents = reselect.asSharedFlow()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val glass = rememberGlassContext()
        val arrivals = rememberCoverArrivalState()
        val density = LocalDensity.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val navigationStyle by uiPreferences.navigationStyle().collectAsState()
        val bottomBarLabels by uiPreferences.bottomBarLabels().collectAsState()
        val tabs = navigationTabs(navigationStyle)
        // Now Reading accessory (ShellBottomDock) is shelved: the Books-style dock below replaces it.
        val searchQuery by ShellSearch.query.collectAsState()
        var bottomHeight by remember { mutableIntStateOf(0) }
        val bottomVisible by produceState(true) {
            showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
        }
        val scrollStates = remember { ShellTabScrollStates() }
        TabNavigator(tab = LibraryTab, key = "HomeTabs") { tabNavigator ->
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalShellGlass provides glass,
                LocalShellArrivals provides arrivals,
                LocalShellBottomPadding provides if (bottomVisible) with(density) { bottomHeight.toDp() } else 0.dp,
                LocalShellTabScrollStates provides scrollStates,
            ) {
                Box(Modifier.fillMaxSize()) {
                    val reducedMotion = TsuzukiTheme.motion.reduced
                    val tabSwitch = updateTransition(tabNavigator.current, label = "tab-switch")
                    CompositionLocalProvider(
                        LocalNavigationMotion provides
                            (tabSwitch.currentState != tabSwitch.targetState || tabSwitch.isRunning),
                    ) {
                        tabSwitch.AnimatedContent(
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(M3Motion.reducedFade()) togetherWith fadeOut(M3Motion.reducedFade())
                                } else {
                                    val outMillis = (M3Motion.Medium2 * M3Motion.FadeThroughThreshold).toInt()
                                    val inMillis = M3Motion.Medium2 - outMillis
                                    val appear: FiniteAnimationSpec<Float> =
                                        tween(inMillis, delayMillis = outMillis, easing = M3Motion.EmphasizedDecelerate)
                                    fadeIn(appear) +
                                        scaleIn(appear, initialScale = M3Motion.FadeThroughScale) togetherWith
                                        fadeOut(tween(outMillis, easing = M3Motion.EmphasizedAccelerate))
                                }
                            },
                        ) { tab ->
                            CompositionLocalProvider(
                                dev.errnolink.tsuzuki.ui.shell.LocalShellTopActive provides (tab == tabSwitch.targetState),
                            ) {
                                tabNavigator.saveableState(key = "currentTab", tab) {
                                    tab.Content()
                                }
                            }
                        }
                    }
                    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
                    val filterUnread by libraryPreferences.filterUnread().collectAsState()
                    val filterStarted by libraryPreferences.filterStarted().collectAsState()
                    val filterDownloaded by libraryPreferences.filterDownloaded().collectAsState()
                    val filterBookmarked by libraryPreferences.filterBookmarked().collectAsState()
                    val filterCompleted by libraryPreferences.filterCompleted().collectAsState()
                    val filterMerged by libraryPreferences.filterMerged().collectAsState()
                    val filterMissing by libraryPreferences.filterMissingChapters().collectAsState()
                    val filterUnavailable by libraryPreferences.filterUnavailableChapters().collectAsState()
                    val filterActiveCount = remember(
                        filterUnread, filterStarted, filterDownloaded, filterBookmarked,
                        filterCompleted, filterMerged, filterMissing, filterUnavailable,
                    ) {
                        calculateActiveFilterCount(
                            listOf(
                                filterUnread, filterStarted, filterDownloaded, filterBookmarked,
                                filterCompleted, filterMerged, filterMissing, filterUnavailable,
                            ),
                        )
                    }
                    if (bottomVisible) {
                        val visible = if (navigationStyle == NavigationStyle.Simple) {
                            tabs
                        } else {
                            tabs.filter { it.isEnabled() }.let { if (it.size in 4..5) it else tabs }
                        }
                        val tabItems = visible.map { tab ->
                            val badgeCount = key(tab.key) { rememberTabBadgeCount(tab) }
                            TsuzukiTab(
                                key = tab.key,
                                title = tab.options.title,
                                icon = { dev.errnolink.tsuzuki.ui.shell.ShellTabIcon(tab, badgeCount = badgeCount) },
                                onClick = {
                                    if (tabNavigator.current.key == tab.key) {
                                        reselect.tryEmit(tab.key)
                                        if (tab == MoreTab) scope.launch { tab.onReselect(navigator) }
                                    } else {
                                        tabNavigator.current = tab
                                    }
                                },
                                badge = badgeCount > 0,
                            )
                        }
                        val selectedIndex = visible.indexOfFirst { it.key == tabNavigator.current.key }.coerceAtLeast(0)
                        Box(
                            Modifier.align(Alignment.BottomCenter)
                                .onSizeChanged { size -> bottomHeight = size.height }
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .widthIn(max = 540.dp),
                        ) {
                            TsuzukiTabBar(
                                modifier = Modifier.fillMaxWidth(),
                                tabs = tabItems,
                                selectedIndex = selectedIndex,
                                isInline = glass.navigationMinimized,
                                onExpand = { glass.expandNavigation() },
                                showLabels = bottomBarLabels,
                                filterActiveCount = filterActiveCount,
                                showFilterCircle = tabNavigator.current == LibraryTab,
                                onFilterClick = {
                                    if (tabNavigator.current != LibraryTab) {
                                        tabNavigator.current = LibraryTab
                                    }
                                    dev.errnolink.tsuzuki.ui.library.LibraryFilterSheetState.open()
                                },
                                glass = glass,
                            )
                        }
                    }
                    // KMK -->
                    dev.errnolink.tsuzuki.ui.shell.StatusPill(Modifier.align(Alignment.BottomCenter))
                    // KMK <--
                }
            }
            // Leaving Library/Browse (or opening another screen on top) closes the dock search.
            LaunchedEffect(tabNavigator.current.key) {
                if (tabNavigator.current != LibraryTab && tabNavigator.current != BrowseTab) ShellSearch.close()
            }
            BackHandler(enabled = tabNavigator.current != LibraryTab && searchQuery == null) { tabNavigator.current = LibraryTab }
            LaunchedEffect(tabs) {
                if (tabs.none { it.key == tabNavigator.current.key }) tabNavigator.current = LibraryTab
            }
            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = LibraryTab
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        openTabFeedSegment(it, navigationStyle)?.let(FeedTab::openSegment)
                        openTabBottomTab(it, navigationStyle)?.let { tab -> tabNavigator.current = tab }
                        if (it is Tab.Library && it.mangaIdToOpen != null) navigator.push(MangaScreen(it.mangaIdToOpen))
                        if (it is Tab.More) {
                            if (it.toDownloads) navigator.push(DownloadQueueScreen)
                            else if (it.toLibraryUpdateErrors) navigator.push(LibraryUpdateErrorScreen())
                        }
                    }
                }
            }
            LaunchedEffect(navigationStyle) {
                if (tabNavigator.current !in navigationTabs(navigationStyle)) {
                    tabNavigator.current = LibraryTab
                }
            }
        }
    }

    @Composable
    private fun rememberTabBadgeCount(tab: eu.kanade.presentation.util.Tab): Int {
        val count by produceState(0, tab.key) {
            when (tab) {
                UpdatesTab -> {
                    val preferences = Injekt.get<LibraryPreferences>()
                    combine(preferences.newShowUpdatesCount().changes(), preferences.newUpdatesCount().changes()) { show, count -> if (show) count else 0 }
                        .collectLatest { value = it }
                }
                BrowseTab -> Injekt.get<SourcePreferences>().extensionUpdatesCount().changes().collectLatest { value = it }
                else -> Unit
            }
        }
        return count
    }

    suspend fun search(query: String) { librarySearchEvent.send(query) }
    suspend fun openTab(tab: Tab) { openTabEvent.send(tab) }
    suspend fun showBottomNav(show: Boolean) { showBottomNavEvent.send(show) }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(val toDownloads: Boolean, val toLibraryUpdateErrors: Boolean = false) : Tab
    }
}
