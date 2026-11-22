package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterRowTitleTest {

    private val strings = ChapterRowStrings(
        pageProgressPattern = "p. %1\$d",
        chapterNumberPattern = "Chapter %1\$s",
        chapterShortPattern = "Ch. %1\$s",
        unavailable = "Unavailable",
        markAsRead = "Mark as read",
        markAsUnread = "Mark as unread",
        bookmark = "Bookmark",
        unbookmark = "Unbookmark",
        cancelDownload = "Cancel download",
        deleteDownload = "Delete download",
        download = "Download",
    )

    @Test
    fun `display by number mode formats the chapter number`() {
        assertEquals("Chapter 2.2", chapterRowTitle("Ch.2.2 - The Letter", 2.2, displayByNumber = true, strings))
        assertEquals("Chapter 12", chapterRowTitle("Anything", 12.0, displayByNumber = true, strings))
    }

    @Test
    fun `raw chapter prefix is normalised when display by number is off`() {
        assertEquals("Ch. 2.2 - The Letter", chapterRowTitle("Ch.2.2 - The Letter", 2.2, displayByNumber = false, strings))
        assertEquals("Ch. 12", chapterRowTitle("Chapter 12", 12.0, displayByNumber = false, strings))
        assertEquals("Ch. 5", chapterRowTitle("ch 5", 5.0, displayByNumber = false, strings))
        assertEquals("Ch. 10.5: Title", chapterRowTitle("CH.10.5: Title", 10.5, displayByNumber = false, strings))
    }

    @Test
    fun `names without a chapter prefix are unchanged`() {
        assertEquals("Oneshot", chapterRowTitle("Oneshot", -1.0, displayByNumber = false, strings))
        assertEquals("Vol.3 Extra", chapterRowTitle("Vol.3 Extra", -1.0, displayByNumber = false, strings))
        assertEquals("12 - Title", chapterRowTitle("12 - Title", 12.0, displayByNumber = false, strings))
    }

    @Test
    fun `translated patterns flow into the title`() {
        val translated = strings.copy(chapterNumberPattern = "Kapitel %1\$s", chapterShortPattern = "Kap. %1\$s")
        assertEquals("Kapitel 12", chapterRowTitle("Anything", 12.0, displayByNumber = true, translated))
        assertEquals("Kap. 12", chapterRowTitle("Chapter 12", 12.0, displayByNumber = false, translated))
    }
}
