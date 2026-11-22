package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.drawBackdrop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/** Most a scrim behind a liquid card darkens the page. */
private const val LiquidScrimAlpha = 0.28f

/** How long the pour waits for the IME to say where it is going, so both rise as one movement. */
private const val LiquidImeWaitMillis = 120L

private val CardFrost = 20.dp

/**
 * DET-03 (pure): the card's resting bottom inset, in px. While open the card follows the LIVE
 * animated IME but never sits lower than the keyboard's published target — it aims at the final
 * place as the keyboard rises (one movement) and rides down smoothly when the IME is dismissed
 * on its own (the old `imeAnimationTarget`-only padding dropped to the nav bar in one frame).
 * While draining, the frozen value keeps the card still. −1 = not frozen.
 */
internal fun liquidRestingBottom(liveImePx: Int, imeTargetPx: Int, navBottomPx: Int, frozenPx: Int): Int =
    if (frozenPx >= 0) frozenPx else maxOf(liveImePx, imeTargetPx, navBottomPx)

private fun GlassBox.shifted(dx: Float, dy: Float): GlassBox =
    if (isEmpty) this else copy(cx = cx + dx, cy = cy + dy)

private fun LiquidGlassGeometry.shifted(dx: Float, dy: Float): LiquidGlassGeometry =
    copy(a = a.shifted(dx, dy), b = b.shifted(dx, dy), zoomCenter = Offset(zoomCenter.x + dx, zoomCenter.y + dy))

/**
 * A card that pours out of its trigger as liquid using the menu liquid morph ([MenuLiquid]) and
 * renders inside [MenuLayerOverlay] on the same window as the page backdrop for live Kasane glass.
 * It sits at the bottom of the screen on top of the keyboard, rising with the IME frame by frame;
 * back, tapping outside, and [onDismissRequest] close it while keeping content composed until home.
 *
 * Wrap the trigger in `Modifier.menuMorphSource(source)` and pass the same [source] here.
 */
