package dev.errnolink.tsuzuki.ui.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedCardLogicTest {

    private fun chapter(
        id: String,
        mangaId: Long,
        chapterId: Long?,
        number: Double,
        isLibrary: Boolean = true,
        isRead: Boolean = false,
        name: String = "Ch. ${number.toString().removeSuffix(".0")}",
        scanlator: String? = null,
    ) = FeedChapterItem(
        id = id,
        mangaId = mangaId,
        chapterId = chapterId,
        mangaTitle = "Title $mangaId",
        chapterName = name,
        chapterNumber = number,
        dateFetch = 1000L,
        coverUrl = null,
        isLibrary = isLibrary,
        isRead = isRead,
        scanlator = scanlator,
        mangaDexId = if (mangaId <= 0L) "md-$id" else null,
    )

    private fun group(
        mangaId: Long,
        chapters: List<FeedChapterItem>,
        isLibrary: Boolean = mangaId > 0L,
    ) = FeedTitleGroup(
        mangaId = mangaId,
        mangaTitle = "Title $mangaId",
        coverUrl = null,
        chapters = chapters,
        isLibrary = isLibrary,
        mangaDexId = if (mangaId <= 0L) "md-series" else null,
    )

    @Test
    fun `expand state toggles multi-chapter titles independently and ignores single-chapter cards`() {
        val initial = FeedExpandState()

        val afterSingle = initial.toggle("id:1", chapterCount = 1)
        assertFalse(afterSingle.isExpanded("id:1", chapterCount = 1))

        val openedFirst = afterSingle.toggle("id:2", chapterCount = 3)
        assertTrue(openedFirst.isExpanded("id:2", chapterCount = 3))
        assertFalse(openedFirst.isExpanded("id:3", chapterCount = 2))

        val openedBoth = openedFirst.toggle("id:3", chapterCount = 2)
        assertTrue(openedBoth.isExpanded("id:2", chapterCount = 3))
        assertTrue(openedBoth.isExpanded("id:3", chapterCount = 2))

        val closedFirst = openedBoth.toggle("id:2", chapterCount = 3)
        assertFalse(closedFirst.isExpanded("id:2", chapterCount = 3))
        assertTrue(closedFirst.isExpanded("id:3", chapterCount = 2))

        val pruned = closedFirst.prune(setOf("id:99"))
        assertFalse(pruned.isExpanded("id:3", chapterCount = 2))

        val dayScoped = FeedExpandState()
            .toggle("2026-11-20-id:2", chapterCount = 2)
        assertTrue(dayScoped.isExpanded("2026-11-20-id:2", chapterCount = 2))
        assertFalse(dayScoped.isExpanded("2026-11-19-id:2", chapterCount = 2))
        val bothDays = dayScoped.toggle("2026-11-19-id:2", chapterCount = 2)
        assertTrue(bothDays.isExpanded("2026-11-20-id:2", chapterCount = 2))
        assertTrue(bothDays.isExpanded("2026-11-19-id:2", chapterCount = 2))
        val collapsedToday = bothDays.toggle("2026-11-20-id:2", chapterCount = 2)
        assertFalse(collapsedToday.isExpanded("2026-11-20-id:2", chapterCount = 2))
        assertTrue(collapsedToday.isExpanded("2026-11-19-id:2", chapterCount = 2))
    }

    @Test
    fun `stackPeekDepth reflects collapsed chapter counts and drops to zero when expanded`() {
        assertEquals(0, stackPeekDepth(chapterCount = 1, expanded = false))
        assertEquals(1, stackPeekDepth(chapterCount = 2, expanded = false))
        assertEquals(2, stackPeekDepth(chapterCount = 3, expanded = false))
        assertEquals(2, stackPeekDepth(chapterCount = 5, expanded = false))
        assertEquals(0, stackPeekDepth(chapterCount = 3, expanded = true))
    }

    @Test
    fun `swipe actions are disabled for non-library follows and already-read groups`() {
        val followOnly = group(
            mangaId = -1L,
            chapters = listOf(chapter("md-1", mangaId = -1L, chapterId = null, number = 12.0, isLibrary = false)),
            isLibrary = false,
        )
        val followCaps = feedCardSwipeCapabilities(followOnly)
        assertFalse(followCaps.canMarkRead)
        assertFalse(followCaps.canDownload)
        assertNull(downloadTargetChapter(followOnly))

        val readLibrary = group(
            mangaId = 5L,
            chapters = listOf(
                chapter("lib-1", mangaId = 5L, chapterId = 50L, number = 10.0, isRead = true),
                chapter("lib-2", mangaId = 5L, chapterId = 51L, number = 9.0, isRead = true),
            ),
        )
        val readCaps = feedCardSwipeCapabilities(readLibrary)
        assertFalse(readCaps.canMarkRead)
        assertTrue(readCaps.canDownload)

        val unreadCh = chapter("lib-3", mangaId = 6L, chapterId = 61L, number = 22.0, isRead = false)
        val readCh = chapter("lib-4", mangaId = 6L, chapterId = 62L, number = 23.0, isRead = true)
        val mixedLibrary = group(mangaId = 6L, chapters = listOf(readCh, unreadCh))
        val mixedCaps = feedCardSwipeCapabilities(mixedLibrary)
        assertTrue(mixedCaps.canMarkRead)
        assertTrue(mixedCaps.canDownload)
        assertEquals(unreadCh, leadChapter(mixedLibrary.chapters))
        assertEquals(unreadCh, downloadTargetChapter(mixedLibrary))

        assertEquals(1, markableUnreadCount(listOf(followOnly, readLibrary, mixedLibrary)))
    }

    @Test
    fun `feedChapterRowLabel avoids duplicate chapter prefixes`() {
        val chWithSubtitle = chapter("1", 1L, 10L, 47.0, name = "Ch. 47: The Rooftop Promise")
        assertEquals("Ch. 47 · The Rooftop Promise", feedChapterRowLabel(chWithSubtitle))

        val chNumberOnly = chapter("2", 1L, 11L, 48.0, name = "Chapter 48")
        assertEquals("Ch. 48", feedChapterRowLabel(chNumberOnly))

        val unnumbered = chapter("3", 1L, 12L, -1.0, name = "Special Bonus")
        assertEquals("Special Bonus", feedChapterRowLabel(unnumbered))
    }
}
