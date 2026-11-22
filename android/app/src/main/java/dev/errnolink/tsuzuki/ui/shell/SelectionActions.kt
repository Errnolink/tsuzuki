package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

/**
 * The in-list selection actions shared by every selection-mode host: a row of pills above the list
 * while selection is on. [animated] swaps the row in with the shell's selection crossfade (the
 * feed hosts); the plain variant appears with the item (Updates).
 */
fun LazyListScope.selectionActions(
    visible: Boolean,
    actions: List<ShellSheetAction>,
    modifier: Modifier = Modifier,
    key: String = "selection-actions",
    animated: Boolean = false,
) {
    if (!visible && !animated) return
    item(key) {
        val pills: @Composable () -> Unit = {
            Row(
                modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = TsuzukiTheme.spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { action ->
                    PillButton(
                        action.label,
                        action.onClick,
                        destructive = action.destructive,
                        prominent = false,
                        minWidth = 0.dp,
                    )
                }
            }
        }
        if (animated) {
            val reduced = TsuzukiTheme.motion.reduced
            AnimatedContent(
                targetState = visible,
                transitionSpec = { selectionSwapTransform(reduced) },
                label = "SelectionActions",
            ) { shown ->
                if (shown) pills()
            }
        } else {
            pills()
        }
    }
}
