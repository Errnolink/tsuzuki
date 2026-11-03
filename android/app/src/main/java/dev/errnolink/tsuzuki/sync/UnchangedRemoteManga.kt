package dev.errnolink.tsuzuki.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.protobuf.ProtoBuf

object UnchangedRemoteManga {

    fun drop(remote: Backup, uploaded: Backup, protoBuf: ProtoBuf = ProtoBuf): Backup {
        if (remote.backupManga.isEmpty() || uploaded.backupManga.isEmpty()) return remote
        if (categoryNames(remote.backupCategories) != categoryNames(uploaded.backupCategories)) return remote
        val sent = HashMap<Pair<Long, String>, ByteArray>(uploaded.backupManga.size * 2)
        uploaded.backupManga.forEach { sent[it.source to it.url] = fingerprint(it, protoBuf) }
        val changed = remote.backupManga.filter { manga ->
            val previous = sent[manga.source to manga.url]
            previous == null || !previous.contentEquals(fingerprint(manga, protoBuf))
        }
        return if (changed.size == remote.backupManga.size) remote else remote.copy(backupManga = changed)
    }

    private fun categoryNames(categories: List<BackupCategory>): Map<Long, String> =
        categories.associate { it.order to it.name }

    private fun fingerprint(manga: BackupManga, protoBuf: ProtoBuf): ByteArray =
        protoBuf.encodeToByteArray(
            BackupManga.serializer(),
            manga.copy(
                lastModifiedAt = 0,
                chapters = manga.chapters.sortedWith(
                    compareBy({ it.url }, { it.scanlator.orEmpty() }, { it.name }, { it.sourceOrder }),
                ),
            ),
        )
}
