package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.Bell
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.PopUpItem
import dev.errnolink.tsuzuki.designsystem.PopUpRow
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import dev.icerock.moko.resources.StringResource
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wave 2 WP11.3: DetentSheet port of `eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog`
 * (the RecyclerView/XML implementation is retired). Every upstream field survives: the dedupe
 * switch and mode, the info-source picker, per-source chapter updates/downloads toggles (each with
 * its upstream confirmation), reorder by priority, delete with confirmation (which dismisses, like
 * upstream), open entry, and the Save callback shape `mergeReference + children`.
 */
@Composable
fun EditMergedSettingsSheet(
    mergedData: MergedMangaData,
    onDismissRequest: () -> Unit,
    onDeleteClick: (MergedMangaReference) -> Unit,
    onPositiveClick: (List<MergedMangaReference>) -> Unit,
    onOpenEntryClick: (MergedMangaReference) -> Unit,
) {
    val context = LocalContext.current
    val sourceManager = remember { Injekt.get<SourceManager>() }

    val invalid = mergedData.references.size <= 1
    LaunchedEffect(invalid) {
        if (invalid) {
            context.toast(SYMR.strings.merged_references_invalid)
            onDismissRequest()
        }
    }
    if (invalid) return

    var children by remember(mergedData) {
        mutableStateOf(
            mergedData.references
                .filter { it.mangaSourceId != MERGED_SOURCE_ID }
                .map { reference -> mergedData.manga.values.firstOrNull { it.id == reference.mangaId } to reference }
                .sortedBy { it.second.chapterPriority },
        )
    }
    var mergeReference by remember(mergedData) {
        mutableStateOf(mergedData.references.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID })
    }
    var pendingToggle by remember { mutableStateOf<Pair<MergedMangaReference, MergedToggle>?>(null) }
    var pendingDelete by remember { mutableStateOf<MergedMangaReference?>(null) }

    val dedupeOn = mergeReference?.let { it.chapterSortMode != MergedMangaReference.CHAPTER_SORT_NONE } ?: false
    val priorityOrder = mergeReference?.let { it.chapterSortMode == MergedMangaReference.CHAPTER_SORT_PRIORITY } ?: false

    fun setSortMode(mode: Int) {
        mergeReference = mergeReference?.copy(chapterSortMode = mode)
    }

    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.tsuzuki_advanced_merge_settings),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Picker,
    ) {
        InsetGroupedList {
            GroupedRow(
                title = stringResource(SYMR.strings.allow_deduplication),
                checked = dedupeOn,
                onCheckedChange = { on ->
                    setSortMode(
                        if (on) MergedMangaReference.CHAPTER_SORT_PRIORITY else MergedMangaReference.CHAPTER_SORT_NONE,
                    )
                },
                divider = false,
            )
            PopUpRow(
                title = stringResource(SYMR.strings.deduplication_mode),
                selected = mergeReference?.chapterSortMode ?: MergedMangaReference.CHAPTER_SORT_NONE,
                enabled = dedupeOn,
                options = listOf(
                    PopUpItem(MergedMangaReference.CHAPTER_SORT_PRIORITY, stringResource(SYMR.strings.dedupe_priority)),
                    PopUpItem(MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS, stringResource(SYMR.strings.dedupe_most_chapters)),
                    PopUpItem(
                        MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER,
                        stringResource(SYMR.strings.dedupe_highest_chapter),
                    ),
                ),
                onSelect = { setSortMode(it) },
                divider = false,
            )
            PopUpRow(
                title = stringResource(SYMR.strings.deduplication_entry_info),
                selected = children.firstOrNull { it.second.isInfoManga }?.second?.id
                    ?: children.firstOrNull()?.second?.id,
                options = children.map { (manga, reference) ->
                    PopUpItem(
                        reference.id,
                        sourceManager.getOrStub(reference.mangaSourceId).name + " " + (manga?.title ?: ""),
                    )
                },
                onSelect = { id ->
                    children = children.map { (manga, reference) ->
                        manga to reference.copy(isInfoManga = reference.id == id)
                    }
                },
                divider = false,
            )
        }

        InsetGroupedList(title = stringResource(MR.strings.tsuzuki_merged_sources)) {
            children.forEachIndexed { index, (manga, reference) ->
                if (index > 0) GroupDivider()
                MergedEditSourceRow(
                    manga = manga,
                    reference = reference,
                    sourceName = sourceManager.getOrStub(reference.mangaSourceId).name,
                    canMove = priorityOrder,
                    canMoveUp = priorityOrder && index > 0,
                    canMoveDown = priorityOrder && index < children.lastIndex,
                    onOpenEntry = { onOpenEntryClick(reference) },
                    onMove = { move ->
                        val reordered = reorderMergePriorities(
                            listOfNotNull(mergeReference) + children.map { it.second },
                            reference.id,
                            move,
                        )
                        children = reordered.filter { it.mangaSourceId != MERGED_SOURCE_ID }
                            .map { ref -> (children.firstOrNull { it.second.id == ref.id }?.first) to ref }
                    },
                    onToggle = { pendingToggle = reference to it },
                    onRemove = { pendingDelete = reference },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = TsuzukiSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_save),
                    onClick = {
                        onPositiveClick(listOfNotNull(mergeReference) + children.map { it.second })
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingToggle?.let { (reference, toggle) ->
        MergedToggleConfirmSheet(
            toggle = toggle,
            onConfirm = {
                children = children.map { (manga, other) ->
                    manga to if (other.id == reference.id) {
                        when (toggle) {
                            MergedToggle.ChapterUpdates -> other.copy(getChapterUpdates = !other.getChapterUpdates)
                            MergedToggle.ChapterDownloads -> other.copy(downloadChapters = !other.downloadChapters)
                        }
                    } else {
                        other
                    }
                }
                pendingToggle = null
            },
            onDismissRequest = { pendingToggle = null },
        )
    }

    pendingDelete?.let { reference ->
        DetentSheet(
            visible = true,
            title = stringResource(SYMR.strings.delete_merged_entry),
            onDismissRequest = { pendingDelete = null },
            kind = SheetKind.Confirm,
        ) {
            TsuzukiText(
                stringResource(SYMR.strings.delete_merged_entry_desc),
                Modifier.padding(bottom = TsuzukiSpacing.medium),
                TsuzukiTheme.typography.body,
                TsuzukiTheme.colors.text,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
            ) {
                Box(Modifier.weight(1f)) {
                    PillButton(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { pendingDelete = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.weight(1f)) {
                    PillButton(
                        label = stringResource(MR.strings.action_ok),
                        onClick = {
                            pendingDelete = null
                            onDeleteClick(reference)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true,
                    )
                }
            }
        }
    }
}

private enum class MergedToggle(val titleRes: StringResource, val messageRes: StringResource) {
    ChapterUpdates(SYMR.strings.chapter_updates_merged_entry, SYMR.strings.chapter_updates_merged_entry_desc),
    ChapterDownloads(SYMR.strings.download_merged_entry, SYMR.strings.download_merged_entry_desc),
}

@Composable
private fun MergedToggleConfirmSheet(
    toggle: MergedToggle,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    DetentSheet(
        visible = true,
        title = stringResource(toggle.titleRes),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Confirm,
    ) {
        TsuzukiText(
            stringResource(toggle.messageRes),
            Modifier.padding(bottom = TsuzukiSpacing.medium),
            TsuzukiTheme.typography.body,
            TsuzukiTheme.colors.text,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_ok),
                    onClick = {
                        onConfirm()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MergedEditSourceRow(
    manga: tachiyomi.domain.manga.model.Manga?,
    reference: MergedMangaReference,
    sourceName: String,
    canMove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpenEntry: () -> Unit,
    onMove: (MergePriorityMove) -> Unit,
    onToggle: (MergedToggle) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 64.dp)
                    .clip(coverShape())
                    .background(colors.elevated)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenEntry),
            ) {
                if (manga != null) {
                    ShellCover(
                        manga.asMangaCover(),
                        manga.title,
                        Modifier.fillMaxSize(),
                        fixedRatio = 44f / 64f,
                        hero = false,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TsuzukiText(
                    sourceName,
                    style = TsuzukiTheme.typography.subhead,
                    color = colors.text,
                    maxLines = 1,
                )
                TsuzukiText(
                    manga?.title ?: "",
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 2,
                )
            }
            if (canMove) {
                TsuzukiText(
                    "${reference.chapterPriority + 1}",
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                )
            }
            val optionsSource = rememberMenuMorphSource()
            val sourceOptionsLabel = stringResource(MR.strings.tsuzuki_source_options)
            val optionsForLabel = stringResource(MR.strings.tsuzuki_options_for, sourceName)
            Box(Modifier.menuMorphSource(optionsSource)) {
                Box(
                    modifier = Modifier
                        .size(TsuzukiSize.control)
                        .clip(CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = sourceOptionsLabel) { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TsuzukiIcons.Ellipsis, contentDescription = optionsForLabel, Modifier.size(20.dp), tint = colors.secondary)
                }
                PullDownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    paneTitle = sourceName,
                    morphSource = optionsSource,
                    actions = buildList {
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.tsuzuki_open_entry),
                                icon = TsuzukiIcons.Info,
                                onClick = { menuOpen = false; onOpenEntry() },
                            ),
                        )
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.tsuzuki_move_up),
                                icon = TsuzukiIcons.ArrowUp,
                                enabled = canMoveUp,
                                onClick = { menuOpen = false; onMove(MergePriorityMove.Up) },
                            ),
                        )
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.tsuzuki_move_down),
                                icon = TsuzukiIcons.ArrowDown,
                                enabled = canMoveDown,
                                onClick = { menuOpen = false; onMove(MergePriorityMove.Down) },
                            ),
                        )
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.tsuzuki_chapter_updates),
                                icon = TsuzukiIcons.Bell,
                                group = 1,
                                onClick = { menuOpen = false; onToggle(MergedToggle.ChapterUpdates) },
                            ),
                        )
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.tsuzuki_download_new_chapters),
                                icon = TsuzukiIcons.Download,
                                group = 1,
                                onClick = { menuOpen = false; onToggle(MergedToggle.ChapterDownloads) },
                            ),
                        )
                        add(
                            PullDownAction(
                                label = stringResource(MR.strings.action_remove),
                                icon = TsuzukiIcons.Trash2,
                                group = 2,
                                destructive = true,
                                onClick = { menuOpen = false; onRemove() },
                            ),
                        )
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dp)
                .height(TsuzukiSpacing.hairline)
                .background(colors.separator),
        )
    }
}
