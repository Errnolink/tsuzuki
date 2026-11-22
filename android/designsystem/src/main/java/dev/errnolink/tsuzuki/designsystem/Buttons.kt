package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Minimum 48 dp hit area without growing the painted control: the node expands to the
 * touch-target minimum while a smaller inner visual keeps its size, centred (the MenuButton
 * pattern). Apply it outside the clickable, with the painted box inside. Compose already
 * ring-pads stray taps around small targets to the same 48 dp (ViewConfiguration's
 * minimumTouchTargetSize, honoured by hit testing); this guarantees the target where that
 * ring would lose to a surrounding interactive parent, and gives TalkBack a 48 dp box.
 */
fun Modifier.minimumTouchTarget(): Modifier = minimumInteractiveComponentSize()

/**
 * Kasane button (`components/button.css`, `loader.css`, `web/docs-data.js` ids "button" and
 * "loading-button"): the M3E size ladder with a capsule or square rest shape, corners that tighten
 * while held (the body gives 2.5 %, never past the squeeze), toggles that flip round to square on
 * the fast spatial spring, and a loading state that holds its width — label, spinner, then a drawn
 * check; a failure shakes and returns to the label.
 */

/** M3E ladder: heights 32 · 40 · 56 · 96 · 136, paddings 12 · 16 · 24 · 48 · 64, icons 20 · 20 · 24 · 32 · 40. */
enum class TsuzukiButtonSize(
    val height: Dp,
    val padH: Dp,
    val gap: Dp,
    val icon: Dp,
    /** Rounded-square rest radius (`--r-square`). */
    val squareRadius: Dp,
    /** Corner radius while held (`--r-press`). */
    val pressedRadius: Dp,
    /** Body squeeze while held (`--k-squeeze`): scale never goes past this. */
    val squeeze: Float,
) {
    Xs(32.dp, 12.dp, 4.dp, 20.dp, 12.dp, 8.dp, 0.975f),
    Sm(40.dp, 16.dp, 8.dp, 20.dp, 12.dp, 8.dp, 0.975f),
    Md(56.dp, 24.dp, 8.dp, 24.dp, 16.dp, 12.dp, 0.975f),
    Lg(96.dp, 48.dp, 12.dp, 32.dp, 28.dp, 16.dp, 0.985f),
    Xl(136.dp, 64.dp, 16.dp, 40.dp, 28.dp, 16.dp, 0.99f),
}

enum class TsuzukiButtonVariant {
    /** The Seal: one filled accent button per view. */
    Filled,

    /** Translucent accent with accent content. */
    Tonal,

    /** Tsuzuki's quiet action: text-tint fill with accent content (the classic PillButton look). */
    Soft,

    /** Quiet surface-high fill with plain content; the toggle-off state. */
    Container,

    /** Transparent with an outline. */
    Outline,

    /** Transparent, accent content. */
    Text,

    /** Destructive tint. */
    Danger,
}

/** Fill and content colour of a variant (Kasane variant roles mapped onto the Tsuzuki palette). */
fun buttonColors(variant: TsuzukiButtonVariant, colors: TsuzukiColors): Pair<Color, Color> = when (variant) {
    TsuzukiButtonVariant.Filled -> colors.accent to colors.onAccent
    TsuzukiButtonVariant.Tonal -> colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.14f) to colors.accent
    TsuzukiButtonVariant.Soft -> colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f) to colors.accent
    TsuzukiButtonVariant.Container -> colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f) to colors.text
    TsuzukiButtonVariant.Outline -> Color.Transparent to colors.text
    TsuzukiButtonVariant.Text -> Color.Transparent to colors.accent
    TsuzukiButtonVariant.Danger ->
        colors.destructive.copy(alpha = if (colors.isDark) 0.16f else 0.12f) to colors.destructive
}

internal fun pillButtonColors(
    prominent: Boolean,
    tinted: Boolean,
    destructive: Boolean,
    colors: TsuzukiColors,
): Pair<Color, Color> = buttonColors(
    when {
        destructive -> TsuzukiButtonVariant.Danger
        tinted -> TsuzukiButtonVariant.Tonal
        prominent -> TsuzukiButtonVariant.Filled
        else -> TsuzukiButtonVariant.Soft
    },
    colors,
)

/** Rest radius: capsule (height / 2) unless square; a selected toggle flips the shape (round ↔ square). */
internal fun buttonRestRadius(size: TsuzukiButtonSize, square: Boolean, selected: Boolean): Dp = when {
    square && selected -> size.height / 2
    square -> size.squareRadius
    selected -> size.squareRadius
    else -> size.height / 2
}

/** Corner radius under a press fraction: pressed wins over selected; the fraction may overshoot on release. */
internal fun buttonCornerRadius(size: TsuzukiButtonSize, square: Boolean, selected: Boolean, pressFraction: Float): Dp {
    val rest = buttonRestRadius(size, square, selected)
    return rest + (size.pressedRadius - rest) * pressFraction
}

