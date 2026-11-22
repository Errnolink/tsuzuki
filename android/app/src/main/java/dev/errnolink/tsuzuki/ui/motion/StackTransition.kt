package dev.errnolink.tsuzuki.ui.motion

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import dev.errnolink.tsuzuki.designsystem.LocalCoverShape
import dev.errnolink.tsuzuki.designsystem.LocalNavigationMotion
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.CancellationException
import tachiyomi.presentation.core.util.collectAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uy.kohesive.injekt.api.get
import dev.errnolink.tsuzuki.ui.shell.LocalShellArrivals
import dev.errnolink.tsuzuki.ui.shell.LocalShellTopOwner
import dev.errnolink.tsuzuki.ui.shell.RailContentStart
import dev.errnolink.tsuzuki.ui.shell.ShellBottomSearchDock
import dev.errnolink.tsuzuki.ui.shell.ShellTopControlRow

@Composable
fun rememberTabNavigationMotion(@Suppress("UNUSED_PARAMETER") key: String): Boolean = LocalNavigationMotion.current




internal enum class StageMotion { Push, Pop, Replace }

@Stable
private class NavStage(initial: Screen) {
    var front by mutableStateOf(initial)
    var back by mutableStateOf<Screen?>(null)
    var motion by mutableStateOf(StageMotion.Push)
    var hero by mutableStateOf(false)
    var moving by mutableStateOf(false)
    var navigating by mutableStateOf(false)
    var pushAnim by mutableStateOf<Job?>(null)
    var gestureBase = 1f
    val progress = Animatable(1f)
    var releaseSpeed = 0f
    /** The pop was a committed swipe or drag (its spring carries a touch of bounce), not a back tap. */
    var gestureCommit = false
    val followX = Animatable(0f)
    val followY = Animatable(0f)
    val cardT = Animatable(0f)
    var settledAt = 0L
}

