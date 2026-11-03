package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import dev.errnolink.tsuzuki.ui.library.DeleteLibraryMangaDialog
import dev.errnolink.tsuzuki.ui.library.LibraryFilterSheetState
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import dev.errnolink.tsuzuki.ui.library.ShellLibraryActions
import dev.errnolink.tsuzuki.ui.library.TsuzukiLibraryScreen
import dev.errnolink.tsuzuki.ui.shell.ShellSearch
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.RecommendsScreen
import exh.recs.batch.RecommendationSearchBottomSheetDialog
import exh.recs.batch.RecommendationSearchProgressDialog
import exh.recs.batch.SearchStatus
import exh.source.MERGED_SOURCE_ID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object LibraryTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = LibraryTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        LibraryFilterSheetState.open()
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current

        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            // SY -->
            val started = LibraryUpdateJob.startNow(
                context = context,
                category = if (state.groupType == LibraryGroup.BY_DEFAULT) category else null,
                group = state.groupType,
                groupExtra = when (state.groupType) {
                    LibraryGroup.BY_DEFAULT -> null
                    LibraryGroup.BY_SOURCE, LibraryGroup.BY_TRACK_STATUS -> category?.id?.toString()
                    LibraryGroup.BY_STATUS -> category?.id?.minus(1)?.toString()
                    else -> null
                },
            )
            // SY <--
            if (!started) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.update_already_running))
                }
            }
            started
        }

        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        TsuzukiLibraryScreen(
            state = state,
            screenModel = screenModel,
            snackbar = snackbarHostState,
            onRefresh = { onClickRefresh(state.activeCategory) },
            onOpen = {
                focusManager.clearFocus()
                navigator.push(MangaScreen(it))
            },
            onResume = { mangaId, chapterId ->
                context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
            },
            onBrowse = { scope.launch { HomeScreen.openTab(HomeScreen.Tab.Browse()) } },
            onOpenDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onOpenLibraryUpdateErrors = { navigator.push(LibraryUpdateErrorScreen()) },
            onOpenHelp = { uriHandler.openUri(Constants.URL_HELP) },
            onOpenSettings = { navigator.push(SettingsScreen()) },
            onOpenStats = { navigator.push(StatsScreen()) },
            onOpenAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
            selectionActions = {
                ShellLibraryActions(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsReadClicked = { screenModel.markReadSelection(true) },
                    onMarkAsUnreadClicked = { screenModel.markReadSelection(false) },
                    onDownloadClicked = screenModel::performDownloadAction
                        .takeIf { state.selectedManga.fastAll { !it.isLocal() } },
                    onDeleteClicked = screenModel::openDeleteMangaDialog,
                    onMigrateClicked = {
                        val selection = state
                            // KMK -->
                            .selectedManga
                            .filterNot { it.source == MERGED_SOURCE_ID }
                            .map { it.id }
                        // KMK <--
                        screenModel.clearSelection()
                        // KMK -->
                        if (selection.isEmpty()) {
                            context.toast(SYMR.strings.no_valid_entry)
                        } else {
                            // KMK <--
                            navigator.push(MigrationConfigScreen(selection))
                        }
                    },
                    // KMK -->
                    onMergeClicked = {
                        if (state.selection.size == 1) {
                            val manga = state.selectedManga.first()
                            // Invoke merging for this manga
                            screenModel.clearSelection()
                            val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)
                            navigator.push(SourcesScreen(smartSearchConfig))
                        } else if (state.selection.isNotEmpty()) {
                            // Invoke multiple merge
                            val selectedManga = state.selectedManga
                            screenModel.clearSelection()
                            scope.launchIO {
                                val mergingMangas = selectedManga.filterNot { it.source == MERGED_SOURCE_ID }
                                val mergedMangaId = screenModel.smartSearchMerge(selectedManga.toPersistentList())
                                snackbarHostState.showSnackbar(context.stringResource(SYMR.strings.entry_merged))
                                if (mergedMangaId != null) {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.stringResource(KMR.strings.action_remove_merged),
                                        actionLabel = context.stringResource(MR.strings.action_remove),
                                        withDismissAction = true,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        screenModel.removeMangas(
                                            mangas = mergingMangas,
                                            deleteFromLibrary = true,
                                            deleteChapters = false,
                                        )
                                    }
                                    navigator.push(MangaScreen(mergedMangaId))
                                } else {
                                    snackbarHostState.showSnackbar(context.stringResource(SYMR.strings.merged_references_invalid))
                                }
                            }
                        } else {
                            screenModel.clearSelection()
                            context.toast(SYMR.strings.no_valid_entry)
                        }
                    },
                    onSelectionUpdateClicked = {
                        val started = screenModel.updateSelectedManga()
                        scope.launch {
                            val msgRes = if (started) {
                                KMR.strings.updating
                            } else {
                                MR.strings.update_already_running
                            }
                            if (started) {
                                screenModel.clearSelection()
                            }
                            snackbarHostState.showSnackbar(context.stringResource(msgRes))
                        }
                    },
                    // KMK <--
                    // SY -->
                    onClickCollectRecommendations = screenModel::showRecommendationSearchDialog.takeIf { state.selection.size > 1 },
                    onClickAddToMangaDex = screenModel::syncMangaToDex.takeIf { state.showAddToMangadex },
                    onClickResetInfo = screenModel::resetInfo.takeIf { state.showResetInfo },
                    // SY <--
                )
            },
        )

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is LibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        // KMK -->
                        // screenModel.clearSelection()
                        // KMK <--
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is LibraryScreenModel.Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        screenModel.clearSelection()
                    },
                )
            }
            // SY -->
            is LibraryScreenModel.Dialog.RecommendationSearchSheet -> {
                RecommendationSearchBottomSheetDialog(
                    onDismissRequest = onDismissRequest,
                    onSearchRequest = {
                        onDismissRequest()
                        screenModel.clearSelection()
                        screenModel.runRecommendationSearch(dialog.manga)
                    },
                )
            }
            // SY <--
            else -> {}
        }

        RecommendationSearchProgressDialog(
            status = screenModel.recommendationSearch.status.collectAsState().value,
            setStatusIdle = { screenModel.recommendationSearch.status.value = SearchStatus.Idle },
            setStatusCancelling = { screenModel.recommendationSearch.status.value = SearchStatus.Cancelling },
        )
        // SY <--

        // Search back-press is handled by the bottom dock (ShellNavDock), which owns the search field.
        BackHandler(enabled = state.selectionMode) { screenModel.clearSelection() }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        // SY -->
        val recSearchState by screenModel.recommendationSearch.status.collectAsState()
        LaunchedEffect(recSearchState) {
            when (val current = recSearchState) {
                is SearchStatus.Finished.WithResults -> {
                    RecommendsScreen.Args.MergedSourceMangas(current.results)
                        .let(::RecommendsScreen)
                        .let(navigator::push)

                    screenModel.recommendationSearch.status.value = SearchStatus.Idle
                }
                is SearchStatus.Finished.WithoutResults -> {
                    context.toast(SYMR.strings.rec_no_results)
                    screenModel.recommendationSearch.status.value = SearchStatus.Idle
                }
                is SearchStatus.Cancelling -> {
                    screenModel.cancelRecommendationSearch()
                    screenModel.recommendationSearch.status.value = SearchStatus.Idle
                }
                else -> {}
            }
        }
        // SY <--

        LaunchedEffect(Unit) {
            // Searches from other screens open the search field pre-filled…
            launch { queryEvent.receiveAsFlow().collect { ShellSearch.open(it) } }
            // …and the query drives the library filter (null = closed, "" = open but empty → show everything).
            launch { ShellSearch.query.collect { screenModel.search(it?.takeIf(String::isNotEmpty)) } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)
}
