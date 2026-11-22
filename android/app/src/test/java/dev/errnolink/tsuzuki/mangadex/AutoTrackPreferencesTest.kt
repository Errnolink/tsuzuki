package dev.errnolink.tsuzuki.mangadex

import exh.md.MangaDexPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class AutoTrackPreferencesTest {

    private class TestStore : PreferenceStore by InMemoryPreferenceStore() {
        private val sets = mutableMapOf<String, Preference<Set<String>>>()
        override fun getStringSet(key: String, defaultValue: Set<String>) =
            sets.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    }

    private fun storeWithOldSelection(selection: Set<String>): Pair<TestStore, MangaDexPreferences> {
        val store = TestStore()
        store.getStringSet("mangadex_auto_track_services", emptySet()).set(selection)
        return store to MangaDexPreferences(store)
    }

    @Test
    fun `nothing stored with AniList signed in enables AniList`() {
        assertEquals(setOf("al"), MangaDexPreferences.effectiveServices(signedIn = setOf("al"), disabled = emptySet()))
    }

    @Test
    fun `old selected AniList keeps MyAnimeList off after migration`() {
        val (store, preferences) = storeWithOldSelection(setOf("al"))
        preferences.migrateAutoTrackSelection()
        assertEquals(setOf("mal", "kt", "mu"), preferences.autoTrackDisabled().get())
        assertTrue(store.getStringSet("mangadex_auto_track_services", emptySet()).get().isEmpty())
        assertEquals(
            setOf("al"),
            MangaDexPreferences.effectiveServices(signedIn = setOf("al", "mal"), disabled = preferences.autoTrackDisabled().get()),
        )
    }

    @Test
    fun `disabled AniList leaves nothing enabled for an AniList-only account`() {
        assertEquals(emptySet<String>(), MangaDexPreferences.effectiveServices(signedIn = setOf("al"), disabled = setOf("al")))
    }

    @Test
    fun `migration without a stored selection does nothing`() {
        val preferences = MangaDexPreferences(TestStore())
        preferences.migrateAutoTrackSelection()
        assertEquals(emptySet<String>(), preferences.autoTrackDisabled().get())
    }

    @Test
    fun `empty old selection keeps the new default`() {
        val (_, preferences) = storeWithOldSelection(emptySet())
        preferences.migrateAutoTrackSelection()
        assertEquals(emptySet<String>(), preferences.autoTrackDisabled().get())
        assertEquals(MangaDexPreferences.serviceCodes, preferences.autoTrackEnabledServices().get())
    }

    @Test
    fun `enabled services preference writes the complement into the disabled set`() {
        val preferences = MangaDexPreferences(TestStore())
        preferences.autoTrackEnabledServices().set(setOf("al", "mal"))
        assertEquals(setOf("kt", "mu"), preferences.autoTrackDisabled().get())
        assertEquals(setOf("al", "mal"), preferences.autoTrackEnabledServices().get())
    }
}
