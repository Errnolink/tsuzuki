package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import dev.errnolink.tsuzuki.ui.shell.ShellSearch
import dev.errnolink.tsuzuki.ui.shell.lateralScrollTo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.EllipsisVertical
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Folder
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiShellMaterial
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.ui.library.QuickFilterItem
import dev.errnolink.tsuzuki.mangadex.ScanlatorFilterOption
import eu.kanade.tachiyomi.data.track.TrackerManager
import androidx.compose.ui.unit.sp
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import androidx.compose.ui.semantics.Role
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass
import dev.errnolink.tsuzuki.designsystem.floatingNavigationScroll
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.shell.LocalShellGlass
import dev.errnolink.tsuzuki.ui.shell.shellBottomPadding
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.home.HomeScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.ContinueButtonSize
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.statusToastPadding
import dev.errnolink.tsuzuki.ui.states.CategoryEmptyState
import dev.errnolink.tsuzuki.ui.states.InCategoryEmptyState
import dev.errnolink.tsuzuki.ui.states.LibrarySearchEmptyState
import dev.errnolink.tsuzuki.ui.shell.ShellMangaTile
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.ShellScrollEdge
import dev.errnolink.tsuzuki.ui.shell.ShellToasts
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.selectionSwapTransform
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.ui.library.LibraryScreenModel
import eu.kanade.tachiyomi.ui.library.LibraryTab
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import androidx.compose.runtime.SideEffect
import eu.kanade.tachiyomi.ui.library.LibraryItem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TsuzukiLibraryScreen(
    state: LibraryScreenModel.State,
    screenModel: LibraryScreenModel,
    toasts: ToastHostState?,
    onRefresh: () -> Unit,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    onBrowse: () -> Unit,
    onOpenDownloadQueue: () -> Unit,
    onSeeAllContinue: () -> Unit,
    onOpenLibraryUpdateErrors: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit,
    selectionActions: @Composable () -> Unit,
) {
    LifecycleResumeEffect(screenModel) {
        screenModel.setLibraryVisible(true)
        onPauseOrDispose { screenModel.setLibraryVisible(false) }
    }
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val density = LocalDensity.current
    val glass = LocalShellGlass.current ?: rememberGlassContext()
    val clearance = shellBottomPadding()
    val historyFlow = remember { Injekt.get<GetHistory>().subscribeRecent(6) }
    val recent by historyFlow.collectAsStateWithLifecycle(RecentHistoryCache.value, minActiveState = Lifecycle.State.RESUMED)
    SideEffect { RecentHistoryCache.value = recent }
    val refreshing by remember { Injekt.get<LibraryUpdateStatus>().isRunning }.collectAsState()
    val preferences = remember { Injekt.get<LibraryPreferences>() }
    val displayMode by preferences.displayMode().collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val columnPreference = remember(orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) preferences.landscapeColumns() else preferences.portraitColumns()
    }
    val columnCount by columnPreference.collectAsState()
    val widthClass = LocalWindowWidthClass.current
    val progressRing by remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>().libraryProgressRing() }.collectAsState()
    val columns = when {
        displayMode == LibraryDisplayMode.List || columnCount > 0 -> libraryColumns(
            setting = columnCount,
            contentWidthDp = (LocalConfiguration.current.screenWidthDp.dp - TsuzukiTheme.spacing.gutter * 2).value,
            widthClass = widthClass,
            displayMode = displayMode,
        )
        else -> StaggeredGridCells.Adaptive(dev.errnolink.tsuzuki.ui.next.NextLibraryAutoCellWidth(displayMode))
    }

    val categories = state.displayedCategories
    val incognitoPreference = remember { Injekt.get<BasePreferences>().incognitoMode() }
    val incognito by incognitoPreference.collectAsState()
    val downloadedOnlyPreference = remember { Injekt.get<BasePreferences>().downloadedOnly() }
    val downloadedOnly by downloadedOnlyPreference.collectAsState()

    var categoryMenuVisible by rememberSaveable { mutableStateOf(false) }
    var filterMenuVisible by rememberSaveable { mutableStateOf(false) }
    val menuPullDownVisible by LibraryMenuPullDownState.isOpen.collectAsState()
    val searchQuery by ShellSearch.query.collectAsState()
    val shellScrollStates = LocalShellTabScrollStates.current


    // ── Pager ⇄ LibraryScreenModel.activeCategoryIndex ────────────────────────────
    val searchPages = categories
    val pagerState = rememberPagerState(initialPage = state.coercedActiveCategoryIndex) { searchPages.size.coerceAtLeast(1) }
    val currentPage by remember(pagerState) { derivedStateOf { pagerState.currentPage } }
    val latestActive by rememberUpdatedState(state.coercedActiveCategoryIndex)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != latestActive && page < categories.size) screenModel.updateActiveCategoryIndex(page)
        }
    }
    LaunchedEffect(state.coercedActiveCategoryIndex, searchPages.size) {
        val target = state.coercedActiveCategoryIndex
        if (target != pagerState.settledPage && !pagerState.isScrollInProgress && target < pagerState.pageCount) {
            pagerState.lateralScrollTo(target, motion.reduced)
        }
    }
    val scope = rememberCoroutineScope()
    val jumpTo: (Int) -> Unit = { index ->
        scope.launch {
            pagerState.lateralScrollTo(index, motion.reduced)
        }
    }

    val searchLabel = stringResource(MR.strings.tsuzuki_search)
    // The search trigger lives in the floating tab bar's trailing circle; the field docks at the
    // bottom of the screen (the shell's bottom dock hosts the flight from that circle). Library
    // search scans every category by default — no category switcher inside the field.
    val librarySearch = rememberTopBarSearch(
        query = searchQuery.orEmpty(),
        onQueryChange = ShellSearch::open,
        placeholder = searchLabel,
        onSearch = { ShellSearch.submit(searchQuery.orEmpty()) },
        onClose = ShellSearch::close,
    )
    LaunchedEffect(searchQuery) {
        if (searchQuery == null) librarySearch.state.collapse() else librarySearch.state.expand()
    }

    val collapseStateMachine = remember {
        LibraryTopRowStateMachine(
            collapseThreshold = with(density) { 8.dp.toPx() },
            hysteresis = with(density) { 24.dp.toPx() },
        )
    }

    // One grid state per category so each page keeps its own scroll position.
    fun gridStateFor(category: Category?): LazyStaggeredGridState =
        shellScrollStates.libraryGridStateFor(category?.id ?: -1L)
    LaunchedEffect(Unit) {
        HomeScreen.reselectEvents.collect { key ->
            if (key == LibraryTab.key) {
                gridStateFor(categories.getOrNull(currentPage)).scrollToItem(0)
            }
        }
    }

    // Collapse the header into the top row once the visible page scrolls.
    val currentGridState = gridStateFor(searchPages.getOrNull(currentPage))
    LaunchedEffect(currentGridState) {
        snapshotFlow {
            Pair(currentGridState.firstVisibleItemIndex, currentGridState.firstVisibleItemScrollOffset.toFloat())
        }.collect { (index, offset) ->
            collapseStateMachine.onScroll(index, offset)
        }
    }
    val collapsed = collapseStateMachine.isCollapsed
    val topRampPx = remember(density) { with(density) { 24.dp.toPx() } }
    val scrolledUnderFractionProvider = remember(currentGridState, topRampPx) {
        derivedStateOf {
            dev.errnolink.tsuzuki.ui.shell.scrolledUnderFraction(
                currentGridState.firstVisibleItemIndex,
                currentGridState.firstVisibleItemScrollOffset.toFloat(),
                topRampPx,
            )
        }
    }
    val scrolledUnderChrome by remember(currentGridState) {
        derivedStateOf { currentGridState.firstVisibleItemIndex > 0 || currentGridState.firstVisibleItemScrollOffset > 0 }
    }
    val scrimAlpha by animateFloatAsState(
        if (scrolledUnderChrome) 1f else 0f,
        if (motion.reduced) snap() else tween(motion.chromeFadeMillis),
        label = "Status bar scrim",
    )

    LaunchedEffect(collapsed) {
        if (collapsed) {
            filterMenuVisible = false
            if (menuPullDownVisible) LibraryMenuPullDownState.close()
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val chromeHeight = statusTop + LibraryChromeHeight
    val chromeTopForContent = chromeHeight

    val headerVisible = !state.selectionMode && state.searchQuery.isNullOrEmpty()
    val continueShelfVisible = headerVisible && recent.isNotEmpty()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val continueShelfTopPx = remember(chromeHeight, density) { with(density) { (chromeHeight + LibraryContinueTopGap).toPx() } }
    val maxHeaderOffsetPx = continueShelfTopPx.roundToInt() + headerHeightPx
    val headerOffset = remember(maxHeaderOffsetPx, categories) {
        derivedStateOf {
            fun offsetOf(page: Int): Int {
                val grid = gridStateFor(searchPages.getOrNull(page))
                return libraryHeaderOffset(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset, maxHeaderOffsetPx)
            }
            val page = pagerState.currentPage
            val fraction = pagerState.currentPageOffsetFraction
            val neighbour = (page + if (fraction > 0f) 1 else -1).coerceIn(0, (searchPages.size - 1).coerceAtLeast(0))
            libraryHeaderOffsetDuringSwipe(offsetOf(page), offsetOf(neighbour), fraction)
        }
    }
    val continueShelfGapPx = remember(density) { with(density) { LibraryContinueShelfGap.toPx() } }
    // The shelf's first measurement lands at once; only later changes (shelf shown/hidden) animate.
    // Animating the first 0 → height made the whole grid slide down 300 ms after the first frame.
    var shelfMeasured by remember { mutableStateOf(false) }
    LaunchedEffect(headerHeightPx > 0) {
        if (headerHeightPx > 0) {
            androidx.compose.runtime.withFrameNanos { }
            shelfMeasured = true
        }
    }
    val continueShelfPadding by animateDpAsState(
        targetValue = if (headerVisible && headerHeightPx > 0) {
            with(density) { (headerHeightPx + continueShelfGapPx.roundToInt()).toDp() }
        } else {
            0.dp
        },
        animationSpec = if (motion.reduced || !shelfMeasured) snap() else M3Motion.standard(M3Motion.Medium1),
        label = "ContinueShelfPadding",
    )

    val settledGridState = gridStateFor(categories.getOrNull(pagerState.settledPage))

    // The active-filter count badge for the top-right filter capsule (moved here from the old
    // bottom filter circle). LIB-05: counts every filter kind, not only the quick tri-states.
    val quickFilterStates = QuickFilterItem.entries.map { item ->
        item.getPreference(preferences).collectAsState().value
    }
    val badgeTrackers = remember { Injekt.get<TrackerManager>().loggedInTrackers() }
    val trackedFilterStates = badgeTrackers.map { service ->
        preferences.filterTracking(service.id.toInt()).collectAsState().value
    }
    val badgeLewd by preferences.filterLewd().collectAsState()
    val badgeIntervalCustom by preferences.filterIntervalCustom().collectAsState()
    val badgeCategoriesFiltered by preferences.filterCategories().collectAsState()
    val badgeCategoryInclude by preferences.filterCategoriesInclude().collectAsState()
    val badgeCategoryExclude by preferences.filterCategoriesExclude().collectAsState()
    val badgeScanlator by remember { ScanlatorFilterOption.observe() }.collectAsState(ScanlatorFilterOption.ANY)
    val filterActiveCount = activeFilterCount(
        LibraryMenuOptionsState(
            quickFilterStates = quickFilterStates,
            trackedStates = trackedFilterStates,
            lewd = badgeLewd,
            intervalCustom = badgeIntervalCustom,
            categoriesFiltered = badgeCategoriesFiltered,
            categoryIncludeIds = badgeCategoryInclude,
            categoryExcludeIds = badgeCategoryExclude,
            scanlatorMatchAll = badgeScanlator == ScanlatorFilterOption.ALL,
        ),
    )

    val menuLabel = stringResource(MR.strings.action_menu)
    val filterLabel = stringResource(MR.strings.action_filter)
    val currentCategory = categories.getOrNull(currentPage)
    val currentCategoryName = currentCategory?.name?.ifBlank { stringResource(MR.strings.label_default) } ?: stringResource(MR.strings.label_library)
    val currentCategoryCount = remember(state, currentCategory) {
        currentCategory?.let(state::getItemCountForCategory)
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxCapsuleWidth = (screenWidth * 0.60f).coerceAtMost((screenWidth - 40.dp - 104.dp).coerceAtLeast(120.dp))
    val changeCategoryLabel = stringResource(MR.strings.tsuzuki_change_category)
    val categoriesTitle = stringResource(MR.strings.categories)
    val defaultCategoryLabel = stringResource(MR.strings.label_default)
    val categoryCaption = currentCategoryCount?.let { pluralStringResource(MR.plurals.tsuzuki_num_titles, it, it) }
    dev.errnolink.tsuzuki.ui.shell.DeclareShellTop(
        title = "",
        glass = glass,
        scrolledUnderFraction = { if (state.selectionMode) 0f else scrolledUnderFractionProvider.value },
        search = if (state.selectionMode) null else librarySearch,
        leadingKey = if (state.selectionMode) null else "library-category",
        leadingContent = if (state.selectionMode) {
            null
        } else {
            {
                dev.errnolink.tsuzuki.ui.next.NextTitlePill(
                    glass = glass,
                    title = currentCategoryName,
                    caption = categoryCaption,
                    icon = TsuzukiIcons.Library,
                    actionLabel = changeCategoryLabel,
                    onClick = { if (categories.size > 1) categoryMenuVisible = true },
                    maxWidth = maxCapsuleWidth,
                ) {
                    dev.errnolink.tsuzuki.designsystem.PullDownMenu(
                        expanded = categoryMenuVisible,
                        onDismissRequest = { categoryMenuVisible = false },
                        paneTitle = categoriesTitle,
                    ) {
                        categories.forEachIndexed { index, category ->
                            dev.errnolink.tsuzuki.designsystem.PullDownMenuItem(
                                text = category.name.ifBlank { defaultCategoryLabel },
                                icon = TsuzukiIcons.Folder,
                                selected = index == currentPage,
                                onClick = {
                                    categoryMenuVisible = false
                                    jumpTo(index)
                                },
                            )
                        }
                    }
                }
            }
        },
        // Top-right group: one glass capsule [filter][more]. Both stay live while the bottom
        // search field is up — nothing in the top row conflicts with it any more.
        actions = if (state.selectionMode) {
            emptyList()
        } else {
            listOf(
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = filterLabel,
                    icon = TsuzukiIcons.SlidersHorizontal,
                    onClick = {
                        LibraryMenuPullDownState.close()
                        filterMenuVisible = true
                    },
                    content = {
                        LibraryFilterIcon(filterActiveCount)
                        LibraryFilterMenu(
                            expanded = filterMenuVisible,
                            onDismissRequest = { filterMenuVisible = false },
                            category = searchPages.getOrNull(currentPage),
                            categories = categories,
                        )
                    },
                ),
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = menuLabel,
                    icon = TsuzukiIcons.EllipsisVertical,
                    onClick = {
                        filterMenuVisible = false
                        LibraryMenuPullDownState.open()
                    },
                    content = {
                        Icon(TsuzukiIcons.EllipsisVertical, null, Modifier.size(22.dp), tint = colors.text)
                        LibraryMenuPullDown(
                            expanded = menuPullDownVisible,
                            incognito = incognito,
                            onToggleIncognito = { incognitoPreference.set(!incognito) },
                            downloadedOnly = downloadedOnly,
                            onToggleDownloadedOnly = { downloadedOnlyPreference.set(!downloadedOnly) },
                            onOpenDownloadQueue = onOpenDownloadQueue,
                            onOpenLibraryUpdateErrors = onOpenLibraryUpdateErrors,
                            onOpenHelp = onOpenHelp,
                            onOpenSettings = onOpenSettings,
                            onOpenStats = onOpenStats,
                            onOpenAbout = onOpenAbout,
                            onDismissRequest = { LibraryMenuPullDownState.close() },
                        )
                    },
                ),
            )
        },
        inlineTitle = { 0f },
        edge = false,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val loadedLibraryBody: @Composable () -> Unit = {
            when {
                state.isLibraryEmpty && !state.searchQuery.isNullOrEmpty() -> LibraryMessage(chromeTopForContent, glass) {
                    LibrarySearchEmptyState(state.searchQuery)
                }
                state.isLibraryEmpty && state.hasActiveFilters -> LibraryMessage(chromeTopForContent, glass) {
                    ShellEmpty(stringResource(MR.strings.tsuzuki_no_matching_manga), stringResource(MR.strings.tsuzuki_no_matching_manga_desc))
                }
                state.isLibraryEmpty -> LibraryMessage(chromeTopForContent, glass) {
                    CategoryEmptyState(onBrowse)
                }
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .floatingNavigationScroll(glass)
                        .glassSource(glass),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { searchPages.getOrNull(it)?.id ?: -1L },
                    ) { page ->
                        val category = searchPages.getOrNull(page)
                        val items = remember(state.libraryData, state.groupedFavorites, category) {
                            category?.let(state::getItemsForCategory).orEmpty()
                        }
                        LazyVerticalStaggeredGrid(
                            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                            columns = columns,
                            state = gridStateFor(category),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
                            verticalItemSpacing = if (displayMode == LibraryDisplayMode.List) 0.dp else TsuzukiTheme.spacing.section,
                            contentPadding = PaddingValues(
                                start = TsuzukiTheme.spacing.gutter,
                                end = TsuzukiTheme.spacing.gutter,
                                top = chromeHeight + 8.dp + continueShelfPadding,
                                // While the bottom search field is up, the last row must clear the
                                // dock row (the same band `clearance` already reserves) and the
                                // keyboard that has risen under it.
                                bottom = clearance + TsuzukiTheme.spacing.section +
                                    if (searchQuery != null) {
                                        WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                                    } else {
                                        0.dp
                                    },
                            ),
                        ) {
                            if (!state.searchQuery.isNullOrEmpty() && !state.isLoading) {
                                item(key = "library-search-count", span = StaggeredGridItemSpan.FullLine) {
                                    val scopeCount = state.groupedFavorites.values.sumOf { it.size }
                                    TsuzukiText(
                                        pluralStringResource(MR.plurals.tsuzuki_num_titles, scopeCount, scopeCount),
                                        Modifier.padding(start = 4.dp),
                                        TsuzukiTheme.typography.footnote,
                                        colors.secondary,
                                    )
                                }
                            }
                            if (items.isEmpty()) {
                                item(key = "library-no-results", span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        Modifier.fillMaxWidth().heightIn(min = 420.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters) {
                                            InCategoryEmptyState()
                                        } else {
                                            LibrarySearchEmptyState(state.searchQuery, inGrid = true)
                                        }
                                    }
                                }
                            } else {
                                libraryGrid(items, state, category, screenModel, displayMode, progressRing, onOpen, onResume)
                            }
                        }
                    }
                    if (headerVisible) {
                        Column(
                            Modifier
                                .scrollable(
                                    state = settledGridState,
                                    orientation = Orientation.Vertical,
                                    reverseDirection = ScrollableDefaults.reverseDirection(
                                        LocalLayoutDirection.current,
                                        Orientation.Vertical,
                                        reverseScrolling = false,
                                    ),
                                    flingBehavior = ScrollableDefaults.flingBehavior(),
                                )
                                .onSizeChanged { headerHeightPx = it.height }
                                .offset { IntOffset(0, (continueShelfTopPx - headerOffset.value).roundToInt()) },
                        ) {
                            if (continueShelfVisible) {
                                LibraryContinueHeader(
                                    recent = recent,
                                    onSeeAllContinue = onSeeAllContinue,
                                    onOpen = onOpen,
                                    onResume = onResume,
                                )
                            }
                        }
                    }
                }
            }
        }

        ShellRefresh(refreshing, !state.selectionMode, onRefresh) {
            when {
                state.isLoading -> LibrarySkeleton(chromeTopForContent, glass, columns, displayMode == LibraryDisplayMode.List)
                else -> loadedLibraryBody()
            }
        }

        Box(Modifier.fillMaxWidth().height(chromeHeight + 16.dp)) {
            if (state.selectionMode) {
                ShellScrollEdge(scrimAlpha, Modifier.matchParentSize())
            }
            Box(Modifier.fillMaxWidth().statusBarsPadding()) {
                AnimatedContent(
                    targetState = state.selectionMode,
                    transitionSpec = { selectionSwapTransform(motion.reduced) },
                    label = "LibraryHeaderSwap",
                ) { selectionMode ->
                    if (selectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LibraryHeaderInset)
                                .height(48.dp)
                                // The trailing Done circle lives outside the swap so its morph is
                                // continuous; reserve its room here.
                                .padding(start = TsuzukiTheme.spacing.gutter, end = TsuzukiTheme.spacing.gutter + SelectionDoneReserve),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TsuzukiText(
                                pluralStringResource(MR.plurals.tsuzuki_n_selected, state.selection.size, state.selection.size),
                                style = TsuzukiTheme.typography.title2,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            selectionActions()
                            GlassSurface(
                                glass,
                                material = GlassMaterial.Thick,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ShellAction(stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, screenModel::selectAll)
                                    ShellAction(stringResource(MR.strings.action_select_inverse), TsuzukiIcons.ArrowLeftRight, screenModel::invertSelection)
                                }
                            }
                        }
                    }
                }
            }
            // The selection Done: a filled tinted check circle that morphs in with selection mode and
            // reverses on leave (iOS 26 Edit → Select), outside the header swap so it is continuous.
            dev.errnolink.tsuzuki.designsystem.SelectionCheckMorph(
                active = state.selectionMode,
                onDone = screenModel::clearSelection,
                glass = glass,
                description = stringResource(MR.strings.tsuzuki_done),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = LibraryHeaderInset, end = TsuzukiTheme.spacing.gutter),
            )
        }
        ShellToasts(toasts, Modifier.align(Alignment.BottomCenter).statusToastPadding())
    }
}

