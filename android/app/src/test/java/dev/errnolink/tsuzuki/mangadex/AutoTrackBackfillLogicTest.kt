package dev.errnolink.tsuzuki.mangadex

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.concurrent.TimeUnit

class AutoTrackBackfillLogicTest {

    private class TestStore : PreferenceStore by InMemoryPreferenceStore() {
        private val sets = mutableMapOf<String, Preference<Set<String>>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val strings = mutableMapOf<String, Preference<String>>()
        override fun getStringSet(key: String, defaultValue: Set<String>) =
            sets.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
        override fun getLong(key: String, defaultValue: Long) =
            longs.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
        override fun getString(key: String, defaultValue: String) =
            strings.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    }

    private val mdIds = setOf(2499283573021220255L)
    private val mergedId = 6969L
    private val trackerIdsByService = mapOf("al" to 2L, "mal" to 1L, "kt" to 3L, "mu" to 7L)

    private fun candidateInput(id: Long, source: Long) = id to source

    @Test
    fun `candidates keep only MangaDex titles missing a signed-in service`() {
        val favorites = listOf(
            candidateInput(1, 2499283573021220255L),
            candidateInput(2, 999L),
            candidateInput(3, mergedId),
            candidateInput(4, 2499283573021220255L),
        )
        val candidates = buildCandidates(
            favorites = favorites,
            mergedMdChildIds = setOf(3L),
            mdSourceIds = mdIds,
            mergedSourceId = mergedId,
            tracksByMangaId = mapOf(4L to setOf(2L)),
            trackerIdsByService = trackerIdsByService,
            signedIn = setOf("al"),
            disabled = emptySet(),
            activeNoLink = emptyMap(),
        )
        assertEquals(listOf(1L, 3L), candidates.map { it.mangaId })
        assertEquals(setOf("al"), candidates.single { it.mangaId == 1L }.services)
    }

    @Test
    fun `cached no link pairs are excluded until thirty days pass`() {
        val now = System.currentTimeMillis()
        val favorites = listOf(candidateInput(1, 2499283573021220255L), candidateInput(2, 2499283573021220255L))
        val active = AutoTrackState.activeNoLinkPairs(
            entries = setOf(
                AutoTrackState.encodeNoLink(1L, "al", now - TimeUnit.DAYS.toMillis(10)),
                AutoTrackState.encodeNoLink(2L, "al", now - TimeUnit.DAYS.toMillis(31)),
            ),
            nowMs = now,
        )
        val candidates = buildCandidates(
            favorites = favorites,
            mergedMdChildIds = emptySet(),
            mdSourceIds = mdIds,
            mergedSourceId = mergedId,
            tracksByMangaId = emptyMap(),
            trackerIdsByService = trackerIdsByService,
            signedIn = setOf("al"),
            disabled = emptySet(),
            activeNoLink = active,
        )
        assertEquals(emptyList<AutoTrackCandidate>(), candidates.filter { it.mangaId == 1L })
        assertEquals(setOf("al"), candidates.single { it.mangaId == 2L }.services)
    }

    @Test
    fun `partially cached titles keep only their uncached services`() {
        val now = System.currentTimeMillis()
        val active = AutoTrackState.activeNoLinkPairs(
            entries = setOf(AutoTrackState.encodeNoLink(1L, "al", now)),
            nowMs = now,
        )
        val candidates = buildCandidates(
            favorites = listOf(candidateInput(1, 2499283573021220255L)),
            mergedMdChildIds = emptySet(),
            mdSourceIds = mdIds,
            mergedSourceId = mergedId,
            tracksByMangaId = emptyMap(),
            trackerIdsByService = trackerIdsByService,
            signedIn = setOf("al", "mu"),
            disabled = emptySet(),
            activeNoLink = active,
        )
        assertEquals(setOf("mu"), candidates.single().services)
    }

    @Test
    fun `resume after the cursor skips processed candidates`() {
        val candidates = (1L..120L).map { AutoTrackCandidate(it, setOf("al")) }
        val cursor = candidates[49].mangaId
        val remaining = remainingAfter(candidates, cursor)
        assertEquals(51L, remaining.first().mangaId)
        assertEquals(70, remaining.size)
    }

    @Test
    fun `a cursor outside the candidate list restarts from the top`() {
        val candidates = (1L..3L).map { AutoTrackCandidate(it, setOf("al")) }
        assertEquals(1L, remainingAfter(candidates, 40L).first().mangaId)
        assertEquals(1L, remainingAfter(candidates, 0L).first().mangaId)
    }

    @Test
    fun `fetches are spaced at least one second apart`() = runTest {
        val fetchTimes = mutableListOf<Long>()
        val pacer = AutoTrackPacer(1000L)
        for (i in 0 until 3) {
            pacer.gate()
            fetchTimes += testScheduler.currentTime
        }
        assertEquals(listOf(0L, 1000L, 2000L), fetchTimes)
    }

    @Test
    fun `cache writes become visible then expire`() {
        val state = AutoTrackState(TestStore())
        val now = 1_000_000L
        state.cacheNoLink(1L, setOf("al", "mal"), now)
        assertEquals(setOf("al", "mal"), state.activeNoLink(now + 1)[1L])
        assertTrue(state.activeNoLink(now + AutoTrackState.NO_LINK_TTL_MS + 1).isEmpty())
    }

    @Test
    fun `summary reports linked already linked no link and rating skips`() {
        val summary = buildSummary(
            linkedByService = mapOf("al" to 412, "mal" to 3),
            alreadyLinked = 1580,
            noLinkTitles = 64,
            skippedByRating = 12,
        )
        assertEquals(
            "Linked 412 titles to AniList and 3 titles to MyAnimeList. 1,580 already linked. 64 have no link on MangaDex. 12 skipped by content rating.",
            summary,
        )
        val empty = buildSummary(emptyMap(), 0, 0, 0)
        assertEquals("No titles linked.", empty)
    }

    @Test
    fun `no link entries round trip`() {
        val encoded = AutoTrackState.encodeNoLink(7L, "kt", 5L)
        assertEquals(Triple(7L, "kt", 5L), AutoTrackState.parseNoLink(encoded))
        assertEquals(null, AutoTrackState.parseNoLink("nonsense"))
    }
}
