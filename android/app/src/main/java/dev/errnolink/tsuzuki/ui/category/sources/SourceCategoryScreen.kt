package dev.errnolink.tsuzuki.ui.category.sources

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
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.category.CategoryInlineAddRow
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreenState
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TsuzukiSourceCategoryScreen(
    state: SourceCategoryScreenState.Success,
    onCreate: (String) -> Unit,
    onClickRename: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    ShellList(
        title = stringResource(MR.strings.action_edit_categories),
        state = lazyListState,
        navigateUp = navigateUp,
        itemSpacing = 0.dp,
    ) {
        if (state.isEmpty) {
            item("source-categories-empty") {
                ShellEmpty(
                    stringResource(MR.strings.action_edit_categories),
                    stringResource(SYMR.strings.no_source_categories),
                )
            }
        } else {
            itemsIndexed(
                items = state.categories,
                key = { _, category -> "source-category-$category" },
            ) { index, category ->
                SourceCategoryListItem(
                    modifier = Modifier.animateItem().padding(horizontal = TsuzukiSpacing.gutter),
                    category = category,
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                        topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                    divider = true,
                    onRename = { onClickRename(category) },
                    onDelete = { onClickDelete(category) },
                )
            }
        }
        item(key = "source-category-add") {
            CategoryInlineAddRow(
                existingNames = state.categories,
                onCreate = onCreate,
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
private fun SourceCategoryListItem(
    category: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
    divider: Boolean = false,
) {
    var showActions by remember { mutableStateOf(false) }
    val colors = TsuzukiTheme.colors
    Row(
        modifier.fillMaxWidth().clip(shape).background(colors.grouped)
            .clickable(onClick = onRename)
            .then(if (divider) Modifier.insetDivider(16.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsuzukiText(category, Modifier.weight(1f).padding(horizontal = 8.dp))
        Box {
            ShellAction("Source category actions", TsuzukiIcons.Ellipsis, { showActions = true })
            GlassMenu(
                showActions,
                category,
                listOf(
                    ContextMenuAction("Rename", icon = { Icon(TsuzukiIcons.Pencil, null, tint = colors.text) }, onClick = onRename),
                    ContextMenuAction("Delete category", icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) }, destructive = true, onClick = onDelete),
                ),
                onDismissRequest = { showActions = false },
            )
        }
    }
}
