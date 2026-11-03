package dev.errnolink.tsuzuki.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.ui.settings.ConfirmSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun HistoryDeleteDialog(onDismissRequest: () -> Unit, onDelete: (Boolean) -> Unit) {
    var removeEverything by remember { mutableStateOf(false) }
    DetentSheet(true, stringResource(MR.strings.action_remove), onDismissRequest) {
        TsuzukiText(stringResource(MR.strings.dialog_with_checkbox_remove_description))
        GroupedRow(
            title = stringResource(MR.strings.dialog_with_checkbox_reset),
            checked = removeEverything,
            onCheckedChange = { removeEverything = it },
            divider = false,
        )
        PillButton(
            stringResource(MR.strings.action_remove),
            { onDelete(removeEverything); onDismissRequest() },
            destructive = true,
        )
    }
}

@Composable
fun HistoryDeleteAllDialog(onDismissRequest: () -> Unit, onDelete: () -> Unit) {
    ConfirmSheet(
        visible = true,
        title = stringResource(MR.strings.action_remove_everything),
        message = stringResource(MR.strings.clear_history_confirmation),
        confirmLabel = stringResource(MR.strings.action_remove),
        destructive = true,
        onConfirm = onDelete,
        onDismissRequest = onDismissRequest,
    )
}
