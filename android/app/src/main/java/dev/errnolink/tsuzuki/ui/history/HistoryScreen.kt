package dev.errnolink.tsuzuki.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.ListFilter
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import dev.errnolink.tsuzuki.ui.shell.HistoryRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.SkeletonCaption
import dev.errnolink.tsuzuki.ui.shell.SkeletonCrossfade
import dev.errnolink.tsuzuki.ui.category.ChangeCategoryDialog
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import dev.errnolink.tsuzuki.ui.history.HistoryItem
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel.HistorySelectionOptions
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import java.time.LocalDate

enum class HistoryContentState { Loading, Empty, Content }

fun historyContentState(isLoading: Boolean, hasEntries: Boolean, hasModels: Boolean): HistoryContentState =
    when {
        isLoading -> HistoryContentState.Loading
        !hasEntries -> HistoryContentState.Empty
        hasModels -> HistoryContentState.Content
        else -> HistoryContentState.Loading
    }

suspend fun prepareHistoryUiModels(state: HistoryScreenModel.State): List<HistoryUiModel> =
    withContext(Dispatchers.Default) {
        state.getUiModel()
    }

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (Long) -> Unit,
    onClickResume: (Long, Long) -> Unit,
    onClickFavorite: (Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
    toggleSelectionMode: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    onFilterClicked: () -> Unit,
    hasActiveFilters: Boolean,
    usePanoramaCover: Boolean,
) {
    BackHandler(state.selectionMode, toggleSelectionMode)
    val navigator = LocalNavigator.current
    val search = rememberTopBarSearch(
        query = state.searchQuery.orEmpty(),
        onQueryChange = onSearchQueryChange,
        placeholder = "Search history",
        onClose = { onSearchQueryChange(null) },
    )
    val models by produceState(initialValue = emptyList<HistoryUiModel>(), state.list) {
        value = prepareHistoryUiModels(state)
    }
    val pageTotals = rememberPageTotals()
    @Composable
    fun HistoryShellList(content: LazyListScope.() -> Unit) {
        ShellList(
            if (state.selectionMode) "${state.selection.size} selected" else "History",
            reselectKey = "eu.kanade.tachiyomi.ui.history.HistoryTab",
            snackbar = snackbarHostState,
            navigateUp = if (navigator?.canPop == true) { { navigator.pop() } } else null,
            itemSpacing = 0.dp,
            search = if (state.selectionMode) null else search,
            content = content,
            actions = if (state.selectionMode) {
                listOf(
                    ShellTopAction(stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, { onSelectAll(true) }),
                    ShellTopAction(stringResource(MR.strings.action_select_inverse), TsuzukiIcons.ArrowUpDown, onInvertSelection),
                    ShellTopAction(
                        stringResource(MR.strings.action_delete),
                        TsuzukiIcons.Trash2,
                        { onDialogChange(HistoryScreenModel.Dialog.Delete(state.selected)) },
                    ),
                    ShellTopAction(stringResource(MR.strings.action_close), TsuzukiIcons.X, toggleSelectionMode),
                )
            } else {
                listOf(
                    ShellTopAction(stringResource(MR.strings.action_search), TsuzukiIcons.Search, search.state::expand),
                    ShellTopAction(stringResource(MR.strings.action_filter), TsuzukiIcons.ListFilter, onFilterClicked, active = hasActiveFilters),
                    ShellTopAction(stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, toggleSelectionMode),
                    ShellTopAction(stringResource(MR.strings.pref_clear_history), TsuzukiIcons.Trash2, { onDialogChange(HistoryScreenModel.Dialog.DeleteAll) }),
                )
            },
    )
    }
    SkeletonCrossfade(
        loading = historyContentState(state.isLoading, state.list.isNotEmpty(), models.isNotEmpty()) == HistoryContentState.Loading,
        skeleton = { HistoryShellList { historyLoadingSkeleton() } },
        content = {
            HistoryShellList {
                when (historyContentState(state.isLoading, state.list.isNotEmpty(), models.isNotEmpty())) {
                    HistoryContentState.Loading -> historyLoadingSkeleton()
            HistoryContentState.Empty -> item(key = "history-empty") {
                ShellEmpty(
                    "Nothing here yet",
                    if (state.searchQuery.isNullOrEmpty()) {
                        "The stories you read will appear here."
                    } else {
                        "No history matches your search."
                    },
                )
            }
            else -> itemsIndexed(
                models,
                key = { _, it ->
                    when (it) {
                        is HistoryUiModel.Header -> "history-date-${it.date}"
                        is HistoryUiModel.Item -> "history-chapter-${it.item.chapterId}"
                    }
                },
                contentType = { _, it -> if (it is HistoryUiModel.Header) "header" else "entry" },
            ) { index, model ->
                val motion = TsuzukiTheme.motion
                val place: FiniteAnimationSpec<IntOffset>? = if (motion.reduced) null else motion.tabSpring()
                val fade: FiniteAnimationSpec<Float>? = if (motion.reduced) null else motion.tabSpring()
                val animated = Modifier.animateItem(fade, place, fade)
                when (model) {
                    is HistoryUiModel.Header -> TsuzukiText(
                        relativeDateText(model.date),
                        animated.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
                        TsuzukiTheme.typography.footnote,
                        TsuzukiTheme.colors.secondary,
                    )
                    is HistoryUiModel.Item -> {
                        val entry = model.item
                        val selected = entry.chapterId in state.selection
                            HistoryItem(
                                history = entry,
                                modifier = animated.padding(horizontal = 20.dp),
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
                                pageTotal = pageTotals[entry.chapterId],
                            )
                    }
                }
            }
            }
            }
        },
    )
}

internal fun LazyListScope.historyLoadingSkeleton(rows: Int = 6) {
    item(key = "history-skeleton-caption") { SkeletonCaption(32.dp, 0.3f) }
    items(
        count = rows,
        key = { "history-skeleton-$it" },
        contentType = { "skeleton" },
    ) { index ->
        HistoryRowSkeleton(
            shape = RoundedCornerShape(
                topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                bottomStart = if (index == rows - 1) TsuzukiCorners.group else 0.dp,
                bottomEnd = if (index == rows - 1) TsuzukiCorners.group else 0.dp,
            ),
            divider = index < rows - 1,
        )
    }
}