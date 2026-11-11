package dev.errnolink.tsuzuki.ui.settings

import eu.kanade.presentation.more.settings.Preference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference as CorePreference
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

class SettingsIaTest {

    @Test
    fun `root lists the eleven IA sections in the designed order`() {
        val screens = iaSections.map { it.screen }
        val expectedTitles = listOf(
            MR.strings.pref_category_appearance,
            MR.strings.pref_category_library,
            SYMR.strings.pref_category_mangadex,
            MR.strings.pref_category_reader,
            MR.strings.pref_category_downloads,
            MR.strings.label_data_storage,
            MR.strings.browse,
            MR.strings.pref_category_tracking,
            MR.strings.pref_category_security,
            MR.strings.pref_category_advanced,
            MR.strings.pref_category_about,
        )
        assertEquals(expectedTitles, iaSections.map { it.titleRes })
        assertEquals(expectedTitles.size, iaSections.size)
        assertEquals(screens.size, screens.toSet().size)
        assertEquals(expectedTitles.last(), iaSections.last().titleRes)
        assertTrue(iaGroups.flatten() == iaSections)
    }

    @Test
    fun `every mapped pref key is reachable from exactly one screen`() {
        val manifests: List<Pair<String, Set<String>>> = listOf(
            "Appearance" to TsuzukiSettingsAppearanceScreen.prefKeys,
            "Library" to TsuzukiSettingsLibraryScreen.prefKeys,
            "Reader" to TsuzukiSettingsReaderScreen.prefKeys,
            "Downloads & storage" to TsuzukiSettingsDownloadScreen.prefKeys,
            "MangaDex" to TsuzukiSettingsMangadexScreen.prefKeys,
            "Trackers" to TsuzukiSettingsTrackingScreen.prefKeys,
            "Browse & sources" to TsuzukiSettingsBrowseScreen.prefKeys,
            "Backup & sync" to TsuzukiSettingsDataScreen.prefKeys,
            "Security & privacy" to TsuzukiSettingsSecurityScreen.prefKeys,
            "Advanced" to TsuzukiSettingsAdvancedScreen.prefKeys,
        )

        val seen = HashMap<String, String>()
        manifests.forEach { (screen, keys) ->
            keys.forEach { key ->
                val owner = seen.put(key, screen)
                assertTrue(owner == null, "key $key reachable from both $owner and $screen")
            }
        }

        mappingTableKeys.forEach { key ->
            assertTrue(seen.containsKey(key), "mapping key $key missing from every screen manifest")
        }
    }

    @Test
    fun `app state backed preference rows match by their bare store key`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getBoolean(CorePreference.appStateKey("pref_show_updating_progress_banner_key"), true)
        val row = Preference.PreferenceItem.SwitchPreference(
            preference = pref,
            title = "Show updating progress banner",
        )
        @Suppress("UNCHECKED_CAST")
        val rows = listOf(row) as List<Preference.PreferenceItem<out Any, out Any>>
        assertEquals(row, rows.byStoreKey("pref_show_updating_progress_banner_key"))
    }

    @Test
    fun `renamed sections expose a display name override`() {
        assertEquals("Downloads & storage", TsuzukiSettingsDownloadScreen.displayName)
        assertEquals("Backup & sync", TsuzukiSettingsDataScreen.displayName)
        assertEquals("Browse & sources", TsuzukiSettingsBrowseScreen.displayName)
        assertEquals("Trackers", TsuzukiSettingsTrackingScreen.displayName)
        assertEquals("Security & privacy", TsuzukiSettingsSecurityScreen.displayName)
        assertEquals(null, TsuzukiSettingsAppearanceScreen.displayName)
    }

    private val mappingTableKeys = setOf(
        // Appearance / Library (doc-04 mapping, must-not-drop set)
        "pref_theme_mode_key", "pref_navigation_style", "pref_show_updates_button",
        "pref_show_history_button", "default_category", "categorized_display",
        "app_date_format", "relative_time_v2", "pref_render_images_description",
        "pref_library_update_interval_key", "library_update_restriction", "library_update_categories",
        "group_library_update_type", "auto_update_metadata", "library_update_manga_restriction",
        "library_show_updates_count", "pref_show_updating_progress_banner_key",
        "pref_chapter_swipe_start_action", "pref_chapter_swipe_end_action",
        "mark_duplicate_read_chapter_read", "pref_hide_missing_chapter_indicators",
        "show_empty_categories_search", "fetch_metadata_on_add", "fetch_chapters_on_add",
        // Reader hard requirements
        "pref_default_reading_mode_key", "pref_default_orientation_type_key", "reader_volume_keys",
        // Downloads
        "pref_download_only_over_wifi_key", "save_chapter_as_cbz", "download_new",
        // Browse
        "show_nsfw_source", "eh_expand_filters", "recommends_in_overflow", "merge_in_overflow",
        // Backup & sync credentials and triggers
        "storage_dir", "backup_interval", "sync_service",
        "connection_sync_client_host", "connection_sync_client_api_key",
        "connection_webdav_url", "connection_webdav_username", "connection_webdav_password",
        "connection_webdav_folder", "sync_interval",
        // Security hard requirements
        "use_biometric_lock", "lock_app_after", "secure_screen_v2", "cbz_password",
        "password_protect_downloads",
        // MangaDex core block
        "preferred_mangaDex_id", "reading_sync_bool", "auto_add_to_mangadex_library",
        "mangadex_chapter_languages", "blocked_scanlators", "blocked_uploaders",
        // Advanced
        "doh_provider", "default_user_agent", "download_include_chapter_url_hash",
    )
}