private object RecentHistoryCache {
    var value: List<HistoryWithRelations> = emptyList()
}

private val LibraryChromeHeight = 56.dp
private val LibraryHeaderInset = 8.dp
private val LibraryContinueTopGap = 8.dp
private val LibraryContinueShelfGap = 24.dp

/** Room the header rows leave for the trailing selection Done circle (48 dp control + gap). */
private val SelectionDoneReserve = 56.dp

@Composable
private fun LibraryMessage(
    top: androidx.compose.ui.unit.Dp,
    glass: dev.errnolink.tsuzuki.designsystem.GlassContext? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .then(if (glass != null) Modifier.glassSource(glass) else Modifier)
            .padding(top = top + 8.dp, start = TsuzukiTheme.spacing.gutter, end = TsuzukiTheme.spacing.gutter),
    ) { content() }
}

@Composable
private fun LibrarySkeleton(
    top: androidx.compose.ui.unit.Dp,
    glass: dev.errnolink.tsuzuki.designsystem.GlassContext? = null,
    columns: StaggeredGridCells,
    rows: Boolean = false,
) {
    dev.errnolink.tsuzuki.designsystem.SkeletonClock {
        LazyVerticalStaggeredGrid(
            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            columns = columns,
            modifier = Modifier.fillMaxSize().then(if (glass != null) Modifier.glassSource(glass) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
            verticalItemSpacing = if (rows) 0.dp else TsuzukiTheme.spacing.section,
            contentPadding = PaddingValues(
                start = TsuzukiTheme.spacing.gutter,
                end = TsuzukiTheme.spacing.gutter,
                top = top + 8.dp,
            ),
        ) {
            items(12, key = { "library-skeleton-$it" }, contentType = { "skeleton" }) {
                if (rows) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dev.errnolink.tsuzuki.designsystem.Skeleton(
                            Modifier.width(48.dp).aspectRatio(2f / 3f),
                            shape = coverShape(),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dev.errnolink.tsuzuki.designsystem.Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp), shape = RoundedCornerShape(6.dp))
                            dev.errnolink.tsuzuki.designsystem.Skeleton(Modifier.fillMaxWidth(0.45f).height(12.dp), shape = RoundedCornerShape(6.dp))
                        }
                    }
                } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dev.errnolink.tsuzuki.designsystem.Skeleton(
                        Modifier.fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = coverShape(),
                    )
                    dev.errnolink.tsuzuki.designsystem.Skeleton(
                        Modifier.fillMaxWidth(0.7f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                    dev.errnolink.tsuzuki.designsystem.Skeleton(
                        Modifier.fillMaxWidth(0.45f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
                }
            }
        }
    }
}


