package dev.errnolink.tsuzuki.ui.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FeedMergerTest {

    @Test
    fun testDedupSameIdentityUrlAndSource() {
        val lib = listOf(
            FeedChapterItem(
                id = "lib-1",
                mangaId = 1L,
                chapterId = 10L,
                mangaTitle = "Onii-chan wa Oshimai!",
                chapterName = "Ch. 117",
                chapterNumber = 117.0,
                dateFetch = 1000L,
                coverUrl = null,
                isLibrary = true,
                isRead = false,
                sourceId = 12345L,
                chapterUrl = "/chapter/abc-123",
            ),
        )
        val md = listOf(
            FeedChapterItem(
                id = "md-1",
                mangaId = -1L,
                chapterId = null,
                mangaTitle = "onii-chan wa oshimai!",
                chapterName = "Ch. 117 · English",
                chapterNumber = 117.0,
                dateFetch = 1005L,
                coverUrl = null,
                isLibrary = false,
                isRead = false,
                mangaDexId = "md-uuid",
                sourceId = 12345L,
                chapterUrl = "/chapter/abc-123",
            ),
        )
        val merged = FeedMerger.merge(lib, md)
        assertEquals(1, merged.size)
        assertEquals("lib-1", merged.first().id)
        assertTrue(merged.first().isLibrary)
    }

    @Test
    fun testSameTitleDifferentUrlsBothKept() {
        val lib = listOf(
            FeedChapterItem(
                id = "lib-1",
                mangaId = 1L,
                chapterId = 10L,
                mangaTitle = "Hero Story",
                chapterName = "Ch. 1",
                chapterNumber = 1.0,
                dateFetch = 1000L,
                coverUrl = null,
                isLibrary = true,
                isRead = false,
                sourceId = 100L,
                chapterUrl = "/chapter/source-a-1",
            ),
        )
        val md = listOf(
            FeedChapterItem(
                id = "md-2",
                mangaId = 2L,
                chapterId = null,
                mangaTitle = "Hero Story",
                chapterName = "Ch. 1",
                chapterNumber = 1.0,
                dateFetch = 1005L,
                coverUrl = null,
                isLibrary = false,
                isRead = false,
                mangaDexId = "md-uuid",
                sourceId = 200L,
                chapterUrl = "/chapter/source-b-1",
            ),
        )
        val merged = FeedMerger.merge(lib, md)
        assertEquals(2, merged.size)
    }

    @Test
    fun testFollowsItemWhoseMangaIsLibraryFavoriteShownAsLibrary() {
        val follows = listOf(
            FeedChapterItem(
                id = "md-fav",
                mangaId = -1L,
                chapterId = null,
                mangaTitle = "Frieren",
                chapterName = "Ch. 130",
                chapterNumber = 130.0,
                dateFetch = 1005L,
                coverUrl = null,
                isLibrary = false,
                isRead = false,
                mangaDexId = "frieren-uuid",
                sourceId = 12345L,
                chapterUrl = "/chapter/frieren-130",
            ),
            FeedChapterItem(
                id = "md-nonfav",
                mangaId = -1L,
                chapterId = null,
                mangaTitle = "Random Series",
                chapterName = "Ch. 1",
                chapterNumber = 1.0,
                dateFetch = 1005L,
                coverUrl = null,
                isLibrary = false,
                isRead = false,
                mangaDexId = "random-uuid",
                sourceId = 12345L,
                chapterUrl = "/chapter/random-1",
            ),
        )
        val merged = FeedMerger.merge(
            libraryItems = emptyList(),
            followsItems = follows,
            libraryMangaDexUuids = setOf("frieren-uuid"),
        )
        assertEquals(2, merged.size)
        val frieren = merged.first { it.mangaTitle == "Frieren" }
        assertTrue(frieren.isLibrary)
        val random = merged.first { it.mangaTitle == "Random Series" }
        assertFalse(random.isLibrary)
    }

    @Test
    fun testChipFiltering() {
        val items = listOf(
            FeedChapterItem("1", 1L, 10L, "M1", "Ch 1", 1.0, 1L, null, isLibrary = true, isRead = false),
            FeedChapterItem("2", 2L, null, "M2", "Ch 1", 1.0, 1L, null, isLibrary = false, isRead = false, mangaDexId = "uuid2"),
            FeedChapterItem("3", 3L, 30L, "M3", "Ch 1", 1.0, 1L, null, isLibrary = true, isRead = true),
        )

        assertEquals(3, FeedMerger.filter(items, FeedChip.ALL, isLoggedIn = true).size)

        val libOnly = FeedMerger.filter(items, FeedChip.LIBRARY, isLoggedIn = true)
        assertEquals(2, libOnly.size)
        assertTrue(libOnly.all { it.isLibrary })

        val mdOnly = FeedMerger.filter(items, FeedChip.MANGADEX, isLoggedIn = true)
        assertEquals(1, mdOnly.size)
        assertEquals("2", mdOnly.first().id)

        val mdLoggedOut = FeedMerger.filter(items, FeedChip.MANGADEX, isLoggedIn = false)
        assertEquals(3, mdLoggedOut.size)

        val unreadOnly = FeedMerger.filter(items, FeedChip.UNREAD, isLoggedIn = true)
        assertEquals(2, unreadOnly.size)
        assertTrue(unreadOnly.none { it.isRead })
    }

    @Test
    fun testHideInLibraryLeavesExplicitChipsAlone() {
        val items = listOf(
            FeedChapterItem("1", 1L, 10L, "M1", "Ch 1", 1.0, 1L, null, isLibrary = true, isRead = false),
            FeedChapterItem("2", 2L, null, "M2", "Ch 1", 1.0, 1L, null, isLibrary = false, isRead = false, mangaDexId = "uuid2"),
            FeedChapterItem("3", 3L, 30L, "M3", "Ch 1", 1.0, 1L, null, isLibrary = true, isRead = true),
        )

        val allChip = FeedMerger.filter(items, FeedChip.ALL, isLoggedIn = true, hideInLibrary = true)
        assertEquals(listOf("2"), allChip.map { it.id })

        val libraryChip = FeedMerger.filter(items, FeedChip.LIBRARY, isLoggedIn = true, hideInLibrary = true)
        assertEquals(listOf("1", "3"), libraryChip.map { it.id })

        val unreadChip = FeedMerger.filter(items, FeedChip.UNREAD, isLoggedIn = true, hideInLibrary = true)
        assertEquals(listOf("1", "2"), unreadChip.map { it.id })
    }

    @Test
    fun testHideInLibraryAppliesToLoggedOutMangaDexFallback() {
        val items = listOf(
            FeedChapterItem("1", 1L, 10L, "M1", "Ch 1", 1.0, 1L, null, isLibrary = true, isRead = false),
            FeedChapterItem("2", 2L, null, "M2", "Ch 1", 1.0, 1L, null, isLibrary = false, isRead = false, mangaDexId = "uuid2"),
        )

        val fallback = FeedMerger.filter(items, FeedChip.MANGADEX, isLoggedIn = false, hideInLibrary = true)
        assertEquals(listOf("2"), fallback.map { it.id })
    }

    @Test
    fun testGroupingByDayAndTitle() {
        val day1 = 1700000000000L // arbitrary fixed epoch millis
        val day2 = day1 + 86_400_000L
        val items = listOf(
            FeedChapterItem("1", 1L, 10L, "Title A", "Ch 1", 1.0, day1, null, isLibrary = true, isRead = false),
            FeedChapterItem("2", 1L, 11L, "Title A", "Ch 2", 2.0, day1, null, isLibrary = true, isRead = false),
            FeedChapterItem("3", 2L, 20L, "Title B", "Ch 5", 5.0, day1, null, isLibrary = false, isRead = false, mangaDexId = "m2"),
            FeedChapterItem("4", 1L, 12L, "Title A", "Ch 3", 3.0, day2, null, isLibrary = true, isRead = false),
        )
        val groups = FeedMerger.groupByDayAndTitle(items)
        assertEquals(2, groups.size)
        val epochDay1 = LocalDate.ofEpochDay(day1 / 86_400_000L)
        val day1Groups = groups[epochDay1]
        assertEquals(2, day1Groups?.size)
        val titleAGroup = day1Groups?.first { it.mangaTitle == "Title A" }
        assertEquals(2, titleAGroup?.chapters?.size)
    }

    @Test
    fun distinctMangaSharingATitleStaySeparateGroups() {
        val t = 1700000000000L
        val items = listOf(
            FeedChapterItem("1", 1L, 10L, "Same", "Ch 1", 1.0, t, null, isLibrary = true, isRead = false),
            FeedChapterItem("2", 2L, 20L, "Same", "Ch 1", 1.0, t, null, isLibrary = true, isRead = false),
        )
        val day = FeedMerger.groupByDayAndTitle(items).values.single()
        assertEquals(2, day.size)
        assertEquals(2, day.map { it.groupKey }.toSet().size)
    }

    @Test
    fun daysBucketInTheGivenZone() {
        val lateUtc = java.time.Instant.parse("2026-11-01T20:00:00Z").toEpochMilli()
        val items = listOf(FeedChapterItem("1", 1L, 10L, "A", "Ch 1", 1.0, lateUtc, null, isLibrary = true, isRead = false))
        val ist = FeedMerger.groupByDayAndTitle(items, java.time.ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDate.of(2026, 11, 2), ist.keys.single())
    }
}
