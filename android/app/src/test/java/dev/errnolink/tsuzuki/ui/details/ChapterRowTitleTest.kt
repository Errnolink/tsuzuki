package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterRowTitleTest {

    @Test
    fun `display by number mode formats the chapter number`() {
        assertEquals("Chapter 2.2", chapterRowTitle("Ch.2.2 - The Letter", 2.2, displayByNumber = true))
        assertEquals("Chapter 12", chapterRowTitle("Anything", 12.0, displayByNumber = true))
    }

    @Test
    fun `raw chapter prefix is normalised when display by number is off`() {
        assertEquals("Ch. 2.2 - The Letter", chapterRowTitle("Ch.2.2 - The Letter", 2.2, displayByNumber = false))
        assertEquals("Ch. 12", chapterRowTitle("Chapter 12", 12.0, displayByNumber = false))
        assertEquals("Ch. 5", chapterRowTitle("ch 5", 5.0, displayByNumber = false))
        assertEquals("Ch. 10.5: Title", chapterRowTitle("CH.10.5: Title", 10.5, displayByNumber = false))
    }

    @Test
    fun `names without a chapter prefix are unchanged`() {
        assertEquals("Oneshot", chapterRowTitle("Oneshot", -1.0, displayByNumber = false))
        assertEquals("Vol.3 Extra", chapterRowTitle("Vol.3 Extra", -1.0, displayByNumber = false))
        assertEquals("12 - Title", chapterRowTitle("12 - Title", 12.0, displayByNumber = false))
    }
}
