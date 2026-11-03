package eu.kanade.presentation.libraryUpdateError

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.libraryUpdateError.components.LibraryUpdateErrorUiModel
import eu.kanade.presentation.libraryUpdateError.components.libraryUpdateErrorUiItems
import eu.kanade.presentation.manga.components.Button
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorItem
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryUpdateErrorScreen(
    state: LibraryUpdateErrorScreenState,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onClickCover: (LibraryUpdateErrorItem) -> Unit,
    onMultiMigrateClicked: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onErrorsDelete: () -> Unit,
    onErrorDelete: (Long) -> Unit,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean) -> Unit,
    navigateUp: () -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val uiModels = remember(state) { state.getUiModel() }
    val headerIndexes = remember(state) {
        uiModels.withIndex()
            .filter { it.value is LibraryUpdateErrorUiModel.Header }
            .map { it.index }
    }

    val enableScrollToTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val enableScrollToBottom by remember(listState) {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    val enableScrollToPrevious by remember(headerIndexes, listState) {
        derivedStateOf {
            headerIndexes.any { it < listState.firstVisibleItemIndex }
        }
    }
    val enableScrollToNext by remember(headerIndexes, listState) {
        derivedStateOf {
            headerIndexes.any { it > listState.firstVisibleItemIndex }
        }
    }

    SettingsScaffold(
        title = if (state.selectionMode) {
            "${state.selected.size} selected"
        } else {
            stringResource(KMR.strings.label_library_update_errors, state.items.size)
        },
        navigateUp = navigateUp,
        state = listState,
        actions = if (state.selectionMode) {
            listOf(
                ShellTopAction(
                    stringResource(MR.strings.action_delete),
                    TsuzukiIcons.Trash2,
                    onErrorsDelete,
                ),
                ShellTopAction(
                    stringResource(MR.strings.action_select_all),
                    Icons.Outlined.SelectAll,
                    { onSelectAll(true) },
                ),
                ShellTopAction(
                    stringResource(MR.strings.action_select_inverse),
                    Icons.Outlined.FlipToBack,
                    onInvertSelection,
                ),
                ShellTopAction(
                    "Done",
                    Icons.Outlined.Close,
                    { onSelectAll(false) },
                ),
            )
        } else {
            emptyList()
        },
        bottomBar = if (state.items.isEmpty()) {
            null
        } else {
            {
                LibraryUpdateErrorBottomBar(
                selected = state.selected,
                onMultiMigrateClicked = onMultiMigrateClicked,
                enableScrollToTop = enableScrollToTop,
                enableScrollToBottom = enableScrollToBottom,
                scrollToTop = {
                    scope.launch {
                        listState.scrollToItem(0)
                    }
                },
                scrollToBottom = {
                    scope.launch {
                        listState.scrollToItem(state.items.size - 1)
                    }
                },
                enableScrollToPrevious = enableScrollToTop && enableScrollToPrevious,
                enableScrollToNext = enableScrollToBottom && enableScrollToNext,
                scrollToPrevious = {
                    scope.launch {
                        listState.scrollToItem(
                            headerIndexes
                                .filter { it < listState.firstVisibleItemIndex }
                                .maxOrNull() ?: 0,
                        )
                    }
                },
                scrollToNext = {
                    scope.launch {
                        listState.scrollToItem(
                            headerIndexes
                                .filter { it > listState.firstVisibleItemIndex }
                                .minOrNull() ?: 0,
                        )
                    }
                },
                )
            }
        },
    ) {
        when {
            state.isLoading -> item(key = "loading") {
                LoadingScreen(Modifier.fillParentMaxSize())
            }
            state.items.isEmpty() -> item(key = "empty") {
                EmptyScreen(
                    message = stringResource(KMR.strings.info_empty_library_update_errors),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }

            else -> libraryUpdateErrorUiItems(
                uiModels = uiModels,
                selectionMode = state.selectionMode,
                onErrorSelected = onErrorSelected,
                onClick = onClick,
                onClickCover = onClickCover,
                onDelete = onErrorDelete,
            )
        }
    }
}

@Composable
private fun LibraryUpdateErrorBottomBar(
    modifier: Modifier = Modifier,
    selected: List<LibraryUpdateErrorItem>,
    onMultiMigrateClicked: () -> Unit,
    enableScrollToTop: Boolean,
    enableScrollToBottom: Boolean,
    scrollToTop: () -> Unit,
    scrollToBottom: () -> Unit,
    enableScrollToPrevious: Boolean,
    enableScrollToNext: Boolean,
    scrollToPrevious: () -> Unit,
    scrollToNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(TsuzukiTheme.colors.grouped),
    ) {
        val haptic = LocalHapticFeedback.current
        val confirm = remember { mutableStateListOf(false, false, false, false, false) }
        var resetJob by remember { mutableStateOf<Job?>(null) }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            confirm.indices.forEach { i -> confirm[i] = i == toConfirmIndex }
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(1.seconds)
                if (isActive) confirm[toConfirmIndex] = false
            }
        }
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp),
        ) {
            Button(
                title = stringResource(KMR.strings.action_scroll_to_top),
                icon = Icons.Outlined.VerticalAlignTop,
                toConfirm = confirm[0],
                onLongClick = { onLongClickItem(0) },
                onClick = if (enableScrollToTop) {
                    scrollToTop
                } else {
                    {}
                },
                enabled = enableScrollToTop,
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_previous),
                icon = Icons.Outlined.ArrowUpward,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = if (enableScrollToPrevious) {
                    scrollToPrevious
                } else {
                    {}
                },
                enabled = enableScrollToPrevious,
            )
            Button(
                title = stringResource(MR.strings.migrate),
                icon = Icons.Outlined.FindReplace,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
                onClick = if (selected.isNotEmpty()) {
                    onMultiMigrateClicked
                } else {
                    {}
                },
                enabled = selected.isNotEmpty(),
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_next),
                icon = Icons.Outlined.ArrowDownward,
                toConfirm = confirm[3],
                onLongClick = { onLongClickItem(3) },
                onClick = if (enableScrollToNext) {
                    scrollToNext
                } else {
                    {}
                },
                enabled = enableScrollToNext,
            )
            Button(
                title = stringResource(KMR.strings.action_scroll_to_bottom),
                icon = Icons.Outlined.VerticalAlignBottom,
                toConfirm = confirm[4],
                onLongClick = { onLongClickItem(4) },
                onClick = if (enableScrollToBottom) {
                    scrollToBottom
                } else {
                    {}
                },
                enabled = enableScrollToBottom,
            )
        }
    }
}
