package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ContinueChapterLabelTest {
    @Test
    fun `unknown numbers use chapter name and retain page`() {
        val chapter = Chapter.create().copy(name = "Prologue", lastPageRead = 4)
        assertEquals("Continue · Prologue · Page 5", continueChapterLabel(chapter))
    }

    @Test
    fun `unnamed unknown chapter has plain continuation`() {
        assertEquals("Continue", continueChapterLabel(Chapter.create()))
        assertEquals("Continue", continueChapterLabel(Chapter.create().copy(chapterNumber = Double.NaN)))
    }

    @Test
    fun `recognized chapter number is retained`() {
        assertEquals("Continue · Ch. 5", continueChapterLabel(Chapter.create().copy(chapterNumber = 5.0)))
    }
}
