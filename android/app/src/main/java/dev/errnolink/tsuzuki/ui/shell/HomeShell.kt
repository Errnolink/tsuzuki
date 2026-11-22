package dev.errnolink.tsuzuki.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.errnolink.tsuzuki.designsystem.*
import dev.errnolink.tsuzuki.ui.shell.LocalShellArrivals
import dev.errnolink.tsuzuki.ui.shell.LocalShellBottomPadding
import dev.errnolink.tsuzuki.ui.shell.LocalShellGlass
import dev.errnolink.tsuzuki.ui.motion.MangaHero
import dev.errnolink.tsuzuki.ui.shell.TsuzukiTab
import dev.errnolink.tsuzuki.ui.shell.TsuzukiTabBar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.domain.history.interactor.GetHistory
import dev.errnolink.tsuzuki.ui.library.LocalShellTabScrollStates
import dev.errnolink.tsuzuki.ui.library.ShellTabScrollStates
import dev.errnolink.tsuzuki.ui.shell.ShellSearch
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.home.navigationTabs
import eu.kanade.tachiyomi.ui.home.openTabBottomTab
import eu.kanade.tachiyomi.ui.home.openTabFeedSegment
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavigationStyle
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun HomeShell(
    tabNavigator: TabNavigator,
    navigator: Navigator,
    librarySearchEvents: Channel<String>,
    openTabEvents: Channel<HomeScreen.Tab>,
    showBottomNavEvents: Channel<Boolean>,
    reselect: kotlinx.coroutines.flow.MutableSharedFlow<String>,
) {
    val scope = rememberCoroutineScope()
    val glass = rememberGlassContext()
    val seedFlow = remember {
        Injekt.get<GetHistory>().subscribeRecent(9)
    }
    val seedRecent by seedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val seedMangaId = seedRecent.firstOrNull()?.mangaId
    LaunchedEffect(seedMangaId) { seedMangaId?.let(MangaHero::requestCardColor) }
    val themeColors = TsuzukiTheme.colors
    val arrivals = rememberCoverArrivalState()
    val density = LocalDensity.current
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val navigationStyle by uiPreferences.navigationStyle().collectAsState()
    val bottomBarLabels by uiPreferences.bottomBarLabels().collectAsState()
    val tabs = navigationTabs(navigationStyle)
    // NAV-05: the tab pill lives at the shell level, so a bar that remounts (bottom-bar hide/
    // show, the rail crossfade) keeps its pill instead of re-seeding one mid-flight.
    val tabPill = remember { TsuzukiPillMotion(scope, 0) }
    val searchQuery by ShellSearch.query.collectAsState()
    var bottomHeight by remember { mutableIntStateOf(0) }
    var statusPillHeight by remember { mutableIntStateOf(0) }
    val bottomVisible by produceState(true) {
        showBottomNavEvents.receiveAsFlow().collectLatest { value = it }
    }
    val scrollStates = remember { ShellTabScrollStates() }
    val widthClass = LocalWindowWidthClass.current
    val railMode = widthClass != WindowWidthClass.Compact
    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalShellGlass provides glass,
        dev.errnolink.tsuzuki.designsystem.LocalGlassContext provides glass,
        LocalShellArrivals provides arrivals,
        LocalShellBottomPadding provides if (bottomVisible && !railMode) with(density) { bottomHeight.toDp() } else 0.dp,
        LocalShellTabScrollStates provides scrollStates,
    ) {
        Box(Modifier.fillMaxSize()) {
            val reducedMotion = TsuzukiTheme.motion.reduced
            // WP3.2: the body's tab-switch fade-through has a background under it, so the frame
            // where both tab contents are half-faded never dips to the window behind the app.
            Box(Modifier.fillMaxSize().background(themeColors.background)) {
            val tabSwitch = updateTransition(tabNavigator.current, label = "tab-switch")
            // One clock for the whole switch (WP3.2): this morph starts the same frame as the
            // body transition and lasts its full duration; the top row's header swap reads it.
            val tabMorph = remember { Animatable(1f) }
            LaunchedEffect(tabNavigator.current.key) {
                if (reducedMotion) {
                    tabMorph.snapTo(1f)
                } else {
                    if (tabMorph.value >= 0.999f || tabMorph.value <= 0.001f) tabMorph.snapTo(0f)
                    tabMorph.animateTo(1f, tween(M3Motion.Medium2, easing = M3Motion.Emphasized))
                }
            }
            SideEffect { ShellTopControls.publishTabMorph(tabMorph::value) }
            CompositionLocalProvider(
                LocalNavigationMotion provides
                    (tabSwitch.currentState != tabSwitch.targetState || tabSwitch.isRunning),
            ) {
            CompositionLocalProvider(dev.errnolink.tsuzuki.designsystem.LocalTsuzukiColors provides themeColors) {
                tabSwitch.AnimatedContent(
                    modifier = Modifier.fillMaxSize()
                        .then(if (railMode) Modifier.padding(start = RailContentStart) else Modifier),
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
            }
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
                // NAV-02: the bar and rail read THIS shell's own declaration (the current tab
                // root's search), never the global front — a pushed page no longer strips the
                // trailing search circle while the pop is still flying.
                val shellOwner = LocalShellTopOwner.current
                val activeSearch = shellOwner?.let { ShellTopControls.declarations[it]?.search }
                Crossfade(
                    targetState = railMode,
                    animationSpec = M3Motion.reducedFade(),
                    label = "NavigationShell",
                ) { rail ->
                    if (rail) {
                        Box(Modifier.fillMaxSize()) {
                            TsuzukiNavRail(
                                tabs = tabItems,
                                selectedIndex = selectedIndex,
                                onSelectTab = { index ->
                                    // Switching away closes an open search first (the rail keeps the
                                    // other tabs reachable while the bottom field is up).
                                    activeSearch?.takeIf { it.state.expanded }?.let(ShellTopControls::closeSearch)
                                    tabItems.getOrNull(index)?.onClick()
                                },
                                showLabels = bottomBarLabels,
                                search = activeSearch,
                                glass = glass,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { size -> bottomHeight = size.height }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            horizontal = ShellBottomBarChrome.HorizontalPadding,
                            vertical = ShellBottomBarChrome.VerticalPadding,
                        )
                        .widthIn(max = ShellBottomBarChrome.MaxWidth),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        TsuzukiTabBar(
                            modifier = Modifier.fillMaxWidth(),
                            tabs = tabItems,
                            selectedIndex = selectedIndex,
                            // N15: derived from scroll state — never collapsed while a search is
                            // open, so closing the search returns the complete bar, labels first.
                            isInline = shellTabBarCollapsed(glass.navigationMinimized, glass.searchActive),
                            onExpand = { glass.expandNavigation() },
                            showLabels = bottomBarLabels,
                            search = activeSearch,
                            glass = glass,
                            pill = tabPill,
                        )
                    }
                }
                        }
                    }
                }
            }
            dev.errnolink.tsuzuki.ui.shell.StatusPill(
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (railMode) Modifier.padding(start = RailContentStart) else Modifier)
                    .onSizeChanged { statusPillHeight = it.height },
            )
        }
    }
    // N15: every fresh context re-derives the bar expanded — a tab switch, and any push/pop that
    // changes the front screen (returning to a screen lands expanded at its top).
    LaunchedEffect(tabNavigator.current.key) { glass.expandNavigation() }
    LaunchedEffect(navigator.lastItem) { glass.expandNavigation() }
    // Leaving Library/Browse (or opening another screen on top) closes the dock search.
    LaunchedEffect(Unit) {
        dev.errnolink.tsuzuki.ui.shell.TabToolRequests.openDownloadQueue.collect {
            navigator.push(DownloadQueueScreen)
        }
    }
    BackHandler(enabled = tabNavigator.current != LibraryTab && searchQuery == null) { tabNavigator.current = LibraryTab }
    LaunchedEffect(tabs) {
        if (tabs.none { it.key == tabNavigator.current.key }) tabNavigator.current = LibraryTab
    }
    LaunchedEffect(Unit) {
        launch {
            librarySearchEvents.receiveAsFlow().collectLatest {
                tabNavigator.current = LibraryTab
                LibraryTab.search(it)
            }
        }
        launch {
            openTabEvents.receiveAsFlow().collectLatest {
                openTabFeedSegment(it, navigationStyle)?.let(FeedTab::openSegment)
                openTabBottomTab(it, navigationStyle)?.let { tab -> tabNavigator.current = tab }
                if (it is HomeScreen.Tab.Library && it.mangaIdToOpen != null) navigator.push(MangaScreen(it.mangaIdToOpen))
                if (it is HomeScreen.Tab.More) {
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
