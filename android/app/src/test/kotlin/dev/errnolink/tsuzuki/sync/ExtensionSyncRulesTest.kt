package dev.errnolink.tsuzuki.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionSyncRulesTest {
    private fun wanted(
        pkg: String,
        updatedAt: Long,
        repo: String = "https://example.invalid/index.pb",
        fingerprint: String = "AA11",
        versionCode: Long = 1,
    ): BackupWantedExtension = BackupWantedExtension(pkg, repo, fingerprint, versionCode, updatedAt, "1.0.0")

    private fun tombstone(
        pkg: String,
        deletedAt: Long,
    ): BackupExtensionTombstone = BackupExtensionTombstone(pkg, deletedAt)

    @Test
    fun newestEventWinsPerPackage() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(
                    wanted = listOf(wanted("a", updatedAt = 100), wanted("b", updatedAt = 100)),
                    tombstones = listOf(tombstone("a", deletedAt = 200), tombstone("c", deletedAt = 50)),
                ),
            )

        resolved["a"] shouldBe ExtensionSyncRules.PkgResolution.Tombstoned(200)
        (resolved["a"] as ExtensionSyncRules.PkgResolution.Tombstoned).deletedAt shouldBe 200L
        resolved["b"] shouldBe ExtensionSyncRules.PkgResolution.Wanted(wanted("b", updatedAt = 100))
        resolved["c"] shouldBe ExtensionSyncRules.PkgResolution.Tombstoned(50)
        resolved.containsKey("d") shouldBe false
    }

    @Test
    fun installWinsExactTimestampTie() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(
                    wanted = listOf(wanted("a", updatedAt = 100)),
                    tombstones = listOf(tombstone("a", deletedAt = 100)),
                ),
            )

        resolved["a"] shouldBe ExtensionSyncRules.PkgResolution.Wanted(wanted("a", updatedAt = 100))
    }

    @Test
    fun newerWantedResurrectsOverTombstone() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(
                    wanted = listOf(wanted("a", updatedAt = 300)),
                    tombstones = listOf(tombstone("a", deletedAt = 200)),
                ),
            )

        val state = resolved["a"] as ExtensionSyncRules.PkgResolution.Wanted
        state.entry.updatedAt shouldBe 300L
    }

    @Test
    fun duplicatesResolveToNewestWithinOneSection() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(
                    wanted = listOf(wanted("a", updatedAt = 10), wanted("a", updatedAt = 20)),
                    tombstones = listOf(tombstone("b", deletedAt = 10), tombstone("b", deletedAt = 20)),
                ),
            )

        (resolved["a"] as ExtensionSyncRules.PkgResolution.Wanted).entry.updatedAt shouldBe 20L
        (resolved["b"] as ExtensionSyncRules.PkgResolution.Tombstoned).deletedAt shouldBe 20L
    }

    @Test
    fun mergeLocalAndRemotePerPackage() {
        val local =
            BackupWantedExtensions(
                wanted = listOf(wanted("keep-local", updatedAt = 500)),
                tombstones = listOf(tombstone("drop-remote", deletedAt = 100)),
            )
        val remote =
            BackupWantedExtensions(
                wanted = listOf(wanted("keep-local", updatedAt = 400), wanted("adopt-remote", updatedAt = 900)),
                tombstones = listOf(tombstone("drop-remote", deletedAt = 800), tombstone("new-tombstone", deletedAt = 1)),
            )

        val resolved = ExtensionSyncRules.mergeSections(local, remote)

        (resolved["keep-local"] as ExtensionSyncRules.PkgResolution.Wanted).entry.updatedAt shouldBe 500L
        (resolved["adopt-remote"] as ExtensionSyncRules.PkgResolution.Wanted).entry.updatedAt shouldBe 900L
        (resolved["drop-remote"] as ExtensionSyncRules.PkgResolution.Tombstoned).deletedAt shouldBe 800L
        resolved["new-tombstone"] shouldBe ExtensionSyncRules.PkgResolution.Tombstoned(1)
    }

    @Test
    fun freshDeviceAdoptsRemoteSection() {
        val resolved = ExtensionSyncRules.mergeSections(null, BackupWantedExtensions(wanted = listOf(wanted("a", 5))))

        (resolved["a"] as ExtensionSyncRules.PkgResolution.Wanted).entry.updatedAt shouldBe 5L
    }

    @Test
    fun planInstallsMissingWantedFromTrustedRepo() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(wanted = listOf(wanted("a", 1))),
            )
        val changes =
            ExtensionSyncRules.planChanges(
                resolved = resolved,
                installed = emptyMap(),
                catalog = mapOf("a" to ExtensionSyncRules.CatalogEntry("https://example.invalid/index.pb", true)),
                trustedRepos = mapOf("https://example.invalid/index.pb" to "aa11"),
                syncUninstalls = true,
            )

        changes.shouldBeEmpty()
    }

    @Test
    fun planSkipReasons() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(
                    wanted =
                        listOf(
                            wanted("untrusted", 1, repo = "https://elsewhere.invalid/index.pb"),
                            wanted("missing", 1, repo = "https://example.invalid/index.pb"),
                            wanted("wrongrepo", 1, repo = "https://example.invalid/index.pb"),
                            wanted("badfp", 1, repo = "https://example.invalid/index.pb", fingerprint = "BB22"),
                        ),
                ),
            )
        val changes =
            ExtensionSyncRules.planChanges(
                resolved = resolved,
                installed = emptyMap(),
                catalog =
                    mapOf(
                        "missing" to ExtensionSyncRules.CatalogEntry(null, true),
                        "wrongrepo" to ExtensionSyncRules.CatalogEntry("https://other.invalid/index.pb", true),
                        "badfp" to ExtensionSyncRules.CatalogEntry("https://example.invalid/index.pb", true),
                    ),
                trustedRepos = mapOf("https://example.invalid/index.pb" to "AA11"),
                syncUninstalls = true,
            ).associateBy { it.pkgName }

        changes["untrusted"]?.action shouldBe ExtensionSyncRules.Action.SKIPPED
        changes["untrusted"]?.reason shouldBe ExtensionSyncRules.REASON_UNTRUSTED_REPO
        changes["missing"]?.reason shouldBe ExtensionSyncRules.REASON_NOT_IN_REPO
        changes["wrongrepo"]?.reason shouldBe ExtensionSyncRules.REASON_NOT_IN_REPO
        changes["badfp"]?.reason shouldBe ExtensionSyncRules.REASON_FINGERPRINT_MISMATCH
    }

    @Test
    fun planDoesNothingForInstalledOrBuiltinWanted() {
        val localSourcePkg = "eu.kanade.tachiyomi.source.local"
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(wanted = listOf(wanted("installed", 1), wanted(localSourcePkg, 1))),
            )
        val changes =
            ExtensionSyncRules.planChanges(
                resolved = resolved,
                installed = mapOf("installed" to ExtensionSyncRules.InstalledExtension("installed", 1)),
                catalog = emptyMap(),
                trustedRepos = emptyMap(),
                syncUninstalls = true,
            )

        changes.shouldBeEmpty()
    }

    @Test
    fun androidLocalSourcePkgIsAlsoBuiltin() {
        ExtensionSyncRules.isBuiltIn("tachiyomi.source.local") shouldBe true
        ExtensionSyncRules.isBuiltIn("suwayomi.tachidesk.manga.impl.source.merged") shouldBe true
        ExtensionSyncRules.isBuiltIn("eu.kanade.tachiyomi.extension.en.weebcentral") shouldBe false
    }

    @Test
    fun planUninstallsTombstonedInstalledUnlessDisabled() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(tombstones = listOf(tombstone("a", 1))),
            )
        val installed = mapOf("a" to ExtensionSyncRules.InstalledExtension("a", 1))

        val uninstallChanges =
            ExtensionSyncRules.planChanges(resolved, installed, emptyMap(), emptyMap(), syncUninstalls = true)
        uninstallChanges.shouldBeEmpty()

        val disabledChanges =
            ExtensionSyncRules.planChanges(resolved, installed, emptyMap(), emptyMap(), syncUninstalls = false)
        disabledChanges.shouldHaveSingleElement(
            ExtensionSyncRules.Change("a", ExtensionSyncRules.Action.SKIPPED, ExtensionSyncRules.REASON_UNINSTALLS_DISABLED),
        )
    }

    @Test
    fun planIgnoresTombstoneForNotInstalled() {
        val resolved =
            ExtensionSyncRules.resolve(
                BackupWantedExtensions(tombstones = listOf(tombstone("gone", 1))),
            )
        val changes = ExtensionSyncRules.planChanges(resolved, emptyMap(), emptyMap(), emptyMap(), true)

        changes.shouldBeEmpty()
    }
}
