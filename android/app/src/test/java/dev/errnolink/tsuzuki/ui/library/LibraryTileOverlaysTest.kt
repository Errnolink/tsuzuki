package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Download
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Language
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Local
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Source
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind.Unread
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryScreenModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    /** The localized stand-ins `from` needs; the words themselves are resolved per screen in the app. */
    private fun fromItem(item: LibraryItem, target: LibraryScreenModel.ContinueTarget? = null) =
        LibraryTileOverlays.from(item, target, "Merged")

    private fun kinds(overlays: LibraryTileOverlays, badges: LibraryTileBadges) =
        overlays.resolvedBadges(badges, "Local").map { it.kind }

    @Test
    fun `all badge prefs on shows full set in order for fully decorated item`() {
        val overlays = fromItem(
            item(downloads = 4, isLocal = true, language = "en", source = Source(5L, "en", "MangaDex", supportsLatest = false, isStub = false)),
            target = LibraryScreenModel.ContinueTarget(99L, null),
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
        val overlays = fromItem(
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
        val overlays = fromItem(item(unread = 0, downloads = 0))
        val badges = LibraryTileBadges(download = true, unread = true, local = true, language = true, source = true)
        assertTrue(overlays.resolvedBadges(badges, "Local").isEmpty())
    }

    @Test
    fun `read manga has no unread badge and marks read state`() {
        val overlays = fromItem(item(unread = 0, total = 10, read = 10))
        val badges = LibraryTileBadges(download = false, unread = true)
        assertTrue(overlays.resolvedBadges(badges, "Local").isEmpty())
        assertTrue(overlays.read)
    }

    @Test
    fun `continue button requires both pref and target chapter`() {
        val withTarget = fromItem(item(), target = LibraryScreenModel.ContinueTarget(42L, null))
        val withoutTarget = fromItem(item())
        val badges = LibraryTileBadges(continueButton = true)
        assertTrue(withTarget.showContinue)
        assertFalse(withoutTarget.showContinue)
        val prefOff = LibraryTileBadges(continueButton = false)
        assertFalse(withTarget.showContinue && prefOff.continueButton)
    }

    @Test
    fun `download badge label carries the count`() {
        val overlays = fromItem(item(unread = 0, downloads = 12))
        val badges = LibraryTileBadges(download = true)
        val resolved = overlays.resolvedBadges(badges, "Local").single()
        assertEquals(Download, resolved.kind)
        assertEquals("\u2193 12", resolved.label)
    }

    @Test
    fun `language badge uppercases the source language`() {
        val overlays = fromItem(item(unread = 0, language = "pt-br"))
        val badges = LibraryTileBadges(language = true)
        val resolved = overlays.resolvedBadges(badges, "Local").single()
        assertEquals(Language, resolved.kind)
        assertEquals("PT-BR", resolved.label)
    }

    @Test
    fun `the continue target becomes the hero tap seed`() {
        val overlays = LibraryTileOverlays(
            continueChapterId = 7,
            continueChapterNumber = 12.5,
            continueChapterName = "Chapter 12.5",
            continueLastPageRead = 3,
            continuePageTotal = 24,
        )
        val seed = overlays.heroTapSeed()!!
        assertEquals(12.5, seed.continueChapterNumber)
        assertEquals("Chapter 12.5", seed.continueChapterName)
        assertEquals(3L, seed.continueLastPageRead)
        assertEquals(24, seed.continuePageTotal)
        assertTrue(seed.hasChapter)
    }

    @Test
    fun `a tile without a continue target seeds nothing`() {
        val seed = LibraryTileOverlays().heroTapSeed()
        assertNull(seed)
    }
}
