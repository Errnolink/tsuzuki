package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

internal class BackupRequestBody(
    private val backup: Backup,
    private val protoBuf: ProtoBuf,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()

    override fun contentLength() = -1L

    override fun writeTo(sink: BufferedSink) {
        GzipSink(sink).buffer().use { gzip ->
            for (manga in backup.backupManga) {
                gzip.write(protoBuf.encodeToByteArray(MangaChunk.serializer(), MangaChunk(manga)))
            }
            gzip.write(protoBuf.encodeToByteArray(Backup.serializer(), backup.copy(backupManga = emptyList())))
        }
    }

    @Serializable
    private class MangaChunk(@ProtoNumber(1) val manga: BackupManga)
}
