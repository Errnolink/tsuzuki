package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import exh.EXHMigrations
import tachiyomi.domain.manga.model.MergedMangaReference

internal data class MergedReferenceReconciliation(
    val toInsert: List<BackupMergedMangaReference>,
    val toUpdate: List<Pair<Long, BackupMergedMangaReference>>,
    val toDelete: List<Long>,
)

/**
 * Reconciles the authoritative winning parent's reference snapshot against the local set.
 * Identity is (child source id, child manga URL) within the owning merged manga;
 * the result replaces the local set instead of unioning with it.
 */
internal fun reconcileMergedReferences(
    existing: List<MergedMangaReference>,
    incoming: List<BackupMergedMangaReference>,
): MergedReferenceReconciliation {
    val migrated = incoming.map { EXHMigrations.migrateBackupMergedMangaReference(it) }
    val incomingByKey = migrated.associateBy { it.key() }

    val toDelete = mutableListOf<Long>()
    val toUpdate = mutableListOf<Pair<Long, BackupMergedMangaReference>>()
    val retainedKeys = mutableSetOf<String>()

    existing.forEach { ref ->
        val key = "${ref.mangaSourceId}|${ref.mangaUrl}"
        val winner = incomingByKey[key]
        if (winner == null) {
            toDelete += ref.id
        } else if (ref.mergeUrl != winner.mergeUrl) {
            // The owning aggregate URL changed; the link is rewritten, not patched.
            toDelete += ref.id
        } else {
            retainedKeys += key
            if (ref.isInfoManga != winner.isInfoManga ||
                ref.getChapterUpdates != winner.getChapterUpdates ||
                ref.chapterSortMode != winner.chapterSortMode ||
                ref.chapterPriority != winner.chapterPriority ||
                ref.downloadChapters != winner.downloadChapters
            ) {
                toUpdate += ref.id to winner
            }
        }
    }

    val toInsert = migrated.filter { "${it.mangaSourceId}|${it.mangaUrl}" !in retainedKeys }

    return MergedReferenceReconciliation(toInsert, toUpdate, toDelete)
}

private fun BackupMergedMangaReference.key() = "$mangaSourceId|$mangaUrl"
