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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.errnolink.tsuzuki.designsystem.GlassBox
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LiquidGlassGeometry
import dev.errnolink.tsuzuki.designsystem.liquidSelectionLens
import dev.errnolink.tsuzuki.designsystem.rememberGlassLensSource
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.MorphingSearchButton
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.VelocityCarryTracker
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.ui.motion.smoothstep
import dev.errnolink.tsuzuki.designsystem.rememberSelectionBarContent
import dev.errnolink.tsuzuki.designsystem.selectionBarContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.layout.layout
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun TsuzukiTabBarCurrent(
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
    val motion = TsuzukiTheme.motion
    val isAccessoryShared = accessory != null && expandedAccessory != null
    // Design 2B: while the field is up the whole bar fades and shrinks away on the very flight
    // that grows the field (Kasane's one-transition rule), and grows back in from its own
    // position on the close flight — the bar no longer folds into the current tab's circle.
    // MOT-05: the bar is gone by 35 % of the flight (smoothstep), not glued to it 1:1.
    // WP6.3: the flight progress is read in draw through the lambda below; composition only
    // derives the interactive flip.
    val searchAway = remember(search) { derivedStateOf { search?.state?.flightProgress?.coerceIn(0f, 1f) ?: 0f } }
    val folded = isInline
    val interactive by remember(searchAway) { derivedStateOf { searchAway.value < 0.5f } }

    val progress = remember { Animatable(if (isInline) 1f else 0f) }
    LaunchedEffect(folded) {
        val target = if (folded) 1f else 0f
        // Kasane's `.nav` spring: critically damped, the fold reverses from wherever it is.
        progress.animateTo(targetValue = target, animationSpec = KasaneSprings.nav(motion.reduced))
    }

    val inlineAccessoryEnter: EnterTransition =
        if (motion.reduced) {
            fadeIn(snap())
        } else {
            expandHorizontally(KasaneSprings.nav(motion.reduced), expandFrom = Alignment.End) +
                fadeIn(KasaneSprings.nav(motion.reduced))
        }
    val inlineAccessoryExit: ExitTransition =
        if (motion.reduced) {
            fadeOut(snap())
        } else {
            shrinkHorizontally(KasaneSprings.nav(motion.reduced), shrinkTowards = Alignment.End) +
                fadeOut(KasaneSprings.nav(motion.reduced))
        }
    val topAccessoryEnter: EnterTransition =
        if (motion.reduced) {
            fadeIn(snap())
        } else {
            expandVertically(KasaneSprings.nav(motion.reduced), expandFrom = Alignment.Bottom) +
                fadeIn(KasaneSprings.nav(motion.reduced))
        }
    val topAccessoryExit: ExitTransition =
        if (motion.reduced) {
            fadeOut(snap())
        } else {
            shrinkVertically(KasaneSprings.nav(motion.reduced), shrinkTowards = Alignment.Bottom) +
                fadeOut(KasaneSprings.nav(motion.reduced))
        }

    SharedTransitionLayout(modifier = modifier) {
        val awayLayer = Modifier.graphicsLayer {
            val away = searchAway.value
            alpha = searchTabBarAlpha(away, search?.state?.expanded == true)
            val scale = 1f - SearchAwayShrink * away
            scaleX = scale
            scaleY = scale
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (expandedAccessory != null) {
                AnimatedVisibility(visible = !isInline, enter = topAccessoryEnter, exit = topAccessoryExit) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(awayLayer)
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
                        // WP4.1: the accessory's backdrop is the shared glass, not a solid fill.
                        Box(Modifier.fillMaxWidth()) {
                            GlassSurface(
                                context = glass,
                                shape = shapes.accessoryShape,
                                material = GlassMaterial.Thick,
                                modifier = Modifier.matchParentSize(),
                            ) { }
                            expandedAccessory(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.accessoryShape),
                                this@AnimatedVisibility,
                            )
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizes.barHeight),
            ) {
                val containerWidthPx = constraints.maxWidth.toFloat()
                // The search circle's slot width rides the same .nav spring as the fold, so the
                // capsule's width budget changes continuously — no re-measure jump when a
                // screen's search declaration appears or leaves (one continuous morph).
                val searchSlot = remember { Animatable(if (search != null) 1f else 0f) }
                LaunchedEffect(search != null) {
                    searchSlot.animateTo(if (search != null) 1f else 0f, KasaneSprings.nav(motion.reduced))
                }
                // WP6.3: the slot's animated width is read in the capsule's own layout, never in
                // composition — the slot animation does not recompose the bar.
                val searchBlockUnitPx = with(LocalDensity.current) {
                    sizes.barHeight.toPx() + 2 * sizes.componentSpacing.toPx()
                }
                val expandedWidthPx = { (containerWidthPx - searchBlockUnitPx * searchSlot.value).coerceAtLeast(0f) }
                val slotVisible by remember { derivedStateOf { searchSlot.value > 0.001f } }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TabCapsule(
                        tabs = tabs,
                        selectedIndex = selectedIndex,
                        isInline = folded,
                        showLabels = showLabels,
                        progress = { progress.value },
                        onExpand = onExpand,
                        onSelectTab = { index ->
                            if (index in tabs.indices) {
                                tabs[index].onClick()
                            }
                        },
                        expandedWidthPx = expandedWidthPx,
                        interactive = interactive,
                        colors = colors,
                        shapes = shapes,
                        sizes = sizes,
                        glass = glass,
                        pill = pill,
                        modifier = Modifier.fillMaxHeight().then(awayLayer),
                    )

                    if (accessory != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(awayLayer),
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
                                    // WP4.1: the accessory's backdrop is the shared glass, not a solid fill.
                                    Box(Modifier.fillMaxSize()) {
                                        GlassSurface(
                                            context = glass,
                                            shape = shapes.accessoryShape,
                                            material = GlassMaterial.Thick,
                                            modifier = Modifier.matchParentSize(),
                                        ) { }
                                        accessory(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(shapes.accessoryShape),
                                            this@AnimatedVisibility,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    // The trailing circle is the search trigger (iOS 26 tab bar): it pours into the
                    // bottom field, which the shell's dock hosts; it hides under the flying drop.
                    // Its arrival rides the same nav spring as the slot width (no second tween).
                    if (search != null || slotVisible) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = searchSlot.value
                                    val scale = 0.6f + 0.4f * searchSlot.value
                                    scaleX = scale
                                    scaleY = scale
                                },
                        ) {
                            search?.let { activeSearch ->
                                MorphingSearchButton(
                                    state = activeSearch.state,
                                    glass = glass,
                                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                                    contentDescription = activeSearch.placeholder,
                                )
                            }
                        }
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
    progress: () -> Float,
    onExpand: () -> Unit,
    onSelectTab: (Int) -> Unit,
    /** WP6.3: read in layout/draw below — the search-slot animation never recomposes the capsule. */
    expandedWidthPx: () -> Float,
    interactive: Boolean = true,
    colors: TsuzukiTabBarColors,
    shapes: TsuzukiTabBarShapes,
    sizes: TsuzukiTabBarSizes,
    glass: GlassContext?,
    /** NAV-05: hoisted to the shell — the pill survives bar remounts instead of re-seeding. */
    pill: TsuzukiPillMotion,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val haptics = TsuzukiTheme.haptics
    val currentTab = tabs.getOrNull(selectedIndex)
    val capsuleShape = shapes.tabBarShape as? CornerBasedShape ?: RoundedCornerShape(100)
    val collapsedTapLabel = "expand navigation"
    val labelStyle = TsuzukiTheme.typography.caption2.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = sizes.labelFontSize,
        lineHeight = sizes.labelLineHeight,
    )
    val barHeightPx = with(density) { sizes.barHeight.toPx() }
    val iconSizePx = with(density) { sizes.iconSize.toPx() }
    // The same gap the label Spacer uses below: the fold's icon lift is computed from this stack,
    // and a 6 dp model over a 3 dp layout left the folded circle's icon 1.5 dp low.
    val labelGap = 3.dp
    val labelGapPx = with(density) { labelGap.toPx() }
    val labelLineHeightPx = with(density) { sizes.labelLineHeight.toPx() }
    val padLeftPx = with(density) { sizes.tabExpandedContentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padRightPx = with(density) { sizes.tabExpandedContentPadding.calculateRightPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { sizes.tabExpandedContentPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { sizes.tabExpandedContentPadding.calculateBottomPadding().toPx() }

    val tabCount = tabs.size

    // WP6.3: every value derived from the animated capsule width is a function read at its point
    // of use (layout, draw or an input event), so the slot animation runs without recomposition.
    fun expandedWidth(): Float = expandedWidthPx()
    fun collapsedWidth(): Float = barHeightPx.coerceAtMost(expandedWidth().coerceAtLeast(barHeightPx))
    fun contentBoxWidth(): Float = expandedWidth().coerceAtLeast(0f)
    fun innerWidth(): Float = (contentBoxWidth() - padLeftPx - padRightPx).coerceAtLeast(0f)
    fun tabStep(): Float = if (tabCount > 0) innerWidth() / tabCount else 0f

    val selectedTabIndex = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val contentInnerHeightPx = (barHeightPx - padTopPx - padBottomPx).coerceAtLeast(0f)
    val stackHeightPx = iconSizePx + if (showLabels) labelGapPx + labelLineHeightPx else 0f
    val expandedIconCenterY = padTopPx + (contentInnerHeightPx - stackHeightPx).coerceAtLeast(0f) / 2f + iconSizePx / 2f
    val iconLiftYPx = barHeightPx / 2f - expandedIconCenterY

    val reduceAnimation = TsuzukiTheme.motion.reduced
    pill.reduceMotion = reduceAnimation

    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelectTab)
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    // First attach of the (hoisted) pill snaps to the current tab: a bar that remounts must not
    // fly the pill in from the seed index (NAV-05).
    var pillSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        if (!pillSeeded) {
            pill.snapTo(selectedIndex)
            pillSeeded = true
        } else if (pill.dragging) {
            pill.release(selectedIndex)
            dragTotalPx = 0f
        }
        pill.animateTo(selectedIndex)
    }

    LaunchedEffect(tabCount) {
        pill.snapTo(selectedIndex)
    }

    val indicatorInsetPx = with(density) { 4.dp.toPx() }
    val pillHeightPx = (barHeightPx - 2f * indicatorInsetPx).coerceAtLeast(0f)
    val liftGrowthPx = with(density) { PillLiftGrowth.toPx() }
    val liftedHeightPx = pillHeightPx + liftGrowthPx
    val liftScale = if (pillHeightPx > 0f) liftedHeightPx / pillHeightPx else 1f
    val lensScale = if (pillHeightPx > 0f) (pillHeightPx + with(density) { LensLiftGrowth.toPx() }) / pillHeightPx else 1f

     fun pillCenterInCapsule(position: Float): Offset =
         Offset(
            glassLensShiftX(progress(), pill.position, tabStep(), padLeftPx, innerWidth(), isRtl, collapsedWidth() / 2f) + padLeftPx +
                 calculatePillCenter(position, tabStep(), tabStep(), isRtl, innerWidth()),
             barHeightPx / 2f,
         )

    val dragEdgeResistance = 0.25f
    val tracker = remember { VelocityCarryTracker() }

    val dragModifier = if (!isInline && interactive) {
        Modifier.pointerInput(tabCount, isRtl) {
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.onStart(0f)
                    pill.startDrag()
                    dragTotalPx = 0f
                },
                onDragEnd = {
                    val releaseVelocityPx = tracker.resolveReleaseVelocity(0f, dragTotalPx)
                    tracker.reset()
                    if (pill.dragging) {
                        val step = tabStep()
                        val target = tabDragTarget(currentSelected, dragTotalPx, step, releaseVelocityPx, tabCount)
                        pill.release(target, if (step > 0f) releaseVelocityPx / step else 0f)
                        if (target != currentSelected) {
                            haptics.select()
                            currentOnSelect(target)
                        }
                    }
                    dragTotalPx = 0f
                },
                onDragCancel = {
                    tracker.reset()
                    if (pill.dragging) {
                        pill.release(currentSelected)
                    }
                    dragTotalPx = 0f
                },
            ) { change, dragAmount ->
                change.consume()
                if (!pill.dragging) return@detectHorizontalDragGestures
                val stepDelta = if (isRtl) -dragAmount else dragAmount
                dragTotalPx += stepDelta
                tracker.onMove(dragTotalPx)
                val lastTab = (tabCount - 1).coerceAtLeast(0)
                val step = tabStep()
                val rawPosition = if (step > 0f) {
                    currentSelected + dragTotalPx / step
                } else {
                    currentSelected.toFloat()
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

    val lensSource = rememberGlassLensSource(glass)
    val barContent = rememberSelectionBarContent()
    // The capsule and the held lens share one box: the lens grows past the capsule's edge, so it
    // lives outside the capsule's clip.
    val lensCapsule = remember { LensCapsule() }
    Box(
        modifier.layout { measurable, constraints ->
            // WP6.3: the slot-driven width is read here (and pill.stepDp refreshed), so the slot
            // animation drives layout without recomposing the capsule.
            pill.stepDp = if (density.density > 0f) tabStep() / density.density else 0f
            val width = tabMorphCapsuleWidthPx(progress(), expandedWidth(), collapsedWidth()).roundToInt().coerceAtLeast(0)
            lensCapsule.width = width
            lensCapsule.height = constraints.maxHeight
            val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
            layout(width, constraints.maxHeight) {
                placeable.place(0, 0)
            }
        },
    ) {
    GlassSurface(
        context = glass,
        shape = capsuleShape,
        material = GlassMaterial.Thick,
        modifier = Modifier
            .matchParentSize()
            // The bar's own content (glass, pill, rows) is what the held lens magnifies.
            .then(if (lensSource.live) Modifier.selectionBarContent(barContent) else Modifier)
            .then(
                if (isInline && currentTab != null) {
                    Modifier
                        .pressScale(capsuleInteraction)
                        .clickable(
                            enabled = interactive,
                            onClick = {
                                haptics.select()
                                onExpand()
                            },
                            indication = null,
                            interactionSource = capsuleInteraction,
                        )
                        .semantics {
                            contentDescription = "${currentTab.title}, $collapsedTapLabel"
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
            val showPill = selectedIndex in tabs.indices && tabCount > 0 && pillHeightPx > 0f
            if (showPill) {
                val lensLift = ((pill.liftX + pill.liftY) / 2f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = morphIndicatorAlpha(progress()) }
                        .then(
                            pill.placement(
                                { Size(tabStep(), pillHeightPx) },
                                { Size(tabStep() * liftScale, liftedHeightPx) },
                                { pillCenterInCapsule(pill.position) },
                            ) {
                                pillTravelStretch(pill.travelProgress(), pill.travelSpanSteps() * pill.stepDp)
                            },
                        ),
                ) {
                    // Expressive tint at rest; it yields to the brighter glass as the pill lifts.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - (if (lensSource.live) 1f else 0.45f) * lensLift }
                            .background(colors.indicatorColor, RoundedCornerShape(100)),
                    )
                    // The held lens without a live backdrop: brighter than the tint, with a
                    // specular rim (lens.css lifted). With one, the real lens draws above the bar.
                    if (!lensSource.live) Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lensLift }
                            .drawBehind {
                                val radius = CornerRadius(size.height / 2f)
                                drawRoundRect(color = Color.White.copy(alpha = 0.07f), cornerRadius = radius)
                                val ring = Stroke(1.dp.toPx())
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.55f),
                                    topLeft = Offset(ring.width / 2f, ring.width / 2f),
                                    size = Size(
                                        (size.width - ring.width).coerceAtLeast(0f),
                                        (size.height - ring.width).coerceAtLeast(0f),
                                    ),
                                    cornerRadius = radius,
                                    style = ring,
                                )
                            },
                    )
                }
            }
             Box(
                 modifier = Modifier
                     .layout { measurable, constraints ->
                         val travel = progress().coerceIn(0f, 1f)
                         val placeable = measurable.measure(
                             Constraints.fixed(contentBoxWidth().roundToInt().coerceAtLeast(0), constraints.maxHeight),
                         )
                         layout(constraints.maxWidth, constraints.maxHeight) {
                            // The lens shift follows the PILL's spring position, not the static
                            // selection: a tab switch while collapsed glides the old tab's icon
                            // out of the circle and the new one in — no teleport, no identity
                            // swap (one continuous morph, one progress).
                             placeable.place(
                                glassLensShiftX(travel, pill.position, tabStep(), padLeftPx, innerWidth(), isRtl, collapsedWidth() / 2f).roundToInt(),
                                 lerp(0f, iconLiftYPx, travel).roundToInt(),
                             )
                         }
                     }
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
                        // Kasane: the tab the lens is over previews as selected (colour and fill).
                        val underLens = pill.dragging && abs(pill.position - tabIndex) < 0.5f
                        val visualSelected = tabSelected || underLens
                        val tabInteraction = remember { MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    if (!visualSelected) {
                                        val fade = tabAlpha(
                                            index = tabIndex,
                                            progress = progress(),
                                            expandedWidthPx = expandedWidth(),
                                            collapsedWidthPx = collapsedWidth(),
                                            contentPaddingLeftPx = padLeftPx,
                                            innerWidthPx = innerWidth(),
                                            tabStepPx = tabStep(),
                                            isRtl = isRtl,
                                            collapsedIconCenterXPx = collapsedWidth() / 2f,
                                            selectedIndex = selectedTabIndex,
                                        )
                                        alpha = fade
                                        val s = 1f - MorphOthersShrink * (1f - fade)
                                        scaleX = s
                                        scaleY = s
                                    }
                                }
                                .then(
                                    if (isInline || !interactive) {
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
                                    if (!showLabels || !tabSelected) contentDescription = tab.title
                                },
                        ) {
                            CompositionLocalProvider(LocalShellTabSelected provides visualSelected) {
                                tab.icon()
                            }
                            if (showLabels) {
                                Spacer(Modifier.height(labelGap))
                                TsuzukiText(
                                    text = tab.title,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = if (visualSelected) {
                                                morphLabelAlpha(progress())
                                            } else {
                                                // N17: the label dims while the travelling pill crosses it.
                                                tabLabelAlphaUnderPill(pill.position, tabIndex, selectedIndex)
                                            }
                                        }
                                        .semantics { if (!tabSelected) invisibleToUser() },
                                    style = labelStyle,
                                    color = if (visualSelected) colors.textColor else colors.unselectedTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // The selected-colour copy of the rows, clipped to the lens rect: recorded with the
            // bar so the held lens magnifies a label that is half-tinted mid-drag.
            if (showPill && lensSource.live) {
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            // The copy is not magnified: it sits exactly on the bar's own icons, so where the
                            // lens edge crosses an icon the two halves meet without a seam. The clip grows
                            // with the lens instead of scaling the content.
                            val stretch = pillTravelStretch(pill.travelProgress(), pill.travelSpanSteps() * pill.stepDp)
                            val sx = (1f + (lensScale - 1f) * pill.liftX) * (1f + stretch)
                            val sy = 1f + (lensScale - 1f) * pill.liftY
                            val w = (tabStep() * sx).roundToInt().coerceAtLeast(1)
                            val h = (pillHeightPx * sy).roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeWithLayer(0, 0) {
                                    val center = pillCenterInCapsule(pill.position)
                                    translationX = center.x - w / 2f
                                    translationY = center.y - h / 2f
                                    val lift = ((pill.liftX + pill.liftY) / 2f).coerceIn(0f, 1f)
                                    val fade = (lift / 0.2f).coerceIn(0f, 1f)
                                    alpha = fade * fade * (3f - 2f * fade) * morphIndicatorAlpha(progress())
                                    shape = RoundedCornerShape(50)
                                    clip = true
                                }
                            }
                        }
                        .semantics { invisibleToUser() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val w = constraints.maxWidth
                                val h = constraints.maxHeight
                                val full = measurable.measure(Constraints.fixed(lensCapsule.width, lensCapsule.height))
                                layout(w, h) {
                                    val center = pillCenterInCapsule(pill.position)
                                    full.place((w / 2f - center.x).roundToInt(), (h / 2f - center.y).roundToInt())
                                }
                            }
                            .padding(
                                start = with(density) { padLeftPx.toDp() },
                                end = with(density) { padRightPx.toDp() },
                                top = with(density) { padTopPx.toDp() },
                                bottom = with(density) { padBottomPx.toDp() },
                            ),
                    ) {
                        tabs.forEachIndexed { tabIndex, tab ->
                            val underLens = abs(pill.position - tabIndex) < 0.5f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalShellTabSelected provides underLens) { tab.icon() }
                                if (showLabels) {
                                    Spacer(Modifier.height(labelGap))
                                    TsuzukiText(
                                        text = tab.title,
                                        style = labelStyle,
                                        color = if (underLens) colors.textColor else colors.unselectedTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val badgeHidden = tabs.withIndex().any { (index, tab) -> tab.badge && index != selectedIndex }
            if (badgeHidden) {
                Box(
                    modifier = Modifier
                        .offset(y = with(density) { (barHeightPx * 0.2f).toDp() })
                        .size(sizes.badgeSize * 0.6f)
                        .graphicsLayer {
                            alpha = morphBadgeDotAlpha(progress())
                            // Follows the collapsed capsule's edge; read here, not in composition.
                            translationX = collapsedWidth() * 0.64f
                        }
                        .background(colors.badgeColor, CircleShape)
                        .semantics { invisibleToUser() },
                )
            }
        }
    }
    if (lensSource.live && selectedIndex in tabs.indices && tabCount > 0 && pillHeightPx > 0f) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val w = tabStep().roundToInt().coerceAtLeast(1)
                    val h = pillHeightPx.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeWithLayer(0, 0) {
                            val center = pillCenterInCapsule(pill.position)
                            translationX = center.x - w / 2f
                            translationY = center.y - h / 2f
                        }
                    }
                }
                .liquidSelectionLens(
                    glass,
                    barContent,
                    geometry = { w, h ->
                        // The lens fills this node: the layer scale below carries the lift wobble
                        // and travel stretch, the pass magnifies the page+bar backdrop by 1.15x.
                        LiquidGlassGeometry(
                            GlassBox.Empty,
                            GlassBox(w / 2f, h / 2f, w / 2f, h / 2f, minOf(w, h) / 2f),
                            0f,
                            1f + 0.15f * ((pill.liftX + pill.liftY) / 2f),
                            Offset(w / 2f, h / 2f),
                        )
                    },
                    lift = { (pill.liftX + pill.liftY) / 2f },
                ) {
                    // Width and height ride their own lift springs (different damping), so the
                    // lens wobbles like a held drop; travel stretches it along the move.
                    val stretch = pillTravelStretch(pill.travelProgress(), pill.travelSpanSteps() * pill.stepDp)
                    scaleX = (1f + (lensScale - 1f) * pill.liftX) * (1f + stretch)
                    scaleY = 1f + (lensScale - 1f) * pill.liftY
                    alpha *= morphIndicatorAlpha(progress())
                },
        )
    }
    }
}

private class LensCapsule {
    var width = 0
    var height = 0
}
