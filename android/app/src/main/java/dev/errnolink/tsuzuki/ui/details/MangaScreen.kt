package dev.errnolink.tsuzuki.ui.details

import dev.errnolink.tsuzuki.reader.readerOrigin
import androidx.activity.compose.BackHandler
import dev.errnolink.tsuzuki.ui.shell.DeclareShellTop
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.heroDismissDrag
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import tachiyomi.core.common.preference.TriState
import eu.kanade.domain.manga.model.downloadedFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.PullDownDivider
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.ArrowDownUp
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Folder
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.Link
import dev.errnolink.tsuzuki.designsystem.LucideLibrary
import dev.errnolink.tsuzuki.designsystem.Palette
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.SettingsLucide
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.Sparkles
import dev.errnolink.tsuzuki.designsystem.Tag
import dev.errnolink.tsuzuki.ui.shell.ShellSnackbar
import eu.kanade.tachiyomi.BuildConfig
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.LocalNavigationMotion
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import dev.errnolink.tsuzuki.mangadex.MangaDexChapterMenu
import dev.errnolink.tsuzuki.mangadex.MangaDexRatingSheet
import dev.errnolink.tsuzuki.mangadex.MangaDexSimilarGrid
import dev.errnolink.tsuzuki.mangadex.rememberMangaDexChapterPresentation
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.SearchMetadataChips
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.isIncognitoModeEnabled
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
fun MangaScreen(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    chapterSettingsMenu: @Composable () -> Unit = {},
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    continueChapter: Chapter?,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    onMetadataViewerClicked: () -> Unit,
    onEditInfoClicked: () -> Unit,
    onRecommendClicked: () -> Unit,
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
    onClickSourceSettingsClicked: (() -> Unit)?,
    onClearManga: () -> Unit,
    onOpenMangaFolder: (() -> Unit)?,
    onRelatedMangasScreenClick: () -> Unit,
    onRelatedMangaClick: (Manga) -> Unit,
    onRelatedMangaLongClick: (Manga) -> Unit,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    coverRatio: MutableFloatState,
    onCoversClicked: (() -> Unit)? = null,
    onPaletteClicked: (() -> Unit)? = null,
    onDownloadAllClicked: (() -> Unit)? = null,
    onRemoveDownloadsClicked: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val glass = rememberGlassContext()
    val context = LocalContext.current
    val dex = rememberMangaDexChapterPresentation(state.manga.id, remember(state.chapters) { state.chapters.map { it.chapter } })
    val chapterPageTotals = rememberChapterPageTotals()
    val chapters = remember(state.processedChapters, dex) { state.processedChapters.filter { dex.isVisible(it.chapter) } }
    val heroLastUpdate = remember(chapters, state.manga.lastUpdate) {
        chapters.asSequence().map { it.chapter.dateUpload }.filter { it > 0 }.maxOrNull() ?: state.manga.lastUpdate
    }
    val chapterListItems = if (chapters.size == state.processedChapters.size) state.chapterListItems else chapters
    var preferredGroup by remember(state.manga.id) { mutableStateOf(PreferredGroups.get(state.manga.id)) }
    var groupSheetVisible by remember { mutableStateOf(false) }
    val chapterRows = remember(chapterListItems, preferredGroup) { groupChapterRows(chapterListItems, preferredGroup) }
    val hasGroupChoice = remember(chapterRows) { chapterRows.any { it is ChapterRowEntry.Group && it.alternates.isNotEmpty() } }
    var openGroups by remember(state.manga.id) { mutableStateOf(emptySet<String>()) }
    val visibleRows = remember(chapterRows, openGroups) { chapterRows.expanded(openGroups) }
    val rowShapes = remember(visibleRows) {
        BooleanArray(visibleRows.size * 2).apply {
            for (i in visibleRows.indices) {
                this[i * 2] = i == 0 || visibleRows[i - 1] is ChapterRowEntry.Gap
                this[i * 2 + 1] = i == visibleRows.lastIndex || visibleRows[i + 1] is ChapterRowEntry.Gap
            }
        }
    }
    val chapterCount = remember(chapterRows) { chapterRows.count { it is ChapterRowEntry.Group } }
    val visibleContinueChapter = remember(continueChapter, dex, chapters) {
        continueChapter?.takeIf(dex::isVisible)
            ?: chapters.asReversed().firstOrNull { !it.chapter.read && dex.accessMessage(it.chapter) == null }?.chapter
    }
    val selected = remember(state.chapters) { state.chapters.filter { it.selected } }
    val heroTitle = remember { HeroTitleAnchor() }
    val collapsed by remember(listState) {
        derivedStateOf {
            val hero = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
            dev.errnolink.tsuzuki.designsystem.headerScrolledAway(
                hero?.offset,
                heroTitle.titleBottomInHero ?: hero?.size ?: 0,
                listState.firstVisibleItemIndex,
                0,
            )
        }
    }
    val motion = TsuzukiTheme.motion
    var aboutVisible by remember { mutableStateOf(false) }
    var ratingSheetVisible by remember { mutableStateOf(false) }
    var detailsTab by rememberSaveable(state.manga.id) { mutableIntStateOf(0) }
    var clearConfirmVisible by remember { mutableStateOf(false) }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val top = statusBarInset + dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar.bodyHeightDp.dp
    val isMdTitle = remember(state.source.id, state.manga.source) {
        state.source.getMainSource<MetadataSource<*, *>>() is MangaDex || state.manga.source == MERGED_SOURCE_ID
    }
    var heroCoverReady by remember(state.manga.id, state.manga.coverLastModified, state.manga.thumbnailUrl) { mutableStateOf(false) }
    val glow = rememberDetailsGlow(state.manga, heroCoverReady)
    val tsuzukiPreferences = remember { Injekt.get<TsuzukiPreferences>() }
    val headerStyle by tsuzukiPreferences.detailsHeader().collectAsState()
    val appleHeader = headerStyle == dev.errnolink.tsuzuki.preference.DetailsHeader.CENTERED
    val coversAvailable = remember(state.source.id, state.manga.source, state.mergedData, onCoversClicked) {
        onCoversClicked != null && (
            state.source.getMainSource<MetadataSource<*, *>>() is MangaDex ||
                state.mergedData?.sources?.any { it.getMainSource<MetadataSource<*, *>>() is MangaDex } == true
            )
    }
    val activeFilters = remember(
        state.manga.downloadedFilter,
        state.manga.unreadFilter,
        state.manga.bookmarkedFilter,
        state.scanlatorFilterActive,
        dex.selected,
        chapters.size,
        state.processedChapters.size,
    ) {
        buildList {
            when (state.manga.downloadedFilter) {
                TriState.ENABLED_IS -> add("Downloaded only")
                TriState.ENABLED_NOT -> add("Not downloaded")
                TriState.DISABLED -> Unit
            }
            when (state.manga.unreadFilter) {
                TriState.ENABLED_IS -> add("Unread only")
                TriState.ENABLED_NOT -> add("Read only")
                TriState.DISABLED -> Unit
            }
            when (state.manga.bookmarkedFilter) {
                TriState.ENABLED_IS -> add("Bookmarked only")
                TriState.ENABLED_NOT -> add("Not bookmarked")
                TriState.DISABLED -> Unit
            }
            if (state.scanlatorFilterActive) add("Scanlator filter")
            if (dex.selected.isNotEmpty()) add(dex.selected.joinToString { it.uppercase() })
            if (chapters.size != state.processedChapters.size) add("MangaDex filters")
        }.joinToString(" · ")
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = bottomInset + animateDpAsState(
        if (selected.isEmpty()) 24.dp else 88.dp,
        if (motion.reduced) snap() else motion.tabSpring(),
        label = "Selection actions inset",
    ).value
    val startOverChapter = if (visibleContinueChapter == null && activeFilters.isEmpty()) chapters.lastOrNull()?.chapter else null
    val navigationMotion = LocalNavigationMotion.current
    val holdContinue = remember { mutableStateOf(false) }
    if (navigationMotion && state.chaptersPending) holdContinue.value = true
    if (!navigationMotion) holdContinue.value = false
    val chaptersKnown = !state.chaptersPending && !holdContinue.value
    val nextUpdateText = nextUpdate?.let { "Next update: ${relativeDateText(it.toEpochMilli())}" }
    val continueLabel = when {
        !chaptersKnown -> "Continue"
        visibleContinueChapter == null && activeFilters.isNotEmpty() -> "No matching chapters"
        visibleContinueChapter == null && startOverChapter != null ->
            "Start over" + startOverChapter.takeIf { it.chapterNumber.isFinite() && it.isRecognizedNumber }
                ?.let { " · Ch. ${formatChapterNumber(it.chapterNumber)}" }.orEmpty()
        visibleContinueChapter == null -> "No unread chapters"
        visibleContinueChapter.lastPageRead > 0 || state.chapters.any { it.chapter.read } ->
            continueChapterLabel(visibleContinueChapter)
        else -> "Start reading"
    }
    val continueEnabled = chaptersKnown && (visibleContinueChapter != null || startOverChapter != null)

    val seededDominant = remember(state.manga.id) {
        tachiyomi.domain.manga.model.MangaCover.dominantCoverColorMap[state.manga.id]?.first?.let { Color(it) }
    }
    val stableDominant = remember(state.manga.id) { mutableStateOf(seededDominant) }
    val glowAccent = glow.accent.value
    if (glowAccent != null && stableDominant.value == null) stableDominant.value = glowAccent
    val themeColors = TsuzukiTheme.colors
    val pendingActionColor = if (themeColors.isDark) themeColors.selectedFill else themeColors.secondary
    val actionColor = androidx.compose.animation.animateColorAsState(
        targetValue = stableDominant.value?.let { ContinueActionColor.forCover(it, themeColors.tabTint) } ?: pendingActionColor,
        animationSpec = if (TsuzukiTheme.motion.reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(220),
        label = "continueActionColor",
    )
    val unreadCount = remember(chapterRows) {
        chapterRows.count { it is ChapterRowEntry.Group && !it.primary.chapter.read && it.alternates.none { alt -> alt.chapter.read } }
    }
    val openContinue: () -> Unit = {
        val chapter = visibleContinueChapter ?: startOverChapter
        chapter?.let {
            val downloaded = chapters.firstOrNull { entry -> entry.id == it.id }?.isDownloaded == true
            val message = if (downloaded) null else dex.accessMessage(it)
            if (message != null) context.toast(message)
            else if (it == continueChapter) onContinueReading()
            else onChapterClicked(it)
        }
    }
    val searchMetadataChips = remember(state.meta, state.manga.genre) { SearchMetadataChips(state.meta, state.source.id, state.manga.genre) }
    val menuActions = remember(
        state.source,
        state.manga.source,
        state.manga.favorite,
        state.showMergeWithAnother,
        state.meta,
        nextUpdateText,
        onPaletteClicked,
        onMergedSettingsClicked,
        onMergeWithAnotherClicked,
        onRefresh,
        onEditInfoClicked,
        onEditCategoryClicked,
        onEditFetchIntervalClicked,
        onRelatedMangasScreenClick,
        onRecommendClicked,
        onMetadataViewerClicked,
        onWebViewClicked,
        onWebViewLongClicked,
        onClickSourceSettingsClicked,
        onMigrateClicked,
        onOpenMangaFolder,
        onClearManga,
        onAddToLibraryClicked,
        hasGroupChoice,
        preferredGroup,
    ) {
        buildList {
            // Group 0: library management (SPEC "More menu": Edit categories, Migrate first)
            onEditCategoryClicked?.let { add(detailMenuAction("Edit categories", TsuzukiIcons.Tag, group = 0, onClick = it)) }
            onMigrateClicked?.let { add(detailMenuAction("Migrate", TsuzukiIcons.ArrowLeftRight, group = 0, onClick = it)) }
            // Group 1: web
            onWebViewClicked?.let { add(detailMenuAction("Open in browser", TsuzukiIcons.Globe, group = 1, onClick = it)) }
            onWebViewLongClicked?.let { add(detailMenuAction("Copy link", TsuzukiIcons.Copy, group = 1, onClick = it)) }
            // Group 2: every action from today's menu that is not now on the row
            if (state.manga.favorite) add(detailMenuAction("Edit title information", TsuzukiIcons.Pencil, group = 2, onClick = onEditInfoClicked))
            onEditFetchIntervalClicked?.let { add(detailMenuAction("Update schedule", TsuzukiIcons.Calendar, subtitle = nextUpdateText, group = 2, onClick = it)) }
            if (BuildConfig.DEBUG) {
                onPaletteClicked?.let { add(detailMenuAction("Palette", TsuzukiIcons.Palette, group = 2, onClick = it)) }
            }
            add(detailMenuAction("Related titles", TsuzukiIcons.LucideLibrary, group = 2, onClick = onRelatedMangasScreenClick))
            add(detailMenuAction("Recommendations", TsuzukiIcons.Sparkles, group = 2, onClick = onRecommendClicked))
            if (state.meta != null) add(detailMenuAction("Source metadata", TsuzukiIcons.Info, group = 2, onClick = onMetadataViewerClicked))
            onClickSourceSettingsClicked?.let { add(detailMenuAction("Source settings", TsuzukiIcons.SettingsLucide, group = 2, onClick = it)) }
            if (hasGroupChoice) {
                add(detailMenuAction("Preferred group", TsuzukiIcons.ListChecks, subtitle = preferredGroup ?: "Automatic", group = 2, onClick = { groupSheetVisible = true }))
            }
            onOpenMangaFolder?.let { add(detailMenuAction("Open download folder", TsuzukiIcons.Folder, group = 2, onClick = it)) }
            if (state.manga.source == MERGED_SOURCE_ID) {
                add(detailMenuAction("Sources and merge settings", TsuzukiIcons.Link, group = 2, onClick = onMergedSettingsClicked))
            }
            if (state.showMergeWithAnother) {
                add(detailMenuAction("Merge with another title", TsuzukiIcons.Link, group = 2, onClick = onMergeWithAnotherClicked))
            }
            // Group 3: destructive, Remove from library last
            add(detailMenuAction("Clear title data", TsuzukiIcons.Trash2, group = 3, destructive = true, onClick = { clearConfirmVisible = true }))
            if (state.manga.favorite) {
                add(detailMenuAction("Remove from library", TsuzukiIcons.Trash2, group = 3, destructive = true, onClick = onAddToLibraryClicked))
            }
        }
    }
    BackHandler(selected.isNotEmpty()) { onAllChapterSelected(false) }
    BackHandler(enabled = aboutVisible || ratingSheetVisible) { aboutVisible = false; ratingSheetVisible = false }
    val detailsActionRow: @Composable (Modifier) -> Unit = { modifier ->
        DetailsActionRow(
            mangaId = state.manga.id,
            mangaTitle = state.manga.title,
            sourceId = state.source.id,
            glass = glass,
            continueLabel = continueLabel,
            continueEnabled = continueEnabled,
            actionColor = { if (chaptersKnown) actionColor.value else pendingActionColor },
            onContinueClick = openContinue,
            inLibrary = state.manga.favorite,
            onLibraryClick = {
                if (state.manga.favorite) onEditCategoryClicked?.invoke() ?: onAddToLibraryClicked()
                else onAddToLibraryClicked()
            },
            hasLoggedInTrackers = state.hasLoggedInTrackers,
            onOpenTrackingSettings = onTrackingClicked,
            trackingCount = state.trackingCount,
            mergedActive = state.manga.source == MERGED_SOURCE_ID || state.mergedData != null,
            onMergeClick = onMergeClicked,
            coversAvailable = coversAvailable,
            onCoversClick = onCoversClicked,
            onNotesClick = onEditNotesClicked,
            modifier = modifier,
        )
    }
    DeclareShellTop(
        title = if (selected.isNotEmpty()) "${selected.size} selected" else state.manga.title,
        glass = glass,
        navigateUp = if (selected.isNotEmpty()) {
            { onAllChapterSelected(false) }
        } else {
            navigateUp
        },
        backLabel = if (selected.isNotEmpty()) "Cancel selection" else "Back",
        backIcon = if (selected.isNotEmpty()) TsuzukiIcons.X else TsuzukiIcons.Back,
        actions = if (selected.isNotEmpty()) {
            listOf(
                ShellTopAction("Select all", TsuzukiIcons.ListChecks, { onAllChapterSelected(true) }),
                ShellTopAction("Invert selection", TsuzukiIcons.ArrowDownUp, onInvertSelection),
            )
        } else {
            buildList {
                onShareClicked?.let { add(ShellTopAction("Share", TsuzukiIcons.Share, it)) }
                add(ShellTopAction("Check for new chapters", TsuzukiIcons.RefreshCw, onRefresh, enabled = !state.isRefreshingData))
                add(
                    ShellTopAction(
                        "More title actions",
                        TsuzukiIcons.Ellipsis,
                        {},
                        menuTitle = state.manga.title,
                        menu = menuActions,
                    ),
                )
            }
        },
        inlineTitle = { if (selected.isNotEmpty() || collapsed) 1f else 0f },
        tint = dev.errnolink.tsuzuki.ui.shell.MangaHero.cardColor(state.manga.id),
    )
    DetailsCardTheme(state.manga.id) {
    Box(Modifier.fillMaxSize().background(TsuzukiTheme.colors.background)) {
        Box(Modifier.fillMaxSize().heroDismissDrag { !listState.canScrollBackward && selected.isEmpty() }) {
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                state = listState,
                modifier = Modifier.fillMaxSize().glassSource(glass),
                contentPadding = PaddingValues(top = top, bottom = bottom),
            ) {
                item("hero") {
                    Box(
                        heroTitle.hero(Modifier)
                            .fillMaxWidth()
                            .detailsHeroGlow(
                                glow = glow,
                                topInset = top,
                                isDark = TsuzukiTheme.colors.isDark,
                                backgroundColor = TsuzukiTheme.colors.background,
                                strength = if (dev.errnolink.tsuzuki.ui.shell.MangaHero.cardColor(state.manga.id) != null) CardGlowStrength else 1f,
                            ),
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalHeroTitleAnchor provides heroTitle) {
                        if (appleHeader) {
                            MangaInfoHeaderApple(
                                appBarPadding = 0.dp,
                                manga = state.manga,
                                accent = glow.accent.value.takeIf { dev.errnolink.tsuzuki.ui.shell.MangaHero.cardColor(state.manga.id) == null },
                                sourceName = state.source.getNameForMangaInfo(state.mergedData?.sources),
                                sourceLanguage = state.source.lang.uppercase(),
                                isStubSource = state.source is StubSource,
                                isSourceIncognito = state.source.isIncognitoModeEnabled(),
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                                librarySearch = librarySearch,
                                onSourceClick = onSourceClick,
                                coverRatio = coverRatio,
                                onCoverLoaded = { heroCoverReady = true },
                                chapterCount = chapterCount,
                                mergedSourceCount = state.mergedData?.sources?.count { it.id != MERGED_SOURCE_ID } ?: 0,
                                actionRow = { detailsActionRow(Modifier) },
                            )
                        } else {
                            MangaInfoBox(
                                isTabletUi = isTabletUi, appBarPadding = 0.dp, manga = state.manga,
                                sourceName = state.source.getNameForMangaInfo(state.mergedData?.sources),
                                sourceLanguage = state.source.lang.uppercase(),
                                isStubSource = state.source is StubSource,
                                isSourceIncognito = state.source.isIncognitoModeEnabled(),
                                onCoverClick = onCoverClicked, doSearch = onSearch, librarySearch = librarySearch,
                                onSourceClick = onSourceClick, coverRatio = coverRatio,
                                showMangaDexRating = isMdTitle,
                                mergedSourceCount = state.mergedData?.sources?.count { it.id != MERGED_SOURCE_ID } ?: 0,
                                chapterCount = chapterCount,
                                lastChapterUpdate = heroLastUpdate,
                                onRatingClick = { ratingSheetVisible = true },
                                onCoverLoaded = { heroCoverReady = true },
                            )
                        }
                        }
                    }
                }
                if (!appleHeader) {
                    item("actions") {
                        detailsActionRow(Modifier.padding(horizontal = 0.dp, vertical = 16.dp))
                    }
                }
                item("synopsis") {
                    SynopsisPreview(
                        manga = state.manga,
                        onOpenAbout = { aboutVisible = true },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                if (isMdTitle) {
                    item("segmented") {
                        SegmentedControl(
                            options = listOf("Chapters", "Similar"),
                            selectedIndex = detailsTab,
                            onSelect = { detailsTab = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                if (detailsTab == 1 && isMdTitle) {
                    item("similar") {
                        Box(
                            Modifier
                                .animateItem(
                                    fadeInSpec = if (motion.reduced) snap() else tween(M3Motion.Medium1, delayMillis = M3Motion.Short2, easing = M3Motion.Standard),
                                    placementSpec = null,
                                    fadeOutSpec = if (motion.reduced) snap() else tween(M3Motion.Short2, easing = M3Motion.Standard),
                                )
                                .fillParentMaxHeight(),
                        ) {
                            MangaDexSimilarGrid(mangaId = state.manga.id, modifier = Modifier.fillMaxSize())
                        }
                    }
                } else {
                    item(key = "chapters-header") {
                        ChapterHeaderRow(
                            count = chapterCount.takeUnless { state.chaptersPending },
                            summary = activeFilters,
                            checking = state.isRefreshingData && !state.chaptersPending,
                            onDownloadActionClicked = onDownloadActionClicked,
                            onDownloadAllClicked = onDownloadAllClicked,
                            onRemoveDownloadsClicked = onRemoveDownloadsClicked,
                            onFilterClick = onFilterButtonClicked,
                            chapterSettingsMenu = chapterSettingsMenu,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = if (motion.reduced) snap() else tween(M3Motion.Medium1, delayMillis = M3Motion.Short2, easing = M3Motion.Standard),
                                    placementSpec = null,
                                    fadeOutSpec = if (motion.reduced) snap() else tween(M3Motion.Short2, easing = M3Motion.Standard),
                                )
                                .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                    if (state.chaptersPending) {
                        items(count = 6, key = { "chapter-skeleton-$it" }) { index ->
                            DetailChapterRowSkeleton(index, Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (chapters.isEmpty() && !state.chaptersPending) {
                        item("empty") {
                            Banner(
                                when {
                                    state.isRefreshingData || !state.manga.initialized -> "Loading chapters…"
                                    activeFilters.isNotEmpty() -> "No chapters match $activeFilters. Change your filters to see other chapters."
                                    else -> "No chapters found. Refresh this title to check for new chapters."
                                },
                                Modifier.padding(20.dp),
                                actionLabel = if (activeFilters.isNotEmpty()) "Filters" else "Refresh",
                                onAction = if (activeFilters.isNotEmpty()) onFilterButtonClicked else onRefresh,
                            )
                        }
                    }
                    itemsIndexed(
                        visibleRows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> row !is ChapterRowEntry.Gap },
                    ) { index, row ->
                        val item = when (row) {
                            is ChapterRowEntry.Gap -> null
                            is ChapterRowEntry.Group -> row.primary
                            is ChapterRowEntry.Alternate -> row.item
                        }
                        if (row is ChapterRowEntry.Gap || item == null) {
                            TsuzukiText(
                                "${(row as ChapterRowEntry.Gap).missing.count} missing chapters",
                                Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
                                TsuzukiTheme.typography.footnote,
                                TsuzukiTheme.colors.secondary,
                            )
                        } else {
                            val haptic = LocalHapticFeedback.current
                            val child = state.mergedData?.manga?.get(item.chapter.mangaId)
                            val roundedTop = rowShapes.getOrElse(index * 2) { true }
                            val roundedBottom = rowShapes.getOrElse(index * 2 + 1) { true }
                            val group = row as? ChapterRowEntry.Group
                            val alternates = group?.alternates?.size ?: 0
                            val groupOpen = group != null && group.key in openGroups
                            MangaChapterListItem(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = if (motion.reduced) snap() else tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3, easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard),
                                        placementSpec = if (motion.reduced) snap() else motion.tabSpring(),
                                        fadeOutSpec = if (motion.reduced) snap() else tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3, easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard),
                                    )
                                    .padding(horizontal = 20.dp)
                                    .readerOrigin(),
                                shape = when {
                                    roundedTop && roundedBottom -> ChapterRowShape
                                    roundedTop -> ChapterRowTopShape
                                    roundedBottom -> ChapterRowBottomShape
                                    else -> ChapterRowMiddleShape
                                },
                                title = if (state.manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) stringResource(MR.strings.display_mode_chapter, formatChapterNumber(item.chapter.chapterNumber)) else item.chapter.name,
                                date = item.chapter.dateUpload.takeIf { it > 0 }?.let { relativeDateText(it) },
                                lastPageRead = item.chapter.lastPageRead.takeIf { it > 0 && (!item.chapter.read || state.alwaysShowReadingProgress) },
                                pageTotal = chapterPageTotals(item.chapter.id),
                                scanlator = item.chapter.scanlator,
                                otherGroups = if (groupOpen) 0 else alternates,
                                unavailable = dex.unavailable(item.chapter),
                                languageLabel = dex.languageLabel(item.chapter),
                                sourceName = item.sourceName ?: child?.let { manga -> state.mergedData.sources.firstOrNull { it.id == manga.source }?.name ?: "Source ${manga.source}" },
                                read = item.chapter.read, bookmark = item.chapter.bookmark, selected = item.selected,
                                downloadIndicatorEnabled = selected.isEmpty() && !(child ?: state.manga).isLocal(),
                                downloadStateProvider = { item.downloadState }, downloadProgressProvider = { item.downloadProgress },
                                chapterSwipeStartAction = chapterSwipeStartAction, chapterSwipeEndAction = chapterSwipeEndAction,
                                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChapterSelected(item, !item.selected, true) },
                                onClick = {
                                    if (selected.isNotEmpty()) onChapterSelected(item, !item.selected, false)
                                    else {
                                        val message = if (item.isDownloaded) null else dex.accessMessage(item.chapter)
                                        if (message != null) context.toast(message) else onChapterClicked(item.chapter)
                                    }
                                },
                                onDownloadClick = onDownloadChapter?.let { action -> { action(listOf(item), it) } },
                                onChapterSwipe = { onChapterSwipe(item, it) },
                                nested = row is ChapterRowEntry.Alternate,
                                actions = {
                                    if (group != null && alternates > 0 && selected.isEmpty()) {
                                        ChapterGroupToggle(
                                            open = groupOpen,
                                            alternates = alternates,
                                            onToggle = { openGroups = if (groupOpen) openGroups - group.key else openGroups + group.key },
                                        )
                                    }
                                    val source = child?.let { manga -> state.mergedData.sources.firstOrNull { it.id == manga.source } } ?: state.source
                                    if (selected.isEmpty() && source.getMainSource<MetadataSource<*, *>>() is MangaDex) {
                                        MangaDexChapterMenu(item.chapter)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        MangaBottomActionMenu(
            visible = selected.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter),
            glass = glass,
            onBookmarkClicked = { onMultiBookmarkClicked(selected.map { it.chapter }, true) }.takeIf { selected.any { !it.chapter.bookmark } },
            onRemoveBookmarkClicked = { onMultiBookmarkClicked(selected.map { it.chapter }, false) }.takeIf { selected.all { it.chapter.bookmark } },
            onMarkAsReadClicked = { onMultiMarkAsReadClicked(selected.map { it.chapter }, true) }.takeIf { selected.any { !it.chapter.read } },
            onMarkAsUnreadClicked = { onMultiMarkAsReadClicked(selected.map { it.chapter }, false) }.takeIf { selected.any { it.chapter.read || it.chapter.lastPageRead > 0 } },
            onMarkPreviousAsReadClicked = { onMarkPreviousAsReadClicked(selected.single().chapter) }.takeIf { selected.size == 1 },
            onDownloadClicked = onDownloadChapter?.let { action -> { action(selected, ChapterDownloadAction.START) } }?.takeIf { selected.any { !it.isDownloaded } },
            onDeleteClicked = { onMultiDeleteClicked(selected.map { it.chapter }) }.takeIf { selected.any { it.isDownloaded } },
        )
        ShellSnackbar(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
        )
    }
    }
    DetailsAboutSheet(
        visible = aboutVisible,
        manga = state.manga,
        searchMetadataChips = searchMetadataChips,
        showMangaDexSection = isMdTitle,
        mangaId = state.manga.id,
        onDismissRequest = { aboutVisible = false },
        onTagSearch = onTagSearch,
        onCopyTagToClipboard = { context.copyToClipboard(it, it) },
        doSearch = onSearch,
    )
    MangaDexRatingSheet(
        mangaId = state.manga.id,
        visible = ratingSheetVisible,
        onDismissRequest = { ratingSheetVisible = false },
    )
    PullDownConfirmSheet(
        visible = clearConfirmVisible,
        title = "Clear title data",
        message = "Are you sure you want to clear title data? This removes cached chapters and progress.",
        confirmLabel = "Clear data",
        onConfirm = onClearManga,
        onDismissRequest = { clearConfirmVisible = false },
    )
    ShellActionSheet(
        visible = groupSheetVisible,
        title = "Open chapters from",
        actions = remember(chapterListItems, preferredGroup) {
            listOf(
                ShellSheetAction(if (preferredGroup == null) "Automatic (current)" else "Automatic") {
                    PreferredGroups.set(state.manga.id, null)
                    preferredGroup = null
                },
            ) + scanlatorGroups(chapterListItems).map { group ->
                val current = group.name.equals(preferredGroup, ignoreCase = true)
                ShellSheetAction("${group.name} · ${group.chapters} ${if (group.chapters == 1) "chapter" else "chapters"}" + if (current) " (current)" else "") {
                    PreferredGroups.set(state.manga.id, group.name)
                    preferredGroup = group.name
                }
            }
        },
        onDismissRequest = { groupSheetVisible = false },
    )
}

/** Two-line description preview with a trailing accent "more" that opens the About sheet. */
@Composable
private fun SynopsisPreview(
    manga: Manga,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = manga.description
    if (description.isNullOrBlank()) return
    Box(modifier.fillMaxWidth().clickable(onClick = onOpenAbout)) {
        androidx.compose.foundation.text.BasicText(
            text = description,
            style = TsuzukiTheme.typography.callout.copy(color = TsuzukiTheme.colors.secondary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
        )
        TsuzukiText(
            "more",
            Modifier.align(Alignment.BottomEnd),
            TsuzukiTheme.typography.headline,
            TsuzukiTheme.colors.accent,
        )
    }
}

@Composable
private fun ChapterHeaderRow(
    count: Int?,
    summary: String,
    checking: Boolean,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onDownloadAllClicked: (() -> Unit)?,
    onRemoveDownloadsClicked: (() -> Unit)?,
    onFilterClick: () -> Unit,
    chapterSettingsMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var downloadsExpanded by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TsuzukiText(count?.let { "$it chapters" } ?: "Chapters", style = TsuzukiTheme.typography.headline)
            if (checking) {
                Spacer(Modifier.width(8.dp))
                ProgressRing(0f, "Checking for new chapters", Modifier.size(16.dp))
            }
            if (summary.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TsuzukiText(
                    summary,
                    Modifier.weight(1f, fill = false),
                    TsuzukiTheme.typography.footnote,
                    TsuzukiTheme.colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDownloadActionClicked != null) {
            Box {
                DetailIcon("Download chapters", TsuzukiIcons.Download, { downloadsExpanded = true })
                PullDownMenu(
                    expanded = downloadsExpanded,
                    onDismissRequest = { downloadsExpanded = false },
                    paneTitle = "Download chapters",
                ) {
                    PullDownMenuItem("Next chapter", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.NEXT_1_CHAPTER)
                    })
                    PullDownDivider()
                    PullDownMenuItem("Next 5 chapters", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.NEXT_5_CHAPTERS)
                    })
                    PullDownDivider()
                    PullDownMenuItem("Next 10 chapters", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.NEXT_10_CHAPTERS)
                    })
                    PullDownDivider()
                    PullDownMenuItem("Next 25 chapters", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.NEXT_25_CHAPTERS)
                    })
                    PullDownSectionDivider()
                    PullDownMenuItem("Unread chapters", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.UNREAD_CHAPTERS)
                    })
                    PullDownDivider()
                    PullDownMenuItem("Bookmarked chapters", icon = TsuzukiIcons.Download, onClick = {
                        downloadsExpanded = false
                        onDownloadActionClicked(DownloadAction.BOOKMARKED_CHAPTERS)
                    })
                    if (onDownloadAllClicked != null) {
                        PullDownDivider()
                        PullDownMenuItem("All chapters", icon = TsuzukiIcons.Download, onClick = {
                            downloadsExpanded = false
                            onDownloadAllClicked()
                        })
                    }
                    if (onRemoveDownloadsClicked != null) {
                        PullDownSectionDivider()
                        PullDownMenuItem("Remove downloads", icon = TsuzukiIcons.Trash2, destructive = true, onClick = {
                            downloadsExpanded = false
                            onRemoveDownloadsClicked()
                        })
                    }
                }
            }
        }
        Box {
            DetailIcon("Filter and sort chapters", TsuzukiIcons.SlidersHorizontal, onFilterClick)
            chapterSettingsMenu()
        }
    }
}

private fun detailMenuAction(
    label: String,
    icon: ImageVector,
    subtitle: String? = null,
    group: Int = 0,
    destructive: Boolean = false,
    onClick: () -> Unit,
) = PullDownAction(
    label = label,
    icon = icon,
    subtitle = subtitle,
    group = group,
    destructive = destructive,
    onClick = onClick,
)

private val ChapterRowShape = RoundedCornerShape(TsuzukiCorners.cover)
private val ChapterRowTopShape = RoundedCornerShape(topStart = TsuzukiCorners.cover, topEnd = TsuzukiCorners.cover)
private val ChapterRowBottomShape = RoundedCornerShape(bottomStart = TsuzukiCorners.cover, bottomEnd = TsuzukiCorners.cover)
private val ChapterRowMiddleShape = RoundedCornerShape(0.dp)

private const val CardGlowStrength = 0.45f
