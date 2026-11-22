package dev.errnolink.tsuzuki.reader

import android.os.SystemClock
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
    private val fallbacks = mutableMapOf<Any, Rect>()
    private var activeOrigin: Rect? = null
    private var activeOriginAtMillis: Long = 0L

    internal const val ActiveOriginWindowMillis = 500L

    internal fun put(key: Any, rect: Rect) {
        bounds[key] = rect
    }

    internal fun remove(key: Any) {
        bounds.remove(key)
    }

    internal fun putFallback(key: Any, rect: Rect) {
        fallbacks[key] = rect
    }

    internal fun removeFallback(key: Any) {
        fallbacks.remove(key)
    }

    fun markActive(rect: Rect?, nowMillis: Long = SystemClock.uptimeMillis()) {
        if (rect == null || rect.isEmpty) return
        activeOrigin = rect
        activeOriginAtMillis = nowMillis
    }

    internal fun clearActive() {
        activeOrigin = null
        activeOriginAtMillis = 0L
    }

    fun pick(touch: Offset?, nowMillis: Long = SystemClock.uptimeMillis()): Rect? {
        val freshActive = activeOrigin?.takeIf {
            !it.isEmpty && (nowMillis - activeOriginAtMillis) in 0L..ActiveOriginWindowMillis
        }
        if (freshActive != null) {
            clearActive()
            return freshActive
        }
        if (touch == null) return null
        return pickFrom(bounds.values, touch) ?: fallbacks.values.lastOrNull { !it.isEmpty }
    }

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

@Composable
fun Modifier.readerFallbackOrigin(): Modifier {
    val key = remember { Any() }
    DisposableEffect(key) {
        onDispose { ReaderOrigins.removeFallback(key) }
    }
    return onGloballyPositioned { ReaderOrigins.putFallback(key, it.boundsInWindow()) }
}
