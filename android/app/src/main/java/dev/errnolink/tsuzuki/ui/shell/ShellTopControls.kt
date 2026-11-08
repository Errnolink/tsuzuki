package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TopBarSearchField
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar
import dev.errnolink.tsuzuki.designsystem.pressScale

private val MorphBlurRadius = 10.dp
private const val SettledSwapMillis = 180
private const val IconOutEnd = 0.6f
private const val IconInStart = 0.25f
private const val IconBlurShare = 0.5f
private val BandFade = 44.dp
private val BandUnderBar = 0.96f to 0.92f
private val BandTail = listOf(
    0.25f to 0.72f,
    0.45f to 0.5f,
    0.62f to 0.3f,
    0.78f to 0.14f,
    0.9f to 0.05f,
    1f to 0f,
)
private const val MenuAnchorPressScale = 1.15f
private const val MenuAnchorPressStiffness = 600f
private const val MenuAnchorPressDamping = 0.55f

class ShellTopAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val menuTitle: String? = null,
    val menu: List<PullDownAction>? = null,
    val content: (@Composable () -> Unit)? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ShellTopAction &&
            other.label == label &&
            other.icon == icon &&
            other.active == active &&
            other.enabled == enabled &&
            other.menuTitle == menuTitle &&
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
    var tint: Color? by mutableStateOf(null)
}

val LocalShellTopOwner = staticCompositionLocalOf<String?> { null }

object ShellTopControls {
    val declarations = mutableStateMapOf<String, ShellTopDeclaration>()
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
    tint: Color? = null,
) {
    val owner = LocalShellTopOwner.current ?: return
    val active = LocalShellTopActive.current
    val declaration = remember(owner) { ShellTopDeclaration() }
    val currentTitle by rememberUpdatedState(title)
    val currentGlass by rememberUpdatedState(glass)
    val currentBack = remember(navigateUp, backLabel, backIcon) {
        navigateUp?.let {
            ShellTopAction(
                label = backLabel,
                icon = backIcon,
                onClick = it,
            )
        }
    }
    val currentActions by rememberUpdatedState(actions)
    val currentSearch by rememberUpdatedState(search)
    val currentTitleClick by rememberUpdatedState(onTitleClick)
    val currentInline by rememberUpdatedState(inlineTitle)
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
        declaration.tint = tint
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
) {
    override fun equals(other: Any?): Boolean =
        other is ShellTopSnap &&
            other.title == title &&
            other.back == back &&
            other.actions == actions &&
            other.glass === glass

    override fun hashCode(): Int = title.hashCode()
}

