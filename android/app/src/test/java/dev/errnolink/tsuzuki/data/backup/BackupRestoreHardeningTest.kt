package dev.errnolink.tsuzuki.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MergedMangaReference

class BackupRestoreHardeningTest {

    @Test
    fun `damaged blank title falls back to existing title or url`() {
        assertEquals("Good Title", BackupRestoreHardening.safeRestoreTitle("Good Title", "Old", "/u"))
        assertEquals("Old Title", BackupRestoreHardening.safeRestoreTitle("   ", "Old Title", "/u"))
        assertEquals("/manga/42", BackupRestoreHardening.safeRestoreTitle("", null, "/manga/42"))
    }

    @Test
    fun `non-replacing merge keeps local references and adds missing backup references`() {
        val local = listOf(
            MergedMangaReference(
                id = 10L,
                isInfoManga = true,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = false,
                mergeId = 1L,
                mergeUrl = "merged:/parent",
                mangaId = 200L,
                mangaUrl = "/local",
                mangaSourceId = 1L,
            ),
        )
        val incoming = listOf(
            BackupMergedMangaReference(
                isInfoManga = true,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = false,
                mergeUrl = "merged:/parent",
                mangaUrl = "/local",
                mangaSourceId = 1L,
            ),
            BackupMergedMangaReference(
                isInfoManga = false,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 1,
                downloadChapters = true,
                mergeUrl = "merged:/parent",
                mangaUrl = "/from-backup",
                mangaSourceId = 2L,
            ),
        )

        val patch = BackupRestoreHardening.reconcileReferences(local, incoming, replace = false)
        assertEquals(emptyList<Long>(), patch.toDelete)
        assertEquals(emptyList<Any>(), patch.toUpdate)
        assertEquals(listOf("/from-backup"), patch.toInsert.map { it.mangaUrl })
    }
}
