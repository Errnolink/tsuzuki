package dev.errnolink.tsuzuki.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.presentation.util.toDurationString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun LazyListScope.statsSections(state: StatsScreenState.Success) {
    item(key = "stats-overview") {
        OverviewSection(state.overview)
    }
    item(key = "stats-titles") {
        TitlesStats(state.titles)
    }
    item(key = "stats-chapters") {
        ChapterStats(state.chapters)
    }
    item(key = "stats-trackers") {
        TrackerStats(state.trackers)
    }
}

@Composable
private fun OverviewSection(data: StatsData.Overview) {
    val none = stringResource(MR.strings.none)
    val context = LocalContext.current
    val readDurationString = remember(data.totalReadDuration) {
        data.totalReadDuration
            .toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = none)
    }
    InsetGroupedList(title = stringResource(MR.strings.label_overview_section)) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = 16.dp, horizontal = 16.dp),
        ) {
            StatsOverviewItem(
                title = data.libraryMangaCount.toString(),
                subtitle = stringResource(MR.strings.in_library),
            )
            StatsOverviewItem(
                title = readDurationString,
                subtitle = stringResource(MR.strings.label_read_duration),
            )
            StatsOverviewItem(
                title = data.completedMangaCount.toString(),
                subtitle = stringResource(MR.strings.label_completed_titles),
            )
        }
    }
}

@Composable
private fun TitlesStats(data: StatsData.Titles) {
    InsetGroupedList(title = stringResource(MR.strings.label_titles_section)) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = 16.dp, horizontal = 16.dp),
        ) {
            StatsItem(
                data.globalUpdateItemCount.toString(),
                stringResource(MR.strings.label_titles_in_global_update),
            )
            StatsItem(
                data.startedMangaCount.toString(),
                stringResource(MR.strings.label_started),
            )
            StatsItem(
                data.localMangaCount.toString(),
                stringResource(MR.strings.label_local),
            )
        }
    }
}

@Composable
private fun ChapterStats(data: StatsData.Chapters) {
    InsetGroupedList(title = stringResource(MR.strings.chapters)) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = 16.dp, horizontal = 16.dp),
        ) {
            StatsItem(
                data.totalChapterCount.toString(),
                stringResource(MR.strings.label_total_chapters),
            )
            StatsItem(
                data.readChapterCount.toString(),
                stringResource(MR.strings.label_read_chapters),
            )
            StatsItem(
                data.downloadCount.toString(),
                stringResource(MR.strings.label_downloaded),
            )
        }
    }
}

@Composable
private fun TrackerStats(data: StatsData.Trackers) {
    val notApplicable = stringResource(MR.strings.not_applicable)
    val meanScoreStr = remember(data.trackedTitleCount, data.meanScore) {
        if (data.trackedTitleCount > 0 && !data.meanScore.isNaN()) {
            // All other numbers are localized in English
            "%.2f ★".format(Locale.ENGLISH, data.meanScore)
        } else {
            notApplicable
        }
    }
    InsetGroupedList(title = stringResource(MR.strings.label_tracker_section)) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = 16.dp, horizontal = 16.dp),
        ) {
            StatsItem(
                data.trackedTitleCount.toString(),
                stringResource(MR.strings.label_tracked_titles),
            )
            StatsItem(
                meanScoreStr,
                stringResource(MR.strings.label_mean_score),
            )
            StatsItem(
                data.trackerCount.toString(),
                stringResource(MR.strings.label_used),
            )
        }
    }
}

@Composable
private fun RowScope.StatsOverviewItem(
    title: String,
    subtitle: String,
) {
    BaseStatsItem(
        title = title,
        titleStyle = TsuzukiTheme.typography.title1,
        subtitle = subtitle,
        subtitleStyle = TsuzukiTheme.typography.footnote,
    )
}

@Composable
private fun RowScope.StatsItem(
    title: String,
    subtitle: String,
) {
    BaseStatsItem(
        title = title,
        titleStyle = TsuzukiTheme.typography.headline,
        subtitle = subtitle,
        subtitleStyle = TsuzukiTheme.typography.caption1,
    )
}

@Composable
private fun RowScope.BaseStatsItem(
    title: String,
    titleStyle: TextStyle,
    subtitle: String,
    subtitleStyle: TextStyle,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .weight(1f)
            .height(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TsuzukiText(
            text = title,
            style = titleStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
        TsuzukiText(
            text = subtitle,
            style = subtitleStyle.copy(textAlign = TextAlign.Center),
            color = TsuzukiTheme.colors.secondary,
        )
    }
}
