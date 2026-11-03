package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrateSourceScreenModel
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading

@Composable
fun MigrateSourceScreen(
    state: MigrateSourceScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source) -> Unit,
    onToggleSortingDirection: () -> Unit,
    onToggleSortingMode: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
) {
    val context = LocalContext.current
    var obsoleteOnly by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) { onChangeSearchQuery("") }
    Column(
        Modifier.fillMaxSize().padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        SearchField(
            state.searchQuery.orEmpty(),
            onChangeSearchQuery,
            {},
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = stringResource(KMR.strings.action_search_for_source),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TsuzukiText(
                stringResource(MR.strings.migration_selection_prompt),
                Modifier.weight(1f),
                TsuzukiTheme.typography.footnote,
                TsuzukiTheme.colors.secondary,
            )
            IconButton(
                stringResource(MR.strings.ext_obsolete),
                onClick = { obsoleteOnly = !obsoleteOnly },
            ) {
                Icon(
                    Icons.Outlined.NewReleases,
                    null,
                    Modifier.size(18.dp),
                    tint = if (obsoleteOnly) TsuzukiTheme.colors.destructive else TsuzukiTheme.colors.text,
                )
            }
            IconButton(
                stringResource(
                    if (state.sortingMode == SetMigrateSorting.Mode.ALPHABETICAL) MR.strings.action_sort_alpha else MR.strings.action_sort_count,
                ),
                onClick = onToggleSortingMode,
            ) {
                Icon(
                    if (state.sortingMode == SetMigrateSorting.Mode.ALPHABETICAL) Icons.Outlined.SortByAlpha else Icons.Outlined.Numbers,
                    null,
                    Modifier.size(18.dp),
                )
            }
            IconButton(
                stringResource(
                    if (state.sortingDirection == SetMigrateSorting.Direction.ASCENDING) MR.strings.action_asc else MR.strings.action_desc,
                ),
                onClick = onToggleSortingDirection,
            ) {
                Icon(
                    if (state.sortingDirection == SetMigrateSorting.Direction.ASCENDING) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    null,
                    Modifier.size(18.dp),
                )
            }
        }
        when {
            state.isLoading -> ShellLoading("Loading sources…")
            state.items.isEmpty() -> ShellEmpty(
                "No sources to migrate",
                "Sources with library entries appear here. Install an extension or change your search.",
            )
            else -> {
                val visible = remember(state.items, obsoleteOnly, state.searchQuery) {
                    val query = state.searchQuery
                    state.items.filter { (source, _) ->
                        (!obsoleteOnly || source.installedExtension?.isObsolete == true) &&
                            (query.isNullOrBlank() || source.name.contains(query, ignoreCase = true))
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (visible.isEmpty()) {
                        item("migrate-none") {
                            ShellEmpty("No matching sources", "Try a different search.")
                        }
                    }
                    itemsIndexed(
                        visible,
                        key = { _, (source, _) -> "migrate-${source.id}" },
                        contentType = { _, _ -> "source" },
                    ) { index, (source, count) ->
                        MigrateSourceRow(
                            source = source,
                            count = count,
                            shape = RoundedCornerShape(
                                topStart = if (index == 0) 14.dp else 0.dp,
                                topEnd = if (index == 0) 14.dp else 0.dp,
                                bottomStart = if (index == visible.lastIndex) 14.dp else 0.dp,
                                bottomEnd = if (index == visible.lastIndex) 14.dp else 0.dp,
                            ),
                            divider = index < visible.lastIndex,
                            onClickItem = { onClickItem(source) },
                            onLongClickItem = {
                                val sourceId = source.id.toString()
                                context.copyToClipboard(sourceId, sourceId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrateSourceRow(
    source: Source,
    count: Long,
    shape: CornerBasedShape,
    divider: Boolean,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(shape).background(colors.grouped)
            .combinedClickable(onClick = onClickItem, onLongClick = onLongClickItem)
            .drawBehind {
                if (divider) drawLine(colors.separator, Offset(56.dp.toPx(), size.height), Offset(size.width, size.height), 0.5.dp.toPx())
            }
            .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SourceIcon(source = source, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TsuzukiText(source.name.ifBlank { source.id.toString() }, style = TsuzukiTheme.typography.callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TsuzukiText(
                when {
                    source.isStub -> stringResource(MR.strings.not_installed)
                    source.installedExtension?.isObsolete == true -> stringResource(MR.strings.ext_obsolete)
                    else -> source.lang
                },
                style = TsuzukiTheme.typography.footnote,
                color = if (source.isStub || source.installedExtension?.isObsolete == true) colors.destructive else colors.secondary,
                maxLines = 1,
            )
        }
        ShellBadge(count.toString())
    }
}