/** Body squeeze: clamped, so a springy release can never scale past the squeeze. */
internal fun buttonPressScale(size: TsuzukiButtonSize, pressFraction: Float): Float =
    1f + (size.squeeze - 1f) * pressFraction.coerceIn(0f, 1f)

internal fun buttonTextStyle(size: TsuzukiButtonSize, typography: TsuzukiTypography): TextStyle = when (size) {
    TsuzukiButtonSize.Xs -> typography.footnote.copy(fontWeight = FontWeight.SemiBold)
    TsuzukiButtonSize.Sm -> typography.subhead.copy(fontWeight = FontWeight.SemiBold)
    TsuzukiButtonSize.Md -> typography.headline
    TsuzukiButtonSize.Lg -> typography.title2.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    TsuzukiButtonSize.Xl -> typography.title1.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
}

enum class LoadingButtonResult { None, Success, Failure }

internal enum class LoadingButtonPhase { Label, Spinner, Check }

/** What the button body shows: the label, the spinner while busy, the check once it succeeded. */
internal fun loadingButtonPhase(busy: Boolean, result: LoadingButtonResult): LoadingButtonPhase = when {
    busy -> LoadingButtonPhase.Spinner
    result == LoadingButtonResult.Success -> LoadingButtonPhase.Check
    else -> LoadingButtonPhase.Label
}

/** Horizontal shake for a failed action: three decaying swings, quiet at both ends. */
internal fun failureShake(fraction: Float): Float {
    if (fraction <= 0f || fraction >= 1f) return 0f
    return sin(fraction * 2f * PI.toFloat() * 3f) * (1f - fraction)
}

