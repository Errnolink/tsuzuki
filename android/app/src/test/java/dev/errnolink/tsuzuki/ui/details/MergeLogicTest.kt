package dev.errnolink.tsuzuki.ui.details

import exh.source.MERGED_SOURCE_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference

class MergeLogicTest {

    private fun sampleManga(id: Long, title: String) = Manga.create().copy(
        id = id,
        ogTitle = title,
        url = "/manga/$id",
        source = 100L,
    )

    private fun sampleReference(
        id: Long,
        mangaId: Long,
        sourceId: Long,
        priority: Int,
        isInfo: Boolean = false,
    ) = MergedMangaReference(
        id = id,
        isInfoManga = isInfo,
        getChapterUpdates = true,
        chapterSortMode = 0,
        chapterPriority = priority,
        downloadChapters = true,
        mergeId = 999L,
        mergeUrl = "/merge/999",
        mangaId = mangaId,
        mangaUrl = "/manga/$mangaId",
        mangaSourceId = sourceId,
    )

    @Test
    fun `orderMergeCandidates puts exact title matches before prefix and substring matches`() {
        val query = "Chainsaw Man"
        val candidate1 = sampleManga(1L, "Chainsaw Man: Buddy Stories")
        val candidate2 = sampleManga(2L, "Chainsaw Man")
        val candidate3 = sampleManga(3L, "Before Chainsaw Man - 17-21")
        val candidate4 = sampleManga(4L, "Man of the Chainsaw")

        val ordered = orderMergeCandidates(
            listOf(candidate1, candidate2, candidate3, candidate4),
            query,
        )

        assertEquals(listOf(2L, 1L, 3L, 4L), ordered.map { it.id })
    }

    @Test
    fun `orderMergeCandidates handles case and whitespace insensitivity`() {
        val query = "  cos-chu  "
        val candidate1 = sampleManga(1L, "Cos-Chu Extra")
        val candidate2 = sampleManga(2L, "COS-CHU")

        val ordered = orderMergeCandidates(listOf(candidate1, candidate2), query)

        assertEquals(listOf(2L, 1L), ordered.map { it.id })
    }

    @Test
    fun `orderMergeCandidates preserves original list when query is empty`() {
        val candidates = listOf(
            sampleManga(1L, "Beta"),
            sampleManga(2L, "Alpha"),
        )

        val ordered = orderMergeCandidates(candidates, "   ")

        assertEquals(listOf(1L, 2L), ordered.map { it.id })
    }

    @Test
    fun `reorderMergePriorities moves secondary source up and swaps priority with predecessor`() {
        val container = sampleReference(1L, 999L, MERGED_SOURCE_ID, -1)
        val child0 = sampleReference(2L, 100L, 200L, 0, isInfo = true)
        val child1 = sampleReference(3L, 101L, 201L, 1)
        val child2 = sampleReference(4L, 102L, 202L, 2)

        val initial = listOf(container, child0, child1, child2)
        val reordered = reorderMergePriorities(initial, targetReferenceId = 4L, move = MergePriorityMove.Up)

        val childOrder = reordered.filter { it.mangaSourceId != MERGED_SOURCE_ID }
        assertEquals(listOf(2L, 4L, 3L), childOrder.map { it.id })
        assertEquals(listOf(0, 1, 2), childOrder.map { it.chapterPriority })

        val containerRef = reordered.single { it.mangaSourceId == MERGED_SOURCE_ID }
        assertEquals(-1, containerRef.chapterPriority)
    }

    @Test
    fun `reorderMergePriorities moves secondary source down and swaps priority with successor`() {
        val container = sampleReference(1L, 999L, MERGED_SOURCE_ID, -1)
        val child0 = sampleReference(2L, 100L, 200L, 0, isInfo = true)
        val child1 = sampleReference(3L, 101L, 201L, 1)
        val child2 = sampleReference(4L, 102L, 202L, 2)

        val initial = listOf(container, child0, child1, child2)
        val reordered = reorderMergePriorities(initial, targetReferenceId = 2L, move = MergePriorityMove.Down)

        val childOrder = reordered.filter { it.mangaSourceId != MERGED_SOURCE_ID }
        assertEquals(listOf(3L, 2L, 4L), childOrder.map { it.id })
        assertEquals(listOf(0, 1, 2), childOrder.map { it.chapterPriority })
    }

    @Test
    fun `reorderMergePriorities does not change order when moving top item up or bottom item down`() {
        val container = sampleReference(1L, 999L, MERGED_SOURCE_ID, -1)
        val child0 = sampleReference(2L, 100L, 200L, 0, isInfo = true)
        val child1 = sampleReference(3L, 101L, 201L, 1)

        val initial = listOf(container, child0, child1)
        val moveTopUp = reorderMergePriorities(initial, targetReferenceId = 2L, move = MergePriorityMove.Up)
        val moveBottomDown = reorderMergePriorities(initial, targetReferenceId = 3L, move = MergePriorityMove.Down)

        assertEquals(initial, moveTopUp)
        assertEquals(initial, moveBottomDown)
    }

    @Test
    fun `reorderMergePrioritiesToIndex correctly moves item and reassigns contiguous priorities`() {
        val container = sampleReference(1L, 999L, MERGED_SOURCE_ID, -1)
        val child0 = sampleReference(2L, 100L, 200L, 0, isInfo = true)
        val child1 = sampleReference(3L, 101L, 201L, 1)
        val child2 = sampleReference(4L, 102L, 202L, 2)

        val initial = listOf(container, child0, child1, child2)
        val reordered = reorderMergePrioritiesToIndex(initial, fromIndex = 2, toIndex = 0)

        val childOrder = reordered.filter { it.mangaSourceId != MERGED_SOURCE_ID }
        assertEquals(listOf(4L, 2L, 3L), childOrder.map { it.id })
        assertEquals(listOf(0, 1, 2), childOrder.map { it.chapterPriority })
    }
}
