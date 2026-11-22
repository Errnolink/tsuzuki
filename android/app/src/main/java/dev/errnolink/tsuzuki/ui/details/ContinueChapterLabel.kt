package dev.errnolink.tsuzuki.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.errnolink.tsuzuki.ui.motion.HeroTapSeed
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The localized words a continue label is built from; resolved once per screen. */
@Immutable
data class ContinueWords(
    val continueWord: String,
    val chapterPattern: String,
    val pagePattern: String,
)

@Composable
fun continueWords(): ContinueWords = ContinueWords(
    continueWord = stringResource(MR.strings.tsuzuki_continue),
    chapterPattern = stringResource(MR.strings.tsuzuki_ch_short),
    pagePattern = stringResource(MR.strings.tsuzuki_page_n),
)

internal fun continueChapterLabel(chapter: Chapter, words: ContinueWords): String =
    words.continueWord + chapterTitleSuffix(
        number = chapter.chapterNumber.takeIf { it.isFinite() && chapter.isRecognizedNumber },
        name = chapter.name,
        lastPageRead = chapter.lastPageRead,
        words = words,
    )

/**
 * The same label from the tile's continue target (round 11 T2): shown while the title page's own
 * chapters are still loading so the button's text is final on the first frame. Null when the tile
 * had no continue target — the caller falls back to the plain `Continue` hold.
 */
internal fun continueSeedLabel(seed: HeroTapSeed, words: ContinueWords): String? {
    if (!seed.hasChapter) return null
    return words.continueWord + chapterTitleSuffix(
        number = seed.continueChapterNumber?.takeIf { it.isFinite() && it >= 0.0 },
        name = seed.continueChapterName.orEmpty(),
        lastPageRead = seed.continueLastPageRead,
        words = words,
    )
}

/** `Continue · Ch. 66.6 · Page 12` — one suffix builder for the loaded chapter and the tile seed. */
internal fun chapterTitleSuffix(number: Double?, name: String, lastPageRead: Long, words: ContinueWords): String = buildString {
    val title = if (number != null) words.chapterPattern.format(formatChapterNumber(number)) else name.trim()
    if (title.isNotEmpty()) append(" · ").append(title)
    if (lastPageRead > 0) append(" · ").append(words.pagePattern.format(lastPageRead + 1))
}
