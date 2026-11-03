package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

class TsuzukiSettingsSearchScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val state = rememberLazyListState()
        var query by rememberSaveable { mutableStateOf("") }
        DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) focus.clearFocus()
        }
        val results = searchResults(query)
        val search = rememberTopBarSearch(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(MR.strings.action_search_settings),
            onClose = { query = "" },
        )
        LaunchedEffect(Unit) { search.state.expand() }
        SettingsScaffold(
            title = stringResource(MR.strings.action_search_settings),
            navigateUp = navigator::pop,
            state = state,
            search = search,
        ) {
            if (query.isNotBlank() && results.isEmpty()) {
                item("settings-no-results") {
                    ShellEmpty("No matching settings", "Try a different setting name.")
                }
            }
            if (results.isNotEmpty()) {
                item("settings-results") {
                    InsetGroupedList {
                        results.forEachIndexed { index, result ->
                            if (index > 0) GroupDivider()
                            GroupedRow(
                                title = result.title,
                                subtitle = result.breadcrumbs,
                                chevron = true,
                                divider = false,
                                onClick = {
                                    SearchableSettings.highlightKey = result.highlightKey
                                    navigator.replace(result.route)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun searchResults(searchKey: String): List<SearchResultItem> {
    if (searchKey.isBlank()) return emptyList()

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val index = getIndex()
    val result by produceState<List<SearchResultItem>?>(initialValue = null, searchKey) {
        value = index.asSequence()
            .flatMap { settingsData ->
                settingsData.contents.asSequence()
                    // Only search from enabled prefs and one with valid title
                    .filter { it.enabled && it.title.isNotBlank() }
                    // Flatten items contained inside *enabled* PreferenceGroup
                    .flatMap { p ->
                        when (p) {
                            is Preference.PreferenceGroup -> {
                                if (p.enabled) {
                                    p.preferenceItems.asSequence()
                                        .filter { it.enabled && it.title.isNotBlank() }
                                        .map { p.title to it }
                                } else {
                                    emptySequence()
                                }
                            }
                            is Preference.PreferenceItem<*, *> -> sequenceOf(null to p)
                        }
                    }
                    // Don't show info preference
                    .filterNot { it.second is Preference.PreferenceItem.InfoPreference }
                    // Filter by search query
                    .filter { (_, p) ->
                        val inTitle = p.title.contains(searchKey, true)
                        val inSummary = p.subtitle?.contains(searchKey, true) ?: false
                        inTitle || inSummary
                    }
                    // Map result data
                    .map { (categoryTitle, p) ->
                        SearchResultItem(
                            route = settingsData.route,
                            title = p.title,
                            breadcrumbs = getLocalizedBreadcrumb(
                                path = settingsData.title,
                                node = categoryTitle,
                                isLtr = isLtr,
                            ),
                            highlightKey = p.title,
                        )
                    }
            }
            .take(10) // Just take top 10 result for quicker result
            .toList()
    }

    return result.orEmpty()
}

@Composable
@NonRestartableComposable
private fun getIndex() = settingScreens
    // SY -->
    .filter(SearchableSettings::isEnabled)
    // SY <--
    .map { screen ->
        SettingsData(
            title = screen.displayName ?: stringResource(screen.getTitleRes()),
            route = screen,
            contents = screen.getPreferences(),
        )
    }

private val settingScreens = listOf(
    TsuzukiSettingsAppearanceScreen,
    TsuzukiSettingsLibraryScreen,
    TsuzukiSettingsMangadexScreen,
    TsuzukiSettingsReaderScreen,
    TsuzukiSettingsDownloadScreen,
    TsuzukiSettingsDataScreen,
    TsuzukiSettingsBrowseScreen,
    TsuzukiSettingsTrackingScreen,
    TsuzukiSettingsSecurityScreen,
    TsuzukiSettingsAdvancedScreen,
)

private data class SettingsData(
    val title: String,
    val route: VoyagerScreen,
    val contents: List<Preference>,
)

private data class SearchResultItem(
    val route: VoyagerScreen,
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
)

private fun getLocalizedBreadcrumb(path: String, node: String?, isLtr: Boolean): String {
    return if (node == null) {
        path
    } else {
        val breadcrumbSeparator = " › "
        if (isLtr) "$path$breadcrumbSeparator$node" else "$node$breadcrumbSeparator$path"
    }
}
