package dev.errnolink.tsuzuki.ui.manga.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackDateRemoverScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
                start = start,
            )
        }
        val serviceName = screenModel.getServiceName()
        val message = if (start) {
            stringResource(MR.strings.track_remove_start_date_conf_text, serviceName)
        } else {
            stringResource(MR.strings.track_remove_finish_date_conf_text, serviceName)
        }
        DetentSheet(true, stringResource(MR.strings.track_remove_date_conf_title), navigator::pop) {
            TsuzukiText(
                message,
                Modifier.padding(horizontal = 16.dp),
                TsuzukiTheme.typography.body,
                TsuzukiTheme.colors.secondary,
            )
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(
                    stringResource(MR.strings.action_remove),
                    {
                        screenModel.removeDate()
                        navigator.popUntil { it is TrackInfoDialogHomeScreen }
                    },
                    destructive = true,
                )
            }
        }
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        fun getServiceName() = tracker.name

        fun removeDate() {
            screenModelScope.launchNonCancellable {
                if (start) {
                    tracker.setRemoteStartDate(track.toDbTrack(), 0)
                } else {
                    tracker.setRemoteFinishDate(track.toDbTrack(), 0)
                }
            }
        }
    }
}
