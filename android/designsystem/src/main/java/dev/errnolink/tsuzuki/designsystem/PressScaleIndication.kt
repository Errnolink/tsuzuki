package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal data class PressScaleIndication(
    val motion: TsuzukiMotion,
    val cover: Boolean = false,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, motion, cover)
}

@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val motion = LocalTsuzukiMotion.current
    return indication(interactionSource, remember(motion) { PressScaleIndication(motion) })
}

private class PressNode(
    private val source: InteractionSource,
    private val motion: TsuzukiMotion,
    private val cover: Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            source.interactions.map { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                }
                presses.isNotEmpty()
            }.distinctUntilChanged().collectLatest { isPressed ->
                pressed = isPressed
                invalidateDraw()
                if (motion.reduced) {
                    scale.snapTo(1f)
                } else if (isPressed) {
                    scale.animateTo(
                        if (cover) motion.coverPressScale else motion.pressScale,
                        tween(if (cover) motion.coverPressMillis else motion.pressMillis),
                    )
                } else if (cover) {
                    scale.animateTo(1f, tween(motion.coverReleaseMillis))
                } else {
                    scale.animateTo(1f, motion.pressSpring())
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scale.value.coerceAtMost(1f)) {
            this@draw.drawContent()
            if (pressed && motion.reduced) drawRect(Color.Gray.copy(alpha = 0.16f))
        }
    }
}
