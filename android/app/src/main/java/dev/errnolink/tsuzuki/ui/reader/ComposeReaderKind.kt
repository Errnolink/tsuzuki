package dev.errnolink.tsuzuki.ui.reader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import dev.errnolink.tsuzuki.reader.engine.model.isEquivalentTo
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode

/**
 * Which Compose viewer the new reader mounts for a resolved reading mode.
 */
sealed interface ComposeReaderKind {
    data class Pager(val isRtl: Boolean, val isVertical: Boolean) : ComposeReaderKind

    data object Webtoon : ComposeReaderKind
}

/** L2R/R2L/vertical read through the Compose pager, webtoon and continuous vertical through the webtoon viewer. */
fun resolveComposeReaderKind(mode: ReadingMode): ComposeReaderKind = when (mode) {
    ReadingMode.LEFT_TO_RIGHT -> ComposeReaderKind.Pager(isRtl = false, isVertical = false)
    ReadingMode.RIGHT_TO_LEFT -> ComposeReaderKind.Pager(isRtl = true, isVertical = false)
    ReadingMode.VERTICAL -> ComposeReaderKind.Pager(isRtl = false, isVertical = true)
    ReadingMode.WEBTOON, ReadingMode.CONTINUOUS_VERTICAL -> ComposeReaderKind.Webtoon
    ReadingMode.DEFAULT -> error("Preference value must be resolved: ${mode.flagValue}")
}

/**
 * Process-wide kind of the currently mounted beta Compose reader (the reader is a single
 * activity, so one holder is safe — same pattern as `ReaderTurnSignals`). The reader settings
 * sheets read it to pick pager/webtoon sections while the old View viewers stay null in
 * `ReaderViewModel.State.viewer`.
 */
object ComposeReaderState {
    @Volatile
    var activeKind: ComposeReaderKind? = null
}

/**
 * True when the rebuilt item list is semantically identical to the current one (same item kinds,
 * chapters and pages in order, via the engine's [dev.errnolink.tsuzuki.reader.engine.model.isEquivalentTo]
 * pairwise comparison). The viewers key their anchor and effect bookkeeping on the list's
 * identity, so an event-driven rebuild that produced the same content must keep the old instance.
 */
fun composeReaderItemsUnchanged(current: List<ReaderUiItem>?, rebuilt: List<ReaderUiItem>): Boolean {
    if (current === rebuilt) return true
    if (current == null || current.size != rebuilt.size || current.isEmpty()) return false
    return current.indices.all { index ->
        val old = current[index]
        val new = rebuilt[index]
        old === new || old.isEquivalentTo(new)
    }
}

/**
 * Whether transitions render in the item list: transitions follow the
 * always-show setting, except continuous vertical which streams adjacent chapters seamlessly.
 */
fun composeReaderForceTransition(
    kind: ComposeReaderKind,
    mode: ReadingMode,
    alwaysShowChapterTransition: Boolean,
): Boolean = when {
    kind == ComposeReaderKind.Webtoon && mode == ReadingMode.CONTINUOUS_VERTICAL -> false
    else -> alwaysShowChapterTransition
}
