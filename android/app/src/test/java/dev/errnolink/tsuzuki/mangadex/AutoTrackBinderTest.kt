package dev.errnolink.tsuzuki.mangadex

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import exh.md.handlers.AutoTrackBinder
import exh.md.handlers.AutoTrackResult
import exh.md.handlers.AutoTrackTitle
import exh.md.handlers.AutoTrackTitleInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoTrackBinderTest {

    private val trackerIds = mapOf("al" to 2L, "mal" to 1L, "kt" to 3L, "mu" to 7L)
    private val title = AutoTrackTitle(1L, "https://mangadex.org/title/uuid", source = null)

    private fun links(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    private class Harness(
        val title: AutoTrackTitle?,
        val info: AutoTrackTitleInfo,
        val trackerIds: Map<String, Long>,
        val ratings: Set<String> = setOf("safe", "suggestive"),
        val existingTracks: Set<Long> = emptySet(),
        val searchResults: MutableMap<String, TrackSearch?> = mutableMapOf(),
    ) {
        val fetched = mutableListOf<String>()
        val bound = mutableListOf<Pair<String, Long>>()
        val boundEvents = mutableListOf<Pair<Long, String>>()

        fun binder() = AutoTrackBinder(
            resolveTitle = { _ -> title },
            viewManga = { fetched += it.mdUrl; info },
            searchById = { key, _ -> searchResults[key] },
            tracksOf = { _ -> existingTracks },
            trackerIds = trackerIds,
            ratings = { ratings },
            bind = { key, _, mangaId -> bound += key to mangaId },
            onBound = { mangaId, key -> boundEvents += mangaId to key },
        )
    }

    private fun searchResult(remoteId: Long) = TrackSearch().apply {
        tracker_id = 2L
        remote_id = remoteId
        title = "Remote title"
        status = 1L
        last_chapter_read = 0.0
    }

    @Test
    fun `link present binds exactly one track and emits one event`() = runTest {
        val harness = Harness(
            title = title,
            info = AutoTrackTitleInfo("safe", links("al" to "https://anilist.co/manga/30005")),
            trackerIds = trackerIds,
        )
        harness.searchResults["al"] = searchResult(30005)
        val result = harness.binder().bind(1L, setOf("al"))
        assertEquals(AutoTrackResult.Linked(setOf("al"), emptySet()), result)
        assertEquals(listOf("al" to 1L), harness.bound)
        assertEquals(listOf(1L to "al"), harness.boundEvents)
        assertEquals(1, harness.fetched.size)
    }

    @Test
    fun `binding twice keeps a single track and skips the second fetch`() = runTest {
        val harness = Harness(
            title = title,
            info = AutoTrackTitleInfo("safe", links("al" to "https://anilist.co/manga/30005")),
            trackerIds = trackerIds,
            existingTracks = setOf(2L),
        )
        val result = harness.binder().bind(1L, setOf("al"))
        assertEquals(AutoTrackResult.Linked(emptySet(), emptySet()), result)
        assertTrue(harness.fetched.isEmpty())
        assertTrue(harness.bound.isEmpty())
    }

    @Test
    fun `missing link reports no link once for every requested service`() = runTest {
        val harness = Harness(
            title = title,
            info = AutoTrackTitleInfo("safe", links("mu" to "https://www.mangaupdates.com/series.html?id=9")),
            trackerIds = trackerIds,
        )
        val result = harness.binder().bind(1L, setOf("al", "mal"))
        assertEquals(AutoTrackResult.NoLink(setOf("al", "mal")), result)
        assertTrue(harness.bound.isEmpty())
    }

    @Test
    fun `erotica rating is skipped under default ratings`() = runTest {
        val harness = Harness(
            title = title,
            info = AutoTrackTitleInfo("erotica", links("al" to "https://anilist.co/manga/30005")),
            trackerIds = trackerIds,
        )
        val result = harness.binder().bind(1L, setOf("al"))
        assertEquals(AutoTrackResult.SkippedByRating, result)
        assertEquals(1, harness.fetched.size)
        assertTrue(harness.bound.isEmpty())
    }

    @Test
    fun `merged title binds on the merged parent id`() = runTest {
        val parentTitle = AutoTrackTitle(77L, "https://mangadex.org/title/parent-uuid", source = null)
        val harness = Harness(
            title = parentTitle,
            info = AutoTrackTitleInfo("safe", links("al" to "https://anilist.co/manga/30005")),
            trackerIds = trackerIds,
        )
        harness.searchResults["al"] = searchResult(30005)
        val result = harness.binder().bind(1L, setOf("al"))
        assertEquals(AutoTrackResult.Linked(setOf("al"), emptySet()), result)
        assertEquals(listOf("al" to 77L), harness.bound)
    }

    @Test
    fun `dead tracker link reports a permanent failure`() = runTest {
        val harness = Harness(
            title = title,
            info = AutoTrackTitleInfo("safe", links("al" to "https://anilist.co/manga/30005")),
            trackerIds = trackerIds,
        )
        harness.searchResults["al"] = null
        val result = harness.binder().bind(1L, setOf("al"))
        assertEquals(AutoTrackResult.Failed(retryable = false), result)
        assertTrue(harness.bound.isEmpty())
    }

    @Test
    fun `thrown search failure reports a retryable failure`() = runTest {
        val binder = AutoTrackBinder(
            resolveTitle = { _ -> title },
            viewManga = { AutoTrackTitleInfo("safe", links("al" to "https://anilist.co/manga/30005")) },
            searchById = { _, _ -> error("service unavailable") },
            tracksOf = { emptySet() },
            trackerIds = trackerIds,
            ratings = { setOf("safe", "suggestive") },
            bind = { _, _, _ -> },
        )
        assertEquals(AutoTrackResult.Failed(retryable = true), binder.bind(1L, setOf("al")))
    }
}
