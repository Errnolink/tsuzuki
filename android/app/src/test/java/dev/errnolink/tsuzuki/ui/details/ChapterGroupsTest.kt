package dev.errnolink.tsuzuki.ui.details

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.ChapterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterGroupsTest {

    private fun item(
        id: Long,
        number: Double,
        scanlator: String?,
        uploaded: Long = id,
        read: Boolean = false,
        lastPageRead: Long = 0,
    ) = ChapterList.Item(
        chapter = Chapter.create().copy(
            id = id,
            chapterNumber = number,
            scanlator = scanlator,
            dateUpload = uploaded,
            read = read,
            lastPageRead = lastPageRead,
        ),
        downloadState = Download.State.NOT_DOWNLOADED,
        downloadProgress = 0,
        sourceName = null,
        showScanlator = true,
    )

    private fun List<ChapterRowEntry>.groups() = filterIsInstance<ChapterRowEntry.Group>()

    @Test
    fun `same chapter from two groups becomes one row with the other group behind it`() {
        val rows = groupChapterRows(
            listOf(item(1, 46.0, "Setsu Scans", uploaded = 10), item(2, 46.0, "Luminare", uploaded = 20), item(3, 45.0, "Setsu Scans")),
        )

        val groups = rows.groups()
        assertEquals(2, groups.size)
        assertEquals(1L, groups[0].primary.id)
        assertEquals(listOf(2L), groups[0].alternates.map { it.id })
        assertTrue(groups[1].alternates.isEmpty())
    }

    @Test
    fun `without history every row shows the same group, the one with the most chapters`() {
        val rows = groupChapterRows(
            listOf(
                item(1, 46.0, "Luminare", uploaded = 50),
                item(2, 46.0, "Setsu Scans", uploaded = 10),
                item(3, 45.0, "Setsu Scans", uploaded = 5),
                item(4, 45.0, "Luminare", uploaded = 40),
                item(5, 44.0, "Setsu Scans", uploaded = 1),
            ),
        )

        assertEquals(listOf("Setsu Scans", "Setsu Scans", "Setsu Scans"), rows.groups().map { it.primary.chapter.scanlator })
    }

    @Test
    fun `a group the reader picked wins over history and coverage`() {
        val items = listOf(
            item(1, 46.0, "Setsu Scans", uploaded = 10),
            item(2, 46.0, "Luminare", uploaded = 20),
            item(3, 45.0, "Setsu Scans", read = true),
            item(4, 45.0, "Luminare", uploaded = 1),
        )

        val rows = groupChapterRows(items, chosen = "luminare")

        assertEquals(listOf(2L, 4L), rows.groups().map { it.primary.id })
    }

    @Test
    fun `groups are listed by how many chapters they released`() {
        val groups = scanlatorGroups(listOf(item(1, 2.0, "A"), item(2, 2.0, "B"), item(3, 1.0, "B"), item(4, 3.0, " ")))

        assertEquals(listOf(ScanlatorGroup("B", 2), ScanlatorGroup("A", 1)), groups)
    }

    @Test
    fun `the group the reader has been following wins over a newer upload`() {
        val rows = groupChapterRows(
            listOf(
                item(1, 46.0, "Setsu Scans", uploaded = 10),
                item(2, 46.0, "Luminare", uploaded = 20),
                item(3, 45.0, "Setsu Scans", read = true),
                item(4, 45.0, "Luminare", uploaded = 1),
            ),
        )

        assertEquals(1L, rows.groups().first { it.primary.chapter.chapterNumber == 46.0 }.primary.id)
    }

    @Test
    fun `a chapter already in progress stays the row you resume`() {
        val rows = groupChapterRows(
            listOf(item(1, 46.0, "Setsu Scans", uploaded = 10, lastPageRead = 5), item(2, 46.0, "Luminare", uploaded = 20)),
        )

        assertEquals(1L, rows.groups().single().primary.id)
    }

    @Test
    fun `same number from the same group is never merged`() {
        val rows = groupChapterRows(listOf(item(1, 1.0, "Setsu Scans"), item(2, 1.0, "Setsu Scans")))

        assertEquals(2, rows.groups().size)
        assertTrue(rows.groups().all { it.alternates.isEmpty() })
    }

    @Test
    fun `unnumbered chapters and chapters without a group stay separate`() {
        val rows = groupChapterRows(
            listOf(item(1, -1.0, "A"), item(2, -1.0, "B"), item(3, 7.0, null), item(4, 7.0, "B")),
        )

        assertEquals(4, rows.groups().size)
    }

    @Test
    fun `expanding a group lists its other groups right under it`() {
        val rows = groupChapterRows(listOf(item(1, 46.0, "A", uploaded = 10), item(2, 46.0, "B", uploaded = 20), item(3, 45.0, "A")))
        val key = rows.groups().first().key

        val shown = rows.expanded(setOf(key))

        assertEquals(listOf("chapter-1", "chapter-2", "chapter-3"), shown.map { it.key })
        assertTrue(shown[1] is ChapterRowEntry.Alternate)
    }

    @Test
    fun `missing chapter gaps keep their place`() {
        val gap = ChapterList.MissingCount("g", 3)
        val rows = groupChapterRows(listOf(item(1, 50.0, "A"), gap, item(2, 46.0, "A")))

        assertEquals(listOf("chapter-1", "gap-g", "chapter-2"), rows.map { it.key })
    }
}
