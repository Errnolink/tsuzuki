package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Stable
class PillSearchState {
    var expanded by mutableStateOf(false)
        private set

    fun expand() {
        expanded = true
    }

    fun collapse() {
        expanded = false
    }
}

@Immutable
class TopBarSearch(
    val state: PillSearchState,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val placeholder: String,
    val onSearch: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val trailing: (@Composable RowScope.() -> Unit)? = null,
)

@Composable
fun rememberPillSearchState(): PillSearchState = remember { PillSearchState() }

@Composable
fun rememberTopBarSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
): TopBarSearch {
    val state = rememberPillSearchState()
    return TopBarSearch(state, query, onQueryChange, placeholder, onSearch, onClose, trailing)
}

object TsuzukiTopBar {
    var bodyHeightDp by mutableFloatStateOf(64f)
}

@Composable
fun TopBarSearchField(search: TopBarSearch, modifier: Modifier = Modifier) {
    val colors = TsuzukiTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(search.state.expanded) {
        if (search.state.expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }
    Row(
        modifier.padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        TsuzukiIcon(TsuzukiIcons.Search, null, Modifier.size(19.dp), tint = colors.secondary)
        BasicTextField(
            value = search.query,
            onValueChange = search.onQueryChange,
            enabled = search.state.expanded,
            modifier = Modifier.weight(1f).focusRequester(focusRequester).padding(vertical = 11.dp).semantics {
                contentDescription = search.placeholder
            },
            textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search.onSearch?.invoke() }),
            decorationBox = { inner ->
                Box {
                    if (search.query.isEmpty()) {
                        TsuzukiText(search.placeholder, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        if (search.query.isNotEmpty()) {
            IconButton(
                LocalTsuzukiComponentLabels.current.clearSearch,
                { search.onQueryChange("") },
                enabled = search.state.expanded,
            ) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(18.dp)) }
        }
        search.trailing?.invoke(this)
    }
}
