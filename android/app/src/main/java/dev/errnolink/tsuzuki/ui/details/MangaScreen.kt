package dev.errnolink.tsuzuki.ui.details

import dev.errnolink.tsuzuki.reader.readerOrigin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.errnolink.tsuzuki.ui.shell.DeclareShellTop
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.motion.heroDismissDrag
import dev.errnolink.tsuzuki.ui.motion.LocalNavigationProgress
import dev.errnolink.tsuzuki.ui.motion.ZoomReveal
import dev.errnolink.tsuzuki.ui.motion.heroReveal
import eu.kanade.tachiyomi.ui.manga.MangaCoverScreenModel
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.LocalMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.MenuMorphSource
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Check
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
import dev.errnolink.tsuzuki.designsystem.Palette
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Gear
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.Sparkles
import dev.errnolink.tsuzuki.designsystem.Tag
import dev.errnolink.tsuzuki.ui.shell.ShellToasts
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import eu.kanade.tachiyomi.BuildConfig
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.ToastAction
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import dev.errnolink.tsuzuki.mangadex.MangaDexChapterMenu
import dev.errnolink.tsuzuki.mangadex.MangaDexRatingSheet
import dev.errnolink.tsuzuki.mangadex.MangaDexSimilarGrid
import dev.errnolink.tsuzuki.mangadex.rememberMangaDexChapterPresentation
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.util.system.openInBrowser
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
import dev.errnolink.tsuzuki.mangadex.AutoTrackState
import exh.md.handlers.AutoAddTrackers
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.StubSource
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.i18n.MR
import androidx.compose.runtime.Immutable
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
fun MangaScreen(
    state: MangaScreenModel.State.Success,
    toasts: ToastHostState,
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
    val headerListState = rememberLazyListState()
    val bodyListState = rememberLazyListState()
    val twoPane = LocalWindowWidthClass.current == WindowWidthClass.Expanded
    val glass = rememberGlassContext()
    val context = LocalContext.current
    val linkedToPattern = stringResource(MR.strings.tsuzuki_linked_to)
    val undoLabel = stringResource(MR.strings.action_undo)
    val undoScope = rememberCoroutineScope()
    LaunchedEffect(state.manga.id, toasts) {
        AutoAddTrackers.events.collect { event ->
            if (event.mangaId != state.manga.id) return@collect
            toasts.show(
                linkedToPattern.format(event.trackerName),
                action = ToastAction(undoLabel) {
                    AutoTrackState().cacheNoLink(event.mangaId, setOf(event.service), System.currentTimeMillis())
                    undoScope.launch { Injekt.get<DeleteTrack>().await(event.mangaId, event.trackerId) }
                },
            )
        }
    }
    val dex = rememberMangaDexChapterPresentation(state.manga.id, remember(state.chapters) { state.chapters.map { it.chapter } })
    val chapterPageTotals = rememberPageTotals()
    val chapters = remember(state.processedChapters, dex) { state.processedChapters.filter { dex.isVisible(it.chapter) } }
    val heroLastUpdate = remember(chapters, state.manga.lastUpdate) {
        chapters.asSequence().map { it.chapter.dateUpload }.filter { it > 0 }.maxOrNull() ?: state.manga.lastUpdate
    }
    val chapterListItems = if (chapters.size == state.processedChapters.size) state.chapterListItems else chapters
    // DET-05: the chapter list keeps the structure it opened with until the stack flight
    // settles — data landing mid-flight never re-lays the page out under the zoom (skeleton
    // rows stay skeletons; a seeded open with its rows final keeps them). Released once, never
    // re-held, so the page leaving again cannot flash skeletons over its rows (back layers
    // read a settled progress by the DET-04 rule in StackTransition).
    val navProgress = LocalNavigationProgress.current
    val flightSettled by remember(state.manga.id) {
        derivedStateOf { navProgress() >= 0.88f }
    }
    var bodyReleased by remember(state.manga.id) { mutableStateOf(!state.chaptersPending) }
    LaunchedEffect(state.manga.id, flightSettled) {
        if (flightSettled) bodyReleased = true
    }
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
    val scrolledUnderChrome by remember(headerListState, bodyListState) {
        derivedStateOf {
            dev.errnolink.tsuzuki.ui.shell.scrolledUnder(headerListState.firstVisibleItemIndex, headerListState.firstVisibleItemScrollOffset, 0f) ||
                dev.errnolink.tsuzuki.ui.shell.scrolledUnder(bodyListState.firstVisibleItemIndex, bodyListState.firstVisibleItemScrollOffset, 0f)
        }
    }
    val collapsed by remember(headerListState) {
        derivedStateOf {
            val hero = headerListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
            dev.errnolink.tsuzuki.designsystem.headerScrolledAway(
                hero?.offset,
                heroTitle.titleBottomInHero ?: hero?.size ?: 0,
                headerListState.firstVisibleItemIndex,
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
    val headerCentered = appleHeader || twoPane
    val coversAvailable = remember(state.source.id, state.manga.source, state.mergedData, onCoversClicked) {
        onCoversClicked != null && (
            state.source.getMainSource<MetadataSource<*, *>>() is MangaDex ||
            state.mergedData?.sources?.any { it.getMainSource<MetadataSource<*, *>>() is MangaDex } == true
            )
    }
    val filterLabels = activeFilterLabels()
    val activeFilters = remember(
        state.manga.downloadedFilter,
        state.manga.unreadFilter,
        state.manga.bookmarkedFilter,
        state.scanlatorFilterActive,
        dex.selected,
        chapters.size,
        state.processedChapters.size,
        filterLabels,
    ) {
        buildList {
            when (state.manga.downloadedFilter) {
                TriState.ENABLED_IS -> add(filterLabels.downloadedOnly)
                TriState.ENABLED_NOT -> add(filterLabels.notDownloaded)
                TriState.DISABLED -> Unit
            }
            when (state.manga.unreadFilter) {
                TriState.ENABLED_IS -> add(filterLabels.unreadOnly)
                TriState.ENABLED_NOT -> add(filterLabels.readOnly)
                TriState.DISABLED -> Unit
            }
            when (state.manga.bookmarkedFilter) {
                TriState.ENABLED_IS -> add(filterLabels.bookmarkedOnly)
                TriState.ENABLED_NOT -> add(filterLabels.notBookmarked)
                TriState.DISABLED -> Unit
            }
            if (state.scanlatorFilterActive) add(filterLabels.scanlatorFilter)
            if (dex.selected.isNotEmpty()) add(dex.selected.joinToString { it.uppercase() })
            if (chapters.size != state.processedChapters.size) add(filterLabels.mangadexFilters)
        }.joinToString(" · ")
    }

    val words = continueWords()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = bottomInset + animateDpAsState(
        if (selected.isEmpty()) 24.dp else 88.dp,
        if (motion.reduced) snap() else KasaneSprings.tab(),
        label = "Selection actions inset",
    ).value
    val startOverChapter = if (visibleContinueChapter == null && activeFilters.isEmpty()) chapters.lastOrNull()?.chapter else null
    // T2 (round 11): the tapped tile already knows the next chapter (the library row's continue
    // target) and the cover's dominant colour, so the Continue button is right on the page's first
    // frame instead of arriving as `Continue` and popping to `Continue · Ch. N` ~250 ms later.
    val heroTapSeed = remember(state.manga.id) { dev.errnolink.tsuzuki.ui.motion.MangaHero.tapSeed(state.manga.id) }
    val seededContinueLabel = remember(heroTapSeed, words) { heroTapSeed?.let { continueSeedLabel(it, words) } }
    val chaptersKnown = !state.chaptersPending
    // N12: the last values this page resolved make the second open instant, and before anything is
    // known the button shows no label at all — never a `Continue` that swaps to `Start reading`.
    val rememberedTitle = remember(state.manga.id) { TitlePageMemory.recall(state.manga.id) }
    val nextUpdateText = nextUpdate?.let { stringResource(MR.strings.tsuzuki_next_update, relativeDateText(it.toEpochMilli())) }
    val continueLabel: String? = when {
        chaptersKnown -> when {
            visibleContinueChapter == null && activeFilters.isNotEmpty() -> stringResource(MR.strings.tsuzuki_no_matching_chapters)
            visibleContinueChapter == null && startOverChapter != null ->
                stringResource(MR.strings.tsuzuki_start_over) + startOverChapter.takeIf { it.chapterNumber.isFinite() && it.isRecognizedNumber }
                    ?.let { " · " + stringResource(MR.strings.tsuzuki_ch_short, formatChapterNumber(it.chapterNumber)) }.orEmpty()
            visibleContinueChapter == null -> stringResource(MR.strings.tsuzuki_no_unread_chapters)
            visibleContinueChapter.lastPageRead > 0 || state.chapters.any { it.chapter.read } ->
                continueChapterLabel(visibleContinueChapter, words)
            else -> stringResource(MR.strings.tsuzuki_start_reading)
        }
        seededContinueLabel != null -> seededContinueLabel
        rememberedTitle?.continueLabel != null -> rememberedTitle.continueLabel
        else -> null
    }
    val continueEnabled = if (chaptersKnown) {
        visibleContinueChapter != null || startOverChapter != null
    } else {
        seededContinueLabel != null || rememberedTitle?.continueEnabled == true
    }
    LaunchedEffect(chaptersKnown, chapterCount, continueLabel, continueEnabled) {
        if (chaptersKnown) {
            TitlePageMemory.remember(state.manga.id, chapterCount, continueLabel, continueEnabled)
        }
    }
    var queuedContinue by remember(state.manga.id) { mutableStateOf(false) }
    // The count the page shows while its own chapter data is still loading: the cached one, else
    // none — the header and metadata reserve the slot instead of guessing (N12).
    val displayChapterCount = when {
        chaptersKnown -> chapterCount
        rememberedTitle?.chapterCount != null -> rememberedTitle.chapterCount
        else -> null
    }

    val seededDominant = remember(state.manga.id) {
        // U1/T2: the tile's cover colour (prepared at tap time) is the same seed the page scheme
        // uses, so Continue, menus and checks all speak one accent and it is known before frame 0.
        dev.errnolink.tsuzuki.ui.motion.MangaHero.peekCardColor(state.manga.id)
            ?: tachiyomi.domain.manga.model.MangaCover.dominantCoverColorMap[state.manga.id]?.first?.let { Color(it) }
    }
    val stableDominant = remember(state.manga.id) { mutableStateOf(seededDominant) }
    val glowAccent = glow.accent.value
    if (glowAccent != null && stableDominant.value == null) stableDominant.value = glowAccent
    val themeColors = TsuzukiTheme.colors
    val pendingActionColor = if (themeColors.isDark) themeColors.selectedFill else themeColors.secondary
    val actionColor = androidx.compose.animation.animateColorAsState(
        targetValue = stableDominant.value?.let { ContinueActionColor.forCover(it, themeColors.accent) } ?: pendingActionColor,
        animationSpec = if (TsuzukiTheme.motion.reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(220),
        label = "continueActionColor",
    )
    val unreadCount = remember(chapterRows) {
        chapterRows.count { it is ChapterRowEntry.Group && !it.primary.chapter.read && it.alternates.none { alt -> alt.chapter.read } }
    }
    val runContinue: () -> Unit = {
        val chapter = visibleContinueChapter ?: startOverChapter
        chapter?.let {
            val downloaded = chapters.firstOrNull { entry -> entry.id == it.id }?.isDownloaded == true
            val message = if (downloaded) null else dex.accessMessage(it)
            if (message != null) context.toast(message)
            else if (it == continueChapter) onContinueReading()
            else onChapterClicked(it)
        }
    }
    val openContinue: () -> Unit = {
        if (chaptersKnown) runContinue() else queuedContinue = true
    }
    LaunchedEffect(chaptersKnown) {
        // a tap on the seeded (still loading) button runs as soon as the chapters are in
        if (chaptersKnown && queuedContinue) {
            queuedContinue = false
            runContinue()
        }
    }
    val searchMetadataChips = remember(state.meta, state.manga.genre) { SearchMetadataChips(state.meta, state.source.id, state.manga.genre) }
    val menuWords = titleMenuWords()
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
        menuWords,
    ) {
        buildList {
            // Group 0: library management (SPEC "More menu": Edit categories, Migrate first)
            onEditCategoryClicked?.let { add(detailMenuAction(menuWords.editCategories, TsuzukiIcons.Tag, group = 0, onClick = it)) }
            onMigrateClicked?.let { add(detailMenuAction(menuWords.migrate, TsuzukiIcons.ArrowLeftRight, group = 0, onClick = it)) }
            // Group 1: web
            onWebViewClicked?.let { add(detailMenuAction(menuWords.openInBrowser, TsuzukiIcons.Globe, group = 1, onClick = it)) }
            onWebViewLongClicked?.let { add(detailMenuAction(menuWords.copyLink, TsuzukiIcons.Copy, group = 1, onClick = it)) }
            // Group 2: discovery inline, the per-title options behind one chevron row (iOS Photos
            // "View Options ›"), so the menu stays short and clear of the cover.
            add(detailMenuAction(menuWords.relatedTitles, TsuzukiIcons.Library, group = 2, onClick = onRelatedMangasScreenClick))
            add(detailMenuAction(menuWords.recommendations, TsuzukiIcons.Sparkles, group = 2, onClick = onRecommendClicked))
            val titleOptions = buildList {
                if (state.manga.favorite) add(detailMenuAction(menuWords.editTitleInformation, TsuzukiIcons.Pencil, onClick = onEditInfoClicked))
                onEditFetchIntervalClicked?.let { add(detailMenuAction(menuWords.updateSchedule, TsuzukiIcons.Calendar, subtitle = nextUpdateText, onClick = it)) }
                if (hasGroupChoice) {
                    add(detailMenuAction(menuWords.preferredGroup, TsuzukiIcons.ListChecks, subtitle = preferredGroup ?: menuWords.automatic, onClick = { groupSheetVisible = true }))
                }
                onOpenMangaFolder?.let { add(detailMenuAction(menuWords.openDownloadFolder, TsuzukiIcons.Folder, onClick = it)) }
                if (state.manga.source == MERGED_SOURCE_ID) {
                    add(detailMenuAction(menuWords.sourcesAndMergeSettings, TsuzukiIcons.Link, onClick = onMergedSettingsClicked))
                }
                if (state.showMergeWithAnother) {
                    add(detailMenuAction(menuWords.mergeWithAnotherTitle, TsuzukiIcons.Link, onClick = onMergeWithAnotherClicked))
                }
                onClickSourceSettingsClicked?.let { add(detailMenuAction(menuWords.sourceSettings, TsuzukiIcons.Gear, group = 1, onClick = it)) }
                if (state.meta != null) add(detailMenuAction(menuWords.sourceMetadata, TsuzukiIcons.Info, group = 1, onClick = onMetadataViewerClicked))
                if (BuildConfig.DEBUG) {
                    onPaletteClicked?.let { add(detailMenuAction(menuWords.palette, TsuzukiIcons.Palette, group = 1, onClick = it)) }
                }
            }
            if (titleOptions.isNotEmpty()) {
                add(PullDownAction(label = menuWords.titleOptions, icon = TsuzukiIcons.SlidersHorizontal, group = 2, submenu = titleOptions, onClick = {}))
            }
            // Group 3: destructive, Remove from library last
            add(detailMenuAction(menuWords.clearTitleData, TsuzukiIcons.Trash2, group = 3, destructive = true, onClick = { clearConfirmVisible = true }))
            if (state.manga.favorite) {
                add(detailMenuAction(menuWords.removeFromLibrary, TsuzukiIcons.Trash2, group = 3, destructive = true, onClick = onAddToLibraryClicked))
            }
        }
    }
    BackHandler(selected.isNotEmpty()) { onAllChapterSelected(false) }
    BackHandler(enabled = aboutVisible || ratingSheetVisible) { aboutVisible = false; ratingSheetVisible = false }
    // Cover actions for the action-row menu: the same handlers the cover dialog uses.
    val coverModel = remember(state.manga.id) { MangaCoverScreenModel(state.manga.id) }
    LaunchedEffect(coverModel) {
        snapshotFlow { coverModel.snackbarHostState.currentSnackbarData }.collect { data ->
            data ?: return@collect
            data.dismiss()
            toasts.show(
                data.visuals.message,
                action = data.visuals.actionLabel?.let { label -> ToastAction(label) { data.performAction() } },
            )
        }
    }
    val coverImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { coverModel.editCover(context, it) }
    }
    val coverSavePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) coverModel.saveCover(context)
    }
    val saveCoverAction = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            coverSavePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            coverModel.saveCover(context)
        }
    }
    val detailsActionRow: @Composable (Modifier) -> Unit = { modifier ->
        DetailsActionRow(
            mangaId = state.manga.id,
            mangaTitle = state.manga.title,
            sourceId = state.source.id,
            glass = glass,
            continueLabel = continueLabel,
            continueEnabled = continueEnabled,
            actionColor = { if (chaptersKnown || stableDominant.value != null) actionColor.value else pendingActionColor },
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
            notesEditor = { onDone -> DetailsNotesEditor(manga = state.manga, onDone = onDone) },
            onEditCategories = onEditCategoryClicked,
            onRemoveFromLibrary = onAddToLibraryClicked.takeIf { state.manga.favorite },
            onSaveCover = saveCoverAction,
            onShareCover = { coverModel.shareCover(context) },
            onSetCustomCover = { coverImagePicker.launch("image/*") }.takeIf { state.manga.favorite },
            modifier = modifier,
        )
    }
    val detailsHeaderItems: LazyListScope.() -> Unit = {
        item("hero") {
            Box(
                heroTitle.hero(Modifier)
                    .fillMaxWidth()
                    .detailsHeroGlow(
                        glow = glow,
                        topInset = top,
                        isDark = TsuzukiTheme.colors.isDark,
                        backgroundColor = TsuzukiTheme.colors.background,
                        strength = if (dev.errnolink.tsuzuki.ui.motion.MangaHero.cardColor(state.manga.id) != null) CardGlowStrength else 1f,
                    ),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalHeroTitleAnchor provides heroTitle) {
                    Box(Modifier.heroReveal(state.manga.id, ZoomReveal.Header)) {
                        if (headerCentered) {
                            MangaInfoHeaderApple(
                                appBarPadding = 0.dp,
                                manga = state.manga,
                                accent = glow.accent.value.takeIf { dev.errnolink.tsuzuki.ui.motion.MangaHero.cardColor(state.manga.id) == null },
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
                                chapterCount = displayChapterCount,
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
                                chapterCount = displayChapterCount,
                                lastChapterUpdate = heroLastUpdate,
                                onRatingClick = { ratingSheetVisible = true },
                                onCoverLoaded = { heroCoverReady = true },
                            )
                        }
                    }
                }
            }
        }
        if (!headerCentered) {
            item("actions") {
                detailsActionRow(
                    Modifier
                        .heroReveal(state.manga.id, ZoomReveal.Actions)
                        .padding(top = 16.dp),
                )
            }
        }
        item("synopsis") {
            SynopsisPreview(
                manga = state.manga,
                onOpenAbout = { aboutVisible = true },
                modifier = Modifier
                    .heroReveal(state.manga.id, ZoomReveal.Actions)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
        }
    }
    val detailsBodyItems: LazyListScope.() -> Unit = {
        if (isMdTitle) {
            item("segmented") {
                SegmentedControl(
                    options = listOf(stringResource(MR.strings.chapters), stringResource(MR.strings.tsuzuki_similar)),
                    selectedIndex = detailsTab,
                    onSelect = { detailsTab = it },
                    modifier = Modifier
                        .heroReveal(state.manga.id, ZoomReveal.Body)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    count = displayChapterCount,
                    summary = activeFilters,
                    checking = state.isRefreshingData && !state.chaptersPending,
                    onRefresh = onRefresh.takeIf { !state.isRefreshingData },
                    onDownloadActionClicked = onDownloadActionClicked,
                    onDownloadAllClicked = onDownloadAllClicked,
                    onRemoveDownloadsClicked = onRemoveDownloadsClicked,
                    onFilterClick = onFilterButtonClicked,
                    chapterSettingsMenu = chapterSettingsMenu,
                    modifier = Modifier
                        .heroReveal(state.manga.id, ZoomReveal.Body)
                        .animateItem(
                            fadeInSpec = if (motion.reduced) snap() else tween(M3Motion.Medium1, delayMillis = M3Motion.Short2, easing = M3Motion.Standard),
                            placementSpec = null,
                            fadeOutSpec = if (motion.reduced) snap() else tween(M3Motion.Short2, easing = M3Motion.Standard),
                        )
                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            // DET-05 / zoom guide: the list area stays empty while the zoom moves; skeleton rows
            // only once it settled with chapters still loading, and real rows fade in after it.
            if (state.chaptersPending && bodyReleased) {
                items(count = 6, key = { "chapter-skeleton-$it" }) { index ->
                    DetailChapterRowSkeleton(index, Modifier.padding(horizontal = 16.dp))
                }
            }
            // DET-05: the empty banner and the real rows arrive only once the flight settled.
            if (bodyReleased && chapters.isEmpty() && !state.chaptersPending) {
                item("empty") {
                    Banner(
                        when {
                            state.isRefreshingData || !state.manga.initialized -> stringResource(MR.strings.tsuzuki_loading_chapters)
                            activeFilters.isNotEmpty() -> stringResource(MR.strings.tsuzuki_no_chapters_match, activeFilters)
                            else -> stringResource(MR.strings.tsuzuki_no_chapters_found_refresh)
                        },
                        Modifier.padding(20.dp),
                        actionLabel = if (activeFilters.isNotEmpty()) stringResource(MR.strings.action_filter) else stringResource(MR.strings.action_webview_refresh),
                        onAction = if (activeFilters.isNotEmpty()) onFilterButtonClicked else onRefresh,
                    )
                }
            }
            if (bodyReleased) itemsIndexed(
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
                        pluralStringResource(MR.plurals.missing_chapters, (row as ChapterRowEntry.Gap).missing.count, (row as ChapterRowEntry.Gap).missing.count),
                        Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
                        TsuzukiTheme.typography.footnote,
                        TsuzukiTheme.colors.secondary,
                    )
                } else {
                    val haptics = TsuzukiTheme.haptics
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
                                placementSpec = if (motion.reduced) snap() else KasaneSprings.tab(),
                                fadeOutSpec = if (motion.reduced) snap() else tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3, easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard),
                            )
                            // One inset grouped container: 16 dp side margins, 20 dp continuous
                            // corners on the first and last rows of each run.
                            .padding(horizontal = 16.dp)
                            .then(if (!twoPane) Modifier.readerOrigin() else Modifier),
                        shape = when {
                            roundedTop && roundedBottom -> ChapterRowShape
                            roundedTop -> ChapterRowTopShape
                            roundedBottom -> ChapterRowBottomShape
                            else -> ChapterRowMiddleShape
                        },
                        title = chapterRowTitle(item.chapter.name, item.chapter.chapterNumber, state.manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER, chapterRowStrings()),
                        date = item.chapter.dateUpload.takeIf { it > 0 }?.let { relativeDateText(it) },
                        lastPageRead = item.chapter.lastPageRead.takeIf { it > 0 && (!item.chapter.read || state.alwaysShowReadingProgress) },
                        pageTotal = chapterPageTotals[item.chapter.id],
                        scanlator = item.chapter.scanlator,
                        otherGroups = if (groupOpen) 0 else alternates,
                        unavailable = dex.unavailable(item.chapter),
                        languageLabel = dex.languageLabel(item.chapter),
                        sourceName = item.sourceName ?: child?.let { manga -> state.mergedData.sources.firstOrNull { it.id == manga.source }?.name ?: stringResource(MR.strings.tsuzuki_source_fallback, manga.source) },
                        read = item.chapter.read, bookmark = item.chapter.bookmark, selected = item.selected,
                        selectionMode = selected.isNotEmpty(),
                        downloadIndicatorEnabled = selected.isEmpty() && !(child ?: state.manga).isLocal(),
                        downloadStateProvider = { item.downloadState }, downloadProgressProvider = { item.downloadProgress },
                        chapterSwipeStartAction = chapterSwipeStartAction, chapterSwipeEndAction = chapterSwipeEndAction,
                        onLongClick = { haptics.lift(); onChapterSelected(item, !item.selected, true) },
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
                        showDivider = !roundedBottom,
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
    DeclareShellTop(
        title = if (selected.isNotEmpty()) pluralStringResource(MR.plurals.tsuzuki_n_selected, selected.size, selected.size) else state.manga.title,
        glass = glass,
        navigateUp = if (selected.isNotEmpty()) {
            { onAllChapterSelected(false) }
        } else {
            navigateUp
        },
        backLabel = if (selected.isNotEmpty()) stringResource(MR.strings.tsuzuki_done) else stringResource(MR.strings.tsuzuki_back),
        backIcon = if (selected.isNotEmpty()) TsuzukiIcons.Check else TsuzukiIcons.Back,
        backCheckMorph = selected.isNotEmpty(),
        actions = if (selected.isNotEmpty()) {
            listOf(
                ShellTopAction(stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, { onAllChapterSelected(true) }),
                ShellTopAction(stringResource(MR.strings.action_select_inverse), TsuzukiIcons.ArrowDownUp, onInvertSelection),
            )
        } else {
            buildList {
                onShareClicked?.let { add(ShellTopAction(stringResource(MR.strings.action_share), TsuzukiIcons.Share, it)) }
                add(
                    ShellTopAction(
                        stringResource(MR.strings.tsuzuki_more_title_actions),
                        TsuzukiIcons.Ellipsis,
                        {},
                        menuTitle = state.manga.title,
                        menu = menuActions,
                    ),
                )
            }
        },
        inlineTitle = { if (selected.isNotEmpty() || collapsed) 1f else 0f },
        scrolledUnder = scrolledUnderChrome,
        tint = dev.errnolink.tsuzuki.ui.motion.MangaHero.cardColor(state.manga.id),
    )
    val dismissListState = if (twoPane) bodyListState else headerListState
    DetailsCardTheme(state.manga.id) {
    CompositionLocalProvider(dev.errnolink.tsuzuki.designsystem.LocalGlassContext provides glass) {
    Box(Modifier.fillMaxSize().background(TsuzukiTheme.colors.background)) {
        Box(Modifier.fillMaxSize().heroDismissDrag { !dismissListState.canScrollBackward && selected.isEmpty() }) {
            if (twoPane) {
                DetailsTwoPane(
                    headerState = headerListState,
                    bodyState = bodyListState,
                    glass = glass,
                    contentPadding = PaddingValues(top = top, bottom = bottom),
                    header = detailsHeaderItems,
                    body = detailsBodyItems,
                )
            } else {
                LazyColumn(
                    flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                    state = headerListState,
                    modifier = Modifier.fillMaxSize().glassSource(glass),
                    contentPadding = PaddingValues(top = top, bottom = bottom),
                ) {
                    detailsHeaderItems()
                    detailsBodyItems()
                }
            }
        }
        MangaBottomActionMenu(
            visible = selected.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter),
            glass = glass,
            // SEL-02: every `all` predicate is guarded by a non-empty selection — an empty list
            // made `all { bookmark }` true, so the exiting bar re-labelled to Unbookmark alone.
            onBookmarkClicked = { onMultiBookmarkClicked(selected.map { it.chapter }, true) }.takeIf { selected.isNotEmpty() && selected.any { !it.chapter.bookmark } },
            onRemoveBookmarkClicked = { onMultiBookmarkClicked(selected.map { it.chapter }, false) }.takeIf { selected.isNotEmpty() && selected.all { it.chapter.bookmark } },
            onMarkAsReadClicked = { onMultiMarkAsReadClicked(selected.map { it.chapter }, true) }.takeIf { selected.isNotEmpty() && selected.any { !it.chapter.read } },
            onMarkAsUnreadClicked = { onMultiMarkAsReadClicked(selected.map { it.chapter }, false) }.takeIf { selected.isNotEmpty() && selected.any { it.chapter.read || it.chapter.lastPageRead > 0 } },
            onMarkPreviousAsReadClicked = { onMarkPreviousAsReadClicked(selected.single().chapter) }.takeIf { selected.size == 1 },
            onDownloadClicked = onDownloadChapter?.let { action -> { action(selected, ChapterDownloadAction.START) } }?.takeIf { selected.isNotEmpty() && selected.any { !it.isDownloaded } },
            onDeleteClicked = { onMultiDeleteClicked(selected.map { it.chapter }) }.takeIf { selected.isNotEmpty() && selected.any { it.isDownloaded } },
        )
        ShellToasts(
            toasts,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
        )
    }
    }
    // The page's own sheets ride the same cover scheme as the page beneath them (U1: one accent
    // per screen — their links, chips and checkmarks follow the cover accent, not the app's blue).
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
        title = stringResource(MR.strings.tsuzuki_clear_data_title),
        message = stringResource(MR.strings.tsuzuki_clear_data_message),
        confirmLabel = stringResource(MR.strings.tsuzuki_clear),
        onConfirm = onClearManga,
        onDismissRequest = { clearConfirmVisible = false },
    )
    val automaticCurrentLabel = stringResource(MR.strings.tsuzuki_automatic_current)
    val automaticLabel = stringResource(MR.strings.zoom_start_automatic)
    val currentSuffix = stringResource(MR.strings.tsuzuki_current_suffix)
    val groupChoices = remember(chapterListItems) { scanlatorGroups(chapterListItems) }
    ShellActionSheet(
        visible = groupSheetVisible,
        title = stringResource(MR.strings.tsuzuki_open_chapters_from),
        actions = buildList {
            add(
                ShellSheetAction(if (preferredGroup == null) automaticCurrentLabel else automaticLabel) {
                    PreferredGroups.set(state.manga.id, null)
                    preferredGroup = null
                },
            )
            groupChoices.forEach { group ->
                val current = group.name.equals(preferredGroup, ignoreCase = true)
                add(
                    ShellSheetAction(
                        group.name + " · " + pluralStringResource(MR.plurals.manga_num_chapters, group.chapters, group.chapters) +
                            if (current) currentSuffix else "",
                    ) {
                        PreferredGroups.set(state.manga.id, group.name)
                        preferredGroup = group.name
                    },
                )
            }
        },
        onDismissRequest = { groupSheetVisible = false },
    )
    }
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
    val context = LocalContext.current
    val accent = TsuzukiTheme.colors.accent
    val blocks = remember(description) { parseDescriptionBlocks(description) }
    val annotated = remember(blocks, accent) {
        descriptionAnnotated(blocks, accent) { url -> context.openInBrowser(url) }
    }
    var truncated by remember(description) { mutableStateOf(false) }
    var moreWidthPx by remember { mutableIntStateOf(0) }
    var lastLineTopPx by remember { mutableIntStateOf(-1) }
    val showMore = truncated || blocks.size > 1
    Box(modifier.fillMaxWidth().clickable(onClick = onOpenAbout)) {
        androidx.compose.foundation.text.BasicText(
            text = annotated,
            style = TsuzukiTheme.typography.callout.copy(color = TsuzukiTheme.colors.secondary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            inlineContent = remember(accent) { descriptionInlineContent(accent) },
            onTextLayout = { result ->
                truncated = result.hasVisualOverflow
                lastLineTopPx = if (result.lineCount > 0) result.getLineTop(result.lineCount - 1).toInt() else -1
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (showMore && moreWidthPx > 0) {
                        val fadeWidth = moreWidthPx + 24.dp.toPx()
                        val fadeLeft = (size.width - fadeWidth).coerceAtLeast(0f)
                        val lineTop = if (lastLineTopPx >= 0) lastLineTopPx.toFloat() else (size.height * 0.5f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black,
                                0.35f to Color.Transparent,
                                1f to Color.Transparent,
                                startX = fadeLeft,
                                endX = size.width,
                            ),
                            topLeft = Offset(fadeLeft, lineTop),
                            size = Size((size.width - fadeLeft).coerceAtLeast(0f), (size.height - lineTop).coerceAtLeast(0f)),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        )
        if (showMore) {
            TsuzukiText(
                text = stringResource(MR.strings.label_more).lowercase(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { moreWidthPx = it.width }
                    .padding(start = 24.dp),
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
    }
}

@Composable
private fun ChapterHeaderRow(
    count: Int?,
    summary: String,
    checking: Boolean,
    onRefresh: (() -> Unit)?,
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
            // DET-12/C-plural: the real count (pluralised — "1 chapter") cross-fades in a slot
            // reserved at the skeleton's size, so it neither pops nor reflows the row.
            ChapterCountSlot(known = count != null, skeletonWidth = 96.dp, skeletonHeight = 18.dp) {
                if (count != null) {
                    TsuzukiText(
                        pluralStringResource(MR.plurals.manga_num_chapters, count, count),
                        style = TsuzukiTheme.typography.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (checking) {
                Spacer(Modifier.width(8.dp))
                ProgressMark(0f, stringResource(MR.strings.tsuzuki_checking_for_new_chapters), Modifier.size(16.dp))
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
        if (onRefresh != null) {
            // Refresh moved out of the top capsule; it sits beside the download and filter
            // buttons in the chapter header row, the same size and style.
            DetailIcon(stringResource(MR.strings.tsuzuki_check_for_new_chapters), TsuzukiIcons.RefreshCw, onRefresh)
        }
        if (onDownloadActionClicked != null) {
            // The download icon IS the drop: its menu pours from the button and returns into it.
            val downloadsSource = rememberMenuMorphSource(borderless = true)
            Box(Modifier.menuMorphSource(downloadsSource)) {
                DetailIcon(stringResource(MR.strings.tsuzuki_download_chapters), TsuzukiIcons.Download, { downloadsExpanded = true })
                ChapterDownloadsMenu(
                    expanded = downloadsExpanded,
                    onDismissRequest = { downloadsExpanded = false },
                    onDownloadActionClicked = onDownloadActionClicked,
                    onDownloadAllClicked = onDownloadAllClicked,
                    onRemoveDownloadsClicked = onRemoveDownloadsClicked,
                    morphSource = downloadsSource,
                )
            }
        }
        // Same for the filter icon: the chapter settings menu pours from its own button.
        val settingsSource = rememberMenuMorphSource(borderless = true)
        Box(Modifier.menuMorphSource(settingsSource)) {
            DetailIcon(stringResource(MR.strings.tsuzuki_filter_and_sort_chapters), TsuzukiIcons.SlidersHorizontal, onFilterClick)
            CompositionLocalProvider(LocalMenuMorphSource provides settingsSource) {
                chapterSettingsMenu()
            }
        }
    }
}

@Composable
internal fun ChapterDownloadsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadActionClicked: (DownloadAction) -> Unit,
    onDownloadAllClicked: (() -> Unit)?,
    onRemoveDownloadsClicked: (() -> Unit)?,
    morphSource: MenuMorphSource? = LocalMenuMorphSource.current,
) {
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        morphSource = morphSource,
        paneTitle = stringResource(MR.strings.tsuzuki_download_chapters),
    ) {
        PullDownMenuItem(pluralStringResource(MR.plurals.download_amount, 1, 1), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.NEXT_1_CHAPTER)
        })
        PullDownMenuItem(pluralStringResource(MR.plurals.download_amount, 5, 5), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.NEXT_5_CHAPTERS)
        })
        PullDownMenuItem(pluralStringResource(MR.plurals.download_amount, 10, 10), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.NEXT_10_CHAPTERS)
        })
        PullDownMenuItem(pluralStringResource(MR.plurals.download_amount, 25, 25), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.NEXT_25_CHAPTERS)
        })
        PullDownSectionDivider()
        PullDownMenuItem(stringResource(MR.strings.download_unread), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.UNREAD_CHAPTERS)
        })
        PullDownMenuItem(stringResource(MR.strings.download_bookmarked), icon = TsuzukiIcons.Download, onClick = {
            onDismissRequest()
            onDownloadActionClicked(DownloadAction.BOOKMARKED_CHAPTERS)
        })
        if (onDownloadAllClicked != null) {
            PullDownMenuItem(stringResource(MR.strings.tsuzuki_all_chapters), icon = TsuzukiIcons.Download, onClick = {
                onDismissRequest()
                onDownloadAllClicked()
            })
        }
        if (onRemoveDownloadsClicked != null) {
            PullDownSectionDivider()
            PullDownMenuItem(stringResource(MR.strings.tsuzuki_remove_downloads), icon = TsuzukiIcons.Trash2, destructive = true, onClick = {
                onDismissRequest()
                onRemoveDownloadsClicked()
            })
        }
    }
}