@Composable
fun TsuzukiScreenTransition(navigator: Navigator, modifier: Modifier = Modifier) {
    val reduced = TsuzukiTheme.motion.reduced
    val surface = TsuzukiTheme.colors.background
    val tileCorner = with(LocalDensity.current) { LocalCoverShape.current.radius.toPx() }
    val density = LocalDensity.current.density
    val haptics = TsuzukiTheme.haptics
    val stage = remember { NavStage(navigator.lastItem).also { it.back = navigator.items.getOrNull(navigator.items.lastIndex - 1) } }
    val scope = rememberCoroutineScope()
    val isHero: (Screen) -> Boolean = { screen ->
        !reduced && (screen as? MangaScreen)?.let { MangaHero.active(it.mangaId) } == true
    }
    // N11: back is the zoom reversed into the tile the page came from; when that tile is gone the
    // remembered origin still routes the pop to the ghost card, never a slide.
    val isHeroPop: (Screen) -> Boolean = { screen ->
        isHero(screen) && (screen as? MangaScreen)?.let { MangaHero.popOrigin(it.mangaId) != null } == true
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
                val velocity = if (stage.releaseSpeed != 0f) {
                    stage.releaseSpeed
                } else if (stage.hero && !reduced) {
                    // back button right after the landing closes quicker
                    -heroButtonBackSpeed(android.os.SystemClock.uptimeMillis() - stage.settledAt)
                } else {
                    0f
                }
                stage.releaseSpeed = 0f
                val swiped = stage.gestureCommit
                stage.gestureCommit = false
                val flight = stage.hero && !reduced
                val homeSpec = if (swiped) KasaneSprings.zoomCommit<Float>() else KasaneSprings.zoomBack<Float>()
                if (flight) {
                    // the finger follow and the predictive-back card ride the same spring with
                    // proportionally scaled velocities, so the flight leaves from exactly the pose the
                    // gesture released and everything melts away together on the way into the tile
                    launch { stage.followX.animateTo(0f, homeSpec, stage.followX.value * velocity) }
                    launch { stage.followY.animateTo(0f, homeSpec, stage.followY.value * velocity) }
                    launch { stage.cardT.animateTo(0f, homeSpec, stage.cardT.value * velocity) }
                }
                stage.progress.animateTo(0f, if (flight) homeSpec else popSpec(stage.hero, reduced), if (flight) velocity else 0f)
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
                    val targetId = target.mangaIdOrNull()
                    if (targetId != null) {
                        for (ignored in 0 until HeroTargetWaitFrames) {
                            if (MangaHero.target(targetId) != null) break
                            withFrameNanos { }
                        }
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
        stage.followX.snapTo(0f)
        stage.followY.snapTo(0f)
        stage.cardT.snapTo(0f)
        stage.moving = false
        stage.navigating = false
        stage.settledAt = android.os.SystemClock.uptimeMillis()
        MangaHero.retain(navigator.items.mapNotNullTo(HashSet()) { (it as? MangaScreen)?.mangaId })
    }

    val rootArrivals = rememberCoverArrivalState()
    val stageHeight = remember { mutableIntStateOf(1) }
    val stageWidth = remember { mutableIntStateOf(1) }
    PredictiveBackHandler(
        enabled = navigator.items.size > 1 &&
            (!stage.navigating || (stage.motion == StageMotion.Push && stage.pushAnim != null)),
    ) { events ->
        try {
            var started = false
            var touch0 = Offset.Zero
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
                    touch0 = Offset(event.touchX, event.touchY)
                    if (interrupting) stage.navigating = false
                }
                tracker.addDataPoint(android.os.SystemClock.uptimeMillis(), kotlin.math.abs(event.touchX - touch0.x))
                val eased = M3Motion.StandardDecelerate.transform(event.progress.coerceIn(0f, 1f))
                val base = stage.gestureBase
                if (stage.hero && base >= 1f) {
                    // the stable stack's predictive-back pose: the page becomes a card that follows the
                    // finger (1:1 across, half speed down) from where the gesture started; the zoom
                    // progress does not move and the page content stays visible
                    stage.cardT.snapTo(eased)
                    stage.followX.snapTo((event.touchX - touch0.x) * ZoomFollowX)
                    stage.followY.snapTo((event.touchY - touch0.y) * ZoomFollowY)
                } else {
                    stage.progress.snapTo(stackGestureProgress(base, eased))
                }
            }
            stage.releaseSpeed = if (stage.hero && !reduced) {
                zoomReleaseVelocity(tracker.calculateVelocity(), stageWidth.intValue.toFloat())
            } else {
                0f
            }
            stage.gestureCommit = stage.hero && !reduced
            haptics.select()
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch {
                launch { stage.followX.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                launch { stage.followY.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                launch { stage.cardT.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                stage.progress.animateTo(1f, KasaneSprings.zoomSnap<Float>())
                stage.moving = false
            }
            throw e
        }
    }

    val heroId = (stage.front as? MangaScreen)?.mangaId?.takeIf { stage.hero }
    val ownedFlight = remember { arrayOfNulls<Long>(1) }
    val ownedHeroFlight = remember { arrayOfNulls<HeroFlight>(1) }
    val flightState = remember(heroId) {
        heroId?.let { id -> HeroFlight(id, progress = { stage.progress.value }, closing = { stage.motion == StageMotion.Pop }) }
    }
    val flying = flightState != null && stage.moving && MangaHero.source(flightState.mangaId)?.image != null
    SideEffect {
        if (heroId != null) {
            MangaHero.inFlight = heroId
            ownedFlight[0] = heroId
        } else if (ownedFlight[0] != null) {
            if (MangaHero.inFlight == ownedFlight[0]) MangaHero.inFlight = null
            ownedFlight[0] = null
        }
        if (flying) {
            MangaHero.flight = flightState
            ownedHeroFlight[0] = flightState
        } else if (ownedHeroFlight[0] != null) {
            if (MangaHero.flight === ownedHeroFlight[0]) MangaHero.flight = null
            ownedHeroFlight[0] = null
        }
    }
    val coverColourCard by remember { uy.kohesive.injekt.Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>().coverColourCard() }.collectAsState()
    SideEffect { MangaHero.cardColorsEnabled = coverColourCard }
    val dismiss = remember(navigator) {
        object : HeroDismiss {
            private var active = false

            override fun canStart(): Boolean = when {
                stage.navigating || stage.moving || navigator.items.size <= 1 -> false
                reduced -> stage.front is MangaScreen
                else -> isHeroPop(stage.front)
            }

            override fun start(): Boolean {
                if (!canStart()) return false
                active = true
                stage.gestureBase = 1f
                stage.motion = StageMotion.Pop
                stage.hero = !reduced && isHeroPop(stage.front)
                stage.moving = true
                return true
            }

            override fun drag(total: Offset) {
                if (!active) return
                val heightPx = stageHeight.intValue.toFloat()
                scope.launch {
                    val travel = zoomDismissTravel(total.y, heightPx)
                    if (stage.hero) {
                        // the same card as predictive back: shrink and follow the finger, content stays
                        stage.cardT.snapTo(travel)
                        stage.followX.snapTo(total.x * ZoomFollowX)
                        stage.followY.snapTo(total.y * ZoomFollowY)
                    } else {
                        stage.progress.snapTo(1f - travel)
                    }
                }
            }

            override fun end(velocityY: Float) {
                if (!active) return
                active = false
                val travel = if (stage.hero) stage.cardT.value else 1f - stage.progress.value
                if (zoomDismissCommits(travel, velocityY / density)) {
                    if (stage.hero && !reduced) {
                        stage.releaseSpeed = zoomReleaseVelocity(velocityY, stageHeight.intValue.toFloat() * ZoomDismissSpan)
                        stage.gestureCommit = true
                    }
                    haptics.select()
                    navigator.pop()
                } else {
                    scope.launch {
                        launch { stage.followX.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                        launch { stage.followY.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                        launch { stage.cardT.animateTo(0f, KasaneSprings.zoomSnap<Float>()) }
                        stage.progress.animateTo(1f, KasaneSprings.zoomSnap<Float>())
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
            modifier
                .onSizeChanged {
                    stageHeight.intValue = it.height
                    stageWidth.intValue = it.width
                }
                .pointerInput(Unit) {
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
                        surface = surface,
                        tileCorner = tileCorner,
                    )
                }
            }
            ShellTopControlRow(
                frontKey = stage.front.key,
                backKey = stage.back?.key,
                moving = stage.moving,
                // the zoom's chrome does not fly: it fades in at its place over p 0.6 → 1 (and
                // leaves over the first 40 % of a close)
                progress = {
                    heroChromeProgress(stage.progress.value, stage.cardT.value, MangaHero.flight != null)
                },
                frontTint = (stage.front as? MangaScreen)?.let { MangaHero.cardColor(it.mangaId) },
                startPadding = if (stage.front == eu.kanade.tachiyomi.ui.home.HomeScreen &&
                    dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass.current !=
                    dev.errnolink.tsuzuki.designsystem.WindowWidthClass.Compact
                ) {
                    RailContentStart
                } else {
                    androidx.compose.ui.unit.Dp(0f)
                },
                // 3B: the bottom dock hosts tab roots; every pushed page keeps its search up here.
                topSearch = stage.front !is eu.kanade.tachiyomi.ui.home.HomeScreen,
            )
            ShellBottomSearchDock(
                modifier = Modifier.matchParentSize(),
                frontKey = stage.front.key,
                isTabRoot = stage.front is eu.kanade.tachiyomi.ui.home.HomeScreen,
                moving = stage.moving,
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
    surface: Color,
    tileCorner: Float,
) {
    val frame = remember { HeroFrame() }
    val dark = TsuzukiTheme.colors.isDark
    val coverSurface = heroId?.let { MangaHero.cardColor(it) }
        ?.let { card -> remember(card, dark) { dev.errnolink.tsuzuki.designsystem.expressiveCoverColors(card, dark).background } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
        // DET-04: only the moving front layer consumes stage progress; a layer beneath a
        // pushed page keeps its settled state, so its cover scheme never lerps away while
        // another screen closes over it.
        LocalNavigationProgress provides { if (isFront) stage.progress.value else 1f },
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
                    shadowElevation = 0f
                    renderEffect = null
                    val pose = stackPose(p, size.width, rtl)
                    if (!isFront) {
                        if (!moving) {
                            alpha = 0f
                        } else if (!stage.hero && !reduced && stage.motion != StageMotion.Replace) {
                            translationX = pose.backX
                        } else if (reduced || stage.motion == StageMotion.Replace) {
                            alpha = M3Motion.fadeThroughOut(p)
                        }
                        return@graphicsLayer
                    }
                    if (!moving) return@graphicsLayer
                    val origin = heroId?.let { MangaHero.source(it)?.bounds?.takeIf { b -> !b.isEmpty } }
                    when {
                        heroId != null && origin != null && size.width > 0f -> {
                            // the page stays at full size; its card clip grows from the tile and the
                            // gesture card shrinks it about the centre, following the finger
                            val pc = p.coerceIn(0f, 1f)
                            val card = stage.cardT.value
                            val zoom = zoomCardPose(
                                zoomPose(
                                    origin,
                                    size,
                                    p,
                                    sourceRadius = tileCorner,
                                    screenRadius = 0f,
                                    flightRadius = ZoomFlightRadius.toPx(),
                                    follow = Offset(stage.followX.value, stage.followY.value),
                                ),
                                size,
                                card,
                                ZoomCardCorner.toPx(),
                            )
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.translation.x
                            translationY = zoom.translation.y
                            shape = HeroClipShape(zoom.clip, zoom.corner / zoom.scale)
                            clip = true
                            shadowElevation = HeroElevation.toPx() * (4f * pc * (1f - pc) + card).coerceAtMost(1f)
                        }
                        heroId != null -> {
                            // N11: the origin tile is gone (scrolled away, filtered out) — the page
                            // leaves as a card shrinking and fading toward the screen centre, with
                            // the same gesture card and finger follow as the live-tile flight.
                            val ghost = zoomCardPose(
                                zoomGhostPose(p, size, ZoomCardCorner.toPx()),
                                size,
                                stage.cardT.value,
                                ZoomCardCorner.toPx(),
                            )
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            scaleX = ghost.scale
                            scaleY = ghost.scale
                            translationX = ghost.translation.x + stage.followX.value
                            translationY = ghost.translation.y + stage.followY.value
                            alpha = zoomGhostAlpha(p)
                            shape = HeroClipShape(ghost.clip, ghost.corner / ghost.scale)
                            clip = true
                            shadowElevation = HeroElevation.toPx()
                        }
                        reduced || stage.motion == StageMotion.Replace -> {
                            alpha = M3Motion.fadeThroughIn(p)
                            val s = lerp(M3Motion.FadeThroughScale, 1f, M3Motion.EmphasizedDecelerate.transform(p))
                            scaleX = s
                            scaleY = s
                        }
                        else -> {
                            translationX = pose.frontX
                            shape = StackEdgeShape(StackSpec.LeadingCorner.toPx(), rtl)
                            clip = true
                            shadowElevation = StackSpec.Elevation.toPx()
                        }
                    }
                }
                .drawBehind { drawRect(coverSurface ?: surface) }
                .onPlaced { frame.coordinates = it },
        ) {
            Box {
                navigator.saveableState("screen-transition-${screen.key}", screen) {
                    screen.Content()
                }
            }
            if (heroId != null) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // the one continuous object: the tile's own decoded cover flies from the
                            // tile's rect to the page's cover rect while the zoom moves; the page's
                            // cover is hidden meanwhile and takes over in the frame it settles
                            val flight = MangaHero.flight ?: return@drawBehind
                            if (flight.mangaId != heroId) return@drawBehind
                            val source = MangaHero.source(heroId) ?: return@drawBehind
                            val image = source.image ?: return@drawBehind
                            val follow = Offset(stage.followX.value, stage.followY.value)
                            val rawTarget = MangaHero.target(heroId)?.takeIf { !it.isEmpty }
                            val coverAlpha = heroCoverAlpha(rawTarget != null, stage.progress.value)
                            if (coverAlpha <= 0.01f) return@drawBehind
                            val target = rawTarget ?: source.bounds.translate(-follow)
                            drawCoverBitmap(image, zoomCoverRect(source.bounds, target, stage.progress.value, follow), tileCorner, coverAlpha)
                        },
                )
            }
            if (!isFront && !reduced) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val p = stage.progress.value
                            val scrimmed = stage.moving && (stage.hero || stage.motion == StageMotion.Pop || (!reduced && stage.motion == StageMotion.Push))
                            if (scrimmed) {
                                if (stage.hero) {
                                    drawRect(Color.Black, alpha = HeroScrimAlpha * zoomBackdrop(p, stage.cardT.value))
                                } else {
                                    drawRect(Color.Black, alpha = stackPose(p, size.width, rtl).scrim)
                                }
                            }
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
    hero -> KasaneSprings.zoomOpen<Float>()
    motion == StageMotion.Replace -> tween(M3Motion.Medium1, easing = M3Motion.Standard)
    else -> tween(M3Motion.Medium4, easing = M3Motion.EmphasizedDecelerate)
}

private fun popSpec(
    hero: Boolean,
    reduced: Boolean,
): androidx.compose.animation.core.AnimationSpec<Float> {
    if (reduced) return M3Motion.reducedFade()
    if (hero) return KasaneSprings.zoomBack<Float>()
    // NAV-06: every non-hero pop leaves on one fixed-duration exit — scaling it by the
    // remaining progress made an interrupted exit finish in ~150 ms (a hard cut) while a pop
    // from rest took the full duration.
    return tween(M3Motion.Medium2, easing = M3Motion.Emphasized)
}

private fun Screen.mangaIdOrNull(): Long? = (this as? MangaScreen)?.mangaId

private class StackEdgeShape(val radius: Float, val rtl: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                Rect(0f, 0f, size.width, size.height),
                topLeft = CornerRadius(if (rtl) 0f else radius),
                topRight = CornerRadius(if (rtl) radius else 0f),
                bottomRight = CornerRadius(if (rtl) radius else 0f),
                bottomLeft = CornerRadius(if (rtl) 0f else radius),
            ),
        )
}
