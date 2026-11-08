package dev.errnolink.tsuzuki.ui.history

import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import kotlinx.coroutines.flow.collectLatest
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.selectionSwapTransform
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel.HistorySelectionOptions
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import java.time.LocalDate

@Composable
fun Screen.rememberHistoryFeedContent(): LazyListScope.() -> Unit {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { HistoryScreenModel() }
    val settingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
    val state by screenModel.state.collectAsState()
    val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val models by produceState(initialValue = emptyList<HistoryUiModel>(), state.list) {
        value = prepareHistoryUiModels(state)
    }
    var searching by remember { mutableStateOf(false) }

    BackHandler(enabled = state.selectionMode, onBack = { screenModel.toggleSelectionMode() })

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
                onOpenManga = { navigator.push(MangaScreen(it.id)) },
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
                onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
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
                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                HistoryScreenModel.Event.HistoryCleared ->
                    snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                is HistoryScreenModel.Event.OpenChapter -> {
                    val chapter = e.chapter
                    if (chapter != null) {
                        dev.errnolink.tsuzuki.reader.ReaderTransition.open(context, ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
                    } else {
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                    }
                }
            }
        }
    }

    return {
        item(key = "feed-history-selection") {
            val motion = TsuzukiTheme.motion
            AnimatedContent(
                targetState = state.selectionMode,
                transitionSpec = { selectionSwapTransform(motion.reduced) },
                label = "HistorySelectionHeaderSwap",
            ) { selectionMode ->
                if (selectionMode) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = TsuzukiTheme.spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
                    ) {
                        PillButton("Select all", { screenModel.toggleAllSelection(true) }, prominent = false)
                        PillButton("Invert", { screenModel.invertSelection() }, prominent = false)
                        PillButton(
                            "Remove",
                            { screenModel.setDialog(HistoryScreenModel.Dialog.Delete(state.selected)) },
                            destructive = true,
                            prominent = false,
                        )
                        PillButton("Done", { screenModel.toggleSelectionMode() }, prominent = false)
                    }
                }
            }
        }
        if (state.searchQuery != null) {
            item(key = "feed-history-search") {
                SearchField(
                    state.searchQuery.orEmpty(),
                    screenModel::updateSearchQuery,
                    {},
                    Modifier.padding(horizontal = TsuzukiTheme.spacing.gutter),
                    placeholder = "Search history",
                )
            }
        }
        when (historyContentState(state.isLoading, state.list.isNotEmpty(), models.isNotEmpty())) {
            HistoryContentState.Loading -> historyLoadingSkeleton()
            HistoryContentState.Empty -> item(key = "feed-history-empty") {
                ShellEmpty(
                    "Nothing here yet",
                    if (state.searchQuery.isNullOrEmpty()) {
                        "The stories you read will appear here."
                    } else {
                        "No history matches your search."
                    },
                )
            }
            HistoryContentState.Content -> itemsIndexed(
                models,
                key = { _, it ->
                    when (it) {
                        is HistoryUiModel.Header -> "feed-history-date-${it.date}"
                        is HistoryUiModel.Item -> "feed-history-chapter-${it.item.chapterId}"
                    }
                },
                contentType = { _, it -> if (it is HistoryUiModel.Header) "header" else "entry" },
            ) { index, model ->
                val motion = TsuzukiTheme.motion
                val place: FiniteAnimationSpec<IntOffset>? = if (motion.reduced) null else motion.tabSpring()
                val fade: FiniteAnimationSpec<Float>? = if (motion.reduced) null else motion.tabSpring()
                FeedHistoryRow(
                    model = model,
                    models = models,
                    state = state,
                    usePanoramaCover = usePanoramaCover,
                    onClickCover = { navigator.push(MangaScreen(it)) },
                    onClickResume = screenModel::getNextChapterForManga,
                    onDialogChange = screenModel::setDialog,
                    onClickFavorite = screenModel::addFavorite,
                    onHistorySelected = screenModel::toggleSelection,
                    index = index,
                    modifier = Modifier.animateItem(fade, place, fade),
                )
            }
        }
    }
}

@Composable
private fun FeedHistoryRow(
    model: HistoryUiModel,
    models: List<HistoryUiModel>,
    state: HistoryScreenModel.State,
    usePanoramaCover: Boolean,
    onClickCover: (Long) -> Unit,
    onClickResume: (Long, Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
    onClickFavorite: (Long) -> Unit,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
) {
    when (model) {
        is HistoryUiModel.Header -> TsuzukiText(
            relativeDateText(model.date),
            modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
            TsuzukiTheme.typography.footnote,
            TsuzukiTheme.colors.secondary,
        )
        is HistoryUiModel.Item -> {
            val entry = model.item
            val selected = entry.chapterId in state.selection
            HistoryItem(
                history = entry,
                modifier = modifier.padding(horizontal = 20.dp),
                shape = RoundedCornerShape(
                    topStart = if (models.getOrNull(index - 1) !is HistoryUiModel.Item) TsuzukiCorners.group else 0.dp,
                    topEnd = if (models.getOrNull(index - 1) !is HistoryUiModel.Item) TsuzukiCorners.group else 0.dp,
                    bottomStart = if (models.getOrNull(index + 1) !is HistoryUiModel.Item) TsuzukiCorners.group else 0.dp,
                    bottomEnd = if (models.getOrNull(index + 1) !is HistoryUiModel.Item) TsuzukiCorners.group else 0.dp,
                ),
                divider = models.getOrNull(index + 1) is HistoryUiModel.Item,
                onClickCover = { onClickCover(entry.mangaId) },
                onClick = {
                    if (state.selectionMode) {
                        onHistorySelected(entry, HistorySelectionOptions(!selected, false))
                    } else {
                        onClickResume(entry.mangaId, entry.chapterId)
                    }
                },
                onLongClick = { onHistorySelected(entry, HistorySelectionOptions(!selected, true)) },
                onClickDelete = { onDialogChange(HistoryScreenModel.Dialog.Delete(entry)) },
                onClickFavorite = { onClickFavorite(entry.mangaId) },
                selected = selected,
                readProgress = if (!entry.read) "Page ${entry.lastPageRead + 1}" else null,
                usePanoramaCover = usePanoramaCover,
            )
        }
    }
}
