package dev.errnolink.tsuzuki.ui.settings
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.icerock.moko.resources.StringResource
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Compass
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.Palette
import dev.errnolink.tsuzuki.designsystem.TrendingUp
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.Gear
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import exh.assets.EhAssets

import exh.assets.ehassets.MangadexLogo
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

object TsuzukiSettingsMainScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsMainScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val state = rememberLazyListState()

        SettingsScaffold(
            title = stringResource(MR.strings.label_settings),
            navigateUp = backPress::invoke,
            state = state,
            onTitleClick = { navigator.push(TsuzukiSettingsSearchScreen()) },
            actions = listOf(
                ShellTopAction(stringResource(MR.strings.action_search), TsuzukiIcons.Search, {
                    navigator.push(TsuzukiSettingsSearchScreen())
                }),
            ),
        ) {
            settingsCategoryItems(
                selected = null,
                onSelect = { navigator.push(it.screen) },
            )
        }
    }
}

internal fun LazyListScope.settingsCategoryItems(
    selected: VoyagerScreen?,
    onSelect: (SettingsSectionSpec) -> Unit,
) {
    iaGroups.forEach { group ->
        val visible = group.filter { spec ->
            spec.screen !is SearchableSettings || spec.screen.isEnabled()
        }
        if (visible.isEmpty()) return@forEach
        item(key = "settings-main-${visible.first().titleRes.resourceId}") {
            InsetGroupedList {
                visible.forEachIndexed { i, spec ->
                    if (i > 0) GroupDivider(SettingsSectionBadgeSize)
                    GroupedRow(
                        title = spec.titleOverrideRes?.let { stringResource(it) } ?: stringResource(spec.titleRes),
                        subtitle = spec.subtitle(),
                        chevron = true,
                        enabled = true,
                        divider = false,
                        selected = spec.screen == selected,
                        leading = { SettingsSectionBadgeCurrent(spec.icon()) },
                        onClick = { onSelect(spec) },
                    )
                }
            }
        }
    }
}

internal data class SettingsSectionSpec(
    val titleRes: StringResource,
    val screen: VoyagerScreen,
    val icon: () -> ImageVector,
    val subtitle: @Composable () -> String,
    val titleOverrideRes: StringResource? = null,
)

internal fun settingsDisplayTitleRes(screen: VoyagerScreen): StringResource? = when (screen) {
    TsuzukiSettingsDownloadScreen -> MR.strings.tsuzuki_settings_title_downloads
    TsuzukiSettingsDataScreen -> MR.strings.tsuzuki_settings_title_data
    TsuzukiSettingsBrowseScreen -> MR.strings.tsuzuki_settings_title_browse
    TsuzukiSettingsTrackingScreen -> MR.strings.tsuzuki_settings_title_tracking
    TsuzukiSettingsSecurityScreen -> MR.strings.tsuzuki_settings_title_security
    else -> null
}

internal val iaSections: List<SettingsSectionSpec> = listOf(
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_appearance,
        screen = TsuzukiSettingsAppearanceScreen,
        icon = { TsuzukiIcons.Palette },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_appearance) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_library,
        screen = TsuzukiSettingsLibraryScreen,
        icon = { TsuzukiIcons.Library },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_library) },
    ),
    SettingsSectionSpec(
        titleRes = SYMR.strings.pref_category_mangadex,
        screen = TsuzukiSettingsMangadexScreen,
        icon = { EhAssets.MangadexLogo },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_mangadex) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_reader,
        screen = TsuzukiSettingsReaderScreen,
        icon = { TsuzukiIcons.BookOpen },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_reader) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_downloads,
        screen = TsuzukiSettingsDownloadScreen,
        icon = { TsuzukiIcons.Download },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_downloads) },
        titleOverrideRes = MR.strings.tsuzuki_settings_title_downloads,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.label_data_storage,
        screen = TsuzukiSettingsDataScreen,
        icon = { TsuzukiIcons.ArrowLeftRight },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_data) },
        titleOverrideRes = MR.strings.tsuzuki_settings_title_data,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.browse,
        screen = TsuzukiSettingsBrowseScreen,
        icon = { TsuzukiIcons.Compass },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_browse) },
        titleOverrideRes = MR.strings.tsuzuki_settings_title_browse,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_tracking,
        screen = TsuzukiSettingsTrackingScreen,
        icon = { TsuzukiIcons.TrendingUp },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_tracking) },
        titleOverrideRes = MR.strings.tsuzuki_settings_title_tracking,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_security,
        screen = TsuzukiSettingsSecurityScreen,
        icon = { TsuzukiIcons.EyeOff },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_security) },
        titleOverrideRes = MR.strings.tsuzuki_settings_title_security,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_advanced,
        screen = TsuzukiSettingsAdvancedScreen,
        icon = { TsuzukiIcons.Gear },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_advanced) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_about,
        screen = TsuzukiAboutScreen,
        icon = { TsuzukiIcons.Info },
        subtitle = {
            "${stringResource(MR.strings.app_name)} ${TsuzukiAboutScreen.getVersionName(withBuildDate = false)}"
        },
    ),
)

internal val iaGroups: List<List<SettingsSectionSpec>> = listOf(
    iaSections.subList(0, 4),
    iaSections.subList(4, 7),
    iaSections.subList(7, 9),
    iaSections.subList(9, 11),
)
