package dev.errnolink.tsuzuki.ui.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * The caption under a library tile. Tiles are about 115 dp wide at three columns, so it stays
 * short enough for one 12 sp line; the unread count is already on the cover pill (LIB-02).
 */
data class NextTileProgress(val caption: String) {
    companion object {
        fun of(read: Long, total: Long, unread: Long, words: NextTileWords): NextTileProgress {
            val safeTotal = total.coerceAtLeast(0)
            val safeRead = read.coerceIn(0, safeTotal)
            return when {
                safeTotal == 0L -> NextTileProgress(words.noChaptersYet)
                safeRead == 0L -> NextTileProgress(words.chapters(safeTotal))
                unread <= 0L -> NextTileProgress(words.caughtUp)
                else -> NextTileProgress(words.readOfTotal(safeRead, safeTotal))
            }
        }
    }
}

/** The localized words a tile caption is built from; resolved once per screen. */
@Immutable
class NextTileWords(
    val noChaptersYet: String,
    val caughtUp: String,
    val readOfTotal: (read: Long, total: Long) -> String,
    val chapters: (count: Long) -> String,
)

@Composable
fun nextTileWords(): NextTileWords {
    val context = LocalContext.current
    return NextTileWords(
        noChaptersYet = context.stringResource(MR.strings.tsuzuki_no_chapters_yet),
        caughtUp = context.stringResource(MR.strings.tsuzuki_caught_up),
        readOfTotal = { read, total -> context.stringResource(MR.strings.tsuzuki_read_of_total, read, total) },
        chapters = { count -> context.pluralStringResource(MR.plurals.manga_num_chapters, count.toInt(), count) },
    )
}
