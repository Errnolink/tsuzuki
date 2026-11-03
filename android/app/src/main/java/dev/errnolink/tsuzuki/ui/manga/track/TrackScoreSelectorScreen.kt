package dev.errnolink.tsuzuki.ui.manga.track
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackScoreSelectorScreen(
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
        val selections = remember { screenModel.getSelections() }
        val listState = rememberLazyListState(selections.indexOf(state.selection).coerceAtLeast(0))
        DetentSheet(true, stringResource(MR.strings.score), navigator::pop, scrollContent = false) {
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TsuzukiCorners.group))
                    .background(TsuzukiTheme.colors.grouped),
            ) {
                itemsIndexed(selections) { index, score ->
                    if (index > 0) {
                        Spacer(
                            Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth()
                                .height(TsuzukiSpacing.hairline)
                                .background(TsuzukiTheme.colors.separator),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.RadioButton) { screenModel.setSelection(score) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TsuzukiText(score, Modifier.weight(1f))
                        if (state.selection == score) TsuzukiCheckMark()
                    }
                }
            }
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(stringResource(MR.strings.action_ok), {
                    screenModel.setScore()
                    navigator.pop()
                })
            }
        }
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(tracker.displayScore(track))) {

        fun getSelections(): ImmutableList<String> {
            return tracker.getScoreList()
        }

        fun setSelection(selection: String) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setScore() {
            screenModelScope.launchNonCancellable {
                tracker.setRemoteScore(track.toDbTrack(), state.value.selection)
            }
        }

        data class State(
            val selection: String,
        )
    }
}
