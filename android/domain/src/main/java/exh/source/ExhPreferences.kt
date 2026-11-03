package exh.source

import exh.log.EHLogLevel
import exh.log.EHLogLevel.Companion.EH_LOG_LEVEL_PREF
import tachiyomi.core.common.preference.PreferenceStore

class ExhPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // KMK -->
    fun logLevel(isDebugBuildType: Boolean) = preferenceStore.getInt(EH_LOG_LEVEL_PREF, EHLogLevel.defaultLogLevel(isDebugBuildType))
    // KMK <--
}