@Composable
internal fun ShellTopControlRow(
    frontKey: String?,
    backKey: String?,
    moving: Boolean,
    progress: () -> Float,
    frontTint: Color? = null,
) {
    val front = frontKey?.let { ShellTopControls.declarations[it] }
    val back = backKey?.let { ShellTopControls.declarations[it] }
    val motion = TsuzukiTheme.motion
    val density = LocalDensity.current

    var shown by remember { mutableStateOf(ShellTopSnap(front)) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(moving) {
        if (!moving) {
            swap.snapTo(1f)
            shown = ShellTopSnap(front)
        }
    }
    LaunchedEffect(front?.title, front?.back, front?.actions, front?.glass, moving) {
        val target = ShellTopSnap(front)
        if (moving || shown == target) return@LaunchedEffect
        if (motion.reduced) {
            shown = target
        } else {
            try {
                swap.snapTo(0f)
                swap.animateTo(1f, tween(SettledSwapMillis, easing = M3Motion.Emphasized))
            } finally {
                withContext(NonCancellable) {
                    swap.snapTo(1f)
                    shown = target
                }
            }
        }
    }

    val to = ShellTopSnap(front)
    val from: ShellTopSnap?
    val morph: Float
    if (moving) {
        from = ShellTopSnap(back)
        morph = progress().coerceIn(0f, 1f)
    } else {
        from = shown.takeIf { it != to }
        morph = if (motion.reduced || from == null) 1f else swap.value
    }

    val search = front?.search
    val searchExpanded = !moving && search?.state?.expanded == true
    val searchFraction by animateFloatAsState(
        targetValue = if (searchExpanded) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else motion.tabSpring(),
        label = "Shell top search",
    )

    val visible = from != null || to.declaration != null
    val baseColors = TsuzukiTheme.colors
    val fromTint = from?.declaration?.tint ?: (if (to.declaration == null) frontTint else to.declaration.tint)?.takeIf { from == null }
    val toTint = if (to.declaration == null) frontTint else to.declaration.tint
    val colors = dev.errnolink.tsuzuki.designsystem.lerpColors(
        fromTint?.let { dev.errnolink.tsuzuki.designsystem.tsuzukiCardColors(it) } ?: baseColors,
        toTint?.let { dev.errnolink.tsuzuki.designsystem.tsuzukiCardColors(it) } ?: baseColors,
        morph,
    )
    val fontScale = density.fontScale
    val bodyHeight = (56.dp * fontScale).coerceAtLeast(56.dp)
    val edgeBrush = remember(colors.background) {
        Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.96f), Color.Transparent))
    }
    val edgeAlpha = lerpValues(
        if (from?.declaration?.edge != false) 1f else 0f,
        if (to.declaration?.edge != false) 1f else 0f,
        morph,
    ).coerceIn(0f, 1f)
    val inlineTarget = lerpValues(from?.declaration?.inlineTitle ?: 0f, to.declaration?.inlineTitle ?: 0f, morph)
        .coerceIn(0f, 1f) * (1f - searchFraction)
    val band by animateFloatAsState(
        targetValue = inlineTarget,
        animationSpec = if (motion.reduced) snap() else tween(M3Motion.Short4, easing = M3Motion.Standard),
        label = "Shell top band",
    )
    val bandFadePx = with(density) { BandFade.toPx() }

    var leadingWidth by remember { mutableStateOf(0.dp) }
    var trailingWidth by remember { mutableStateOf(0.dp) }
    androidx.compose.runtime.CompositionLocalProvider(
        dev.errnolink.tsuzuki.designsystem.LocalTsuzukiColors provides colors,
        dev.errnolink.tsuzuki.designsystem.LocalTsuzukiContentColor provides colors.text,
    ) {
    Box(Modifier.fillMaxWidth()) {
        if (visible) {
            Spacer(
                Modifier.matchParentSize().drawWithCache {
                    val total = size.height + bandFadePx
                    val bar = (size.height / total).coerceIn(0f, 1f)
                    val bandBrush = Brush.verticalGradient(
                        *(
                            listOf(0f to BandUnderBar.first, bar to BandUnderBar.second) +
                                BandTail.map { (at, alpha) -> (bar + (1f - bar) * at) to alpha }
                            ).map { (at, alpha) -> at to colors.background.copy(alpha = alpha) }.toTypedArray(),
                        startY = 0f,
                        endY = total,
                    )
                    onDrawBehind {
                        if (band < 1f && edgeAlpha > 0f) drawRect(brush = edgeBrush, alpha = (1f - band) * edgeAlpha)
                        if (band > 0f) {
                            drawRect(
                                brush = bandBrush,
                                size = androidx.compose.ui.geometry.Size(size.width, total),
                                alpha = band,
                            )
                        }
                    }
                },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(min = bodyHeight)
                .onSizeChanged { size ->
                    with(density) { TsuzukiTopBar.bodyHeightDp = size.height.toDp().value }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.onSizeChanged { leadingWidth = with(density) { it.width.toDp() } }) {
                BackCircleSlot(from, to, morph, moving)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (search != null && searchFraction > 0.01f && to.declaration != null) {
                    SearchPill(to, search, searchFraction, maxWidth)
                }
            }
            Box(Modifier.onSizeChanged { trailingWidth = with(density) { it.width.toDp() } }) {
                ActionsSlot(from, to, morph, searchFraction, moving)
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp + maxOf(leadingWidth, trailingWidth), vertical = 4.dp)
                .heightIn(min = bodyHeight),
            contentAlignment = Alignment.Center,
        ) {
            TitleSlot(from, to, morph, searchFraction)
        }
    }
    }
}

@Composable
private fun BackCircleSlot(from: ShellTopSnap?, to: ShellTopSnap?, morph: Float, moving: Boolean) {
    val toAlpha = if (to?.back != null) 1f else 0f
    val fromAlpha = when {
        from == null -> toAlpha
        from.back != null -> 1f
        else -> 0f
    }
    val alpha = lerpValues(fromAlpha, toAlpha, morph).coerceIn(0f, 1f)
    val source = if (morph < 0.5f) from ?: to else to ?: from
    val action = source?.back
    if (alpha <= 0.01f) {
        Spacer(Modifier.size(0.dp))
        return
    }
    Box(Modifier.alpha(alpha)) {
        GlassSurface(source?.glass, Modifier.size(48.dp), material = GlassMaterial.Thick) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .matchParentSize()
                    .pressScale(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !moving && action != null,
                        role = Role.Button,
                        onClickLabel = action?.label,
                    ) { action?.onClick() }
                    .semantics { contentDescription = action?.label ?: "" },
                contentAlignment = Alignment.Center,
            ) {
                action?.let {
                    Icon(it.icon, null, Modifier.size(22.dp), tint = TsuzukiTheme.colors.text)
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
    val motion = TsuzukiTheme.motion
    val outFraction = M3Motion.fadeThroughOut(morph)
    val inFraction = M3Motion.fadeThroughIn(morph)
    val fromInline = from?.declaration?.inlineTitle ?: 0f
    val toInline = to?.declaration?.inlineTitle ?: 0f
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        if (from == to) {
            if (to != null && to.title.isNotEmpty()) {
                TitleText(to.title, toInline * (1f - searchFraction), motion.reduced, to.onTitleClick)
            }
        } else {
            if (from != null && outFraction > 0.01f && from.title.isNotEmpty()) {
                TitleText(from.title, outFraction * fromInline * (1f - searchFraction), motion.reduced, from.onTitleClick)
            }
            if (to != null && inFraction > 0.01f && to.title.isNotEmpty()) {
                TitleText(to.title, inFraction * toInline * (1f - searchFraction), motion.reduced, to.onTitleClick)
            }
        }
    }
}

@Composable
private fun TitleText(title: String, alpha: Float, reduced: Boolean, onTitleClick: (() -> Unit)?) {
    TsuzukiText(
        title,
        Modifier
            .alpha(alpha.coerceIn(0f, 1f))
            .then(if (reduced) Modifier else Modifier.blur(MorphBlurRadius * (1f - alpha.coerceIn(0f, 1f))))
            .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier),
        TsuzukiTheme.typography.headline,
        color = TsuzukiTheme.colors.text,
        maxLines = 1,
    )
}

