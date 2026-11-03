package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

data class MenuPositionResult(
    val offset: IntOffset,
    val origin: TransformOrigin,
    val maxAvailableHeightPx: Int,
    val isAbove: Boolean,
)

private const val MenuEdgeMarginPx = 16
private const val MenuSettleDamping = 0.75f
private const val ActionHandoffMillis = 180L
private val SubmenuCardElevation = 8.dp
private const val ParentContentDimAlpha = 0.45f
private const val ParentRecedeScale = 0.93f

fun computeMenuPosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    isLtr: Boolean,
    popupSize: IntSize,
    marginPx: Int = 16,
    verticalGapPx: Int = 6,
    offsetPx: IntOffset = IntOffset.Zero,
    glueToAnchor: Boolean = false,
): MenuPositionResult {
    val anchorCenter = anchorBounds.center.x
    val targetX = if (!isLtr || anchorCenter > windowSize.width / 2) {
        anchorBounds.right - popupSize.width + offsetPx.x
    } else {
        anchorBounds.left + offsetPx.x
    }
    val minX = marginPx
    val maxX = (windowSize.width - popupSize.width - marginPx).coerceAtLeast(minX)
    val clampedX = targetX.coerceIn(minX, maxX)

    val spaceBelow = (windowSize.height - anchorBounds.bottom - marginPx).coerceAtLeast(0)
    val spaceAbove = (anchorBounds.top - marginPx).coerceAtLeast(0)

    val placeAbove = when {
        popupSize.height <= spaceBelow -> false
        popupSize.height <= spaceAbove -> true
        if (glueToAnchor) spaceAbove >= spaceBelow else spaceAbove > spaceBelow -> true
        else -> false
    }

    val sideSpace = if (placeAbove) spaceAbove else spaceBelow
    val placedHeight = if (glueToAnchor && popupSize.height > sideSpace) sideSpace else popupSize.height

    val targetY = if (placeAbove) {
        anchorBounds.top - placedHeight - verticalGapPx + offsetPx.y
    } else {
        anchorBounds.bottom + verticalGapPx + offsetPx.y
    }

    val minY = marginPx
    val maxY = (windowSize.height - popupSize.height - marginPx).coerceAtLeast(minY)
    val clampedY = targetY.coerceIn(minY, maxY)

    val originX = if (popupSize.width > 0) {
        ((anchorCenter - clampedX).toFloat() / popupSize.width).coerceIn(0f, 1f)
    } else 0.5f

    val originY = if (placeAbove) 1f else 0f
    val availableHeight = sideSpace

    return MenuPositionResult(
        offset = IntOffset(clampedX, clampedY),
        origin = TransformOrigin(originX, originY),
        maxAvailableHeightPx = availableHeight.coerceAtLeast(120),
        isAbove = placeAbove,
    )
}

class PullDownMenuPositionProvider(
    private val offsetPx: IntOffset = IntOffset.Zero,
    private val marginPx: Int = 16,
    private val verticalGapPx: Int = 6,
    private val glueToAnchor: Boolean = false,
) : PopupPositionProvider {
    var origin by mutableStateOf(TransformOrigin.Center)
        private set
    var maxAvailableHeightPx by mutableIntStateOf(Int.MAX_VALUE)
        private set
    var contentOffset by mutableStateOf(IntOffset.Zero)
        private set
    var positionLocked: Boolean = false

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        if (positionLocked) {
            return contentOffset
        }
        val result = computeMenuPosition(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            isLtr = layoutDirection == LayoutDirection.Ltr,
            popupSize = popupContentSize,
            marginPx = marginPx,
            verticalGapPx = verticalGapPx,
            offsetPx = offsetPx,
            glueToAnchor = glueToAnchor,
        )
        origin = result.origin
        maxAvailableHeightPx = result.maxAvailableHeightPx
        contentOffset = result.offset
        return result.offset
    }
}

class PullDownAction(
    val label: String,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    val iconContent: (@Composable () -> Unit)? = null,
    val subtitle: String? = null,
    val group: Int = 0,
    val destructive: Boolean = false,
    val selected: Boolean? = null,
    val onClick: () -> Unit,
)

class SubmenuData(
    val title: String,
    val content: @Composable ColumnScope.() -> Unit,
)

class SubmenuHolder {
    var activeSubmenu by mutableStateOf<SubmenuData?>(null)
    var anchorBounds by mutableStateOf<Rect?>(null)
}

