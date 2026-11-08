package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.DetailIcon

@Composable
fun ReaderTopBar(
    enabled: Boolean,
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onTitleClick: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onToolsClick: () -> Unit,
    modifier: Modifier = Modifier,
    compactTools: Boolean = false,
    onClickChapterList: () -> Unit = {},
    onClickSettings: () -> Unit = {},
    moreActions: List<ContextMenuAction> = emptyList(),
    pagesLeft: String? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassSurface(null, Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailIcon("Back", TsuzukiIcons.Back, navigateUp, enabled = enabled)
                Column(Modifier.weight(1f).clickable(enabled = enabled, onClick = onTitleClick).padding(end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    TsuzukiText(mangaTitle.orEmpty(), style = TsuzukiTheme.typography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TsuzukiText(
                        listOfNotNull(chapterTitle?.takeIf { it.isNotBlank() }, pagesLeft).joinToString(" · "),
                        style = TsuzukiTheme.typography.caption1,
                        color = TsuzukiTheme.colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        GlassSurface(null) {
            if (compactTools) {
                var menu by remember { mutableStateOf(false) }
                val topActions = remember(bookmarked, onToggleBookmarked, onToolsClick, moreActions) {
                    buildList {
                        add(
                            ContextMenuAction(
                                if (bookmarked) "Remove bookmark" else "Bookmark chapter",
                                icon = { Icon(if (bookmarked) TsuzukiIcons.BookmarkFilled else TsuzukiIcons.Bookmark, null, tint = TsuzukiTheme.colors.text) },
                                onClick = onToggleBookmarked,
                            ),
                        )
                        add(
                            ContextMenuAction(
                                "Reader tools",
                                icon = { Icon(TsuzukiIcons.Ellipsis, null, tint = TsuzukiTheme.colors.text) },
                                onClick = onToolsClick,
                            ),
                        )
                        addAll(moreActions)
                    }
                }
                Row {
                    DetailIcon("Chapters", TsuzukiIcons.Chapters, onClickChapterList, enabled = enabled)
                    DetailIcon("Reading settings", TsuzukiIcons.Settings, onClickSettings, enabled = enabled)
                    if (topActions.isNotEmpty()) {
                        Box {
                            DetailIcon("More reading actions", TsuzukiIcons.Ellipsis, { menu = true }, enabled = enabled)
                            GlassMenu(menu && enabled, "Reading actions", topActions, { menu = false })
                        }
                    }
                }
            } else {
                Row {
                    DetailIcon(if (bookmarked) "Remove bookmark" else "Bookmark chapter", if (bookmarked) TsuzukiIcons.BookmarkFilled else TsuzukiIcons.Bookmark, onToggleBookmarked, enabled = enabled)
                    DetailIcon("Reader tools", TsuzukiIcons.Ellipsis, onToolsClick, enabled = enabled)
                }
            }
        }
    }
}