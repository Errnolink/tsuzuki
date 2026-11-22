package dev.errnolink.tsuzuki.ui.reader

import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

fun Flow<ReaderViewModel.State>.readerChromeState(): Flow<ReaderViewModel.State> = distinctUntilChanged { old, new ->
    !old.menuVisible && !new.menuVisible &&
        !old.ehUtilsVisible && !new.ehUtilsVisible &&
        old.dialog == null && new.dialog == null &&
        old.brightnessOverlayValue == new.brightnessOverlayValue
}
