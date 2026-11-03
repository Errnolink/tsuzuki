package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Duration.Companion.seconds

val LocalPreferenceHighlighted = compositionLocalOf(structuralEqualityPolicy()) { false }
val LocalPreferenceMinHeight = compositionLocalOf(structuralEqualityPolicy()) { 56.dp }

@Composable
fun PreferenceScaffold(
    titleRes: StringResource,
    titleOverride: String? = null,
    actions: List<dev.errnolink.tsuzuki.ui.shell.ShellTopAction> = emptyList(),
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    val preferences = itemsProvider()
    val sections = remember(preferences) { preferenceSections(preferences) }
    val state = rememberLazyListState()
    val highlightKey = SearchableSettings.highlightKey
    LaunchedEffect(sections, highlightKey) {
        if (highlightKey != null) {
            for ((sectionIndex, section) in sections.withIndex()) {
                val row = section.items.indexOfFirst { it.title == highlightKey }
                if (section.title == highlightKey || row >= 0) {
                    delay(0.5.seconds)
                    state.scrollToItem(sectionIndex + 1)
                    break
                }
            }
            SearchableSettings.highlightKey = null
        }
    }
    SettingsScaffold(
        title = titleOverride ?: stringResource(titleRes),
        navigateUp = onBackPressed,
        actions = actions,
        state = state,
        itemSpacing = 0.dp,
    ) {
        renderPreferenceSections(sections, highlightKey)
    }
}

@Composable
fun PreferenceScreen(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val sections = remember(items) { preferenceSections(items) }
    LazyColumn(
        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
        modifier = modifier,
        state = rememberLazyListState(),
        contentPadding = contentPadding,
    ) {
        renderPreferenceSections(sections, SearchableSettings.highlightKey)
    }
}

@Composable
fun SearchableSettings.TsuzukiPreferenceContent() {
    val handleBack = eu.kanade.presentation.util.LocalBackPress.current
    val actions = rememberSearchableTopActions(this)
    PreferenceScaffold(
        titleRes = getTitleRes(),
        titleOverride = displayName,
        onBackPressed = if (handleBack != null) handleBack::invoke else null,
        actions = actions,
        itemsProvider = { getPreferences() },
    )
}

@Composable
private fun rememberSearchableTopActions(screen: SearchableSettings): List<dev.errnolink.tsuzuki.ui.shell.ShellTopAction> {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    return remember(screen) {
        when (screen) {
            is SettingsDataScreen -> listOf(
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = "Help",
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.HelpOutline,
                    onClick = { uriHandler.openUri(SettingsDataScreen.HELP_URL) },
                ),
            )
            is SettingsTrackingScreen -> listOf(
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = "Tracking guide",
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.HelpOutline,
                    onClick = { uriHandler.openUri("https://mihon.app/docs/guides/tracking") },
                ),
            )
            else -> emptyList()
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    TsuzukiText(
        title,
        modifier
            .padding(start = TsuzukiSpacing.gutter + 16.dp, end = TsuzukiSpacing.gutter + 16.dp, bottom = 8.dp),
        TsuzukiTheme.typography.sectionHeader,
        TsuzukiTheme.colors.secondary,
    )
}

private data class PreferenceSection(val title: String?, val items: List<Preference.PreferenceItem<*, *>>, val footer: String?)

private fun preferenceSections(items: List<Preference>): List<PreferenceSection> = buildList {
    var rows = mutableListOf<Preference.PreferenceItem<*, *>>()
    fun flushRows() {
        if (rows.isNotEmpty()) {
            add(PreferenceSection(null, rows, null))
            rows = mutableListOf()
        }
    }
    items.forEach { preference ->
        if (preference.enabled) {
            when (preference) {
                is Preference.PreferenceGroup -> {
                    flushRows()
                    val visible = preference.preferenceItems.filter { it.enabled }
                    val body = visible.filterNot { it is Preference.PreferenceItem.InfoPreference }
                    val footer = visible.filterIsInstance<Preference.PreferenceItem.InfoPreference>()
                        .joinToString(separator = "\n\n") { it.title }
                        .takeIf { it.isNotBlank() }
                    if (body.isNotEmpty() || footer != null) {
                        add(PreferenceSection(preference.title.takeIf { it.isNotBlank() }, body, footer))
                    }
                }
                is Preference.PreferenceItem<*, *> -> rows.add(preference)
            }
        }
    }
    flushRows()
}

