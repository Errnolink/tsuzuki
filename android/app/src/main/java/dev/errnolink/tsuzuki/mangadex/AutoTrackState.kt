package dev.errnolink.tsuzuki.mangadex

import exh.md.MangaDexPreferences
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AutoTrackState(private val store: PreferenceStore = Injekt.get()) {
    private fun cursorPref() = store.getLong(CURSOR_KEY, 0L)
    private fun summaryPref() = store.getString(SUMMARY_KEY, "")
    private fun noLinkPref() = store.getStringSet(NO_LINK_KEY, emptySet())

    fun cursor(): Long = cursorPref().get()

    fun setCursor(value: Long) {
        cursorPref().set(value)
    }

    fun summary(): String = summaryPref().get()

    fun setSummary(value: String) {
        summaryPref().set(value)
    }

    fun cacheNoLink(mangaId: Long, services: Collection<String>, nowMs: Long) {
        if (services.isEmpty()) return
        noLinkPref().set(noLinkPref().get() + services.map { encodeNoLink(mangaId, it, nowMs) })
    }

    fun activeNoLink(nowMs: Long): Map<Long, Set<String>> = activeNoLinkPairs(noLinkPref().get(), nowMs)

    companion object {
        const val CURSOR_KEY = "tsuzuki_auto_track_cursor"
        const val SUMMARY_KEY = "tsuzuki_auto_track_summary"
        const val NO_LINK_KEY = "tsuzuki_auto_track_nolink"
        const val NO_LINK_TTL_MS = 30L * 24 * 60 * 60 * 1000

        fun encodeNoLink(mangaId: Long, service: String, nowMs: Long) = "$mangaId|$service|$nowMs"

        fun parseNoLink(entry: String): Triple<Long, String, Long>? {
            val parts = entry.split("|")
            if (parts.size != 3) return null
            val mangaId = parts[0].toLongOrNull() ?: return null
            val at = parts[2].toLongOrNull() ?: return null
            return Triple(mangaId, parts[1], at)
        }

        fun activeNoLinkPairs(entries: Set<String>, nowMs: Long, ttlMs: Long = NO_LINK_TTL_MS): Map<Long, Set<String>> =
            entries.mapNotNull(::parseNoLink)
                .filter { (_, _, at) -> nowMs - at < ttlMs }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
    }
}

data class AutoTrackCandidate(
    val mangaId: Long,
    val services: Set<String>,
)

fun remainingAfter(candidates: List<AutoTrackCandidate>, cursorId: Long): List<AutoTrackCandidate> {
    val index = candidates.indexOfFirst { it.mangaId == cursorId }
    return if (index < 0) candidates else candidates.drop(index + 1)
}

fun buildCandidates(
    favorites: List<Pair<Long, Long>>,
    mergedMdChildIds: Set<Long>,
    mdSourceIds: Set<Long>,
    mergedSourceId: Long,
    tracksByMangaId: Map<Long, Set<Long>>,
    trackerIdsByService: Map<String, Long>,
    signedIn: Set<String>,
    disabled: Set<String>,
    activeNoLink: Map<Long, Set<String>>,
): List<AutoTrackCandidate> {
    val wanted = MangaDexPreferences.effectiveServices(signedIn, disabled)
    return favorites.mapNotNull { (id, source) ->
        val isMangaDex = source in mdSourceIds || (source == mergedSourceId && id in mergedMdChildIds)
        if (!isMangaDex) return@mapNotNull null
        val tracked = tracksByMangaId[id] ?: emptySet()
        val missing = wanted.filter { service -> trackerIdsByService[service] !in tracked } -
            (activeNoLink[id] ?: emptySet())
        if (missing.isEmpty()) null else AutoTrackCandidate(id, missing.toSet())
    }.sortedBy { it.mangaId }
}

fun groupedCount(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

fun buildSummary(
    linkedByService: Map<String, Int>,
    alreadyLinked: Int,
    noLinkTitles: Int,
    skippedByRating: Int,
): String {
    val parts = mutableListOf<String>()
    val linkedParts = MangaDexPreferences.serviceCodes
        .mapNotNull { code -> linkedByService[code]?.takeIf { it > 0 }?.let { code to it } }
        .map { (code, count) -> "${groupedCount(count)} titles to ${MangaDexPreferences.serviceNames.getValue(code)}" }
    parts += if (linkedParts.isEmpty()) "No titles linked" else "Linked " + linkedParts.joinToString(" and ")
    if (alreadyLinked > 0) parts += "${groupedCount(alreadyLinked)} already linked"
    if (noLinkTitles > 0) parts += "${groupedCount(noLinkTitles)} have no link on MangaDex"
    if (skippedByRating > 0) parts += "${groupedCount(skippedByRating)} skipped by content rating"
    return parts.joinToString(". ") + "."
}

internal class AutoTrackPacer(private val intervalMs: Long) {
    private var first = true

    suspend fun gate() {
        if (first) {
            first = false
        } else {
            delay(intervalMs)
        }
    }
}
