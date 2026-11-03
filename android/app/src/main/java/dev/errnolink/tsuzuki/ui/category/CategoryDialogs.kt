package dev.errnolink.tsuzuki.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Minus
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.category.visualName
import dev.errnolink.tsuzuki.ui.settings.ConfirmSheet
import dev.errnolink.tsuzuki.ui.settings.TextEntrySheet
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    categories: ImmutableList<String>,
    category: String,
) {
    TextEntrySheet(
        visible = true,
        title = stringResource(MR.strings.action_rename_category),
        initialValue = category,
        confirmLabel = stringResource(MR.strings.action_ok),
        errorText = stringResource(MR.strings.error_category_exists),
        supportsConfirm = { it.isNotBlank() && it != category && it !in categories },
        onConfirm = { onRename(it); true },
        onDismissRequest = onDismissRequest,
        placeholder = stringResource(MR.strings.name),
    )
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    category: String = "",
    title: String = stringResource(MR.strings.delete_category),
    text: String = stringResource(MR.strings.delete_category_confirmation, category),
) {
    ConfirmSheet(true, title, text, stringResource(MR.strings.action_delete), true, onDelete, onDismissRequest)
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: ImmutableList<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    var selection by remember(initialSelection) { mutableStateOf(initialSelection.toList()) }
    val colors = TsuzukiTheme.colors
    val nothingIncluded = selection.isNotEmpty() && selection.none {
        it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include
    }
    DetentSheet(true, stringResource(MR.strings.action_move_category), onDismissRequest) {
        if (selection.isEmpty()) {
            TsuzukiText(stringResource(MR.strings.information_empty_category_dialog))
        } else {
            selection.forEachIndexed { index, checkbox ->
                val state = when (checkbox) {
                    is CheckboxState.State.Checked, is CheckboxState.TriState.Include -> ToggleableState.On
                    is CheckboxState.TriState.Exclude -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TsuzukiTheme.spacing.touchTarget)
                        .triStateToggleable(
                            state = state,
                            onClick = {
                                selection = selection.toMutableList().also { it[index] = checkbox.next() }
                            },
                        )
                        .padding(horizontal = TsuzukiSpacing.row, vertical = TsuzukiSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
                ) {
                    TsuzukiText(checkbox.value.visualName, Modifier.weight(1f))
                    when (state) {
                        ToggleableState.On -> Icon(TsuzukiIcons.Check, null, Modifier.size(20.dp), tint = colors.accent)
                        ToggleableState.Indeterminate -> Icon(TsuzukiIcons.Minus, null, Modifier.size(20.dp), tint = colors.accent)
                        ToggleableState.Off -> {}
                    }
                }
                if (index < selection.lastIndex) {
                    Spacer(
                        Modifier.padding(start = TsuzukiSpacing.row).fillMaxWidth()
                            .height(TsuzukiSpacing.hairline)
                            .background(colors.separator),
                    )
                }
            }
            if (nothingIncluded) {
                TsuzukiText(
                    "This removes the title from every category below.",
                    Modifier.padding(horizontal = TsuzukiSpacing.row),
                    TsuzukiTheme.typography.footnote,
                    colors.secondary,
                )
            }
            PillButton(
                stringResource(if (nothingIncluded) MR.strings.action_remove_everything else MR.strings.action_ok),
                {
                    onConfirm(
                        selection.filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }.map { it.value.id },
                        selection.filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }.map { it.value.id },
                    )
                    onDismissRequest()
                },
                destructive = nothingIncluded,
            )
        }
        PillButton(stringResource(MR.strings.action_edit_categories), { onDismissRequest(); onEditCategories() }, prominent = false)
    }
}
