package dev.errnolink.tsuzuki.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class WantedExtensionsProtoTest {

    private val proto = ProtoBuf

    private val exportedPkg = "eu.kanade.tachiyomi.extension.en.weebcentral"
    private val exportedRepo = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
    private val exportedFingerprint = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
    private val exportedVersionCode = 106025L
    private val exportedUpdatedAt = 1791385691338L
    private val exportedVersionName = "1.6.25"

    private fun exportedSection() = BackupWantedExtensions(
        wanted = listOf(
            BackupWantedExtension(
                pkgName = exportedPkg,
                repoIndexUrl = exportedRepo,
                signingFingerprint = exportedFingerprint,
                versionCode = exportedVersionCode,
                updatedAt = exportedUpdatedAt,
                versionName = exportedVersionName,
            ),
        ),
    )

    @Test
    fun `round trips section through backup field 700`() {
        val backup = Backup(
            backupCategories = listOf(BackupCategory(name = "Default")),
            wantedExtensions = exportedSection(),
        )

        val decoded = proto.decodeFromByteArray(
            Backup.serializer(),
            proto.encodeToByteArray(Backup.serializer(), backup),
        )

        decoded.wantedExtensions shouldBe exportedSection()
        decoded.backupCategories.first().name shouldBe "Default"
    }

    @Test
    fun `decodes proto3 omitted zero values`() {
        val section = BackupWantedExtensions(
            wanted = listOf(BackupWantedExtension(pkgName = "a", updatedAt = 42)),
            tombstones = listOf(BackupExtensionTombstone(pkgName = "b", deletedAt = 7)),
            syncUninstalls = false,
        )

        val decoded = proto.decodeFromByteArray(
            BackupWantedExtensions.serializer(),
            proto.encodeToByteArray(BackupWantedExtensions.serializer(), section),
        )

        decoded shouldBe section
        decoded.syncUninstalls shouldBe false

        val defaulted = proto.decodeFromByteArray(
            BackupWantedExtensions.serializer(),
            proto.encodeToByteArray(
                BackupWantedExtensions.serializer(),
                BackupWantedExtensions(wanted = section.wanted),
            ),
        )
        defaulted.syncUninstalls shouldBe true
        defaulted.wanted.single().versionCode shouldBe 0L
        defaulted.wanted.single().repoIndexUrl shouldBe ""
        defaulted.tombstones.shouldBeEmpty()
    }

    @Test
    fun `absent section decodes as null and re-encodes without field 700`() {
        val decoded = proto.decodeFromByteArray(
            Backup.serializer(),
            proto.encodeToByteArray(Backup.serializer(), Backup()),
        )

        decoded.wantedExtensions shouldBe null
        val wire = proto.encodeToByteArray(Backup.serializer(), decoded)
        wire.none { it == 0xE2.toByte() } shouldBe true
    }

    @Test
    fun `decodes server exported tachibk`() {
        val bytes = locateExportedBackup()?.readBytes() ?: synthesizeExportedBackup()

        val backup = proto.decodeFromByteArray(Backup.serializer(), gunzip(bytes))

        backup.backupCategories shouldHaveSize 1
        backup.backupCategories.first().name shouldBe "Default"
        val section = backup.wantedExtensions.shouldBeInstanceOf<BackupWantedExtensions>()
        section.wanted shouldHaveSize 1
        val entry = section.wanted.single()
        entry.pkgName shouldBe exportedPkg
        entry.repoIndexUrl shouldBe exportedRepo
        entry.signingFingerprint shouldBe exportedFingerprint
        entry.versionCode shouldBe exportedVersionCode
        entry.updatedAt shouldBe exportedUpdatedAt
        entry.versionName shouldBe exportedVersionName
        section.tombstones.shouldBeEmpty()
        section.syncUninstalls shouldBe true
    }

    private fun synthesizeSection(): ByteArray =
        stringField(1, exportedPkg) +
            stringField(2, exportedRepo) +
            stringField(3, exportedFingerprint) +
            int64Field(4, exportedVersionCode) +
            int64Field(5, exportedUpdatedAt) +
            stringField(6, exportedVersionName)

    @Test
    fun `server exported tachibk round trips through our encoder`() {
        val bytes = locateExportedBackup()?.readBytes() ?: synthesizeExportedBackup()
        val decoded = proto.decodeFromByteArray(Backup.serializer(), gunzip(bytes))

        val reencoded = proto.encodeToByteArray(Backup.serializer(), decoded)
        val reread = proto.decodeFromByteArray(Backup.serializer(), reencoded)

        reread.wantedExtensions shouldBe decoded.wantedExtensions
        reread.backupCategories.first().name shouldBe decoded.backupCategories.first().name
    }

    private fun locateExportedBackup(): File? =
        System.getenv("TSUZUKI_EXT_SYNC_BACKUP")?.let(::File)?.takeIf { it.isFile }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun synthesizeExportedBackup(): ByteArray {
        val category = stringField(1, "Default")
        val backup = lengthDelimited(2, category) + lengthDelimited(700, lengthDelimited(1, synthesizeSection()))
        return ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(backup) }
            out.toByteArray()
        }
    }

    private fun varint(value: Long): ByteArray {
        var v = value
        val out = mutableListOf<Byte>()
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) {
                out.add((b or 0x80).toByte())
            } else {
                out.add(b.toByte())
                return out.toByteArray()
            }
        }
    }

    private fun tag(field: Int, wireType: Int): ByteArray = varint(((field.toLong() shl 3) or wireType.toLong()))

    private fun lengthDelimited(field: Int, payload: ByteArray): ByteArray =
        tag(field, 2) + varint(payload.size.toLong()) + payload

    private fun stringField(field: Int, value: String): ByteArray =
        lengthDelimited(field, value.toByteArray(Charsets.UTF_8))

    private fun int64Field(field: Int, value: Long): ByteArray =
        tag(field, 0) + varint(value)
}
