package dev.errnolink.tsuzuki.ui.settings

import dev.errnolink.tsuzuki.mangadex.backup.BackupMergeManga
import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.extension.model.Extension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestoreExtensionsTest {

    private fun ext(pkg: String, vararg sources: Extension.Available.Source) = Extension.Available(
        name = pkg, pkgName = pkg, versionName = "1", versionCode = 1, libVersion = 1.5, lang = sources.first().lang,
        isNsfw = false, signatureHash = "", storeName = "Store", sources = sources.toList(), apkUrl = "", iconUrl = "",
        store = mockk(relaxed = true),
    )

    private val mangaDex = NekoBackupMapper.SourceIdentity(2499283573021220255, "MangaDex", "en", "https://mangadex.org")

    @Test
    fun `Neko merges ask for each missing merge source once per title and pick the English extension`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(source = mangaDex.id, url = "/a", title = "A", mergeMangaList = listOf(BackupMergeManga("https://mangaball.net/a", "A", "", 7))),
                BackupManga(source = mangaDex.id, url = "/b", title = "B", mergeMangaList = listOf(BackupMergeManga("https://mangaball.net/b", "B", "", 7), BackupMergeManga("https://comix.to/b", "B", "", 10))),
            ),
        )
        val needed = RestoreExtensions.needed(backup, listOf(mangaDex))
        assertEquals(listOf("MangaBall" to 2, "Comix" to 1), needed.map { it.name to it.titles })

        val matches = RestoreExtensions.match(
            needed,
            listOf(
                ext("all.mangaball", Extension.Available.Source(1, "es", "MangaBall", "https://mangaball.net")),
                ext("en.mangaball", Extension.Available.Source(2, "en", "MangaBall", "https://mangaball.net")),
            ),
        )
        assertEquals(listOf("en.mangaball", null), matches.map { it.extension?.pkgName })
    }

    @Test
    fun `Merge sources that are already installed are not asked for`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(source = mangaDex.id, url = "/a", title = "A", mergeMangaList = listOf(BackupMergeManga("https://mangaball.net/a", "A", "", 7))),
            ),
        )
        val installed = listOf(mangaDex, NekoBackupMapper.SourceIdentity(5, "MangaBall", "en", "https://mangaball.net"))
        assertEquals(emptyList<NeededSource>(), RestoreExtensions.needed(backup, installed))
    }

    @Test
    fun `Missing sources from a regular backup match their extension by source id`() {
        val backup = Backup(
            backupManga = listOf(BackupManga(source = 77, url = "/x", title = "X"), BackupManga(source = 77, url = "/y", title = "Y")),
            backupSources = listOf(BackupSource("Some Source", 77)),
        )
        val needed = RestoreExtensions.needed(backup, listOf(mangaDex))
        assertEquals(listOf(NeededSource("Some Source", 77, null, 2)), needed)
        val matches = RestoreExtensions.match(
            needed,
            listOf(
                ext("en.other", Extension.Available.Source(78, "en", "Some Source", "https://other.example")),
                ext("en.right", Extension.Available.Source(77, "en", "Renamed Source", "https://right.example")),
            ),
        )
        assertEquals("en.right", matches.single().extension?.pkgName)
    }
}
