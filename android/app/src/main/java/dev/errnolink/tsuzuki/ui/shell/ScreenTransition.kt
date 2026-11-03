package dev.errnolink.tsuzuki.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import dev.errnolink.tsuzuki.designsystem.LocalNavigationMotion
import dev.errnolink.tsuzuki.designsystem.LocalMotionTokens
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

@Composable
fun rememberTabNavigationMotion(@Suppress("UNUSED_PARAMETER") key: String): Boolean = LocalNavigationMotion.current

private const val PopCardScale = 0.9f
private val PopCardCorner = 28.dp
private val PopCardMargin = 8.dp
private val HeroCardCorner = 28.dp
private const val HeroPopCardFadeEnd = 0.3f
private const val ContainerContentFadeEnd = 0.35f
private const val HeroScrimAlpha = 0.5f
private const val HeroContentStartAlpha = 0.2f
private const val HeroContentFadeEnd = 0.4f
private const val PopFadeEnd = 0.6f
private const val HeroPostponeMillis = 120L
private const val CoverlessFadeEnd = 0.15f
private const val PushExitProgress = 0.5f

internal enum class StageMotion { Push, Pop, Replace }

@Stable
private class NavStage(initial: Screen) {
    var front by mutableStateOf(initial)
    var back by mutableStateOf<Screen?>(null)
    var motion by mutableStateOf(StageMotion.Push)
    var hero by mutableStateOf(false)
    var moving by mutableStateOf(false)
    var navigating by mutableStateOf(false)
    var fromRight by mutableStateOf(false)
    var pushAnim by mutableStateOf<Job?>(null)
    var gestureBase = 1f
    val progress = Animatable(1f)
}

