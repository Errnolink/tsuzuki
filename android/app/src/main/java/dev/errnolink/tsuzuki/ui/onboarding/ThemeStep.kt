package dev.errnolink.tsuzuki.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.presentation.more.onboarding.OnboardingStep

internal class ThemeStep : OnboardingStep {

    override val isComplete: Boolean = true

    private val uiPreferences: UiPreferences = Injekt.get()

    @Composable
    override fun Content() {
        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val modes = listOf(
            ThemeMode.SYSTEM to stringResource(MR.strings.theme_system),
            ThemeMode.LIGHT to stringResource(MR.strings.theme_light),
            ThemeMode.DARK to stringResource(MR.strings.theme_dark),
        )

        InsetGroupedList(
            footer = stringResource(MR.strings.pref_app_theme),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(TsuzukiSpacing.row),
            ) {
                SegmentedControl(
                    options = modes.map { it.second },
                    selectedIndex = modes.indexOfFirst { it.first == themeMode }.coerceAtLeast(0),
                    onSelect = { index ->
                        val mode = modes[index].first
                        themeModePref.set(mode)
                        setAppCompatDelegateThemeMode(mode)
                    },
                )
            }
        }
    }
}
