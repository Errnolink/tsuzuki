package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import tachiyomi.domain.manga.model.Manga
import kotlinx.serialization.protobuf.ProtoBuf
import java.security.MessageDigest

/**
 * Per-manga state as of the last acknowledged push, taken from the snapshot that
 * produced the ack. Manga whose version, progress-bearing chapter versions and
 * chapter count all still match are metadata-only churn (fetch-interval stamps,
 * title/thumbnail edits) and ride the 24 h full instead of the delta (R3).
 */
internal data class PushedManga(
    val version: Long,
    val maxChapterVersion: Long,
    val chapterCount: Int,
)

internal fun pushedMangaKey(source: Long, url: String) = "$source|$url"

internal fun encodePushedManga(states: Map<String, PushedManga>): String =
    states.entries.joinToString("\u001e") { (key, state) ->
        "$key\u001f${state.version}\u001f${state.maxChapterVersion}\u001f${state.chapterCount}"
    }

internal fun decodePushedManga(encoded: String): Map<String, PushedManga> {
    if (encoded.isEmpty()) return emptyMap()
    val states = LinkedHashMap<String, PushedManga>()
    for (entry in encoded.split('\u001e')) {
        val fields = entry.split('\u001f')
        if (fields.size < 4) continue
        val version = fields[1].toLongOrNull() ?: continue
        val maxChapterVersion = fields[2].toLongOrNull() ?: continue
        val chapterCount = fields[3].toIntOrNull() ?: continue
        states[fields[0]] = PushedManga(version, maxChapterVersion, chapterCount)
    }
    return states
}

internal fun selectSyncDelta(backup: Backup, since: Long, pushed: Map<String, PushedManga> = emptyMap()): Backup = backup.copy(
    backupManga = backup.backupManga.mapNotNull { manga ->
        val sinceChapters = manga.chapters.filter { it.lastModifiedAt >= since }
        val ack = pushed[pushedMangaKey(manga.source, manga.url)]
        val keep = when (ack) {
            // Pre-guard fallback: no acked state for this manga yet.
            null -> manga.lastModifiedAt >= since || sinceChapters.isNotEmpty()
            else ->
                manga.version > ack.version || // favourite/description/url change
                    sinceChapters.any { it.version > ack.maxChapterVersion } || // read/bookmark/progress
                    manga.chapters.size > ack.chapterCount // new chapters (b28 insert stamp)
        }
        if (keep) manga.copy(chapters = sinceChapters) else null
    },
)

internal data class SyncSections(val backup: Backup, val digests: Set<String>)

internal fun selectSyncSections(backup: Backup, previous: Set<String>, full: Boolean): SyncSections {
    val sections = listOf(
        "preferences" to Backup(backupPreferences = backup.backupPreferences),
        "sources" to Backup(backupSourcePreferences = backup.backupSourcePreferences),
        "stores" to Backup(backupExtensionStores = backup.backupExtensionStores),
        "searches" to Backup(backupSavedSearches = backup.backupSavedSearches),
        "feeds" to Backup(backupFeeds = backup.backupFeeds),
        "categories" to Backup(backupCategories = backup.backupCategories),
        "extensions" to Backup(wantedExtensions = backup.wantedExtensions),
    )
    val digests = sections.map { (name, section) ->
        name + ":" + MessageDigest.getInstance("SHA-256")
            .digest(ProtoBuf.encodeToByteArray(Backup.serializer(), section))
            .joinToString("") { "%02x".format(it) }
    }.toSet()
    fun changed(name: String) = full || digests.first { it.startsWith("$name:") } !in previous
    return SyncSections(
        backup.copy(
            backupPreferences = backup.backupPreferences.takeIf { changed("preferences") }.orEmpty(),
            backupSourcePreferences = backup.backupSourcePreferences.takeIf { changed("sources") }.orEmpty(),
            backupExtensionStores = backup.backupExtensionStores.takeIf { changed("stores") }.orEmpty(),
            backupSavedSearches = backup.backupSavedSearches.takeIf { changed("searches") }.orEmpty(),
            backupFeeds = backup.backupFeeds.takeIf { changed("feeds") }.orEmpty(),
        ),
        digests,
    )
}

/**
 * Everything except the hub's own seq that a sync would push. `localStamps` covers the
 * manga/chapter watermark filter, `digestsChanged` the always-ridden non-manga sections
 * (categories, app/source settings, stores, searches, feeds, wanted extensions).
 */
internal data class SyncWorkState(
    val fullDue: Boolean,
    val tombstonesPending: Boolean,
    val localStamps: Boolean,
    val digestsChanged: Boolean,
)

/**
 * R2 idle skip: the hub's version equals our cursor (nothing to pull) and we have
 * nothing to push, so the backup build and merge request are skipped entirely.
 */
internal fun syncIsSkippable(hubVersion: Long, cursor: Long, work: SyncWorkState): Boolean =
    hubVersion == cursor && !work.fullDue && !work.tombstonesPending && !work.localStamps && !work.digestsChanged

/**
 * Whether a manga rides a delta sync: local work since the last acked push. Either the
 * manga row itself was touched after the push watermark, or any of its chapters carry
 * a stamp (reads and progress update chapter rows only). Every keep selectSyncDelta
 * can decide from acked state implies one of these stamps, so a candidate chosen here
 * is never dropped by the trim; the trim may still drop further per chapter.
 */
internal fun isDeltaCandidate(manga: Manga, since: Long, hasChapterStamp: Boolean): Boolean =
    manga.lastModifiedAt >= since || hasChapterStamp

/**
 * R1/R4: the phone sends `X-Sync-Caps: chapter-delta` only when the hub advertised
 * the capability during the cached capabilities probe.
 */
internal fun chapterDeltaCapEnabled(cachedCaps: String): Boolean =
    cachedCaps.split(',').any { it.trim() == "chapter-delta" }
const val CAPABILITIES_PROBE_TTL_MS = 24 * 60 * 60 * 1000L

fun shouldReprobeCapabilities(
    lastProbedAt: Long,
    now: Long,
    lastCursor: Long = 0L,
    responseCursor: Long = 0L,
    seqJumpThreshold: Long = 1000L,
): Boolean {
    if (lastProbedAt <= 0L) return true
    if (now - lastProbedAt >= CAPABILITIES_PROBE_TTL_MS) return true
    if (responseCursor > 0L && lastCursor > 0L && responseCursor - lastCursor >= seqJumpThreshold) return true
    return false
}