@Composable
fun TsuzukiScreenTransition(navigator: Navigator, modifier: Modifier = Modifier) {
    val reduced = TsuzukiTheme.motion.reduced
    val tokens = LocalMotionTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slidePx = with(LocalDensity.current) { M3Motion.SharedAxisSlide.toPx() } * if (rtl) -1f else 1f
    val surface = TsuzukiTheme.colors.background
    val tileCorner = with(LocalDensity.current) { TsuzukiCorners.cover.toPx() }
    val haptics = TsuzukiTheme.haptics
    val stage = remember { NavStage(navigator.lastItem).also { it.back = navigator.items.getOrNull(navigator.items.lastIndex - 1) } }
    val scope = rememberCoroutineScope()
    val isHero: (Screen) -> Boolean = { screen ->
        !reduced && (screen as? MangaScreen)?.let { MangaHero.active(it.mangaId) } == true
    }
    val isHeroPop: (Screen) -> Boolean = { screen ->
        isHero(screen) && (screen as? MangaScreen)?.let { MangaHero.source(it.mangaId)?.bounds?.isEmpty == false } == true
    }
    LaunchedEffect(navigator.lastItem) {
        val target = navigator.lastItem
        val items = navigator.items
        stage.navigating = true
        if (stage.front != target) {
            val removed = stage.front !in items
            val popped = removed && (target == stage.back || navigator.lastEvent == StackEvent.Pop)
            if (popped) {
                stage.back = target
                if (!stage.moving) {
                    stage.motion = StageMotion.Pop
                    stage.hero = isHeroPop(stage.front)
                }
                stage.moving = true
                stage.progress.animateTo(0f, popSpec(stage.hero, reduced, stage.progress.value, tokens))
            } else {
                val previous = stage.front
                stage.back = previous.takeIf { it in items } ?: items.getOrNull(items.lastIndex - 1)
                stage.motion = if (
                    navigator.lastEvent == StackEvent.Replace || (removed && navigator.lastEvent != StackEvent.Push)
                ) {
                    StageMotion.Replace
                } else {
                    StageMotion.Push
                }
                if (!reduced && target is MangaScreen) MangaHero.claim(target.mangaId)
                stage.hero = stage.motion == StageMotion.Push && isHero(target)
                stage.moving = true
                stage.progress.snapTo(0f)
                stage.front = target
                withFrameNanos { }
                if (stage.hero) {
                    withTimeoutOrNull(HeroPostponeMillis) {
                        snapshotFlow { ScreenReadiness.isReady(target.key) }.first { it }
                        withFrameNanos { }
                    }
                }
                var settled = false
                val anim = scope.launch {
                    stage.progress.animateTo(1f, pushSpec(stage.hero, stage.motion, reduced, tokens))
                    settled = true
                }
                stage.pushAnim = anim
                anim.join()
                stage.pushAnim = null
                if (settled) {
                    if (stage.hero) haptics.select()
                } else {
                    stage.navigating = false
                    return@LaunchedEffect
                }
            }
        }
        stage.front = navigator.lastItem
        stage.back = navigator.items.getOrNull(navigator.items.lastIndex - 1)
        stage.progress.snapTo(1f)
        stage.moving = false
        stage.navigating = false
        stage.fromRight = false
        MangaHero.retain(navigator.items.mapNotNullTo(HashSet()) { (it as? MangaScreen)?.mangaId })
        ScreenReadiness.retain(navigator.items.mapTo(HashSet()) { it.key })
    }

    PredictiveBackHandler(
        enabled = !reduced && navigator.items.size > 1 &&
            (!stage.navigating || (stage.motion == StageMotion.Push && stage.pushAnim != null)),
    ) { events ->
        try {
            var started = false
            events.collect { event ->
                if (!started) {
                    started = true
                    val interrupting = stage.pushAnim != null
                    stage.pushAnim?.cancel()
                    stage.pushAnim = null
                    stage.gestureBase = stage.progress.value
                    stage.motion = StageMotion.Pop
                    stage.hero = isHeroPop(stage.front)
                    stage.moving = true
                    if (interrupting) stage.navigating = false
                }
                stage.fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                val eased = if (tokens) M3Motion.StandardDecelerate.transform(event.progress.coerceIn(0f, 1f)) else event.progress.coerceIn(0f, 1f)
                stage.progress.snapTo(stage.gestureBase * (1f - eased))
            }
            haptics.select()
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch {
                val cancelDuration = ((M3Motion.Medium2 * (1f - stage.progress.value)).toInt()).coerceIn(M3Motion.Short3, M3Motion.Medium2)
                val cancelSpec = if (tokens) tween<Float>(cancelDuration, easing = M3Motion.EmphasizedDecelerate) else tween(M3Motion.Short4, easing = M3Motion.Standard)
                stage.progress.animateTo(1f, cancelSpec)
                stage.moving = false
                stage.fromRight = false
            }
            throw e
        }
    }

    val heroId = (stage.front as? MangaScreen)?.mangaId?.takeIf { stage.hero && stage.moving }
    val ownedFlight = remember { arrayOfNulls<Long>(1) }
    SideEffect {
        if (heroId != null) {
            MangaHero.inFlight = heroId
            ownedFlight[0] = heroId
        } else if (ownedFlight[0] != null) {
            if (MangaHero.inFlight == ownedFlight[0]) MangaHero.inFlight = null
            ownedFlight[0] = null
        }
    }
    val rootArrivals = rememberCoverArrivalState()
    CompositionLocalProvider(
        LocalNavigationMotion provides stage.moving,
        LocalShellArrivals provides (LocalShellArrivals.current ?: rootArrivals),
    ) {
        Box(
            modifier.pointerInput(Unit) {
                awaitEachGesture {
                    MangaHero.pointerDown(awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position)
                }
            },
        ) {
            val rendered = listOfNotNull(stage.back, stage.front).distinct()
            rendered.forEach { screen ->
                key(screen.key) {
                    val isFront = screen == stage.front
                    ScreenLayer(
                        navigator = navigator,
                        screen = screen,
                        stage = stage,
                        isFront = isFront,
                        heroId = if (isFront) heroId else null,
                        reduced = reduced,
                        tokens = tokens,
                        slidePx = slidePx,
                        surface = surface,
                        tileCorner = tileCorner,
                    )
                }
            }
            if (heroId != null) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind { drawFlyingCover(heroId, stage.progress.value, tileCorner) },
                )
            }
            ShellTopControlRow(
                frontKey = stage.front.key,
                backKey = stage.back?.key,
                moving = stage.moving,
                progress = stage.progress.value,
            )
        }
    }
}

