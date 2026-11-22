package dev.errnolink.tsuzuki.ui.details

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
import androidx.compose.runtime.produceState
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
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.ChipTone
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.LocalMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
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
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

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
    val installedSources by produceState(
        initialValue = emptyList<Source>(),
        sourceManager,
        ownSourceIds,
    ) {
        value = withContext(Dispatchers.Default) {
            sourceManager.getVisibleOnlineSources()
                .filter { it.id != MERGED_SOURCE_ID && it.id !in ownSourceIds }
                .distinctBy { it.name }
        }
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
    val searchFailedLabel = stringResource(MR.strings.tsuzuki_search_failed)

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
                            sourceSearchStates[source.id] = MergeSearchStatus.Error(e.message ?: searchFailedLabel)
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
        title = stringResource(MR.strings.tsuzuki_merge_chapters),
        onDismissRequest = onDismissRequest,
        scrollContent = false,
        // DET-02: a search field plus a result list — BrowseList gives it a content-independent
        // height (half the viewport, full while the keyboard is up). Under Picker its open height
        // came from the measured content, but this sheet's content is a fill-MaxSize LazyColumn
        // whose measured height follows the sheet itself (0 while hidden), so the height chased
        // itself and the present settle never left "fully hidden".
        kind = SheetKind.BrowseList,
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
                        title = stringResource(MR.strings.tsuzuki_merged_sources),
                        footer = stringResource(MR.strings.tsuzuki_merge_priority_footer),
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
                                title = stringResource(MR.strings.tsuzuki_advanced_merge_settings),
                                subtitle = stringResource(MR.strings.tsuzuki_advanced_merge_subtitle),
                                chevron = true,
                                divider = false,
                                onClick = onOpenAdvancedSettings,
                            )
                        }
                    }
                } else {
                    InsetGroupedList(
                        title = stringResource(MR.strings.tsuzuki_this_title),
                        footer = stringResource(MR.strings.tsuzuki_merge_this_title_footer),
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
                                    stringResource(MR.strings.tsuzuki_search_for_this_title),
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
                                    contentDescription = stringResource(MR.strings.tsuzuki_clear_search),
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
                            SourceChip(stringResource(MR.strings.tsuzuki_all_sources), !singleSource) { selectedSourceId = null }
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
                        title = stringResource(MR.strings.tsuzuki_search_other_sources),
                        description = stringResource(MR.strings.tsuzuki_search_other_sources_desc),
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
                            ProgressMark(0f, stringResource(MR.strings.tsuzuki_searching_sources))
                            TsuzukiText(
                                pluralStringResource(MR.plurals.tsuzuki_searching_sources, pendingCount, pendingCount),
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
                                    stringResource(MR.strings.tsuzuki_no_matches_on_source, source.name),
                                    TsuzukiTheme.colors.secondary,
                                )
                                is MergeSearchStatus.Error -> MergeMessageRow(
                                    stringResource(MR.strings.tsuzuki_couldnt_search_source, source.name, status.message),
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
                            title = stringResource(MR.strings.tsuzuki_no_matches),
                            description = stringResource(MR.strings.tsuzuki_no_matches_any_source),
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
            title = stringResource(MR.strings.tsuzuki_remove_merge),
            onDismissRequest = { pendingDeleteRef = null },
            kind = SheetKind.Confirm,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TsuzukiText(
                    stringResource(MR.strings.tsuzuki_remove_merge_confirm, deleteSourceName),
                    style = TsuzukiTheme.typography.body,
                    color = TsuzukiTheme.colors.text,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PillButton(
                            label = stringResource(MR.strings.action_cancel),
                            onClick = { pendingDeleteRef = null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PillButton(
                            label = stringResource(MR.strings.action_remove),
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
                    if (main) MergeTag(stringResource(MR.strings.tsuzuki_merge_main), colors.accent)
                }
            }
            if (onRemove != null) {
                // The options circle is the drop: its menu pours from it and returns into it.
                val optionsSource = rememberMenuMorphSource(borderless = true)
                Box(Modifier.menuMorphSource(optionsSource)) {
                    Box(
                        modifier = Modifier
                            .size(TsuzukiSize.control)
                            .clickable(role = Role.Button, onClickLabel = stringResource(MR.strings.tsuzuki_source_options)) { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(TsuzukiIcons.Ellipsis, contentDescription = stringResource(MR.strings.tsuzuki_options_for, sourceName), Modifier.size(20.dp), tint = colors.secondary)
                    }
                    PullDownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        paneTitle = sourceName,
                        morphSource = optionsSource,
                        actions = buildList {
                            if (onMoveUp != null) {
                                add(PullDownAction(label = stringResource(MR.strings.tsuzuki_move_up), icon = TsuzukiIcons.ArrowUp, enabled = canMoveUp, onClick = { menuOpen = false; onMoveUp() }))
                            }
                            if (onMoveDown != null) {
                                add(PullDownAction(label = stringResource(MR.strings.tsuzuki_move_down), icon = TsuzukiIcons.ArrowDown, enabled = canMoveDown, onClick = { menuOpen = false; onMoveDown() }))
                            }
                            add(PullDownAction(label = stringResource(MR.strings.tsuzuki_remove_from_merge), icon = TsuzukiIcons.Trash2, group = 1, destructive = true, onClick = { menuOpen = false; onRemove() }))
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
            .clip(coverShape())
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
    Chip(
        label = label,
        selected = selected,
        onClick = onClick,
        tone = ChipTone.Neutral,
        minHeight = 36.dp,
        horizontalPadding = 14.dp,
        textStyle = TsuzukiTheme.typography.footnote,
    )
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
                    candidate.chapterCount?.let { pluralStringResource(MR.plurals.manga_num_chapters, it, it) },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    TsuzukiText(details, style = TsuzukiTheme.typography.footnote, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                merged -> MergeTag(stringResource(MR.strings.tsuzuki_merged), colors.secondary)
                isMerging -> Box(Modifier.size(TsuzukiSize.control), contentAlignment = Alignment.Center) { ProgressMark(0f, stringResource(MR.strings.tsuzuki_merging)) }
                else -> Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = mergeEnabled, role = Role.Button, onClickLabel = stringResource(MR.strings.tsuzuki_merge_title, candidate.manga.title), onClick = onMergeClick)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiText(
                        stringResource(MR.strings.tsuzuki_merge),
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
