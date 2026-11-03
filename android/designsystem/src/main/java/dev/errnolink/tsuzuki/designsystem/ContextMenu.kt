package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class ContextMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val icon: (@Composable () -> Unit)? = null,
    val subtitle: String? = null,
    val group: Int = 0,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun GlassMenu(
    expanded: Boolean,
    label: String,
    actions: List<ContextMenuAction>,
    onDismissRequest: () -> Unit,
) {
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        paneTitle = label,
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                if (actions[index - 1].group != action.group) {
                    PullDownSectionDivider()
                } else {
                    PullDownDivider()
                }
            }
            PullDownMenuItem(
                text = action.label,
                subtitle = action.subtitle,
                iconContent = action.icon,
                destructive = action.destructive,
                enabled = action.enabled,
                onClick = {
                    onDismissRequest()
                    action.onClick()
                },
            )
        }
    }
}

@Composable
fun ContextMenu(
    label: String,
    actions: List<ContextMenuAction>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = LocalTsuzukiComponentLabels.current
    val haptics = TsuzukiTheme.haptics
    Box(
        modifier.combinedClickable(
            onClick = onClick, onLongClickLabel = labels.showActionsFor(label),
            onLongClick = { haptics.select(); expanded = true },
        ),
    ) {
        content()
        GlassMenu(expanded, label, actions, onDismissRequest = { expanded = false })
    }
}