val LocalSubmenuHolder = compositionLocalOf<SubmenuHolder?> { null }

data class TsuzukiMenuLabels(
    val collapseSubmenu: String = "Collapse submenu",
    val selected: String = "Selected",
    val menu: String = "Menu",
    val confirm: String = "Confirm",
    val cancel: String = "Cancel",
)

val LocalTsuzukiMenuLabels = staticCompositionLocalOf { TsuzukiMenuLabels() }

data class SubmenuPlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

fun computeSubmenuPlacement(
    rowBounds: IntRect,
    parentWidthPx: Int,
    naturalHeightPx: Int,
    windowBounds: IntRect,
    liftPx: Int,
    marginPx: Int,
): SubmenuPlacement {
    val height = naturalHeightPx
        .coerceAtMost(windowBounds.height - 2 * marginPx)
        .coerceAtLeast(1)
    val maxEndY = (windowBounds.bottom - marginPx - height).coerceAtLeast(windowBounds.top + marginPx)
    val y = (rowBounds.top - liftPx).coerceIn(windowBounds.top + marginPx, maxEndY)
    return SubmenuPlacement(x = 0, y = y, width = parentWidthPx, height = height)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

private fun Rect.toIntRect(): IntRect = IntRect(
    left = left.roundToInt(),
    top = top.roundToInt(),
    right = right.roundToInt(),
    bottom = bottom.roundToInt(),
)

@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    paneTitle: String? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    glueToAnchor: Boolean = false,
    width: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = TsuzukiTheme.motion
    val haptics = LocalHaptics.current ?: rememberHaptics()
    val density = LocalDensity.current
    val labels = LocalTsuzukiMenuLabels.current

    val offsetPx = remember(offset, density) {
        with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    }
    val positionProvider = remember(offsetPx, glueToAnchor) {
        PullDownMenuPositionProvider(offsetPx = offsetPx, glueToAnchor = glueToAnchor)
    }
    val submenuHolder = remember { SubmenuHolder() }
    val activeSubmenu = submenuHolder.activeSubmenu
    var mounted by remember { mutableStateOf(false) }
    val menuScale = remember { Animatable(1f) }
    val menuBlur = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            submenuHolder.activeSubmenu = null
            submenuHolder.anchorBounds = null
            mounted = true
            haptics.select()
            if (motion.reduced) {
                menuScale.snapTo(1f)
                menuBlur.snapTo(0f)
            } else {
                menuScale.snapTo(0.6f)
                menuBlur.snapTo(12f)
                launch {
                    menuBlur.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 260, easing = M3Motion.EmphasizedDecelerate),
                    )
                }
                menuScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = MenuSettleDamping, stiffness = Spring.StiffnessMediumLow),
                )
            }
        } else {
            if (motion.reduced) {
                menuScale.snapTo(1f)
                menuBlur.snapTo(0f)
            } else {
                launch {
                    menuBlur.animateTo(
                        targetValue = 8f,
                        animationSpec = tween(durationMillis = 180, easing = M3Motion.EmphasizedAccelerate),
                    )
                }
                menuScale.animateTo(
                    targetValue = 0.6f,
                    animationSpec = tween(durationMillis = 180, easing = M3Motion.EmphasizedAccelerate),
                )
            }
        }
    }

    val transition = updateTransition(targetState = expanded, label = "PullDownMenuTransition")
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (motion.reduced) {
                M3Motion.reducedFade()
            } else if (targetState) {
                tween(120, easing = M3Motion.EmphasizedDecelerate)
            } else {
                tween(180, easing = M3Motion.EmphasizedAccelerate)
            }
        },
        label = "MenuAlpha",
    ) { isVisible -> if (isVisible) 1f else 0f }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.currentState && !transition.targetState) {
            mounted = false
        }
    }

    if (!mounted) return

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val dynamicMaxHeight = remember(screenHeightDp, positionProvider.maxAvailableHeightPx, density) {
        if (positionProvider.maxAvailableHeightPx < Int.MAX_VALUE) {
            with(density) { positionProvider.maxAvailableHeightPx.toDp() }
        } else {
            (screenHeightDp - 64.dp).coerceAtLeast(160.dp)
        }
    }

    val windowContainer = LocalWindowInfo.current.containerSize
    val submenuProgress = remember { Animatable(0f) }
    val presenting = activeSubmenu != null
    positionProvider.positionLocked = presenting
    val shownSubmenu = remember { mutableStateOf<SubmenuData?>(null) }
    if (activeSubmenu != null) {
        shownSubmenu.value = activeSubmenu
    }
    val showChildCard = presenting || submenuProgress.value > 0f
    LaunchedEffect(presenting) {
        if (presenting) {
            if (motion.reduced) {
                submenuProgress.snapTo(1f)
            } else {
                val dur = if (motion.standardTokens) M3Motion.Medium2 else M3Motion.Medium1
                submenuProgress.animateTo(1f, M3Motion.emphasized(dur))
            }
        } else if (submenuProgress.value > 0f) {
            if (motion.reduced) {
                submenuProgress.snapTo(0f)
            } else {
                val dur = if (motion.standardTokens) M3Motion.Medium2 else M3Motion.Medium1
                submenuProgress.animateTo(0f, M3Motion.emphasized(dur))
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            if (submenuHolder.activeSubmenu != null) {
                submenuHolder.activeSubmenu = null
            } else {
                onDismissRequest()
            }
        },
        properties = properties,
    ) {
        CompositionLocalProvider(
            LocalInsideGlass provides false,
            LocalSubmenuHolder provides submenuHolder,
        ) {
            val cardShape = RoundedCornerShape(TsuzukiCorners.group)
            val liftPx = with(density) { TsuzukiSpacing.tiny.roundToPx() }
            val rowHeightPx = with(density) { 44.dp.roundToPx() }
            SubcomposeLayout { constraints ->
                val menuPanel: @Composable (Boolean) -> Unit = { scrollable ->
                    val menuScroll = rememberScrollState()
                    GlassSurface(
                        context = null,
                        material = GlassMaterial.Thick,
                        shape = cardShape,
                        modifier = modifier
                            .then(if (width != null) Modifier.width(width) else Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp, max = 280.dp))
                            .heightIn(max = dynamicMaxHeight)
                            .graphicsLayer {
                                val recede = lerp(1f, ParentRecedeScale, submenuProgress.value)
                                scaleX = menuScale.value * recede
                                scaleY = menuScale.value * recede
                                this.alpha = alpha
                                transformOrigin = if (submenuProgress.value > 0f) {
                                    TransformOrigin(1f, 0f)
                                } else {
                                    positionProvider.origin
                                }
                            }
                            .then(
                                if (motion.reduced || menuBlur.value <= 0.01f) {
                                    Modifier
                                } else {
                                    Modifier.blur(menuBlur.value.dp)
                                },
                            )
                            .semantics { this.paneTitle = paneTitle ?: labels.menu },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fadeEdges(menuScroll)
                                .then(if (scrollable) Modifier.verticalScroll(menuScroll) else Modifier)
                                .graphicsLayer {
                                    this.alpha = 1f - (1f - ParentContentDimAlpha) * submenuProgress.value
                                },
                            content = content,
                        )
                        if (showChildCard) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { submenuHolder.activeSubmenu = null },
                                    ),
                            )
                        }
                    }
                }
                val capPx = with(density) { dynamicMaxHeight.roundToPx() }
                val probed = subcompose("panel-probe") { menuPanel(false) }[0].measure(
                    constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
                )
                val parentMeasurable = if (probed.height >= capPx) {
                    subcompose("panel-scroll") { menuPanel(true) }[0].measure(
                        constraints.copy(minWidth = 0, minHeight = 0),
                    )
                } else {
                    probed
                }

                if (!showChildCard) {
                    return@SubcomposeLayout layout(parentMeasurable.width, parentMeasurable.height) {
                        parentMeasurable.place(0, 0)
                    }
                }

                val submenuData = shownSubmenu.value
                    ?: return@SubcomposeLayout layout(parentMeasurable.width, parentMeasurable.height) {
                        parentMeasurable.place(0, 0)
                    }
                val childMeasurables = subcompose("submenu") {
                    val submenuScroll = rememberScrollState()
                    GlassSurface(
                        context = null,
                        material = GlassMaterial.Thick,
                        shape = cardShape,
                        modifier = Modifier
                            .graphicsLayer { this.alpha = submenuProgress.value.coerceIn(0f, 1f) }
                            .shadow(elevation = SubmenuCardElevation, shape = cardShape, clip = false),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fadeEdges(submenuScroll)
                                .verticalScroll(submenuScroll),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = TsuzukiSpacing.touchTarget)
                                    .clickable(role = Role.Button) {
                                        submenuHolder.activeSubmenu = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TsuzukiText(
                                    text = submenuData.title,
                                    style = TsuzukiTheme.typography.callout,
                                    color = TsuzukiTheme.colors.text,
                                    maxLines = 1,
                                )
                                Image(
                                    imageVector = TsuzukiIcons.ChevronDown,
                                    contentDescription = labels.collapseSubmenu,
                                    colorFilter = ColorFilter.tint(TsuzukiTheme.colors.accent),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            PullDownDivider()
                            submenuData.content(this)
                        }
                    }
                }

                val anchorRect = submenuHolder.anchorBounds?.toIntRect()
                    ?: IntRect(0, 0, parentMeasurable.width, rowHeightPx)
                val windowBounds = IntRect(
                    left = -positionProvider.contentOffset.x,
                    top = -positionProvider.contentOffset.y,
                    right = windowContainer.width - positionProvider.contentOffset.x,
                    bottom = windowContainer.height - positionProvider.contentOffset.y,
                )
                val settledAnchor = IntRect(
                    left = parentMeasurable.width -
                        ((parentMeasurable.width - anchorRect.left) * ParentRecedeScale).roundToInt(),
                    top = (anchorRect.top * ParentRecedeScale).roundToInt(),
                    right = parentMeasurable.width -
                        ((parentMeasurable.width - anchorRect.right) * ParentRecedeScale).roundToInt(),
                    bottom = (anchorRect.bottom * ParentRecedeScale).roundToInt(),
                )
                val placement = computeSubmenuPlacement(
                    rowBounds = settledAnchor,
                    parentWidthPx = parentMeasurable.width,
                    naturalHeightPx = childMeasurables[0].minIntrinsicHeight(parentMeasurable.width),
                    windowBounds = windowBounds,
                    liftPx = liftPx,
                    marginPx = MenuEdgeMarginPx,
                )

                val progress = submenuProgress.value.coerceIn(0f, 1f)
                val startHeight = anchorRect.height.takeIf { it > 0 } ?: rowHeightPx
                val rectX = lerp(anchorRect.left.toFloat(), placement.x.toFloat(), progress).roundToInt()
                val rectY = lerp(anchorRect.top.toFloat(), placement.y.toFloat(), progress).roundToInt()
                val rectWidth = lerp(
                    parentMeasurable.width.toFloat(),
                    placement.width.toFloat(),
                    progress,
                ).roundToInt()
                val rectHeight = lerp(
                    startHeight.toFloat(),
                    placement.height.toFloat(),
                    progress,
                ).roundToInt().coerceAtLeast(1)
                val childPlaceable = childMeasurables[0].measure(
                    Constraints.fixed(rectWidth, rectHeight),
                )

                return@SubcomposeLayout layout(
                    maxOf(parentMeasurable.width, placement.x + placement.width),
                    maxOf(parentMeasurable.height, placement.y + placement.height),
                ) {
                    parentMeasurable.place(0, 0)
                    childPlaceable.place(rectX, rectY)
                }
            }
        }
    }
}

