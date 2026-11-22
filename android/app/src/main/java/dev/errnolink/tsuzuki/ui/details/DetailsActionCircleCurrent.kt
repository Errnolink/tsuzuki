package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import androidx.compose.foundation.interaction.MutableInteractionSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal val CircleSize = 44.dp

/** 44dp glass circle with a press "grow" pulse used when the tap opens a sheet/screen. */
@Composable
internal fun GrowPulseCircle(
    glass: GlassContext?,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    checked: Boolean = false,
    /** True only when the circle toggles a two-state action (Library, Tracking); Merge/Covers/Notes are buttons and must not read "Off". */
    isToggle: Boolean = false,
    pulseOnClick: Boolean = true,
    onPositioned: ((Offset) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val toggleOnLabel = stringResource(MR.strings.on)
    val toggleOffLabel = stringResource(MR.strings.off)
    val motion = TsuzukiTheme.motion
    var pulsing by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    val pulse by animateFloatAsState(
        // reduced motion: no scale pulse, the circle simply fires
        if (pulsing && pulseOnClick && !motion.reduced) 1.14f else 1f,
        androidx.compose.animation.core.tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3, easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard),
        label = "Row pulse",
    )
    LaunchedEffect(pulsing) {
        if (pulsing) {
            kotlinx.coroutines.delay(140)
            pulsing = false
        }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .onGloballyPositioned { onPositioned?.invoke(it.boundsInWindow().topLeft) }
            .size(CircleSize),
    ) {
        GlassSurface(
            glass,
            Modifier.fillMaxSize().then(
                if (pulseOnClick && pressState.enabled) {
                    Modifier.pressShapeScale(pressState)
                } else {
                    Modifier
                },
            ),
            shape = if (pulseOnClick && pressState.enabled) pressState.shape else CircleShape,
            material = GlassMaterial.Thick,
        ) {
            Box(
                Modifier.fillMaxSize()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (pulseOnClick) pulsing = true
                            onClick()
                        },
                    )
                    .then(
                        if (!pulseOnClick || pressState.enabled) {
                            Modifier
                        } else {
                            Modifier.pressScale(interaction)
                        },
                    )
                    .semantics {
                        contentDescription = label
                        if (isToggle) {
                            stateDescription = if (checked) toggleOnLabel else toggleOffLabel
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.size(20.dp),
                    tint = if (active) colors.accent else colors.text,
                )
            }
        }
        if (checked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Check, null, Modifier.size(10.dp), tint = colors.onAccent)
            }
        }
    }
}
