package eu.kanade.tachiyomi.data.backup

import dev.errnolink.tsuzuki.sync.BackupWantedExtension
import dev.errnolink.tsuzuki.sync.BackupWantedExtensions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupStreamTest {

    private val parser: ProtoBuf = ProtoBuf

    private fun fixture(): Backup = Backup(
        backupManga = listOf(
            BackupManga(
                source = 2499283573021220255L,
                url = "/manga/01234567-89ab-cdef-0123-456789abcdef",
                title = "Streamed One",
                favorite = true,
                dateAdded = 1700000000,
                version = 7,
                lastModifiedAt = 99,
                chapters = listOf(
                    BackupChapter(url = "/chapter/a", name = "Ch 1", read = true, lastPageRead = 12, version = 3),
                    BackupChapter(url = "/chapter/b", name = "Ch 2", read = false),
                ),
                categories = listOf(2L),
                history = listOf(BackupHistory("/chapter/a", 123456L, 60)),
            ),
            BackupManga(
                source = 1L,
                url = "/manga/ffffffff-89ab-cdef-0123-456789abcdef",
                title = "Streamed Two",
                favorite = false,
                dateAdded = 777,
            ),
        ),
        backupCategories = listOf(BackupCategory(name = "Reading", order = 0, id = 2)),
        backupSources = listOf(BackupSource(name = "MangaDex", sourceId = 2499283573021220255L)),
        wantedExtensions = BackupWantedExtensions(
            wanted = listOf(
                BackupWantedExtension(pkgName = "eu.kanade.tachiyomi.extension.all.mangadex", versionCode = 100),
            ),
        ),
    )

    private fun streamedBytes(backup: Backup): ByteArray = Buffer().let { buffer ->
        val writer = BackupWriter(buffer, parser)
        backup.backupManga.forEach(writer::writeEntry)
        writer.writeMetadata(backup.copy(backupManga = emptyList()))
        buffer.readByteArray()
    }

    @Test
    fun `streamed bytes are identical to the whole message encoding`() {
        val backup = fixture()
        val wholeMessage = parser.encodeToByteArray(Backup.serializer(), backup)
        val streamed = streamedBytes(backup)
        assertArrayEquals(wholeMessage, streamed)
    }

    @Test
    fun `streamed bytes without entries are identical to the whole message encoding`() {
        val backup = fixture().copy(backupManga = emptyList())
        val wholeMessage = parser.encodeToByteArray(Backup.serializer(), backup)
        assertArrayEquals(wholeMessage, streamedBytes(backup))
    }

    @Test
    fun `the old whole message decoder reads a streamed backup identically`() {
        val backup = fixture()
        val wholeMessage = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), streamedBytes(backup))
        assertArrayEquals(wholeMessage, parser.encodeToByteArray(Backup.serializer(), decoded))
    }

    @Test
    fun `the streaming reader restores the same rows and metadata`() {
        val backup = fixture()
        val reader = BackupReader(Buffer().write(streamedBytes(backup)), parser)

        val entries = mutableListOf<BackupManga>()
        while (reader.nextEntry()) {
            entries.add(reader.readEntry())
        }
        val metadata = reader.readMetadata()

        assertEquals(2, entries.size)
        assertEquals(listOf("Streamed One", "Streamed Two"), entries.map { it.title })
        assertEquals(2, entries.first().chapters.size)
        assertEquals(listOf("/chapter/a", "/chapter/b"), entries.first().chapters.map { it.url })
        assertEquals(true, entries.first().chapters.first().read)
        assertEquals(12L, entries.first().chapters.first().lastPageRead)
        assertEquals(7L, entries.first().version)
        assertEquals(listOf(2L), entries.first().categories)
        assertEquals("Reading", metadata.backupCategories.single().name)
        assertEquals("MangaDex", metadata.backupSources.single().name)
        assertEquals(
            "eu.kanade.tachiyomi.extension.all.mangadex",
            metadata.wantedExtensions?.wanted?.single()?.pkgName,
        )
        assertEquals(emptyList<BackupManga>(), metadata.backupManga)
    }

    @Test
    fun `entries and metadata may be interleaved and the reader still separates them`() {
        val backup = fixture()
        val buffer = Buffer()
        val writer = BackupWriter(buffer, parser)
        writer.writeEntry(backup.backupManga.first())
        val categories = parser.encodeToByteArray(Backup.serializer(), Backup(backupCategories = backup.backupCategories))
        buffer.write(categories)
        writer.writeEntry(backup.backupManga.last())
        writer.writeMetadata(Backup(backupSources = backup.backupSources))

        val reader = BackupReader(buffer, parser)
        val entries = mutableListOf<BackupManga>()
        while (reader.nextEntry()) {
            entries.add(reader.readEntry())
        }
        val metadata = reader.readMetadata()

        assertEquals(listOf("Streamed One", "Streamed Two"), entries.map { it.title })
        assertEquals("Reading", metadata.backupCategories.single().name)
        assertEquals("MangaDex", metadata.backupSources.single().name)
    }
}
