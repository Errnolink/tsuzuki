package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

/**
 * One source/extension row: leading icon, name, subtitle, trailing slot. The hand-rolled rows it
 * replaces (sources sheet, sources screen, the browse card, the two extension lists) share this
 * scaffold; grouped backgrounds, dividers between rows and corner shapes stay with their callers.
 */
@Composable
fun SourceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    titleSemibold: Boolean = false,
    subtitle: String? = null,
    subtitleStyle: TextStyle = TsuzukiTheme.typography.footnote,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    subtitleSpacing: Dp? = null,
    leading: @Composable () -> Unit,
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    dividerStart: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .then(
                if (dividerStart != null) {
                    Modifier.drawBehind {
                        drawLine(
                            colors.separator,
                            Offset(dividerStart.toPx(), size.height),
                            Offset(size.width, size.height),
                            0.5.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        val titleStyle = TsuzukiTheme.typography.callout.copy(
            fontWeight = if (titleSemibold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = if (subtitleSpacing != null) Arrangement.spacedBy(subtitleSpacing) else Arrangement.Top,
        ) {
            if (titleAccessory != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TsuzukiText(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    titleAccessory()
                }
            } else {
                TsuzukiText(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (subtitle != null) {
                TsuzukiText(
                    subtitle,
                    style = subtitleStyle,
                    color = colors.secondary,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}
