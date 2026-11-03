package dev.errnolink.tsuzuki.ui.manga.track
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackStatusSelectorScreen(
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
        DetentSheet(true, stringResource(MR.strings.status), navigator::pop) {
            InsetGroupedList {
                selections.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) GroupDivider()
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.RadioButton) { screenModel.setSelection(key) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TsuzukiText(value?.let { stringResource(it) } ?: "", Modifier.weight(1f))
                        if (state.selection == key) TsuzukiCheckMark()
                    }
                }
            }
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(stringResource(MR.strings.action_ok), {
                    screenModel.setStatus()
                    navigator.pop()
                })
            }
        }
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State(track.status)) {

        fun getSelections(): Map<Long, StringResource?> {
            return tracker.getStatusList().associateWith { tracker.getStatus(it) }
        }

        fun setSelection(selection: Long) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            screenModelScope.launchNonCancellable {
                tracker.setRemoteStatus(track.toDbTrack(), state.value.selection)
            }
        }

        data class State(
            val selection: Long,
        )
    }
}
