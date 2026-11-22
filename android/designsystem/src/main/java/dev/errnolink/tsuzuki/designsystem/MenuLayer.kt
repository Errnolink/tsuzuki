package dev.errnolink.tsuzuki.designsystem

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Menus drawn in the app's own window, above every screen, instead of in a popup window. A popup is
 * a separate window, so the Kasane glass (which reads the window's recorded page) cannot see what
 * lies under it; a menu in this layer refracts the page like the tab bar does.
 */
@Stable
internal class MenuLayerState(val view: android.view.View) {
    val entries = mutableStateListOf<MenuLayerEntry>()
    var hostCoordinates: LayoutCoordinates? = null
}

internal val LocalMenuLayer = staticCompositionLocalOf<MenuLayerState?> { null }

internal class MenuLayerEntry(
    locals: CompositionLocalContext,
    content: @Composable () -> Unit,
) {
    var locals by mutableStateOf(locals)
    var content by mutableStateOf(content)
}

/** Hosts the in-window menus. Installed once by the outermost [TsuzukiTheme]; nested themes reuse it. */
@Composable
internal fun MenuLayerHost(content: @Composable () -> Unit) {
    if (LocalMenuLayer.current != null) {
        content()
        return
    }
    val view = LocalView.current
    val state = remember(view) { MenuLayerState(view) }
    Box(Modifier.onGloballyPositioned { state.hostCoordinates = it }, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalMenuLayer provides state, content = content)
        state.entries.forEach { entry ->
            key(entry) {
                CompositionLocalProvider(entry.locals) { entry.content() }
            }
        }
    }
}

/**
 * A [Popup] that lives in the window's menu layer when one is reachable from this window, and a
 * real popup window otherwise (inside dialogs and sheets, which are their own windows).
 */
@Composable
internal fun MenuLayerPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    val layer = LocalMenuLayer.current
    val view = LocalView.current
    if (layer == null || layer.view !== view) {
        Popup(popupPositionProvider, onDismissRequest, properties, content)
        return
    }
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
            val host = layer.hostCoordinates ?: return@onGloballyPositioned
            val topLeft = host.localPositionOf(parent, Offset.Zero).round()
            anchor = IntRect(topLeft, IntSize(parent.size.width, parent.size.height))
        },
    ) { _, _ -> layout(0, 0) {} }
    val locals = currentCompositionLocalContext
    val dismiss by rememberUpdatedState(onDismissRequest)
    val provider by rememberUpdatedState(popupPositionProvider)
    val props by rememberUpdatedState(properties)
    val body by rememberUpdatedState(content)
    val frame: @Composable () -> Unit = remember {
        {
            MenuLayerFrame(
                anchor = { anchor },
                positionProvider = provider,
                properties = props,
                onDismissRequest = { dismiss() },
                content = body,
            )
        }
    }
    val entry = remember(layer) { MenuLayerEntry(locals, frame) }
    entry.locals = locals
    DisposableEffect(layer, entry) {
        layer.entries.add(entry)
        onDispose { layer.entries.remove(entry) }
    }
}

@Composable
private fun MenuLayerFrame(
    anchor: () -> IntRect?,
    positionProvider: PopupPositionProvider,
    properties: PopupProperties,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    BackHandler(enabled = properties.dismissOnBackPress, onBack = onDismissRequest)
    if (properties.focusable || properties.dismissOnClickOutside) {
        // Outside the panel: a tap closes the menu and goes no further, as with a focusable popup.
        Layout(
            content = {},
            modifier = Modifier.pointerInput(properties.dismissOnClickOutside) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (properties.dismissOnClickOutside) onDismissRequest()
                }
            },
        ) { _, constraints -> layout(constraints.maxWidth, constraints.maxHeight) {} }
    }
    // A popup window hands its provider the visible display frame's size (the window minus the
    // system bars) with the anchor in window coordinates; the layer does the same, so every menu
    // lands where it did as a popup.
    val bars = WindowInsets.systemBars
    Layout(content = content) { measurables, constraints ->
        val topPx = bars.getTop(this)
        val bottomPx = bars.getBottom(this)
        val window = IntSize(constraints.maxWidth, (constraints.maxHeight - bottomPx).coerceAtLeast(0))
        (positionProvider as? PullDownMenuPositionProvider)?.safeTopPx = topPx
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        val size = IntSize(placeables.maxOfOrNull { it.width } ?: 0, placeables.maxOfOrNull { it.height } ?: 0)
        val rect = anchor()
        val position = if (rect != null) {
            positionProvider.calculatePosition(rect, window, layoutDirection, size)
        } else {
            IntOffset.Zero
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (rect != null) placeables.forEach { it.place(position) }
        }
    }
}

/**
 * Full-window overlay inside [MenuLayerHost] so liquid cards (Notes) render over the screen in
 * the same window as the page backdrop and refract live content through Kasane Liquid Glass.
 * Falls back to a transparent [Dialog] when hosted outside a [MenuLayerHost] window.
 */
@Composable
internal fun MenuLayerOverlay(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit,
) {
    val layer = LocalMenuLayer.current
    val view = LocalView.current
    if (layer == null || layer.view !== view) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val dialogView = LocalView.current
            LaunchedEffect(dialogView) {
                (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                    window.setDimAmount(0f)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
        return
    }
    val locals = currentCompositionLocalContext
    val dismiss by rememberUpdatedState(onDismissRequest)
    val backEnabled by rememberUpdatedState(dismissOnBackPress)
    val body by rememberUpdatedState(content)
    val frame: @Composable () -> Unit = remember {
        {
            BackHandler(enabled = backEnabled, onBack = { dismiss() })
            Box(Modifier.fillMaxSize()) { body() }
        }
    }
    val entry = remember(layer) { MenuLayerEntry(locals, frame) }
    entry.locals = locals
    DisposableEffect(layer, entry) {
        layer.entries.add(entry)
        onDispose { layer.entries.remove(entry) }
    }
}
