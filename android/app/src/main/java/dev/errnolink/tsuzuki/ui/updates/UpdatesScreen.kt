package dev.errnolink.tsuzuki.ui.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.ListFilter
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.SkeletonCrossfade
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.UpdateSelectionOptions
import eu.kanade.presentation.updates.UpdatesUiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.domain.library.service.LibraryPreferences.ChapterSwipeAction
import kotlin.time.Duration.Companion.seconds

enum class UpdatesContentState { Loading, Empty, Content }

fun updatesContentState(isLoading: Boolean, hasItems: Boolean, hasModels: Boolean): UpdatesContentState =
    when {
        isLoading -> UpdatesContentState.Loading
        !hasItems -> UpdatesContentState.Empty
        hasModels -> UpdatesContentState.Content
        else -> UpdatesContentState.Loading
    }

suspend fun prepareUpdatesUiModels(state: UpdatesScreenModel.State): List<UpdatesUiModel> =
    withContext(Dispatchers.Default) {
        state.getUiModel()
    }

@Composable
fun UpdateScreen(
    state: UpdatesScreenModel.State,
    snackbarHostState: SnackbarHostState,
    lastUpdated: Long,
    preserveReadingPosition: Boolean,
    onClickCover: (UpdatesItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onCalendarClicked: () -> Unit,
    onUpdateLibrary: () -> Boolean,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
    updateSwipeStartAction: ChapterSwipeAction,
    updateSwipeEndAction: ChapterSwipeAction,
    onUpdateSwipe: (UpdatesItem, ChapterSwipeAction) -> Unit,
    onUpdateSelected: (UpdatesItem, UpdateSelectionOptions) -> Unit,
    onOpenChapter: (UpdatesItem) -> Unit,
    onFilterClicked: () -> Unit,
    hasActiveFilters: Boolean,
    usePanoramaCover: Boolean,
    collapseToggle: (key: String) -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })
    val navigator = LocalNavigator.current
    val isRefreshing by remember { Injekt.get<LibraryUpdateStatus>().isRunning }.collectAsState()
    var actionsVisible by remember { mutableStateOf(false) }
    val uiModels by produceState(initialValue = emptyList<UpdatesUiModel>(), state.items) {
        value = prepareUpdatesUiModels(state)
    }
    val startRefresh: () -> Unit = { onUpdateLibrary(); Unit }
    ShellActionSheet(
        visible = actionsVisible,
        title = "Selected chapters",
        actions = listOf(
            ShellSheetAction("Mark as read") { onMultiMarkAsReadClicked(state.selected, true) },
            ShellSheetAction("Mark as unread") { onMultiMarkAsReadClicked(state.selected, false) },
            ShellSheetAction("Bookmark") { onMultiBookmarkClicked(state.selected, true) },
            ShellSheetAction("Remove bookmark") { onMultiBookmarkClicked(state.selected, false) },
            ShellSheetAction("Download") { onDownloadChapter(state.selected, ChapterDownloadAction.START) },
            ShellSheetAction("Download next") { onDownloadChapter(state.selected, ChapterDownloadAction.START_NOW) },
            ShellSheetAction("Delete downloads", destructive = true) { onMultiDeleteClicked(state.selected) },
        ),
        onDismissRequest = { actionsVisible = false },
    )
    ShellRefresh(refreshing = isRefreshing, enabled = !state.selectionMode, onRefresh = startRefresh) {
        @Composable
        fun UpdatesShellList(content: LazyListScope.() -> Unit) {
            ShellList(
                if (state.selectionMode) "${state.selected.size} selected" else "Updates",
                reselectKey = "eu.kanade.tachiyomi.ui.updates.UpdatesTab",
                snackbar = snackbarHostState,
                navigateUp = if (navigator?.canPop == true) { { navigator.pop() } } else null,
                itemSpacing = 0.dp,
                content = content,
                actions = if (state.selectionMode) {
                    listOf(
                        ShellTopAction(stringResource(MR.strings.action_select_all), TsuzukiIcons.ListChecks, { onSelectAll(true) }),
                        ShellTopAction(stringResource(MR.strings.action_select_inverse), TsuzukiIcons.ArrowUpDown, onInvertSelection),
                        ShellTopAction(stringResource(MR.strings.action_close), TsuzukiIcons.X, { onSelectAll(false) }),
                    )
                } else {
                    listOf(
                        ShellTopAction(stringResource(MR.strings.action_filter), TsuzukiIcons.ListFilter, onFilterClicked, active = hasActiveFilters),
                        ShellTopAction(stringResource(MR.strings.action_view_upcoming), TsuzukiIcons.Calendar, onCalendarClicked),
                        ShellTopAction(stringResource(MR.strings.action_update_library), TsuzukiIcons.RefreshCw, startRefresh),
                    )
                },
            )
        }
        SkeletonCrossfade(
            loading = updatesContentState(state.isLoading, state.items.isNotEmpty(), uiModels.isNotEmpty()) == UpdatesContentState.Loading,
            skeleton = { UpdatesShellList { updatesLoadingSkeleton() } },
            content = {
                UpdatesShellList {
                    if (state.selectionMode) {
                        item("updates-selection-actions") {
                            PillButton("Actions", { actionsVisible = true }, Modifier.padding(horizontal = 20.dp))
                        }
                    }
                    when (updatesContentState(state.isLoading, state.items.isNotEmpty(), uiModels.isNotEmpty())) {
                        UpdatesContentState.Loading -> updatesLoadingSkeleton()
                        UpdatesContentState.Empty -> item(key = "updates-empty") {
                            ShellEmpty(
                                "No chapter updates",
                                "New chapters from your library appear here. Pull down or tap the refresh action to check now.",
                            )
                        }
                        UpdatesContentState.Content -> {
                            updatesLastUpdatedItem(lastUpdated)
                            updatesUiItems(
                                uiModels = uiModels,
                                expandedState = state.expandedState,
                                collapseToggle = collapseToggle,
                                usePanoramaCover = usePanoramaCover,
                                selectionMode = state.selectionMode,
                                preserveReadingPosition = preserveReadingPosition,
                                onUpdateSelected = onUpdateSelected,
                                onClickCover = onClickCover,
                                onClickUpdate = onOpenChapter,
                                onDownloadChapter = onDownloadChapter,
                                updateSwipeStartAction = updateSwipeStartAction,
                                updateSwipeEndAction = updateSwipeEndAction,
                                onUpdateSwipe = onUpdateSwipe,
                            )
                        }
                    }
                }
            },
        )
    }
}
