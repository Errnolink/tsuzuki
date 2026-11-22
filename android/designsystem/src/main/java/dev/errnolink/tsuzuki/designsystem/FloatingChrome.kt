package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

@Immutable
class TopBarSearch(
    val state: MorphingSearchState,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val placeholder: String,
    val onSearch: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val trailing: (@Composable RowScope.() -> Unit)? = null,
    /**
     * Round 12: this search lives at the bottom and rides the keyboard even on a pushed page
     * (the round-10 rule re-affirmed in review for Global search). The shell's bottom dock hosts it;
     * the top row shows no magnifier and no field for it.
     */
    val bottomHosted: Boolean = false,
) {
    override fun toString(): String = "TopBarSearch(placeholder=$placeholder)"
}

@Composable
fun rememberTopBarSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    bottomHosted: Boolean = false,
): TopBarSearch {
    val state = rememberMorphingSearchState()
    return TopBarSearch(state, query, onQueryChange, placeholder, onSearch, onClose, trailing, bottomHosted)
}

object TsuzukiTopBar {
    var bodyHeightDp by mutableFloatStateOf(64f)
}
