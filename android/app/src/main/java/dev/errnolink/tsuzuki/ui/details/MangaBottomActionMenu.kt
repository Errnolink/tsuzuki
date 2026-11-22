package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.CheckCheck
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private data class BottomMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun MangaBottomActionMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    glass: GlassContext? = null,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onMarkAsReadClicked: (() -> Unit)? = null,
    onMarkAsUnreadClicked: (() -> Unit)? = null,
    onMarkPreviousAsReadClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
) {
    val motion = TsuzukiTheme.motion
    val actions = buildList {
        onBookmarkClicked?.let { add(BottomMenuAction(stringResource(MR.strings.tsuzuki_bookmark), TsuzukiIcons.Bookmark, it)) }
        onRemoveBookmarkClicked?.let { add(BottomMenuAction(stringResource(MR.strings.tsuzuki_unbookmark), TsuzukiIcons.BookmarkFilled, it)) }
        onMarkAsReadClicked?.let { add(BottomMenuAction(stringResource(MR.strings.tsuzuki_read), TsuzukiIcons.Check, it)) }
        onMarkAsUnreadClicked?.let { add(BottomMenuAction(stringResource(MR.strings.unread), TsuzukiIcons.X, it)) }
        onMarkPreviousAsReadClicked?.let { add(BottomMenuAction(stringResource(MR.strings.tsuzuki_prev_unread), TsuzukiIcons.CheckCheck, it)) }
        onDownloadClicked?.let { add(BottomMenuAction(stringResource(MR.strings.action_download), TsuzukiIcons.Download, it)) }
        onDeleteClicked?.let { add(BottomMenuAction(stringResource(MR.strings.action_delete), TsuzukiIcons.Trash2, it)) }
    }
    // SEL-02: the selection empties while the bar is still sliding out; the actions it was
    // opened with stay frozen so the exit never re-labels or empties it.
    var frozenActions by remember { mutableStateOf(emptyList<BottomMenuAction>()) }
    if (actions.isNotEmpty()) frozenActions = actions
    val shownActions = actions.ifEmpty { frozenActions }
    AnimatedVisibility(
        visible = visible && actions.isNotEmpty(),
        enter = if (motion.reduced) fadeIn(M3Motion.reducedFade()) else slideInVertically(M3Motion.enter()) { it },
        exit = if (motion.reduced) fadeOut(M3Motion.reducedFade()) else slideOutVertically(M3Motion.exit()) { it },
        modifier = modifier,
    ) {
        DetailGlassGroup(
            context = glass,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shownActions.chunked(4).forEach { rowActions ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        rowActions.forEach { action ->
                            DetailLabeledAction(action.label, action.icon, action.onClick, Modifier.weight(1f))
                        }
                        repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