/** The localized labels of the title page's ⋯ menu; resolved once so the remember-built list stays pure. */
@Immutable
private data class TitleMenuWords(
    val editCategories: String,
    val migrate: String,
    val openInBrowser: String,
    val copyLink: String,
    val relatedTitles: String,
    val recommendations: String,
    val editTitleInformation: String,
    val updateSchedule: String,
    val preferredGroup: String,
    val automatic: String,
    val openDownloadFolder: String,
    val sourcesAndMergeSettings: String,
    val mergeWithAnotherTitle: String,
    val sourceSettings: String,
    val sourceMetadata: String,
    val palette: String,
    val titleOptions: String,
    val clearTitleData: String,
    val removeFromLibrary: String,
)

@Composable
private fun titleMenuWords(): TitleMenuWords = TitleMenuWords(
    editCategories = stringResource(MR.strings.action_edit_categories),
    migrate = stringResource(MR.strings.action_migrate),
    openInBrowser = stringResource(MR.strings.action_open_in_browser),
    copyLink = stringResource(MR.strings.tsuzuki_copy_link),
    relatedTitles = stringResource(MR.strings.tsuzuki_related_titles),
    recommendations = stringResource(MR.strings.tsuzuki_recommendations),
    editTitleInformation = stringResource(MR.strings.tsuzuki_edit_title_information),
    updateSchedule = stringResource(MR.strings.tsuzuki_update_schedule),
    preferredGroup = stringResource(MR.strings.tsuzuki_preferred_group),
    automatic = stringResource(MR.strings.zoom_start_automatic),
    openDownloadFolder = stringResource(MR.strings.tsuzuki_open_download_folder),
    sourcesAndMergeSettings = stringResource(MR.strings.tsuzuki_sources_and_merge_settings),
    mergeWithAnotherTitle = stringResource(MR.strings.tsuzuki_merge_with_another_title),
    sourceSettings = stringResource(MR.strings.source_settings),
    sourceMetadata = stringResource(MR.strings.tsuzuki_source_metadata),
    palette = stringResource(MR.strings.tsuzuki_palette),
    titleOptions = stringResource(MR.strings.tsuzuki_title_options),
    clearTitleData = stringResource(MR.strings.tsuzuki_clear_title_data),
    removeFromLibrary = stringResource(MR.strings.remove_from_library),
)

