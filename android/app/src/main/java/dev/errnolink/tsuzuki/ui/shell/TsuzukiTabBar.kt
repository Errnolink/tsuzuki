@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LocalMotionTokens
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.VelocityCarryTracker
import dev.errnolink.tsuzuki.designsystem.pressScale
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.errnolink.tsuzuki.designsystem.TsuzukiShellMaterial
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.library.formatFilterCount
import kotlin.math.roundToInt

@Immutable
data class TsuzukiTab(
    val key: Any,
    val title: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
)

@Immutable
data class TsuzukiTabBarColors(
    val backgroundColor: Color,
    val indicatorColor: Color,
    val textColor: Color,
    val unselectedTextColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
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
    val filterIconSize: Dp = 22.dp,
    val badgeSize: Dp = 14.dp,
    val labelFontSize: TextUnit = 11.sp,
    val labelLineHeight: TextUnit = 14.sp,
)

object TsuzukiTabBarDefaults {
    val LabelFontSize: TextUnit = 11.sp
    val LabelLineHeight: TextUnit = 14.sp

    @Composable
    fun colors(
        backgroundColor: Color = TsuzukiShellMaterial.surfaceColor(TsuzukiTheme.colors),
        indicatorColor: Color = TsuzukiTheme.colors.accent.copy(alpha = 0.14f),
        textColor: Color = TsuzukiTheme.colors.text,
        unselectedTextColor: Color = TsuzukiTheme.colors.secondary,
        accentColor: Color = TsuzukiTheme.colors.accent,
        onAccentColor: Color = TsuzukiTheme.colors.onAccent,
    ): TsuzukiTabBarColors = TsuzukiTabBarColors(
        backgroundColor = backgroundColor,
        indicatorColor = indicatorColor,
        textColor = textColor,
        unselectedTextColor = unselectedTextColor,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
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
        filterIconSize: Dp = 22.dp,
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
        filterIconSize = filterIconSize,
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

fun isTabBarAccessoryVisible(
    isInline: Boolean,
    hasAccessory: Boolean,
    imeVisible: Boolean = false,
    searchActive: Boolean = false,
): Boolean = isInline && hasAccessory && !imeVisible && !searchActive

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

@Immutable
data class TabBarMorphGeometry(
    val capsuleWidthPx: Float,
    val contentBoxWidthPx: Float,
    val contentBoxOffset: Offset,
    val tabStepPx: Float,
    val tabsAlpha: Float,
    val collapsedAlpha: Float,
    val expandedAlpha: Float,
)

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
    val p = progress.coerceIn(0f, 1f)
    val capsuleWidthPx = lerp(expandedWidthPx, collapsedWidthPx, p)
    val contentBoxWidthPx = expandedWidthPx.coerceAtLeast(0f)
    val innerWidth = (contentBoxWidthPx - contentPaddingLeftPx - contentPaddingRightPx).coerceAtLeast(0f)
    val tabStepPx = if (tabCount > 0) innerWidth / tabCount else 0f
    val selected = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val iconCenterInBoxX = contentPaddingLeftPx + if (isRtl) {
        innerWidth - (selected + 0.5f) * tabStepPx
    } else {
        (selected + 0.5f) * tabStepPx
    }
    val restX = 0f
    val collapsedX = collapsedIconCenterXPx - iconCenterInBoxX
    return TabBarMorphGeometry(
        capsuleWidthPx = capsuleWidthPx,
        contentBoxWidthPx = contentBoxWidthPx,
        contentBoxOffset = Offset(lerp(restX, collapsedX, p), lerp(0f, iconLiftYPx, p)),
        tabStepPx = tabStepPx,
        tabsAlpha = ((0.6f - p) / 0.2f).coerceIn(0f, 1f),
        collapsedAlpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f),
        expandedAlpha = (1f - p / 0.4f).coerceIn(0f, 1f),
    )
}

