package dev.errnolink.tsuzuki.ui.next

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import androidx.compose.material3.Icon
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeValue
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadges
import dev.errnolink.tsuzuki.ui.library.LibraryTileContinuePill
import dev.errnolink.tsuzuki.ui.library.LibraryTileOverlays
import dev.errnolink.tsuzuki.ui.library.resolvedBadges
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.tachiyomi.R
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover

fun NextLibraryAutoCellWidth(displayMode: LibraryDisplayMode): Dp = when (displayMode) {
    LibraryDisplayMode.CompactGrid -> 112.dp
    LibraryDisplayMode.ComfortableGrid -> 148.dp
    LibraryDisplayMode.ComfortableGridPanorama -> 232.dp
    LibraryDisplayMode.CoverOnlyGrid -> 108.dp
    LibraryDisplayMode.List -> 140.dp
}

@Composable
fun NextLibraryTile(
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.clip(RoundedCornerShape(12.dp))) {
            ShellCover(manga.asMangaCover(), manga.title, Modifier.fillMaxWidth())
            if (badges.unread && overlays.unread > 0) {
                TsuzukiText(
                    overlays.unread.toString(),
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .semantics { contentDescription = "${overlays.unread} unread" },
                    TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onAccent,
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TsuzukiIcons.Check, "Selected", Modifier.size(17.dp), tint = colors.onAccent)
                }
            }
            val badgeValues = overlays.resolvedBadges(badges).filterNot { it.kind == LibraryTileBadgeKind.Unread }
            if (badgeValues.isNotEmpty()) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    badgeValues.chunked(2).forEach { rowBadges ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowBadges.forEach { badge ->
                                NextTileBadgePill(badge, overlays)
                            }
                        }
                    }
                }
            }
            if (badges.continueButton && onResume != null) {
                LibraryTileContinuePill(
                    Modifier.align(Alignment.BottomEnd).padding(2.dp),
                    progress = if (progressRing) progress.fraction else null,
                ) { onResume() }
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

@Composable
private fun NextTileBadgePill(
    badge: LibraryTileBadgeValue,
    overlays: LibraryTileOverlays,
    modifier: Modifier = Modifier,
) {
    val description = when (badge.kind) {
        LibraryTileBadgeKind.Download -> "${overlays.downloads} downloads"
        LibraryTileBadgeKind.Unread -> "${overlays.unread} unread"
        LibraryTileBadgeKind.Local -> "Local source"
        LibraryTileBadgeKind.Language -> "Language ${badge.label}"
        LibraryTileBadgeKind.Source -> "Source ${badge.label}"
    }
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .widthIn(max = 132.dp),
    ) {
        if (badge.kind == LibraryTileBadgeKind.Language && badge.languageIcon) {
            val iconResId = overlays.sourceLanguage
                ?.let { GetExtensionLanguages.getLanguageIconID(it) }
                ?: R.drawable.globe
            Image(
                painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(width = 17.dp, height = 12.dp),
            )
        } else {
            TsuzukiText(
                badge.label,
                Modifier,
                TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
