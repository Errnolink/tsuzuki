package dev.errnolink.tsuzuki.ui.manga.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import logcat.LogPriority

data class TrackerRemoveScreen(
    private val mangaId: Long,
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                track = track,
                tracker = Injekt.get<TrackerManager>().get(serviceId)!!,
            )
        }
        val serviceName = screenModel.getName()
        var removeRemoteTrack by remember { mutableStateOf(false) }
        DetentSheet(true, stringResource(MR.strings.track_delete_title, serviceName), navigator::pop) {
            TsuzukiText(
                stringResource(MR.strings.track_delete_text, serviceName),
                Modifier.padding(horizontal = 16.dp),
                TsuzukiTheme.typography.body,
                TsuzukiTheme.colors.secondary,
            )
            if (screenModel.isDeletable()) {
                InsetGroupedList {
                    GroupedRow(
                        title = stringResource(MR.strings.track_delete_remote_text, serviceName),
                        checked = removeRemoteTrack,
                        onCheckedChange = { removeRemoteTrack = it },
                    )
                }
            }
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(
                    stringResource(MR.strings.action_ok),
                    {
                        screenModel.unregisterTracking(serviceId)
                        if (removeRemoteTrack) screenModel.deleteMangaFromService()
                        navigator.pop()
                    },
                    destructive = true,
                )
            }
        }
    }

    private class Model(
        private val mangaId: Long,
        private val track: Track,
        private val tracker: Tracker,
        private val deleteTrack: DeleteTrack = Injekt.get(),
    ) : ScreenModel {

        fun getName() = tracker.name

        fun isDeletable() = tracker is DeletableTracker

        fun deleteMangaFromService() {
            screenModelScope.launchNonCancellable {
                try {
                    (tracker as DeletableTracker).delete(track)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to delete entry from service" }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            screenModelScope.launchNonCancellable { deleteTrack.await(mangaId, serviceId) }
        }
    }
}
