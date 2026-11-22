package dev.errnolink.tsuzuki.mangadex

import exh.md.MangaDexPreferences
import kotlinx.coroutines.test.runTest
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class AutoTrackUpgradeMigrationTest {

    private class TestStore : PreferenceStore by InMemoryPreferenceStore() {
        private val sets = mutableMapOf<String, Preference<Set<String>>>()
        override fun getStringSet(key: String, defaultValue: Set<String>) =
            sets.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    }

    @Test
    fun `an upgrader who never opted in keeps every tracker off`() = runTest {
        val store = TestStore()

        AutoTrackUpgradeMigration(store).invoke(MigrationContext(dryrun = false))

        assertEquals(emptySet<String>(), MangaDexPreferences(store).autoTrackEnabledServices().get())
    }

    @Test
    fun `an upgrader who opted in keeps exactly the trackers they picked`() = runTest {
        val store = TestStore()
        store.getStringSet("mangadex_auto_track_services", emptySet()).set(setOf("al"))
        val preferences = MangaDexPreferences(store)

        AutoTrackUpgradeMigration(store).invoke(MigrationContext(dryrun = false))
        preferences.migrateAutoTrackSelection()

        assertEquals(setOf("al"), preferences.autoTrackEnabledServices().get())
    }

    @Test
    fun `a choice already made on the new opt-out setting is left alone`() = runTest {
        val store = TestStore()
        val preferences = MangaDexPreferences(store)
        preferences.autoTrackDisabled().set(setOf("mal"))

        AutoTrackUpgradeMigration(store).invoke(MigrationContext(dryrun = false))

        assertEquals(MangaDexPreferences.serviceCodes - "mal", preferences.autoTrackEnabledServices().get())
    }
}
