package dev.errnolink.tsuzuki.ui.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadges
import dev.errnolink.tsuzuki.ui.library.LibraryTileOverlays
import dev.errnolink.tsuzuki.ui.library.heroTapSeed
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun TileCurrent(
    manga: Manga,
    progress: NextTileProgress,
    overlays: LibraryTileOverlays,
    badges: LibraryTileBadges,
    selected: Boolean,
    onResume: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    progressRing: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val cover = coverShape()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .clip(cover),
        ) {
            ShellCover(manga.asMangaCover(), manga.title, Modifier.fillMaxWidth(), tapSeed = overlays.heroTapSeed())
            if (badges.unread && overlays.unread > 0) {
                TileUnreadPill(overlays, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (selected) {
                TileSelectionMark(Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            TileBadgePills(overlays, badges, Modifier.align(Alignment.BottomStart).padding(8.dp))
            if (badges.continueButton && onResume != null) {
                ContinueButton(
                    progress = if (progressRing) overlays.continueProgress else null,
                    onClick = onResume,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).readerOrigin(),
                    onImage = true,
                    contentDescription = stringResource(MR.strings.tsuzuki_resume_next_chapter),
                )
            }
        }
        if (showText) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TsuzukiText(
                    manga.title,
                    style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TsuzukiText(
                    progress.caption,
                    style = TsuzukiTheme.typography.caption1,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
