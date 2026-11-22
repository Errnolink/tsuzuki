package dev.errnolink.tsuzuki.ui.states

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CategoryEmptyState(onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    CategoryEmptyCurrent(onBrowse, modifier)
}

@Composable
fun InCategoryEmptyState(modifier: Modifier = Modifier) {
    InCategoryEmptyCurrent(modifier)
}

@Composable
fun LibrarySearchEmptyState(
    query: String?,
    modifier: Modifier = Modifier,
    inGrid: Boolean = false,
) {
    SearchEmptyCurrent(query, inGrid, modifier)
}

@Composable
fun FeedEmptyState(description: String, modifier: Modifier = Modifier) {
    FeedEmptyCurrent(description, modifier)
}

@Composable
fun HistoryEmptyState(searching: Boolean, modifier: Modifier = Modifier) {
    HistoryEmptyCurrent(searching, modifier)
}
