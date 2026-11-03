package dev.errnolink.tsuzuki.ui.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import androidx.compose.foundation.layout.padding
import dev.icerock.moko.resources.StringResource
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Compass
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.LucideLibrary
import dev.errnolink.tsuzuki.designsystem.Palette
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SettingsLucide
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
        Content(twoPane = false)
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val state = rememberLazyListState()

        SettingsScaffold(
            title = stringResource(MR.strings.label_settings),
            navigateUp = backPress::invoke,
            state = state,
            onTitleClick = { navigator.navigate(TsuzukiSettingsSearchScreen(), twoPane) },
            actions = listOf(
                ShellTopAction(stringResource(MR.strings.action_search), TsuzukiIcons.Search, {
                    navigator.navigate(TsuzukiSettingsSearchScreen(), twoPane)
                }),
            ),
        ) {
            iaGroups.forEach { group ->
                val visible = group.filter { spec ->
                    spec.screen !is SearchableSettings || spec.screen.isEnabled()
                }
                if (visible.isEmpty()) return@forEach
                item(key = "settings-main-${visible.first().titleRes.resourceId}") {
                    InsetGroupedList {
                        visible.forEachIndexed { i, spec ->
                            if (i > 0) GroupDivider()
                            GroupedRow(
                                title = spec.titleOverride ?: stringResource(spec.titleRes),
                                subtitle = spec.subtitle(),
                                chevron = true,
                                enabled = true,
                                divider = false,
                                leading = { SettingsSectionBadge(spec.icon()) },
                                onClick = { navigator.navigate(spec.screen, twoPane) },
                            )
                        }
                    }
                }
            }
        }
    }


    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }
}

@Composable
internal fun SettingsSectionBadge(icon: ImageVector) {
    Box(
        Modifier
            .size(29.dp)
            .background(
                TsuzukiTheme.colors.secondary.copy(alpha = 0.12f),
                RoundedCornerShape(TsuzukiSpacing.small),
            )
            .padding(TsuzukiSpacing.tiny),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            null,
            Modifier.size(17.dp),
            tint = TsuzukiTheme.colors.secondary,
        )
    }
}

internal data class SettingsSectionSpec(
    val titleRes: StringResource,
    val screen: VoyagerScreen,
    val icon: () -> ImageVector,
    val subtitle: @Composable () -> String,
    val titleOverride: String? = null,
)

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
        icon = { TsuzukiIcons.LucideLibrary },
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
        titleOverride = TsuzukiSettingsDownloadScreen.displayName,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.label_data_storage,
        screen = TsuzukiSettingsDataScreen,
        icon = { TsuzukiIcons.ArrowLeftRight },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_data) },
        titleOverride = TsuzukiSettingsDataScreen.displayName,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.browse,
        screen = TsuzukiSettingsBrowseScreen,
        icon = { TsuzukiIcons.Compass },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_browse) },
        titleOverride = TsuzukiSettingsBrowseScreen.displayName,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_tracking,
        screen = TsuzukiSettingsTrackingScreen,
        icon = { TsuzukiIcons.RefreshCw },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_tracking) },
        titleOverride = TsuzukiSettingsTrackingScreen.displayName,
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_security,
        screen = TsuzukiSettingsSecurityScreen,
        icon = { TsuzukiIcons.EyeOff },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_security) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_advanced,
        screen = TsuzukiSettingsAdvancedScreen,
        icon = { TsuzukiIcons.SettingsLucide },
        subtitle = { stringResource(MR.strings.tsuzuki_settings_sub_advanced) },
    ),
    SettingsSectionSpec(
        titleRes = MR.strings.pref_category_about,
        screen = TsuzukiAboutScreen(),
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
