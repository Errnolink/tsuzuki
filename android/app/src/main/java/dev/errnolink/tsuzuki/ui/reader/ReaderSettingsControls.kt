package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import androidx.compose.foundation.layout.Box
import tachiyomi.core.common.preference.Preference
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ReaderToggle(label: String, pref: Preference<Boolean>) {
    val checked by pref.collectAsState()
    GroupedRow(label, checked = checked, onCheckedChange = pref::set)
}

@Composable
internal fun ReaderChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val subhead = TsuzukiTheme.typography.subhead
        val measure: (String) -> Float = { text ->
            with(density) {
                textMeasurer
                    .measure(
                        text,
                        style = subhead.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    .size
                    .width
                    .toDp()
                    .value
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.field)).background(TsuzukiTheme.colors.elevated),
        ) {
            if (settingControl(options, (availableWidth - SegmentedRowInset * 2).value, measure) == SettingControl.Segmented) {
                TsuzukiText(label, Modifier.padding(12.dp), TsuzukiTheme.typography.headline)
                SegmentedControl(options, selected.coerceIn(options.indices), onSelect, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
            } else {
                Box {
                    GroupedRow(label, value = options.getOrNull(selected), chevron = true, onClick = { expanded = true }, divider = false)
                    Box(Modifier.matchParentSize(), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                        Box(Modifier.padding(end = 16.dp).size(24.dp)) {
                            GlassMenu(
                                expanded,
                                label,
                                options.mapIndexed { index, option ->
                                    ContextMenuAction(
                                        option,
                                        selected = index == selected,
                                        onClick = { onSelect(index) },
                                    )
                                },
                                onDismissRequest = { expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SegmentedRowInset = 8.dp

@Composable
internal fun ReaderSettingSlider(
    value: Int,
    valueRange: IntRange,
    label: String,
    onChange: (Int) -> Unit,
    valueString: String = value.toString(),
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GroupedRow(label, value = valueString, divider = false)
        ReaderSlider(value, valueRange, onChange, label = label, onPreview = { it?.let(onChange) })
    }
}