@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<PullDownAction>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    paneTitle: String? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
) {
    val reduced = TsuzukiTheme.motion.reduced
    val scope = rememberCoroutineScope()
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        paneTitle = paneTitle,
        properties = properties,
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                if (actions[index - 1].group != action.group) {
                    PullDownSectionDivider()
                } else {
                    PullDownDivider()
                }
            }
            PullDownMenuItem(
                text = action.label,
                subtitle = action.subtitle,
                icon = action.icon,
                iconContent = action.iconContent,
                selected = action.selected,
                destructive = action.destructive,
                enabled = action.enabled,
                onClick = {
                    onDismissRequest()
                    if (reduced) {
                        action.onClick()
                    } else {
                        scope.launch {
                            delay(ActionHandoffMillis)
                            action.onClick()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun PullDownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    selected: Boolean? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val textColor = when {
        !enabled -> colors.secondary.copy(alpha = 0.5f)
        destructive -> colors.destructive
        else -> colors.text
    }
    val iconTint = when {
        !enabled -> colors.secondary.copy(alpha = 0.5f)
        destructive -> colors.destructive
        else -> colors.secondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .clickable(
                enabled = enabled,
                role = if (selected != null) Role.RadioButton else Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = TsuzukiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TsuzukiText(
                text = text,
                style = TsuzukiTheme.typography.callout,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                TsuzukiText(
                    text = subtitle,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected == true) {
            Image(
                imageVector = TsuzukiIcons.Check,
                contentDescription = LocalTsuzukiMenuLabels.current.selected,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(18.dp),
            )
        } else if (icon != null) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconTint),
                modifier = Modifier.size(18.dp),
            )
        } else if (iconContent != null) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                iconContent()
            }
        }
    }
}

