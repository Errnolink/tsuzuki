package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.Bell
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.Compass
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.HistoryLucide
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab

val LocalShellTabSelected = compositionLocalOf { false }

@Composable
fun ShellTabIcon(
    tab: Tab,
    selected: Boolean = LocalShellTabSelected.current,
    badgeCount: Int = 0,
) {
    val icon = when (tab) {
        LibraryTab -> TsuzukiIcons.BookOpen
        UpdatesTab -> TsuzukiIcons.Bell
        FeedTab -> TsuzukiIcons.Calendar
        HistoryTab -> TsuzukiIcons.HistoryLucide
        BrowseTab -> TsuzukiIcons.Compass
        MoreTab -> TsuzukiIcons.Ellipsis
        else -> return
    }
    val colors = TsuzukiTheme.colors
    val tint = if (selected) colors.text else colors.secondary
    Box {
        Icon(icon, null, Modifier.size(23.dp), tint = tint)
        if (badgeCount > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)) {
                ShellTabBadge(badgeCount)
            }
        }
    }
}

@Composable
fun ShellTabBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val text = if (count > 99) "99+" else count.toString()
    val colors = TsuzukiTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.accent)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        TsuzukiText(
            text = text,
            style = TsuzukiTheme.typography.caption2.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            color = colors.onAccent,
            maxLines = 1,
        )
    }
}
