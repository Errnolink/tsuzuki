// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md

import exh.md.utils.FollowStatus
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexPreferences(private val store: PreferenceStore = Injekt.get()) {
    fun readingSync() = store.getBoolean("reading_sync_bool", false)
    fun autoAddToLibrary() = store.getInt("auto_add_to_mangadex_library", 0)
    fun visibleRatings() = store.getStringSet("content_rating_options", setOf("safe", "suggestive"))
    fun showRatingFilter() = store.getBoolean("show_R18_filter", true)
    fun includeUnavailable() = store.getBoolean("include_unavailable", false)
    fun multiLanguage() = store.getBoolean("mangadex_multi_language", false)
    fun chapterLanguages() = store.getStringSet("mangadex_chapter_languages", setOf("en"))
    fun followsSyncEnabled() = store.getBoolean("mangadex_follows_sync_enabled", false)
    fun followsPullStatuses() = store.getStringSet("mangadex_follows_pull_statuses", setOf(FollowStatus.READING.toDex()))
    fun mangaLanguages(id: String) = store.getStringSet("mangadex_languages_$id", emptySet())
    fun blockedGroups() = store.getStringSet("blocked_scanlators", emptySet())
    fun blockedUploaders() = store.getStringSet("blocked_uploaders", emptySet())
    fun blockName(id: String) = store.getString("mangadex_block_name_$id", id)
    fun autoTrackServices() = store.getStringSet("mangadex_auto_track_services", emptySet())
    fun autoTrackRatings() = store.getStringSet("mangadex_auto_track_ratings", setOf("safe", "suggestive"))
    fun dynamicCovers() = store.getBoolean("mangadex_dynamic_covers", false)
    fun manualCover(mangaId: Long) = store.getBoolean("mangadex_manual_cover_$mangaId", false)

    fun coverQuality() = store.getInt("mangadex_cover_quality", exh.md.utils.MangaDexCoverQuality.AUTOMATIC.value)
    fun dynamicCoverUrl(mangaId: String) = store.getString("mangadex_dynamic_cover_$mangaId", "")
    fun languages(sourceLanguage: String, mangaId: String? = null): List<String> {
        if (!multiLanguage().get()) return listOf(sourceLanguage)
        val enabled = chapterLanguages().get().ifEmpty { setOf(sourceLanguage) }
        val selected = mangaId?.let { mangaLanguages(it).get() }.orEmpty()
        return (if (selected.isEmpty()) enabled else enabled.intersect(selected)).sorted()
    }

    companion object {
        val ratings = listOf("safe", "suggestive", "erotica", "pornographic")

        fun autoFollowStatus(value: Int): FollowStatus? = when (value) {
            1 -> FollowStatus.PLAN_TO_READ
            2 -> FollowStatus.ON_HOLD
            3 -> FollowStatus.READING
            else -> null
        }
    }
}
