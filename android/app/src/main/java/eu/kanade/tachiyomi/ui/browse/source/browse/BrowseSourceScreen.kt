package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.FlipToBack
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.settings.ChoiceSheet
import tachiyomi.domain.library.model.LibraryDisplayMode
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import dev.errnolink.tsuzuki.ui.browse.BrowseSourceContent
import dev.errnolink.tsuzuki.ui.browse.MissingSourceScreen
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.browse.components.SavedSearchCreateDialog
import eu.kanade.presentation.browse.components.SavedSearchDeleteDialog
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.manga.DuplicateMangaDialog

import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen

import exh.source.anyIs

import exh.source.isMdBasedSource
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource

import uy.kohesive.injekt.api.get

data class BrowseSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    // SY -->
    private val filtersJson: String? = null,
    private val savedSearch: Long? = null,
    /** being set when called from [SmartSearchScreen] or when click on a manga from this screen
     * which was previously opened from `SmartSearchScreen` */
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
    // SY <--
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        if (!ifSourcesLoaded()) {
            ShellContentScaffold(
                title = stringResource(MR.strings.browse),
                navigateUp = navigator::pop,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ProgressRing(0f, stringResource(MR.strings.loading))
                }
            }
            return
        }

        val screenModel = rememberScreenModel {
            BrowseSourceScreenModel(
                sourceId = sourceId,
                listingQuery = listingQuery,
                // SY -->
                filtersJson = filtersJson,
                savedSearch = savedSearch,
                // SY <--
            )
        }
        val state by screenModel.state.collectAsState()

        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        // SY -->
        val context = LocalContext.current
        // SY <--

        // KMK -->
        screenModel.source.let {
            // KMK <--
            if (it is StubSource) {
                MissingSourceScreen(
                    source = it,
                    navigateUp = navigateUp,
                )
                return
            }
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.getHomeUrl(),
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.getHomeUrl()
        }

        // KMK -->
        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()

        val isConfigurableSource = screenModel.source.anyIs<ConfigurableSource>()
        // KMK <--

        var showActions by remember { mutableStateOf(false) }
        var showDisplayModes by remember { mutableStateOf(false) }
        ShellActionSheet(
            visible = showActions,
            title = screenModel.source.name,
            actions = buildList {
                add(ShellSheetAction("Select manga", onClick = bulkFavoriteScreenModel::toggleSelectionMode))
                add(ShellSheetAction("Display mode") { showDisplayModes = true })
                add(ShellSheetAction("Toggle incognito", onClick = screenModel::toggleIncognitoMode))
                add(ShellSheetAction("Open website", onClick = onWebViewClick))
                if (isConfigurableSource) {
                    add(ShellSheetAction("Source settings") { navigator.push(SourcePreferencesScreen(sourceId)) })
                }
                add(ShellSheetAction("Help", onClick = onHelpClick))
            },
            onDismissRequest = { showActions = false },
        )
        ChoiceSheet(
            visible = showDisplayModes,
            title = "Display mode",
            entries = listOf(
                LibraryDisplayMode.CompactGrid to "Compact covers",
                LibraryDisplayMode.ComfortableGrid to "Masonry",
                LibraryDisplayMode.ComfortableGridPanorama to "Original cover proportions",
                LibraryDisplayMode.CoverOnlyGrid to "Covers only",
                LibraryDisplayMode.List to "List",
            ),
            selected = screenModel.displayMode,
            onDismissRequest = { showDisplayModes = false },
            onSelect = { screenModel.displayMode = it },
        )
        ShellContentScaffold(
            title = if (bulkFavoriteState.selectionMode) "${bulkFavoriteState.selection.size} selected" else screenModel.source.name,
            navigateUp = navigateUp,
            snackbar = snackbarHostState,
            actions = if (bulkFavoriteState.selectionMode) {
                listOf(
                    ShellTopAction(
                        "Select all",
                        Icons.Outlined.SelectAll,
                        { mangaList.itemSnapshotList.items.forEach { bulkFavoriteScreenModel.select(it.value.first) } },
                    ),
                    ShellTopAction(
                        "Invert selection",
                        Icons.Outlined.FlipToBack,
                        { bulkFavoriteScreenModel.reverseSelection(mangaList.itemSnapshotList.items.map { it.value.first }) },
                    ),
                    ShellTopAction("Done", Icons.Outlined.Close, bulkFavoriteScreenModel::toggleSelectionMode),
                )
            } else {
                buildList {
                    if (state.filterable) {
                        add(ShellTopAction("Filters", Icons.Outlined.FilterList, screenModel::openFilterSheet, active = state.filters.isNotEmpty()))
                    }
                    add(ShellTopAction("More", Icons.Outlined.MoreHoriz, { showActions = true }))
                }
            },
            search = if (bulkFavoriteState.selectionMode) {
                null
            } else {
                rememberTopBarSearch(
                    query = state.toolbarQuery.orEmpty(),
                    onQueryChange = screenModel::setToolbarQuery,
                    placeholder = "Search ${screenModel.source.name}",
                    onSearch = { screenModel.search() },
                    onClose = { screenModel.setToolbarQuery(null) },
                )
            },
            header = {
                if (bulkFavoriteState.selectionMode) {
                    PillButton("Add to library", bulkFavoriteScreenModel::addFavorite, Modifier.padding(horizontal = 20.dp))
                } else {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            PillButton(stringResource(MR.strings.popular), {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            }, prominent = state.listing == Listing.Popular)
                        }
                        if (screenModel.source.supportsLatest) {
                            item {
                                PillButton(stringResource(MR.strings.latest), {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                }, prominent = state.listing == Listing.Latest)
                            }
                        }
                        state.savedSearches.forEach { saved ->
                            item(key = saved.id) {
                                PillButton(saved.name, {
                                    screenModel.onSavedSearch(saved) { context.toast(it) }
                                }, prominent = (state.listing as? Listing.Search)?.savedSearchId == saved.id)
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = mangaList,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),

                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                onMangaClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        navigator.push(
                            MangaScreen(
                                mangaId = manga.id,
                                // KMK -->
                                // Finding the entry to be merged to, so we don't want to expand description
                                // so that user can see the `Merge to another` button
                                fromSource = smartSearchConfig == null,
                                // KMK <--
                                smartSearchConfig = smartSearchConfig,
                            ),
                        )
                    }
                },
                onMangaLongClick = { manga ->
                    // KMK -->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (bulkFavoriteState.selectionMode) {
                        navigator.push(MangaScreen(manga.id, true))
                    } else {
                        // KMK <--
                        scope.launchIO {
                            val duplicates = screenModel.getDuplicateLibraryManga(manga)
                            when {
                                manga.favorite -> screenModel.setDialog(BrowseSourceScreenModel.Dialog.RemoveManga(manga))
                                duplicates.isNotEmpty() -> screenModel.setDialog(
                                    BrowseSourceScreenModel.Dialog.AddDuplicateManga(manga, duplicates),
                                )
                                else -> screenModel.addFavorite(manga)
                            }
                        }
                    }
                },
                // KMK -->
                selection = bulkFavoriteState.selection,
                // KMK <--
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseSourceScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    // SY -->
                    startExpanded = screenModel.startExpanded,
                    onSave = screenModel::onSaveSearch,
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        screenModel.onSavedSearch(search) {
                            context.toast(it)
                        }
                    },
                    onSavedSearchPress = screenModel::onSavedSearchPress,
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(KMR.strings.saved_searches_delete),
                    // KMK <--
                    openMangaDexRandom = if (screenModel.source.isMdBasedSource()) {
                        {
                            screenModel.onMangaDexRandom {
                                navigator.replace(
                                    BrowseSourceScreen(
                                        sourceId,
                                        "id:$it",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    openMangaDexFollows = if (screenModel.source.isMdBasedSource()) {
                        {
                            // KMK -->
                            // navigator.replace(MangaDexFollowsScreen(sourceId))
                            navigator.push(MangaDexFollowsScreen(sourceId))
                            // KMK <--
                        }
                    } else {
                        null
                    },
                    // SY <--
                )
            }
            is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.setDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)) },
                    // KMK -->
                    targetManga = dialog.manga,
                    // KMK <--
                )
            }

            is BrowseSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            is BrowseSourceScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            is BrowseSourceScreenModel.Dialog.CreateSavedSearch -> SavedSearchCreateDialog(
                onDismissRequest = onDismissRequest,
                currentSavedSearches = dialog.currentSavedSearches,
                saveSearch = screenModel::saveSearch,
            )
            is BrowseSourceScreenModel.Dialog.DeleteSavedSearch -> SavedSearchDeleteDialog(
                onDismissRequest = onDismissRequest,
                name = dialog.name,
                deleteSavedSearch = {
                    screenModel.deleteSearch(dialog.idToDelete)
                },
            )
            else -> {}
        }

        // KMK -->
        // Bulk-favorite actions only
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