@Composable
private fun SearchPill(snap: ShellTopSnap, search: TopBarSearch, fraction: Float, available: Dp) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val headline = TsuzukiTheme.typography.headline
    val hugWidth = remember(snap.title, headline, density.fontScale) {
        with(density) { textMeasurer.measure(snap.title, headline).size.width.toDp() } + 32.dp
    }
    val grow = (available - hugWidth).coerceAtLeast(0.dp)
    GlassSurface(
        snap.glass,
        Modifier.width(hugWidth + grow * fraction).height(48.dp).graphicsLayer { alpha = fraction },
        material = GlassMaterial.Thick,
    ) {
        Box(Modifier.matchParentSize()) {
            TopBarSearchField(search, Modifier.matchParentSize())
        }
    }
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
    val source = if (morph < 0.5f) from ?: to else to ?: from
    val count = maxOf(fromActions.size, toActions.size)
    GlassSurface(
        source?.glass,
        Modifier.width(width).height(48.dp).alpha(alpha),
        material = GlassMaterial.Thick,
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { index ->
                    ActionSlot(fromActions.getOrNull(index), toActions.getOrNull(index), morph, moving)
                }
            }
        }
    }
}

private fun capsuleWidth(actionCount: Int): Dp = if (actionCount <= 0) 0.dp else (actionCount * 48 + 8).dp

@Composable
private fun ActionSlot(from: ShellTopAction?, to: ShellTopAction?, morph: Float, moving: Boolean) {
    if (from == to || (from != null && to != null && from.label == to.label && from.icon == to.icon)) {
        to?.let { RowTopButton(it, 1f, 0f, moving) }
        return
    }
    val outFraction = (1f - morph / IconOutEnd).coerceIn(0f, 1f)
    val inFraction = ((morph - IconInStart) / (1f - IconInStart)).coerceIn(0f, 1f)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (from != null && outFraction > 0.01f) {
            RowTopButton(from, outFraction, (1f - outFraction) * IconBlurShare, moving)
        }
        if (to != null && inFraction > 0.01f) {
            RowTopButton(to, inFraction, (1f - inFraction) * IconBlurShare, moving)
        }
    }
}

@Composable
private fun RowTopButton(action: ShellTopAction, alpha: Float, blurFraction: Float, moving: Boolean) {
    val motion = TsuzukiTheme.motion
    val colors = TsuzukiTheme.colors
    var menuOpen by remember(action.label) { mutableStateOf(false) }
    val hasMenu = !action.menu.isNullOrEmpty()
    val interaction = remember(action.label) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val anchorScale by animateFloatAsState(
        targetValue = if (hasMenu && pressed && !motion.reduced) MenuAnchorPressScale else 1f,
        animationSpec = spring(stiffness = MenuAnchorPressStiffness, dampingRatio = MenuAnchorPressDamping),
        label = "Menu anchor pop",
    )
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = anchorScale
                scaleY = anchorScale
            }
            .alpha(alpha)
            .then(if (motion.reduced || blurFraction <= 0.01f) Modifier else Modifier.blur(MorphBlurRadius * blurFraction)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(if (hasMenu) Modifier else Modifier.pressScale(interaction))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = action.enabled && !moving,
                    role = Role.Button,
                    onClickLabel = action.label,
                ) { if (hasMenu) menuOpen = true else action.onClick() }
                .semantics { contentDescription = action.label },
            contentAlignment = Alignment.Center,
        ) {
            if (hasMenu && pressed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                )
            }
            if (action.content != null) {
                action.content()
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
            )
        }
    }
}

private fun lerpValues(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private fun lerpDp(from: Dp, to: Dp, fraction: Float): Dp = Dp(from.value + (to.value - from.value) * fraction)