@Composable
private fun ScreenLayer(
    navigator: Navigator,
    screen: Screen,
    stage: NavStage,
    isFront: Boolean,
    heroId: Long?,
    reduced: Boolean,
    tokens: Boolean,
    slidePx: Float,
    surface: Color,
    tileCorner: Float,
) {
    val frame = remember { HeroFrame() }
    val readyAlpha by animateFloatAsState(
        targetValue = if (heroId == null || ScreenReadiness.isReady(screen.key)) 1f else 0f,
        animationSpec = tween(M3Motion.Short4, easing = M3Motion.Standard),
        label = "hero-content-ready",
    )
    val container = heroId?.let { id ->
        MangaHero.source(id)?.bounds?.takeIf { b -> !b.isEmpty }?.let { heroContainerTransform(it, MangaHero.target(id)) }
    } == true
    val interactive = layerInteractive(stage.moving, stage.motion, isFront)
    val lifecycleOwner = LocalLifecycleOwner.current
    val realBack = LocalOnBackPressedDispatcherOwner.current
    val inertBack = remember(lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
            override val lifecycle get() = lifecycleOwner.lifecycle
        }
    }
    val realNav = androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner.current
    val inertNav = remember {
        object : androidx.navigationevent.NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = androidx.navigationevent.NavigationEventDispatcher()
        }
    }
    DisposableEffect(inertNav) {
        onDispose { inertNav.navigationEventDispatcher.dispose() }
    }
    val navOwner = if (interactive) realNav else inertNav
    CompositionLocalProvider(
        LocalHeroFrame provides frame,
        LocalShellTopOwner provides screen.key,
        LocalOnBackPressedDispatcherOwner provides (if (interactive) realBack else inertBack) as OnBackPressedDispatcherOwner,
        *listOfNotNull(navOwner?.let { androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides it }).toTypedArray(),
    ) {
        Box(
            Modifier
                .then(
                    when {
                        interactive -> Modifier
                        stage.moving && stage.motion == StageMotion.Pop -> Modifier.hitTestInvisible()
                        else -> Modifier.blockInput()
                    },
                )
                .fillMaxSize()
                .graphicsLayer {
                    val p = stage.progress.value
                    val moving = stage.moving
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    alpha = 1f
                    transformOrigin = TransformOrigin.Center
                    clip = false
                    if (!isFront) {
                        if (!moving) {
                            alpha = 0f
                        } else if (stage.motion == StageMotion.Push && !stage.hero && !reduced) {
                            if (tokens) {
                                translationX = -slidePx * M3Motion.EmphasizedAccelerate.transform((p / PushExitProgress).coerceIn(0f, 1f))
                                alpha = 1f - M3Motion.EmphasizedAccelerate.transform((p / M3Motion.FadeThroughThreshold).coerceIn(0f, 1f))
                            } else {
                                translationX = lerp(0f, -slidePx, p)
                                alpha = M3Motion.fadeThroughOut(p)
                            }
                        } else if (reduced || stage.motion == StageMotion.Replace) {
                            alpha = if (tokens) M3Motion.fadeThroughOut(p) else 1f - p
                        }
                        return@graphicsLayer
                    }
                    if (!moving) return@graphicsLayer
                    val origin = heroId?.let { MangaHero.source(it)?.bounds?.takeIf { b -> !b.isEmpty } }
                    when {
                        heroId != null && origin == null -> alpha = 0f
                        heroId != null && origin != null && size.width > 0f -> {
                            val target = MangaHero.target(heroId)
                            val card = heroCard(origin, target, size, p)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = card.scale
                            scaleY = card.scale
                            translationX = card.translation.x
                            translationY = card.translation.y
                            shape = HeroClipShape(
                                card.clip,
                                lerp(if (card.fromCover) HeroCardCorner.toPx() else tileCorner, 0f, p) / card.scale,
                            )
                            clip = true
                            alpha = when {
                                !card.fromCover -> 1f
                                stage.motion != StageMotion.Pop -> 1f
                                target == null -> (p / CoverlessFadeEnd).coerceIn(0f, 1f)
                                else -> (p / HeroPopCardFadeEnd).coerceIn(0f, 1f)
                            }
                        }
                        reduced || stage.motion == StageMotion.Replace -> {
                            alpha = if (tokens) M3Motion.fadeThroughIn(p) else p
                            if (tokens) {
                                val s = lerp(M3Motion.FadeThroughScale, 1f, M3Motion.EmphasizedDecelerate.transform(p))
                                scaleX = s
                                scaleY = s
                            }
                        }
                        stage.motion == StageMotion.Push -> {
                            translationX = lerp(slidePx, 0f, p)
                            alpha = M3Motion.fadeThroughIn(p)
                        }
                        else -> {
                            val s = lerp(PopCardScale, 1f, p)
                            scaleX = s
                            scaleY = s
                            val shift = (size.width / 20f - PopCardMargin.toPx()).coerceAtLeast(0f)
                            translationX = (if (stage.fromRight) -shift else shift) * (1f - p)
                            shape = RoundedCornerShape(PopCardCorner * (1f - p))
                            clip = true
                            alpha = (p / PopFadeEnd).coerceIn(0f, 1f)
                        }
                    }
                }
                .drawBehind { if (heroId != null) drawRect(surface) }
                .onPlaced { frame.coordinates = it },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = stage.progress.value
                        alpha = when {
                            heroId == null -> 1f
                            container -> readyAlpha * lerp(0f, 1f, (p / ContainerContentFadeEnd).coerceIn(0f, 1f))
                            else -> readyAlpha * lerp(HeroContentStartAlpha, 1f, (p / HeroContentFadeEnd).coerceIn(0f, 1f))
                        }
                    },
            ) {
                navigator.saveableState("screen-transition-${screen.key}", screen) {
                    screen.Content()
                }
            }
            if (!isFront && !reduced) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val p = stage.progress.value
                            val scrimmed = stage.moving && (stage.hero || stage.motion == StageMotion.Pop)
                            if (scrimmed) drawRect(Color.Black, alpha = HeroScrimAlpha * p)
                        },
                )
            }
        }
    }
}

