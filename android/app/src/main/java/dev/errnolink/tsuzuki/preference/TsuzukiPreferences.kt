package dev.errnolink.tsuzuki.preference

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
        preferenceStore.getBoolean("pref_cover_colour_card", false)

    fun smartUpdate(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_library_smart_update", false)

    fun nullRepairLastVersionCode(): Preference<Int> =
        preferenceStore.getInt("pref_null_repair_last_version_code", 0)
}