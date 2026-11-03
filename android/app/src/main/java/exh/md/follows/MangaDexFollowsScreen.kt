package exh.md.follows

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
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
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class MangaDexFollowsScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val screenModel = rememberScreenModel { MangaDexFollowsScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val snackbarHostState = remember { SnackbarHostState() }

        // KMK -->
        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
        // KMK <--
        // B34 legacy sweep: the Material Scaffold/simple toolbar pair is replaced by the Tsuzuki
        // shell; display mode moves into a top action with the shared choice sheet.
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
            title = stringResource(SYMR.strings.mangadex_follows),
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
                listOf(
                    ShellTopAction("Select manga", Icons.Outlined.Checklist, bulkFavoriteScreenModel::toggleSelectionMode),
                    ShellTopAction("Display mode", TsuzukiIcons.LayoutGrid, { showDisplayModes = true }),
                )
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
                        navigator.push(MangaScreen(manga.id, true))
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
            is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    // KMK -->
                    targetManga = dialog.manga,
                    onMigrate = { screenModel.setDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)) },
                    // KMK <--
                )
            }
            // KMK -->
            is BrowseSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            // KMK <--
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
                    onEditCategories = {
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            else -> {}
        }

        // KMK -->
        // Bulk-favorite actions only
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
