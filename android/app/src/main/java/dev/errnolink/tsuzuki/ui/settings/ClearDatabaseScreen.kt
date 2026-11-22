package dev.errnolink.tsuzuki.ui.settings


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.data.Database
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryManga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TsuzukiClearDatabaseScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ClearDatabaseScreenModel() }
        val state by model.state.collectAsState()
        val scope = rememberCoroutineScope()

        when (val s = state) {
            is ClearDatabaseScreenModel.State.Loading -> {
                SettingsScaffold(
                    title = stringResource(MR.strings.pref_clear_database),
                    navigateUp = navigator::pop,
                ) {
                    item(key = "clear-db-loading") {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            dev.errnolink.tsuzuki.designsystem.ProgressMark(progress = 0f, label = stringResource(MR.strings.loading))
                        }
                    }
                }
            }
            is ClearDatabaseScreenModel.State.Ready -> {
                if (s.showConfirmation) {
                    var keepReadManga by remember { mutableStateOf(true) }
                    PullDownConfirmSheet(
                        visible = true,
                        title = stringResource(MR.strings.are_you_sure),
                        message = stringResource(MR.strings.clear_database_text),
                        confirmLabel = stringResource(MR.strings.action_delete),
                        destructive = true,
                        onConfirm = {
                            scope.launchUI {
                                model.removeMangaBySourceId(keepReadManga)
                                model.clearSelection()
                                model.hideConfirmation()
                                context.toast(MR.strings.clear_database_completed)
                            }
                        },
                        onDismissRequest = model::hideConfirmation,
                        extraContent = {
                            InsetGroupedList {
                                GroupedRow(
                                    title = stringResource(MR.strings.clear_db_exclude_read),
                                    checked = keepReadManga,
                                    onCheckedChange = { keepReadManga = it },
                                )
                            }
                            if (!keepReadManga) {
                                TsuzukiText(
                                    text = stringResource(MR.strings.clear_database_history_warning),
                                    style = TsuzukiTheme.typography.footnote,
                                    color = TsuzukiTheme.colors.destructive,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        },
                    )
                }

                SettingsScaffold(
                    title = stringResource(MR.strings.pref_clear_database),
                    navigateUp = navigator::pop,
                    actions = if (s.items.isNotEmpty()) {
                        listOf(
                            ShellTopAction("Select all", TsuzukiIcons.ListChecks, model::selectAll),
                            ShellTopAction("Invert selection", TsuzukiIcons.ArrowLeftRight, model::invertSelection),
                        )
                    } else {
                        emptyList()
                    },
                    bottomBar = if (s.items.isEmpty()) {
                        null
                    } else {
                        {
                            dev.errnolink.tsuzuki.designsystem.PillButton(
                                stringResource(MR.strings.action_delete),
                                model::showConfirmation,
                                Modifier.fillMaxWidth(),
                                enabled = s.selection.isNotEmpty(),
                                destructive = true,
                                large = true,
                            )
                        }
                    },
                ) {
                    if (s.items.isEmpty()) {
                        item(key = "clear-db-empty") {
                            ShellEmpty(
                                stringResource(MR.strings.database_clean),
                                stringResource(MR.strings.pref_clear_database_summary),
                            )
                        }
                    } else {
                        item(key = "clear-db-list") {
                            InsetGroupedList {
                                s.items.forEachIndexed { index, sourceWithCount ->
                                    ClearDatabaseItem(
                                        source = sourceWithCount.source,
                                        count = sourceWithCount.count,
                                        isSelected = s.selection.contains(sourceWithCount.id),
                                        onClickSelect = { model.toggleSelection(sourceWithCount.source) },
                                        divider = index < s.items.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ClearDatabaseItem(
        source: Source,
        count: Long,
        isSelected: Boolean,
        onClickSelect: () -> Unit,
        divider: Boolean,
    ) {
        GroupedRow(
            title = source.visualName,
            subtitle = stringResource(MR.strings.clear_database_source_item_count, count),
            divider = divider,
            leading = { SourceIcon(source = source) },
            trailing = {
                SelectionMark(selected = isSelected)
            },
            onClick = onClickSelect,
        )
    }
}

private class ClearDatabaseScreenModel : StateScreenModel<ClearDatabaseScreenModel.State>(State.Loading) {
    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga = Injekt.get()
    private val database: Database = Injekt.get()

    init {
        screenModelScope.launchIO {
            getSourcesWithNonLibraryManga.subscribe()
                .collectLatest { list ->
                    mutableState.update { old ->
                        val items = list.sortedBy { it.name }
                        when (old) {
                            State.Loading -> State.Ready(items)
                            is State.Ready -> old.copy(items = items)
                        }
                    }
                }
        }
    }

    suspend fun removeMangaBySourceId(keepReadManga: Boolean) = withNonCancellableContext {
        val state = state.value as? State.Ready ?: return@withNonCancellableContext
        database.mangasQueries.deleteNonLibraryManga(state.selection, keepReadManga.toLong())
        database.historyQueries.removeResettedHistory()
    }

    fun toggleSelection(source: Source) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        val mutableList = state.selection.toMutableList()
        if (mutableList.contains(source.id)) {
            mutableList.remove(source.id)
        } else {
            mutableList.add(source.id)
        }
        state.copy(selection = mutableList)
    }

    fun clearSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = emptyList())
    }

    fun selectAll() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = state.items.fastMap { it.id })
    }

    fun invertSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(
            selection = state.items
                .fastMap { it.id }
                .filterNot { it in state.selection },
        )
    }

    fun showConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = true)
    }

    fun hideConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = false)
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val items: List<SourceWithCount>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
        ) : State
    }
}
