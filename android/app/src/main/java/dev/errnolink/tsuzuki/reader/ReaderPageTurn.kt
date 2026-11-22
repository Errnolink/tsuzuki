package dev.errnolink.tsuzuki.reader

import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screen edge a page turn flashed: the tapped edge (or the reading-direction edge for keys). */
enum class TurnEdge { None, Left, Right, Top, Bottom }

data class TurnFlash(val edge: TurnEdge, val seq: Long)

/**
 * Page-turn signals to the Compose reader chrome. The reader is a single activity, so a
 * process-wide holder is safe.
 */
object ReaderTurnSignals {
    private val _flash = MutableStateFlow(TurnFlash(TurnEdge.None, 0L))

    /** Latest edge flash; [TurnFlash.seq] increments per flash so the overlay restarts the animation. */
    val flash: StateFlow<TurnFlash> = _flash.asStateFlow()

    internal fun flashAt(edge: TurnEdge) {
        if (edge == TurnEdge.None) return
        _flash.value = TurnFlash(edge, _flash.value.seq + 1)
    }
}

/** The pager regions a turn can come from, mapped free of Android classes for tests. */
internal enum class TurnKind { AbsoluteNext, AbsolutePrev, SideRight, SideLeft, Menu }

/**
 * Turn direction for a region: +1 forward (reading direction), -1 backward, null when the region
 * is the menu. Left/right sides follow the reader's direction: on a right-to-left pager the left
 * side is forward.
 */
internal fun regionDirection(kind: TurnKind, rightToLeft: Boolean): Int? = when (kind) {
    TurnKind.AbsoluteNext -> 1
    TurnKind.AbsolutePrev -> -1
    TurnKind.SideRight -> if (rightToLeft) -1 else 1
    TurnKind.SideLeft -> if (rightToLeft) 1 else -1
    TurnKind.Menu -> null
}

internal fun regionKind(region: NavigationRegion): TurnKind = when (region) {
    NavigationRegion.MENU -> TurnKind.Menu
    NavigationRegion.NEXT -> TurnKind.AbsoluteNext
    NavigationRegion.PREV -> TurnKind.AbsolutePrev
    NavigationRegion.RIGHT -> TurnKind.SideRight
    NavigationRegion.LEFT -> TurnKind.SideLeft
}
