@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiShellMaterial
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
import kotlin.math.roundToInt

object TabToolRequests {
    val markAllFeedRead = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val historySearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openDownloadQueue = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

@Immutable
data class TsuzukiTab(
    val key: Any,
    val title: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val badge: Boolean = false,
)

@Immutable
data class TsuzukiTabBarColors(
    val backgroundColor: Color,
    val indicatorColor: Color,
    val textColor: Color,
    val unselectedTextColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val badgeColor: Color,
)

@Immutable
data class TsuzukiTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

@Immutable
data class TsuzukiTabBarSizes(
    val barHeight: Dp = 54.dp,
    val componentSpacing: Dp = 8.dp,
    val tabBarContentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    val tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
    val tabInlineContentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    val tabSpacing: Dp = 0.dp,
    val iconSize: Dp = 23.dp,
    val badgeSize: Dp = 14.dp,
    val labelFontSize: TextUnit = 11.sp,
    val labelLineHeight: TextUnit = 14.sp,
)

/** Unselected tabs: the label colour, a step quieter, so they stay legible over bright covers under the glass. */
internal fun shellTabUnselected(text: Color): Color = text.copy(alpha = 0.78f)

object TsuzukiTabBarDefaults {
    val LabelFontSize: TextUnit = 11.sp
    val LabelLineHeight: TextUnit = 14.sp

    @Composable
    fun colors(
        backgroundColor: Color = TsuzukiShellMaterial.surfaceColor(TsuzukiTheme.colors),
        indicatorColor: Color = TsuzukiTheme.colors.text.copy(alpha = if (TsuzukiTheme.colors.isDark) 0.14f else 0.08f),
        textColor: Color = TsuzukiTheme.colors.text,
        unselectedTextColor: Color = shellTabUnselected(TsuzukiTheme.colors.text),
        accentColor: Color = TsuzukiTheme.colors.accent,
        onAccentColor: Color = TsuzukiTheme.colors.onAccent,
        badgeColor: Color = TsuzukiTheme.colors.destructive,
    ): TsuzukiTabBarColors = TsuzukiTabBarColors(
        backgroundColor = backgroundColor,
        indicatorColor = indicatorColor,
        textColor = textColor,
        unselectedTextColor = unselectedTextColor,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
        badgeColor = badgeColor,
    )

