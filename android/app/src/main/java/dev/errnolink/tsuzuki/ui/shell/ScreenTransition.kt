package dev.errnolink.tsuzuki.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import androidx.compose.animation.core.spring
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
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.CancellationException
import tachiyomi.presentation.core.util.collectAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.api.get

@Composable
fun rememberTabNavigationMotion(@Suppress("UNUSED_PARAMETER") key: String): Boolean = LocalNavigationMotion.current

private const val PopCardScale = 0.9f
private val PopCardCorner = 28.dp
private val PopCardMargin = 8.dp
private val HeroCardCorner = 28.dp
private const val ContainerContentFadeEnd = 0.35f
private const val HeroScrimAlpha = 0.14f
private const val PopScrimAlpha = 0.4f
private val HeroElevation = 28.dp
private val HeroBackdropBlur = 12.dp
private const val DragShrink = 0.2f
private const val DragFollow = 0.6f
private val DragCorner = 34.dp
private const val FlyingCoverFadeEnd = 0.3f
private const val PopCrossfadeStart = 0.35f
private const val PopCrossfadeEnd = 0.7f
internal const val HeroOpenMillis = 440
internal const val HeroCloseRelaxedMillis = 380
internal const val HeroCloseSnappyMillis = 150
private const val HeroReleaseMinMillis = 110
internal val HeroOpenEasing: Easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.75f, 0.15f, 1f)
internal val HeroCloseEasing: Easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.75f, 0.2f, 1f)
private const val GestureSlowDp = 300f
private const val GestureFastDp = 2000f
private const val QuickBackWindowMillis = 700L
private const val QuickBackSpeed = 0.6f
private const val DragSettleDamping = 1f
private const val DragSettleStiffness = 175f
private const val PopFadeEnd = 0.6f
private const val HeroTargetWaitFrames = 2
private const val PushExitProgress = 0.5f
private val HeroDismissRange = 360.dp
private const val HeroDismissSideFollow = 0.4f
private const val HeroDismissSlowCommit = 0.35f
private const val HeroDismissFastCommit = 0.05f
private const val HeroDismissPullBackCommit = 0.6f

internal fun heroDismissAmount(dragY: Float, rangePx: Float): Float =
    if (rangePx <= 0f) 0f else (dragY / rangePx).coerceIn(0f, 1f)

