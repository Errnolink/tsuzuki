package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.MorphingSearchButton
import dev.errnolink.tsuzuki.designsystem.MorphingSearchOverlay
import dev.errnolink.tsuzuki.designsystem.SearchCloseAction
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.searchCloseAction

/* Voice Memos bottom search (design 2B; round 12 extends it to bottom-hosted pushed pages such
   as Global search): on the tab roots the tab bar's trailing search circle grows into a
   full-width glass field at the bottom of the screen while the tab bar fades and shrinks away
   (it grows back from its own position on the same flight); the row rides the keyboard. A
   separate glass × circle sits at the trailing end of the field — with text it clears, empty it
   closes. Other pushed pages keep the magnifier in the top bar (3B). */

/** Shared chrome metrics for the shell's bottom bar row and the bottom search dock. */
internal object ShellBottomBarChrome {
    val HorizontalPadding = 16.dp
    val VerticalPadding = 8.dp
    val MaxWidth = 540.dp

    /** The row height: the floating tab bar's height, so the dock row and the bar line up. */
    val RowHeight = TsuzukiTabBarClearance.BarHeight

    /** The field capsule, the × circle and the search circle share the bar's height (no third height). */
    val CircleSize = RowHeight

    /** Height the dock row adds above the navigation bar (row + its vertical padding). */
    val Clearance = RowHeight + VerticalPadding * 2
}

/**
 * Pure: how visible the trailing × circle is at a flight progress. Opening: it waits for the shape
 * (the field-content window) and scales in from .6. Closing: it drains with the field content so
 * no blank circle outlives the capsule. One implementation with the field (C-OVL-22).
 */
internal fun searchCloseCircleAppear(progress: Float, opening: Boolean): Float =
    dev.errnolink.tsuzuki.designsystem.searchContentAlpha(progress, opening)

internal fun searchCloseCircleScale(progress: Float, opening: Boolean): Float =
    0.6f + 0.4f * searchCloseCircleAppear(progress, opening)

/** Pure: the full-width field's landing rect between the row's leading edge and its trailing × slot. */
internal fun bottomSearchFieldRect(
    row: Rect,
    hostOrigin: Offset,
    rowHeightPx: Float,
    gapPx: Float,
    fieldHeightPx: Float,
): Rect {
    val left = row.left
    val right = row.right - rowHeightPx - gapPx
    val top = row.top + (row.height - fieldHeightPx) / 2f
    return Rect(left - hostOrigin.x, top - hostOrigin.y, right - hostOrigin.x, top + fieldHeightPx - hostOrigin.y)
}

/** Pure: whether the bottom dock (not the top row) hosts a declared search. */
internal fun searchHostsAtBottom(bottomHosted: Boolean, isTabRoot: Boolean): Boolean = bottomHosted || isTabRoot

/**
 * The shell's bottom search dock. Reads the front screen's search declaration, publishes it for
 * the tab bar and the nav rail, and hosts the flight: tab roots always, and pushed pages that
 * declare `bottomHosted` (round 12: Global search) — one field, at the bottom, riding the
 * keyboard. The tab bar's trailing circle is the trigger, the field lands across the whole row
 * minus its trailing × circle, and the whole row rides the IME's animated inset every frame.
 */
@Composable
internal fun ShellBottomSearchDock(
    modifier: Modifier = Modifier,
    frontKey: String?,
    isTabRoot: Boolean,
    moving: Boolean,
) {
    val declaration = frontKey?.let { ShellTopControls.declarations[it] }
    val search = declaration?.search
    // 3B + round 12: tab roots and bottom-hosted pushed pages; other pushed pages keep the
    // magnifier up top.
    if (search == null || moving || !searchHostsAtBottom(search.bottomHosted, isTabRoot)) return
    val glass = declaration?.glass
    val density = LocalDensity.current
    val expanded = search.state.expanded

    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var rowRect by remember { mutableStateOf(Rect.Zero) }
    val rowHeightPx = with(density) { ShellBottomBarChrome.RowHeight.toPx() }
    val circlePx = with(density) { ShellBottomBarChrome.CircleSize.toPx() }
    val gapPx = with(density) { ShellBottomBarChrome.VerticalPadding.toPx() }
    // WP6.3: composition gates on a threshold, not the raw flight read — the close circle stays
    // composed through the flight without recomposing the dock every frame.
    val circleVisible by remember(search) { derivedStateOf { search.state.flightProgress > 0.001f } }

    Box(modifier.onGloballyPositioned { hostOrigin = it.positionInWindow() }) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                )
                .padding(
                    horizontal = ShellBottomBarChrome.HorizontalPadding,
                    vertical = ShellBottomBarChrome.VerticalPadding,
                )
                .widthIn(max = ShellBottomBarChrome.MaxWidth)
                .onGloballyPositioned { rowRect = it.boundsInWindow() },
        ) {
            Row(
                Modifier.fillMaxWidth().height(ShellBottomBarChrome.RowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(ShellBottomBarChrome.RowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isTabRoot) {
                        MorphingSearchButton(
                            state = search.state,
                            glass = glass,
                            modifier = Modifier.size(ShellBottomBarChrome.CircleSize),
                            contentDescription = search.placeholder,
                        )
                    }
                    // Stays composed through the close flight so the × scales out with the field
                    // content instead of popping out the moment search collapses.
                    if (circleVisible) {
                        SearchCloseCircle(search, glass, progress = { search.state.flightProgress }, opening = { expanded })
                    }
                }
            }
        }
        val target = bottomSearchFieldRect(
            row = rowRect,
            hostOrigin = hostOrigin,
            rowHeightPx = rowHeightPx,
            gapPx = gapPx,
            fieldHeightPx = circlePx,
        )
        MorphingSearchOverlay(
            search = search,
            glass = glass,
            targetRect = target,
            hostOrigin = hostOrigin,
            modifier = Modifier.matchParentSize(),
            inlineClose = false,
        )
    }
}

/** Voice Memos' close: a separate glass × circle beside the field — clears with text, closes when empty. */
@Composable
private fun SearchCloseCircle(
    search: TopBarSearch,
    glass: GlassContext?,
    progress: () -> Float,
    opening: () -> Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(ShellBottomBarChrome.CircleSize)
            .graphicsLayer {
                val p = progress()
                alpha = searchCloseCircleAppear(p, opening())
                val scale = searchCloseCircleScale(p, opening())
                scaleX = scale
                scaleY = scale
            },
    ) {
        GlassSurface(
            context = glass,
            shape = CircleShape,
            material = GlassMaterial.Thick,
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pressScale(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Close search",
                    ) {
                        when (searchCloseAction(search.query.isNotEmpty())) {
                            SearchCloseAction.Clear -> search.onQueryChange("")
                            SearchCloseAction.Close -> ShellTopControls.closeSearch(search)
                        }
                    }
                    .semantics { contentDescription = "Close search" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.X, null, Modifier.size(20.dp), tint = TsuzukiTheme.colors.text)
            }
        }
    }
}
