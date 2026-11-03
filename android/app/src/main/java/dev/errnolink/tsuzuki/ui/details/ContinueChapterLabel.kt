package dev.errnolink.tsuzuki.ui.details

import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.domain.chapter.model.Chapter

internal fun continueChapterLabel(chapter: Chapter): String = buildString {
    append("Continue")
    val title = if (chapter.chapterNumber.isFinite() && chapter.isRecognizedNumber) {
        "Ch. ${formatChapterNumber(chapter.chapterNumber)}"
    } else {
        chapter.name.trim()
    }
    if (title.isNotEmpty()) append(" · ").append(title)
    if (chapter.lastPageRead > 0) append(" · Page ").append(chapter.lastPageRead + 1)
}
