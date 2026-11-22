package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/* Mode switch morph (iOS 26 Edit → Select, reference notes): entering selection, the control's glass
   gives way to a filled tinted circle with a check and the label blur-crossfades; leaving reverses
   it. About 200 ms, critically damped, no overshoot (effects never overshoot). */

/** How far the control swells in from when it has no resting content beside the header swap. */
private const val CheckInFromScale = 0.5f

/** Blur radius of the label ↔ check blur-crossfade. */
private val CheckMorphBlur = 10.dp

/**
 * The selection-mode Done control: at rest a glass circle showing [restIcon], in selection mode a
 * filled accent-tinted circle with a check. [active] follows the page's selection mode; the fill,
 * the blur-crossfade and the reverse all ride one spring. Tapping it (while active) leaves
 * selection via [onDone].
 */
@Composable
fun SelectionCheckMorph(
    active: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    glass: GlassContext? = null,
    restIcon: ImageVector? = null,
    description: String? = null,
) {
    val motion = TsuzukiTheme.motion
    val colors = TsuzukiTheme.colors
    val resolvedDescription = description ?: LocalTsuzukiComponentLabels.current.done
    val fill = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active, motion.reduced) {
        if (motion.reduced) {
            fill.snapTo(if (active) 1f else 0f)
        } else {
            fill.animateTo(if (active) 1f else 0f, KasaneSprings.selectionCheck<Float>())
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    Box(
        modifier
            .size(TsuzukiSize.touch)
            .graphicsLayer {
                val f = fill.value
                alpha = f
                val s = CheckInFromScale + (1f - CheckInFromScale) * f
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        // The resting glass circle gives way to the tinted fill; the icons blur-crossfade over it.
        GlassSurface(
            context = glass,
            shape = CircleShape,
            material = GlassMaterial.Thick,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - fill.value },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (restIcon != null) {
                    val rest = 1f - fill.value
                    Icon(
                        restIcon,
                        null,
                        Modifier
                            .size(22.dp)
                            .alpha(rest)
                            .then(
                                if (motion.reduced || rest >= 0.99f) Modifier else Modifier.blur(CheckMorphBlur * (1f - rest)),
                            ),
                        tint = colors.text,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pressState.enabled) Modifier.pressShapeScale(pressState) else Modifier)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = active,
                    role = Role.Button,
                    onClick = onDone,
                )
                .semantics { contentDescription = resolvedDescription },
            contentAlignment = Alignment.Center,
        ) {
            val check = fill.value
            Icon(
                TsuzukiIcons.Check,
                null,
                Modifier
                    .size(22.dp)
                    .alpha(check)
                    .then(
                        if (motion.reduced || check >= 0.99f) Modifier else Modifier.blur(CheckMorphBlur * (1f - check)),
                    ),
                tint = colors.accent,
            )
        }
    }
}
