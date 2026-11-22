package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.ContinueButtonSize
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.selectionFill
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun LibraryListRowCurrent(
    manga: LibraryManga,
    overlays: LibraryTileOverlays,
    badges: LibraryTileBadges,
    selected: Boolean,
    selectionMode: Boolean,
    showSeparator: Boolean,
    resume: (() -> Unit)?,
) {
    val colors = TsuzukiTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TsuzukiCorners.field))
                .selectionFill(selected)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionMark(selected, size = 24.dp)
            }
            ShellCover(manga.manga.asMangaCover(), manga.manga.title, Modifier.width(48.dp), fixedRatio = 2f / 3f, hero = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TsuzukiText(
                    manga.manga.title,
                    style = TsuzukiTheme.typography.subhead,
                    color = if (overlays.read) colors.secondary else colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = overlays.summaryText(badges, libraryBadgeWords())
                if (summary.isNotEmpty()) {
                    TsuzukiText(
                        summary,
                        style = TsuzukiTheme.typography.caption1,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badges.continueButton && overlays.showContinue) {
                ContinueButton(
                    progress = overlays.continueProgress,
                    onClick = { resume?.invoke() },
                    modifier = Modifier.readerOrigin(),
                    size = ContinueButtonSize.Compact,
                    contentDescription = stringResource(MR.strings.tsuzuki_resume_next_chapter),
                )
            }
        }
        if (showSeparator && !selected) {
            Box(
                Modifier
                    .padding(start = 64.dp)
                    .fillMaxWidth()
                    .height(TsuzukiTheme.spacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}