@Composable
fun TsuzukiTabBar(
    tabs: List<TsuzukiTab>,
    selectedIndex: Int,
    isInline: Boolean,
    onExpand: () -> Unit,
    showLabels: Boolean = true,
    filterActiveCount: Int = 0,
    onFilterClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showFilterCircle: Boolean = true,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: TsuzukiTabBarColors = TsuzukiTabBarDefaults.colors(),
    shapes: TsuzukiTabBarShapes = TsuzukiTabBarDefaults.shapes(),
    sizes: TsuzukiTabBarSizes = TsuzukiTabBarDefaults.sizes(),
    glass: GlassContext? = LocalShellGlass.current,
) {
    val motion = TsuzukiTheme.motion
    val tokens = LocalMotionTokens.current
    val density = LocalDensity.current
    val isAccessoryShared = accessory != null && expandedAccessory != null

    val progress = remember { Animatable(if (isInline) 1f else 0f) }
    LaunchedEffect(isInline) {
        progress.animateTo(
            targetValue = if (isInline) 1f else 0f,
            animationSpec = when {
                motion.reduced -> snap()
                else -> tween(M3Motion.Medium1, easing = M3Motion.Emphasized)
            },
        )
    }

    val inlineAccessoryEnter: EnterTransition = when {
        motion.reduced -> fadeIn(snap())
        tokens -> expandHorizontally(tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate), expandFrom = Alignment.End) +
            fadeIn(tween(M3Motion.Short3, easing = M3Motion.Standard))
        else -> expandHorizontally(motion.tabSpring(), expandFrom = Alignment.End) + fadeIn(tween(160))
    }
    val inlineAccessoryExit: ExitTransition = when {
        motion.reduced -> fadeOut(snap())
        tokens -> shrinkHorizontally(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate), shrinkTowards = Alignment.End) +
            fadeOut(tween(M3Motion.Short3, easing = M3Motion.StandardAccelerate))
        else -> shrinkHorizontally(motion.tabSpring(), shrinkTowards = Alignment.End) + fadeOut(tween(120))
    }
    val topAccessoryEnter: EnterTransition = when {
        motion.reduced -> fadeIn(snap())
        tokens -> expandVertically(tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate), expandFrom = Alignment.Bottom) +
            fadeIn(tween(M3Motion.Short3, easing = M3Motion.Standard))
        else -> expandVertically(motion.tabSpring(), expandFrom = Alignment.Bottom) + fadeIn(tween(160))
    }
    val topAccessoryExit: ExitTransition = when {
        motion.reduced -> fadeOut(snap())
        tokens -> shrinkVertically(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate), shrinkTowards = Alignment.Bottom) +
            fadeOut(tween(M3Motion.Short3, easing = M3Motion.StandardAccelerate))
        else -> shrinkVertically(motion.tabSpring(), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
    }

    SharedTransitionLayout(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (expandedAccessory != null) {
                AnimatedVisibility(visible = !isInline, enter = topAccessoryEnter, exit = topAccessoryExit) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isAccessoryShared) {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState("accessory"),
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        expandedAccessory(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.backgroundColor, shapes.accessoryShape)
                                .clip(shapes.accessoryShape),
                            this@AnimatedVisibility,
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizes.barHeight),
            ) {
                val containerWidthPx = constraints.maxWidth.toFloat()
                val filterBlockPx = with(density) {
                    if (showFilterCircle) sizes.barHeight.toPx() + sizes.componentSpacing.toPx() else 0f
                }
                val expandedWidthPx = (containerWidthPx - filterBlockPx).coerceAtLeast(0f)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TabCapsule(
                        tabs = tabs,
                        selectedIndex = selectedIndex,
                        isInline = isInline,
                        showLabels = showLabels,
                        progress = progress.value,
                        onExpand = onExpand,
                        onSelectTab = { index ->
                            if (index in tabs.indices) {
                                tabs[index].onClick()
                            }
                        },
                        expandedWidthPx = expandedWidthPx,
                        colors = colors,
                        shapes = shapes,
                        sizes = sizes,
                        glass = glass,
                        modifier = Modifier.fillMaxHeight(),
                    )

                    if (accessory != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isInline,
                                enter = inlineAccessoryEnter,
                                exit = inlineAccessoryExit,
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (isAccessoryShared) {
                                                Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState("accessory"),
                                                    animatedVisibilityScope = this@AnimatedVisibility,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    accessory(
                                        Modifier
                                            .fillMaxSize()
                                            .background(colors.backgroundColor, shapes.accessoryShape)
                                            .clip(shapes.accessoryShape),
                                        this@AnimatedVisibility,
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    FilterSlot(visible = showFilterCircle, spacing = 0.dp) {
                        FilterCircle(
                            activeCount = filterActiveCount,
                            onClick = onFilterClick,
                            colors = colors,
                            shapes = shapes,
                            sizes = sizes,
                            glass = glass,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCapsule(
    tabs: List<TsuzukiTab>,
    selectedIndex: Int,
    isInline: Boolean,
    showLabels: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onSelectTab: (Int) -> Unit,
    expandedWidthPx: Float,
    colors: TsuzukiTabBarColors,
    shapes: TsuzukiTabBarShapes,
    sizes: TsuzukiTabBarSizes,
    glass: GlassContext?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val haptics = TsuzukiTheme.haptics
    val currentTab = tabs.getOrNull(selectedIndex)
    val capsuleShape = shapes.tabBarShape as? CornerBasedShape ?: RoundedCornerShape(100)

    val labelStyle = TsuzukiTheme.typography.caption2.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = sizes.labelFontSize,
        lineHeight = sizes.labelLineHeight,
    )
    val textMeasurer = rememberTextMeasurer()
    val collapsedLabelLayout = remember(currentTab?.title, showLabels, labelStyle) {
        if (showLabels && currentTab != null) {
            textMeasurer.measure(AnnotatedString(currentTab.title), labelStyle, softWrap = false, maxLines = 1)
        } else {
            null
        }
    }

    val barHeightPx = with(density) { sizes.barHeight.toPx() }
    val iconSizePx = with(density) { sizes.iconSize.toPx() }
    val labelGapPx = with(density) { 6.dp.toPx() }
    val labelLineHeightPx = with(density) { sizes.labelLineHeight.toPx() }
    val inlinePadLeftPx = with(density) { sizes.tabInlineContentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val inlinePadRightPx = with(density) { sizes.tabInlineContentPadding.calculateRightPadding(layoutDirection).toPx() }
    val padLeftPx = with(density) { sizes.tabExpandedContentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padRightPx = with(density) { sizes.tabExpandedContentPadding.calculateRightPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { sizes.tabExpandedContentPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { sizes.tabExpandedContentPadding.calculateBottomPadding().toPx() }

    val collapsedContentWidthPx = if (collapsedLabelLayout != null) {
        inlinePadLeftPx + inlinePadRightPx + iconSizePx + labelGapPx + collapsedLabelLayout.size.width
    } else {
        barHeightPx
    }
    val collapsedWidthPx = collapsedContentWidthPx.coerceAtMost(expandedWidthPx.coerceAtLeast(barHeightPx))

    val tabCount = tabs.size
    val selectedTabIndex = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val contentInnerHeightPx = (barHeightPx - padTopPx - padBottomPx).coerceAtLeast(0f)
    val stackHeightPx = iconSizePx + labelGapPx + labelLineHeightPx
    val expandedIconCenterY = padTopPx + (contentInnerHeightPx - stackHeightPx).coerceAtLeast(0f) / 2f + iconSizePx / 2f
    val collapsedIconCenterX = when {
        collapsedLabelLayout == null -> collapsedWidthPx / 2f
        isRtl -> collapsedWidthPx - (inlinePadLeftPx + iconSizePx / 2f)
        else -> inlinePadLeftPx + iconSizePx / 2f
    }
    val iconLiftYPx = barHeightPx / 2f - expandedIconCenterY
    val geometry = tabBarMorphGeometry(
        progress = progress,
        expandedWidthPx = expandedWidthPx,
        collapsedWidthPx = collapsedWidthPx,
        contentPaddingLeftPx = padLeftPx,
        contentPaddingRightPx = padRightPx,
        tabCount = tabCount,
        selectedIndex = selectedIndex,
        isRtl = isRtl,
        collapsedIconCenterXPx = collapsedIconCenterX,
        iconLiftYPx = iconLiftYPx,
    )
    val contentRowWidthPx = (geometry.contentBoxWidthPx - padLeftPx - padRightPx).coerceAtLeast(0f)
    val tabStepPx = geometry.tabStepPx

    val coroutineScope = rememberCoroutineScope()
    val reduceAnimation = TsuzukiTheme.motion.reduced
    val pill = remember { TsuzukiPillMotion(coroutineScope, selectedTabIndex) }
    pill.reduceMotion = reduceAnimation
    pill.stepDp = if (density.density > 0f) tabStepPx / density.density else 0f

    LaunchedEffect(selectedIndex) {
        pill.animateTo(selectedIndex)
    }

    val indicatorInsetPx = with(density) { 4.dp.toPx() }
    val pillHeightPx = (barHeightPx - 2f * indicatorInsetPx).coerceAtLeast(0f)
    val restSize = Size(tabStepPx, pillHeightPx)
    val liftGrowthPx = with(density) { PillLiftGrowth.toPx() }
    val liftedHeightPx = pillHeightPx + liftGrowthPx
    val liftScale = if (pillHeightPx > 0f) liftedHeightPx / pillHeightPx else 1f
    val liftedSize = Size(restSize.width * liftScale, liftedHeightPx)

    fun pillCenterInCapsule(position: Float): Offset =
        Offset(
            geometry.contentBoxOffset.x + padLeftPx +
                calculatePillCenter(position, tabStepPx, tabStepPx, isRtl, contentRowWidthPx),
            barHeightPx / 2f,
        )

    val dragEdgeResistance = 0.25f
    val dragBiasDivisor = 18f
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val tracker = remember { VelocityCarryTracker() }

    val dragModifier = if (!isInline) {
        Modifier.pointerInput(tabCount, tabStepPx, isRtl) {
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.onStart(0f)
                    pill.startDrag()
                    dragTotalPx = 0f
                },
                onDragEnd = {
                    val releaseVelocityPx = tracker.resolveReleaseVelocity(0f, dragTotalPx)
                    tracker.reset()
                    val rawPosition = if (tabStepPx > 0f) {
                        selectedIndex + dragTotalPx / tabStepPx
                    } else {
                        selectedIndex.toFloat()
                    }
                    val bias = if (tabStepPx > 0f) releaseVelocityPx / (tabStepPx * dragBiasDivisor) else 0f
                    val target = (rawPosition + bias).roundToInt().coerceIn(0, tabCount - 1)
                    pill.release(target, if (tabStepPx > 0f) releaseVelocityPx / tabStepPx else 0f)
                    if (target != selectedIndex) {
                        haptics.select()
                        onSelectTab(target)
                    }
                    dragTotalPx = 0f
                },
                onDragCancel = {
                    tracker.reset()
                    pill.release(selectedIndex)
                    dragTotalPx = 0f
                },
            ) { change, dragAmount ->
                change.consume()
                val stepDelta = if (isRtl) -dragAmount else dragAmount
                dragTotalPx += stepDelta
                tracker.onMove(dragTotalPx)
                val lastTab = (tabCount - 1).coerceAtLeast(0)
                val rawPosition = if (tabStepPx > 0f) {
                    selectedIndex + dragTotalPx / tabStepPx
                } else {
                    selectedIndex.toFloat()
                }
                val resisted = when {
                    rawPosition < 0f -> rawPosition * dragEdgeResistance
                    rawPosition > lastTab -> lastTab + (rawPosition - lastTab) * dragEdgeResistance
                    else -> rawPosition
                }
                pill.dragTo(resisted)
            }
        }
    } else {
        Modifier
    }

    val capsuleInteraction = remember { MutableInteractionSource() }

    GlassSurface(
        context = glass,
        shape = capsuleShape,
        material = GlassMaterial.Regular,
        modifier = modifier
            .width(with(density) { geometry.capsuleWidthPx.toDp() })
            .then(
                if (isInline && currentTab != null) {
                    Modifier
                        .pressScale(capsuleInteraction)
                        .clickable(
                            onClick = {
                                haptics.select()
                                onExpand()
                                currentTab.onClick()
                            },
                            indication = null,
                            interactionSource = capsuleInteraction,
                        )
                        .semantics {
                            contentDescription = "${currentTab.title}, expand navigation"
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(capsuleShape),
        ) {
            val showPill = selectedIndex in tabs.indices && tabStepPx > 0f && pillHeightPx > 0f
            if (showPill) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = geometry.expandedAlpha }
                        .then(
                            pill.placement(restSize, liftedSize) {
                                pillCenterInCapsule(pill.position)
                            },
                        )
                        .background(colors.indicatorColor, RoundedCornerShape(100)),
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            geometry.contentBoxOffset.x.roundToInt(),
                            geometry.contentBoxOffset.y.roundToInt(),
                        )
                    }
                    .requiredWidth(with(density) { geometry.contentBoxWidthPx.toDp() })
                    .fillMaxHeight()
                    .semantics { if (isInline) invisibleToUser() }
                    .then(dragModifier)
                    .padding(
                        start = with(density) { padLeftPx.toDp() },
                        end = with(density) { padRightPx.toDp() },
                        top = with(density) { padTopPx.toDp() },
                        bottom = with(density) { padBottomPx.toDp() },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    tabs.forEachIndexed { tabIndex, tab ->
                        val tabSelected = tabIndex == selectedIndex
                        val tabInteraction = remember { MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = if (tabSelected) {
                                        maxOf(geometry.tabsAlpha, 1f - geometry.collapsedAlpha)
                                    } else {
                                        geometry.tabsAlpha
                                    }
                                }
                                .then(
                                    if (isInline) {
                                        Modifier
                                    } else {
                                        Modifier.clickable(
                                            interactionSource = tabInteraction,
                                            indication = null,
                                            role = Role.Tab,
                                            onClickLabel = tab.title,
                                        ) {
                                            haptics.select()
                                            onSelectTab(tabIndex)
                                        }
                                    },
                                )
                                .semantics {
                                    selected = tabSelected
                                },
                        ) {
                            CompositionLocalProvider(LocalShellTabSelected provides tabSelected) {
                                tab.icon()
                            }
                            if (showLabels) {
                                Spacer(Modifier.height(3.dp))
                                TsuzukiText(
                                    text = tab.title,
                                    modifier = Modifier.graphicsLayer { alpha = geometry.tabsAlpha },
                                    style = labelStyle,
                                    color = if (tabSelected) colors.textColor else colors.unselectedTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (currentTab != null && geometry.collapsedAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = geometry.collapsedAlpha }
                        .semantics { if (!isInline) invisibleToUser() },
                ) {
                    if (collapsedLabelLayout != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = with(density) { inlinePadLeftPx.toDp() }),
                        ) {
                            CompositionLocalProvider(LocalShellTabSelected provides true) {
                                currentTab.icon()
                            }
                            Spacer(Modifier.width(with(density) { labelGapPx.toDp() }))
                            TsuzukiText(
                                text = currentTab.title,
                                modifier = Modifier.width(
                                    with(density) {
                                        (collapsedWidthPx - inlinePadLeftPx - inlinePadRightPx - iconSizePx - labelGapPx)
                                            .coerceAtLeast(0f)
                                            .toDp()
                                    },
                                ),
                                style = labelStyle,
                                color = colors.textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalShellTabSelected provides true) {
                                currentTab.icon()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSlot(visible: Boolean, spacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val motion = TsuzukiTheme.motion
    val tokens = LocalMotionTokens.current
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxHeight(),
        enter = if (motion.reduced) {
            fadeIn(snap())
        } else if (tokens) {
            expandHorizontally(
                tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate),
                expandFrom = Alignment.Start,
            ) +
                scaleIn(tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate), initialScale = 0.6f) +
                fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
        } else {
            expandHorizontally(motion.tabSpring(), expandFrom = Alignment.Start) +
                scaleIn(motion.tabSpring(), initialScale = 0.6f) + fadeIn(tween(160))
        },
        exit = if (motion.reduced) {
            fadeOut(snap())
        } else if (tokens) {
            shrinkHorizontally(
                tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate),
                shrinkTowards = Alignment.Start,
            ) +
                scaleOut(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate), targetScale = 0.6f) +
                fadeOut(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate))
        } else {
            shrinkHorizontally(motion.tabSpring(), shrinkTowards = Alignment.Start) +
                scaleOut(motion.tabSpring(), targetScale = 0.6f) + fadeOut(tween(120))
        },
    ) {
        Box(Modifier.fillMaxHeight().padding(start = spacing)) { content() }
    }
}

@Composable
private fun FilterCircle(
    activeCount: Int,
    onClick: () -> Unit,
    colors: TsuzukiTabBarColors,
    shapes: TsuzukiTabBarShapes,
    sizes: TsuzukiTabBarSizes,
    glass: GlassContext? = null,
    modifier: Modifier = Modifier,
) {
    val filterInteraction = remember { MutableInteractionSource() }
    val motion = TsuzukiTheme.motion
    Box(modifier = modifier.pressScale(filterInteraction)) {
        GlassSurface(
            context = glass,
            shape = shapes.standaloneTabShape as? CornerBasedShape ?: CircleShape,
            material = GlassMaterial.Regular,
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.standaloneTabShape)
                    .clickable(
                        interactionSource = filterInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Library filters",
                        onClick = onClick,
                    )
                    .semantics {
                        contentDescription = if (activeCount > 0) {
                            "Library filters, $activeCount active"
                        } else {
                            "Library filters"
                        }
                        stateDescription = if (activeCount > 0) {
                            "$activeCount active filters"
                        } else {
                            "No active filters"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TsuzukiIcons.SlidersHorizontal,
                    contentDescription = null,
                    modifier = Modifier.size(sizes.filterIconSize),
                    tint = colors.textColor,
                )
            }
        }
        AnimatedVisibility(
            visible = activeCount > 0,
            enter = if (motion.reduced) {
                fadeIn(snap())
            } else if (LocalMotionTokens.current) {
                scaleIn(tween(M3Motion.Short2, easing = M3Motion.Standard), initialScale = 0f) +
                    fadeIn(tween(M3Motion.Short2, easing = M3Motion.Standard))
            } else {
                scaleIn(motion.tabSpring(), initialScale = 0f) + fadeIn(tween(120))
            },
            exit = if (motion.reduced) {
                fadeOut(snap())
            } else if (LocalMotionTokens.current) {
                scaleOut(tween(M3Motion.Short2, easing = M3Motion.StandardAccelerate), targetScale = 0f) +
                    fadeOut(tween(M3Motion.Short2, easing = M3Motion.StandardAccelerate))
            } else {
                scaleOut(motion.tabSpring(), targetScale = 0f) + fadeOut(tween(120))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-2).dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accentColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    text = formatFilterCount(activeCount),
                    style = TsuzukiTheme.typography.caption2.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    ),
                    color = colors.onAccentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