@Composable
fun LiquidCard(
    visible: Boolean,
    source: MenuMorphSource,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = TsuzukiTheme.motion
    val colors = TsuzukiTheme.colors
    val glass = LocalGlassContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val liquid = remember { MenuLiquid(scope) }
    var mounted by remember { mutableStateOf(false) }
    var pourStarted by remember { mutableStateOf(false) }
    var cardOnScreen by remember { mutableStateOf(Rect.Zero) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    // N9: the resting bottom is decided, never ridden. Open aims at the IME's published target so
    // the pour goes straight to the place above the rising keyboard; close freezes where the card
    // sits and drains into the trigger while the keyboard drops under it. −1 = not frozen.
    var frozenImeBottom by remember { mutableStateOf(-1) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val restingPx = liquidRestingBottom(imeBottom, imeTargetBottom, navBottom, frozenImeBottom)
    val restingRef = remember { mutableIntStateOf(-1) }
    SideEffect { restingRef.intValue = restingPx }

    fun sagPx(): Float = cardOnScreen.height * (if (source.boundsOnScreen.center.y > cardOnScreen.center.y) -1f else 1f)

    DisposableEffect(source) {
        onDispose {
            if (source.covered) source.reveal(wobble = false)
        }
    }

    LaunchedEffect(visible, motion.reduced) {
        if (visible) {
            mounted = true
            frozenImeBottom = -1
            if (motion.reduced) {
                pourStarted = true
                source.hide()
                liquid.morph.snapTo(1f)
                liquid.content.snapTo(1f)
                liquid.fade.snapTo(1f)
                return@LaunchedEffect
            }
            val fromRest = !liquid.inFlight
            if (fromRest) {
                snapshotFlow { cardOnScreen }.first { it != Rect.Zero }
                withFrameNanos { }
                // One movement each way (N9): wait briefly for the keyboard to say where it is
                // going; the pour then lands at the final place above it as it rises.
                if (imeTargetBottom <= 0) {
                    withTimeoutOrNull(LiquidImeWaitMillis) {
                        snapshotFlow { restingRef.intValue }.first { it > navBottom }
                    }
                }
            }
            pourStarted = true
            val sincePress = SystemClock.uptimeMillis() - source.pressedAtMillis
            val lead = (MenuLiquidMotion.PressLeadMillis - sincePress).coerceIn(0L, MenuLiquidMotion.PressLeadMillis)
            liquid.open(fromRest = fromRest, leadMillis = lead, sagPx = sagPx()) { source.hide() }
        } else if (mounted) {
            frozenImeBottom = restingRef.intValue.coerceAtLeast(0)
            if (motion.reduced) {
                source.reveal(wobble = false)
                mounted = false
                pourStarted = false
                cardOnScreen = Rect.Zero
            } else if (liquid.inFlight) {
                liquid.close(
                    sagPx(),
                    landDropPx = with(density) { MenuLiquidMotion.LandDrop.toPx() },
                    onReveal = { source.reveal(wobble = false) },
                    onLanded = {
                        source.reveal(wobble = false)
                        mounted = false
                        pourStarted = false
                        cardOnScreen = Rect.Zero
                    },
                    borderless = source.borderless,
                )
            } else {
                liquid.cancelPour()
                source.reveal(wobble = false)
                mounted = false
                pourStarted = false
                cardOnScreen = Rect.Zero
            }
        }
    }
    if (!mounted) return

    val radius = TsuzukiCorners.group
    val restBottom = with(density) { restingPx.toDp() }
    fun triggerInCard(): Rect {
        val raw = source.boundsOnScreen
        return if (raw != Rect.Zero) {
            raw.translate(-cardOnScreen.left, -cardOnScreen.top)
        } else {
            Rect(
                cardOnScreen.width / 2f - 24f,
                cardOnScreen.height / 2f - 24f,
                cardOnScreen.width / 2f + 24f,
                cardOnScreen.height / 2f + 24f,
            )
        }
    }

    fun frame(): MenuLiquidFrame = with(density) {
        menuLiquidFrame(
            trigger = triggerInCard(),
            panel = Size(cardOnScreen.width, cardOnScreen.height),
            menuRadius = radius.toPx(),
            morph = liquid.morph.value,
            drop = liquid.drop.value,
            corner = liquid.corner.value,
            presence = liquid.presence.value,
            absorb = liquid.absorb.value,
            content = liquid.content.value,
            label = liquid.label.value,
            press = source.press.value,
            pressGrowPx = MenuLiquidMotion.PressGrow.toPx(),
            mergePx = MenuLiquidMotion.MergeSmoothness.toPx(),
            borderless = source.borderless,
        )
    }

    fun hostGlass(f: MenuLiquidFrame): LiquidGlassGeometry =
        f.glass.shifted(cardOnScreen.left - hostOrigin.x, cardOnScreen.top - hostOrigin.y)

    MenuLayerOverlay(onDismissRequest = onDismissRequest) {
        CompositionLocalProvider(LocalInsideGlass provides false) {
            val view = LocalView.current
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            val tintStrength = LocalGlassTintStrength.current
            val backdrop = glass?.backdrop
            val useKasane = glass?.enabled == true &&
                LocalLiquidGlass.current &&
                Build.VERSION.SDK_INT >= 33 &&
                backdrop != null &&
                glass.sourceView === view &&
                glass.haze.areas.isNotEmpty()
            val fallback = if (glass?.enabled == true) {
                colors.elevated.copy(alpha = LocalGlassFallbackAlpha.current ?: GlassMaterial.Thick.fallbackAlpha)
            } else {
                colors.elevated
            }

            // The keyboard leaves with the liquid, not after the overlay unmounts.
            LaunchedEffect(visible) {
                if (!visible) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    (view.parent as? DialogWindowProvider)?.window?.let { window ->
                        androidx.core.view.WindowCompat.getInsetsController(window, view)
                            .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { hostOrigin = it.positionOnScreen() },
            ) {
                // Scrim follows the liquid content fade.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = when {
                                !pourStarted -> 0f
                                motion.reduced -> 1f
                                else -> liquid.content.value.coerceIn(0f, 1f)
                            }
                        }
                        .background(Color.Black.copy(alpha = LiquidScrimAlpha))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            focus.clearFocus()
                            onDismissRequest()
                        },
                )

                // Soft ambient shadow under the morphing bubble + card in host coordinates.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (!pourStarted || cardOnScreen == Rect.Zero) return@drawBehind
                            val fadeAlpha = liquid.fade.value
                            if (fadeAlpha <= 0.001f) return@drawBehind
                            val g = if (motion.reduced) {
                                LiquidGlassGeometry(
                                    GlassBox.Empty,
                                    GlassBox.of(cardOnScreen.translate(-hostOrigin.x, -hostOrigin.y), radius.toPx()),
                                    0f,
                                )
                            } else {
                                hostGlass(frame())
                            }
                            fun drawBoxShadow(box: GlassBox) {
                                if (box.isEmpty) return
                                val r = box.radius.coerceIn(0f, min(box.halfW, box.halfH))
                                for (i in 1..4) {
                                    val expand = i * 4.dp.toPx()
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.045f * fadeAlpha),
                                        topLeft = Offset(box.left - expand / 2f, box.top - expand / 2f + expand * 0.35f),
                                        size = Size(box.halfW * 2f + expand, box.halfH * 2f + expand),
                                        cornerRadius = CornerRadius(r + expand / 2f),
                                    )
                                }
                            }
                            drawBoxShadow(g.a)
                            drawBoxShadow(g.b)
                        },
                )

                // Full-host Liquid Glass surface so the drop moving toward the trigger is never clipped to cardRect.
                if (useKasane && backdrop != null) {
                    val kasaneTint = colors.glassTint.copy(
                        alpha = GlassMaterial.Thick.kasaneTintAlpha(colors.isDark, tintStrength),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                layerBlock = {
                                    alpha = if (!pourStarted || cardOnScreen == Rect.Zero) 0f else liquid.fade.value
                                },
                                effects = {
                                    if (Build.VERSION.SDK_INT >= 33 && cardOnScreen != Rect.Zero) {
                                        val g = if (motion.reduced) {
                                            LiquidGlassGeometry(
                                                GlassBox.Empty,
                                                GlassBox.of(cardOnScreen.translate(-hostOrigin.x, -hostOrigin.y), radius.toPx()),
                                                0f,
                                            )
                                        } else {
                                            hostGlass(frame())
                                        }
                                        liquidGlass(
                                            g,
                                            dark = colors.isDark,
                                            tint = kasaneTint,
                                            page = colors.background,
                                            highlightStrength = if (colors.isDark) 0.35f else 0.6f,
                                            blurRadius = CardFrost,
                                        )
                                    }
                                },
                                highlight = null,
                                shadow = null,
                            ),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (!pourStarted || cardOnScreen == Rect.Zero) 0f else liquid.fade.value
                                if (pourStarted && cardOnScreen != Rect.Zero) {
                                    val g = if (motion.reduced) {
                                        LiquidGlassGeometry(
                                            GlassBox.Empty,
                                            GlassBox.of(cardOnScreen.translate(-hostOrigin.x, -hostOrigin.y), radius.toPx()),
                                            0f,
                                        )
                                    } else {
                                        hostGlass(frame())
                                    }
                                    shape = LiquidGlassShape(g)
                                    clip = true
                                }
                            }
                            .background(fallback),
                    )
                }

                // Content stays composed (alpha = 0 while waiting briefly for initial IME target) and
                // is clipped to the full-host liquid outline once the pour starts.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (!pourStarted) 0f else liquid.fade.value
                            if (pourStarted && cardOnScreen != Rect.Zero) {
                                val g = if (motion.reduced) {
                                    LiquidGlassGeometry(
                                        GlassBox.Empty,
                                        GlassBox.of(cardOnScreen.translate(-hostOrigin.x, -hostOrigin.y), radius.toPx()),
                                        0f,
                                    )
                                } else {
                                    hostGlass(frame())
                                }
                                shape = LiquidGlassShape(g)
                                clip = true
                            }
                        },
                ) {
                    Box(
                        modifier
                            .align(Alignment.BottomCenter)
                            // N9: the bottom is decided (IME target / frozen), never ridden frame by frame.
                            .padding(bottom = restBottom)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .onGloballyPositioned {
                                val p = it.positionOnScreen()
                                cardOnScreen = Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height)
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (!motion.reduced && pourStarted && cardOnScreen != Rect.Zero) {
                                        val f = frame()
                                        scaleX = f.contentScale
                                        scaleY = f.contentScale
                                        translationX = f.contentOffset.x
                                        translationY = f.contentOffset.y
                                        alpha = f.contentAlpha
                                        val blur = f.contentBlur * MenuLiquidMotion.ContentMaxBlur.toPx()
                                        renderEffect = if (blur >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            BlurEffect(blur, blur, TileMode.Decal)
                                        } else {
                                            null
                                        }
                                    }
                                },
                            content = content,
                        )
                    }

                    val snapshot = source.snapshot
                    if (!motion.reduced && snapshot != null && source.boundsOnScreen != Rect.Zero) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (!pourStarted || cardOnScreen == Rect.Zero) {
                                        this.alpha = 0f
                                        return@graphicsLayer
                                    }
                                    val triggerInHost = source.boundsOnScreen.translate(-hostOrigin.x, -hostOrigin.y)
                                    val f = frame()
                                    this.alpha = f.labelAlpha
                                    val blur = f.labelBlur * MenuLiquidMotion.LabelMaxBlur.toPx()
                                    renderEffect = if (blur >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        BlurEffect(blur, blur, TileMode.Decal)
                                    } else {
                                        null
                                    }
                                    if (size.width > 0f && size.height > 0f) {
                                        transformOrigin = TransformOrigin(
                                            triggerInHost.center.x / size.width,
                                            triggerInHost.center.y / size.height,
                                        )
                                    }
                                    scaleX = f.labelScale
                                    scaleY = f.labelScale
                                }
                                .drawBehind {
                                    if (pourStarted && cardOnScreen != Rect.Zero) {
                                        val triggerInHost = source.boundsOnScreen.translate(-hostOrigin.x, -hostOrigin.y)
                                        translate(triggerInHost.left, triggerInHost.top) { drawLayer(snapshot) }
                                    }
                                },
                        )
                        if (!source.borderless) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        if (!pourStarted || cardOnScreen == Rect.Zero) return@drawBehind
                                        val f = frame()
                                        val g = hostGlass(f)
                                        if (f.brighten > 0.005f && !g.a.isEmpty) {
                                            val r = g.a.toRoundRect()
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = f.brighten),
                                                topLeft = Offset(r.left, r.top),
                                                size = Size(r.width, r.height),
                                                cornerRadius = r.topLeftCornerRadius,
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
