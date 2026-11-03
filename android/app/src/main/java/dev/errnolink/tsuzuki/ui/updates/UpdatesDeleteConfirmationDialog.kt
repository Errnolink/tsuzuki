package dev.errnolink.tsuzuki.ui.updates

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.settings.ConfirmSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun UpdatesDeleteConfirmationDialog(onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    ConfirmSheet(
        visible = true,
        title = stringResource(MR.strings.action_delete),
        message = stringResource(MR.strings.confirm_delete_chapters),
        confirmLabel = stringResource(MR.strings.action_delete),
        destructive = true,
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}