    @Composable
    fun shapes(
        tabBarShape: Shape = RoundedCornerShape(100),
        tabShape: Shape = RoundedCornerShape(100),
        standaloneTabShape: Shape = CircleShape,
        accessoryShape: Shape = RoundedCornerShape(100),
    ): TsuzukiTabBarShapes = TsuzukiTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    fun sizes(
        barHeight: Dp = 54.dp,
        componentSpacing: Dp = 8.dp,
        tabBarContentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        tabExpandedContentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        tabSpacing: Dp = 0.dp,
        iconSize: Dp = 23.dp,
        badgeSize: Dp = 14.dp,
        labelFontSize: TextUnit = LabelFontSize,
        labelLineHeight: TextUnit = LabelLineHeight,
    ): TsuzukiTabBarSizes = TsuzukiTabBarSizes(
        barHeight = barHeight,
        componentSpacing = componentSpacing,
        tabBarContentPadding = tabBarContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabSpacing = tabSpacing,
        iconSize = iconSize,
        badgeSize = badgeSize,
        labelFontSize = labelFontSize,
        labelLineHeight = labelLineHeight,
    )
}

object TsuzukiTabBarClearance {
    val BarHeight: Dp = 54.dp
    val OuterVerticalPadding: Dp = 8.dp
    val Clearance: Dp = BarHeight + OuterVerticalPadding * 2
}

/** How far the tab bar shrinks while the bottom search field is up (it fades over the same flight). */
internal const val SearchAwayShrink = 0.10f

internal fun searchTabBarAlpha(away: Float, opening: Boolean): Float = if (opening) {
    val t = (away / 0.35f).coerceIn(0f, 1f)
    1f - t * t * (3f - 2f * t)
} else {
    val t = ((away - 0.05f) / 0.60f).coerceIn(0f, 1f)
    1f - t * t * (3f - 2f * t)
}
 
/**
 * N15: the collapsed state is derived, never stored: only sustained down-scroll keeps it, and any
 * fresh context — keyboard search open, tab change, sheet close, screen return, reaching the top —
 * reads as expanded. The watcher inside GlassContext clears the fold on up-scroll and at the top
 * during scrolling; the shell force-expands on the navigation events.
 */
internal fun shellTabBarCollapsed(scrollFolded: Boolean, searchActive: Boolean): Boolean =
    scrollFolded && !searchActive

/**
 * N17: a label the travelling selection pill passes under dims for the crossing instead of the
 * light capsule slicing through the word. The selected tab's label rides on the pill and is not
 * dimmed; at rest every other tab is a whole step away, so nothing changes.
 */
internal fun tabLabelAlphaUnderPill(pillPosition: Float, tabIndex: Int, selectedIndex: Int): Float {
    if (tabIndex == selectedIndex) return 1f
    val overlap = (1f - abs(pillPosition - tabIndex)).coerceIn(0f, 1f)
    return 1f - 0.45f * overlap
}


fun calculatePillCenter(
    position: Float,
    tabStepPx: Float,
    tabWidthPx: Float,
    isRtl: Boolean,
    rowWidthPx: Float,
): Float {
    val x = position * tabStepPx + tabWidthPx / 2f
    return if (isRtl) rowWidthPx - x else x
}

internal fun tabDragTarget(
    selectedIndex: Int,
    dragTotalPx: Float,
    tabStepPx: Float,
    releaseVelocityPx: Float,
    tabCount: Int,
): Int {
    val rawPosition = if (tabStepPx > 0f) selectedIndex + dragTotalPx / tabStepPx else selectedIndex.toFloat()
    val bias = if (tabStepPx > 0f) releaseVelocityPx / (tabStepPx * 18f) else 0f
    val lastTab = (tabCount - 1).coerceAtLeast(0)
    return (rawPosition + bias).roundToInt().coerceIn(0, lastTab)
}

@Immutable
data class TabBarMorphGeometry(
    val capsuleWidthPx: Float,
    val contentBoxWidthPx: Float,
    val contentBoxOffset: Offset,
    val tabStepPx: Float,
    val tabAlphas: List<Float>,
    val labelAlpha: Float,
    val indicatorAlpha: Float,
    val badgeDotAlpha: Float,
)

private const val MorphTabFadeShare = 0.35f
private const val MorphLabelFadeEnd = 0.45f
private const val MorphIndicatorFadeEnd = 0.6f
private const val MorphBadgeDotStart = 0.55f
internal const val MorphOthersShrink = 0.15f

private fun slotLeft(index: Int, tabStepPx: Float, contentPaddingLeftPx: Float, innerWidthPx: Float, isRtl: Boolean): Float =
    contentPaddingLeftPx + if (isRtl) innerWidthPx - (index + 1) * tabStepPx else index * tabStepPx

internal fun glassLensShiftX(
    travel: Float,
    selectionPosition: Float,
    tabStepPx: Float,
    contentPaddingLeftPx: Float,
    innerWidthPx: Float,
    isRtl: Boolean,
    collapsedIconCenterXPx: Float,
): Float {
    val t = travel.coerceIn(0f, 1f)
    // A float position (the pill's own spring value) lets the shift glide between tabs, so the
    // collapsed circle's icon never teleports when the tab changes mid-collapse.
    val slot = contentPaddingLeftPx +
        (if (isRtl) innerWidthPx - (selectionPosition + 1f) * tabStepPx else selectionPosition * tabStepPx)
    val iconCenter = slot + tabStepPx / 2f
    return lerp(0f, collapsedIconCenterXPx - iconCenter, t)
}

internal fun tabMorphCapsuleWidthPx(progress: Float, expandedWidthPx: Float, collapsedWidthPx: Float): Float =
    lerp(expandedWidthPx, collapsedWidthPx, progress.coerceIn(0f, 1f))

internal fun morphLabelAlpha(progress: Float): Float =
    (1f - progress.coerceIn(0f, 1f) / MorphLabelFadeEnd).coerceIn(0f, 1f)

internal fun morphIndicatorAlpha(progress: Float): Float =
    (1f - progress.coerceIn(0f, 1f) / MorphIndicatorFadeEnd).coerceIn(0f, 1f)

internal fun morphBadgeDotAlpha(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - MorphBadgeDotStart) / (1f - MorphBadgeDotStart)).coerceIn(0f, 1f)