@Composable
fun TsuzukiButton(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: TsuzukiButtonSize = TsuzukiButtonSize.Sm,
    variant: TsuzukiButtonVariant = TsuzukiButtonVariant.Filled,
    /** Rounded-square rest shape; a selected toggle rounds out instead. */
    square: Boolean = false,
    /** Non-null turns the button into a toggle the caller owns. */
    selected: Boolean? = null,
    icon: (@Composable () -> Unit)? = null,
    busy: Boolean = false,
    result: LoadingButtonResult = LoadingButtonResult.None,
    minWidth: Dp = 88.dp,
    interactionSource: MutableInteractionSource? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    fillWidth: Boolean = false,
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    // press morph: corners tighten on effects while held, spring back on the fast spatial spring
    val press by rememberPressFraction(
        interaction,
        pressIn = tween(PressInMs.toInt(), easing = M3Motion.Standard),
        release = KasaneSprings.fastSpatial(reduced),
    )
    val toggled = selected == true
    val isToggle = selected != null
    val phase = loadingButtonPhase(busy, result)
    val succeeded = phase == LoadingButtonPhase.Check

    // toggle flip rides the fast spatial spring (round ↔ square, ~9 % overshoot)
    val flip by animateFloatAsState(if (toggled) 1f else 0f, KasaneSprings.fastSpatial(reduced), label = "ButtonFlip")
    val restRadius = lerp(buttonRestRadius(size, square, false), buttonRestRadius(size, square, true), flip)
    val corner = restRadius + (size.pressedRadius - restRadius) * press
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val scale = buttonPressScale(size, press)

    val baseColors = if (isToggle) {
        if (toggled) buttonColors(TsuzukiButtonVariant.Filled, colors)
        else buttonColors(TsuzukiButtonVariant.Container, colors)
    } else {
        buttonColors(variant, colors)
    }
    val fill = if (succeeded) colors.success else baseColors.first
    val animatedFill by animateColorAsStateCompat(fill)
    val contentColor = when {
        succeeded -> if (colors.success.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White
        else -> baseColors.second
    }

    // failure shake: three decaying swings, then the label returns
    val shake = remember { Animatable(0f) }
    LaunchedEffect(result) {
        if (result == LoadingButtonResult.Failure && !reduced) {
            shake.animateTo(1f, tween(M3Motion.Medium4, easing = M3Motion.Standard))
            shake.snapTo(0f)
        } else {
            shake.snapTo(0f)
        }
    }

    val bodyAlpha by animateFloatAsState(
        if (phase == LoadingButtonPhase.Label) 1f else 0f,
        tween(M3Motion.Short3, easing = M3Motion.Standard),
        label = "ButtonBodyAlpha",
    )
    val chromeAlpha by animateFloatAsState(
        if (phase == LoadingButtonPhase.Label) 0f else 1f,
        tween(M3Motion.Short3, easing = M3Motion.Standard),
        label = "ButtonChromeAlpha",
    )

    val clickModifier = when {
        isToggle -> Modifier.toggleable(
            value = toggled,
            enabled = enabled && !busy,
            role = Role.Checkbox,
            onValueChange = { onClick() },
            interactionSource = interaction,
            indication = null,
        )
        else -> Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled && !busy,
            role = Role.Button,
            onClick = onClick,
        )
    }
    val labels = LocalTsuzukiComponentLabels.current
    Box(
        modifier
            .then(if (size <= TsuzukiButtonSize.Sm) Modifier.minimumTouchTarget() else Modifier)
            .graphicsLayer {
                translationX = failureShake(shake.value) * 6.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (busy) Modifier.semantics { contentDescription = labels.loading } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Xs/Sm ride in a 48 dp hit box; the painted capsule keeps the ladder height
        Box(
            Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(size.height)
                .clip(shape)
                .background(animatedFill, shape)
                .then(
                    if (variant == TsuzukiButtonVariant.Outline && !isToggle && !succeeded) {
                        Modifier.border(1.dp, colors.separator, shape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // the label stays in the layout while busy so the width holds; an icon leading the row
            // tightens the start edge (Kasane .k-btn:has(> svg:first-child))
            Row(
                Modifier
                    .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = bodyAlpha }
                    .defaultMinSize(minWidth = minWidth)
                    .padding(
                        PaddingValues(
                            start = if (icon != null) size.padH - 4.dp else size.padH,
                            end = if (trailingIcon != null) size.padH - 4.dp else size.padH,
                        ),
                    ),
                horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke()
                if (label != null) {
                    TsuzukiText(
                        label,
                        modifier = if (fillWidth) Modifier.weight(1f, fill = false) else Modifier,
                        style = buttonTextStyle(size, TsuzukiTheme.typography),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailingIcon?.invoke()
            }
            if (phase != LoadingButtonPhase.Label) {
                Box(
                    Modifier.matchParentSize().alpha(chromeAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    when (phase) {
                        LoadingButtonPhase.Spinner ->
                            LoadingIndicator(Modifier.size(size.icon + 2.dp), color = contentColor)
                        LoadingButtonPhase.Check ->
                            DrawnCheck(1f, Modifier.size(size.icon), color = contentColor)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = true,
    destructive: Boolean = false,
    tinted: Boolean = false,
    large: Boolean = false,
    minWidth: Dp = 88.dp,
) {
    TsuzukiButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = if (large) TsuzukiButtonSize.Md else TsuzukiButtonSize.Sm,
        variant = when {
            destructive -> TsuzukiButtonVariant.Danger
            tinted -> TsuzukiButtonVariant.Tonal
            prominent -> TsuzukiButtonVariant.Filled
            else -> TsuzukiButtonVariant.Soft
        },
        minWidth = minWidth,
    )
}

/**
 * Glass buttons swell under the finger (iOS 26): the capsule grows and its rim catches more light,
 * with no corner morph. [swell] is 1.07 for text glass buttons, 1.10 for icon glass buttons.
 */
@Composable
private fun Modifier.glassSwell(interactionSource: MutableInteractionSource, swell: Float): Modifier {
    val reduced = TsuzukiTheme.motion.reduced
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) swell else 1f,
        if (reduced) snap() else KasaneSprings.fastSpatial(reduced),
        label = "GlassSwell",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GlassButton(
    context: GlassContext?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hero: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(context, modifier.glassSwell(interaction, 1.07f), shape = RoundedCornerShape(TsuzukiCorners.capsulePercent), hero = hero) {
        Row(
            Modifier
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .alpha(if (enabled) 1f else 0.45f)
                .defaultMinSize(minHeight = TsuzukiSpacing.touchTarget)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun IconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    context: GlassContext? = null,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(context, modifier.glassSwell(interaction, 1.10f), shape = CircleShape) {
        Box(
            Modifier.sizeIn(minWidth = TsuzukiSpacing.touchTarget, minHeight = TsuzukiSpacing.touchTarget)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) { contentDescription = label }
                .alpha(if (enabled) 1f else 0.45f)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalTsuzukiContentColor provides colors.text,
                LocalContentColor provides colors.text,
                content = icon,
            )
        }
    }
}

@Composable
fun PlainIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(TsuzukiSpacing.touchTarget)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTsuzukiContentColor provides colors.secondary,
            LocalContentColor provides colors.secondary,
            content = icon,
        )
    }
}

@Composable
fun MenuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    val shape = if (state.enabled) state.shape else CircleShape
    val press = if (state.enabled) Modifier.pressShapeScale(state) else Modifier.pressScale(interaction)
    Box(
        modifier
            .then(press)
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.DropdownList,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 38.dp)
                .clip(shape)
                .background(colors.text.copy(alpha = if (colors.isDark) 0.08f else 0.05f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke(this)
            TsuzukiText(
                label,
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
            )
            TsuzukiIcon(TsuzukiIcons.ChevronsUpDown, null, Modifier.size(14.dp), tint = colors.secondary)
        }
    }
}
