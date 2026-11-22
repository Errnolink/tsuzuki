package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import tachiyomi.domain.history.model.HistoryWithRelations

@Composable
fun LibraryContinueHeader(
    recent: List<HistoryWithRelations>,
    onSeeAllContinue: () -> Unit,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recent.isEmpty()) return
    ContinueCardSurface(recent, onSeeAllContinue, onOpen, onResume, modifier)
}
