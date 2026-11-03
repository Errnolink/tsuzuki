package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import dev.errnolink.tsuzuki.ui.details.TrackingSheet
import dev.errnolink.tsuzuki.ui.details.MangaCoverContent
import dev.errnolink.tsuzuki.ui.details.ChapterSettingsMenu
import eu.kanade.presentation.manga.DuplicateMangaDialog
import dev.errnolink.tsuzuki.ui.details.MangaScreen
import eu.kanade.presentation.manga.components.ClearMangaDialog
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.theme.TachiyomiTheme
import tachiyomi.domain.history.interactor.GetHistory
import eu.kanade.presentation.util.AssistContentScreen
import dev.errnolink.tsuzuki.mangadex.openMangaDexCovers
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog
import dev.errnolink.tsuzuki.ui.details.MangaMergeSheet
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.RecommendsScreen
import exh.source.MERGED_SOURCE_ID
import exh.source.anyIs
import exh.source.getMainSource
import exh.ui.metadata.MetadataViewScreen
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import dev.errnolink.tsuzuki.ui.details.DetailSkeleton
import dev.errnolink.tsuzuki.ui.details.rememberPreparedDetailsState
import dev.errnolink.tsuzuki.ui.shell.SkeletonCrossfade
import dev.errnolink.tsuzuki.ui.shell.mangaHeroTarget

