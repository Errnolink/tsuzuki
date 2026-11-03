package dev.errnolink.tsuzuki.ui.category.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.category.CategoryInlineAddRow
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import eu.kanade.tachiyomi.ui.category.genre.SortTagScreenState
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TsuzukiSortTagScreen(
    state: SortTagScreenState.Success,
    onCreate: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickMoveUp: (String, Int) -> Unit,
    onClickMoveDown: (String, Int) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    ShellList(
        title = stringResource(SYMR.strings.action_edit_tags),
        state = lazyListState,
        navigateUp = navigateUp,
        itemSpacing = 0.dp,
    ) {
        if (state.isEmpty) {
            item("sort-tags-empty") {
                ShellEmpty(
                    stringResource(SYMR.strings.action_edit_tags),
                    stringResource(SYMR.strings.information_empty_tags),
                )
            }
        } else {
            itemsIndexed(
                items = state.tags,
                key = { _, tag -> "sort-tag-$tag" },
            ) { index, tag ->
                SortTagListItem(
                    modifier = Modifier.animateItem().padding(horizontal = TsuzukiSpacing.gutter),
                    tag = tag,
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                        topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                    divider = true,
                    canMoveUp = index != 0,
                    canMoveDown = index != state.tags.lastIndex,
                    onMoveUp = { onClickMoveUp(tag, index) },
                    onMoveDown = { onClickMoveDown(tag, index) },
                    onDelete = { onClickDelete(tag) },
                )
            }
        }
        item(key = "sort-tag-add") {
            CategoryInlineAddRow(
                existingNames = state.tags,
                onCreate = onCreate,
                placeholder = "New tag",
                modifier = Modifier.padding(horizontal = TsuzukiSpacing.gutter),
                shape = RoundedCornerShape(
                    topStart = if (state.isEmpty) TsuzukiCorners.group else 0.dp,
                    topEnd = if (state.isEmpty) TsuzukiCorners.group else 0.dp,
                    bottomStart = TsuzukiCorners.group,
                    bottomEnd = TsuzukiCorners.group,
                ),
            )
        }
    }
}

@Composable
private fun SortTagListItem(
    tag: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
    divider: Boolean = false,
) {
    var showActions by remember { mutableStateOf(false) }
    val colors = TsuzukiTheme.colors
    Row(
        modifier.fillMaxWidth().clip(shape).background(colors.grouped)
            .semantics {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction("Move up") { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction("Move down") { onMoveDown(); true })
                }
            }
            .then(if (divider) Modifier.insetDivider(16.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsuzukiText(tag, Modifier.weight(1f).padding(horizontal = 8.dp))
        Box {
            ShellAction("Tag actions", TsuzukiIcons.Ellipsis, { showActions = true })
            GlassMenu(
                showActions,
                tag,
                listOf(
                    ContextMenuAction("Move up", enabled = canMoveUp, icon = { Icon(TsuzukiIcons.ArrowUp, null, tint = colors.text) }, onClick = onMoveUp),
                    ContextMenuAction("Move down", enabled = canMoveDown, icon = { Icon(TsuzukiIcons.ArrowDown, null, tint = colors.text) }, onClick = onMoveDown),
                    ContextMenuAction("Delete tag", icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) }, destructive = true, onClick = onDelete),
                ),
                onDismissRequest = { showActions = false },
            )
        }
    }
}
