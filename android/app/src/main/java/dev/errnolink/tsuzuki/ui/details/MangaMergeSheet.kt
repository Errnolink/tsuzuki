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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.ChevronDown
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

    val installedSources = remember(sourceManager) {
        sourceManager.getVisibleOnlineSources().filter { it.id != MERGED_SOURCE_ID }.distinctBy { it.name }
    }

    var searchQuery by remember(manga.id) { mutableStateOf(manga.title) }
    var selectedSourceId by remember(manga.id, installedSources) {
        mutableStateOf(resolveMergePrimarySourceId(manga, mergedData, installedSources.map { it.id }))
    }
    var isSourceMenuOpen by remember { mutableStateOf(false) }
    var isMergingId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteRef by remember { mutableStateOf<MergedMangaReference?>(null) }

    val sourceSearchStates = remember { mutableStateMapOf<Long, MergeSearchStatus>() }
    var activeSearchJob by remember { mutableStateOf<Job?>(null) }

    val targetSources = remember(selectedSourceId, installedSources) {
        val targetId = selectedSourceId
        if (targetId != null) {
            installedSources.filter { it.id == targetId }
        } else {
            installedSources
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("merged_sources_section") {
                if (mergedData != null) {
                    val childRefs = remember(mergedData.references) {
                        mergedData.references
                            .filter { it.mangaSourceId != MERGED_SOURCE_ID }
                            .sortedBy { it.chapterPriority }
                    }

                    InsetGroupedList(title = "Merged sources", footer = "Chapters are prioritized in descending order.") {
                        childRefs.forEachIndexed { index, child ->
                            val childManga = mergedData.manga[child.mangaId]
                            val sourceName = sourceManager.getOrStub(child.mangaSourceId).name
                            val title = childManga?.title ?: manga.title

                            MergeSourceRow(
                                title = title,
                                subtitle = if (child.isInfoManga) "$sourceName · Main source" else "$sourceName · Priority ${child.chapterPriority + 1}",
                                badgeText = if (child.isInfoManga) "Main" else "P${child.chapterPriority + 1}",
                                badgeActive = child.isInfoManga,
                                moveUpEnabled = index > 0,
                                moveDownEnabled = index < childRefs.lastIndex,
                                divider = index < childRefs.lastIndex,
                                onMoveUp = {
                                    val updated = reorderMergePriorities(mergedData.references, child.id, MergePriorityMove.Up)
                                    onReorderMerge(updated)
                                },
                                onMoveDown = {
                                    val updated = reorderMergePriorities(mergedData.references, child.id, MergePriorityMove.Down)
                                    onReorderMerge(updated)
                                },
                                onRemove = { pendingDeleteRef = child },
                            )
                        }

                        if (onOpenAdvancedSettings != null) {
                            GroupedRow(
                                title = "Advanced merge settings",
                                subtitle = "Deduplication mode, downloads and sync",
                                chevron = true,
                                divider = false,
                                onClick = onOpenAdvancedSettings,
                            )
                        }
                    }
                } else {
                    InsetGroupedList(title = "Primary source") {
                        val sourceName = sourceManager.getOrStub(manga.source).name
                        GroupedRow(
                            title = manga.title,
                            subtitle = "$sourceName · Source of truth",
                            divider = false,
                        )
                    }
                }
            }

            item("search_input_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TsuzukiSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TsuzukiText(
                        "SEARCH CANDIDATES",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = TsuzukiTheme.typography.sectionHeader,
                        color = TsuzukiTheme.colors.secondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(TsuzukiCorners.field))
                                .background(TsuzukiTheme.colors.elevated)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                TsuzukiIcons.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TsuzukiTheme.colors.secondary,
                            )

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
                                modifier = Modifier.weight(1f),
                            )

                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            searchQuery = ""
                                            executeSearch("")
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        TsuzukiIcons.X,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(14.dp),
                                        tint = TsuzukiTheme.colors.secondary,
                                    )
                                }
                            }
                        }

                        Box {
                            val currentSourceLabel = remember(selectedSourceId, installedSources) {
                                val currentId = selectedSourceId
                                if (currentId == null) {
                                    "All sources"
                                } else {
                                    installedSources.firstOrNull { it.id == currentId }?.name ?: "Source"
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(TsuzukiCorners.field))
                                    .background(TsuzukiTheme.colors.elevated)
                                    .clickable { isSourceMenuOpen = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TsuzukiText(
                                    currentSourceLabel,
                                    style = TsuzukiTheme.typography.footnote,
                                    color = TsuzukiTheme.colors.text,
                                    maxLines = 1,
                                )
                                Icon(
                                    TsuzukiIcons.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = TsuzukiTheme.colors.secondary,
                                )
                            }

                            PullDownMenu(
                                expanded = isSourceMenuOpen,
                                onDismissRequest = { isSourceMenuOpen = false },
                                paneTitle = "Select search source",
                                actions = buildList {
                                    add(
                                        PullDownAction(
                                            label = "All installed sources",
                                            group = 0,
                                            selected = selectedSourceId == null,
                                            onClick = {
                                                selectedSourceId = null
                                                isSourceMenuOpen = false
                                            },
                                        ),
                                    )
                                    installedSources.forEach { src ->
                                        add(
                                            PullDownAction(
                                                label = src.name,
                                                group = 1,
                                                selected = selectedSourceId == src.id,
                                                onClick = {
                                                    selectedSourceId = src.id
                                                    isSourceMenuOpen = false
                                                },
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            val queryEmpty = searchQuery.trim().isEmpty()
            if (queryEmpty) {
                item("search_empty_state") {
                    EmptyState(
                        title = "Search other sources",
                        description = "Search other sources to merge chapters into this title. Results replace nothing; you choose what to merge.",
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(28.dp), tint = TsuzukiTheme.colors.secondary) },
                    )
                }
            } else {
                val pendingCount = targetSources.count { sourceSearchStates[it.id] is MergeSearchStatus.Loading }
                if (pendingCount > 0) {
                    item("search_progress") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = TsuzukiSpacing.gutter, vertical = 8.dp),
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
                    item(key = "source_${source.id}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TsuzukiSpacing.gutter),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TsuzukiText(
                                source.name,
                                style = TsuzukiTheme.typography.headline,
                                color = TsuzukiTheme.colors.text,
                            )

                            when (status) {
                                is MergeSearchStatus.Empty -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(TsuzukiCorners.group))
                                            .background(TsuzukiTheme.colors.grouped)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TsuzukiText(
                                            "No matching titles found on ${source.name}",
                                            style = TsuzukiTheme.typography.footnote,
                                            color = TsuzukiTheme.colors.secondary,
                                        )
                                    }
                                }
                                is MergeSearchStatus.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(TsuzukiCorners.group))
                                            .background(TsuzukiTheme.colors.grouped)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TsuzukiText(
                                            "Error: ${status.message}",
                                            style = TsuzukiTheme.typography.footnote,
                                            color = TsuzukiTheme.colors.destructive,
                                        )
                                    }
                                }
                                is MergeSearchStatus.Success -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(TsuzukiCorners.group))
                                            .background(TsuzukiTheme.colors.grouped),
                                    ) {
                                        status.candidates.forEachIndexed { index, candidate ->
                                            CandidateRow(
                                                candidate = candidate,
                                                isMerging = isMergingId == candidate.manga.id,
                                                divider = index < status.candidates.lastIndex,
                                                onMergeClick = {
                                                    isMergingId = candidate.manga.id
                                                    onMergeCandidate(candidate.manga)
                                                },
                                            )
                                        }
                                    }
                                }
                                MergeSearchStatus.Loading -> Unit
                            }
                        }
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
    title: String,
    subtitle: String,
    badgeText: String,
    badgeActive: Boolean,
    moveUpEnabled: Boolean,
    moveDownEnabled: Boolean,
    divider: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (badgeActive) colors.accent.copy(alpha = 0.15f) else colors.elevated)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TsuzukiText(
                    badgeText,
                    style = TsuzukiTheme.typography.caption2,
                    color = if (badgeActive) colors.accent else colors.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TsuzukiText(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TsuzukiText(subtitle, style = TsuzukiTheme.typography.footnote, color = colors.secondary)
            }
            MergeAccessoryButton("Move up", TsuzukiIcons.ArrowUp, colors.text, moveUpEnabled, onMoveUp)
            MergeAccessoryButton("Move down", TsuzukiIcons.ArrowDown, colors.text, moveDownEnabled, onMoveDown)
            MergeAccessoryButton("Remove source", TsuzukiIcons.Trash2, colors.destructive, true, onRemove)
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(TsuzukiSpacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}

@Composable
private fun MergeAccessoryButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: MergeCandidate,
    isMerging: Boolean,
    divider: Boolean,
    onMergeClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isMerging, onClick = onMergeClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(candidate.manga.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = candidate.manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(TsuzukiCorners.cover))
                    .background(TsuzukiTheme.colors.elevated),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TsuzukiText(
                    candidate.manga.title,
                    style = TsuzukiTheme.typography.subhead,
                    color = TsuzukiTheme.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TsuzukiTheme.colors.elevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        TsuzukiText(
                            candidate.source.name,
                            style = TsuzukiTheme.typography.caption2,
                            color = TsuzukiTheme.colors.secondary,
                        )
                    }

                    val count = candidate.chapterCount
                    if (count != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TsuzukiTheme.colors.elevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            TsuzukiText(
                                "$count ch",
                                style = TsuzukiTheme.typography.caption2,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                    }
                }
            }

            if (isMerging) {
                ProgressRing(0f, "Merging")
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(TsuzukiTheme.colors.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    TsuzukiText(
                        "Merge",
                        style = TsuzukiTheme.typography.footnote,
                        color = TsuzukiTheme.colors.accent,
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
                    .background(TsuzukiTheme.colors.separator),
            )
        }
    }
}
