package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Download
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Language
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Local
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Source
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Unread
import eu.kanade.tachiyomi.ui.library.LibraryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source

class LibraryTileOverlaysTest {

    private fun item(
        unread: Long = 3,
        total: Long = 10,
        read: Long = total - unread,
        downloads: Long = 0,
        isLocal: Boolean = false,
        language: String = "",
        useLangIcon: Boolean = true,
        source: Source? = null,
    ) = LibraryItem(
        libraryManga = LibraryManga(
            manga = Manga.create().copy(id = 7L),
            categories = emptyList(),
            totalChapters = total,
            readCount = read,
            bookmarkCount = 0,
            bookmarkReadCount = 0,
            chapterFlags = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        ),
        downloadCount = downloads,
        unreadCount = unread,
        isLocal = isLocal,
        sourceLanguage = language,
        useLangIcon = useLangIcon,
        source = source,
    )

    private fun kinds(overlays: LibraryTileOverlays, badges: LibraryTileBadges) =
        overlays.resolvedBadges(badges).map { it.kind }

    @Test
    fun `all badge prefs on shows full set in order for fully decorated item`() {
        val overlays = LibraryTileOverlays.from(
            item(downloads = 4, isLocal = true, language = "en", source = Source(5L, "en", "MangaDex", supportsLatest = false, isStub = false)),
            continueChapterId = 99L,
        )
        val badges = LibraryTileBadges(
            download = true,
            unread = true,
            local = true,
            language = true,
            source = true,
            continueButton = true,
        )
        assertEquals(listOf(Download, Unread, Local, Language, Source), kinds(overlays, badges))
        assertTrue(overlays.showContinue)
    }

    @Test
    fun `pref off hides its badge even when data present`() {
        val overlays = LibraryTileOverlays.from(
            item(downloads = 4, isLocal = true, language = "en", source = Source(5L, "en", "MangaDex", supportsLatest = false, isStub = false)),
        )
        val badges = LibraryTileBadges(
            download = false,
            unread = true,
            local = false,
            language = false,
            source = false,
        )
        assertEquals(listOf(Unread), kinds(overlays, badges))
        assertFalse(overlays.showContinue)
    }

    @Test
    fun `zero and missing data yields no badges even with prefs on`() {
        val overlays = LibraryTileOverlays.from(item(unread = 0, downloads = 0))
        val badges = LibraryTileBadges(download = true, unread = true, local = true, language = true, source = true)
        assertTrue(overlays.resolvedBadges(badges).isEmpty())
    }

    @Test
    fun `read manga has no unread badge and marks read state`() {
        val overlays = LibraryTileOverlays.from(item(unread = 0, total = 10, read = 10))
        val badges = LibraryTileBadges(download = false, unread = true)
        assertTrue(overlays.resolvedBadges(badges).isEmpty())
        assertTrue(overlays.read)
    }

    @Test
    fun `continue button requires both pref and target chapter`() {
        val withTarget = LibraryTileOverlays.from(item(), continueChapterId = 42L)
        val withoutTarget = LibraryTileOverlays.from(item())
        val badges = LibraryTileBadges(continueButton = true)
        assertTrue(withTarget.showContinue)
        assertFalse(withoutTarget.showContinue)
        val prefOff = LibraryTileBadges(continueButton = false)
        assertFalse(withTarget.showContinue && prefOff.continueButton)
    }

    @Test
    fun `download badge label carries the count`() {
        val overlays = LibraryTileOverlays.from(item(unread = 0, downloads = 12))
        val badges = LibraryTileBadges(download = true)
        val resolved = overlays.resolvedBadges(badges).single()
        assertEquals(Download, resolved.kind)
        assertEquals("\u2193 12", resolved.label)
    }

    @Test
    fun `language badge uppercases the source language`() {
        val overlays = LibraryTileOverlays.from(item(unread = 0, language = "pt-br"))
        val badges = LibraryTileBadges(language = true)
        val resolved = overlays.resolvedBadges(badges).single()
        assertEquals(Language, resolved.kind)
        assertEquals("PT-BR", resolved.label)
    }
}
