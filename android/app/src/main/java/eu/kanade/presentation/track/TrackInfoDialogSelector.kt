package eu.kanade.presentation.track

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackStatusSelector(
    selection: Long,
    onSelectionChange: (Long) -> Unit,
    selections: Map<Long, StringResource?>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.status),
        content = {
            InsetGroupedList {
                selections.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) GroupDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.RadioButton) { onSelectionChange(key) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        TsuzukiText(
                            text = value?.let { stringResource(it) } ?: "",
                            modifier = Modifier.weight(1f),
                        )
                        if (selection == key) SelectionCheck()
                    }
                }
            }
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackChapterSelector(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    range: Iterable<Int>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.chapters),
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelNumberPicker(
                    items = range.toImmutableList(),
                    startIndex = selection,
                    size = DpSize(160.dp, 128.dp),
                    onSelectionChanged = { onSelectionChange(it) },
                )
            }
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackScoreSelector(
    selection: String,
    onSelectionChange: (String) -> Unit,
    selections: ImmutableList<String>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.score),
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelTextPicker(
                    items = selections,
                    startIndex = selections.indexOf(selection).takeIf { it > 0 } ?: (selections.size / 2),
                    size = DpSize(160.dp, 128.dp),
                    onSelectionChanged = { onSelectionChange(selections[it]) },
                )
            }
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackDateSelector(
    title: String,
    initialSelectedDateMillis: Long,
    selectableDates: SelectableDates,
    onConfirm: (Long) -> Unit,
    onRemove: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis,
        selectableDates = selectableDates,
    )
    DetentSheet(visible = true, title = title, onDismissRequest = onDismissRequest) {
        DatePicker(
            state = pickerState,
            title = null,
            headline = null,
            showModeToggle = false,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (onRemove != null) {
                PillButton(
                    label = stringResource(MR.strings.action_remove),
                    onClick = onRemove,
                    prominent = false,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            PillButton(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismissRequest,
                prominent = false,
            )
            PillButton(
                label = stringResource(MR.strings.action_ok),
                onClick = { onConfirm(pickerState.selectedDateMillis!!) },
            )
        }
    }
}

@Composable
private fun BaseSelector(
    title: String,
    content: @Composable BoxScope.() -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    DetentSheet(visible = true, title = title, onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.fillMaxWidth(), content = content)
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismissRequest,
                prominent = false,
            )
            PillButton(
                label = stringResource(MR.strings.action_ok),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun SelectionCheck() {
    val color = TsuzukiTheme.colors.accent
    Canvas(Modifier.size(width = 14.dp, height = 11.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.55f)
            lineTo(size.width * 0.36f, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(
            path,
            color,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackStatusSelectorPreviews() {
    TachiyomiPreviewTheme {
        Surface {
            TrackStatusSelector(
                selection = 1,
                onSelectionChange = {},
                selections = persistentMapOf(
                    // Anilist values
                    1L to MR.strings.reading,
                    2L to MR.strings.plan_to_read,
                    3L to MR.strings.completed,
                    4L to MR.strings.on_hold,
                    5L to MR.strings.dropped,
                    6L to MR.strings.repeating,
                ),
                onConfirm = {},
                onDismissRequest = {},
            )
        }
    }
}
