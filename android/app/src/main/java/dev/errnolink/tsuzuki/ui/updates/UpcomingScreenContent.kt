package dev.errnolink.tsuzuki.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ChevronLeft
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.TrendingUp
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.coroutines.launch
import mihon.feature.upcoming.UpcomingScreenModel
import mihon.feature.upcoming.UpcomingUIModel
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun UpcomingScreenContent(
    state: UpcomingScreenModel.State,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickUpcoming: (Manga) -> Unit,
    showUpdatingMangas: () -> Unit,
    hideUpdatingMangas: () -> Unit,
    isPredictReleaseDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val items = if (state.isShowingUpdatingMangas) state.updatingItems else state.items
    val events = if (state.isShowingUpdatingMangas) state.updatingEvents else state.events
    val indexes = if (state.isShowingUpdatingMangas) state.updatingHeaderIndexes else state.headerIndexes
    val loading = if (state.isShowingUpdatingMangas) state.isLoadingUpdating else state.isLoadingUpcoming
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    ShellRefresh(refreshing = false, onRefresh = { LibraryUpdateJob.startNow(context) }) {
    ShellList(
        title = "Upcoming",
        modifier = modifier,
        state = listState,
        navigateUp = { navigator.pop() },
        itemSpacing = 0.dp,
        actions = buildList {
            if (isPredictReleaseDate) {
                add(
                    ShellTopAction(
                        "Titles to update",
                        TsuzukiIcons.TrendingUp,
                        {
                            if (state.isShowingUpdatingMangas) hideUpdatingMangas() else showUpdatingMangas()
                        },
                        active = state.isShowingUpdatingMangas,
                    ),
                )
            }
            add(ShellTopAction("Upcoming guide", TsuzukiIcons.Info, { uriHandler.openUri(Constants.URL_HELP_UPCOMING) }))
        },
    ) {
        item("calendar") {
            UpcomingCalendar(state.selectedYearMonth, selectedDay, events, setSelectedYearMonth) { date ->
                selectedDay = date
                indexes[date]?.let { scope.launch { listState.scrollToItem(it + 2) } }
            }
        }
        if (loading) {
            items(count = 5, key = { "upcoming-skeleton-$it" }, contentType = { "skeleton" }) { index ->
                dev.errnolink.tsuzuki.ui.shell.UpdateRowSkeleton(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = if (index == 0) dev.errnolink.tsuzuki.designsystem.TsuzukiCorners.group else 0.dp,
                        topEnd = if (index == 0) dev.errnolink.tsuzuki.designsystem.TsuzukiCorners.group else 0.dp,
                        bottomStart = if (index == 4) dev.errnolink.tsuzuki.designsystem.TsuzukiCorners.group else 0.dp,
                        bottomEnd = if (index == 4) dev.errnolink.tsuzuki.designsystem.TsuzukiCorners.group else 0.dp,
                    ),
                    divider = index < 4,
                    index = index,
                )
            }
        }
        else if (items.isEmpty()) item("empty") {
            ShellEmpty("No expected releases", "Release estimates appear here after titles in your library have an update schedule.")
        }
        itemsIndexed(items, key = { _, item ->
            when (item) {
                is UpcomingUIModel.Header -> "date-${item.date}"
                is UpcomingUIModel.Item -> "manga-${item.manga.id}"
            }
        }, contentType = { _, item -> if (item is UpcomingUIModel.Header) "date" else "manga" }) { index, item ->
            when (item) {
                is UpcomingUIModel.Header -> TsuzukiText(
                    "${relativeDateText(item.date)} · ${item.mangaCount}",
                    Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
                    TsuzukiTheme.typography.footnote,
                    TsuzukiTheme.colors.secondary,
                )
                is UpcomingUIModel.Item -> {
                    val first = items.getOrNull(index - 1) !is UpcomingUIModel.Item
                    val last = items.getOrNull(index + 1) !is UpcomingUIModel.Item
                    val separator = TsuzukiTheme.colors.separator
                    val shape = RoundedCornerShape(
                        topStart = if (first) TsuzukiCorners.group else 0.dp, topEnd = if (first) TsuzukiCorners.group else 0.dp,
                        bottomStart = if (last) TsuzukiCorners.group else 0.dp, bottomEnd = if (last) TsuzukiCorners.group else 0.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(shape)
                            .background(TsuzukiTheme.colors.grouped).clickable { onClickUpcoming(item.manga) }
            .then(if (!last) Modifier.insetDivider(64.dp) else Modifier)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShellCover(item.manga.asMangaCover(), item.manga.title, Modifier.width(40.dp), fixedRatio = 2f / 3f)
                        TsuzukiText(item.manga.title, Modifier.weight(1f), TsuzukiTheme.typography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun UpcomingCalendar(
    month: YearMonth,
    selectedDay: LocalDate,
    events: Map<LocalDate, Int>,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val locale = Locale.getDefault()
    val firstWeekday = WeekFields.of(locale).firstDayOfWeek
    val firstOffset = (month.atDay(1).dayOfWeek.value - firstWeekday.value + 7) % 7
    val weeks = (firstOffset + month.lengthOfMonth() + 6) / 7
    val label = remember(month, locale) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(TsuzukiCorners.group)).background(colors.grouped).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsuzukiText(label, Modifier.weight(1f).padding(start = 8.dp), TsuzukiTheme.typography.headline)
            ShellAction("Previous month", TsuzukiIcons.ChevronLeft, { onMonth(month.minusMonths(1)) })
            ShellAction("Next month", TsuzukiIcons.ChevronRight, { onMonth(month.plusMonths(1)) })
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    TsuzukiText(firstWeekday.plus(column.toLong()).getDisplayName(TextStyle.NARROW, locale), style = TsuzukiTheme.typography.caption1, color = colors.secondary)
                }
            }
        }
        repeat(weeks) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - firstOffset + 1
                    if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else {
                        val date = month.atDay(number)
                        val isSelected = date == selectedDay
                        val count = events[date] ?: 0
                        Column(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .clickable(role = Role.Button) { onDay(date) }
                                .semantics { selected = isSelected; contentDescription = "$date, $count expected releases" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(32.dp).background(if (isSelected) colors.accent else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                TsuzukiText(number.toString(), style = TsuzukiTheme.typography.subhead, color = if (isSelected) colors.onAccent else if (date == LocalDate.now()) colors.accent else colors.text)
                            }
                            Box(
                                Modifier.size(8.dp)
                                    .background(if (count > 0) colors.accent else Color.Transparent, CircleShape)
                                    .border(1.dp, if (count > 0) colors.separator else Color.Transparent, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
