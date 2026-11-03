package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.shellBottomPadding
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data class MigrateMangaScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateMangaScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> context.toast(MR.strings.internal_error)
                }
            }
        }

        if (state.isLoading) {
            ShellList(state.source?.name ?: stringResource(MR.strings.label_migration)) {
                item("migrate-manga-loading") { ShellLoading("Loading titles…") }
            }
            return
        }

        BackHandler(enabled = state.selectionMode) { screenModel.clearSelection() }

        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            ShellList(
                title = if (state.selectionMode) "${state.selection.size} selected" else state.source?.name.orEmpty(),
                state = listState,
                navigateUp = navigator::pop,
                itemSpacing = 0.dp,
                actions = if (state.selectionMode) {
                    listOf(
                        ShellTopAction("Select all", Icons.Outlined.SelectAll, { screenModel.toggleAllSelection(true) }),
                        ShellTopAction("Invert selection", Icons.Outlined.FlipToBack, screenModel::invertSelection),
                        ShellTopAction("Done", Icons.Outlined.Close, screenModel::clearSelection),
                    )
                } else {
                    emptyList()
                },
            ) {
                if (state.isEmpty) {
                    item("migrate-manga-empty") {
                        ShellEmpty("Nothing to migrate", stringResource(MR.strings.empty_screen))
                    }
                } else {
                    items(state.titles, key = { it.id }) { manga ->
                        val isSelected = manga.id in state.selection
                        MigrateMangaRow(
                            manga = manga,
                            isSelected = isSelected,
                            onClickItem = {
                                if (state.selectionMode) {
                                    screenModel.toggleSelection(manga, !isSelected)
                                } else {
                                    navigator.push(MigrationConfigScreen(manga.id))
                                }
                            },
                            onClickCover = { navigator.push(MangaScreen(manga.id)) },
                            onLongClick = { screenModel.toggleSelection(manga, !isSelected, fromLongPress = true) },
                        )
                    }
                }
            }
            if (state.selection.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .padding(bottom = shellBottomPadding() + 12.dp)
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton("Scroll to top", { scope.launch { listState.scrollToItem(0) } }) {
                        Icon(TsuzukiIcons.ArrowUp, null, Modifier.size(18.dp))
                    }
                    PillButton(
                        label = "Migrate ${state.selection.size}",
                        onClick = { navigator.push(MigrationConfigScreen(state.selection)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton("Scroll to bottom", { scope.launch { listState.scrollToItem(state.titles.lastIndex) } }) {
                        Icon(TsuzukiIcons.ArrowDown, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun MigrateMangaRow(
        manga: Manga,
        isSelected: Boolean,
        onClickItem: () -> Unit,
        onClickCover: () -> Unit,
        onLongClick: () -> Unit,
    ) {
        val colors = TsuzukiTheme.colors
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) colors.selectedFill else colors.grouped)
                .combinedClickable(onClick = onClickItem, onLongClick = onLongClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(48.dp).clickable(onClickLabel = manga.title, onClick = onClickCover)) {
                ShellCover(manga.asMangaCover(), manga.title, Modifier.width(48.dp), fixedRatio = 2f / 3f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TsuzukiText(manga.title, style = TsuzukiTheme.typography.callout, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (manga.favorite) {
                    TsuzukiText(
                        stringResource(MR.strings.in_library),
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.secondary,
                    )
                }
            }
            if (isSelected) {
                Icon(TsuzukiIcons.Check, "Selected", Modifier.size(18.dp), tint = colors.accent)
            }
        }
    }
}
