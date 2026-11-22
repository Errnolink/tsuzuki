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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.Bell
import dev.errnolink.tsuzuki.designsystem.BellFill
import dev.errnolink.tsuzuki.designsystem.BooksFill
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.Compass
import dev.errnolink.tsuzuki.designsystem.CompassFill
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
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
     val colors = TsuzukiTheme.colors
    // Kasane tab bar: the selected tab pours into its filled icon. The swap cross-fades on the
    // nav spring — a tab keeps its own icon identity and never hard-swaps mid-switch (the
    // collapsed circle cross-fades only when the tab actually changes).
    val variants = when (tab) {
        LibraryTab -> TsuzukiIcons.Library to TsuzukiIcons.BooksFill
        UpdatesTab -> TsuzukiIcons.Bell to TsuzukiIcons.BellFill
        BrowseTab -> TsuzukiIcons.Compass to TsuzukiIcons.CompassFill
        FeedTab -> TsuzukiIcons.Calendar to TsuzukiIcons.Calendar
        HistoryTab -> TsuzukiIcons.History to TsuzukiIcons.History
        MoreTab -> TsuzukiIcons.Ellipsis to TsuzukiIcons.Ellipsis
        else -> return
    }
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = KasaneSprings.nav(TsuzukiTheme.motion.reduced),
        label = "Tab icon fill",
    )
    val tint = lerp(shellTabUnselected(colors.text), colors.text, fill)
    Box {
        if (variants.first == variants.second) {
            Icon(variants.first, null, Modifier.size(23.dp), tint = tint)
        } else {
            Icon(
                variants.first,
                null,
                Modifier.size(23.dp).graphicsLayer { alpha = 1f - fill },
                tint = tint,
            )
            Icon(
                variants.second,
                null,
                Modifier.size(23.dp).graphicsLayer { alpha = fill },
                tint = tint,
            )
        }
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
            .background(colors.destructive)
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
            color = colors.background,
            maxLines = 1,
        )
    }
}
