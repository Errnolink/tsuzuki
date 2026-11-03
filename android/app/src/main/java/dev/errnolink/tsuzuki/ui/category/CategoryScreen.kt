package dev.errnolink.tsuzuki.ui.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onCreate: (String) -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    // KMK -->
    onClickHide: (Category) -> Unit,
    // KMK <--
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val categoriesState = remember { state.categories.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val item = categoriesState.removeAt(from.index)
        categoriesState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(state.categories) {
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(state.categories)
        }
    }

    ShellList(
        title = stringResource(MR.strings.categories),
        state = lazyListState,
        navigateUp = navigateUp,
        itemSpacing = 0.dp,
    ) {
        if (state.isEmpty) {
            item("categories-empty") {
                ShellEmpty("Organize your library", stringResource(MR.strings.information_empty_category))
            }
        } else {
            itemsIndexed(
                items = categoriesState,
                key = { _, category -> category.key },
            ) { index, category ->
                ReorderableItem(reorderableState, category.key) {
                    CategoryListItem(
                        modifier = Modifier.animateItem().padding(horizontal = TsuzukiSpacing.gutter),
                        category = category,
                        shape = RoundedCornerShape(
                            topStart = if (index == 0) TsuzukiCorners.group else 0.dp, topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                            bottomStart = 0.dp, bottomEnd = 0.dp,
                        ),
                        divider = true,
                        onRename = { onClickRename(category) },
                        onDelete = { onClickDelete(category) },
                        // KMK -->
                        onHide = { onClickHide(category) },
                        // KMK <--
                        onMoveUp = if (index > 0) {
                            { onChangeOrder(category, index - 1) }
                        } else {
                            null
                        },
                        onMoveDown = if (index < categoriesState.lastIndex) {
                            { onChangeOrder(category, index + 1) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item(key = "category-add") {
            CategoryInlineAddRow(
                existingNames = state.categories.map { it.name },
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

private val Category.key inline get() = "category-$id"
