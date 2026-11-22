package dev.errnolink.tsuzuki.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class VolumeAwareChapterRecognitionTest {

    @Test
    fun `falls back to volume number only when no chapter number is present`() {
        assertEquals(3.0, VolumeAwareChapterRecognition.parseVolumeFallback("Monster", "Monster Vol. 3"))
        assertEquals(12.0, VolumeAwareChapterRecognition.parseVolumeFallback("Akira", "Volume 12"))
        assertNull(VolumeAwareChapterRecognition.parseVolumeFallback("Alice", "Alice Vol. 1 Ch. 4"))
    }

    @Test
    fun `fills unrecognized chapter numbers flanked by sequential neighbours`() {
        val chapters = listOf(
            Chapter.create().copy(url = "c6", chapterNumber = 6.0),
            Chapter.create().copy(url = "interlude", chapterNumber = -1.0),
            Chapter.create().copy(url = "c4", chapterNumber = 4.0),
            Chapter.create().copy(url = "special", chapterNumber = -1.0),
            Chapter.create().copy(url = "c1", chapterNumber = 1.0),
        )
        val resolved = VolumeAwareChapterRecognition.resolveWithNeighbours(chapters)
        assertEquals(listOf(6.0, 5.0, 4.0, -1.0, 1.0), resolved.map { it.chapterNumber })
    }
}
