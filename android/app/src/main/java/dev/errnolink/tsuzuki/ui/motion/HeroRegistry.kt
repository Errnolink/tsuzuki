package dev.errnolink.tsuzuki.ui.motion

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import dev.errnolink.tsuzuki.designsystem.LocalCoverShape
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

class HeroFrame {
    var coordinates: LayoutCoordinates? = null
}

val LocalHeroFrame = staticCompositionLocalOf<HeroFrame?> { null }

/** A title's zoom while it moves: its progress (0 at the tile, 1 at rest) and whether it is closing. */
class HeroFlight(val mangaId: Long, val progress: () -> Float, val closing: () -> Boolean)

object MangaHero {
    internal class Source(val mangaId: Long) {
        var bounds: Rect = Rect.Zero
        var image: ImageBitmap? = null
        var tapSeed: HeroTapSeed? = null
    }

    private val sourcesByManga = mutableMapOf<Long, MutableSet<Source>>()
    private val origins = mutableStateMapOf<Long, Rect>()
    private val bound = mutableMapOf<Long, Source>()
    private class Target(val owner: Any, val bounds: Rect)

    private val targets = mutableStateMapOf<Long, Target>()
    private val tapSeeds = mutableStateMapOf<Long, HeroTapSeed>()
    private var lastDown: Offset? = null
    private var lastDownAt = 0L

    var inFlight by mutableStateOf<Long?>(null)
        internal set

    /** The zoom while it moves (open, close or a held gesture); null once it has settled. */
    var flight by mutableStateOf<HeroFlight?>(null)
        internal set

    private val cardColors = mutableStateMapOf<Long, Color>()
    private val cardColorJobs = mutableSetOf<Long>()
    private val cardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    fun pointerDown(position: Offset) {
        lastDown = position
        lastDownAt = android.os.SystemClock.uptimeMillis()
        sourcesByManga.values.firstNotNullOfOrNull { set -> set.firstOrNull { it.bounds.contains(position) } }
            ?.let { prepareCardColor(it.mangaId, it.image) }
    }

    var cardColorsEnabled by mutableStateOf(false)

    fun cardColor(mangaId: Long): Color? = if (cardColorsEnabled) cardColors[mangaId] else null

    fun peekCardColor(mangaId: Long): Color? = cardColors[mangaId]

    fun requestCardColor(mangaId: Long) {
        if (mangaId in cardColors) return
        val image = sourcesByManga[mangaId]?.firstOrNull()?.image ?: return
        prepareCardColor(mangaId, image)
    }

    fun onScreenCovers(maxCount: Int): List<Triple<Long, Rect, ImageBitmap>> =
        sourcesByManga.values
            .flatMap { set -> set.filter { source -> source.image != null && !source.bounds.isEmpty } }
            .filter { it.bounds.bottom >= 0f }
            .sortedBy { it.bounds.top }
            .take(maxCount)
            .map { Triple(it.mangaId, it.bounds, it.image!!) }

    fun reportCardColor(mangaId: Long, color: Color) {
        if (cardColors[mangaId] != color) cardColors[mangaId] = color
    }

