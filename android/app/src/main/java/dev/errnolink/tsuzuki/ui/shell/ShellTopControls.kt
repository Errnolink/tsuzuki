package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import kotlinx.coroutines.NonCancellable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.MorphingSearchButton
import dev.errnolink.tsuzuki.designsystem.MorphingSearchOverlay
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.LocalMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.MenuMorphSource
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.menuScrubLauncher
import androidx.compose.runtime.CompositionLocalProvider
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize

private val MorphBlurRadius = 10.dp
private const val SettledSwapMillis = 180
private const val IconOutEnd = 0.6f
private const val IconInStart = 0.25f
private const val IconBlurShare = 0.5f
/** How far the actions slide away while the morphing field lands over the row (Kasane search). */
private val SearchSlideAwayPx = 12.dp

class ShellTopAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val menuTitle: String? = null,
    val menu: List<PullDownAction>? = null,
    val content: (@Composable () -> Unit)? = null,
    /** The back slot's mode-switch morph: rests as glass, active becomes a tinted check circle. */
    val checkMorph: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is ShellTopAction &&
            other.label == label &&
            other.icon == icon &&
            other.active == active &&
            other.enabled == enabled &&
            other.menuTitle == menuTitle &&
            other.checkMorph == checkMorph &&
            other.menu?.map(PullDownAction::label) == menu?.map(PullDownAction::label)

    override fun hashCode(): Int = label.hashCode() * 31 + icon.hashCode()
}

@Stable
class ShellTopDeclaration {
    var title: String by mutableStateOf("")
    var glass: GlassContext? by mutableStateOf(null)
    var back: ShellTopAction? by mutableStateOf(null)
    var actions: List<ShellTopAction> by mutableStateOf(emptyList())
    var search: TopBarSearch? by mutableStateOf(null)
    var onTitleClick: (() -> Unit)? by mutableStateOf(null)
    var inlineTitle: Float by mutableFloatStateOf(1f)
    var edge: Boolean by mutableStateOf(true)
    var scrolledUnder: Boolean by mutableStateOf(false)
    /** Continuous scrolled-under progress (TOP-02); wins over the boolean when provided. */
    var scrolledUnderFraction: (() -> Float)? by mutableStateOf(null)
    var tint: Color? by mutableStateOf(null)
    var leadingContent: (@Composable () -> Unit)? by mutableStateOf(null)
    var leadingKey: Any? by mutableStateOf(null)
}

val LocalShellTopOwner = staticCompositionLocalOf<String?> { null }

object ShellTopControls {
    val declarations = mutableStateMapOf<String, ShellTopDeclaration>()

    /** The shell body's tab-switch morph (0 the frame the switch starts, 1 settled), published by
     *  HomeShell so the top row's header swap rides the same clock as the body's fade-through
     *  (one transition, one progress; WP3.2). */
    var tabMorph by mutableStateOf<(() -> Float)?>(null)
        private set

    /** Called by HomeShell each composition; the lambda instance is remembered, so this writes once. */
    fun publishTabMorph(morph: () -> Float) {
        if (tabMorph !== morph) tabMorph = morph
    }

    /** Kasane's close rule for the bottom dock's × circle, the collapsed tab bar tap and the rail. */
    fun closeSearch(search: TopBarSearch) {
        search.onClose?.invoke()
        search.state.collapse()
    }
}

val LocalShellTopActive = androidx.compose.runtime.compositionLocalOf { true }