internal fun layerInteractive(moving: Boolean, motion: StageMotion, isFront: Boolean): Boolean = when {
    !moving -> isFront
    motion == StageMotion.Pop -> !isFront
    else -> false
}

private fun Modifier.blockInput(): Modifier = clearAndSetSemantics { }.pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun Modifier.hitTestInvisible(): Modifier = clearAndSetSemantics { }.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) { placeable.place(0, 0) }
}

private fun pushSpec(hero: Boolean, motion: StageMotion, reduced: Boolean, tokens: Boolean) = when {
    reduced -> M3Motion.reducedFade<Float>()
    hero -> if (tokens) spring<Float>(dampingRatio = 0.9f, stiffness = 380f) else tween(M3Motion.Medium4, easing = M3Motion.EmphasizedDecelerate)
    motion == StageMotion.Replace -> tween(if (tokens) M3Motion.Medium1 else M3Motion.Medium2, easing = M3Motion.Standard)
    else -> tween(M3Motion.Medium4, easing = M3Motion.EmphasizedDecelerate)
}

private fun popSpec(
    hero: Boolean,
    reduced: Boolean,
    remaining: Float,
    tokens: Boolean,
): androidx.compose.animation.core.AnimationSpec<Float> {
    if (reduced) return M3Motion.reducedFade()
    val full = if (hero) M3Motion.Medium1 else M3Motion.Medium2
    val duration = (full * remaining.coerceIn(0f, 1f)).toInt().coerceAtLeast(M3Motion.Short3)
    val easing = if (tokens && !hero) M3Motion.EmphasizedAccelerate else M3Motion.EmphasizedDecelerate
    return tween(duration, easing = easing)
}

