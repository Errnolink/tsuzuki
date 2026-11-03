package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * SyncYomi v2 renders proto3 payloads: zero/false/empty scalars are omitted and a
 * zero-length body is a valid empty backup. Decoding must survive all of them.
 */
class BackupProtoDefaultsTest {

    private val proto = ProtoBuf

    @Test
    fun `decodes empty response body as empty backup`() {
        val backup = assertDoesNotThrow {
            proto.decodeFromByteArray(Backup.serializer(), byteArrayOf())
        }
        backup.backupManga.shouldBeEmpty()
        backup.backupCategories.shouldBeEmpty()
    }

    @Test
    fun `decodes categories-only response`() {
        val wire = proto.encodeToByteArray(
            Backup.serializer(),
            Backup(backupCategories = listOf(BackupCategory(name = "Reading"))),
        )
        val backup = assertDoesNotThrow {
            proto.decodeFromByteArray(Backup.serializer(), wire)
        }
        backup.backupManga.shouldBeEmpty()
        backup.backupCategories shouldHaveSize 1
        backup.backupCategories.first().name shouldBe "Reading"
        backup.backupCategories.first().uid shouldBe 0
        backup.backupCategories.first().version shouldBe 0
        backup.backupCategories.first().lastModifiedAt shouldBe 0
    }

    @Test
    fun `decodes merged reference with false and zero fields`() {
        val manga = BackupManga(
            source = 6969,
            url = "merged:/parent",
            title = "Parent",
            mergedMangaReferences = listOf(
                BackupMergedMangaReference(mergeUrl = "merged:/parent", mangaUrl = "/child", mangaSourceId = 2499283573021220255),
            ),
        )
        val wire = proto.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(manga)))

        val backup = assertDoesNotThrow {
            proto.decodeFromByteArray(Backup.serializer(), wire)
        }
        val refs = backup.backupManga.single().mergedMangaReferences
        refs shouldHaveSize 1
        with(refs.first()) {
            isInfoManga shouldBe false
            getChapterUpdates shouldBe false
            chapterSortMode shouldBe 0
            chapterPriority shouldBe 0
            downloadChapters shouldBe false
            mergeUrl shouldBe "merged:/parent"
            mangaUrl shouldBe "/child"
            mangaSourceId shouldBe 2499283573021220255
        }
    }

    @Test
    fun `decodes chapter with zero lastPageRead and version`() {
        val manga = BackupManga(
            source = 2499283573021220255,
            url = "/manga",
            chapters = listOf(BackupChapter(url = "/chapter", name = "Ch. 1")),
        )
        val wire = proto.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(manga)))

        val chapter = assertDoesNotThrow {
            proto.decodeFromByteArray(Backup.serializer(), wire)
        }.backupManga.single().chapters.single()
        chapter.lastPageRead shouldBe 0
        chapter.read shouldBe false
        chapter.version shouldBe 0
        chapter.lastModifiedAt shouldBe 0
    }

    @Test
    fun `round-trips category sync identity fields 601 to 603`() {
        val category = BackupCategory(name = "Reading", order = 3, uid = 42, version = 7, lastModifiedAt = 1700000000)
        val wire = proto.encodeToByteArray(
            Backup.serializer(),
            Backup(backupCategories = listOf(category)),
        )
        val decoded = proto.decodeFromByteArray(Backup.serializer(), wire).backupCategories.single()
        decoded.uid shouldBe 42
        decoded.version shouldBe 7
        decoded.lastModifiedAt shouldBe 1700000000
    }
}
