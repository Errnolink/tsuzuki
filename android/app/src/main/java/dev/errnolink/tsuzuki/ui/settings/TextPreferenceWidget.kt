package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

@Composable
fun TextPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: CharSequence? = null,
    /** Can be either [ImageVector] or [Painter] */
    icon: Any? = null,
    iconTint: Color? = null,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
    divider: Boolean = true,
) {
    val subtitleText = when (subtitle) {
        null -> null
        is AnnotatedString -> subtitle.text.takeIf { it.isNotBlank() }
        else -> subtitle.toString().takeIf { it.isNotBlank() }
    }
    GroupedRow(
        title = title.orEmpty(),
        modifier = modifier.preferenceHighlight(),
        subtitle = subtitleText,
        divider = divider,
        enabled = onPreferenceClick != null,
        leading = icon?.let {
            {
                when (it) {
                    is ImageVector -> Icon(it, null, Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
                    is Painter -> Icon(it, null, Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
                }
            }
        },
        trailing = widget,
        onClick = onPreferenceClick,
    )
}