internal class HeroCard(
    val scale: Float,
    val translation: Offset,
    val clip: Rect,
    val cover: Rect?,
    val fromCover: Boolean,
) {
    val bounds: Rect
        get() = Rect(
            translation.x + clip.left * scale,
            translation.y + clip.top * scale,
            translation.x + clip.right * scale,
            translation.y + clip.bottom * scale,
        )
}

internal fun heroContainerTransform(origin: Rect, target: Rect?): Boolean =
    target != null && !target.isEmpty && target.width < origin.width

internal fun heroCard(origin: Rect, target: Rect?, size: Size, p: Float): HeroCard {
    if (target == null || target.isEmpty) {
        val s = lerp(origin.width / size.width, 1f, p)
        val translation = Offset(lerp(origin.left, 0f, p), lerp(origin.top, 0f, p))
        val clip = Rect(
            0f,
            0f,
            size.width,
            lerp(origin.height * size.width / origin.width, size.height, p).coerceAtMost(size.height),
        )
        return HeroCard(s, translation, clip, null, fromCover = true)
    }
    if (heroContainerTransform(origin, target)) {
        val s = lerp(maxOf(origin.width, target.width) / size.width, 1f, p)
        val coverRect = Rect(
            lerp(origin.left, target.left, p),
            lerp(origin.top, target.top, p),
            lerp(origin.right, target.right, p),
            lerp(origin.bottom, target.bottom, p),
        )
        val translation = Offset(coverRect.left - target.left * s, coverRect.top - target.top * s)
        val visible = Rect(
            lerp(origin.left, 0f, p),
            lerp(origin.top, 0f, p),
            lerp(origin.right, size.width, p),
            lerp(origin.bottom, size.height, p),
        )
        val clip = Rect(
            (visible.left - translation.x) / s,
            (visible.top - translation.y) / s,
            (visible.right - translation.x) / s,
            (visible.bottom - translation.y) / s,
        )
        return HeroCard(s, translation, clip, coverRect, fromCover = false)
    }
    val s = lerp(origin.width / target.width, 1f, p)
    val translation = Offset(
        lerp(origin.left, target.left, p) - target.left * s,
        lerp(origin.top, target.top, p) - target.top * s,
    )
    val clip = Rect(
        lerp(target.left, 0f, p),
        lerp(target.top, 0f, p),
        lerp(target.right, size.width, p),
        lerp(target.bottom, size.height, p),
    )
    val safe = Rect(
        (clip.left * s + translation.x).coerceIn(0f, size.width),
        (clip.top * s + translation.y).coerceIn(0f, size.height),
        (clip.right * s + translation.x).coerceIn(0f, size.width),
        (clip.bottom * s + translation.y).coerceIn(0f, size.height),
    )
    val local = Rect(
        (safe.left - translation.x) / s,
        (safe.top - translation.y) / s,
        (safe.right - translation.x) / s,
        (safe.bottom - translation.y) / s,
    )
    val cover = Rect(
        translation.x + target.left * s,
        translation.y + target.top * s,
        translation.x + target.right * s,
        translation.y + target.bottom * s,
    )
    return HeroCard(s, translation, local, cover, fromCover = true)
}

private fun DrawScope.drawFlyingCover(mangaId: Long, progress: Float, corner: Float) {
    val source = MangaHero.source(mangaId) ?: return
    val from = source.bounds
    val image = source.image ?: return
    if (from.isEmpty) return
    val rect = MangaHero.target(mangaId)?.let { heroCard(from, it, size, progress).cover } ?: from
    if (rect.width <= 0f || rect.height <= 0f) return
    val srcW = image.width.toFloat()
    val srcH = image.height.toFloat()
    val dstAspect = rect.width / rect.height
    val cropW: Float
    val cropH: Float
    if (srcW / srcH > dstAspect) {
        cropH = srcH
        cropW = srcH * dstAspect
    } else {
        cropW = srcW
        cropH = srcW / dstAspect
    }
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }
    clipPath(path) {
        drawImage(
            image = image,
            srcOffset = IntOffset(((srcW - cropW) / 2f).toInt(), ((srcH - cropH) / 2f).toInt()),
            srcSize = IntSize(cropW.toInt(), cropH.toInt()),
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

private data class HeroClipShape(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))),
        )
}
