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
import eu.kanade.presentation.more.settings.Preference

@Composable
fun MultiSelectListPreferenceWidget(
    preference: Preference.PreferenceItem.MultiSelectListPreference,
    values: Set<String>,
    onValuesChange: (Set<String>) -> Unit,
    divider: Boolean = true,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    GroupedRow(
        title = preference.title,
        modifier = Modifier.preferenceHighlight(),
        subtitle = preference.subtitleProvider(values, preference.entries),
        value = null,
        chevron = true,
        divider = divider,
        leading = preference.icon?.let {
            {
                Icon(it, null, Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
            }
        },
        onClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        MultiChoiceSheet(
            visible = true,
            title = preference.title,
            options = preference.entries.map { (key, label) ->
                Triple(label, key in values) { checked ->
                    onValuesChange(if (checked) values + key else values - key)
                }
            },
            onDismissRequest = { isDialogShown = false },
        )
    }
}
