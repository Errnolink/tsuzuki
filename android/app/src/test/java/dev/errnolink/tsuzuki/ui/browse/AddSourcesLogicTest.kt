package dev.errnolink.tsuzuki.ui.browse

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

class AddSourcesLogicTest {

    private val dummyStore = ExtensionStore(
        indexUrl = "https://example.com/index.json",
        name = "Example",
        badgeLabel = "EX",
        signingKey = "key",
        contact = ExtensionStore.Contact(website = "https://example.com", discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private fun availableExt(pkg: String, lang: String, name: String = pkg): Extension.Available = Extension.Available(
        name = name,
        pkgName = pkg,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = false,
        signatureHash = "sig-$pkg",
        storeName = "Example",
        sources = emptyList(),
        apkUrl = "ext.apk",
        iconUrl = "ext.png",
        store = dummyStore,
    )

    private fun installedExt(pkg: String, lang: String, hasUpdate: Boolean = false): Extension.Installed = Extension.Installed(
        name = pkg,
        pkgName = pkg,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = false,
        signatureHash = "sig-$pkg",
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        hasUpdate = hasUpdate,
        isShared = false,
    )

    private fun untrustedExt(pkg: String): Extension.Untrusted = Extension.Untrusted(
        name = pkg,
        pkgName = pkg,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.4,
        signatureHash = "sig-$pkg",
    )

    @Test
    fun `resolveAddSourcesRowAction maps active installs to Cancel and extension states to actions`() {
        val available = availableExt("pkg.a", "en")
        val installed = installedExt("pkg.b", "en", hasUpdate = false)
        val updatable = installedExt("pkg.c", "en", hasUpdate = true)
        val untrusted = untrustedExt("pkg.d")

        assertEquals(AddSourcesRowActionKind.CANCEL, resolveAddSourcesRowAction(available, InstallStep.Pending))
        assertEquals(AddSourcesRowActionKind.CANCEL, resolveAddSourcesRowAction(available, InstallStep.Downloading))
        assertEquals(AddSourcesRowActionKind.CANCEL, resolveAddSourcesRowAction(installed, InstallStep.Installing))
        assertEquals(AddSourcesRowActionKind.RETRY, resolveAddSourcesRowAction(available, InstallStep.Error))
        assertEquals(AddSourcesRowActionKind.TRUST, resolveAddSourcesRowAction(untrusted, InstallStep.Idle))
        assertEquals(AddSourcesRowActionKind.UPDATE, resolveAddSourcesRowAction(updatable, InstallStep.Idle))
        assertEquals(AddSourcesRowActionKind.INSTALLED, resolveAddSourcesRowAction(installed, InstallStep.Idle))
        assertEquals(AddSourcesRowActionKind.ADD, resolveAddSourcesRowAction(available, InstallStep.Idle))
    }

    @Test
    fun `installStepStatusLabel returns text only for active or failed steps`() {
        assertEquals("Pending…", installStepStatusLabel(InstallStep.Pending))
        assertEquals("Downloading…", installStepStatusLabel(InstallStep.Downloading))
        assertEquals("Installing…", installStepStatusLabel(InstallStep.Installing))
        assertEquals("Failed", installStepStatusLabel(InstallStep.Error))
        assertNull(installStepStatusLabel(InstallStep.Idle))
        assertNull(installStepStatusLabel(InstallStep.Installed))
    }

    @Test
    fun `language options and filtering include arbitrary languages and all-lang extensions`() {
        val enItem = ExtensionUiModel.Item(availableExt("pkg.en", "en"), InstallStep.Idle)
        val esItem = ExtensionUiModel.Item(availableExt("pkg.es", "es"), InstallStep.Idle)
        val multiItem = ExtensionUiModel.Item(availableExt("pkg.all", "all"), InstallStep.Idle)
        val updateItem = ExtensionUiModel.Item(installedExt("pkg.upd", "fr", hasUpdate = true), InstallStep.Idle)

        val items = linkedMapOf(
            ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending) to listOf(updateItem),
            ExtensionUiModel.Header.Text("English") to listOf(enItem),
            ExtensionUiModel.Header.Text("Spanish") to listOf(esItem),
            ExtensionUiModel.Header.Text("All") to listOf(multiItem),
            ExtensionUiModel.Header.Resource(KMR.strings.extensions_page_more) to emptyList(),
        )

        val names = mapOf("en" to "English", "es" to "Spanish", "fr" to "French")
        val options = buildAddSourcesLanguageOptions(items) { code -> names[code] ?: code }
        assertEquals(
            listOf(null to "All languages", "fr" to "French", "en" to "English", "es" to "Spanish"),
            options.map { it.code to it.label },
        )

        val spanishGroups = filterAddSourcesGroups(items, "es")
        assertEquals(
            setOf(
                ExtensionUiModel.Header.Text("Spanish"),
                ExtensionUiModel.Header.Text("All"),
            ),
            spanishGroups.keys,
        )
        assertEquals(listOf(esItem), spanishGroups[ExtensionUiModel.Header.Text("Spanish")])
        assertEquals(listOf(multiItem), spanishGroups[ExtensionUiModel.Header.Text("All")])
    }

    @Test
    fun `resolveAddSourcesEmptyKind distinguishes loading, empty repo placeholder, and filtered empty`() {
        assertEquals(
            AddSourcesEmptyKind.NONE,
            resolveAddSourcesEmptyKind(
                isLoading = true,
                updateCount = 0,
                availableGroupCount = 0,
                searchQuery = null,
                nsfwOnly = false,
                selectedLangCode = null,
            ),
        )

        assertEquals(
            AddSourcesEmptyKind.NO_REPOS,
            resolveAddSourcesEmptyKind(
                isLoading = false,
                updateCount = 0,
                availableGroupCount = 0,
                searchQuery = "",
                nsfwOnly = false,
                selectedLangCode = null,
            ),
        )

        assertEquals(
            AddSourcesEmptyKind.NO_MATCHES,
            resolveAddSourcesEmptyKind(
                isLoading = false,
                updateCount = 0,
                availableGroupCount = 0,
                searchQuery = "mangadex",
                nsfwOnly = false,
                selectedLangCode = null,
            ),
        )

        assertEquals(
            AddSourcesEmptyKind.NO_MATCHES,
            resolveAddSourcesEmptyKind(
                isLoading = false,
                updateCount = 0,
                availableGroupCount = 0,
                searchQuery = null,
                nsfwOnly = true,
                selectedLangCode = null,
            ),
        )

        assertEquals(
            AddSourcesEmptyKind.NONE,
            resolveAddSourcesEmptyKind(
                isLoading = false,
                updateCount = 0,
                availableGroupCount = 2,
                searchQuery = null,
                nsfwOnly = false,
                selectedLangCode = null,
            ),
        )
    }
}
