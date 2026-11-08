package dev.errnolink.tsuzuki.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

object ReaderOrigins {
    private val bounds = mutableMapOf<Any, Rect>()

    internal fun put(key: Any, rect: Rect) {
        bounds[key] = rect
    }

    internal fun remove(key: Any) {
        bounds.remove(key)
    }

    fun pick(touch: Offset?): Rect? = pickFrom(bounds.values, touch)

    internal fun pickFrom(candidates: Collection<Rect>, touch: Offset?): Rect? {
        if (touch == null) return null
        return candidates.filter { !it.isEmpty && it.contains(touch) }.minByOrNull { it.width * it.height }
    }
}

@Composable
fun Modifier.readerOrigin(): Modifier {
    val key = remember { Any() }
    DisposableEffect(key) {
        onDispose { ReaderOrigins.remove(key) }
    }
    return onGloballyPositioned { ReaderOrigins.put(key, it.boundsInWindow()) }
}
