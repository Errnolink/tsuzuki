package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MergedMangaReference

class MergedReferenceReconciliationTest {

    private fun localRef(
        id: Long,
        source: Long,
        url: String,
        priority: Int = 0,
        info: Boolean = false,
        updates: Boolean = true,
    ) = MergedMangaReference(
        id = id,
        isInfoManga = info,
        getChapterUpdates = updates,
        chapterSortMode = 0,
        chapterPriority = priority,
        downloadChapters = false,
        mergeId = 1,
        mergeUrl = "merged:/parent",
        mangaId = 100 + id,
        mangaUrl = url,
        mangaSourceId = source,
    )

    private fun remoteRef(
        source: Long,
        url: String,
        priority: Int = 0,
        info: Boolean = false,
        updates: Boolean = true,
        mergeUrl: String = "merged:/parent",
    ) = BackupMergedMangaReference(
        isInfoManga = info,
        getChapterUpdates = updates,
        chapterSortMode = 0,
        chapterPriority = priority,
        downloadChapters = false,
        mergeUrl = mergeUrl,
        mangaUrl = url,
        mangaSourceId = source,
    )

    @Test
    fun `removes local references absent from the winning snapshot`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a"), localRef(2, 10, "/b")),
            incoming = listOf(remoteRef(10, "/a")),
        )
        result.toDelete shouldContainExactly listOf(2)
        result.toUpdate.shouldBeEmpty()
        result.toInsert.shouldBeEmpty()
    }

    @Test
    fun `updates changed settings in place`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a", priority = 0, info = false)),
            incoming = listOf(remoteRef(10, "/a", priority = 5, info = true)),
        )
        result.toDelete.shouldBeEmpty()
        result.toInsert.shouldBeEmpty()
        result.toUpdate shouldContainExactly listOf(1L to remoteRef(10, "/a", priority = 5, info = true))
    }

    @Test
    fun `keeps unchanged references untouched`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a", priority = 3, info = true, updates = false)),
            incoming = listOf(remoteRef(10, "/a", priority = 3, info = true, updates = false)),
        )
        result.toDelete.shouldBeEmpty()
        result.toUpdate.shouldBeEmpty()
        result.toInsert.shouldBeEmpty()
    }

    @Test
    fun `inserts new references without requiring the child row`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a")),
            incoming = listOf(remoteRef(10, "/a"), remoteRef(20, "/b")),
        )
        result.toInsert shouldContainExactly listOf(remoteRef(20, "/b"))
        result.toDelete.shouldBeEmpty()
        result.toUpdate.shouldBeEmpty()
    }

    @Test
    fun `same child URL on a different source is a distinct reference`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a"), localRef(2, 20, "/a")),
            incoming = listOf(remoteRef(20, "/a")),
        )
        result.toDelete shouldContainExactly listOf(1)
        result.toInsert.shouldBeEmpty()
        result.toUpdate.shouldBeEmpty()
    }

    @Test
    fun `empty incoming snapshot dissolves the aggregate`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a"), localRef(2, 20, "/b")),
            incoming = emptyList(),
        )
        result.toDelete shouldContainExactly listOf(1, 2)
        result.toInsert.shouldBeEmpty()
        result.toUpdate.shouldBeEmpty()
    }

    @Test
    fun `legacy source ids migrate before matching`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 7309872737163460316L, "/a")),
            incoming = listOf(remoteRef(6907L, "/a")),
        )
        result.toDelete.shouldBeEmpty()
        result.toInsert.shouldBeEmpty()
        result.toUpdate.shouldBeEmpty()
    }

    @Test
    fun `merge url change rewrites the reference`() {
        val result = reconcileMergedReferences(
            existing = listOf(localRef(1, 10, "/a")),
            incoming = listOf(remoteRef(10, "/a", mergeUrl = "merged:/renamed")),
        )
        result.toDelete shouldContainExactly listOf(1)
        result.toInsert shouldContainExactly listOf(remoteRef(10, "/a", mergeUrl = "merged:/renamed"))
        result.toUpdate.shouldBeEmpty()
    }
}
