package dev.errnolink.tsuzuki.ui.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedChapterChipsTest {

    private fun chapter(
        id: String,
        number: Double,
        read: Boolean = false,
        name: String = if (number >= 0) "Ch. ${number.toString().removeSuffix(".0")}" else "Onsen chapter",
    ) = FeedChapterItem(
        id = id,
        mangaId = 1L,
        chapterId = number.toLong(),
        mangaTitle = "Title",
        chapterName = name,
        chapterNumber = number,
        dateFetch = 1000L,
        coverUrl = null,
        isLibrary = true,
        isRead = read,
    )

    @Test
    fun testOverflowBeyondThreeUnreadChapters() {
        val chips = feedChapterChips(
            listOf(chapter("1", 189.0), chapter("2", 188.0), chapter("3", 187.0), chapter("4", 186.0)),
        )
        assertEquals(listOf("Ch. 189", "188", "187", "+1"), chips.map { it.label })
        assertTrue(chips.take(3).all { it.unread })
        assertFalse(chips.last().unread)
        assertNull(chips.last().chapter)
        assertEquals(189.0, chips[0].chapter?.chapterNumber)
    }

    @Test
    fun testFloatStoredChapterNumberShowsAsWrittenNumber() {
        val chips = feedChapterChips(listOf(chapter("1", 3.200000047683716), chapter("2", 3.0)))
        assertEquals(listOf("Ch. 3.2", "3"), chips.map { it.label })
    }

    @Test
    fun testExactlyThreeUnreadChaptersHasNoOverflow() {
        val chips = feedChapterChips(
            listOf(chapter("1", 21.0), chapter("2", 20.0), chapter("3", 19.0)),
        )
        assertEquals(3, chips.size)
        assertEquals(listOf("Ch. 21", "20", "19"), chips.map { it.label })
    }

    @Test
    fun testReadChaptersAreNotShownAndDoNotCountTowardOverflow() {
        val chips = feedChapterChips(
            listOf(
                chapter("1", 24.0),
                chapter("2", 23.0, read = true),
                chapter("3", 22.0),
                chapter("4", 21.0),
                chapter("5", 20.0),
                chapter("6", 19.0),
            ),
        )
        assertEquals(listOf("Ch. 24", "22", "21", "+2"), chips.map { it.label })
        assertTrue(chips.take(3).all { it.unread })
    }

    @Test
    fun testFullyReadGroupShowsSingleReadChip() {
        val chips = feedChapterChips(
            listOf(chapter("1", 214.0, read = true), chapter("2", 213.0, read = true)),
        )
        assertEquals(1, chips.size)
        assertFalse(chips[0].unread)
        assertTrue(chips[0].chapter!!.isRead)
        assertEquals("Ch. 214 · read", chips[0].label)
    }

    @Test
    fun testUnnumberedChapterUsesChapterNameWithoutPrefix() {
        val chips = feedChapterChips(listOf(chapter("1", -1.0)))
        assertEquals("Onsen chapter", chips[0].label)
        assertTrue(chips[0].unread)
    }

    @Test
    fun testEmptyGroupProducesNoChips() {
        assertEquals(emptyList<FeedChapterChip>(), feedChapterChips(emptyList()))
    }
}
