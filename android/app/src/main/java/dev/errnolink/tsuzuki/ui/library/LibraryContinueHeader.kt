package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.data.library.chapterPageProgress
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.domain.history.model.HistoryWithRelations

@Composable
fun LibraryContinueHeader(
    recent: List<HistoryWithRelations>,
    onSeeAllContinue: () -> Unit,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val pageTotals = rememberPageTotals()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(
        modifier.fillMaxWidth().padding(horizontal = TsuzukiTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsuzukiText(
                "Continue reading",
                Modifier.weight(1f),
                TsuzukiTheme.typography.title2,
            )
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .clickable(onClick = onSeeAllContinue)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    "See all",
                    style = TsuzukiTheme.typography.subhead,
                    color = colors.accent,
                )
            }
        }
        if (recent.size == 1) {
            val first = recent.first()
            ContinueCard(
                first,
                pageTotals,
                Modifier.fillMaxWidth(),
                onClick = { onOpen(first.mangaId) },
                onResume = { onResume(first.mangaId, first.chapterId) },
            )
        } else {
            LazyRow(
                flingBehavior = tsuzukiFlingBehavior(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recent, key = { it.chapterId }, contentType = { "continue" }) { entry ->
                    ContinueCard(
                        entry,
                        pageTotals,
                        Modifier.width(screenWidth * 0.85f),
                        onClick = { onOpen(entry.mangaId) },
                        onResume = { onResume(entry.mangaId, entry.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    entry: HistoryWithRelations,
    pageTotals: Map<Long, Int>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val totalPages = pageTotals[entry.chapterId]
    val progress = chapterPageProgress(entry.lastPageRead, totalPages, entry.read)
    val pageLabel = totalPages?.let { "Page ${entry.lastPageRead + 1} of $it" } ?: "Page ${entry.lastPageRead + 1}"
    Row(
        modifier
            .background(colors.grouped, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellCover(entry.coverData, entry.title, Modifier.width(56.dp), fixedRatio = 2f / 3f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TsuzukiText(
                entry.title,
                style = TsuzukiTheme.typography.subhead,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TsuzukiText(
                "Ch. ${formatChapterNumber(entry.chapterNumber)} · $pageLabel",
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ContinueButton(
            progress = progress,
            onClick = onResume,
            contentDescription = "Resume ${entry.title}",
        )
    }
}
