package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

class HeroFrame {
    var coordinates: LayoutCoordinates? = null
}

val LocalHeroFrame = staticCompositionLocalOf<HeroFrame?> { null }

object MangaHero {
    internal class Source(val mangaId: Long) {
        var bounds: Rect = Rect.Zero
        var image: ImageBitmap? = null
    }

    private val sourcesByManga = mutableMapOf<Long, MutableSet<Source>>()
    private val origins = mutableStateMapOf<Long, Rect>()
    private val bound = mutableMapOf<Long, Source>()
    private class Target(val owner: Any, val bounds: Rect)

    private val targets = mutableStateMapOf<Long, Target>()
    private var lastDown: Offset? = null

    var inFlight by mutableStateOf<Long?>(null)
        internal set

    fun pointerDown(position: Offset) {
        lastDown = position
    }

    fun claim(mangaId: Long): Boolean {
        val candidates = sourcesByManga[mangaId]?.filter { !it.bounds.isEmpty }.orEmpty()
        val touch = lastDown
        val pick = candidates.firstOrNull { touch != null && it.bounds.contains(touch) }
            ?: candidates.minByOrNull { source -> touch?.let { (source.bounds.center - it).getDistanceSquared() } ?: 0f }
        if (pick == null) {
            origins.remove(mangaId)
            bound.remove(mangaId)
            return false
        }
        origins[mangaId] = pick.bounds
        bound[mangaId] = pick
        return true
    }

    fun active(mangaId: Long): Boolean = mangaId in origins

    fun retain(mangaIds: Set<Long>) {
        origins.keys.retainAll(mangaIds)
        bound.keys.retainAll(mangaIds)
        targets.keys.retainAll(mangaIds)
    }

    internal fun source(mangaId: Long): Source? {
        val list = sourcesByManga[mangaId] ?: return null
        bound[mangaId]?.takeIf { it in list }?.let { return it }
        val origin = origins[mangaId] ?: return null
        val nearest = list
            .filter { !it.bounds.isEmpty }
            .minByOrNull { (it.bounds.center - origin.center).getDistanceSquared() }
            ?: return null
        bound[mangaId] = nearest
        return nearest
    }

    internal fun target(mangaId: Long): Rect? = targets[mangaId]?.bounds?.takeIf { it.center.y > 0f }

    internal fun register(source: Source) {
        sourcesByManga.getOrPut(source.mangaId) { mutableSetOf() } += source
    }

    internal fun unregister(source: Source) {
        sourcesByManga[source.mangaId]?.let { set ->
            set -= source
            if (set.isEmpty()) sourcesByManga.remove(source.mangaId)
        }
    }

    internal fun reportTarget(mangaId: Long, owner: Any, bounds: Rect) {
        val current = targets[mangaId]
        if (current == null || current.owner !== owner || current.bounds != bounds) targets[mangaId] = Target(owner, bounds)
    }

    internal fun clearTarget(mangaId: Long, owner: Any) {
        if (targets[mangaId]?.owner === owner) targets.remove(mangaId)
    }
}

object ScreenReadiness {
    private val ready = mutableStateSetOf<String>()

    fun mark(screenKey: String) {
        ready += screenKey
    }

    fun isReady(screenKey: String): Boolean = screenKey in ready

    fun retain(screenKeys: Set<String>) {
        ready.retainAll(screenKeys)
    }
}

@Composable
fun Modifier.mangaHeroSource(mangaId: Long, image: ImageBitmap?): Modifier {
    val source = remember(mangaId) { MangaHero.Source(mangaId) }
    source.image = image
    DisposableEffect(source) {
        MangaHero.register(source)
        onDispose { MangaHero.unregister(source) }
    }
    val positioned = Modifier.onGloballyPositioned { source.bounds = it.boundsInRoot() }
    if (!MangaHero.active(mangaId)) return then(positioned)
    return then(positioned).drawWithContent {
        val flying = MangaHero.inFlight == mangaId && source.image != null && MangaHero.source(mangaId) === source &&
            MangaHero.target(mangaId) != null
        if (!flying) drawContent()
    }
}

@Composable
fun Modifier.mangaHeroTarget(mangaId: Long): Modifier {
    val frame = LocalHeroFrame.current ?: return this
    val owner = remember(mangaId) { Any() }
    DisposableEffect(mangaId, owner) {
        onDispose { MangaHero.clearTarget(mangaId, owner) }
    }
    return onGloballyPositioned { coordinates ->
        val root = frame.coordinates
        if (root != null && root.isAttached && coordinates.isAttached) {
            MangaHero.reportTarget(mangaId, owner, root.localBoundingBoxOf(coordinates, clipBounds = false))
        }
    }.drawWithContent { if (MangaHero.inFlight != mangaId) drawContent() }
}
