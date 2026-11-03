package dev.errnolink.tsuzuki.mangadex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class MangaDexIntentsTest {
    private val id = "f9c33607-9180-4ba6-b85c-e4b5faee7192"

    @Test
    fun `public entity links route only supported MangaDex UUID paths`() {
        for (kind in listOf("author", "group", "list")) {
            assertEquals(MangaDexIntents.Entity(kind, id), MangaDexIntents.entity("https://mangadex.org/$kind/$id/name"))
        }
        assertNull(MangaDexIntents.entity("https://mangadex.org.evil.test/author/$id"))
        assertNull(MangaDexIntents.entity("https://example.org/author/$id"))
        assertNull(MangaDexIntents.entity("file://mangadex.org/author/$id"))
        assertNull(MangaDexIntents.entity("https://mangadex.org/author/not-a-uuid"))
        assertNull(MangaDexIntents.entity("https://mangadex.org/title/$id"))
        assertNull(MangaDexIntents.entity(null))
    }

    @Test
    fun `transient refresh failures do not expire the saved session`() {
        listOf(400, 401, 403).forEach { assertTrue(MangaDexSessionNotification.isSessionRejected(it)) }
        listOf(200, 429, 500, 502, 503, 504).forEach { assertFalse(MangaDexSessionNotification.isSessionRejected(it)) }
        assertEquals("eu.kanade.tachiyomi.MANGADEX_SETTINGS", MangaDexIntents.SETTINGS)
    }

    @Test
    fun `tracker links resolve external services to mapping lookups`() {
        assertEquals(MangaDexIntents.TrackerLink("al", "AniList", "113138"), MangaDexIntents.trackerLink("https://anilist.co/manga/113138/An-Entry"))
        assertEquals(MangaDexIntents.TrackerLink("mal", "MyAnimeList", "2"), MangaDexIntents.trackerLink("https://myanimelist.net/manga/2"))
        assertEquals(
            MangaDexIntents.TrackerLink("mu_new", "MangaUpdates", BigInteger("6521", 36).toString()),
            MangaDexIntents.trackerLink("https://www.mangaupdates.com/series/6521"),
        )
        assertEquals(MangaDexIntents.TrackerLink("mb", "MangaBaka", "60922"), MangaDexIntents.trackerLink("https://mangabaka.org/60922"))
        assertEquals(MangaDexIntents.TrackerLink("mb", "MangaBaka", "10584"), MangaDexIntents.trackerLink("https://mangabaka.org/title/10584/name"))
    }

    @Test
    fun `foreign tracker links are ignored`() {
        assertNull(MangaDexIntents.trackerLink("https://anilist.co/manga"))
        assertNull(MangaDexIntents.trackerLink("https://example.org/manga/113138"))
        assertNull(MangaDexIntents.trackerLink("https://anilist.co.evil.test/manga/113138"))
        assertNull(MangaDexIntents.trackerLink("https://www.mangaupdates.com/series/not-base36!"))
        assertNull(MangaDexIntents.trackerLink("file://anilist.co/manga/113138"))
        assertNull(MangaDexIntents.trackerLink(null))
        assertNull(MangaDexIntents.trackerLink("https://mangadex.org/title/$id"))
    }
}
