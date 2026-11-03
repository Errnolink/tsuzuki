package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsLibraryScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsLibraryScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    override fun Content() = TsuzukiPreferenceContent()

    val prefKeys: Set<String> = setOf(
        "default_category",
        "categorized_display",
        "pref_library_update_interval_key",
        "library_update_restriction",
        "library_update_categories",
        "group_library_update_type",
        "auto_update_metadata",
        "library_update_manga_restriction",
        "library_show_updates_count",
        "pref_show_updating_progress_banner_key",
        "pref_chapter_swipe_start_action",
        "pref_chapter_swipe_end_action",
        "mark_duplicate_read_chapter_read",
        "pref_hide_missing_chapter_indicators",
        "show_empty_categories_search",
        "fetch_metadata_on_add",
        "fetch_chapters_on_add",
        "pref_render_images_description",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val base = SettingsLibraryScreen.getPreferences()
        val groups = base.preferenceGroups()
        val categoriesGroup = groups.firstOrNull { it.title == stringResource(MR.strings.categories) }
            ?: groups.getOrNull(0)
        val updateGroup = groups.firstOrNull { it.title == stringResource(MR.strings.pref_category_library_update) }
            ?: groups.getOrNull(1)
        val behaviorGroup = groups.firstOrNull { it.title == stringResource(MR.strings.pref_behavior) }
            ?: groups.getOrNull(2)
        val sortingGroup = groups.firstOrNull { it.title == stringResource(SYMR.strings.pref_sorting_settings) }

        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        // Keyless nav row: the only TextPreference inside the Categories group.
        val editCategories = categoriesGroup?.preferenceItems
            ?.filterIsInstance<Preference.PreferenceItem.TextPreference>()?.firstOrNull()
        val defaultCategory = categoriesGroup?.preferenceItems?.byStoreKey("default_category")
        val perCategoryLayout = categoriesGroup?.preferenceItems?.byStoreKey("categorized_display")
            ?.withRetitle("Per-category layout")
            ?.withSubtitle("Each category can keep its own sort and filters")
        // Keyless nav row: the sole item of the SY sorting group.
        val tagSorting = sortingGroup?.preferenceItems?.firstOrNull()?.let { item ->
            (item as? Preference.PreferenceItem.TextPreference)
                ?.copy(title = "Tag priority", subtitle = "Prioritise entries that match your tags")
                ?: item
        }

        val categoriesSection = newGroup(
            title = stringResource(MR.strings.categories),
            items = listOfNotNull(
                editCategories,
                defaultCategory,
                perCategoryLayout,
                tagSorting,
            ),
        )

        val detailsSection = Preference.PreferenceGroup(
            title = "Details",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.imagesInDescription(),
                    title = stringResource(MR.strings.pref_display_images_description),
                ),
            ),
        )

        // Keyless action row: the only TextPreference inside the update group (opens the
        // categories tri-state dialog).
        val updateItems = updateGroup?.preferenceItems
        val interval = updateItems?.byStoreKey("pref_library_update_interval_key")
        val restriction = updateItems?.byStoreKey("library_update_restriction")?.withRetitle("Update only on")
        val updateCategories = updateItems
            ?.filterIsInstance<Preference.PreferenceItem.TextPreference>()?.firstOrNull()
            ?.withRetitle("Categories to update")
        val groupUpdates = updateItems?.byStoreKey("group_library_update_type")
            ?.withRetitle("Category update behavior")
        val refreshMetadata = updateItems?.byStoreKey("auto_update_metadata")
        val smartUpdate = updateItems?.byStoreKey("library_update_manga_restriction")
            ?.withRetitle("Skip chapters you won't read")
        val updatesBadge = updateItems?.byStoreKey("library_show_updates_count")
        val updatingBanner = updateItems?.byStoreKey("pref_show_updating_progress_banner_key")

        val updateSection = newGroup(
            title = "Library updates",
            items = listOfNotNull(
                interval,
                restriction,
                updateCategories,
                groupUpdates,
                refreshMetadata,
                smartUpdate,
                updatesBadge,
                updatingBanner,
            ),
        )

        val behaviorSection = newGroup(
            title = "Behavior",
            items = behaviorGroup?.preferenceItems.orEmpty()
                .retitledWhere("pref_chapter_swipe_start_action", "Swipe left on a chapter")
                .retitledWhere("pref_chapter_swipe_end_action", "Swipe right on a chapter")
                .retitledWhere("mark_duplicate_read_chapter_read", "Mark duplicate chapters as read"),
        )

        return listOf(
            categoriesSection,
            detailsSection,
            updateSection,
            behaviorSection,
        )
    }
}
