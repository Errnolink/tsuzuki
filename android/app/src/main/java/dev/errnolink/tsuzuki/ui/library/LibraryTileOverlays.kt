package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.reader.ReaderOrigins
import dev.errnolink.tsuzuki.reader.readerOrigin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryScreenModel
import exh.source.MERGED_SOURCE_ID
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.ui.platform.LocalContext
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
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
    val continueChapterNumber: Double? = null,
    val continueChapterName: String? = null,
    val continueLastPageRead: Long = 0,
    val continuePageTotal: Int? = null,
    val readCount: Long = 0,
    val totalChapters: Long = 0,
) {
    companion object {
        fun from(
            item: LibraryItem,
            target: LibraryScreenModel.ContinueTarget?,
            mergedLabel: String,
        ): LibraryTileOverlays = LibraryTileOverlays(
            unread = item.unreadCount.coerceAtLeast(0),
            downloads = item.downloadCount.coerceAtLeast(0),
            read = item.libraryManga.unreadCount <= 0L,
            isLocal = item.isLocal,
            sourceLanguage = item.sourceLanguage.takeIf { it.isNotEmpty() && item.source?.id != MERGED_SOURCE_ID },
            useLangIcon = item.useLangIcon,
            sourceName = if (item.source?.id == MERGED_SOURCE_ID) mergedLabel else item.source?.name,
            continueChapterId = target?.chapterId,
            continueProgress = target?.progress,
            continueChapterNumber = target?.chapterNumber,
            continueChapterName = target?.chapterName,
            continueLastPageRead = target?.lastPageRead ?: 0L,
            continuePageTotal = target?.pageTotal,
            readCount = item.libraryManga.readCount.coerceAtLeast(0),
            totalChapters = item.libraryManga.totalChapters.coerceAtLeast(0),
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

fun LibraryTileOverlays.resolvedBadges(badges: LibraryTileBadges, localLabel: String): List<LibraryTileBadgeValue> = buildList {
    if (badges.download && downloads > 0) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Download, "\u2193 $downloads"))
    }
    if (badges.unread && unread > 0) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Unread, unread.toString()))
    }
    if (badges.local && isLocal) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Local, localLabel))
    }
    if (badges.language && !sourceLanguage.isNullOrEmpty()) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Language, sourceLanguage.uppercase(), badges.languageIcon))
    }
    if (badges.source && !sourceName.isNullOrEmpty()) {
        add(LibraryTileBadgeValue(LibraryTileBadgeKind.Source, sourceName))
    }
}

/** The localized words a badge summary or description is built from; resolved once per screen. */
@Immutable
class LibraryBadgeWords(
    val downloaded: (count: Long) -> String,
    val unread: (count: Long) -> String,
    val localSource: String,
    val localLabel: String,
    val language: (label: String) -> String,
    val source: (label: String) -> String,
)

@Composable
fun libraryBadgeWords(): LibraryBadgeWords {
    val context = LocalContext.current
    return LibraryBadgeWords(
        downloaded = { count -> context.pluralStringResource(MR.plurals.tsuzuki_num_downloads, count.toInt(), count) },
        unread = { count -> context.pluralStringResource(MR.plurals.tsuzuki_num_unread, count.toInt(), count) },
        localSource = context.stringResource(MR.strings.local_source),
        localLabel = context.stringResource(MR.strings.label_local),
        language = { label -> context.stringResource(MR.strings.tsuzuki_language_label, label) },
        source = { label -> context.stringResource(MR.strings.tsuzuki_source_label, label) },
    )
}

val LibraryTileOverlays.showContinue: Boolean
    get() = continueChapterId != null

fun LibraryTileOverlays.summaryText(badges: LibraryTileBadges, words: LibraryBadgeWords): String {
    return resolvedBadges(badges, words.localLabel).joinToString(" · ") { badge ->
        when (badge.kind) {
            LibraryTileBadgeKind.Download -> words.downloaded(downloads)
            LibraryTileBadgeKind.Unread -> words.unread(unread)
            LibraryTileBadgeKind.Local -> words.localLabel
            LibraryTileBadgeKind.Language -> badge.label
            LibraryTileBadgeKind.Source -> badge.label
        }
    }
}

/** The tile's continue target as a hero tap seed, so the title page's Continue button is right on its first frame. */
fun LibraryTileOverlays.heroTapSeed(): dev.errnolink.tsuzuki.ui.motion.HeroTapSeed? =
    if (continueChapterId == null) {
        null
    } else {
        dev.errnolink.tsuzuki.ui.motion.HeroTapSeed(
            continueChapterNumber = continueChapterNumber,
            continueChapterName = continueChapterName,
            continueLastPageRead = continueLastPageRead,
            continuePageTotal = continuePageTotal,
        )
    }


/**
 * Round 11 T7: a badge count that changes while the page is away (or while the flight home is
 * still settling) cross-fades instead of popping after the landing.
 */
@Composable
fun BadgeNumberText(label: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = TsuzukiTheme.motion.reduced
    AnimatedContent(
        targetState = label,
        transitionSpec = {
            if (reduced) {
                fadeIn(snap()).togetherWith(fadeOut(snap()))
            } else {
                fadeIn(tween(M3Motion.Short3)).togetherWith(fadeOut(tween(M3Motion.Short3)))
            }
        },
        label = "badgeNumber",
    ) { text ->
        TsuzukiText(text, modifier, style, color)
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
    val words = libraryBadgeWords()
    Box(modifier) {
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).padding(7.dp).size(26.dp)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Check, stringResource(MR.strings.tsuzuki_selected), Modifier.size(17.dp), tint = colors.onAccent)
            }
        }
        val badgeValues = overlays.resolvedBadges(badges, words.localLabel)
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
                                LibraryTileBadgeKind.Download -> words.downloaded(overlays.downloads)
                                LibraryTileBadgeKind.Unread -> words.unread(overlays.unread)
                                LibraryTileBadgeKind.Local -> words.localSource
                                LibraryTileBadgeKind.Language -> words.language(badge.label)
                                LibraryTileBadgeKind.Source -> words.source(badge.label)
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
            var continueBounds by remember { mutableStateOf<Rect?>(null) }
            ContinueButton(
                progress = overlays.continueProgress,
                onClick = {
                    ReaderOrigins.markActive(continueBounds)
                    onResume()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .readerOrigin()
                    .onGloballyPositioned { continueBounds = it.boundsInWindow() },
                onImage = true,
                contentDescription = stringResource(MR.strings.tsuzuki_resume_next_chapter),
            )
        }
    }
}