private fun LazyListScope.renderPreferenceSections(sections: List<PreferenceSection>, highlightKey: String?) {
    sections.forEachIndexed { index, section ->
        val groupKey = "settings-group-$index-${section.title}"
        item(key = groupKey, contentType = "settings-section") {
            Column {
                Spacer(Modifier.height(TsuzukiSpacing.section))
                section.title?.let { title ->
                    SettingsSectionHeader(title)
                }
                Column(
                    Modifier
                        .padding(horizontal = TsuzukiSpacing.gutter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TsuzukiCorners.group))
                        .background(TsuzukiTheme.colors.grouped),
                ) {
                    section.items.forEachIndexed { row, item ->
                        if (row > 0) GroupDivider()
                        PreferenceItem(item = item, highlightKey = highlightKey)
                    }
                }
                section.footer?.let { footer ->
                    TsuzukiText(
                        footer,
                        Modifier.padding(start = TsuzukiSpacing.gutter + 16.dp, end = TsuzukiSpacing.gutter + 16.dp, top = 8.dp),
                        TsuzukiTheme.typography.footnote,
                        TsuzukiTheme.colors.secondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreferenceItem(
    item: Preference.PreferenceItem<*, *>,
    highlightKey: String?,
) {
    val scope = rememberCoroutineScope()
    val enabled = item.enabled
    val highlighted = item.title == highlightKey
    val motion = TsuzukiTheme.motion
    val enter = if (motion.reduced) {
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap())
    } else {
        androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn()
    }
    val exit = if (motion.reduced) {
        androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())
    } else {
        androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = enabled,
        enter = enter,
        exit = exit,
        content = {
            CompositionLocalProvider(LocalPreferenceHighlighted provides highlighted) {
                when (item) {
                    is Preference.PreferenceItem.SwitchPreference -> {
                        val value by item.preference.collectAsState()
                        SwitchPreferenceWidget(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            checked = value,
                            divider = false,
                            onCheckedChanged = { newValue ->
                                scope.launch {
                                    if (item.onValueChanged(newValue)) {
                                        item.preference.set(newValue)
                                    }
                                }
                            },
                        )
                    }
                    is Preference.PreferenceItem.SliderPreference -> {
                        dev.errnolink.tsuzuki.designsystem.SliderRow(
                            title = item.title,
                            value = item.value,
                            valueString = item.valueString.takeUnless { it.isNullOrEmpty() } ?: item.value.toString(),
                            range = item.valueRange,
                            enabled = item.enabled,
                            onChange = {
                                scope.launch {
                                    item.onValueChanged(it)
                                }
                            },
                        )
                    }
                    is Preference.PreferenceItem.ListPreference<*> -> {
                        val value by item.preference.collectAsState()
                        ListPreferenceWidget(
                            value = value,
                            title = item.title,
                            subtitle = item.internalSubtitleProvider(value, item.entries),
                            icon = item.icon,
                            entries = item.entries,
                            divider = false,
                            onValueChange = { newValue ->
                                scope.launch {
                                    if (item.internalOnValueChanged(newValue!!)) {
                                        item.internalSet(newValue)
                                    }
                                }
                            },
                        )
                    }
                    is Preference.PreferenceItem.BasicListPreference -> {
                        ListPreferenceWidget(
                            value = item.value,
                            title = item.title,
                            subtitle = item.subtitleProvider(item.value, item.entries),
                            icon = item.icon,
                            entries = item.entries,
                            divider = false,
                            onValueChange = { scope.launch { item.onValueChanged(it) } },
                        )
                    }
                    is Preference.PreferenceItem.MultiSelectListPreference -> {
                        val values by item.preference.collectAsState()
                        MultiSelectListPreferenceWidget(
                            preference = item,
                            values = values,
                            divider = false,
                            onValuesChange = { newValues ->
                                scope.launch {
                                    if (item.onValueChanged(newValues)) {
                                        item.preference.set(newValues.toMutableSet())
                                    }
                                }
                            },
                        )
                    }
                    is Preference.PreferenceItem.EditTextPreference -> {
                        val values by item.preference.collectAsState()
                        EditTextPreferenceWidget(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            value = values,
                            divider = false,
                            onConfirm = {
                                val accepted = item.onValueChanged(it)
                                if (accepted) item.preference.set(it)
                                accepted
                            },
                        )
                    }
                    is Preference.PreferenceItem.TextPreference -> {
                        TextPreferenceWidget(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            divider = false,
                            onPreferenceClick = item.onClick,
                        )
                    }
                    is Preference.PreferenceItem.TrackerPreference -> {
                        val isLoggedIn by item.tracker.let { tracker ->
                            tracker.isLoggedInFlow.collectAsState(tracker.isLoggedIn)
                        }
                        TrackingPreferenceWidget(
                            tracker = item.tracker,
                            checked = isLoggedIn,
                            onClick = { if (isLoggedIn) item.logout() else item.login() },
                        )
                    }
                    is Preference.PreferenceItem.InfoPreference -> {
                        InfoWidget(text = item.title)
                    }
                    is Preference.PreferenceItem.CustomPreference -> {
                        item.content()
                    }
                }
            }
        },
    )
}
