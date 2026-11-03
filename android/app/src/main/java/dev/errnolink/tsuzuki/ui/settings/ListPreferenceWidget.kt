package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.PopUpItem
import dev.errnolink.tsuzuki.designsystem.PopUpRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

@Composable
fun <T> ListPreferenceWidget(
    value: T,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
    divider: Boolean = true,
) {
    PopUpRow(
        title = title,
        selected = value,
        options = entries.map { (key, label) -> PopUpItem(key, label) },
        onSelect = onValueChange,
        subtitle = subtitle?.takeUnless { it == entries[value] },
        divider = divider,
        leading = icon?.let {
            {
                Icon(it, null, Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
            }
        },
    )
}
