package dev.errnolink.tsuzuki.update

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UpdatePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun autoUpdate(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_auto_update_app", true)

    fun lastAppCheck(): Preference<Long> =
        preferenceStore.getLong("pref_last_app_update_check", 0L)
}
