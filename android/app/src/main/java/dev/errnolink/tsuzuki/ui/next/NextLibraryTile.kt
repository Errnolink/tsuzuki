package dev.errnolink.tsuzuki.ui.next

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeKind
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadgeValue
import dev.errnolink.tsuzuki.ui.library.LibraryTileBadges
import dev.errnolink.tsuzuki.ui.library.LibraryTileOverlays
import dev.errnolink.tsuzuki.ui.library.resolvedBadges
import dev.errnolink.tsuzuki.ui.library.libraryBadgeWords
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.tachiyomi.R
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

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
    TileCurrent(manga, progress, overlays, badges, selected, onResume, modifier, showText, progressRing)
}

@Composable
internal fun rememberTilePressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

internal fun Modifier.tilePress(source: MutableInteractionSource): Modifier = pointerInput(source) {
    coroutineScope {
        val scope = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = PressInteraction.Press(down.position)
            scope.launch { source.emit(press) }
            val up = waitForUpOrCancellation()
            scope.launch {
                if (up != null) {
                    source.emit(PressInteraction.Release(press))
                } else {
                    source.emit(PressInteraction.Cancel(press))
                }
            }
        }
    }
}

@Composable
internal fun TileUnreadPill(overlays: LibraryTileOverlays, modifier: Modifier = Modifier) {
    val unreadDescription = pluralStringResource(MR.plurals.tsuzuki_num_unread, overlays.unread.toInt(), overlays.unread)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TsuzukiTheme.colors.accent)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = unreadDescription },
    ) {
        dev.errnolink.tsuzuki.ui.library.BadgeNumberText(
            overlays.unread.toString(),
            TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            TsuzukiTheme.colors.onAccent,
        )
    }
}

@Composable
internal fun TileSelectionMark(modifier: Modifier = Modifier) {
    SelectionMark(selected = true, modifier = modifier)
}

@Composable
internal fun TileBadgePills(overlays: LibraryTileOverlays, badges: LibraryTileBadges, modifier: Modifier = Modifier) {
    val words = libraryBadgeWords()
    val values = overlays.resolvedBadges(badges, words.localLabel).filterNot { it.kind == LibraryTileBadgeKind.Unread }
    if (values.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.chunked(2).forEach { rowBadges ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowBadges.forEach { badge ->
                    NextTileBadgePill(badge, overlays)
                }
            }
        }
    }
}

@Composable
internal fun NextTileBadgePill(
    badge: LibraryTileBadgeValue,
    overlays: LibraryTileOverlays,
    modifier: Modifier = Modifier,
) {
    val description = when (badge.kind) {
        LibraryTileBadgeKind.Download -> pluralStringResource(MR.plurals.tsuzuki_num_downloads, overlays.downloads.toInt(), overlays.downloads)
        LibraryTileBadgeKind.Unread -> pluralStringResource(MR.plurals.tsuzuki_num_unread, overlays.unread.toInt(), overlays.unread)
        LibraryTileBadgeKind.Local -> stringResource(MR.strings.local_source)
        LibraryTileBadgeKind.Language -> stringResource(MR.strings.tsuzuki_language_label, badge.label)
        LibraryTileBadgeKind.Source -> stringResource(MR.strings.tsuzuki_source_label, badge.label)
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