private fun LazyStaggeredGridScope.libraryGrid(
    libraryItems: List<LibraryItem>,
    state: LibraryScreenModel.State,
    category: Category?,
    screenModel: LibraryScreenModel,
    displayMode: LibraryDisplayMode,
    progressRing: Boolean,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
) {
    itemsIndexed(
        libraryItems,
        key = { _, item -> item.id },
        // One content type for every tile mode, so switching Compact/Grid/Wide/Cover keeps each
        // tile's composition and loaded cover instead of rebuilding every cell blank (MOT-06).
        contentType = { _, _ -> if (displayMode == LibraryDisplayMode.List) "row" else "tile" },
    ) { index, item ->
        val motion = TsuzukiTheme.motion
        Box(
            Modifier.animateItem(
                fadeInSpec = null,
                placementSpec = if (motion.reduced) null else KasaneSprings.tab(),
                fadeOutSpec = null,
            ),
        ) {
            LibraryGridItem(
                item = item,
                state = state,
                category = category,
                screenModel = screenModel,
                displayMode = displayMode,
                progressRing = progressRing,
                onOpen = onOpen,
                onResume = onResume,
                isLast = index == libraryItems.lastIndex,
            )
        }
    }
}

@Composable
private fun LibraryGridItem(
    item: LibraryItem,
    state: LibraryScreenModel.State,
    category: Category?,
    screenModel: LibraryScreenModel,
    displayMode: LibraryDisplayMode,
    progressRing: Boolean,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    isLast: Boolean,
    listShowsTiles: Boolean = false,
) {
    val manga = item.libraryManga
    val colors = TsuzukiTheme.colors
    val target = state.continueTargets[item.id]
    val mergedLabel = stringResource(MR.strings.tsuzuki_merged)
    val overlays = LibraryTileOverlays.from(item, target, mergedLabel)
    val badges = state.tileBadges
    val resume: (() -> Unit)? = target?.let { { onResume(manga.id, it.chapterId) } }
    val currentManga by androidx.compose.runtime.rememberUpdatedState(manga)
    val currentCategory by androidx.compose.runtime.rememberUpdatedState(category)
    val selectLabel = stringResource(MR.strings.tsuzuki_select)
    val markReadLabel = stringResource(MR.strings.action_mark_as_read)
    val markUnreadLabel = stringResource(MR.strings.action_mark_as_unread)
    val changeCategoriesLabel = stringResource(MR.strings.action_move_category)
    val removeFromLibraryLabel = stringResource(MR.strings.remove_from_library)
    val selectOnly: () -> Unit = remember(item.id) {
        {
            screenModel.clearSelection()
            currentCategory?.let { screenModel.toggleSelection(it, currentManga) }
        }
    }
    val menuActions = remember(item.id, selectLabel, markReadLabel, markUnreadLabel, changeCategoriesLabel, removeFromLibraryLabel) {
        listOf(
            ContextMenuAction(selectLabel, icon = { Icon(TsuzukiIcons.Check, null, tint = TsuzukiTheme.colors.text) }) { currentCategory?.let { screenModel.toggleRangeSelection(it, currentManga) } },
            ContextMenuAction(markReadLabel, icon = { Icon(TsuzukiIcons.BookOpen, null, tint = TsuzukiTheme.colors.text) }) { selectOnly(); screenModel.markReadSelection(true) },
            ContextMenuAction(markUnreadLabel, icon = { Icon(TsuzukiIcons.EyeOff, null, tint = TsuzukiTheme.colors.text) }) { selectOnly(); screenModel.markReadSelection(false) },
            ContextMenuAction(changeCategoriesLabel, icon = { Icon(TsuzukiIcons.Folder, null, tint = TsuzukiTheme.colors.text) }, group = 1) { selectOnly(); screenModel.openChangeCategoryDialog() },
            ContextMenuAction(removeFromLibraryLabel, icon = { Icon(TsuzukiIcons.Trash2, null, tint = TsuzukiTheme.colors.destructive) }, group = 2, destructive = true) { selectOnly(); screenModel.openDeleteMangaDialog() },
        )
    }
    ContextMenu(
        manga.manga.title,
        menuActions,
        onClick = {
            if (state.selectionMode && category != null) {
                screenModel.toggleSelection(category, manga)
            } else {
                dev.errnolink.tsuzuki.ui.details.DetailsEarlyState.prime(manga.manga)
                onOpen(manga.id)
            }
        },
    ) {
        val selected = item.id in state.selection
        val effectiveMode = if (listShowsTiles && displayMode == LibraryDisplayMode.List) {
            LibraryDisplayMode.CompactGrid
        } else {
            displayMode
        }
        when (effectiveMode) {
            LibraryDisplayMode.List -> {
                LibraryListRowCurrent(manga, overlays, badges, selected, state.selectionMode, showSeparator = !isLast, resume)
            }
            else -> dev.errnolink.tsuzuki.ui.next.NextLibraryTile(
                manga = manga.manga,
                progress = dev.errnolink.tsuzuki.ui.next.NextTileProgress.of(manga.readCount, manga.totalChapters, overlays.unread, dev.errnolink.tsuzuki.ui.next.nextTileWords()),
                overlays = overlays,
                badges = badges,
                selected = selected,
                onResume = resume,
                showText = effectiveMode != LibraryDisplayMode.CoverOnlyGrid,
                progressRing = progressRing,
            )
        }
    }
}

