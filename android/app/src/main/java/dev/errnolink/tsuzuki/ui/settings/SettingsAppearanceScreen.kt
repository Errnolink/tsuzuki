package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.util.system.toast
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassTintPreset
import dev.errnolink.tsuzuki.designsystem.PopUpItem
import dev.errnolink.tsuzuki.designsystem.PopUpRow
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.SliderRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.ui.model.NavigationStyle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object TsuzukiSettingsAppearanceScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsAppearanceScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance


    @Composable
    override fun Content() = TsuzukiPreferenceContent()
val prefKeys: Set<String> = setOf(
        "pref_theme_mode_key",
        "pref_colours_from_wallpaper",
        "tablet_ui_mode",
        "pref_navigation_style",
        "pref_show_updates_button",
        "pref_show_history_button",
        "app_date_format",
        "app_time_format",
        "relative_time_v2",
        "pref_details_header",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getNavbarGroup(uiPreferences = uiPreferences),
            getLayoutGroup(uiPreferences = uiPreferences),
            getLanguageRegionGroup(),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()
        val tsuzukiPreferences = remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>() }
        val context = LocalContext.current
        val glassPreferences = remember(context) { context.getSharedPreferences("tsuzuki_glass", android.content.Context.MODE_PRIVATE) }
        var tintStrength by remember(glassPreferences) { mutableFloatStateOf(glassPreferences.getFloat("tint_strength", GlassMaterial.DefaultTintStrength)) }
        var liquidGlass by remember(glassPreferences) { mutableStateOf(glassPreferences.getBoolean("liquid", true)) }

        val modes = listOf(
            ThemeMode.SYSTEM to stringResource(MR.strings.theme_system),
            ThemeMode.LIGHT to stringResource(MR.strings.theme_light),
            ThemeMode.DARK to stringResource(MR.strings.theme_dark),
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    SegmentedControl(
                        options = modes.map { it.second },
                        selectedIndex = modes.indexOfFirst { it.first == themeMode }.coerceAtLeast(0),
                        onSelect = { index ->
                            val mode = modes[index].first
                            themeModePref.set(mode)
                            setAppCompatDelegateThemeMode(mode)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = tsuzukiPreferences.coloursFromWallpaper(),
                    title = "Colours from wallpaper",
                    subtitle = "Page, surfaces, text and controls follow the system wallpaper (Android 12+).",
                    enabled = android.os.Build.VERSION.SDK_INT >= 31,
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Glass tint",
                ) {
                    val presets = GlassTintPreset.entries
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TsuzukiText("Glass tint", Modifier.weight(1f))
                            TsuzukiText(
                                GlassTintPreset.summaryLabel(tintStrength),
                                style = TsuzukiTheme.typography.subhead,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                        SegmentedControl(
                            options = presets.map { it.label },
                            selectedIndex = GlassTintPreset.nearestIndex(tintStrength),
                            onSelect = { index ->
                                tintStrength = presets[index].strength
                                glassPreferences.edit().putFloat("tint_strength", tintStrength).apply()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                        SliderRow(
                            title = "Glass tint",
                            value = kotlin.math.round(tintStrength * 100f).toInt().coerceIn(0, 100),
                            valueString = GlassTintPreset.summaryLabel(tintStrength),
                            range = 0..100,
                            enabled = true,
                            showHeader = false,
                            onChange = {
                                tintStrength = it / 100f
                                glassPreferences.edit().putFloat("tint_strength", tintStrength).apply()
                            },
                            startLabel = "Clear",
                            endLabel = "Opaque",
                        )
                        TsuzukiText(
                            "Battery saver and Increase Contrast use opaque controls.",
                            style = TsuzukiTheme.typography.footnote,
                            color = TsuzukiTheme.colors.secondary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_liquid_glass),
                ) {
                    SwitchPreferenceWidget(
                        title = stringResource(MR.strings.pref_liquid_glass),
                        subtitle = stringResource(MR.strings.pref_liquid_glass_summary),
                        checked = liquidGlass,
                        enabled = android.os.Build.VERSION.SDK_INT >= 33,
                        divider = false,
                        onCheckedChanged = {
                            liquidGlass = it
                            glassPreferences.edit().putBoolean("liquid", it).apply()
                        },
                    )
                },
            ),
        )
    }

    @Composable
    private fun getLayoutGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val tsuzukiPreferences = remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>() }
        val headerPref = tsuzukiPreferences.detailsHeader()
        val header by headerPref.collectAsState()
        val headers = listOf(
            dev.errnolink.tsuzuki.preference.DetailsHeader.CENTERED to "Large cover",
            dev.errnolink.tsuzuki.preference.DetailsHeader.CLASSIC to "Small cover",
        )

        return Preference.PreferenceGroup(
            title = "Layout",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = "Title page header",
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        dev.errnolink.tsuzuki.designsystem.TsuzukiText("Title page header", style = dev.errnolink.tsuzuki.designsystem.TsuzukiTheme.typography.body, modifier = Modifier.padding(bottom = 10.dp))
                        SegmentedControl(
                            options = headers.map { it.second },
                            selectedIndex = headers.indexOfFirst { it.first == header }.coerceAtLeast(0),
                            onSelect = { index -> headerPref.set(headers[index].first) },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = tsuzukiPreferences.coverColourCard(),
                    title = "Cover colour behind title",
                    subtitle = "The title page takes a deep shade of its cover's colour, with matching buttons.",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode(),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    fun getNavbarGroup(uiPreferences: UiPreferences): Preference.PreferenceGroup {
        val navigationStyle by uiPreferences.navigationStyle().collectAsState()
        return Preference.PreferenceGroup(
            title = "Navigation",
            preferenceItems = listOfNotNull(
                Preference.PreferenceItem.CustomPreference(
                    title = "Navigation style",
                ) {
                    PopUpRow(
                        title = "Navigation style",
                        selected = navigationStyle,
                        options = listOf(
                            PopUpItem(NavigationStyle.Simple, "Simple"),
                            PopUpItem(NavigationStyle.Classic, "Classic"),
                        ),
                        divider = false,
                        onSelect = { uiPreferences.navigationStyle().set(it) },
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showNavUpdates(),
                    title = stringResource(SYMR.strings.pref_hide_updates_button),
                ).takeIf { navigationStyle == NavigationStyle.Classic },
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showNavHistory(),
                    title = stringResource(SYMR.strings.pref_hide_history_button),
                ).takeIf { navigationStyle == NavigationStyle.Classic },
            ).toPersistentList(),
        )
    }

    @Composable
    private fun getLanguageRegionGroup(): Preference.PreferenceGroup {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val tsuzukiPreferences = remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>() }
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val now = remember { LocalDate.now() }
        val nowMillis = remember { System.currentTimeMillis() }
        val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }
        val currentLanguage = remember {
            AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: ""
        }

        return Preference.PreferenceGroup(
            title = "Language & region",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language) +
                        if (currentLanguage.isNotEmpty() && !currentLanguage.startsWith("en")) " (App Language)" else "",
                    onClick = { navigator.push(TsuzukiAppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat(),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_date_format),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = tsuzukiPreferences.timeFormat(),
                    entries = mapOf(
                        dev.errnolink.tsuzuki.util.TimeFormat.SYSTEM to
                            "${stringResource(MR.strings.label_default)} (${dev.errnolink.tsuzuki.util.formatTime(nowMillis, dev.errnolink.tsuzuki.util.TimeFormat.SYSTEM, is24Hour)})",
                        dev.errnolink.tsuzuki.util.TimeFormat.H12 to
                            "12-hour (${dev.errnolink.tsuzuki.util.formatTime(nowMillis, dev.errnolink.tsuzuki.util.TimeFormat.H12, is24Hour)})",
                        dev.errnolink.tsuzuki.util.TimeFormat.H24 to
                            "24-hour (${dev.errnolink.tsuzuki.util.formatTime(nowMillis, dev.errnolink.tsuzuki.util.TimeFormat.H24, is24Hour)})",
                    ).toImmutableMap(),
                    title = "Time format",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
