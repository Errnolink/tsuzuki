package dev.errnolink.tsuzuki.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UnchangedRemoteMangaTest {

    private fun chapter(url: String, read: Boolean = false) = BackupChapter(
        url = url,
        name = url,
        read = read,
        version = 3,
        memo = """{"k":"v"}""".toByteArray(),
    )

    private fun manga(url: String, chapters: List<BackupChapter>, lastModifiedAt: Long = 100) = BackupManga(
        source = 1,
        url = url,
        title = url,
        chapters = chapters,
        categories = listOf(2),
        lastModifiedAt = lastModifiedAt,
        version = 7,
        memo = """{"m":1}""".toByteArray(),
    )

    private val categories = listOf(BackupCategory(name = "Reading", order = 2))

    private fun local() = Backup(
        backupManga = listOf(
            manga("/same", listOf(chapter("/1"), chapter("/2"))),
            manga("/read", listOf(chapter("/1"), chapter("/2"))),
        ),
        backupCategories = categories,
    )

    @Test
    fun `restores only manga the other side changed or the phone never sent`() {
        val remote = Backup(
            backupManga = listOf(
                manga("/same", listOf(chapter("/2"), chapter("/1")), lastModifiedAt = 50),
                manga("/read", listOf(chapter("/1", read = true), chapter("/2"))),
                manga("/new", listOf(chapter("/1"))),
            ),
            backupCategories = listOf(BackupCategory(name = "Reading", order = 2)),
        )

        val applied = UnchangedRemoteManga.drop(remote, local())

        applied.backupManga.map { it.url } shouldContainExactlyInAnyOrder listOf("/read", "/new")
        applied.backupCategories.map { it.name } shouldBe listOf("Reading")
    }

    @Test
    fun `restores everything when the category orders mean different categories`() {
        val remote = Backup(
            backupManga = listOf(manga("/same", listOf(chapter("/1"), chapter("/2")))),
            backupCategories = listOf(BackupCategory(name = "Plan to read", order = 2)),
        )

        UnchangedRemoteManga.drop(remote, local()).backupManga.map { it.url } shouldBe listOf("/same")
    }
}