@Composable
fun ShellLibraryActions(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: ((eu.kanade.presentation.manga.DownloadAction) -> Unit)?,
    onDeleteClicked: () -> Unit,
    onMigrateClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onSelectionUpdateClicked: () -> Unit,
    onClickCollectRecommendations: (() -> Unit)?,
    onClickAddToMangaDex: (() -> Unit)?,
    onClickResetInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(stringResource(MR.strings.tsuzuki_actions), { expanded = true }, prominent = false)
        PillButton(stringResource(MR.strings.categories), onChangeCategoryClicked, prominent = false)
    }
    val actions = buildList {
        add(ShellSheetAction(stringResource(MR.strings.action_move_category), onClick = onChangeCategoryClicked))
        add(ShellSheetAction(stringResource(MR.strings.action_mark_as_read), onClick = onMarkAsReadClicked))
        add(ShellSheetAction(stringResource(MR.strings.action_mark_as_unread), onClick = onMarkAsUnreadClicked))
        onDownloadClicked?.let {
            eu.kanade.presentation.manga.DownloadAction.entries.forEach { choice ->
                add(
                    ShellSheetAction(
                        downloadActionLabel(choice),
                        onClick = { it(choice) },
                    ),
                )
            }
        }
        add(ShellSheetAction(stringResource(MR.strings.tsuzuki_update_selected), onClick = onSelectionUpdateClicked))
        add(ShellSheetAction(stringResource(MR.strings.action_migrate), onClick = onMigrateClicked))
        add(ShellSheetAction(stringResource(MR.strings.tsuzuki_merge), onClick = onMergeClicked))
        onClickCollectRecommendations?.let { add(ShellSheetAction(stringResource(MR.strings.tsuzuki_collect_recommendations), onClick = it)) }
        onClickAddToMangaDex?.let { add(ShellSheetAction(stringResource(MR.strings.tsuzuki_add_to_mangadex), onClick = it)) }
        onClickResetInfo?.let { add(ShellSheetAction(stringResource(MR.strings.tsuzuki_reset_information), onClick = it)) }
        add(ShellSheetAction(stringResource(MR.strings.remove_from_library), destructive = true, onClick = onDeleteClicked))
    }
    dev.errnolink.tsuzuki.ui.shell.ShellActionSheet(
        visible = expanded,
        title = stringResource(MR.strings.tsuzuki_selected_manga),
        actions = actions,
        onDismissRequest = { expanded = false },
    )
}

@Composable
private fun downloadActionLabel(choice: eu.kanade.presentation.manga.DownloadAction): String = when (choice) {
    eu.kanade.presentation.manga.DownloadAction.NEXT_1_CHAPTER -> pluralStringResource(MR.plurals.download_amount, 1, 1)
    eu.kanade.presentation.manga.DownloadAction.NEXT_5_CHAPTERS -> pluralStringResource(MR.plurals.download_amount, 5, 5)
    eu.kanade.presentation.manga.DownloadAction.NEXT_10_CHAPTERS -> pluralStringResource(MR.plurals.download_amount, 10, 10)
    eu.kanade.presentation.manga.DownloadAction.NEXT_25_CHAPTERS -> pluralStringResource(MR.plurals.download_amount, 25, 25)
    eu.kanade.presentation.manga.DownloadAction.UNREAD_CHAPTERS -> stringResource(MR.strings.download_unread)
    eu.kanade.presentation.manga.DownloadAction.BOOKMARKED_CHAPTERS -> stringResource(MR.strings.download_bookmarked)
}
