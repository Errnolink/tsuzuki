package dev.errnolink.tsuzuki.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.restore.restorers.MergedReferenceReconciliation
import eu.kanade.tachiyomi.data.backup.restore.restorers.reconcileMergedReferences
import exh.EXHMigrations
import tachiyomi.domain.manga.model.MergedMangaReference

/**
 * Guards restored titles against blank/damaged names and preserves local merged references when
 * merging non-authoritative backups.
 */
object BackupRestoreHardening {

    fun safeRestoreTitle(title: String, existingTitle: String?, fallbackUrl: String): String =
        title.takeIf { it.isNotBlank() }
            ?: existingTitle?.takeIf { it.isNotBlank() }
            ?: fallbackUrl

    internal fun reconcileReferences(
        existing: List<MergedMangaReference>,
        incoming: List<BackupMergedMangaReference>,
        replace: Boolean,
    ): MergedReferenceReconciliation {
        if (replace) return reconcileMergedReferences(existing, incoming)
        val existingKeys = existing.mapTo(hashSetOf()) { "${it.mangaSourceId}|${it.mangaUrl}" }
        val toInsert = incoming
            .map { EXHMigrations.migrateBackupMergedMangaReference(it) }
            .filter { "${it.mangaSourceId}|${it.mangaUrl}" !in existingKeys }
        return MergedReferenceReconciliation(
            toInsert = toInsert,
            toUpdate = emptyList(),
            toDelete = emptyList(),
        )
    }
}
