package dev.errnolink.tsuzuki.preference

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

enum class FeedLayout {
    NEW,
    CLASSIC,
}

enum class BrowseLayout {
    HYBRID,
    DISCOVER,
    CLASSIC,
}

class TsuzukiPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun feedLayout(): Preference<FeedLayout> =
        preferenceStore.getEnum("pref_feed_layout", FeedLayout.NEW)

    fun browseLayout(): Preference<BrowseLayout> =
        preferenceStore.getEnum("pref_browse_layout", BrowseLayout.HYBRID)

    fun motionStandardTokens(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_layout_motion_tokens", true)

    fun largeTitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_layout_large_titles", true)

    fun detailsAppleHeader(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_layout_details_apple_header", false)

    fun nullRepairLastVersionCode(): Preference<Int> =
        preferenceStore.getInt("pref_null_repair_last_version_code", 0)
}