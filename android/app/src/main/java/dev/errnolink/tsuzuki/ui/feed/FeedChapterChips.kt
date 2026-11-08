package dev.errnolink.tsuzuki.ui.feed

internal data class FeedChapterChip(
    val label: String,
    val chapter: FeedChapterItem?,
    val unread: Boolean,
)

private const val MaxChapterChips = 3

internal fun chapterChipLabel(chapter: FeedChapterItem, withPrefix: Boolean): String =
    if (chapter.chapterNumber >= 0) {
        (if (withPrefix) "Ch. " else "") + eu.kanade.presentation.util.formatChapterNumber(chapter.chapterNumber)
    } else {
        chapter.chapterName
    }

internal fun feedChapterChips(chapters: List<FeedChapterItem>): List<FeedChapterChip> {
    val unread = chapters.filter { !it.isRead }
    if (unread.isEmpty()) {
        val newest = chapters.firstOrNull() ?: return emptyList()
        return listOf(FeedChapterChip(chapterChipLabel(newest, withPrefix = true) + " · read", newest, false))
    }
    val shown = unread.take(MaxChapterChips).mapIndexed { index, chapter ->
        FeedChapterChip(chapterChipLabel(chapter, withPrefix = index == 0), chapter, true)
    }
    return if (unread.size > MaxChapterChips) {
        shown + FeedChapterChip("+${unread.size - MaxChapterChips}", null, false)
    } else {
        shown
    }
}
