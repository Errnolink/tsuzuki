package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExhUtils(
    isVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
) {
    DetentSheet(isVisible, "Reader tools", { onSetExhUtilsVisibility(false) }, initialDetent = SheetDetent.FitContent) {
        GroupedRow(stringResource(SYMR.strings.eh_autoscroll), checked = isAutoScroll, onCheckedChange = onToggleAutoscroll, enabled = isAutoScrollEnabled)
        var helpExpanded by remember { mutableStateOf(false) }
        IntervalStepperRow(autoScrollFrequency, onSetAutoScrollFrequency)
        if (!isAutoScrollEnabled) TsuzukiText(stringResource(SYMR.strings.eh_autoscroll_freq_invalid), color = TsuzukiTheme.colors.destructive)
        GroupedRow(stringResource(SYMR.strings.eh_retry_all), onClick = onClickRetryAll)
        GroupedRow(stringResource(SYMR.strings.eh_boost_page), onClick = onClickBoostPage)
        GroupedRow("Help", chevron = true, onClick = { helpExpanded = true })
        if (helpExpanded) {
            Box {
                GlassMenu(
                    expanded = true,
                    label = "Help",
                    listOf(
                        ContextMenuAction(stringResource(SYMR.strings.eh_autoscroll_help)) { helpExpanded = false; onClickAutoScrollHelp() },
                        ContextMenuAction(stringResource(SYMR.strings.eh_retry_all_help)) { helpExpanded = false; onClickRetryAllHelp() },
                        ContextMenuAction(stringResource(SYMR.strings.eh_boost_page_help)) { helpExpanded = false; onClickBoostPageHelp() },
                    ),
                    onDismissRequest = { helpExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun IntervalStepperRow(frequency: String, onSet: (String) -> Unit) {
    val parsed = frequency.toDoubleOrNull()
    val seconds = (parsed ?: 5.0).coerceIn(0.5, 60.0)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TsuzukiText("Interval", Modifier.weight(1f))
        StepperButton("Decrease interval") {
            onSet(formatInterval((seconds - 0.5).coerceAtLeast(0.5)))
        }
        TsuzukiText(
            "${formatInterval(seconds)} s",
            style = TsuzukiTheme.typography.subhead,
            color = TsuzukiTheme.colors.text,
        )
        StepperButton("Increase interval") {
            onSet(formatInterval((seconds + 0.5).coerceAtMost(60.0)))
        }
    }
}

private fun formatInterval(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TsuzukiTheme.colors.elevated),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiText(if (label.startsWith("Decrease")) "−" else "+", style = TsuzukiTheme.typography.headline, color = TsuzukiTheme.colors.text)
        }
    }
}
