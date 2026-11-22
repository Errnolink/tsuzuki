package dev.errnolink.tsuzuki.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.ListFilter
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import dev.errnolink.tsuzuki.ui.motion.openTitle
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import dev.errnolink.tsuzuki.ui.shell.selectionActions
import dev.errnolink.tsuzuki.ui.states.HistoryEmptyState
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState

class HistoryFeedContent(
    val toasts: ToastHostState,
    val search: TopBarSearch,
    val actions: List<ShellTopAction>,
    val selectionMode: Boolean,
    val selectionCount: Int,
    val onExitSelection: () -> Unit,
    val content: LazyListScope.() -> Unit,
)

@Composable
fun Screen.rememberHistoryFeedContent(backHandlerEnabled: Boolean = true): HistoryFeedContent {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { HistoryScreenModel() }
    val settingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
    val state by screenModel.state.collectAsState()
    val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()
    val toasts = remember { ToastHostState() }
    val models by produceState(initialValue = emptyList<HistoryUiModel>(), state.list) {
        value = prepareHistoryUiModels(state)
    }
    val pageTotals = rememberPageTotals()
    val search = rememberTopBarSearch(
        query = state.searchQuery.orEmpty(),
        onQueryChange = screenModel::updateSearchQuery,
        placeholder = context.stringResource(MR.strings.tsuzuki_search_history),
        onClose = { screenModel.updateSearchQuery(null) },
    )

    BackHandler(enabled = backHandlerEnabled && state.selectionMode, onBack = { screenModel.toggleSelectionMode() })

    val onDismissRequest = { screenModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is HistoryScreenModel.Dialog.Delete -> {
            HistoryDeleteDialog(
                onDismissRequest = onDismissRequest,
                onDelete = { all ->
                    if (all) {
                        screenModel.removeAllFromHistory(dialog.histories)
                    } else {
                        screenModel.removeFromHistory(dialog.histories)
                    }
                },
            )
        }
        is HistoryScreenModel.Dialog.DeleteAll -> {
            HistoryDeleteAllDialog(
                onDismissRequest = onDismissRequest,
                onDelete = screenModel::removeAllHistory,
            )
        }
        is HistoryScreenModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.addFavorite(dialog.manga) },
                onOpenManga = { openTitle(navigator, it.id) },
                onMigrate = { screenModel.showMigrateDialog(dialog.manga, it) },
                targetManga = dialog.manga,
            )
        }
        is HistoryScreenModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { navigator.push(CategoryScreen()) },
                onConfirm = { include, _ ->
                    screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                },
            )
        }
        is HistoryScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = { openTitle(navigator, dialog.current.id) },
                onDismissRequest = onDismissRequest,
            )
        }
        is HistoryScreenModel.Dialog.FilterSheet -> {
            HistoryFilterDialog(
                onDismissRequest = onDismissRequest,
                screenModel = settingsScreenModel,
            )
        }
        null -> {}
    }

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { e ->
            when (e) {
                HistoryScreenModel.Event.InternalError ->
                    toasts.show(context.stringResource(MR.strings.internal_error))
                HistoryScreenModel.Event.HistoryCleared ->
                    toasts.show(context.stringResource(MR.strings.clear_history_completed))
                is HistoryScreenModel.Event.OpenChapter -> {
                    val chapter = e.chapter
                    if (chapter != null) {
                        dev.errnolink.tsuzuki.reader.ReaderTransition.open(
                            context,
                            ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                        )
                    } else {
                        toasts.show(context.stringResource(MR.strings.no_next_chapter))
                    }
                }
            }
        }
    }

    val actions = if (state.selectionMode) {
        listOf(
            ShellTopAction(context.stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, { screenModel.toggleAllSelection(true) }),
            ShellTopAction(context.stringResource(MR.strings.action_select_inverse), TsuzukiIcons.ArrowUpDown, screenModel::invertSelection),
            ShellTopAction(context.stringResource(MR.strings.action_delete), TsuzukiIcons.Trash2, { screenModel.setDialog(HistoryScreenModel.Dialog.Delete(state.selected)) }),
            ShellTopAction(context.stringResource(MR.strings.action_close), TsuzukiIcons.X, { screenModel.toggleSelectionMode() }),
        )
    } else {
        listOf(
            ShellTopAction(context.stringResource(MR.strings.action_filter), TsuzukiIcons.ListFilter, screenModel::showFilterDialog, active = state.hasActiveFilters),
            ShellTopAction(context.stringResource(MR.strings.tsuzuki_select), TsuzukiIcons.ListChecks, { screenModel.toggleSelectionMode() }),
            ShellTopAction(context.stringResource(MR.strings.pref_clear_history), TsuzukiIcons.Trash2, { screenModel.setDialog(HistoryScreenModel.Dialog.DeleteAll) }),
        )
    }

    return HistoryFeedContent(
        toasts = toasts,
        search = search,
        actions = actions,
        selectionMode = state.selectionMode,
        selectionCount = state.selection.size,
        onExitSelection = { screenModel.toggleSelectionMode(false) },
    ) {
        val selectAllActionLabel = context.stringResource(MR.strings.action_select_all)
        val invertActionLabel = context.stringResource(MR.strings.tsuzuki_invert)
        val removeActionLabel = context.stringResource(MR.strings.action_remove)
        val doneActionLabel = context.stringResource(MR.strings.tsuzuki_done)
        selectionActions(
            visible = state.selectionMode,
            animated = true,
            key = "feed-history-selection",
            actions = listOf(
                ShellSheetAction(selectAllActionLabel) { screenModel.toggleAllSelection(true) },
                ShellSheetAction(invertActionLabel) { screenModel.invertSelection() },
                ShellSheetAction(removeActionLabel, destructive = true) { screenModel.setDialog(HistoryScreenModel.Dialog.Delete(state.selected)) },
                ShellSheetAction(doneActionLabel) { screenModel.toggleSelectionMode() },
            ),
        )
        when (historyContentState(state.isLoading, state.list.isNotEmpty(), models.isNotEmpty())) {
            HistoryContentState.Loading -> historyLoadingSkeleton()
            HistoryContentState.Empty -> item(key = "feed-history-empty") {
                HistoryEmptyState(historyEmptySearching(state.searchQuery))
            }
            HistoryContentState.Content -> historyItems(
                models = models,
                selection = state.selection,
                selectionMode = state.selectionMode,
                pageTotals = pageTotals,
                usePanoramaCover = usePanoramaCover,
                keyPrefix = "feed-history",
                onClickCover = { openTitle(navigator, it) },
                onClickResume = screenModel::getNextChapterForManga,
                onDialogChange = screenModel::setDialog,
                onClickFavorite = screenModel::addFavorite,
                onHistorySelected = screenModel::toggleSelection,
            )
        }
    }
}
