package tachiyomi.domain.chapter.service

import exh.source.MERGED_SOURCE_ID
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class MergedChapterSortTest {

    private val weebCentral = (1..85).map { n -> chapter(mangaId = 1, number = n.toDouble(), sourceOrder = 85L - n) }
    private val mangaDex = listOf(
        chapter(mangaId = 2, number = 2.0, sourceOrder = 0),
        chapter(mangaId = 2, number = 1.0, sourceOrder = 1),
    )

    @Test
    fun `merged title lists chapters from every source newest first by default`() {
        val merged = Manga.create().copy(source = MERGED_SOURCE_ID)

        val order = (weebCentral + mangaDex).sortedWith(getChapterSort(merged)).map { it.chapterNumber }

        order.take(3) shouldBe listOf(85.0, 84.0, 83.0)
        order.takeLast(4) shouldBe listOf(2.0, 2.0, 1.0, 1.0)
    }

    @Test
    fun `reader order of a merged title goes up one chapter at a time across sources`() {
        val merged = Manga.create().copy(source = MERGED_SOURCE_ID)

        val order = (mangaDex + weebCentral).sortedWith(getChapterSort(merged, sortDescending = false))
            .map { it.chapterNumber }
            .distinct()

        order shouldBe (1..85).map { it.toDouble() }
    }

    @Test
    fun `single source title still follows the source order`() {
        val single = Manga.create().copy(source = 1L)
        val chapters = listOf(
            chapter(mangaId = 1, number = 10.0, sourceOrder = 1),
            chapter(mangaId = 1, number = 3.0, sourceOrder = 0),
        )

        chapters.sortedWith(getChapterSort(single)).map { it.chapterNumber } shouldBe listOf(3.0, 10.0)
    }

    private fun chapter(mangaId: Long, number: Double, sourceOrder: Long) = Chapter.create().copy(
        mangaId = mangaId,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        dateUpload = number.toLong() * 1000L,
    )
}
