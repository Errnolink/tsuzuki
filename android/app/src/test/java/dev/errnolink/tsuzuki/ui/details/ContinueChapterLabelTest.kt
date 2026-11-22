package dev.errnolink.tsuzuki.ui.details

import dev.errnolink.tsuzuki.ui.motion.HeroTapSeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ContinueChapterLabelTest {

    private val words = ContinueWords(
        continueWord = "Continue",
        chapterPattern = "Ch. %1\$s",
        pagePattern = "Page %1\$d",
    )

    @Test
    fun `unknown numbers use chapter name and retain page`() {
        val chapter = Chapter.create().copy(name = "Prologue", lastPageRead = 4)
        assertEquals("Continue · Prologue · Page 5", continueChapterLabel(chapter, words))
    }

    @Test
    fun `unnamed unknown chapter has plain continuation`() {
        assertEquals("Continue", continueChapterLabel(Chapter.create(), words))
        assertEquals("Continue", continueChapterLabel(Chapter.create().copy(chapterNumber = Double.NaN), words))
    }

    @Test
    fun `recognized chapter number is retained`() {
        assertEquals("Continue · Ch. 5", continueChapterLabel(Chapter.create().copy(chapterNumber = 5.0), words))
    }

    @Test
    fun `the tile seed produces the same label the loaded chapter will`() {
        // T2: the seeded label shown on the first frame must equal the computed one once chapters
        // land, so the button never re-labels itself after the flight
        val chapter = Chapter.create().copy(id = 1, chapterNumber = 66.5, lastPageRead = 3)
        val seed = HeroTapSeed(continueChapterNumber = 66.5, continueLastPageRead = 3)
        assertEquals(continueChapterLabel(chapter, words), continueSeedLabel(seed, words))
    }

    @Test
    fun `seeded name is used when the number is unrecognized`() {
        val seed = HeroTapSeed(continueChapterNumber = -1.0, continueChapterName = "Volume extras", continueLastPageRead = 0)
        assertEquals("Continue · Volume extras", continueSeedLabel(seed, words))
        val byNameOnly = HeroTapSeed(continueChapterName = "Omake")
        assertEquals("Continue · Omake", continueSeedLabel(byNameOnly, words))
    }

    @Test
    fun `a seed without any chapter is null so the caller keeps the plain hold`() {
        assertNull(continueSeedLabel(HeroTapSeed(), words))
        assertNull(continueSeedLabel(HeroTapSeed(continueChapterName = "  "), words))
    }

    @Test
    fun `translated words flow into the composed label`() {
        val translated = ContinueWords(continueWord = "Fortsetzen", chapterPattern = "Kap. %1\$s", pagePattern = "Seite %1\$d")
        assertEquals(
            "Fortsetzen · Kap. 5 · Seite 3",
            continueChapterLabel(Chapter.create().copy(chapterNumber = 5.0, lastPageRead = 2), translated),
        )
    }
}