    fun prepareCardColor(mangaId: Long, image: ImageBitmap?) {
        if (mangaId in cardColors || image == null || !cardColorJobs.add(mangaId)) return
        cardScope.launch {
            val color = runCatching { dev.errnolink.tsuzuki.ui.details.DetailsCardColor.fromBitmap(image.asAndroidBitmap()) }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                cardColorJobs.remove(mangaId)
                if (color != null && mangaId !in cardColors) cardColors[mangaId] = color
            }
        }
    }

    /** A tap this fresh may claim a cover as the zoom origin; older touches belong to other flows. */
    internal const val ClaimTouchWindowMillis = 500L

    fun recentTouch(maxAgeMillis: Long = 1500L): Offset? =
        lastDown?.takeIf { android.os.SystemClock.uptimeMillis() - lastDownAt <= maxAgeMillis }

    fun claim(mangaId: Long): Boolean {
        // One way to open a title (N13): the zoom comes from the row's cover whichever part of the
        // row was tapped, so the touch picks the cover nearest to it (containment first for grids
        // where several covers sit close). Opens with no fresh touch (deep links, notifications,
        // merges) or no cover on screen claim nothing and keep the plain push.
        val touch = recentTouch(ClaimTouchWindowMillis) ?: return false
        val set = sourcesByManga[mangaId] ?: return false
        val pick = pickClaimBounds(set.map { it.bounds }, touch)?.let { bounds -> set.firstOrNull { it.bounds == bounds } }
        if (pick == null) {
            origins.remove(mangaId)
            bound.remove(mangaId)
            return false
        }
        origins[mangaId] = pick.bounds
        bound[mangaId] = pick
        pick.tapSeed?.let { tapSeeds[mangaId] = it }
        return true
    }

    /** What the tapped tile knew when the flight started; null once the page leaves the stack. */
    fun tapSeed(mangaId: Long): HeroTapSeed? = tapSeeds[mangaId]

    fun active(mangaId: Long): Boolean = mangaId in origins

    /**
     * Where a title's flight came from, live or remembered: the back zoom returns into it, and when
     * the tile itself is gone (scrolled away, filtered out) the remembered rect still proves the
     * page arrived by zoom, so back plays the card fallback instead of a slide (N11).
     */
    fun popOrigin(mangaId: Long): Rect? =
        source(mangaId)?.bounds?.takeIf { !it.isEmpty } ?: origins[mangaId]?.takeIf { !it.isEmpty }

    fun retain(mangaIds: Set<Long>) {
        origins.keys.retainAll(mangaIds)
        bound.keys.retainAll(mangaIds)
        targets.keys.retainAll(mangaIds)
        tapSeeds.keys.retainAll(mangaIds)
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

/**
 * What the tapped tile already knows, handed to the title page before its first frame: the next
 * chapter to continue (the library row's continue target) so the Continue button's label and colour
 * are resolved when the page is shown, not ~250 ms after it lands (round 11, T2).
 */
class HeroTapSeed(
    val continueChapterNumber: Double? = null,
    val continueChapterName: String? = null,
    val continueLastPageRead: Long = 0,
    val continuePageTotal: Int? = null,
) {
    val hasChapter: Boolean get() = continueChapterNumber != null || !continueChapterName.isNullOrBlank()
}

/**
 * The registered cover a tap opens from (N13): the cover containing the touch — the smallest one
 * when covers nest — else the cover nearest to it, so tapping a row's title or a tile's caption
 * still zooms from that row's own cover. Pure.
 */
internal fun pickClaimBounds(bounds: List<Rect>, touch: Offset): Rect? {
    val live = bounds.filter { !it.isEmpty }
    val inside = live.filter { it.contains(touch) }
    return when {
        inside.isNotEmpty() -> inside.minByOrNull { it.width * it.height }
        else -> live.minByOrNull { (it.center - touch).getDistanceSquared() }
    }
}

@Composable
fun Modifier.mangaHeroSource(mangaId: Long, image: ImageBitmap?, tapSeed: HeroTapSeed? = null): Modifier {
    val source = remember(mangaId) { MangaHero.Source(mangaId) }
    source.image = image
    source.tapSeed = tapSeed
    DisposableEffect(source) {
        MangaHero.register(source)
        onDispose { MangaHero.unregister(source) }
    }
    val positioned = Modifier.onGloballyPositioned { source.bounds = it.boundsInRoot() }
    if (!MangaHero.active(mangaId)) return then(positioned)
    // While the page is up the slot shows an empty placeholder in the tile's own shape
    // (guide §5) instead of a hole in the grid.
    val placeholderColor = TsuzukiTheme.colors.elevated
    val placeholderCorner = with(LocalDensity.current) { LocalCoverShape.current.radius.toPx() }
    return then(positioned).drawWithContent {
        val flying = MangaHero.inFlight == mangaId && source.image != null && MangaHero.source(mangaId) === source &&
            MangaHero.target(mangaId) != null
        if (flying) {
            drawRoundRect(
                color = placeholderColor,
                cornerRadius = CornerRadius(placeholderCorner),
            )
        } else {
            drawContent()
        }
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
    }.drawWithContent {
        // The flying cover is the page's cover while the zoom moves: one cover on screen, swapped
        // back in the frame the flight settles (same bitmap, same rect).
        if (MangaHero.flight?.mangaId != mangaId) drawContent()
    }
}

/**
 * A tile's caption while its title is up: it fades out over the first 15 % of the open and back
 * in over the last 15 % of the close, and stays hidden while the page is up. Layer-only.
 */
fun Modifier.heroFlightLabel(mangaId: Long): Modifier = graphicsLayer {
    val flight = MangaHero.flight
    alpha = when {
        flight?.mangaId == mangaId -> zoomTileLabelAlpha(flight.progress())
        MangaHero.inFlight == mangaId -> 0f
        else -> 1f
    }
}

/**
 * A title-page section during its zoom: laid out at its final place, it fades in (and rises
 * [ZoomReveal.riseDp]) on its own window of the open, and leaves over the first 40 % of a close.
 * Layer-only; settled pages are untouched.
 */
fun Modifier.heroReveal(mangaId: Long, section: ZoomReveal): Modifier = graphicsLayer {
    val flight = MangaHero.flight
    if (flight == null || flight.mangaId != mangaId) {
        alpha = 1f
        translationY = 0f
        return@graphicsLayer
    }
    val a = zoomRevealAlpha(flight.progress(), section.start, section.end, flight.closing())
    alpha = a
    translationY = (1f - a) * section.riseDp * density
}

interface HeroDismiss {
    fun canStart(): Boolean
    fun start(): Boolean
    fun drag(total: Offset)
    fun end(velocityY: Float)
}

val LocalHeroDismiss = staticCompositionLocalOf<HeroDismiss?> { null }

/**
 * The stack transition's stage progress: 1f when the front page is settled, 0f when it is fully
 * behind the previous one. Readers must observe it outside recomposition (lambda, snapshotFlow);
 * the provider reads an Animatable. Defaults to settled so screens outside the stack (reader,
 * dialogs) see 1f.
 */
val LocalNavigationProgress = staticCompositionLocalOf<() -> Float> { { 1f } }

@Composable
fun Modifier.heroDismissDrag(atTop: () -> Boolean): Modifier {
    val dismiss = LocalHeroDismiss.current ?: return this
    val currentAtTop = androidx.compose.runtime.rememberUpdatedState(atTop)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun LayoutCoordinates?.window(local: Offset): Offset = this?.takeIf { it.isAttached }?.localToWindow(local) ?: local
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(dismiss) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentAtTop.value() || !dismiss.canStart()) return@awaitEachGesture
            var travel = Offset.Zero
            var claimed: androidx.compose.ui.input.pointer.PointerInputChange? = null
            while (claimed == null) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                travel += change.position - change.previousPosition
                if (kotlin.math.abs(travel.x) > slop || kotlin.math.abs(travel.y) > slop) {
                    if (travel.y <= 0f || kotlin.math.abs(travel.y) <= kotlin.math.abs(travel.x) || !currentAtTop.value()) {
                        return@awaitEachGesture
                    }
                    claimed = change
                }
            }
            val picked = claimed ?: return@awaitEachGesture
            picked.consume()
            val origin = coordinates[0].window(down.position)
            if (!dismiss.start()) return@awaitEachGesture
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            tracker.addPosition(picked.uptimeMillis, coordinates[0].window(picked.position))
            var velocityY = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val position = coordinates[0].window(change.position)
                if (!change.pressed) {
                    velocityY = tracker.calculateVelocity().y
                    change.consume()
                    break
                }
                tracker.addPosition(change.uptimeMillis, position)
                event.changes.forEach { it.consume() }
                dismiss.drag(position - origin)
            }
            dismiss.end(velocityY)
        }
    }
}
