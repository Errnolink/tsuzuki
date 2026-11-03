package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun Flow<ReaderViewModel.State>.readerChromeState(): Flow<ReaderViewModel.State> = distinctUntilChanged { old, new ->
    !old.menuVisible && !new.menuVisible &&
        !old.ehUtilsVisible && !new.ehUtilsVisible &&
        old.dialog == null && new.dialog == null &&
        old.brightnessOverlayValue == new.brightnessOverlayValue
}

@Composable
fun ReaderPageCounter(state: StateFlow<ReaderViewModel.State>, modifier: Modifier = Modifier) {
    val page by state.collectAsState()
    ReaderPageIndicator(currentPage = page.currentPageText, totalPages = page.totalPages, modifier = modifier)
}
