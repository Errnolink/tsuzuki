// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/ui/reader/model/ReaderChapterTest.kt (Neko #3567)
package dev.errnolink.tsuzuki.reader.engine.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ReaderChapterTest {

    private fun readerChapter() =
        ReaderChapter(
            Chapter.create().copy(
                url = "/chapter/1",
                name = "Chapter 1",
            )
        )

    @Test
    fun `unref with no references left keeps the count at zero`() {
        // Given a chapter holding one reference
        val chapter = readerChapter().apply { ref() }

        // When it is released once and then once more
        chapter.unref()
        chapter.unref()

        // Then the count stays at zero
        assertEquals(0, chapter.references)
    }

    @Test
    fun `a chapter released once too often still recycles on its next release`() {
        // Given a chapter released once more than it was referenced
        val chapter = readerChapter().apply { ref() }
        chapter.unref()
        chapter.unref()

        // When it is loaded again and released
        chapter.ref()
        chapter.state = ReaderChapter.State.Loading
        chapter.unref()

        // Then that release recycles it
        assertEquals(ReaderChapter.State.Wait, chapter.state)
    }
}
