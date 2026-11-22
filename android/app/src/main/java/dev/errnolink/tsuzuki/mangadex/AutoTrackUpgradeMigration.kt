package dev.errnolink.tsuzuki.mangadex

import exh.md.MangaDexPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AutoTrackUpgradeMigration(
    private val store: PreferenceStore = Injekt.get(),
) : Migration {
    override val version: Float = 37f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferences = MangaDexPreferences(store)
        val optedIn = store.getStringSet("mangadex_auto_track_services", emptySet()).get()
        if (optedIn.isEmpty() && !preferences.autoTrackDisabled().isSet()) {
            preferences.autoTrackDisabled().set(MangaDexPreferences.serviceCodes)
        }
        return true
    }
}
