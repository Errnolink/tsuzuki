package dev.errnolink.tsuzuki.ui.updates

import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.ui.shell.SkeletonCaption
import dev.errnolink.tsuzuki.ui.shell.UpdateRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState

class UpdatesFeedContent(
    val loading: Boolean,
    val skeleton: LazyListScope.() -> Unit,
    val content: LazyListScope.() -> Unit,
)

@Composable
fun Screen.rememberUpdatesFeedContent(
    snackbarHostState: SnackbarHostState? = null,
): UpdatesFeedContent {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val screenModel = rememberScreenModel { UpdatesScreenModel() }
    val settingsScreenModel = rememberScreenModel { UpdatesSettingsScreenModel() }
    val state by screenModel.state.collectAsState()
    val usePanoramaCover by settingsScreenModel.updatesPreferences.usePanoramaCover().collectAsState()
    val uiModels by produceState(initialValue = emptyList<UpdatesUiModel>(), state.items) {
        value = prepareUpdatesUiModels(state)
    }
    var actionsVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = state.selectionMode, onBack = { screenModel.toggleAllSelection(false) })

    ShellActionSheet(
        visible = actionsVisible,
        title = "Selected chapters",
        actions = listOf(
            ShellSheetAction("Mark as read") { screenModel.markUpdatesRead(state.selected, true) },
            ShellSheetAction("Mark as unread") { screenModel.markUpdatesRead(state.selected, false) },
            ShellSheetAction("Bookmark") { screenModel.bookmarkUpdates(state.selected, true) },
            ShellSheetAction("Remove bookmark") { screenModel.bookmarkUpdates(state.selected, false) },
            ShellSheetAction("Download") { screenModel.downloadChapters(state.selected, ChapterDownloadAction.START) },
            ShellSheetAction("Download next") { screenModel.downloadChapters(state.selected, ChapterDownloadAction.START_NOW) },
            ShellSheetAction("Delete downloads", destructive = true) { screenModel.showConfirmDeleteChapters(state.selected) },
        ),
        onDismissRequest = { actionsVisible = false },
    )

    val onDismissDialog = { screenModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is UpdatesScreenModel.Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = { screenModel.deleteChapters(dialog.toDelete) },
            )
        }
        is UpdatesScreenModel.Dialog.FilterSheet -> {
            UpdatesFilterDialog(
                onDismissRequest = onDismissDialog,
                screenModel = settingsScreenModel,
            )
        }
        null -> {}
    }

    LaunchedEffect(snackbarHostState) {
        val host = snackbarHostState ?: return@LaunchedEffect
        screenModel.events.collectLatest { event ->
            when (event) {
                UpdatesScreenModel.Event.InternalError ->
                    host.showSnackbar(context.stringResource(MR.strings.internal_error))
                is UpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                    val msg = if (event.started) MR.strings.updating_library else MR.strings.update_already_running
                    host.showSnackbar(context.stringResource(msg))
                }
            }
        }
    }
    LaunchedEffect(state.selectionMode) {
        HomeScreen.showBottomNav(!state.selectionMode)
    }
    DisposableEffect(Unit) {
        screenModel.resetNewUpdatesCount()
        onDispose {
            screenModel.resetNewUpdatesCount()
        }
    }

    val contentState = updatesContentState(state.isLoading, state.items.isNotEmpty(), uiModels.isNotEmpty())
    return UpdatesFeedContent(
        loading = contentState == UpdatesContentState.Loading,
        skeleton = { updatesLoadingSkeleton() },
        content = {
            if (state.selectionMode) {
                item(key = "feed-updates-selection") {
                    PillButton("Actions", { actionsVisible = true }, Modifier.padding(horizontal = 20.dp))
                }
            }
            when (contentState) {
                UpdatesContentState.Loading -> updatesLoadingSkeleton()
                UpdatesContentState.Empty -> item(key = "feed-updates-empty") {
                    ShellEmpty(
                        "No chapter updates",
                        "New chapters from your library appear here. Pull down or tap the refresh action to check now.",
                    )
                }
                UpdatesContentState.Content -> {
                    updatesLastUpdatedItem(screenModel.lastUpdated)
                    updatesUiItems(
                        uiModels = uiModels,
                        expandedState = state.expandedState,
                        collapseToggle = screenModel::toggleExpandedState,
                        usePanoramaCover = usePanoramaCover,
                        selectionMode = state.selectionMode,
                        preserveReadingPosition = screenModel.preserveReadingPosition,
                        onUpdateSelected = screenModel::toggleSelection,
                        onClickCover = { navigator?.push(MangaScreen(it.update.mangaId)) },
                        onClickUpdate = {
                            dev.errnolink.tsuzuki.reader.ReaderTransition.open(context, ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId))
                        },
                        onDownloadChapter = screenModel::downloadChapters,
                        updateSwipeStartAction = screenModel.chapterSwipeStartAction,
                        updateSwipeEndAction = screenModel.chapterSwipeEndAction,
                        onUpdateSwipe = screenModel::updateSwipe,
                    )
                }
            }
        },
    )
}

internal fun LazyListScope.updatesLastUpdatedItem(lastUpdated: Long) {
    if (lastUpdated <= 0L) return
    item(key = "updates-last-updated") {
        ShellCaption(
            "Updated ${eu.kanade.presentation.util.relativeTimeSpanString(lastUpdated)}",
            Modifier.padding(start = 32.dp, end = 20.dp, top = 4.dp),
        )
    }
}

internal fun LazyListScope.updatesLoadingSkeleton(rows: Int = 6) {
    item(key = "updates-skeleton-caption") { SkeletonCaption(32.dp, 0.3f) }
    items(
        count = rows,
        key = { "updates-skeleton-$it" },
        contentType = { "skeleton" },
    ) { index ->
        UpdateRowSkeleton(
            shape = RoundedCornerShape(
                topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                bottomStart = if (index == rows - 1) TsuzukiCorners.group else 0.dp,
                bottomEnd = if (index == rows - 1) TsuzukiCorners.group else 0.dp,
            ),
            divider = index < rows - 1,
            index = index,
        )
    }
}
