package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellFilterRow
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import dev.errnolink.tsuzuki.ui.settings.BaseTextFieldRow
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SourceFilterDialog(
    onDismissRequest: () -> Unit,
    filters: FilterList,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (FilterList) -> Unit,
    startExpanded: Boolean,
    savedSearches: ImmutableList<EXHSavedSearch>,
    onSave: () -> Unit,
    onSavedSearch: (EXHSavedSearch) -> Unit,
    onSavedSearchPress: (EXHSavedSearch) -> Unit,
    onSavedSearchPressDesc: String,
    shouldShowSavingButton: Boolean = true,
    openMangaDexRandom: (() -> Unit)?,
    openMangaDexFollows: (() -> Unit)?,
) {
    DetentSheet(true, stringResource(MR.strings.action_filter), onDismissRequest) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(MR.strings.action_reset), onReset, Modifier.weight(1f), prominent = false)
            if (shouldShowSavingButton) PillButton(stringResource(MR.strings.action_save), onSave, Modifier.weight(1f), prominent = false)
            PillButton(stringResource(MR.strings.action_filter), { onFilter(); onDismissRequest() }, Modifier.weight(1f))
        }
        openMangaDexRandom?.let { GroupedRow("Random MangaDex title", chevron = true, onClick = { onDismissRequest(); it() }) }
        openMangaDexFollows?.let { GroupedRow("MangaDex follows", chevron = true, onClick = { onDismissRequest(); it() }) }
        if (savedSearches.isNotEmpty()) {
            TsuzukiText("Saved searches", style = TsuzukiTheme.typography.headline)
            savedSearches.forEach { search ->
                ContextMenu(search.name, listOf(ContextMenuAction(onSavedSearchPressDesc) { onSavedSearchPress(search) }), onClick = { onSavedSearch(search) }) {
                    GroupedRow(search.name, chevron = true)
                }
            }
        }
        filters.forEach { filter -> FilterItem(filter, { onUpdate(filters) }, startExpanded) }
    }
}

@Composable
private fun FilterItem(filter: Filter<*>, onUpdate: () -> Unit, startExpanded: Boolean) {
    when (filter) {
        is Filter.AutoComplete -> AutoCompleteItem(
            name = filter.name,
            state = filter.state.toImmutableList(),
            hint = filter.hint,
            values = filter.values.toImmutableList(),
            skipAutoFillTags = filter.skipAutoFillTags.toImmutableList(),
            validPrefixes = filter.validPrefixes.toImmutableList(),
        ) { filter.state = it; onUpdate() }
        is Filter.Header -> TsuzukiText(filter.name, style = TsuzukiTheme.typography.headline)
        is Filter.Separator -> GroupDivider()
        is Filter.CheckBox -> GroupedRow(filter.name, checked = filter.state, onCheckedChange = { filter.state = it; onUpdate() })
        is Filter.TriState -> ShellFilterRow(filter.name, filter.state.toTriStateFilter()) {
            filter.state = filter.state.toTriStateFilter().next().toTriStateInt()
            onUpdate()
        }
        is Filter.Text -> {
            var value by remember(filter) { mutableStateOf(TextFieldValue(filter.state)) }
            TsuzukiText(filter.name, style = TsuzukiTheme.typography.subhead)
            BaseTextFieldRow(value, { value = it; filter.state = it.text; onUpdate() })
        }
        is Filter.Select<*> -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                GroupedRow(filter.name, value = filter.values.getOrNull(filter.state)?.toString(), chevron = true, onClick = { expanded = true })
                GlassMenu(
                    expanded = expanded,
                    label = filter.name,
                    actions = filter.values.mapIndexed { index, value ->
                        ContextMenuAction(
                            label = value.toString(),
                            icon = if (index == filter.state) {
                                {
                                    Icon(TsuzukiIcons.Check, null, Modifier.size(16.dp), tint = TsuzukiTheme.colors.accent)
                                }
                            } else {
                                null
                            },
                        ) { filter.state = index; onUpdate() }
                    },
                    onDismissRequest = { expanded = false },
                )
            }
        }
        is Filter.Sort -> FilterGroup(filter.name, startExpanded) {
            filter.values.forEachIndexed { index, label ->
                GroupedRow(
                    title = label,
                    value = filter.state?.takeIf { it.index == index }?.let { if (it.ascending) "Ascending" else "Descending" },
                    onClick = {
                        val ascending = if (index == filter.state?.index) !filter.state!!.ascending else filter.state?.ascending ?: true
                        filter.state = Filter.Sort.Selection(index, ascending)
                        onUpdate()
                    },
                )
            }
        }
        is Filter.Group<*> -> FilterGroup(filter.name, startExpanded) {
            filter.state.filterIsInstance<Filter<*>>().forEach { FilterItem(it, onUpdate, startExpanded) }
        }
    }
}

@Composable
private fun FilterGroup(name: String, initiallyExpanded: Boolean, content: @Composable () -> Unit) {
    var expanded by remember(name) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(if (expanded) 0f else -90f, label = "FilterGroupChevron")
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TsuzukiText(name, Modifier.weight(1f))
            Icon(
                TsuzukiIcons.ChevronDown,
                null,
                Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
                tint = TsuzukiTheme.colors.secondary,
            )
        }
        if (expanded) content()
    }
}

private fun Int.toTriStateFilter(): TriState = when (this) {
    Filter.TriState.STATE_IGNORE -> TriState.DISABLED
    Filter.TriState.STATE_INCLUDE -> TriState.ENABLED_IS
    Filter.TriState.STATE_EXCLUDE -> TriState.ENABLED_NOT
    else -> error("Unknown filter state: $this")
}

private fun TriState.toTriStateInt(): Int = when (this) {
    TriState.DISABLED -> Filter.TriState.STATE_IGNORE
    TriState.ENABLED_IS -> Filter.TriState.STATE_INCLUDE
    TriState.ENABLED_NOT -> Filter.TriState.STATE_EXCLUDE
}
