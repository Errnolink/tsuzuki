package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.errnolink.tsuzuki.preference.BrowseLayout
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsBrowseScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsBrowseScreen

    override val displayName = "Browse & sources"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.browse

    @Composable
    override fun Content() = TsuzukiPreferenceContent()

    val prefKeys: Set<String> = setOf(
        "related_mangas",
        "expand_related_mangas",
        "sources_tab_categories",
        "sources_tab_categories_filter",
        "use_new_source_navigation",
        "allow_local_source_hidden_folders",
        "feed_hide_in_library_items",
        "browse_hide_in_library_items",
        "eh_expand_filters",
        "recommends_in_overflow",
        "merge_in_overflow",
        "show_nsfw_source",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val base = SettingsBrowseScreen.getPreferences()
        val groups = base.preferenceGroups()

        val sourcesFirst = groups.firstOrNull { it.title == stringResource(MR.strings.label_sources) }
            ?: groups.getOrNull(0)
        val feed = groups.firstOrNull { it.title == stringResource(SYMR.strings.feed) }
            ?: groups.getOrNull(1)
        val secondSources = groups.mapIndexedNotNull { index, group ->
            if (index != groups.indexOfFirst { it === sourcesFirst } && group.title == stringResource(MR.strings.label_sources)) group else null
        }.firstOrNull()
            ?: groups.getOrNull(2)
        val nsfw = groups.firstOrNull { it.title == stringResource(MR.strings.pref_category_nsfw_content) }
            ?: groups.getOrNull(3)

        val relatedRows = listOfNotNull(
            sourcesFirst?.preferenceItems?.byStoreKey("related_mangas"),
            sourcesFirst?.preferenceItems?.byStoreKey("expand_related_mangas"),
        )
        // Keyless nav row: the only TextPreference inside the first Sources group.
        val editSourceCategories = sourcesFirst?.preferenceItems
            ?.filterIsInstance<Preference.PreferenceItem.TextPreference>()?.firstOrNull()
        val sourceRows = listOfNotNull(
            editSourceCategories,
            sourcesFirst?.preferenceItems?.byStoreKey("sources_tab_categories_filter"),
            sourcesFirst?.preferenceItems?.byStoreKey("use_new_source_navigation"),
            sourcesFirst?.preferenceItems?.byStoreKey("allow_local_source_hidden_folders"),
        )

        val relatedGroup = newGroup(
            title = "Related manga",
            items = relatedRows,
        )

        // Keyless nav row: the last item of the second Sources group (extension repositories).
        val extensionStores = secondSources?.preferenceItems?.lastOrNull()

        val tsuzukiPreferences = remember { Injekt.get<TsuzukiPreferences>() }
        val browseLayout by tsuzukiPreferences.browseLayout().collectAsState()
        val classicOnlyRows = browseLayout == BrowseLayout.CLASSIC

        val feedHideInLibrary = feed?.preferenceItems?.byStoreKey("feed_hide_in_library_items")
            ?.withRetitle("Hide titles already in your library")
        val feedRows = listOfNotNull(
            feed?.preferenceItems?.byStoreKey("hide_latest_tab")?.takeIf { classicOnlyRows },
            feed?.preferenceItems?.byStoreKey("latest_tab_position")?.takeIf { classicOnlyRows },
            feedHideInLibrary,
        )
        val feedGroup = if (feedRows.isNotEmpty()) {
            newGroup(
                title = stringResource(SYMR.strings.feed),
                items = feedRows,
            )
        } else {
            null
        }

        val browseHideInLibrary = secondSources?.preferenceItems?.byStoreKey("browse_hide_in_library_items")
            ?.withRetitle("Hide in-library items in Browse")

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val browseGroup = newGroup(
            title = "Browse",
            items = listOfNotNull(
                browseHideInLibrary,
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.expandFilters(),
                    title = "Expand filter panels",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.recommendsInOverflow(),
                    title = stringResource(SYMR.strings.put_recommends_in_overflow),
                    subtitle = stringResource(SYMR.strings.put_recommends_in_overflow_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.mergeInOverflow(),
                    title = stringResource(SYMR.strings.put_merge_in_overflow),
                    subtitle = stringResource(SYMR.strings.put_merge_in_overflow_summary),
                ),
            ),
        )

        return listOfNotNull(
                relatedGroup,
                if (sourceRows.isNotEmpty() || extensionStores != null) {
                    newGroup(
                        title = stringResource(MR.strings.label_sources),
                        items = sourceRows + listOfNotNull(extensionStores),
                    )
                } else {
                    null
                },
                feedGroup,
                browseGroup,
                nsfw,
            )
    }
}