@Composable
fun DeclareShellTop(
    title: String,
    glass: GlassContext? = null,
    navigateUp: (() -> Unit)? = null,
    backLabel: String = "Back",
    backIcon: ImageVector = TsuzukiIcons.Back,
    actions: List<ShellTopAction> = emptyList(),
    search: TopBarSearch? = null,
    onTitleClick: (() -> Unit)? = null,
    inlineTitle: (() -> Float)? = null,
    edge: Boolean = true,
    scrolledUnder: Boolean = false,
    /** Continuous 0..1 scrolled-under progress (TOP-02); screens with a list state pass it so the
     *  band tracks the scroll. The boolean stays for screens without one. */
    scrolledUnderFraction: (() -> Float)? = null,
    tint: Color? = null,
    // Mode-switch morph: while selection is active the back slot rests as the tinted check circle.
    backCheckMorph: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    leadingKey: Any? = null,
) {
    val owner = LocalShellTopOwner.current ?: return
    val active = LocalShellTopActive.current
    val declaration = remember(owner) { ShellTopDeclaration() }
    val currentTitle by rememberUpdatedState(title)
    val currentGlass by rememberUpdatedState(glass)
    val currentBack = remember(navigateUp, backLabel, backIcon, backCheckMorph) {
        navigateUp?.let {
            ShellTopAction(
                label = backLabel,
                icon = backIcon,
                onClick = it,
                checkMorph = backCheckMorph,
            )
        }
    }
    val currentActions by rememberUpdatedState(actions)
    val currentSearch by rememberUpdatedState(search)
    val currentTitleClick by rememberUpdatedState(onTitleClick)
    val currentInline by rememberUpdatedState(inlineTitle)
    val currentLeading by rememberUpdatedState(leadingContent)
    val currentLeadingKey by rememberUpdatedState(leadingKey)
    val inlineNow = currentInline?.invoke() ?: 1f
    SideEffect {
        if (!active) return@SideEffect
        declaration.title = currentTitle
        declaration.glass = currentGlass
        declaration.back = currentBack
        declaration.actions = currentActions
        declaration.search = currentSearch
        declaration.onTitleClick = currentTitleClick
        declaration.inlineTitle = inlineNow
        declaration.edge = edge
        declaration.scrolledUnder = scrolledUnder
        declaration.scrolledUnderFraction = scrolledUnderFraction
        declaration.tint = tint
        declaration.leadingContent = currentLeading
        declaration.leadingKey = currentLeadingKey
        ShellTopControls.declarations[owner] = declaration
    }
    DisposableEffect(owner) {
        onDispose {
            if (ShellTopControls.declarations[owner] === declaration) {
                ShellTopControls.declarations.remove(owner)
            }
        }
    }
}

private class ShellTopSnap(
    val declaration: ShellTopDeclaration?,
    val title: String = declaration?.title.orEmpty(),
    val back: ShellTopAction? = declaration?.back,
    val actions: List<ShellTopAction> = declaration?.actions.orEmpty(),
    val glass: GlassContext? = declaration?.glass,
    val onTitleClick: (() -> Unit)? = declaration?.onTitleClick,
    val leadingContent: (@Composable () -> Unit)? = declaration?.leadingContent,
    val leadingKey: Any? = declaration?.leadingKey,
) {
    override fun equals(other: Any?): Boolean =
        other is ShellTopSnap &&
            other.title == title &&
            other.back == back &&
            other.actions == actions &&
            other.glass === glass &&
            other.leadingKey == leadingKey

    override fun hashCode(): Int = title.hashCode()
}

internal data class TopRowMorphState(val showFrom: Boolean, val morph: Float)

internal fun resolveTopRowMorph(
    moving: Boolean,
    justSettled: Boolean,
    tabJustSettled: Boolean,
    progress: Float,
    tabMorph: Float,
    hasDiff: Boolean,
    reduced: Boolean,
    armedMatches: Boolean,
    swapValue: Float,
): TopRowMorphState = when {
    moving -> TopRowMorphState(showFrom = true, morph = progress.coerceIn(0f, 1f))
    justSettled || tabJustSettled || !hasDiff -> TopRowMorphState(showFrom = false, morph = 1f)
    tabMorph < 0.999f -> TopRowMorphState(showFrom = true, morph = tabMorph.coerceIn(0f, 1f))
    reduced -> TopRowMorphState(showFrom = true, morph = 1f)
    !armedMatches -> TopRowMorphState(showFrom = true, morph = 0f)
    else -> TopRowMorphState(showFrom = true, morph = swapValue)
}

