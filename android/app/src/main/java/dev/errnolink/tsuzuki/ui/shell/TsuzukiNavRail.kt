package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.MorphingSearchButton
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val RailWidth = 72.dp
internal val RailEdgeGap = 12.dp
internal val RailContentStart = RailWidth + RailEdgeGap * 2

private val RailItemHeight = 56.dp
private val RailPillSize = 56.dp

@Composable
internal fun TsuzukiNavRail(
    tabs: List<TsuzukiTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    showLabels: Boolean,
    search: TopBarSearch? = null,
    modifier: Modifier = Modifier,
    colors: TsuzukiTabBarColors = TsuzukiTabBarDefaults.colors(),
    glass: GlassContext? = LocalShellGlass.current,
) {
    val density = LocalDensity.current
    val reduceAnimation = TsuzukiTheme.motion.reduced
    val haptics = TsuzukiTheme.haptics
    val tabCount = tabs.size
    val selected = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val stepPx = with(density) { RailItemHeight.toPx() }
    val capsulePadPx = with(density) { 4.dp.toPx() }
    val railWidthPx = with(density) { RailWidth.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val pill = remember { TsuzukiPillMotion(coroutineScope, selected) }
    pill.reduceMotion = reduceAnimation
    pill.stepDp = if (density.density > 0f) stepPx / density.density else 0f
    LaunchedEffect(selected) { pill.animateTo(selected) }
    LaunchedEffect(tabCount) { pill.snapTo(selected) }

    val restSize = with(density) { Size(RailPillSize.toPx(), RailPillSize.toPx()) }
    val liftGrowthPx = with(density) { PillLiftGrowth.toPx() }
    val liftScale = if (restSize.height > 0f) (restSize.height + liftGrowthPx) / restSize.height else 1f
    val liftedSize = Size(restSize.width * liftScale, restSize.height + liftGrowthPx)
    val capsuleHeightDp = 4.dp * 2 + RailItemHeight * tabCount

    Column(
        modifier
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Start))
            .padding(start = RailEdgeGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            context = glass,
            shape = RoundedCornerShape(100),
            material = GlassMaterial.Regular,
            modifier = Modifier.width(RailWidth).height(capsuleHeightDp),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (tabCount > 0) {
                    Box(
                        Modifier
                            .then(
                                pill.placement(
                                    { restSize },
                                    { liftedSize },
                                    { Offset(railWidthPx / 2f, capsulePadPx + (pill.position + 0.5f) * stepPx) },
                                ) {
                                    pillTravelStretch(pill.travelProgress(), pill.travelSpanSteps() * pill.stepDp)
                                },
                            )
                            .background(colors.indicatorColor, RoundedCornerShape(100)),
                    )
                }
                Column(Modifier.padding(vertical = 4.dp)) {
                    tabs.forEachIndexed { index, tab ->
                        val tabSelected = index == selected
                        val tabInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .width(RailWidth)
                                .height(RailItemHeight)
                                .clickable(
                                    interactionSource = tabInteraction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClickLabel = tab.title,
                                ) {
                                    haptics.select()
                                    onSelectTab(index)
                                }
                                .semantics {
                                    this.selected = tabSelected
                                    if (!showLabels) contentDescription = tab.title
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CompositionLocalProvider(LocalShellTabSelected provides tabSelected) {
                                    tab.icon()
                                }
                                if (showLabels) {
                                    Spacer(Modifier.height(3.dp))
                                    TsuzukiText(
                                        text = tab.title,
                                        style = TsuzukiTheme.typography.tabLabel,
                                        color = if (tabSelected) colors.textColor else colors.unselectedTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        RailToolSlot(
            visible = search != null,
            reduced = reduceAnimation,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            search?.let { activeSearch ->
                // The rail's search trigger; the field itself docks at the bottom (shell dock).
                MorphingSearchButton(
                    state = activeSearch.state,
                    glass = glass,
                    modifier = Modifier.size(TsuzukiSize.touch),
                    contentDescription = activeSearch.placeholder,
                )
            }
        }
    }
}

@Composable
private fun RailToolSlot(
    visible: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(snap())
        } else {
            expandVertically(tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate)) +
                fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
        },
        exit = if (reduced) {
            fadeOut(snap())
        } else {
            shrinkVertically(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate)) +
                fadeOut(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate))
        },
    ) {
        Row {
            content()
        }
    }
}
