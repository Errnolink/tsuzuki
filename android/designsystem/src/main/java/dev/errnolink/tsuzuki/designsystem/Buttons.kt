package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
) {
    val colors = TsuzukiTheme.colors
    val tint = colors.tabTint
    val foreground = when {
        destructive -> colors.destructive
        prominent && !tinted -> androidx.compose.ui.graphics.Color.White
        else -> tint
    }
    val filled = tinted || (prominent && !destructive)
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val minHeight = if (large) 52.dp else TsuzukiSpacing.touchTarget
    val verticalPadding = if (large) 13.dp else 11.dp
    val shapeMorph = LocalPressShape.current && !TsuzukiTheme.motion.reduced
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    var minDimPx by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }
    val squareness = remember { Animatable(1f) }
    LaunchedEffect(shapeMorph, pressed) {
        if (!shapeMorph) {
            scale.snapTo(1f)
            squareness.snapTo(1f)
            return@LaunchedEffect
        }
        val spec: AnimationSpec<Float> = if (pressed) {
            tween(durationMillis = 90)
        } else {
            spring(dampingRatio = 0.7f, stiffness = 700f)
        }
        val scaleTarget = if (pressed) 0.96f else 1f
        val squareTarget = if (pressed) 0f else 1f
        coroutineScope {
            launch { scale.animateTo(scaleTarget, spec) }
            launch { squareness.animateTo(squareTarget, spec) }
        }
    }
    val capsuleRadius = with(density) { (minDimPx / 2f).toDp() }
    val shape = if (shapeMorph && minDimPx > 0) {
        RoundedCornerShape(16.dp + (capsuleRadius - 16.dp) * squareness.value)
    } else {
        CircleShape
    }
    val press = if (shapeMorph) {
        Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onSizeChanged { minDimPx = minOf(it.width, it.height) }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    }
    val content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        Box(
            Modifier.align(Alignment.Center)
                .alpha(if (enabled) 1f else 0.4f)
                .defaultMinSize(minWidth = 88.dp, minHeight = minHeight)
                .padding(horizontal = 20.dp, vertical = verticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiText(label, style = TsuzukiTheme.typography.headline, color = foreground)
        }
    }
    if (filled) {
        val fill = if (tinted) tint.copy(alpha = if (colors.isDark) 0.22f else 0.14f) else tint
        Box(
            modifier.then(press).clip(shape)
                .background(fill),
            content = content,
        )
    } else {
        GlassSurface(context = null, modifier = modifier.then(press), shape = shape, content = content)
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
    GlassSurface(context, modifier, hero = hero) {
        Row(
            Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
    GlassSurface(context, modifier, shape = CircleShape) {
        Box(
            Modifier.sizeIn(minWidth = TsuzukiSpacing.touchTarget, minHeight = TsuzukiSpacing.touchTarget)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
