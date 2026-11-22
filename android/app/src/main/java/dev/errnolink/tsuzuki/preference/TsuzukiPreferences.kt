package dev.errnolink.tsuzuki.preference

import dev.errnolink.tsuzuki.util.TimeFormat
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

enum class DetailsHeader {
    CENTERED,
    CLASSIC,
}

class TsuzukiPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun libraryProgressRing(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_library_progress_ring", true)

    fun detailsHeader(): Preference<DetailsHeader> =
        preferenceStore.getEnum("pref_details_header", DetailsHeader.CENTERED)

    fun coverColourCard(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_cover_colour_card", true)

    /** "Colours from wallpaper" — page, surfaces, text and controls follow the Material You scheme (API 31+). */
    fun coloursFromWallpaper(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_colours_from_wallpaper", true)

    fun smartUpdate(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_library_smart_update", false)

    fun nullRepairLastVersionCode(): Preference<Int> =
        preferenceStore.getInt("pref_null_repair_last_version_code", 0)

    fun timeFormat(): Preference<TimeFormat> =
        preferenceStore.getEnum("app_time_format", TimeFormat.SYSTEM)
}