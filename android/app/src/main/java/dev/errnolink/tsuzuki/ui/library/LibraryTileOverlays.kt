package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.reader.readerOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import eu.kanade.tachiyomi.ui.library.LibraryItem
import exh.source.MERGED_SOURCE_ID

@Immutable
data class LibraryTileOverlays(
    val unread: Long = 0,
    val downloads: Long = 0,
    val read: Boolean = false,
    val isLocal: Boolean = false,
    val sourceLanguage: String? = null,
    val useLangIcon: Boolean = true,
    val sourceName: String? = null,
    val continueChapterId: Long? = null,
    val continueProgress: Float? = null,
) {
    companion object {
        fun from(
            item: LibraryItem,
            continueChapterId: Long? = null,
            continueProgress: Float? = null,
        ): LibraryTileOverlays = LibraryTileOverlays(
            unread = item.unreadCount.coerceAtLeast(0),
            downloads = item.downloadCount.coerceAtLeast(0),
            read = item.libraryManga.unreadCount <= 0L,
            isLocal = item.isLocal,
            sourceLanguage = item.sourceLanguage.takeIf { it.isNotEmpty() && item.source?.id != MERGED_SOURCE_ID },
            useLangIcon = item.useLangIcon,
            sourceName = if (item.source?.id == MERGED_SOURCE_ID) "Merged" else item.source?.name,
            continueChapterId = continueChapterId,
            continueProgress = continueProgress,
        )
    }
}

@Immutable
data class LibraryTileBadges(
    val download: Boolean = true,
    val unread: Boolean = true,
    val local: Boolean = false,
    val language: Boolean = false,
    val languageIcon: Boolean = false,
    val source: Boolean = false,
    val continueButton: Boolean = false,
)

enum class LibraryTileBadgeKind {
    Download,
    Unread,
    Local,
    Language,
    Source,
}

data class LibraryTileBadgeValue(
    val kind: LibraryTileBadgeKind,
    val label: String,
    val languageIcon: Boolean = false,
)

fun LibraryTileOverlays.resolvedBadges(badges: LibraryTileBadges): List<LibraryTileBadgeValue> = buildList {
    if (badges.download && downloads > 0) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Download, "\u2193 $downloads"))
    }
    if (badges.unread && unread > 0) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Unread, unread.toString()))
    }
    if (badges.local && isLocal) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Local, "Local"))
    }
    if (badges.language && !sourceLanguage.isNullOrEmpty()) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Language, sourceLanguage.uppercase(), badges.languageIcon))
    }
    if (badges.source && !sourceName.isNullOrEmpty()) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Source, sourceName))
    }
}

val LibraryTileOverlays.showContinue: Boolean
    get() = continueChapterId != null

fun LibraryTileOverlays.summaryText(badges: LibraryTileBadges): String {
    return resolvedBadges(badges).joinToString(" · ") { badge ->
        when (badge.kind) {
            LibraryTileBadgeKind.Download -> "${downloads} downloaded"
            LibraryTileBadgeKind.Unread -> "${unread} unread"
            LibraryTileBadgeKind.Local -> "Local"
            LibraryTileBadgeKind.Language -> badge.label
            LibraryTileBadgeKind.Source -> badge.label
        }
    }
}

@Composable
fun LibraryTileOverlayStack(
    overlays: LibraryTileOverlays,
    badges: LibraryTileBadges,
    selected: Boolean,
    onResume: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Box(modifier) {
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).padding(7.dp).size(26.dp)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Check, "Selected", Modifier.size(17.dp), tint = colors.onAccent)
            }
        }
        val badgeValues = overlays.resolvedBadges(badges)
        if (badgeValues.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(7.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                badgeValues.chunked(2).forEach { rowBadges ->
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        rowBadges.forEach { badge ->
                            val label = when (badge.kind) {
                                LibraryTileBadgeKind.Download -> "${badge.label} downloads"
                                LibraryTileBadgeKind.Unread -> "${badge.label} unread"
                                LibraryTileBadgeKind.Local -> "Local source"
                                LibraryTileBadgeKind.Language -> "Language ${badge.label}"
                                LibraryTileBadgeKind.Source -> "Source ${badge.label}"
                            }
                            Box(Modifier.semantics { contentDescription = label }) {
                                ShellBadge(badge.label)
                            }
                        }
                    }
                }
            }
        }
        if (badges.continueButton && overlays.showContinue && onResume != null) {
            ContinueButton(
                progress = overlays.continueProgress,
                onClick = onResume,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).readerOrigin(),
                onImage = true,
                contentDescription = "Resume next chapter",
            )
        }
    }
}
