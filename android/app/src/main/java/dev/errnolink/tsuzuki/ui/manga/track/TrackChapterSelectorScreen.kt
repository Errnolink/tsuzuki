package dev.errnolink.tsuzuki.ui.manga.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackChapterSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val state by screenModel.state.collectAsState()
        val range = remember { screenModel.getRange() }
        DetentSheet(true, stringResource(MR.strings.chapters), navigator::pop) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelNumberPicker(
                    items = range.map { it as Number }.toImmutableList(),
                    startIndex = state.selection,
                    size = DpSize(160.dp, 128.dp),
                    onSelectionChanged = { screenModel.setSelection(it) },
                )
            }
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(stringResource(MR.strings.action_ok), {
                    screenModel.setChapter()
                    navigator.pop()
                })
            }
        }
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.lastChapterRead.toInt())) {

        fun getRange(): Iterable<Int> {
            val endRange = if (track.totalChapters > 0) {
                track.totalChapters
            } else {
                10000
            }
            return 0..endRange.toInt()
        }

        fun setSelection(selection: Int) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setChapter() {
            screenModelScope.launchNonCancellable {
                tracker.setRemoteLastChapterRead(track.toDbTrack(), state.value.selection)
            }
        }

        data class State(
            val selection: Int,
        )
    }
}
