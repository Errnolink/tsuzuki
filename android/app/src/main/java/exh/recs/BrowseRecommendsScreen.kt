package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.LayoutGrid
import dev.errnolink.tsuzuki.ui.browse.BrowseSourceContent
import dev.errnolink.tsuzuki.ui.settings.ChoiceSheet
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.batch.RankedSearchResults
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen
import java.io.Serializable

class BrowseRecommendsScreen(
    private val args: Args,
    private val isExternalSource: Boolean,
) : Screen() {

    sealed interface Args : Serializable {
        data class SingleSourceManga(
            val mangaId: Long,
            val sourceId: Long,
            val recommendationSourceName: String,
        ) : Args
        data class MergedSourceMangas(val results: RankedSearchResults) : Args
    }

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { BrowseRecommendsScreenModel(args) }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (isExternalSource) {
                    true -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    false -> MangaScreen(manga.id, true)
                },
            )
        }

        // KMK -->
        val onLongClickItem = { manga: Manga ->
            when (isExternalSource) {
                true -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                false -> navigator.push(MangaScreen(manga.id, true))
            }
        }

        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
        // KMK <--

        // B34 legacy sweep: the Material Scaffold/simple toolbar pair is replaced by the Tsuzuki
        // shell; display mode moves into a top action with the shared choice sheet.
        val title = remember {
            val recSource = screenModel.recommendationPagingSource
            "${recSource.name} (${recSource.category.getString(context)})"
        }
        var showDisplayModes by remember { mutableStateOf(false) }
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
            title = title,
            navigateUp = navigator::pop,
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
                    if (!isExternalSource) {
                        add(
                            ShellTopAction(
                                "Select manga",
                                Icons.Outlined.Checklist,
                                { bulkFavoriteScreenModel.toggleSelectionMode(true) },
                            ),
                        )
                    }
                    add(ShellTopAction("Display mode", TsuzukiIcons.LayoutGrid, { showDisplayModes = true }))
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
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        onClickItem(manga)
                    }
                },
                onMangaLongClick = { manga ->
                    // KMK -->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        onLongClickItem(manga)
                    }
                    // KMK <--
                },
                // KMK -->
                selection = bulkFavoriteState.selection,
                // KMK <--
            )
        }

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