internal fun heroGestureSpeed(velocityDp: Float): Float {
    val t = ((velocityDp - GestureSlowDp) / (GestureFastDp - GestureSlowDp)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun heroDismissCommits(amount: Float, velocityDp: Float): Boolean = when {
    velocityDp < -GestureSlowDp -> amount >= HeroDismissPullBackCommit
    else -> amount >= lerp(HeroDismissSlowCommit, HeroDismissFastCommit, heroGestureSpeed(velocityDp))
}

internal fun heroButtonBackSpeed(sinceSettledMillis: Long): Float =
    if (sinceSettledMillis in 0 until QuickBackWindowMillis) QuickBackSpeed else 0f
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
    var releaseSpeed = -1f
    var settledAt = 0L
    val dragAmount = Animatable(0f)
    val dragX = Animatable(0f)
    val dragY = Animatable(0f)
    var dragStart = Offset.Zero
}

@Composable
fun TsuzukiScreenTransition(navigator: Navigator, modifier: Modifier = Modifier) {
    val reduced = TsuzukiTheme.motion.reduced
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slidePx = with(LocalDensity.current) { M3Motion.SharedAxisSlide.toPx() } * if (rtl) -1f else 1f
    val surface = TsuzukiTheme.colors.background
    val tileCorner = with(LocalDensity.current) { TsuzukiCorners.cover.toPx() }
    val density = LocalDensity.current.density
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
                val speed = stage.releaseSpeed.takeIf { it >= 0f }
                    ?: heroButtonBackSpeed(android.os.SystemClock.uptimeMillis() - stage.settledAt)
                stage.releaseSpeed = -1f
                stage.progress.animateTo(0f, popSpec(stage.hero, reduced, stage.progress.value, speed))
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
                    repeat(HeroTargetWaitFrames) {
                        if (MangaHero.target(target.mangaIdOrNull() ?: return@repeat) == null) withFrameNanos { }
                    }
                }
                var settled = false
                val anim = scope.launch {
                    stage.progress.animateTo(1f, pushSpec(stage.hero, stage.motion, reduced))
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
        stage.dragAmount.snapTo(0f)
        stage.dragX.snapTo(0f)
        stage.dragY.snapTo(0f)
        stage.moving = false
        stage.navigating = false
        stage.fromRight = false
        stage.settledAt = android.os.SystemClock.uptimeMillis()
        MangaHero.retain(navigator.items.mapNotNullTo(HashSet()) { (it as? MangaScreen)?.mangaId })
    }

    PredictiveBackHandler(
        enabled = !reduced && navigator.items.size > 1 &&
            (!stage.navigating || (stage.motion == StageMotion.Push && stage.pushAnim != null)),
    ) { events ->
        try {
            var started = false
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker1D(isDataDifferential = false)
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
                    stage.dragStart = Offset(event.touchX, event.touchY)
                    if (interrupting) stage.navigating = false
                }
                stage.fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                tracker.addDataPoint(android.os.SystemClock.uptimeMillis(), kotlin.math.abs(event.touchX - stage.dragStart.x))
                val eased = M3Motion.StandardDecelerate.transform(event.progress.coerceIn(0f, 1f))
                if (stage.hero && stage.gestureBase >= 1f) {
                    stage.dragAmount.snapTo(eased)
                    stage.dragX.snapTo(event.touchX - stage.dragStart.x)
                    stage.dragY.snapTo(event.touchY - stage.dragStart.y)
                } else {
                    stage.progress.snapTo(stage.gestureBase * (1f - eased))
                }
            }
            stage.releaseSpeed = heroGestureSpeed(tracker.calculateVelocity() / density)
            haptics.select()
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch {
                val cancelDuration = ((M3Motion.Medium2 * (1f - stage.progress.value)).toInt()).coerceIn(M3Motion.Short3, M3Motion.Medium2)
                val cancelSpec = tween<Float>(cancelDuration, easing = M3Motion.EmphasizedDecelerate)
                val settle = spring<Float>(dampingRatio = DragSettleDamping, stiffness = DragSettleStiffness)
                launch { stage.dragX.animateTo(0f, settle) }
                launch { stage.dragY.animateTo(0f, settle) }
                stage.progress.animateTo(1f, cancelSpec)
                stage.dragAmount.animateTo(0f, settle)
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
    val coverColourCard by remember { uy.kohesive.injekt.Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>().coverColourCard() }.collectAsState()
    SideEffect { MangaHero.cardColorsEnabled = coverColourCard }
    val rootArrivals = rememberCoverArrivalState()
    val dismissRangePx = with(LocalDensity.current) { HeroDismissRange.toPx() }
    val dismiss = remember(navigator) {
        object : HeroDismiss {
            private var active = false

            override fun canStart(): Boolean =
                !reduced && !stage.navigating && !stage.moving && navigator.items.size > 1 && isHeroPop(stage.front)

            override fun start(): Boolean {
                if (!canStart()) return false
                active = true
                stage.gestureBase = 1f
                stage.motion = StageMotion.Pop
                stage.hero = true
                stage.moving = true
                return true
            }

            override fun drag(total: Offset) {
                if (!active) return
                scope.launch {
                    stage.dragAmount.snapTo(heroDismissAmount(total.y, dismissRangePx))
                    stage.dragX.snapTo(total.x / DragFollow * HeroDismissSideFollow)
                    stage.dragY.snapTo(total.y.coerceAtLeast(0f) / DragFollow)
                }
            }

            override fun end(velocityY: Float) {
                if (!active) return
                active = false
                val velocityDp = velocityY / density
                if (heroDismissCommits(stage.dragAmount.value, velocityDp)) {
                    stage.releaseSpeed = heroGestureSpeed(velocityDp)
                    haptics.select()
                    navigator.pop()
                } else {
                    scope.launch {
                        val settle = spring<Float>(dampingRatio = DragSettleDamping, stiffness = DragSettleStiffness)
                        launch { stage.dragX.animateTo(0f, settle) }
                        launch { stage.dragY.animateTo(0f, settle) }
                        stage.dragAmount.animateTo(0f, settle)
                        stage.moving = false
                    }
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalNavigationMotion provides stage.moving,
        LocalShellArrivals provides (LocalShellArrivals.current ?: rootArrivals),
        LocalHeroDismiss provides dismiss,
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
                        .drawBehind {
                            drawFlyingCover(
                                heroId,
                                stage.progress.value,
                                tileCorner,
                                stage.dragAmount.value,
                                Offset(stage.dragX.value, stage.dragY.value),
                                popping = stage.motion == StageMotion.Pop,
                                surface = MangaHero.cardColor(heroId) ?: surface,
                            )
                        },
                )
            }
            ShellTopControlRow(
                frontKey = stage.front.key,
                backKey = stage.back?.key,
                moving = stage.moving,
                progress = { stage.progress.value * (1f - stage.dragAmount.value) },
                frontTint = (stage.front as? MangaScreen)?.let { MangaHero.cardColor(it.mangaId) },
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
    slidePx: Float,
    surface: Color,
    tileCorner: Float,
) {
    val frame = remember { HeroFrame() }
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
                    renderEffect = null
                    if (!isFront) {
                        if (!moving) {
                            alpha = 0f
                        } else if (stage.motion == StageMotion.Push && !stage.hero && !reduced) {
                            translationX = -slidePx * M3Motion.EmphasizedAccelerate.transform((p / PushExitProgress).coerceIn(0f, 1f))
                            alpha = 1f - M3Motion.EmphasizedAccelerate.transform((p / M3Motion.FadeThroughThreshold).coerceIn(0f, 1f))
                        } else if (reduced || stage.motion == StageMotion.Replace) {
                            alpha = M3Motion.fadeThroughOut(p)
                        } else if (stage.hero && android.os.Build.VERSION.SDK_INT >= 31) {
                            val radius = HeroBackdropBlur.toPx() * heroBackdrop(p, stage.dragAmount.value)
                            if (radius >= 0.5f) renderEffect = androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Clamp)
                        }
                        return@graphicsLayer
                    }
                    if (!moving) return@graphicsLayer
                    val origin = heroId?.let { MangaHero.source(it)?.bounds?.takeIf { b -> !b.isEmpty } }
                    when {
                        heroId != null && origin == null -> alpha = 0f
                        heroId != null && origin != null && size.width > 0f -> {
                            val pc = p.coerceIn(0f, 1f)
                            val target = MangaHero.target(heroId)
                            val drag = stage.dragAmount.value * pc
                            val card = heroPose(
                                heroCard(origin, target, size, pc),
                                drag,
                                Offset(stage.dragX.value, stage.dragY.value) * (DragFollow * pc),
                            )
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = card.scale
                            scaleY = card.scale
                            translationX = card.translation.x
                            translationY = card.translation.y
                            val cornerPx = lerp(if (card.fromCover) HeroCardCorner.toPx() else tileCorner, 0f, pc) + DragCorner.toPx() * drag
                            shape = HeroClipShape(card.clip, cornerPx / card.scale)
                            clip = true
                            shadowElevation = HeroElevation.toPx() * (4f * pc * (1f - pc) + drag).coerceAtMost(1f)
                            alpha = when {
                                !card.fromCover -> 1f
                                stage.motion != StageMotion.Pop -> 1f
                                MangaHero.source(heroId)?.image != null -> 1f
                                else -> (pc / FlyingCoverFadeEnd).coerceIn(0f, 1f)
                            }
                        }
                        reduced || stage.motion == StageMotion.Replace -> {
                            alpha = M3Motion.fadeThroughIn(p)
                            val s = lerp(M3Motion.FadeThroughScale, 1f, M3Motion.EmphasizedDecelerate.transform(p))
                            scaleX = s
                            scaleY = s
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
                .drawBehind { if (heroId != null) drawRect(MangaHero.cardColor(heroId) ?: surface) }
                .onPlaced { frame.coordinates = it },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = stage.progress.value
                        alpha = when {
                            heroId == null -> 1f
                            container -> lerp(0f, 1f, (p / ContainerContentFadeEnd).coerceIn(0f, 1f))
                            else -> 1f
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
                            if (scrimmed) drawRect(Color.Black, alpha = if (stage.hero) HeroScrimAlpha * heroBackdrop(p, stage.dragAmount.value) else PopScrimAlpha * p)
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

private fun pushSpec(hero: Boolean, motion: StageMotion, reduced: Boolean) = when {
    reduced -> M3Motion.reducedFade<Float>()
    hero -> tween(HeroOpenMillis, easing = HeroOpenEasing)
    motion == StageMotion.Replace -> tween(M3Motion.Medium1, easing = M3Motion.Standard)
    else -> tween(M3Motion.Medium4, easing = M3Motion.EmphasizedDecelerate)
}

private fun popSpec(
    hero: Boolean,
    reduced: Boolean,
    remaining: Float,
    speed: Float = 0f,
): androidx.compose.animation.core.AnimationSpec<Float> {
    if (reduced) return M3Motion.reducedFade()
    if (hero) return tween(heroCloseMillis(remaining, speed), easing = HeroCloseEasing)
    val full = M3Motion.Medium2
    val duration = (full * remaining.coerceIn(0f, 1f)).toInt().coerceAtLeast(M3Motion.Short3)
    return tween(duration, easing = M3Motion.EmphasizedAccelerate)
}

internal fun heroCloseMillis(remaining: Float, speed: Float): Int =
    (lerp(HeroCloseRelaxedMillis.toFloat(), HeroCloseSnappyMillis.toFloat(), speed.coerceIn(0f, 1f)) * remaining.coerceIn(0f, 1f))
        .toInt().coerceAtLeast(HeroReleaseMinMillis)

internal fun heroBackdrop(progress: Float, drag: Float = 0f): Float =
    kotlin.math.sqrt(progress.coerceIn(0f, 1f)) * (1f - 0.7f * drag.coerceIn(0f, 1f))

internal fun heroPopCrossfade(progress: Float): Float {
    val travel = 1f - progress.coerceIn(0f, 1f)
    return ((travel - PopCrossfadeStart) / (PopCrossfadeEnd - PopCrossfadeStart)).coerceIn(0f, 1f)
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

internal fun heroCard(origin: Rect, measuredTarget: Rect?, size: Size, p: Float): HeroCard {
    val target = measuredTarget?.takeIf { !it.isEmpty && it.bottom > 0f && it.top < size.height }
    if (target == null) {
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
    val fill = maxOf(origin.width / target.width, origin.height / target.height)
    val s = lerp(fill, 1f, p)
    val center = Offset(lerp(origin.center.x, target.center.x, p), lerp(origin.center.y, target.center.y, p))
    val translation = Offset(center.x - target.center.x * s, center.y - target.center.y * s)
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
    val cover = Rect(
        translation.x + target.left * s,
        translation.y + target.top * s,
        translation.x + target.right * s,
        translation.y + target.bottom * s,
    ).intersect(visible)
    return HeroCard(s, translation, clip, cover, fromCover = true)
}

internal fun heroPose(card: HeroCard, drag: Float, offset: Offset): HeroCard {
    if (drag <= 0f && offset == Offset.Zero) return card
    val k = 1f - DragShrink * drag
    val center = card.bounds.center
    fun map(point: Offset) = center + (point - center) * k + offset
    return HeroCard(
        scale = card.scale * k,
        translation = map(card.translation),
        clip = card.clip,
        cover = card.cover?.let { Rect(map(it.topLeft), map(it.bottomRight)) },
        fromCover = card.fromCover,
    )
}

internal fun flyingCoverAlpha(progress: Float): Float = (1f - progress.coerceIn(0f, 1f) / FlyingCoverFadeEnd).coerceIn(0f, 1f)

private fun DrawScope.drawFlyingCover(
    mangaId: Long,
    progress: Float,
    corner: Float,
    dragAmount: Float,
    dragOffset: Offset,
    popping: Boolean,
    surface: Color,
) {
    val alpha = if (popping) heroPopCrossfade(progress) else flyingCoverAlpha(progress)
    if (alpha <= 0f) return
    val source = MangaHero.source(mangaId) ?: return
    val from = source.bounds
    val image = source.image ?: return
    if (from.isEmpty) return
    val pc = progress.coerceIn(0f, 1f)
    val drag = dragAmount * pc
    val card = heroPose(heroCard(from, MangaHero.target(mangaId), size, pc), drag, dragOffset * (DragFollow * pc))
    val rect = if (popping) {
        val bounds = card.bounds
        val pageCorner = lerp(if (card.fromCover) HeroCardCorner.toPx() else corner, 0f, pc) + DragCorner.toPx() * drag
        val fillCorner = lerp(corner, pageCorner, pc)
        drawRoundRect(surface, bounds.topLeft, bounds.size, CornerRadius(fillCorner), alpha = alpha)
        card.cover ?: bounds
    } else {
        MangaHero.target(mangaId)?.let { card.cover } ?: from
    }
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
            alpha = alpha,
        )
    }
}

private data class HeroClipShape(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))),
        )
}

private fun Screen.mangaIdOrNull(): Long? = (this as? MangaScreen)?.mangaId