@Composable
fun PullDownDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(TsuzukiSpacing.hairline)
            .background(TsuzukiTheme.colors.separator.copy(alpha = 0.6f)),
    )
}

@Composable
fun PullDownSectionDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(TsuzukiSpacing.small)
            .background(TsuzukiTheme.colors.separator.copy(alpha = 0.25f)),
    )
}

@Composable
fun PullDownSubmenuItem(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val submenuHolder = LocalSubmenuHolder.current
    var rowBounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
            .clickable(role = Role.Button) {
                submenuHolder?.anchorBounds = rowBounds
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = title,
            style = TsuzukiTheme.typography.callout,
            color = colors.text,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (value != null) {
                TsuzukiText(
                    text = value,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Image(
                    imageVector = TsuzukiIcons.ChevronRight,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.secondary),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun PullDownSubmenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.callout,
            color = if (selected) colors.accent else colors.text,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        if (selected) {
            Image(
                imageVector = TsuzukiIcons.Check,
                contentDescription = LocalTsuzukiMenuLabels.current.selected,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
enum class PullDownTriState {
    Off,
    Include,
    Exclude,
}

data class PullDownPaletteOption(
    val icon: ImageVector,
    val label: String,
)

@Composable
fun PullDownIconPaletteRow(
    options: List<PullDownPaletteOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.tiny),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TsuzukiSpacing.touchTarget)
                    .clip(RoundedCornerShape(TsuzukiCorners.cover))
                    .background(
                        if (isSelected) colors.accent.copy(alpha = 0.14f) else Color.Transparent,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .pressScale(interaction)
                    .then(if (!enabled) Modifier.alpha(0.45f) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                ) {
                    Image(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        colorFilter = ColorFilter.tint(if (isSelected) colors.accent else colors.secondary),
                        modifier = Modifier.size(18.dp),
                    )
                    TsuzukiText(
                        text = option.label,
                        style = TsuzukiTheme.typography.caption2,
                        color = if (isSelected) colors.accent else colors.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun PullDownTriStateItem(
    label: String,
    state: PullDownTriState,
    onSet: (PullDownTriState) -> Unit,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val haptics = LocalHaptics.current ?: rememberHaptics()
    val isExcluded = state == PullDownTriState.Exclude
    val stateLabel = when (state) {
        PullDownTriState.Off -> "Off"
        PullDownTriState.Include -> "Include"
        PullDownTriState.Exclude -> "Exclude"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .semantics { stateDescription = stateLabel }
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.toggleOn()
                onCycle()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = label,
            style = if (isExcluded) {
                TsuzukiTheme.typography.callout.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                TsuzukiTheme.typography.callout
            },
            color = when {
                !enabled -> colors.secondary.copy(alpha = 0.5f)
                state == PullDownTriState.Include -> colors.accent
                isExcluded -> colors.secondary
                else -> colors.text
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        val (chipText, chipBg, chipFg) = when (state) {
            PullDownTriState.Off -> Triple("Any", colors.separator.copy(alpha = 0.25f), colors.secondary)
            PullDownTriState.Include -> Triple("Only", colors.accent.copy(alpha = 0.16f), colors.accent)
            PullDownTriState.Exclude -> Triple("Hide", colors.destructive.copy(alpha = 0.16f), colors.destructive)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(chipBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiText(
                text = chipText,
                style = TsuzukiTheme.typography.caption1,
                color = if (enabled) chipFg else chipFg.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun PullDownToggleItem(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current ?: rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    if (it) haptics.toggleOn() else haptics.toggleOff()
                    onChecked(it)
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.callout,
            color = if (enabled) TsuzukiTheme.colors.text else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        MenuSwitchMark(checked = checked, enabled = enabled)
    }
}

@Composable
private fun MenuSwitchMark(checked: Boolean, enabled: Boolean) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 2.dp,
        animationSpec = if (motion.reduced) snap() else motion.tabSpring(),
        label = "MenuSwitchThumb",
    )
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> colors.secondary.copy(alpha = 0.3f)
                    checked -> colors.accent
                    else -> colors.separator
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 2.dp, start = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.background),
        )
    }
}

@Composable
fun PullDownStepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String = value.toString(),
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val haptics = LocalHaptics.current ?: rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.callout,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.tiny),
        ) {
            StepperButton(
                symbol = "−",
                contentDescription = "Decrease $label",
                enabled = canDecrement,
                onClick = {
                    haptics.select()
                    onDecrement()
                },
            )
            TsuzukiText(
                text = valueLabel,
                style = TsuzukiTheme.typography.callout.copy(textAlign = TextAlign.Center),
                color = colors.secondary,
                modifier = Modifier.widthIn(min = 36.dp),
            )
            StepperButton(
                symbol = "+",
                contentDescription = "Increase $label",
                enabled = canIncrement,
                onClick = {
                    haptics.select()
                    onIncrement()
                },
            )
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.separator.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TsuzukiText(
            text = symbol,
            style = TsuzukiTheme.typography.callout,
            color = if (enabled) colors.text else colors.secondary.copy(alpha = 0.5f),
        )
    }
}

private fun Modifier.fadeEdges(scrollState: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = 14.dp.toPx()
            if (scrollState.value > 0) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = 0f,
                        endY = fade,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.value < scrollState.maxValue) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
fun PullDownConfirmSheet(
    visible: Boolean,
    title: String,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val labels = LocalTsuzukiMenuLabels.current
    DetentSheet(
        visible = visible,
        title = title,
        onDismissRequest = onDismissRequest,
        initialDetent = SheetDetent.Medium,
        scrollContent = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuzukiSpacing.gutter, vertical = TsuzukiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            if (message != null) {
                TsuzukiText(
                    text = message,
                    style = TsuzukiTheme.typography.callout,
                    color = TsuzukiTheme.colors.secondary,
                )
            }
            PillButton(
                label = confirmLabel ?: labels.confirm,
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
                large = true,
            )
            PillButton(
                label = labels.cancel,
                onClick = onDismissRequest,
                prominent = false,
                modifier = Modifier.fillMaxWidth(),
                large = true,
            )
        }
    }
}

data class PopUpItem<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
)

@Composable
fun <T> PopUpButton(
    selected: T,
    options: List<PopUpItem<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = options.firstOrNull { it.value == selected }
    val selectedLabel = selectedItem?.label ?: selected.toString()
    val haptics = LocalHaptics.current ?: rememberHaptics()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (interactive) {
                        Modifier.clickable(enabled = enabled, role = Role.Button) {
                            haptics.select()
                            expanded = true
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TsuzukiText(
                text = selectedLabel,
                style = TsuzukiTheme.typography.callout,
                color = if (enabled) TsuzukiTheme.colors.secondary else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Image(
                imageVector = TsuzukiIcons.ChevronsUpDown,
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (enabled) TsuzukiTheme.colors.secondary else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                ),
                modifier = Modifier.size(14.dp),
            )
        }

        if (interactive) {
            PullDownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { index, option ->
                    if (index > 0) PullDownDivider()
                    val isSelected = option.value == selected
                    PullDownMenuItem(
                        text = option.label,
                        subtitle = option.subtitle,
                        icon = option.icon,
                        selected = isSelected,
                        onClick = {
                            onSelect(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun <T> PopUpRow(
    title: String,
    selected: T,
    options: List<PopUpItem<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current ?: rememberHaptics()
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TsuzukiSpacing.touchTarget)
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.select()
                    expanded = true
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leading?.invoke()
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TsuzukiText(
                        text = title,
                        style = TsuzukiTheme.typography.callout,
                        color = if (enabled) TsuzukiTheme.colors.text else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                    )
                    if (subtitle != null) {
                        TsuzukiText(
                            text = subtitle,
                            style = TsuzukiTheme.typography.footnote,
                            color = TsuzukiTheme.colors.secondary,
                        )
                    }
                }
            }
            PopUpButton(
                selected = selected,
                options = options,
                onSelect = onSelect,
                enabled = enabled,
                interactive = false,
            )
        }
        if (divider) {
            PullDownDivider()
        }
    }
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) PullDownDivider()
            val isSelected = option.value == selected
            PullDownMenuItem(
                text = option.label,
                subtitle = option.subtitle,
                icon = option.icon,
                selected = isSelected,
                onClick = {
                    onSelect(option.value)
                    expanded = false
                },
            )
        }
    }
}
