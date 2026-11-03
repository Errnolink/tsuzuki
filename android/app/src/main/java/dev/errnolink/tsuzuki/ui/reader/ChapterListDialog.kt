package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.GlassButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.ui.details.MangaChapterListItem
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ChapterListDialog(
    onDismissRequest: () -> Unit,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
    onDownloadAction: ((Chapter, ChapterDownloadAction) -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = TsuzukiTheme.haptics
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val visibleChapters = remember(chapters, query) {
        if (query.isBlank()) {
            chapters
        } else {
            chapters.filter { it.chapter.name.contains(query.trim(), ignoreCase = true) }
        }
    }
    val currentState = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val queue by downloadManager.queueState.collectAsState()
    DetentSheet(true, stringResource(MR.strings.chapters), onDismissRequest, scrollContent = false, initialDetent = SheetDetent.Large) {
        Column(Modifier.fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { },
                placeholder = "Search chapters",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                if (visibleChapters.isEmpty()) {
                    if (chapters.isEmpty()) {
                        ShellEmpty("No chapters", "The source has no chapters available for this title.")
                    } else {
                        EmptyState(
                            title = "No matches",
                            description = "No chapter titles match \"${query.trim()}\".",
                            icon = { Icon(TsuzukiIcons.Search, null, Modifier.padding(top = 8.dp), tint = TsuzukiTheme.colors.secondary) },
                        )
                    }
                } else {
                    LazyColumn(
                        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                        state = currentState,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(TsuzukiTheme.colors.grouped),
                    ) {
                        items(visibleChapters, key = { it.chapter.id }, contentType = { "chapter" }) { item ->
                            val active = queue.find { it.chapter.id == item.chapter.id }
                            val progress = active?.let {
                                remember(item.chapter.id) {
                                    downloadManager.progressFlow().filter { it.chapter.id == item.chapter.id }.map { it.progress }
                                }.collectAsState(0).value
                            } ?: 0
                            val downloaded = item.manga.isLocal() || downloadManager.isChapterDownloaded(
                                item.chapter.name, item.chapter.scanlator, item.chapter.url, item.manga.ogTitle, item.manga.source,
                            )
                            val downloadState = active?.status ?: if (downloaded) Download.State.DOWNLOADED else Download.State.NOT_DOWNLOADED
                            MangaChapterListItem(
                                title = item.chapter.name,
                                date = item.chapter.dateUpload.takeIf { it > 0L }?.let {
                                    LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toRelativeString(context, dateRelativeTime, item.dateFormat)
                                },
                                lastPageRead = null,
                                pageTotal = null,
                                scanlator = item.chapter.scanlator,
                                sourceName = null,
                                read = item.chapter.read,
                                bookmark = item.chapter.bookmark,
                                selected = false,
                                downloadIndicatorEnabled = onDownloadAction != null,
                                downloadStateProvider = { downloadState },
                                downloadProgressProvider = { progress },
                                chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                                chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                                onLongClick = { onBookmark(item.chapter) },
                                onClick = { haptics.chapterArm(); onClickChapter(item.chapter) },
                                onDownloadClick = onDownloadAction?.let { callback -> { callback(item.chapter, it) } },
                                onChapterSwipe = { onBookmark(item.chapter) },
                                shape = RoundedCornerShape(0.dp),
                                isCurrent = item.isCurrent,
                            )
                        }
                    }
                }
                val currentIndex = visibleChapters.indexOfFirst { it.isCurrent }
                if (currentIndex >= 0) {
                    GlassButton(
                        context = null,
                        onClick = { scope.launch { currentState.animateScrollToItem(currentIndex) } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    ) {
                        Icon(TsuzukiIcons.BookOpen, null)
                        TsuzukiText(stringResource(MR.strings.transition_current).removeSuffix(":"), style = TsuzukiTheme.typography.footnote)
                    }
                }
            }
        }
    }
}
