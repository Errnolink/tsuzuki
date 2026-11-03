package dev.errnolink.tsuzuki.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.GripVertical
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
    divider: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    var showActions by remember { mutableStateOf(false) }
    val colors = TsuzukiTheme.colors
    Row(
        modifier.fillMaxWidth().clip(shape).background(colors.grouped)
            .clickable(onClick = onRename)
            .semantics {
                customActions = buildList {
                    onMoveUp?.let { up -> add(CustomAccessibilityAction("Move up") { up(); true }) }
                    onMoveDown?.let { down -> add(CustomAccessibilityAction("Move down") { down(); true }) }
                }
            }
            .then(if (divider) Modifier.insetDivider(52.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                TsuzukiIcons.GripVertical,
                stringResource(MR.strings.tsuzuki_reorder_category),
                Modifier.draggableHandle().padding(12.dp),
                tint = colors.secondary,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            TsuzukiText(category.name, color = if (category.hidden) colors.secondary else colors.text)
            if (category.hidden) {
                TsuzukiText("Hidden", style = TsuzukiTheme.typography.caption1, color = colors.secondary)
            }
        }
        Box {
            ShellAction("Category actions", TsuzukiIcons.Ellipsis, { showActions = true })
            GlassMenu(
                showActions,
                category.name,
                listOf(
                    ContextMenuAction(stringResource(MR.strings.action_rename_category), icon = { Icon(TsuzukiIcons.Pencil, null, tint = colors.text) }, onClick = onRename),
                    ContextMenuAction(if (category.hidden) "Show category" else "Hide category", icon = { Icon(TsuzukiIcons.EyeOff, null, tint = colors.text) }, onClick = onHide),
                    ContextMenuAction(stringResource(MR.strings.delete_category), icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) }, destructive = true, onClick = onDelete),
                ),
                onDismissRequest = { showActions = false },
            )
        }
    }
}

@Composable
fun CategoryInlineAddRow(
    existingNames: List<String>,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "New category",
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
) {
    val colors = TsuzukiTheme.colors
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val valid = draft.text.isNotBlank() && draft.text !in existingNames
    val confirm: () -> Unit = {
        if (valid) {
            onCreate(draft.text.trim())
            draft = TextFieldValue("")
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier.fillMaxWidth().clip(shape).background(colors.grouped)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f)
                .focusRequester(focusRequester)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = placeholder },
            textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 12.dp)) {
                    if (draft.text.isEmpty()) TsuzukiText(placeholder, color = colors.secondary, maxLines = 1)
                    inner()
                }
            },
        )
        IconButton("Add category", confirm, enabled = valid) {
            Icon(TsuzukiIcons.Check, null, Modifier.size(20.dp), tint = if (valid) colors.accent else colors.secondary)
        }
    }
}
