package dev.errnolink.tsuzuki.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

fun initialPreparedDetailsState(current: MangaScreenModel.State): MangaScreenModel.State = current

@OptIn(ExperimentalCoroutinesApi::class)
fun prepareDetailsFlow(states: Flow<MangaScreenModel.State>): Flow<MangaScreenModel.State> =
    states.mapLatest { state ->
        if (state is MangaScreenModel.State.Success) {
            withContext(Dispatchers.Default) {
                state.processedChapters
                state.chapterListItems
            }
        }
        state
    }

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberPreparedDetailsState(states: StateFlow<MangaScreenModel.State>): State<MangaScreenModel.State> {
    val prepared = remember(states) { prepareDetailsFlow(states) }
    return prepared.collectAsStateWithLifecycle(initialValue = initialPreparedDetailsState(states.value))
}
