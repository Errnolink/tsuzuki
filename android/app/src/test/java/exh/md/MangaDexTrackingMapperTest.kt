package exh.md

import exh.md.handlers.MangaDexFeatureMapper
import exh.md.service.MangaDexFeatureRequests
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaDexTrackingMapperTest {
    @Test
    fun `Kitsu lookup uses the current upstream host and parses the public response`() {
        val request = MangaDexFeatureRequests.kitsu("2")
        assertEquals("https://kitsu.app/api/edge/manga/2", request.url.toString())
        assertEquals("GET", request.method)
        val body = javaClass.getResourceAsStream("/mangadex/kitsu.json")!!.bufferedReader().use { it.readText() }
        val track = MangaDexFeatureMapper.kitsuTrack(body)
        assertEquals(2L, track.remote_id)
        assertEquals("Boku no Mune ga Itakutemo - Glass", track.title)
        assertEquals(0L, track.total_chapters)
        assertEquals("https://kitsu.app/manga/2", track.tracking_url)
    }

    @Test
    fun `tracker links extract the identifier before human-readable slugs`() {
        val links = buildJsonObject {
            put("mu", "https://www.mangaupdates.com/series/a1b2c3/title-slug")
            put("al", "https://anilist.co/manga/113138/title-slug")
            put("mal", "https://myanimelist.net/manga/2/title-slug")
        }
        assertEquals(mapOf("mu" to "a1b2c3", "al" to "113138", "mal" to "2"), MangaDexFeatureMapper.trackerLinks(links))
    }
}