/** The localized names of the active chapter filters for the empty-state summary. */
@Immutable
internal data class ActiveFilterLabels(
    val downloadedOnly: String,
    val notDownloaded: String,
    val unreadOnly: String,
    val readOnly: String,
    val bookmarkedOnly: String,
    val notBookmarked: String,
    val scanlatorFilter: String,
    val mangadexFilters: String,
)

@Composable
internal fun activeFilterLabels(): ActiveFilterLabels = ActiveFilterLabels(
    downloadedOnly = stringResource(MR.strings.tsuzuki_filter_downloaded_only),
    notDownloaded = stringResource(MR.strings.tsuzuki_filter_not_downloaded),
    unreadOnly = stringResource(MR.strings.tsuzuki_filter_unread_only),
    readOnly = stringResource(MR.strings.tsuzuki_filter_read_only),
    bookmarkedOnly = stringResource(MR.strings.tsuzuki_filter_bookmarked_only),
    notBookmarked = stringResource(MR.strings.tsuzuki_filter_not_bookmarked),
    scanlatorFilter = stringResource(MR.strings.tsuzuki_scanlator_filter),
    mangadexFilters = stringResource(MR.strings.tsuzuki_mangadex_filters),
)
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

private val ChapterRowShape = RoundedCornerShape(TsuzukiCorners.group)
private val ChapterRowTopShape = RoundedCornerShape(topStart = TsuzukiCorners.group, topEnd = TsuzukiCorners.group)
private val ChapterRowBottomShape = RoundedCornerShape(bottomStart = TsuzukiCorners.group, bottomEnd = TsuzukiCorners.group)
private val ChapterRowMiddleShape = RoundedCornerShape(0.dp)

private const val CardGlowStrength = 0.45f