internal fun tabAlpha(
    index: Int,
    progress: Float,
    expandedWidthPx: Float,
    collapsedWidthPx: Float,
    contentPaddingLeftPx: Float,
    innerWidthPx: Float,
    tabStepPx: Float,
    isRtl: Boolean,
    collapsedIconCenterXPx: Float,
    selectedIndex: Int,
): Float {
    if (index == selectedIndex) return 1f
    val travel = progress.coerceIn(0f, 1f)
    val slotLeftPx = contentPaddingLeftPx + if (isRtl) innerWidthPx - (index + 1) * tabStepPx else index * tabStepPx
    val left = slotLeftPx + tabStepPx * 0.15f
    val right = slotLeftPx + tabStepPx * 0.85f
    val fadeDistance = (tabStepPx * MorphTabFadeShare).coerceAtLeast(1f)
    val restClearance = minOf(left, expandedWidthPx - right)
    val shift = glassLensShiftX(travel, selectedIndex.toFloat(), tabStepPx, contentPaddingLeftPx, innerWidthPx, isRtl, collapsedIconCenterXPx)
    val capsuleWidthPx = tabMorphCapsuleWidthPx(travel, expandedWidthPx, collapsedWidthPx)
    val clearance = minOf(shift + left, capsuleWidthPx - (shift + right))
    return (clearance / minOf(fadeDistance, restClearance).coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal fun tabBarMorphGeometry(
    progress: Float,
    expandedWidthPx: Float,
    collapsedWidthPx: Float,
    contentPaddingLeftPx: Float,
    contentPaddingRightPx: Float,
    tabCount: Int,
    selectedIndex: Int,
    isRtl: Boolean,
    collapsedIconCenterXPx: Float,
    iconLiftYPx: Float,
): TabBarMorphGeometry {
    val travel = progress.coerceIn(0f, 1f)
    val contentBoxWidthPx = expandedWidthPx.coerceAtLeast(0f)
    val innerWidth = (contentBoxWidthPx - contentPaddingLeftPx - contentPaddingRightPx).coerceAtLeast(0f)
    val tabStepPx = if (tabCount > 0) innerWidth / tabCount else 0f
    val selected = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val alphas = List(tabCount) { index ->
        tabAlpha(
            index = index,
            progress = travel,
            expandedWidthPx = expandedWidthPx,
            collapsedWidthPx = collapsedWidthPx,
            contentPaddingLeftPx = contentPaddingLeftPx,
            innerWidthPx = innerWidth,
            tabStepPx = tabStepPx,
            isRtl = isRtl,
            collapsedIconCenterXPx = collapsedIconCenterXPx,
            selectedIndex = selected,
        )
    }
    return TabBarMorphGeometry(
        capsuleWidthPx = tabMorphCapsuleWidthPx(travel, expandedWidthPx, collapsedWidthPx),
        contentBoxWidthPx = contentBoxWidthPx,
        contentBoxOffset = Offset(
            glassLensShiftX(travel, selected.toFloat(), tabStepPx, contentPaddingLeftPx, innerWidth, isRtl, collapsedIconCenterXPx),
            lerp(0f, iconLiftYPx, travel),
        ),
        tabStepPx = tabStepPx,
        tabAlphas = alphas,
        labelAlpha = morphLabelAlpha(travel),
        indicatorAlpha = morphIndicatorAlpha(travel),
        badgeDotAlpha = morphBadgeDotAlpha(travel),
    )
}

@Composable
fun TsuzukiTabBar(
    tabs: List<TsuzukiTab>,
    selectedIndex: Int,
    isInline: Boolean,
    onExpand: () -> Unit,
    showLabels: Boolean = true,
    search: TopBarSearch? = null,
    modifier: Modifier = Modifier,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: TsuzukiTabBarColors = TsuzukiTabBarDefaults.colors(),
    shapes: TsuzukiTabBarShapes = TsuzukiTabBarDefaults.shapes(),
    sizes: TsuzukiTabBarSizes = TsuzukiTabBarDefaults.sizes(),
    glass: GlassContext? = LocalShellGlass.current,
    /** NAV-05: the shell's hoisted pill; survives bar remounts. */
    pill: TsuzukiPillMotion,
) {
    TsuzukiTabBarCurrent(
        tabs = tabs,
        selectedIndex = selectedIndex,
        isInline = isInline,
        onExpand = onExpand,
        showLabels = showLabels,
        search = search,
        modifier = modifier,
        accessory = accessory,
        expandedAccessory = expandedAccessory,
        colors = colors,
        shapes = shapes,
        sizes = sizes,
        glass = glass,
        pill = pill,
    )
}

