package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import dev.errnolink.tsuzuki.ui.motion.TsuzukiScreenTransition
import dev.errnolink.tsuzuki.ui.shell.LocalShellTopActive
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal val SettingsStartPaneWidth = 320.dp
internal val SettingsEndPaneMaxWidth = 720.dp
private val SettingsPaneGap = 24.dp

@Composable
internal fun SettingsHost(destination: Int?) {
    val parentNavigator = LocalNavigator.currentOrThrow
    if (LocalWindowWidthClass.current != WindowWidthClass.Expanded) {
        Navigator(
            screen = settingsDestinationRoot(destination) ?: TsuzukiSettingsMainScreen,
            content = { navigator ->
                val pop: () -> Unit = {
                    if (navigator.canPop) {
                        navigator.pop()
                    } else {
                        parentNavigator.pop()
                    }
                }
                CompositionLocalProvider(LocalBackPress provides pop) {
                    TsuzukiScreenTransition(navigator = navigator)
                }
            },
        )
    } else {
        SettingsTwoPane(settingsDestinationRoot(destination))
    }
}

private fun settingsDestinationRoot(destination: Int?): VoyagerScreen? = when (destination) {
    SettingsScreen.Destination.About.id -> TsuzukiAboutScreen
    SettingsScreen.Destination.DataAndStorage.id -> TsuzukiSettingsDataScreen
    SettingsScreen.Destination.Tracking.id -> TsuzukiSettingsTrackingScreen
    else -> null
}

@Composable
private fun SettingsTwoPane(initialRoot: VoyagerScreen?) {
    val parentNavigator = LocalNavigator.currentOrThrow
    val backPress = LocalBackPress.current
    val sections = remember { visibleSettingsSections() }
    var selectedKey by rememberSaveable { mutableStateOf(initialRoot?.key ?: sections.first().screen.key) }
    val selected = sections.firstOrNull { it.screen.key == selectedKey }?.screen ?: sections.first().screen
    var nested by remember { mutableStateOf<Navigator?>(null) }
    val nestedRootKey = remember { selectedKey }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(SettingsStartPaneWidth)) {
            val state = rememberLazyListState()
            SettingsScaffold(
                title = stringResource(MR.strings.label_settings),
                navigateUp = {
                    val stack = nested
                    if (stack?.canPop == true) {
                        stack.pop()
                    } else {
                        backPress?.invoke() ?: parentNavigator.pop()
                    }
                },
                state = state,
                onTitleClick = { parentNavigator.push(TsuzukiSettingsSearchScreen()) },
                actions = listOf(
                    ShellTopAction(stringResource(MR.strings.action_search), TsuzukiIcons.Search, {
                        parentNavigator.push(TsuzukiSettingsSearchScreen())
                    }),
                ),
            ) {
                settingsCategoryItems(
                    selected = selected,
                    onSelect = { spec -> selectedKey = spec.screen.key },
                )
            }
        }
        Spacer(Modifier.width(SettingsPaneGap))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Navigator(screen = selected) { navigator ->
                SideEffect { nested = navigator }
                val nestedPop: (() -> Unit)? = if (navigator.canPop) navigator::pop else null
                CompositionLocalProvider(
                    LocalBackPress provides nestedPop,
                    LocalShellTopActive provides navigator.canPop,
                ) {
                    TsuzukiScreenTransition(navigator = navigator, modifier = Modifier.widthIn(max = SettingsEndPaneMaxWidth))
                }
            }
        }
    }

    LaunchedEffect(selectedKey) {
        if (selectedKey != nestedRootKey) {
            nested?.replaceAll(selected)
        }
    }
}

private fun visibleSettingsSections(): List<SettingsSectionSpec> =
    iaSections.filter { it.screen !is SearchableSettings || it.screen.isEnabled() }
