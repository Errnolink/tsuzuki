package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import dev.errnolink.tsuzuki.designsystem.M3Motion

/**
 * M3 lateral pattern tap-jump: pages slide in unison with no fade or parallax. Jumps of more than
 * one page snap to the target's neighbour page first, then animate one page so only two pages move.
 */
suspend fun PagerState.lateralScrollTo(page: Int, reduced: Boolean) {
    if (pageCount == 0) return
    val target = page.coerceIn(0, pageCount - 1)
    if (reduced) {
        scrollToPage(target)
        return
    }
    val current = currentPage
    if (target > current + 1) scrollToPage(target - 1)
    if (target < current - 1) scrollToPage(target + 1)
    animateScrollToPage(
        target,
        animationSpec = tween(M3Motion.Medium4, easing = M3Motion.Emphasized),
    )
}