@Composable
internal fun ShellTopControlRow(
    frontKey: String?,
    backKey: String?,
    moving: Boolean,
    progress: () -> Float,
    frontTint: Color? = null,
    startPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
    /** 3B: pushed pages host the morphing search in this row; tab roots use the bottom dock. */
    topSearch: Boolean = false,
) {
    val front = frontKey?.let { ShellTopControls.declarations[it] }
    val back = backKey?.let { ShellTopControls.declarations[it] }
    val motion = TsuzukiTheme.motion
    val density = LocalDensity.current

    var shown by remember { mutableStateOf(ShellTopSnap(front)) }
    val swap = remember { Animatable(1f) }
    // NAV-01/SEL-01: a changed declaration must not draw its destination at morph 1 before the
    // swap has armed. `armed` turns set the same moment the snap to 0 happens inside the effect;
    // until then (and on the restart frame of a superseded swap) composition reads morph 0.
    var armed by remember { mutableStateOf<ShellTopSnap?>(null) }
    LaunchedEffect(moving) {
        if (!moving) {
            swap.snapTo(1f)
            shown = ShellTopSnap(front)
            armed = null
        }
    }
    LaunchedEffect(front?.title, front?.back, front?.actions, front?.glass, front?.leadingKey, moving) {
        val target = ShellTopSnap(front)
        if (moving || shown == target) return@LaunchedEffect
        // A tab switch rides the shell body's own morph (WP3.2): header and body crossfade on one
        // clock; the swap machinery only syncs `shown` once that morph has settled.
        val shared = ShellTopControls.tabMorph
        if (!motion.reduced && shared != null && shared() < 0.999f) {
            try {
                snapshotFlow { shared() }.first { it >= 0.999f }
            } finally {
                withContext(NonCancellable) {
                    swap.snapTo(1f)
                    shown = target
                    armed = null
                }
            }
            return@LaunchedEffect
        }
        if (motion.reduced) {
            shown = target
        } else {
            try {
                swap.snapTo(0f)
                armed = target
                swap.animateTo(1f, tween(SettledSwapMillis, easing = M3Motion.Emphasized))
            } finally {
                withContext(NonCancellable) {
                    swap.snapTo(1f)
                    shown = target
                    armed = null
                }
            }
        }
    }

    val to = ShellTopSnap(front)
    val tabMorph = ShellTopControls.tabMorph?.invoke() ?: 1f
    var prevMoving by remember { mutableStateOf(moving) }
    var prevTabMorphing by remember { mutableStateOf(tabMorph < 0.999f) }
    val justSettled = prevMoving && !moving
    val tabJustSettled = prevTabMorphing && tabMorph >= 0.999f
    SideEffect {
        prevMoving = moving
        prevTabMorphing = tabMorph < 0.999f
        if (justSettled || tabJustSettled) {
            shown = to
            armed = null
        } else if (!moving && armed == null && shown == to && shown.leadingContent !== to.leadingContent) {
            shown = to
        }
    }
    val hasDiff = shown != to
    val morphState = resolveTopRowMorph(
        moving = moving,
        justSettled = justSettled,
        tabJustSettled = tabJustSettled,
        progress = progress(),
        tabMorph = tabMorph,
        hasDiff = hasDiff,
        reduced = motion.reduced,
        armedMatches = armed == to,
        swapValue = swap.value,
    )
    val from: ShellTopSnap? = if (morphState.showFrom) {
        if (moving) ShellTopSnap(back) else shown
    } else {
        null
    }
    val morph: Float = morphState.morph

    val visible = from != null || to.declaration != null
    val baseColors = TsuzukiTheme.colors
    val fromTint = from?.declaration?.tint ?: (if (to.declaration == null) frontTint else to.declaration.tint)?.takeIf { from == null }
    val toTint = if (to.declaration == null) frontTint else to.declaration.tint
    // A cover-tinted page wears the same cover scheme as its body (CoverTheme), not the raw swatch.
    val dark = baseColors.isDark
    val fromColors = fromTint?.let { remember(it, dark) { dev.errnolink.tsuzuki.designsystem.expressiveCoverColors(it, dark) } }
    val toColors = toTint?.let { remember(it, dark) { dev.errnolink.tsuzuki.designsystem.expressiveCoverColors(it, dark) } }
    val colors = dev.errnolink.tsuzuki.designsystem.lerpColors(fromColors ?: baseColors, toColors ?: baseColors, morph)
    val fontScale = density.fontScale
    val bodyHeight = (56.dp * fontScale).coerceAtLeast(56.dp)
    val edgeAlpha = {
        lerpValues(
            if (from?.declaration?.edge != false) 1f else 0f,
            if (to.declaration?.edge != false) 1f else 0f,
            morph,
        ).coerceIn(0f, 1f)
    }
    // 3B: pushed pages keep the magnifier in the top bar; the field lands in this row. Tab roots
    // host their search at the bottom (the dock), and so do pushed pages that declare
    // `bottomHosted` (round 12: Global search) — exactly one field per screen.
    val search = if (topSearch) front?.search?.takeIf { !it.bottomHosted } else null
    val searchOpen = !moving && search?.state?.expanded == true
    // The flight's own progress drives the fold-away: the title and the actions fade and
    // slide with the drop, and back closes the field first.
    val searchFraction = if (moving || search == null) 0f else search.state.flightProgress
    // N18 + TOP-02: the band rises with the inline title OR the first 24 dp of content scrolling
    // under the row — whichever first — and the value is read in draw, so scrolling moves the
    // band without recomposing the row and no tween chases the scroll.
    val band = {
        val inline = lerpValues(from?.declaration?.inlineTitle ?: 0f, to.declaration?.inlineTitle ?: 0f, morph)
            .coerceIn(0f, 1f)
        val under = lerpValues(
            from?.declaration?.scrolledUnderNow() ?: 0f,
            to.declaration?.scrolledUnderNow() ?: 0f,
            morph,
        ).coerceIn(0f, 1f)
        val flight = if (moving || search == null) 0f else search.state.flightProgress
        maxOf(inline, under) * (1f - flight)
    }
    // WP6.6: while the stack stage flies, the band's glass rides the same progress (a bell: 0 at
    // rest and at settle, 1 mid-flight) so the input scale never snaps at a stage boundary.
    val bandMotion = { if (moving) 1f - kotlin.math.abs(2f * progress().coerceIn(0f, 1f) - 1f) else 0f }
    val glassSource = if (morph < 0.5f) from ?: to else to ?: from

    var leadingWidth by remember { mutableStateOf(0.dp) }
    var trailingWidth by remember { mutableStateOf(0.dp) }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    var rowContentRect by remember { mutableStateOf(Rect.Zero) }
    androidx.compose.runtime.CompositionLocalProvider(
        dev.errnolink.tsuzuki.designsystem.LocalTsuzukiColors provides colors,
        dev.errnolink.tsuzuki.designsystem.LocalTsuzukiContentColor provides colors.text,
        dev.errnolink.tsuzuki.designsystem.LocalGlassContext provides (glassSource?.glass ?: dev.errnolink.tsuzuki.designsystem.LocalGlassContext.current),
    ) {
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowOrigin = it.positionInWindow() },
    ) {
        if (visible) {
            TopEdge(
                edgeAlpha = edgeAlpha,
                band = band,
                glass = glassSource?.glass,
                motionFraction = bandMotion,
                modifier = Modifier.matchParentSize(),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp + startPadding, end = 16.dp, top = 4.dp, bottom = 4.dp)
                .heightIn(min = bodyHeight)
                .onGloballyPositioned { rowContentRect = it.boundsInWindow() }
                .onSizeChanged { size ->
                    with(density) { TsuzukiTopBar.bodyHeightDp = size.height.toDp().value }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.onSizeChanged { leadingWidth = with(density) { it.width.toDp() } }) {
                LeadingContentSlot(from, to, morph)
                BackCircleSlot(from, to, morph, moving, searchOpen) {
                    search?.let { ShellTopControls.closeSearch(it) }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.onSizeChanged { trailingWidth = with(density) { it.width.toDp() } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search leads the trailing group; the actions (the menu last) sit at the edge.
                if (search != null && !moving) {
                    MorphingSearchButton(
                        state = search.state,
                        glass = to.glass,
                        modifier = Modifier.padding(end = 8.dp).size(TsuzukiSize.touch),
                        contentDescription = search.placeholder,
                    )
                }
                ActionsSlot(from, to, morph, searchFraction, moving)
            }
        }
        // N16: the collapsed title is plain centred text between the bar buttons — no container,
        // so nothing reads as an empty input field while it forms.
        Box(
            Modifier
                .matchParentSize()
                .statusBarsPadding()
                .padding(
                    start = 16.dp + startPadding + leadingWidth,
                    end = 16.dp + trailingWidth + 8.dp,
                    top = 4.dp,
                    bottom = 4.dp,
                )
                .heightIn(min = bodyHeight),
            contentAlignment = Alignment.Center,
        ) {
            TitleSlot(from, to, morph, searchFraction)
        }
        if (search != null && !moving) {
            // Field landing: the padded row content, minus the back slot; vertically centred on it.
            val content = Rect(
                rowContentRect.left - rowOrigin.x,
                rowContentRect.top - rowOrigin.y,
                rowContentRect.right - rowOrigin.x,
                rowContentRect.bottom - rowOrigin.y,
            )
            val fieldHeight = content.height
            MorphingSearchOverlay(
                search = search,
                glass = to.glass,
                targetRect = Rect(content.left, content.top, content.right, content.top + fieldHeight),
                hostOrigin = rowOrigin,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    }
}

@Composable
private fun LeadingContentSlot(
    from: ShellTopSnap?,
    to: ShellTopSnap?,
    morph: Float,
) {
    val outFraction = M3Motion.fadeThroughOut(morph)
    val inFraction = M3Motion.fadeThroughIn(morph)
    val fromLeading = from?.leadingContent
    val toLeading = to?.leadingContent
    if (from == null || from.leadingKey == to?.leadingKey) {
        toLeading?.invoke()
        return
    }
    if (fromLeading != null && outFraction > 0.01f) {
        Box(Modifier.alpha(outFraction)) { fromLeading() }
    }
    if (toLeading != null && inFraction > 0.01f) {
        Box(Modifier.alpha(inFraction)) { toLeading() }
    }
}

@Composable
private fun BackCircleSlot(
    from: ShellTopSnap?,
    to: ShellTopSnap?,
    morph: Float,
    moving: Boolean,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
) {
    val toAlpha = if (to?.back != null) 1f else 0f
    val fromAlpha = when {
        from == null -> toAlpha
        from.back != null -> 1f
        else -> 0f
    }
    val alpha = lerpValues(fromAlpha, toAlpha, morph).coerceIn(0f, 1f)
    val fromAction = from?.back
    val toAction = to?.back
    // The settled swap and the navigation morph both ride morph: the outgoing icon blurs out while
    // the incoming one blurs in, and a checkMorph target pours its tinted fill over the glass.
    val crossfade = fromAction != null && fromAction != toAction
    val outFraction = M3Motion.fadeThroughOut(morph)
    val inFraction = M3Motion.fadeThroughIn(morph)
    val checkFill = lerpValues(
        if (fromAction?.checkMorph == true) 1f else 0f,
        if (toAction?.checkMorph == true) 1f else 0f,
        if (crossfade) inFraction else 1f,
    ).coerceIn(0f, 1f)
    val action = toAction ?: fromAction
    val glassSource = if (morph < 0.5f) from ?: to else to ?: from
    if (alpha <= 0.01f) {
        Spacer(Modifier.size(0.dp))
        return
    }
    Box(Modifier.alpha(alpha)) {
        val backInteraction = remember { MutableInteractionSource() }
        val backPressState = rememberPressShapeState(backInteraction, restRadius = CapsuleRestRadius)
        TopChrome(
            glassSource?.glass,
            Modifier.size(TsuzukiSize.touch).then(
                if (backPressState.enabled) {
                    Modifier.pressShapeScale(backPressState)
                } else {
                    Modifier
                },
            ),
            shape = if (backPressState.enabled) {
                backPressState.shape
            } else {
                RoundedCornerShape(TsuzukiCorners.capsulePercent)
            },
            material = GlassMaterial.Thick,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (backPressState.enabled) {
                            Modifier
                        } else {
                            Modifier.pressScale(backInteraction)
                        },
                    )
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        enabled = !moving && (action != null || searchOpen),
                        role = Role.Button,
                        onClickLabel = if (searchOpen) null else action?.label,
                    ) {
                        // An open search closes first; the back action runs when it is closed.
                        if (searchOpen) onCloseSearch() else action?.onClick()
                    }
                    .semantics { contentDescription = action?.label ?: "" },
                contentAlignment = Alignment.Center,
            ) {
                // The tinted check circle under the mode-switch morph; the glass shows through
                // until the fill covers it.
                if (checkFill > 0.01f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { this.alpha = checkFill }
                            .clip(CircleShape)
                            .background(TsuzukiTheme.colors.accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            TsuzukiIcons.Check,
                            null,
                            Modifier
                                .size(22.dp)
                                .alpha(checkFill)
                                .then(
                                    if (TsuzukiTheme.motion.reduced || checkFill >= 0.99f) Modifier
                                    else Modifier.blur(MorphBlurRadius * (1f - checkFill)),
                                ),
                            tint = TsuzukiTheme.colors.accent,
                        )
                    }
                }
                if (!crossfade) {
                    if (action != null && action.checkMorph != true) {
                        Icon(action.icon, null, Modifier.size(22.dp), tint = TsuzukiTheme.colors.text)
                    }
                } else {
                    if (fromAction != null && outFraction > 0.01f && fromAction.checkMorph != true) {
                        Icon(
                            fromAction.icon,
                            null,
                            Modifier
                                .size(22.dp)
                                .alpha(outFraction)
                                .then(if (TsuzukiTheme.motion.reduced) Modifier else Modifier.blur(MorphBlurRadius * (1f - outFraction))),
                            tint = TsuzukiTheme.colors.text,
                        )
                    }
                    if (toAction != null && inFraction > 0.01f && toAction.checkMorph != true) {
                        Icon(
                            toAction.icon,
                            null,
                            Modifier
                                .size(22.dp)
                                .alpha(inFraction * (1f - checkFill))
                                .then(if (TsuzukiTheme.motion.reduced) Modifier else Modifier.blur(MorphBlurRadius * (1f - inFraction))),
                            tint = TsuzukiTheme.colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleSlot(
    from: ShellTopSnap?,
    to: ShellTopSnap?,
    morph: Float,
    searchFraction: Float,
) {
    val outFraction = M3Motion.fadeThroughOut(morph)
    val inFraction = M3Motion.fadeThroughIn(morph)
    val fromInline = from?.declaration?.inlineTitle ?: 0f
    val toInline = to?.declaration?.inlineTitle ?: 0f
    if (from == to) {
        if (to != null && to.title.isNotEmpty()) {
            CollapsedTitle(to.title, toInline * (1f - searchFraction), to.onTitleClick)
        }
    } else {
        if (from != null && outFraction > 0.01f && from.title.isNotEmpty()) {
            CollapsedTitle(from.title, outFraction * fromInline * (1f - searchFraction), from.onTitleClick)
        }
        if (to != null && inFraction > 0.01f && to.title.isNotEmpty()) {
            CollapsedTitle(to.title, inFraction * toInline * (1f - searchFraction), to.onTitleClick)
        }
    }
}

/**
 * N16: the collapsed page title — plain centred text (~17 sp semibold, the row's headline style)
 * cross-fading with the large title as the scroll collapses it. No glass container (the wide
 * outlined capsule read as an empty input field) and no blur while it forms (the blur smeared
 * the glyphs mid-transition).
 */
@Composable
private fun CollapsedTitle(
    title: String,
    alpha: Float,
    onTitleClick: (() -> Unit)?,
) {
    val shown = alpha.coerceIn(0f, 1f)
    if (shown <= 0.01f) return
    TsuzukiText(
        title,
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = shown }
            .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier)
            .semantics { contentDescription = title }
            .padding(horizontal = 4.dp),
        TsuzukiTheme.typography.headline,
        color = TsuzukiTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActionsSlot(
    from: ShellTopSnap?,
    to: ShellTopSnap?,
    morph: Float,
    searchFraction: Float,
    moving: Boolean,
) {
    val fromActions = from?.actions.orEmpty()
    val toActions = to?.actions.orEmpty()
    val morphing = moving || (from != null && from != to)
    val width = if (morphing) {
        lerpDp(capsuleWidth(fromActions.size), capsuleWidth(toActions.size), morph)
    } else {
        capsuleWidth(toActions.size)
    }
    val alpha = lerpValues(
        if (fromActions.isNotEmpty() || from == null) 1f else 0f,
        if (toActions.isNotEmpty()) 1f else 0f,
        morph,
    ).coerceIn(0f, 1f) * (1f - searchFraction)
    if (width <= 0.dp || alpha <= 0.01f) {
        Spacer(Modifier.width(0.dp))
        return
    }
    // The actions slide away with the field's flight so the capsule never sits under the glass.
    val slidePx = with(LocalDensity.current) { SearchSlideAwayPx.toPx() } * (if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f) * searchFraction
    val source = if (morph < 0.5f) from ?: to else to ?: from
    val count = maxOf(fromActions.size, toActions.size)
    // Every capsule pours into its menus as one glass (iOS 26 Calendar: the long-pressed back button
    // swells over the neighbouring toolbar group as one liquid): with several actions the menu still
    // covers the whole capsule and the sibling icons ride into the liquid with it.
    val capsuleSource = rememberMenuMorphSource()
    TopChrome(
        source?.glass,
        Modifier.width(width).height(48.dp).alpha(alpha)
            .graphicsLayer { translationX = slidePx }
            .menuMorphSource(capsuleSource),
        material = GlassMaterial.Thick,
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { index ->
                    ActionSlot(
                        fromActions.getOrNull(index),
                        toActions.getOrNull(index),
                        morph,
                        moving,
                        enabled = true,
                        capsuleSource = capsuleSource,
                    )
                }
            }
        }
    }
}

/** One action is a 48 dp circle (like the search circle beside it); more share a capsule with 4 dp ends. */
private fun capsuleWidth(actionCount: Int): Dp = when {
    actionCount <= 0 -> 0.dp
    actionCount == 1 -> 48.dp
    else -> (actionCount * 48 + 8).dp
}

@Composable
private fun TopChrome(
    glass: dev.errnolink.tsuzuki.designsystem.GlassContext?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(TsuzukiCorners.capsulePercent),
    material: GlassMaterial,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    GlassSurface(glass, modifier, shape = shape, material = material, content = content)
}

@Composable
private fun ActionSlot(
    from: ShellTopAction?,
    to: ShellTopAction?,
    morph: Float,
    moving: Boolean,
    enabled: Boolean = true,
    capsuleSource: MenuMorphSource? = null,
) {
    if (from == to || (from != null && to != null && from.label == to.label && from.icon == to.icon)) {
        to?.let { RowTopButton(it, 1f, 0f, moving, enabled, capsuleSource) }
        return
    }
    val outFraction = (1f - morph / IconOutEnd).coerceIn(0f, 1f)
    val inFraction = ((morph - IconInStart) / (1f - IconInStart)).coerceIn(0f, 1f)
    Box(Modifier.size(TsuzukiSize.touch), contentAlignment = Alignment.Center) {
        if (from != null && outFraction > 0.01f) {
            RowTopButton(from, outFraction, (1f - outFraction) * IconBlurShare, moving, enabled, capsuleSource)
        }
        if (to != null && inFraction > 0.01f) {
            RowTopButton(to, inFraction, (1f - inFraction) * IconBlurShare, moving, enabled, capsuleSource)
        }
    }
}

@Composable
private fun RowTopButton(
    action: ShellTopAction,
    alpha: Float,
    blurFraction: Float,
    moving: Boolean,
    enabled: Boolean = true,
    capsuleSource: MenuMorphSource?,
) {
    val motion = TsuzukiTheme.motion
    val colors = TsuzukiTheme.colors
    var menuOpen by remember(action.label) { mutableStateOf(false) }
    val hasMenu = !action.menu.isNullOrEmpty()
    val interaction = remember(action.label) { MutableInteractionSource() }
    val buttonPressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    // A menu pours out of the whole capsule (sole capsule: the button IS the capsule); the capsule
    // carries the morph source, so its sibling icons ride into the liquid with the open menu.
    val morphSource = capsuleSource ?: rememberMenuMorphSource()
    Box(
        Modifier
            .size(TsuzukiSize.touch)
            .then(if (hasMenu) Modifier.menuScrubLauncher(morphSource) { menuOpen = true } else Modifier)
            .alpha(alpha)
            .then(if (motion.reduced || blurFraction <= 0.01f) Modifier else Modifier.blur(MorphBlurRadius * blurFraction)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(
                    when {
                        hasMenu -> Modifier
                        buttonPressState.enabled -> Modifier.pressShapeScale(buttonPressState)
                        else -> Modifier.pressScale(interaction)
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = action.enabled && enabled && !moving,
                    role = Role.Button,
                    onClickLabel = action.label,
                ) { if (hasMenu) menuOpen = true else action.onClick() }
                .semantics { contentDescription = action.label },
            contentAlignment = Alignment.Center,
        ) {
            if (action.content != null) {
                // A menu declared in the content (Library's) pours from this button.
                CompositionLocalProvider(LocalMenuMorphSource provides morphSource) { action.content() }
            } else {
                Icon(
                    action.icon,
                    null,
                    Modifier.size(22.dp),
                    tint = if (action.active) colors.accent else colors.text,
                )
            }
        }
        if (hasMenu) {
            PullDownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                actions = action.menu,
                paneTitle = action.menuTitle,
                morphSource = morphSource,
            )
        }
    }
}

private fun lerpValues(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private fun lerpDp(from: Dp, to: Dp, fraction: Float): Dp = Dp(from.value + (to.value - from.value) * fraction)

/** The declaration's scrolled-under progress: the fraction when provided, else the boolean. */
private fun ShellTopDeclaration.scrolledUnderNow(): Float =
    scrolledUnderFraction?.invoke() ?: if (scrolledUnder) 1f else 0f
