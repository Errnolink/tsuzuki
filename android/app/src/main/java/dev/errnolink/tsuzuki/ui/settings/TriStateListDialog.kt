package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.res.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private enum class State {
    CHECKED,
    INVERSED,
    UNCHECKED,
}

@Composable
fun <T> TriStateListDialog(
    title: String,
    message: String? = null,
    items: List<T>,
    initialChecked: List<T>,
    initialInversed: List<T>,
    itemLabel: @Composable (T) -> String,
    onDismissRequest: () -> Unit,
    onValueChanged: (newIncluded: List<T>, newExcluded: List<T>) -> Unit,
) {
    val selected = remember {
        items
            .map {
                when (it) {
                    in initialChecked -> State.CHECKED
                    in initialInversed -> State.INVERSED
                    else -> State.UNCHECKED
                }
            }
            .toMutableStateList()
    }
    TriStateChoiceSheet(
        visible = true,
        title = title,
        message = message,
        doneLabel = stringResource(MR.strings.action_ok),
        options = items.mapIndexed { index, item ->
            Triple<String, Int, (Int) -> Unit>(
                itemLabel(item),
                selected[index].ordinal,
            ) { newState ->
                selected[index] = State.entries[newState]
            }
        },
        onDismissRequest = onDismissRequest,
        onDone = {
            val included = items.mapIndexedNotNull { index, category ->
                if (selected[index] == State.CHECKED) category else null
            }
            val excluded = items.mapIndexedNotNull { index, category ->
                if (selected[index] == State.INVERSED) category else null
            }
            onValueChanged(included, excluded)
            onDismissRequest()
        },
    )
}
