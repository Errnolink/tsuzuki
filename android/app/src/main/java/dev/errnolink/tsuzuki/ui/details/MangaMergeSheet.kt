package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import tachiyomi.domain.manga.model.asMangaCover
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed interface MergeSearchStatus {
    data object Loading : MergeSearchStatus
    data class Success(val candidates: List<MergeCandidate>) : MergeSearchStatus
    data object Empty : MergeSearchStatus
    data class Error(val message: String) : MergeSearchStatus
}

data class MergeCandidate(
    val manga: Manga,
    val source: Source,
    val chapterCount: Int?,
)

@Composable
fun MangaMergeSheet(
    visible: Boolean,
    manga: Manga,
    mergedData: MergedMangaData?,
    onDismissRequest: () -> Unit,
    onMergeCandidate: (Manga) -> Unit,
    onDeleteMerge: (MergedMangaReference) -> Unit,
    onReorderMerge: (List<MergedMangaReference>) -> Unit,
    onOpenAdvancedSettings: (() -> Unit)? = null,
) {
    if (!visible) return

    val sourceManager: SourceManager = remember { Injekt.get() }
    val networkToLocalManga: NetworkToLocalManga = remember { Injekt.get() }
    val chapterRepository: ChapterRepository = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val ownSourceIds = remember(manga.source, mergedData?.references) {
        (mergedData?.references.orEmpty().map { it.mangaSourceId } + manga.source).toSet()
    }
    val installedSources = remember(sourceManager, ownSourceIds) {
        sourceManager.getVisibleOnlineSources()
            .filter { it.id != MERGED_SOURCE_ID && it.id !in ownSourceIds }
            .distinctBy { it.name }
    }

    var searchQuery by remember(manga.id) { mutableStateOf(manga.title) }
    var selectedSourceId by remember(manga.id) { mutableStateOf<Long?>(null) }
    var isMergingId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteRef by remember { mutableStateOf<MergedMangaReference?>(null) }

    val sourceSearchStates = remember { mutableStateMapOf<Long, MergeSearchStatus>() }
    var activeSearchJob by remember { mutableStateOf<Job?>(null) }

    val targetSources = remember(selectedSourceId, installedSources) {
        installedSources.filter { it.id == selectedSourceId }.ifEmpty { installedSources }
    }
    val singleSource = targetSources.size == 1 && selectedSourceId != null
    val gutter = dev.errnolink.tsuzuki.designsystem.LocalGroupedListGutter.current

    fun executeSearch(query: String) {
        activeSearchJob?.cancel()
        sourceSearchStates.clear()
        val clean = query.trim()
        if (clean.isEmpty() || targetSources.isEmpty()) return

        targetSources.forEach { source ->
            sourceSearchStates[source.id] = MergeSearchStatus.Loading
        }

        activeSearchJob = scope.launch {
            supervisorScope {
                targetSources.map { source ->
                    async {
                        try {
                            val page = withContext(Dispatchers.IO) {
                                source.getSearchManga(1, clean, source.getFilterList())
                            }
                            val domainMangas = page.mangas.map { it.toDomainManga(source.id) }
                            val localMangas = withContext(Dispatchers.IO) {
                                networkToLocalManga(domainMangas, updateInfo = false)
                            }
                            val candidates = localMangas.map { local ->
                                val count = withContext(Dispatchers.IO) {
                                    chapterRepository.getChapterByMangaId(local.id).size.takeIf { it > 0 }
                                }
                                MergeCandidate(local, source, count)
                            }
                            val ordered = orderMergeCandidates(candidates.map { it.manga }, clean)
                            val orderedCandidates = ordered.mapNotNull { ord ->
                                candidates.firstOrNull { it.manga.id == ord.id }
                            }

                            if (orderedCandidates.isEmpty()) {
                                sourceSearchStates[source.id] = MergeSearchStatus.Empty
                            } else {
                                sourceSearchStates[source.id] = MergeSearchStatus.Success(orderedCandidates)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            sourceSearchStates[source.id] = MergeSearchStatus.Error(e.message ?: "Search failed")
                        }
                    }
                }.awaitAll()
            }
        }
    }

    LaunchedEffect(manga.id, targetSources) {
        executeSearch(searchQuery)
    }

    val mergedChildIds = remember(mergedData?.references) {
        mergedData?.references.orEmpty().filter { it.mangaSourceId != MERGED_SOURCE_ID }.mapNotNull { it.mangaId }.toSet()
    }
    LaunchedEffect(mergedData?.references) { isMergingId = null }

    DetentSheet(
        visible = visible,
        title = "Merge chapters",
        onDismissRequest = onDismissRequest,
        initialDetent = SheetDetent.Large,
        scrollContent = false,
    ) {
        LazyColumn(
            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("merged_sources_section") {
                if (mergedData != null) {
                    val childRefs = remember(mergedData.references) {
                        mergedData.references
                            .filter { it.mangaSourceId != MERGED_SOURCE_ID }
                            .sortedBy { it.chapterPriority }
                    }

                    InsetGroupedList(
                        title = "Merged sources",
                        footer = "When sources share a chapter, the one higher in this list is used.",
                    ) {
                        childRefs.forEachIndexed { index, child ->
                            val childManga = mergedData.manga[child.mangaId]
                            val sourceName = sourceManager.getOrStub(child.mangaSourceId).name
                            MergeSourceRow(
                                manga = childManga,
                                fallbackTitle = manga.title,
                                sourceName = sourceName,
                                main = child.isInfoManga,
                                canMoveUp = index > 0,
                                canMoveDown = index < childRefs.lastIndex,
                                divider = index < childRefs.lastIndex || onOpenAdvancedSettings != null,
                                onMoveUp = {
                                    onReorderMerge(reorderMergePriorities(mergedData.references, child.id, MergePriorityMove.Up))
                                },
                                onMoveDown = {
                                    onReorderMerge(reorderMergePriorities(mergedData.references, child.id, MergePriorityMove.Down))
                                },
                                onRemove = { pendingDeleteRef = child },
                            )
                        }

                        if (onOpenAdvancedSettings != null) {
                            GroupedRow(
                                title = "Advanced merge settings",
                                subtitle = "Duplicates, downloads and sync",
                                chevron = true,
                                divider = false,
                                onClick = onOpenAdvancedSettings,
                            )
                        }
                    }
                } else {
                    InsetGroupedList(
                        title = "This title",
                        footer = "Find the same title on another source to add its chapters here.",
                    ) {
                        MergeSourceRow(
                            manga = manga,
                            fallbackTitle = manga.title,
                            sourceName = sourceManager.getOrStub(manga.source).name,
                            main = true,
                            canMoveUp = false,
                            canMoveDown = false,
                            divider = false,
                            onMoveUp = null,
                            onMoveDown = null,
                            onRemove = null,
                        )
                    }
                }
            }

            item("search_input_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = gutter)
                            .height(48.dp)
                            .clip(RoundedCornerShape(TsuzukiCorners.field))
                            .background(TsuzukiTheme.colors.grouped)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            TsuzukiIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = TsuzukiTheme.colors.secondary,
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                TsuzukiText(
                                    "Search for this title",
                                    style = TsuzukiTheme.typography.body,
                                    color = TsuzukiTheme.colors.secondary,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TsuzukiTheme.typography.body.copy(color = TsuzukiTheme.colors.text),
                                cursorBrush = SolidColor(TsuzukiTheme.colors.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    executeSearch(searchQuery)
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) {
                                        searchQuery = ""
                                        executeSearch("")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    TsuzukiIcons.X,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp),
                                    tint = TsuzukiTheme.colors.secondary,
                                )
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item("all") {
                            SourceChip("All sources", !singleSource) { selectedSourceId = null }
                        }
                        items(installedSources, key = { it.id }) { src ->
                            SourceChip(src.name, singleSource && targetSources.single().id == src.id) { selectedSourceId = src.id }
                        }
                    }
                }
            }

            val queryEmpty = searchQuery.trim().isEmpty()
            if (queryEmpty) {
                item("search_empty_state") {
                    EmptyState(
                        title = "Search other sources",
                        description = "Find this title on another source to add its chapters. Nothing changes until you tap Merge.",
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(28.dp), tint = TsuzukiTheme.colors.secondary) },
                    )
                }
            } else {
                val pendingCount = targetSources.count { sourceSearchStates[it.id] is MergeSearchStatus.Loading }
                if (pendingCount > 0) {
                    item("search_progress") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = gutter + 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProgressRing(0f, "Searching sources")
                            TsuzukiText(
                                "Searching $pendingCount ${if (pendingCount == 1) "source" else "sources"}…",
                                style = TsuzukiTheme.typography.footnote,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                    }
                }
                targetSources.forEach { source ->
                    val status = sourceSearchStates[source.id]
                    if (status == null || status is MergeSearchStatus.Loading) return@forEach
                    if (status is MergeSearchStatus.Empty && !singleSource) return@forEach
                    item(key = "source_${source.id}") {
                        InsetGroupedList(title = source.name) {
                            when (status) {
                                is MergeSearchStatus.Empty -> MergeMessageRow(
                                    "No matches on ${source.name}. Try a shorter or alternative title.",
                                    TsuzukiTheme.colors.secondary,
                                )
                                is MergeSearchStatus.Error -> MergeMessageRow(
                                    "Couldn't search ${source.name}: ${status.message}",
                                    TsuzukiTheme.colors.destructive,
                                )
                                is MergeSearchStatus.Success -> status.candidates.forEachIndexed { index, candidate ->
                                    CandidateRow(
                                        candidate = candidate,
                                        merged = candidate.manga.id in mergedChildIds || candidate.manga.id == manga.id,
                                        isMerging = isMergingId == candidate.manga.id,
                                        mergeEnabled = isMergingId == null,
                                        divider = index < status.candidates.lastIndex,
                                        onMergeClick = {
                                            isMergingId = candidate.manga.id
                                            onMergeCandidate(candidate.manga)
                                        },
                                    )
                                }
                                MergeSearchStatus.Loading -> Unit
                            }
                        }
                    }
                }
                val allEmpty = !singleSource && pendingCount == 0 && targetSources.isNotEmpty() &&
                    targetSources.all { sourceSearchStates[it.id] is MergeSearchStatus.Empty }
                if (allEmpty) {
                    item("all_empty") {
                        EmptyState(
                            title = "No matches",
                            description = "None of your sources found this title. Try a shorter or alternative title.",
                            icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(28.dp), tint = TsuzukiTheme.colors.secondary) },
                        )
                    }
                }
            }
        }
    }

    val deleteRef = pendingDeleteRef
    if (deleteRef != null) {
        val deleteSourceName = remember(deleteRef) {
            sourceManager.getOrStub(deleteRef.mangaSourceId).name
        }
        DetentSheet(
            visible = true,
            title = "Remove merge",
            onDismissRequest = { pendingDeleteRef = null },
            initialDetent = SheetDetent.FitContent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TsuzukiText(
                    "Remove $deleteSourceName from merged chapters? Existing chapters from this source will no longer appear in the chapter list.",
                    style = TsuzukiTheme.typography.body,
                    color = TsuzukiTheme.colors.text,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PillButton(
                            label = "Cancel",
                            onClick = { pendingDeleteRef = null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PillButton(
                            label = "Remove",
                            destructive = true,
                            onClick = {
                                pendingDeleteRef = null
                                onDeleteMerge(deleteRef)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeSourceRow(
    manga: Manga?,
    fallbackTitle: String,
    sourceName: String,
    main: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    divider: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val colors = TsuzukiTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MergeCover(manga, fallbackTitle)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TsuzukiText(
                    manga?.title ?: fallbackTitle,
                    style = TsuzukiTheme.typography.subhead,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TsuzukiText(
                        sourceName,
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (main) MergeTag("Main", colors.accent)
                }
            }
            if (onRemove != null) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Source options") { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(TsuzukiIcons.Ellipsis, contentDescription = "Options for $sourceName", Modifier.size(20.dp), tint = colors.secondary)
                    }
                    PullDownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        paneTitle = sourceName,
                        actions = buildList {
                            if (onMoveUp != null) {
                                add(PullDownAction(label = "Move up", icon = TsuzukiIcons.ArrowUp, enabled = canMoveUp, onClick = { menuOpen = false; onMoveUp() }))
                            }
                            if (onMoveDown != null) {
                                add(PullDownAction(label = "Move down", icon = TsuzukiIcons.ArrowDown, enabled = canMoveDown, onClick = { menuOpen = false; onMoveDown() }))
                            }
                            add(PullDownAction(label = "Remove from merge", icon = TsuzukiIcons.Trash2, group = 1, destructive = true, onClick = { menuOpen = false; onRemove() }))
                        },
                    )
                }
            }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp)
                    .height(TsuzukiSpacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}

@Composable
private fun MergeCover(manga: Manga?, title: String) {
    Box(
        Modifier
            .size(width = 44.dp, height = 64.dp)
            .clip(RoundedCornerShape(TsuzukiCorners.cover))
            .background(TsuzukiTheme.colors.elevated),
    ) {
        if (manga != null) {
            ShellCover(manga.asMangaCover(), title, Modifier.fillMaxSize(), fixedRatio = 44f / 64f, hero = false)
        }
    }
}

@Composable
private fun MergeTag(label: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        TsuzukiText(label, style = TsuzukiTheme.typography.caption2, color = tint)
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.grouped)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(TsuzukiIcons.Check, null, Modifier.size(14.dp), tint = colors.accent)
        TsuzukiText(label, style = TsuzukiTheme.typography.footnote, color = if (selected) colors.accent else colors.text, maxLines = 1)
    }
}

@Composable
private fun MergeMessageRow(text: String, color: Color) {
    TsuzukiText(
        text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = TsuzukiTheme.typography.footnote,
        color = color,
    )
}

@Composable
private fun CandidateRow(
    candidate: MergeCandidate,
    merged: Boolean,
    isMerging: Boolean,
    mergeEnabled: Boolean,
    divider: Boolean,
    onMergeClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MergeCover(candidate.manga, candidate.manga.title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TsuzukiText(
                    candidate.manga.title,
                    style = TsuzukiTheme.typography.subhead,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    candidate.manga.author?.takeIf { it.isNotBlank() },
                    candidate.chapterCount?.let { "$it chapters" },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    TsuzukiText(details, style = TsuzukiTheme.typography.footnote, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                merged -> MergeTag("Merged", colors.secondary)
                isMerging -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ProgressRing(0f, "Merging") }
                else -> Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = if (mergeEnabled) 0.18f else 0.08f))
                        .clickable(enabled = mergeEnabled, role = Role.Button, onClickLabel = "Merge ${candidate.manga.title}", onClick = onMergeClick)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiText(
                        "Merge",
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.accent.copy(alpha = if (mergeEnabled) 1f else 0.4f),
                    )
                }
            }
        }
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp)
                    .height(TsuzukiSpacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}
