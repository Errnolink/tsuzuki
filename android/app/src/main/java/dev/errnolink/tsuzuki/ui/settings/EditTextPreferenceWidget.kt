package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

@Composable
fun EditTextPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    value: String,
    widget: @Composable (() -> Unit)? = null,
    divider: Boolean = true,
    onConfirm: suspend (String) -> Boolean,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    GroupedRow(
        title = title,
        modifier = Modifier.preferenceHighlight(),
        subtitle = subtitle?.let { template ->
            runCatching { template.format(value) }.getOrDefault(template)
        },
        value = null,
        chevron = widget == null,
        divider = divider,
        leading = icon?.let {
            {
                Icon(it, null, Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
            }
        },
        trailing = widget,
        onClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        TextEntrySheet(
            visible = true,
            title = title,
            initialValue = value,
            confirmLabel = tachiyomi.presentation.core.i18n.stringResource(tachiyomi.i18n.MR.strings.action_ok),
            errorText = null,
            supportsConfirm = { it.isNotBlank() },
            onConfirm = { onConfirm(it) },
            onDismissRequest = { isDialogShown = false },
        )
    }
}
