package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.LiquidCard
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
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
internal fun ChapterListSheetCurrent(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
    onDownloadAction: ((Chapter, ChapterDownloadAction) -> Unit)? = null,
) {
    val morphSources = LocalReaderMorphSources.current
    if (morphSources != null) {
        LiquidCard(
            visible = visible,
            source = morphSources.activeChaptersSource,
            onDismissRequest = onDismissRequest,
            maxHeight = 460.dp,
        ) {
            ChapterListContent(
                visible = visible,
                onDismissRequest = onDismissRequest,
                chapters = chapters,
                onClickChapter = onClickChapter,
                onBookmark = onBookmark,
                dateRelativeTime = dateRelativeTime,
                onDownloadAction = onDownloadAction,
                showHeader = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    } else if (visible) {
        DetentSheet(true, stringResource(MR.strings.chapters), onDismissRequest, scrollContent = false, initialDetent = SheetDetent.Large, kind = SheetKind.Free) {
            ChapterListContent(
                visible = visible,
                onDismissRequest = onDismissRequest,
                chapters = chapters,
                onClickChapter = onClickChapter,
                onBookmark = onBookmark,
                dateRelativeTime = dateRelativeTime,
                onDownloadAction = onDownloadAction,
                showHeader = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ChapterListContent(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
    onDownloadAction: ((Chapter, ChapterDownloadAction) -> Unit)?,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
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
    val initialScroll = chapterListInitialScroll(chapters.indexOfFirst { it.isCurrent }, chapters.size)
    val currentState = rememberLazyListState(initialScroll)
    LaunchedEffect(visible) {
        if (visible) {
            val targetIdx = chapterListInitialScroll(visibleChapters.indexOfFirst { it.isCurrent }, visibleChapters.size)
            currentState.scrollToItem(targetIdx)
        }
    }
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val queue by downloadManager.queueState.collectAsState()
    val colors = TsuzukiTheme.colors
    val cardShape = RoundedCornerShape(14.dp)
    val readingNowLabel = stringResource(MR.strings.tsuzuki_reading_now)

    Column(modifier) {
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TsuzukiText(
                        stringResource(MR.strings.chapters),
                        style = TsuzukiTheme.typography.headline.copy(fontWeight = FontWeight.Bold),
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.text.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        TsuzukiText(
                            visibleChapters.size.toString(),
                            style = TsuzukiTheme.typography.caption2.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.secondary,
                        )
                    }
                }
                TsuzukiButton(
                    label = stringResource(MR.strings.tsuzuki_done),
                    onClick = onDismissRequest,
                    size = TsuzukiButtonSize.Xs,
                    variant = TsuzukiButtonVariant.Soft,
                    minWidth = 0.dp,
                )
            }
        }
        SearchField(
            value = query,
            onValueChange = { query = it },
            onSearch = { },
            placeholder = stringResource(MR.strings.tsuzuki_search_chapters),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f, fill = !showHeader)) {
            if (visibleChapters.isEmpty()) {
                if (chapters.isEmpty()) {
                    ShellEmpty(stringResource(MR.strings.tsuzuki_no_chapters), stringResource(MR.strings.tsuzuki_no_chapters_sheet_desc))
                } else {
                    EmptyState(
                        title = stringResource(MR.strings.tsuzuki_no_matches),
                        description = stringResource(MR.strings.tsuzuki_no_chapter_title_matches, query.trim()),
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.padding(top = 8.dp), tint = colors.secondary) },
                    )
                }
            } else {
                LazyColumn(
                    flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                    state = currentState,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
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
                        val cardModifier = Modifier
                            .clip(cardShape)
                            .background(
                                if (item.isCurrent) colors.accent.copy(alpha = 0.12f)
                                else colors.grouped.copy(alpha = 0.55f),
                            )
                            .border(
                                width = if (item.isCurrent) 1.5.dp else 1.dp,
                                color = if (item.isCurrent) colors.accent.copy(alpha = 0.55f) else colors.separator.copy(alpha = 0.35f),
                                shape = cardShape,
                            )
                            .then(
                                if (item.chapter.read && !item.isCurrent) Modifier.alpha(0.62f) else Modifier,
                            )
                        MangaChapterListItem(
                            modifier = cardModifier,
                            title = dev.errnolink.tsuzuki.ui.details.chapterRowTitle(
                                item.chapter.name,
                                item.chapter.chapterNumber,
                                item.manga.displayMode == tachiyomi.domain.manga.model.Manga.CHAPTER_DISPLAY_NUMBER,
                                dev.errnolink.tsuzuki.ui.details.chapterRowStrings(),
                            ),
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
                            shape = cardShape,
                            actions = if (item.isCurrent) {
                                {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.accent.copy(alpha = 0.18f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        TsuzukiText(
                                            readingNowLabel,
                                            style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold),
                                            color = colors.accent,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            isCurrent = item.isCurrent,
                            showDivider = false,
                        )
                    }
                }
            }
            val currentIndex = visibleChapters.indexOfFirst { it.isCurrent }
            val currentKey = visibleChapters.getOrNull(currentIndex)?.chapter?.id
            val currentOffScreen by remember(currentKey) {
                derivedStateOf {
                    currentKey != null && currentState.layoutInfo.visibleItemsInfo.none { it.key == currentKey }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = currentOffScreen,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
                TsuzukiButton(
                    label = stringResource(MR.strings.transition_current).removeSuffix(":"),
                    onClick = { scope.launch { currentState.animateScrollToItem(chapterListInitialScroll(currentIndex, visibleChapters.size)) } },
                    size = TsuzukiButtonSize.Xs,
                    variant = TsuzukiButtonVariant.Container,
                    icon = { Icon(TsuzukiIcons.BookOpen, null, Modifier.size(14.dp)) },
                    minWidth = 0.dp,
                )
            }
        }
    }
}

/** Room under the last chapter row for the floating Current pill (44 dp pill + 16 dp margin + 12 dp air). */
private val CurrentPillClearance = 72.dp