class MangaScreen(
    val mangaId: Long,
    /**
     * If it is opened from Source then it will auto expand the manga description.
     * - `true`: Expand description if it's not favorited
     * - `false`: Don't expand description
     */
    val fromSource: Boolean = false,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            MangaScreenModel(
                context = context,
                lifecycle = lifecycleOwner.lifecycle,
                mangaId = mangaId,
                isFromSource = fromSource,
                smartSearched = smartSearchConfig != null,
            )
        }

        val state by rememberPreparedDetailsState(screenModel.state)

        val successState = state as? MangaScreenModel.State.Success
        if (state !is MangaScreenModel.State.Loading) {
            LaunchedEffect(Unit) { dev.errnolink.tsuzuki.ui.shell.ScreenReadiness.mark(key) }
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val showingRelatedMangasScreen = rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingRelatedMangasScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.backHandler()
                showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
            }
        }

        val content: @Composable (MangaScreenModel.State.Success) -> Unit = { successState ->
            Crossfade(
                targetState = showingRelatedMangasScreen.value,
                label = "manga_related_crossfade",
            ) { showRelatedMangasScreen ->
                when (showRelatedMangasScreen) {
                    true -> RelatedMangasScreen(
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        navigateUp = { showingRelatedMangasScreen.value = false },
                        navigator = navigator,
                        scope = scope,
                    )
                    false -> MangaDetailContent(
                        context = context,
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        showRelatedMangasScreen = { showingRelatedMangasScreen.value = true },
                        navigator = navigator,
                        scope = scope,
                    )
                }
            }
        }

        TachiyomiTheme {
            SkeletonCrossfade(
                loading = state is MangaScreenModel.State.Loading,
                skeleton = { DetailSkeleton(coverModifier = Modifier.mangaHeroTarget(mangaId)) },
                content = {
                    val loadedState = successState
                    if (loadedState != null) content(loadedState)
                },
            )
        }

        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
    }

    @Composable
    fun MangaDetailContent(
        context: Context,
        screenModel: MangaScreenModel,
        successState: MangaScreenModel.State.Success,
        bulkFavoriteScreenModel: BulkFavoriteScreenModel,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        // KMK <--
        val haptic = LocalHapticFeedback.current
        val isHttpSource = remember { successState.source is HttpSource }
        val historyMangaIds = remember(successState.manga.id, successState.processedChapters) {
            successState.processedChapters.mapTo(hashSetOf(successState.manga.id)) { it.chapter.mangaId }
        }
        val historyFlow = remember(historyMangaIds) { Injekt.get<GetHistory>().subscribe(historyMangaIds) }
        val history by historyFlow.collectAsStateWithLifecycle(emptyList())
        val continueChapter = remember(successState.chapters, successState.manga, history) {
            val chaptersById = successState.processedChapters.associateBy { it.id }
            val recent = history.firstNotNullOfOrNull { chaptersById[it.chapterId]?.chapter }
            recent?.takeUnless { it.read } ?: screenModel.getNextUnreadChapter()
        }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }


        // KMK -->
        val coverRatio = remember { mutableFloatStateOf(1f) }

        val isConfigurableSource = successState.source.anyIs<ConfigurableSource>()
        // KMK <--

        var showScanlatorsDialog by remember { mutableStateOf(false) }
        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                screenModel.toggleFavorite()
            },
            // SY -->
            onWebViewClicked = {
                if (successState.mergedData == null) {
                    openMangaInWebView(
                        navigator,
                        screenModel.manga,
                        screenModel.source,
                    )
                } else {
                    mergedMangaAction(
                        context,
                        navigator,
                        successState.mergedData,
                        // KMK -->
                        action = { _, nav, manga, source -> openMangaInWebView(nav, manga, source) },
                        titleRes = MR.strings.action_open_in_web_view,
                        // KMK <--
                    )
                }
            }.takeIf { isHttpSource },
            // SY <--
            onWebViewLongClicked = {
                // KMK -->
                if (successState.mergedData == null) {
                    // KMK <--
                    copyMangaUrl(
                        context,
                        screenModel.manga,
                        screenModel.source,
                    )
                    // KMK -->
                } else {
                    mergedMangaAction(
                        context,
                        navigator,
                        successState.mergedData,
                        action = { ctx, _, manga, source -> copyMangaUrl(ctx, manga, source) },
                        titleRes = MR.strings.action_copy_link,
                    )
                    // KMK <--
                }
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            chapterSettingsMenu = {
                dev.errnolink.tsuzuki.ui.details.ChapterSettingsMenu(
                    expanded = successState.dialog == MangaScreenModel.Dialog.SettingsSheet,
                    onDismissRequest = screenModel::dismissDialog,
                    manga = successState.manga,
                    onDownloadFilterChanged = screenModel::setDownloadedFilter,
                    onUnreadFilterChanged = screenModel::setUnreadFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                    onSortModeChanged = screenModel::setSorting,
                    onSortOrderChanged = screenModel::setSortOrder,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                    onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                    onResetToDefault = screenModel::resetToDefaultSettings,
                    scanlatorFilterActive = successState.scanlatorFilterActive,
                    onScanlatorFilterClicked = { showScanlatorsDialog = true },
                )
            },
            onRefresh = screenModel::fetchAllFromSource,
            continueChapter = continueChapter,
            onContinueReading = { continueReading(context, continueChapter) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            // KMK -->
            librarySearch = { query ->
                scope.launch { performSearch(navigator, query, global = false, library = true) }
            },
            // KMK <--
            onCoverClicked = screenModel::showCoverDialog,
            onCoversClicked = { openMangaDexCovers(navigator, successState.manga.id) },
            onPaletteClicked = { navigator.push(PaletteScreen(null)) },
            onDownloadAllClicked = screenModel::downloadAllChapters.takeIf { !successState.source.isLocalOrStub() },
            onRemoveDownloadsClicked = screenModel::removeDownloads.takeIf { !successState.source.isLocalOrStub() },
            onShareClicked = {
                // KMK -->
                if (successState.mergedData == null) {
                    // KMK <--
                    shareManga(context, screenModel.manga, screenModel.source)
                    // KMK -->
                } else {
                    mergedMangaAction(
                        context,
                        navigator,
                        successState.mergedData,
                        action = { ctx, _, manga, source -> shareManga(ctx, manga, source) },
                        titleRes = MR.strings.action_share,
                    )
                    // KMK <--
                }
            }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = screenModel::showSetFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            onMigrateClicked = {
                navigator.push(MigrationConfigScreen(successState.manga.id))
            }.takeIf { successState.manga.favorite },
            // SY -->
            onMetadataViewerClicked = {
                openMetadataViewer(
                    navigator,
                    successState.manga,
                )
            },
            onEditInfoClicked = screenModel::showEditMangaInfoDialog,
            onRecommendClicked = {
                openRecommends(navigator, screenModel.source?.getMainSource(), successState.manga)
            },
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = screenModel::showMergeSheet,
            onMergeWithAnotherClicked = {
                mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge)
            },
            // SY <--
            onEditNotesClicked = { navigator.push(MangaNotesScreen(manga = successState.manga)) },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            // KMK -->
            getMangaState = { screenModel.getManga(initialManga = it) },
            onClickSourceSettingsClicked = {
                navigator.push(SourcePreferencesScreen(successState.source.id))
            }.takeIf { isConfigurableSource },
            onClearManga = { screenModel.showClearMangaDialog() },
            onOpenMangaFolder = {
                if (successState.mergedData == null) {
                    screenModel.openMangaFolder(screenModel.source, screenModel.manga)
                } else {
                    mergedMangaAction(
                        context,
                        navigator,
                        successState.mergedData,
                        action = { _, _, manga, source -> screenModel.openMangaFolder(source, manga) },
                        titleRes = KMR.strings.action_open_folder,
                    )
                }
            }
                .takeIf { successState.source !is StubSource },
            onRelatedMangasScreenClick = {
                if (successState.isRelatedMangasFetched == null) {
                    scope.launchIO { screenModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedMangaClick = { navigator.push(MangaScreen(it.id, true)) },
            onRelatedMangaLongClick = { bulkFavoriteScreenModel.addRemoveManga(it, haptic) },
            onSourceClick = {
                if (successState.source !is StubSource) {
                    // KMK -->
                    if (successState.mergedData == null) {
                        screenModel.source?.let { browseSource(navigator, it, screenModel.useNewSourceNavigation) }
                    } else {
                        mergedMangaAction(
                            context,
                            navigator,
                            successState.mergedData,
                            action = { _, nav, _, source ->
                                source?.let { browseSource(nav, it, screenModel.useNewSourceNavigation) }
                            },
                            titleRes = MR.strings.browse,
                        )
                    }
                    // KMK <--
                } else {
                    navigator.push(ExtensionsScreen(searchSource = successState.source.name))
                }
            },
            coverRatio = coverRatio,
            // KMK <--
        )

        val onDismissRequest = {
            screenModel.dismissDialog()
            // KMK -->
            if (screenModel.autoOpenTrack && screenModel.showTrackDialogAfterCategorySelection) {
                screenModel.showTrackDialogAfterCategorySelection = false
                if (successState.manga.favorite) screenModel.showTrackDialog()
            }
            // KMK <--
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(it) },
                    // KMK -->
                    targetManga = dialog.manga,
                    // KMK <--
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> Unit
            MangaScreenModel.Dialog.TrackSheet -> {
                TrackingSheet(
                    mangaId = successState.manga.id,
                    mangaTitle = successState.manga.title,
                    sourceId = successState.source.id,
                    visible = true,
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.FullCover -> MangaCoverContent(successState.manga.id, onDismissRequest)
            is MangaScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is MangaScreenModel.Dialog.EditMangaInfo -> {
                EditMangaDialog(
                    manga = dialog.manga,
                    // KMK -->
                    coverRatio = coverRatio,
                    // KMK <--
                    onDismissRequest = screenModel::dismissDialog,
                    onPositiveClick = screenModel::updateMangaInfo,
                )
            }

            is MangaScreenModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onDeleteClick = screenModel::deleteMerge,
                    onPositiveClick = screenModel::updateMergeSettings,
                    // KMK -->
                    onOpenEntryClick = { merge ->
                        merge.mangaId?.let { navigator.push(MangaScreen(it)) }
                    },
                    // KMK <--
                )
            }
            is MangaScreenModel.Dialog.MergeSheet -> {
                MangaMergeSheet(
                    visible = true,
                    manga = successState.manga,
                    mergedData = successState.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onMergeCandidate = { candidate ->
                        screenModel.mergeCandidate(candidate) { newId ->
                            screenModel.dismissDialog()
                            navigator.replace(MangaScreen(newId))
                        }
                    },
                    onDeleteMerge = screenModel::deleteMerge,
                    onReorderMerge = screenModel::updateMergeSettings,
                    onOpenAdvancedSettings = {
                        screenModel.showEditMergedSettingsDialog()
                    }.takeIf { successState.mergedData != null },
                )
            }
            // SY <--
            // KMK -->
            is MangaScreenModel.Dialog.ClearManga -> {
                ClearMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = screenModel::clearManga,
                )
            }
            // KMK <--
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, mangaId, chapter.id))
    }

    @Suppress("LocalVariableName")
    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("LocalVariableName")
    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: Source?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    @Suppress("LocalVariableName")
    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(
        navigator: Navigator,
        query: String,
        global: Boolean,
        // KMK -->
        library: Boolean = false,
        // KMK <--
    ) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        // KMK -->
        navigator.popUntil { screen ->
            screen is HomeScreen ||
                (!library && (screen is BrowseSourceScreen || screen is SourceFeedScreen))
        }
        // KMK <--

        when (val previousController = navigator.lastItem) {
            is HomeScreen -> {
                // KMK -->
                // navigator.pop()
                // KMK <--
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                // KMK -->
                // navigator.pop()
                // KMK <--
                previousController.search(query)
            }
            // SY -->
            is SourceFeedScreen -> {
                // KMK -->
                // navigator.pop()
                // navigator.replace(BrowseSourceScreen(previousController.sourceId, query))
                navigator.push(BrowseSourceScreen(previousController.sourceId, query))
                // KMK <--
            }
            // SY <--
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        var previousController: cafe.adriel.voyager.core.screen.Screen
        var idx = navigator.size - 2
        while (idx >= 0) {
            previousController = navigator.items[idx--]
            if (previousController is BrowseSourceScreen && source is HttpSource) {
                // KMK -->
                // navigator.pop()
                navigator.popUntil { navigator.size == idx + 2 }
                // KMK <--
                previousController.searchGenre(genreName)
                return
            }
            // KMK -->
            if (previousController is SourceFeedScreen && source is HttpSource) {
                navigator.popUntil { navigator.size == idx + 2 }
                navigator.push(BrowseSourceScreen(previousController.sourceId, ""))
                previousController = navigator.lastItem as BrowseSourceScreen
                previousController.searchGenre(genreName)
                return
            }
            // KMK <--
        }
        performSearch(navigator, genreName, global = false)
    }

    /**
     * Copy Manga URL to Clipboard
     */
    @Suppress("LocalVariableName")
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }

    // SY -->
    private fun openMetadataViewer(
        navigator: Navigator,
        manga: Manga,
    ) {
        navigator.push(MetadataViewScreen(manga.id, manga.source))
    }

    private fun mergedMangaAction(
        context: Context,
        navigator: Navigator,
        mergedMangaData: MergedMangaData,
        // KMK -->
        action: (Context, Navigator, Manga, HttpSource?) -> Unit,
        titleRes: StringResource,
        // KMK <--
    ) {
        val sourceManager: SourceManager = Injekt.get()
        // KMK -->
        val mergedMangaAndSources = mergedMangaData.manga.values
            .filterNot { it.source == MERGED_SOURCE_ID }
            .map { manga -> manga to sourceManager.getOrStub(manga.source) }
        // KMK <--
        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes.getString(context))
            .setSingleChoiceItems(
                Array(mergedMangaAndSources.size) { index ->
                    // KMK -->
                    mergedMangaAndSources[index].second.toString()
                    // KMK <--
                },
                -1,
            ) { dialog, index ->
                dialog.dismiss()
                // KMK -->
                val (manga, source) = mergedMangaAndSources[index]
                action(context, navigator, manga, source as? HttpSource)
                // KMK <--
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    // KMK -->
    private fun browseSource(navigator: Navigator, source: Source, useNewSourceNavigation: Boolean) {
        val screen = when {
            // Clicked on source of an entry being merged with previous entry or
            // source of an recommending entry (to search again)
            smartSearchConfig != null -> SmartSearchScreen(source.id, smartSearchConfig)
            useNewSourceNavigation -> SourceFeedScreen(source.id)
            else -> BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR)
        }
        when (screen) {
            // When doing a migrate/recommend => replace previous screen to perform search again.
            is SmartSearchScreen -> {
                navigator.popUntil { it is SmartSearchScreen }
                if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
            }

            is SourceFeedScreen -> {
                navigator.popUntil { it is SourceFeedScreen }
                if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
            }

            else -> {
                navigator.popUntil { it is BrowseSourceScreen }
                if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
            }
        }
    }
    // KMK <--


    // EXH -->
    /**
     * Called when click Merge on an entry to search for entries to merge.
     */
    private fun openSmartSearch(navigator: Navigator, manga: Manga) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)

        navigator.push(SourcesScreen(smartSearchConfig))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mergeWithAnother(
        navigator: Navigator,
        context: Context,
        manga: Manga,
        smartSearchMerge: suspend (Manga, Long) -> Manga,
    ) {
        launchUI {
            try {
                val mergedManga = withNonCancellableContext {
                    smartSearchMerge(manga, smartSearchConfig?.origMangaId!!)
                }

                navigator.popUntil { it is SourcesScreen }
                navigator.pop()
                // KMK -->
                if (navigator.lastItem !is MangaScreen) {
                    navigator push MangaScreen(mergedManga.id)
                } else {
                    // KMK <--
                    navigator replace MangaScreen(mergedManga.id)
                }
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--

    // AZ -->
    private fun openRecommends(navigator: Navigator, source: Source?, manga: Manga) {
        source ?: return
        RecommendsScreen.Args.SingleSourceManga(manga.id, source.id)
            .let(::RecommendsScreen)
            .let(navigator::push)
    }
    // AZ <--
}
