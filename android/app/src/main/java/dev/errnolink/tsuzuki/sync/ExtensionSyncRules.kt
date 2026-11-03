package dev.errnolink.tsuzuki.sync

object ExtensionSyncRules {
    enum class Action {
        INSTALLED,
        UNINSTALLED,
        SKIPPED,
        FAILED,
    }

    data class Change(
        val pkgName: String,
        val action: Action,
        val reason: String? = null,
    )

    const val REASON_UNTRUSTED_REPO = "UNTRUSTED_REPO"
    const val REASON_FINGERPRINT_MISMATCH = "FINGERPRINT_MISMATCH"
    const val REASON_NOT_IN_REPO = "NOT_IN_REPO"
    const val REASON_UNINSTALLS_DISABLED = "UNINSTALLS_DISABLED"
    const val REASON_INSTALL_FAILED = "INSTALL_FAILED"

    sealed interface PkgResolution {
        data class Wanted(
            val entry: BackupWantedExtension,
        ) : PkgResolution

        data class Tombstoned(
            val deletedAt: Long,
        ) : PkgResolution
    }

    data class InstalledExtension(
        val pkgName: String,
        val versionCode: Long,
    )

    data class CatalogEntry(
        val storeIndexUrl: String?,
        val hasDownloadUrl: Boolean,
    )

    private val builtInPackages =
        setOf(
            "eu.kanade.tachiyomi.source.local",
            "tachiyomi.source.local",
            "suwayomi.tachidesk.manga.impl.source.merged",
        )

    fun isBuiltIn(pkgName: String): Boolean = pkgName in builtInPackages

    fun resolve(section: BackupWantedExtensions): Map<String, PkgResolution> {
        val wantedByPkg = section.wanted.groupBy { it.pkgName }
        val tombstonesByPkg = section.tombstones.groupBy { it.pkgName }
        return (wantedByPkg.keys + tombstonesByPkg.keys).associateWith { pkg ->
            val wanted = wantedByPkg[pkg].orEmpty().maxByOrNull { it.updatedAt }
            val tombstone = tombstonesByPkg[pkg].orEmpty().maxByOrNull { it.deletedAt }
            if (tombstone != null && (wanted == null || tombstone.deletedAt > wanted.updatedAt)) {
                PkgResolution.Tombstoned(tombstone.deletedAt)
            } else {
                PkgResolution.Wanted(wanted!!)
            }
        }
    }

    fun mergeSections(
        local: BackupWantedExtensions?,
        remote: BackupWantedExtensions?,
    ): Map<String, PkgResolution> =
        resolve(
            BackupWantedExtensions(
                wanted = local?.wanted.orEmpty() + remote?.wanted.orEmpty(),
                tombstones = local?.tombstones.orEmpty() + remote?.tombstones.orEmpty(),
            ),
        )

    fun planChanges(
        resolved: Map<String, PkgResolution>,
        installed: Map<String, InstalledExtension>,
        catalog: Map<String, CatalogEntry>,
        trustedRepos: Map<String, String>,
        syncUninstalls: Boolean,
    ): List<Change> {
        val changes = mutableListOf<Change>()
        resolved.forEach { (pkg, state) ->
            when (state) {
                is PkgResolution.Wanted -> {
                    if (installed.containsKey(pkg) || isBuiltIn(pkg)) return@forEach
                    val trustedKey = trustedRepos[state.entry.repoIndexUrl.lowercase()]
                    val catalogEntry = catalog[pkg]
                    when {
                        trustedKey == null ->
                            changes.add(Change(pkg, Action.SKIPPED, REASON_UNTRUSTED_REPO))

                        catalogEntry == null ||
                            !catalogEntry.hasDownloadUrl ||
                            !catalogEntry.storeIndexUrl.equals(state.entry.repoIndexUrl, ignoreCase = true) ->
                            changes.add(Change(pkg, Action.SKIPPED, REASON_NOT_IN_REPO))

                        !trustedKey.equals(state.entry.signingFingerprint, ignoreCase = true) ->
                            changes.add(Change(pkg, Action.SKIPPED, REASON_FINGERPRINT_MISMATCH))
                    }
                }

                is PkgResolution.Tombstoned -> {
                    if (!installed.containsKey(pkg) || isBuiltIn(pkg)) return@forEach
                    if (!syncUninstalls) {
                        changes.add(Change(pkg, Action.SKIPPED, REASON_UNINSTALLS_DISABLED))
                    }
                }
            }
        }
        return changes
    }
}
