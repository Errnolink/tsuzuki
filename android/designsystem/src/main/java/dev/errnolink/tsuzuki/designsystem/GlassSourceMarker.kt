package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.traverseAncestors

private const val GlassSourceKey = "dev.errnolink.tsuzuki.glassSource"

/**
 * Marks a layout as a backdrop source of [context]. A Kasane glass surface inside its own source
 * would draw the backdrop layer into itself (a render-node cycle that crashes the render thread),
 * so surfaces probe their ancestors for this marker and keep Haze when they find it.
 */
internal fun Modifier.glassSourceMarker(context: GlassContext): Modifier = this then GlassSourceMarkerElement(context)

private data class GlassSourceMarkerElement(val context: GlassContext) : ModifierNodeElement<GlassSourceMarkerNode>() {
    override fun create() = GlassSourceMarkerNode(context)
    override fun update(node: GlassSourceMarkerNode) {
        node.context = context
    }
}

private class GlassSourceMarkerNode(var context: GlassContext) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any = GlassSourceKey
}

/** Reports once attached whether an ancestor is a backdrop source of [context]. */
internal fun Modifier.glassSourceProbe(context: GlassContext?, onResult: (Boolean) -> Unit): Modifier =
    this then GlassSourceProbeElement(context, onResult)

private data class GlassSourceProbeElement(
    val context: GlassContext?,
    val onResult: (Boolean) -> Unit,
) : ModifierNodeElement<GlassSourceProbeNode>() {
    override fun create() = GlassSourceProbeNode(context, onResult)
    override fun update(node: GlassSourceProbeNode) {
        node.context = context
        node.onResult = onResult
        node.report()
    }
}

private class GlassSourceProbeNode(
    var context: GlassContext?,
    var onResult: (Boolean) -> Unit,
) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any = "dev.errnolink.tsuzuki.glassSourceProbe"

    override fun onAttach() {
        report()
    }

    fun report() {
        if (!isAttached) return
        val target = context
        var inside = false
        if (target != null) {
            traverseAncestors(GlassSourceKey) { node ->
                if ((node as GlassSourceMarkerNode).context === target) {
                    inside = true
                    false
                } else {
                    true
                }
            }
        }
        onResult(inside)
    }
}
