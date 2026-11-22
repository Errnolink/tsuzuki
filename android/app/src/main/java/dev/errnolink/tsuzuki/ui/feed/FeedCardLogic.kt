package dev.errnolink.tsuzuki.ui.feed

internal enum class FeedHostTab {
    FEED,
    HISTORY,
}

internal data class FeedSwipeCapabilities(
    val canMarkRead: Boolean,
    val canDownload: Boolean,
)

internal data class FeedExpandState(
    val expandedKeys: Set<String> = emptySet(),
) {
    fun toggle(cardKey: String, chapterCount: Int): FeedExpandState {
        if (chapterCount <= 1) return this
        val updated = if (cardKey in expandedKeys) expandedKeys - cardKey else expandedKeys + cardKey
        return copy(expandedKeys = updated)
    }

    fun isExpanded(cardKey: String, chapterCount: Int): Boolean =
        chapterCount > 1 && cardKey in expandedKeys

    fun prune(validExpandableKeys: Set<String>): FeedExpandState {
        val retained = expandedKeys.intersect(validExpandableKeys)
        return if (retained == expandedKeys) this else copy(expandedKeys = retained)
    }
}

internal fun stackPeekDepth(chapterCount: Int, expanded: Boolean): Int = when {
    expanded || chapterCount <= 1 -> 0
    chapterCount == 2 -> 1
    else -> 2
}

internal fun leadChapter(chapters: List<FeedChapterItem>): FeedChapterItem? =
    chapters.firstOrNull { !it.isRead } ?: chapters.firstOrNull()

internal fun canMarkChapterRead(chapter: FeedChapterItem): Boolean =
    chapter.chapterId != null && !chapter.isRead

internal fun canDownloadChapter(chapter: FeedChapterItem): Boolean =
    chapter.chapterId != null && chapter.mangaId > 0L

internal fun canMarkGroupRead(group: FeedTitleGroup): Boolean =
    group.chapters.any(::canMarkChapterRead)

internal fun canDownloadGroup(group: FeedTitleGroup): Boolean =
    group.chapters.any(::canDownloadChapter)

internal fun downloadTargetChapter(group: FeedTitleGroup): FeedChapterItem? =
    group.chapters.firstOrNull { !it.isRead && canDownloadChapter(it) }
        ?: group.chapters.firstOrNull(::canDownloadChapter)

internal fun feedCardSwipeCapabilities(group: FeedTitleGroup): FeedSwipeCapabilities =
    FeedSwipeCapabilities(
        canMarkRead = canMarkGroupRead(group),
        canDownload = canDownloadGroup(group),
    )

internal fun markableUnreadCount(groups: Collection<FeedTitleGroup>): Int =
    groups.sumOf { group -> group.chapters.count(::canMarkChapterRead) }

internal fun feedChapterRowLabel(chapter: FeedChapterItem): String {
    if (chapter.chapterNumber < 0) return chapter.chapterName
    val formatted = eu.kanade.presentation.util.formatChapterNumber(chapter.chapterNumber)
    val numLabel = chapterChipLabel(chapter, withPrefix = true)
    val prefixRegex = Regex(
        """^(?i)(?:ch(?:apter)?\.?\s*${Regex.escape(formatted)})(?:\s*[:\-·]\s*|\s+)?""",
    )
    val remainder = chapter.chapterName.replaceFirst(prefixRegex, "").trim()
    return if (remainder.isNotBlank()) "$numLabel · $remainder" else numLabel
}

internal fun feedGroupMetaSubtitle(group: FeedTitleGroup): String? =
    group.chapters.firstNotNullOfOrNull { it.scanlator?.takeIf(String::isNotBlank) }
        ?: if (group.mangaDexId != null) "MangaDex" else null
