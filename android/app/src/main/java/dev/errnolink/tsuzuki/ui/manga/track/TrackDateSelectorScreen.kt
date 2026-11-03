package dev.errnolink.tsuzuki.ui.manga.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

data class TrackDateSelectorScreen(
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

        val canRemove = if (start) {
            track.startDate > 0
        } else {
            track.finishDate > 0
        }
        val minDate = if (!start && track.startDate > 0) {
            Instant.ofEpochMilli(track.startDate).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            null
        }
        val today = remember { LocalDate.now(ZoneOffset.UTC) }
        val maxDate = if (start && track.finishDate > 0) {
            minOf(today, Instant.ofEpochMilli(track.finishDate).atZone(ZoneOffset.UTC).toLocalDate())
        } else {
            today
        }
        val floorDate = LocalDate.of(MinYear, 1, 1)
        val initialDate = remember { Instant.ofEpochMilli(screenModel.initialSelection).atZone(ZoneOffset.UTC).toLocalDate() }
        val minYear = minDate?.year ?: MinYear
        val maxYear = maxDate.year
        var year by rememberSaveable { mutableIntStateOf(initialDate.year.coerceIn(minYear, maxYear)) }
        var month by rememberSaveable { mutableIntStateOf(initialDate.monthValue) }
        var day by rememberSaveable { mutableIntStateOf(initialDate.dayOfMonth) }

        val earliest = maxOf(minDate ?: floorDate, LocalDate.of(year, 1, 1))
        val latest = minOf(maxDate, LocalDate.of(year, 12, 31))
        val months = (earliest.monthValue..latest.monthValue).toList()
        val effectiveMonth = month.coerceIn(earliest.monthValue, latest.monthValue)
        val minDay = if (year == earliest.year && effectiveMonth == earliest.monthValue) earliest.dayOfMonth else 1
        val maxDay = if (year == latest.year && effectiveMonth == latest.monthValue) {
            latest.dayOfMonth
        } else {
            YearMonth.of(year, effectiveMonth).lengthOfMonth()
        }
        val days = (minDay..maxDay).toList()
        val effectiveDay = day.coerceIn(minDay, maxDay)
        val years = (minYear..maxYear).toList()

        DetentSheet(
            true,
            if (start) {
                stringResource(MR.strings.track_started_reading_date)
            } else {
                stringResource(MR.strings.track_finished_reading_date)
            },
            navigator::pop,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                key(days.first(), days.last()) {
                    WheelNumberPicker(
                        items = days.map { it as Number }.toImmutableList(),
                        startIndex = days.indexOf(effectiveDay),
                        size = DpSize(72.dp, 120.dp),
                        onSelectionChanged = { day = days[it] },
                    )
                }
                key(months.first(), months.last()) {
                    WheelTextPicker(
                        items = months.map { Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }.toImmutableList(),
                        startIndex = months.indexOf(effectiveMonth),
                        size = DpSize(112.dp, 120.dp),
                        onSelectionChanged = { month = months[it] },
                    )
                }
                WheelNumberPicker(
                    items = years.map { it as Number }.toImmutableList(),
                    startIndex = years.indexOf(year),
                    size = DpSize(88.dp, 120.dp),
                    onSelectionChanged = { year = years[it] },
                )
            }
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRemove) {
                    PillButton(stringResource(MR.strings.action_remove), { screenModel.confirmRemoveDate(navigator) }, prominent = false)
                }
                PillButton(stringResource(MR.strings.action_cancel), navigator::pop, prominent = false)
                PillButton(stringResource(MR.strings.action_ok), {
                    screenModel.setDate(
                        LocalDate.of(year, effectiveMonth, effectiveDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    )
                    navigator.pop()
                })
            }
        }
    }

    private companion object {
        val MinYear = 1900
    }

    private class Model(
        private val track: Track,
        private val tracker: Tracker,
        private val start: Boolean,
    ) : ScreenModel {

        // In UTC
        val initialSelection: Long
            get() {
                val millis = (if (start) track.startDate else track.finishDate)
                    .takeIf { it != 0L }
                    ?: Instant.now().toEpochMilli()
                return millis.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
            }

        // In UTC
        fun setDate(millis: Long) {
            // Convert to local time
            val localMillis = millis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault())
            screenModelScope.launchNonCancellable {
                if (start) {
                    tracker.setRemoteStartDate(track.toDbTrack(), localMillis)
                } else {
                    tracker.setRemoteFinishDate(track.toDbTrack(), localMillis)
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, tracker.id, start))
        }
    }
}
