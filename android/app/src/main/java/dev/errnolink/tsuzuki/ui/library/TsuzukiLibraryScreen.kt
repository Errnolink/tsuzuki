package dev.errnolink.tsuzuki.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import dev.errnolink.tsuzuki.ui.shell.ShellSearch
import dev.errnolink.tsuzuki.ui.shell.lateralScrollTo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.EllipsisVertical
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Folder
import dev.errnolink.tsuzuki.designsystem.LucideLibrary
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiShellMaterial
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.ui.library.LibraryCategoryMenu
import dev.errnolink.tsuzuki.ui.library.QuickFilterItem
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import androidx.compose.ui.semantics.Role
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.floatingNavigationScroll
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.LocalShellBottomPadding
import dev.errnolink.tsuzuki.ui.shell.NowReadingPreferences
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellContinueButton
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellMangaTile
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.ShellScrollEdge
import dev.errnolink.tsuzuki.ui.shell.ShellSnackbar
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.selectionSwapTransform
import eu.kanade.domain.ui.UiPreferences
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
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    onBrowse: () -> Unit,
    onOpenDownloadQueue: () -> Unit,
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
    val pagesPreference = remember { Injekt.get<UiPreferences>().betaNowReadingPages() }
    val pagesPref by pagesPreference.collectAsState()
    val refreshing by remember { Injekt.get<LibraryUpdateStatus>().isRunning }.collectAsState()
    val preferences = remember { Injekt.get<LibraryPreferences>() }
    val displayMode by preferences.displayMode().collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val columnPreference = remember(orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) preferences.landscapeColumns() else preferences.portraitColumns()
    }
    val columnCount by columnPreference.collectAsState()
    val progressRing by remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>().libraryProgressRing() }.collectAsState()
    val columns = when {
        displayMode == LibraryDisplayMode.List -> StaggeredGridCells.Fixed(1)
        columnCount > 0 -> StaggeredGridCells.Fixed(columnCount)
        else -> StaggeredGridCells.Adaptive(dev.errnolink.tsuzuki.ui.next.NextLibraryAutoCellWidth(displayMode))
    }

    val categories = state.displayedCategories
    val names = remember(categories) { categories.map { it.name.ifBlank { "Default" } } }
    val incognitoPreference = remember { Injekt.get<BasePreferences>().incognitoMode() }
    val incognito by incognitoPreference.collectAsState()
    val downloadedOnlyPreference = remember { Injekt.get<BasePreferences>().downloadedOnly() }
    val downloadedOnly by downloadedOnlyPreference.collectAsState()
    val categoryTabs by preferences.categoryTabs().collectAsState()

    var categoryMenuVisible by rememberSaveable { mutableStateOf(false) }
    val menuPullDownVisible by LibraryMenuPullDownState.isOpen.collectAsState()
    val searchQuery by ShellSearch.query.collectAsState()
    val filterSheetVisible by LibraryFilterSheetState.isOpen.collectAsState()
    val shellScrollStates = LocalShellTabScrollStates.current


    // ── Pager ⇄ LibraryScreenModel.activeCategoryIndex ────────────────────────────
    val pagerState = rememberPagerState(initialPage = state.coercedActiveCategoryIndex) { categories.size.coerceAtLeast(1) }
    val currentPage by remember(pagerState) { derivedStateOf { pagerState.currentPage } }
    val latestActive by rememberUpdatedState(state.coercedActiveCategoryIndex)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != latestActive && page < categories.size) screenModel.updateActiveCategoryIndex(page)
        }
    }
    LaunchedEffect(state.coercedActiveCategoryIndex) {
        val target = state.coercedActiveCategoryIndex
        if (target != pagerState.settledPage && !pagerState.isScrollInProgress && target < pagerState.pageCount) {
            pagerState.lateralScrollTo(target, motion.reduced)
        }
    }
    val scope = rememberCoroutineScope()
    val jumpTo: (Int) -> Unit = { index ->
        scope.launch { pagerState.lateralScrollTo(index, motion.reduced) }
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
            if (key == LibraryTab.key) gridStateFor(categories.getOrNull(currentPage)).scrollToItem(0)
        }
    }

    // Collapse the header into the top row once the visible page scrolls.
    val currentGridState = gridStateFor(categories.getOrNull(currentPage))
    LaunchedEffect(currentGridState) {
        snapshotFlow {
            Pair(currentGridState.firstVisibleItemIndex, currentGridState.firstVisibleItemScrollOffset.toFloat())
        }.collect { (index, offset) ->
            collapseStateMachine.onScroll(index, offset)
        }
    }
    val collapsed = collapseStateMachine.isCollapsed
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
            if (filterSheetVisible) LibraryFilterSheetState.close()
            if (menuPullDownVisible) LibraryMenuPullDownState.close()
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val chromeHeight = statusTop + LibraryChromeHeight

    val searchLabel = stringResource(MR.strings.tsuzuki_search)
    val menuLabel = stringResource(MR.strings.action_menu)
    dev.errnolink.tsuzuki.ui.shell.DeclareShellTop(
        title = "",
        glass = glass,
        actions = if (state.selectionMode || searchQuery != null) {
            emptyList()
        } else {
            listOf(
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(searchLabel, TsuzukiIcons.Search, { ShellSearch.open("") }),
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = menuLabel,
                    icon = TsuzukiIcons.EllipsisVertical,
                    onClick = { LibraryMenuPullDownState.open() },
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
    var screenOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .onGloballyPositioned { screenOrigin = it.positionInWindow() },
    ) {
        ShellRefresh(refreshing, !state.selectionMode, onRefresh) {
            when {
                state.isLoading -> LibrarySkeleton(chromeHeight, glass, columns)
                state.isLibraryEmpty && !state.searchQuery.isNullOrEmpty() -> LibraryMessage(chromeHeight, glass) {
                    ShellEmpty("No matching manga", "Nothing in your library matches \"${state.searchQuery}\".")
                }
                state.isLibraryEmpty && state.hasActiveFilters -> LibraryMessage(chromeHeight, glass) {
                    ShellEmpty("No matching manga", "Change your filters to see more of your library.")
                }
                state.isLibraryEmpty -> LibraryMessage(chromeHeight, glass) {
                    ShellEmpty("Your next story starts here", "Add manga from Browse to build your library.") {
                        PillButton("Browse manga", onBrowse)
                    }
                }
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().floatingNavigationScroll(glass).glassSource(glass),
                    key = { categories.getOrNull(it)?.id ?: -1L },
                ) { page ->
                    val category = categories.getOrNull(page)
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
                            top = chromeHeight + 8.dp,
                            bottom = clearance + TsuzukiTheme.spacing.section,
                        ),
                    ) {
                        if (!state.selectionMode && state.searchQuery.isNullOrEmpty() && recent.isNotEmpty()) {
                            item(key = "library-continue", span = StaggeredGridItemSpan.FullLine) {
                                val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TsuzukiText(
                                            "Continue reading",
                                            Modifier.weight(1f),
                                            TsuzukiTheme.typography.title2,
                                        )
                                        Box(
                                            Modifier.heightIn(min = 44.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                                .clickable {
                                                    scope.launch { HomeScreen.openTab(HomeScreen.Tab.History) }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            TsuzukiText(
                                                "See all",
                                                style = TsuzukiTheme.typography.subhead,
                                                color = colors.accent,
                                            )
                                        }
                                    }
                                    if (recent.size == 1) {
                                        val first = recent.first()
                                        ContinueCard(
                                            first,
                                            pagesPref,
                                            Modifier.fillMaxWidth(),
                                            onClick = { onOpen(first.mangaId) },
                                            onResume = { onResume(first.mangaId, first.chapterId) },
                                        )
                                    } else {
                                        LazyRow(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(recent, key = { it.chapterId }, contentType = { "continue" }) { entry ->
                                                ContinueCard(
                                                    entry,
                                                    pagesPref,
                                                    Modifier.width(screenWidth * 0.85f),
                                                    onClick = { onOpen(entry.mangaId) },
                                                    onResume = { onResume(entry.mangaId, entry.chapterId) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!state.searchQuery.isNullOrEmpty() && !state.isLoading) {
                            item(key = "library-search-count", span = StaggeredGridItemSpan.FullLine) {
                                val scopeCount = state.groupedFavorites.values.sumOf { it.size }
                                TsuzukiText(
                                    if (scopeCount == 1) "1 title" else "$scopeCount titles",
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
                                        ShellEmpty("No manga in this category", "Add manga or move titles into this category.")
                                    } else {
                                        ShellEmpty("No matching manga", "Try another category, search, or filter.")
                                    }
                                }
                            }
                        } else {
                            libraryGrid(items, state, category, screenModel, displayMode, progressRing, onOpen, onResume)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(chromeHeight + 16.dp)) {
            ShellScrollEdge(scrimAlpha, Modifier.matchParentSize())
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
                            .padding(horizontal = TsuzukiTheme.spacing.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TsuzukiText(
                            "${state.selection.size} selected",
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
                                ShellAction("Select all", Icons.Outlined.SelectAll, screenModel::selectAll)
                                ShellAction("Invert selection", Icons.Outlined.FlipToBack, screenModel::invertSelection)
                                ShellAction("Done", Icons.Outlined.Close, screenModel::clearSelection)
                            }
                        }
                    }
                } else {
                    val currentCategory = categories.getOrNull(currentPage)
                    val currentCategoryName = currentCategory?.name?.ifBlank { "Default" } ?: "Library"
                    val currentCategoryCount = remember(state, currentCategory) {
                        currentCategory?.let(state::getItemCountForCategory)
                    }
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val maxCapsuleWidth = (screenWidth * 0.60f).coerceAtMost((screenWidth - 40.dp - 104.dp).coerceAtLeast(120.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LibraryHeaderInset)
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchQuery == null,
                            enter = if (motion.reduced) fadeIn(snap()) else fadeIn(tween(90)),
                            exit = if (motion.reduced) fadeOut(snap()) else fadeOut(tween(90)),
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            dev.errnolink.tsuzuki.ui.next.NextTitlePill(
                                glass = glass,
                                title = currentCategoryName,
                                caption = currentCategoryCount?.let { if (it == 1) "1 title" else "$it titles" },
                                icon = TsuzukiIcons.LucideLibrary,
                                actionLabel = "Change category",
                                onClick = { if (categoryTabs && categories.size > 1) categoryMenuVisible = true },
                                maxWidth = maxCapsuleWidth,
                            ) {
                                dev.errnolink.tsuzuki.designsystem.PullDownMenu(
                                    expanded = categoryMenuVisible,
                                    onDismissRequest = { categoryMenuVisible = false },
                                    paneTitle = "Categories",
                                ) {
                                    categories.forEachIndexed { index, category ->
                                        if (index > 0) dev.errnolink.tsuzuki.designsystem.PullDownDivider()
                                        dev.errnolink.tsuzuki.designsystem.PullDownMenuItem(
                                            text = category.name.ifBlank { "Default" },
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

                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchQuery != null,
                            modifier = Modifier.fillMaxSize(),
                            enter = if (motion.reduced) fadeIn(snap()) else fadeIn(tween(160)) + expandHorizontally(motion.tabSpring(), expandFrom = Alignment.End),
                            exit = if (motion.reduced) fadeOut(snap()) else fadeOut(tween(160)) + shrinkHorizontally(motion.tabSpring(), shrinkTowards = Alignment.End),
                        ) {
                            val searchPillInteraction = remember { MutableInteractionSource() }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GlassSurface(
                                    context = glass,
                                    shape = CircleShape,
                                    material = GlassMaterial.Regular,
                                    modifier = Modifier.height(44.dp).wrapContentWidth().pressScale(searchPillInteraction),
                                ) {
                                    Row(
                                        Modifier
                                            .combinedClickable(
                                                interactionSource = searchPillInteraction,
                                                indication = null,
                                                role = Role.Button,
                                                onClickLabel = "Change category",
                                                onClick = { categoryMenuVisible = true },
                                            )
                                            .padding(horizontal = 14.dp)
                                            .fillMaxHeight(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        TsuzukiText(
                                            text = currentCategoryName,
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.text,
                                            ),
                                            maxLines = 1,
                                        )
                                        Icon(
                                            imageVector = TsuzukiIcons.ChevronDown,
                                            contentDescription = null,
                                            tint = colors.secondary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                LibraryTopSearchField(
                                    glass = glass,
                                    query = searchQuery.orEmpty(),
                                    onQueryChange = ShellSearch::update,
                                    onSubmit = { query ->
                                        ShellSearch.submit(query)
                                    },
                                    onClose = ShellSearch::close,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    LibraryCategoryMenu(
                        expanded = categoryMenuVisible && searchQuery != null,
                        categories = categories,
                        currentCategoryIndex = currentPage,
                        onSelectCategory = { index ->
                            jumpTo(index)
                            categoryMenuVisible = false
                        },
                        onDismissRequest = { categoryMenuVisible = false },
                    )
                }
                }
            }
        }
        ShellSnackbar(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalShellBottomPadding.current + 12.dp))
        val filterButton = LibraryFilterSheetState.buttonBoundsInWindow
        Box(
            modifier = if (filterButton != null) {
                Modifier
                    .offset {
                        IntOffset(
                            (filterButton.left - screenOrigin.x).roundToInt(),
                            (filterButton.top - screenOrigin.y).roundToInt(),
                        )
                    }
                    .size(with(density) { DpSize(filterButton.width.toDp(), filterButton.height.toDp()) })
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = LocalShellBottomPadding.current + 8.dp)
            },
        ) {
            LibraryFilterMenu(
                expanded = filterSheetVisible,
                onDismissRequest = { LibraryFilterSheetState.close() },
                category = categories.getOrNull(currentPage),
                categories = categories,
            )
        }
    }
}

private object RecentHistoryCache {
    var value: List<HistoryWithRelations> = emptyList()
}

private val LibraryChromeHeight = 56.dp
private val LibraryHeaderInset = 8.dp

@Composable
private fun LibraryTopSearchField(
    glass: dev.errnolink.tsuzuki.designsystem.GlassContext?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (query != value.text) value = TextFieldValue(query, TextRange(query.length))
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val dismiss = {
        keyboard?.hide()
        onClose()
    }
    BackHandler(onBack = dismiss)

    GlassSurface(
        context = glass,
        shape = CircleShape,
        material = GlassMaterial.Regular,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = TsuzukiIcons.Search,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    TsuzukiText(
                        text = "Search library",
                        style = TsuzukiTheme.typography.body,
                        color = colors.secondary,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        val changed = it.text != value.text
                        value = it
                        if (changed) onQueryChange(it.text)
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSubmit(value.text)
                        keyboard?.hide()
                    }),
                )
            }
            if (value.text.isNotEmpty()) {
                val clearInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressScale(clearInteraction)
                        .clickable(
                            interactionSource = clearInteraction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Clear search",
                        ) {
                            value = TextFieldValue("")
                            onQueryChange("")
                            focus.requestFocus()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.text.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TsuzukiIcons.X,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colors.text,
                        )
                    }
                }
            }
            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(closeInteraction)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = stringResource(MR.strings.tsuzuki_close_search),
                        onClick = dismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TsuzukiIcons.X,
                    contentDescription = stringResource(MR.strings.tsuzuki_close_search),
                    modifier = Modifier.size(20.dp),
                    tint = colors.secondary,
                )
            }
        }
    }
}

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
) {
    dev.errnolink.tsuzuki.ui.details.SkeletonPulseClock {
        LazyVerticalStaggeredGrid(
            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            columns = columns,
            modifier = Modifier.fillMaxSize().then(if (glass != null) Modifier.glassSource(glass) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
            verticalItemSpacing = TsuzukiTheme.spacing.section,
            contentPadding = PaddingValues(
                start = TsuzukiTheme.spacing.gutter,
                end = TsuzukiTheme.spacing.gutter,
                top = top + 8.dp,
            ),
        ) {
            items(12, key = { "library-skeleton-$it" }, contentType = { "skeleton" }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dev.errnolink.tsuzuki.ui.details.ShimmerBox(
                        Modifier.fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(TsuzukiCorners.cover),
                    )
                    dev.errnolink.tsuzuki.ui.details.ShimmerBox(
                        Modifier.fillMaxWidth(0.7f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                    dev.errnolink.tsuzuki.ui.details.ShimmerBox(
                        Modifier.fillMaxWidth(0.45f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
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
        contentType = { _, _ -> displayMode },
    ) { index, item ->
        val manga = item.libraryManga
        val colors = TsuzukiTheme.colors
        val target = state.continueTargets[item.id]
        val overlays = LibraryTileOverlays.from(item, target?.chapterId, target?.progress)
        val badges = state.tileBadges
        val resume: (() -> Unit)? = target?.let { { onResume(manga.id, it.chapterId) } }
        val selectOnly: () -> Unit = remember(item.id) {
            {
                screenModel.clearSelection()
                if (category != null) screenModel.toggleSelection(category, manga)
            }
        }
        val menuActions = remember(item.id) {
            listOf(
                ContextMenuAction("Select", icon = { Icon(TsuzukiIcons.Check, null, tint = TsuzukiTheme.colors.text) }) { if (category != null) screenModel.toggleRangeSelection(category, manga) },
                ContextMenuAction("Mark as read", icon = { Icon(TsuzukiIcons.BookOpen, null, tint = TsuzukiTheme.colors.text) }) { selectOnly(); screenModel.markReadSelection(true) },
                ContextMenuAction("Mark as unread", icon = { Icon(TsuzukiIcons.EyeOff, null, tint = TsuzukiTheme.colors.text) }) { selectOnly(); screenModel.markReadSelection(false) },
                ContextMenuAction("Change categories", icon = { Icon(TsuzukiIcons.Folder, null, tint = TsuzukiTheme.colors.text) }, group = 1) { selectOnly(); screenModel.openChangeCategoryDialog() },
                ContextMenuAction("Remove from library", icon = { Icon(TsuzukiIcons.Trash2, null, tint = TsuzukiTheme.colors.destructive) }, group = 2, destructive = true) { selectOnly(); screenModel.openDeleteMangaDialog() },
            )
        }
        ContextMenu(
            manga.manga.title,
            menuActions,
            onClick = {
                if (state.selectionMode && category != null) {
                    screenModel.toggleSelection(category, manga)
                } else {
                    onOpen(manga.id)
                }
            },
        ) {
            val selected = item.id in state.selection
            when (displayMode) {
                LibraryDisplayMode.List -> Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) colors.selectedFill else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShellCover(manga.manga.asMangaCover(), manga.manga.title, Modifier.width(48.dp), fixedRatio = 2f / 3f)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TsuzukiText(
                                manga.manga.title,
                                style = TsuzukiTheme.typography.subhead,
                                color = if (overlays.read) colors.secondary else colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val summary = overlays.summaryText(badges)
                            if (summary.isNotEmpty()) {
                                TsuzukiText(
                                    summary,
                                    style = TsuzukiTheme.typography.caption1,
                                    color = colors.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (badges.continueButton && overlays.showContinue) {
                            LibraryTileContinuePill { resume?.invoke() }
                        }
                    }
                    if (index < libraryItems.lastIndex && !selected) {
                        Box(
                            Modifier
                                .padding(start = 64.dp)
                                .fillMaxWidth()
                                .height(TsuzukiTheme.spacing.hairline)
                                .background(colors.separator),
                        )
                    }
                }
                else -> dev.errnolink.tsuzuki.ui.next.NextLibraryTile(
                    manga = manga.manga,
                    progress = dev.errnolink.tsuzuki.ui.next.NextTileProgress.of(manga.readCount, manga.totalChapters, overlays.unread),
                    overlays = overlays,
                    badges = badges,
                    selected = selected,
                    onResume = resume,
                    showText = displayMode != LibraryDisplayMode.CoverOnlyGrid,
                    progressRing = progressRing,
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    entry: HistoryWithRelations,
    pagesPref: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val totalPages = NowReadingPreferences.decodePageTotal(pagesPref, entry.chapterId)
    val progress = totalPages?.takeIf { it > 0 }?.let { pages ->
        ((entry.lastPageRead + 1).coerceAtMost(pages.toLong())) / pages.toFloat()
    }
    Row(
        modifier
            .background(colors.grouped, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellCover(entry.coverData, entry.title, Modifier.width(56.dp), fixedRatio = 2f / 3f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TsuzukiText(
                entry.title,
                style = TsuzukiTheme.typography.subhead,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TsuzukiText(
                "Ch. ${eu.kanade.presentation.util.formatChapterNumber(entry.chapterNumber)} · Page ${entry.lastPageRead + 1}",
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.separator),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.accent),
                    )
                }
            }
        }
        ShellContinueButton(progress, Modifier) { onResume() }
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
        PillButton("Actions", { expanded = true }, prominent = false)
        PillButton("Categories", onChangeCategoryClicked, prominent = false)
    }
    val actions = buildList {
        add(ShellSheetAction("Change categories", onClick = onChangeCategoryClicked))
        add(ShellSheetAction("Mark as read", onClick = onMarkAsReadClicked))
        add(ShellSheetAction("Mark as unread", onClick = onMarkAsUnreadClicked))
        onDownloadClicked?.let {
            eu.kanade.presentation.manga.DownloadAction.entries.forEach { choice ->
                add(
                    ShellSheetAction(
                        choice.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() },
                        onClick = { it(choice) },
                    ),
                )
            }
        }
        add(ShellSheetAction("Update selected", onClick = onSelectionUpdateClicked))
        add(ShellSheetAction("Migrate", onClick = onMigrateClicked))
        add(ShellSheetAction("Merge", onClick = onMergeClicked))
        onClickCollectRecommendations?.let { add(ShellSheetAction("Collect recommendations", onClick = it)) }
        onClickAddToMangaDex?.let { add(ShellSheetAction("Add to MangaDex", onClick = it)) }
        onClickResetInfo?.let { add(ShellSheetAction("Reset information", onClick = it)) }
        add(ShellSheetAction("Remove from library", destructive = true, onClick = onDeleteClicked))
    }
    dev.errnolink.tsuzuki.ui.shell.ShellActionSheet(
        visible = expanded,
        title = "Selected manga",
        actions = actions,
        onDismissRequest = { expanded = false },
    )
}
