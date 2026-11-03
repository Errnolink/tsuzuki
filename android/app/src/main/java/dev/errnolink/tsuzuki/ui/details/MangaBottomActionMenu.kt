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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
        onBookmarkClicked?.let { add(BottomMenuAction("Bookmark", TsuzukiIcons.Bookmark, it)) }
        onRemoveBookmarkClicked?.let { add(BottomMenuAction("Unbookmark", TsuzukiIcons.BookmarkFilled, it)) }
        onMarkAsReadClicked?.let { add(BottomMenuAction("Read", TsuzukiIcons.Check, it)) }
        onMarkAsUnreadClicked?.let { add(BottomMenuAction("Unread", TsuzukiIcons.X, it)) }
        onMarkPreviousAsReadClicked?.let { add(BottomMenuAction("Prev unread", TsuzukiIcons.CheckCheck, it)) }
        onDownloadClicked?.let { add(BottomMenuAction("Download", TsuzukiIcons.Download, it)) }
        onDeleteClicked?.let { add(BottomMenuAction("Delete", TsuzukiIcons.Trash2, it)) }
    }
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
                actions.chunked(4).forEach { rowActions ->
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
