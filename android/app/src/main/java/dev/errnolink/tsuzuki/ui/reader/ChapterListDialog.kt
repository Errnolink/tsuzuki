package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import tachiyomi.domain.chapter.model.Chapter

@Composable
fun ChapterListDialog(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
    onDownloadAction: ((Chapter, ChapterDownloadAction) -> Unit)? = null,
) {
    ChapterListSheetCurrent(
        visible,
        onDismissRequest,
        chapters,
        onClickChapter,
        onBookmark,
        dateRelativeTime,
        onDownloadAction,
    )
}
