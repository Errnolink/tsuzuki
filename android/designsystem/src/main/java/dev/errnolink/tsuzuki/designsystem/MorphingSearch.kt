package dev.errnolink.tsuzuki.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/* Kasane search (kasane/src/js/components.js `search`, tab-bar.css `.is-searching`, Voice Memos
   bottom field): the search circle widens into the field. It stays a capsule the whole way; its
   edges travel on the critically damped navigation curve (iOS .smooth, response .42 s, no
   overshoot, no stretch), the magnifier rides at the field's leading edge, the trigger folds away,
   the placeholder fades in and the close scales in from .6. Close is the same motion reversed.
   The field itself is a glass capsule (the same material and rim as the bar and the circles); the
   shell hosts it at the bottom of the screen on tab roots (the floating tab bar's trailing circle
   pours into it while the bar fades away) and in the top row on pushed pages; the bottom row
   rides the keyboard on the IME insets. */

@Stable
class MorphingSearchState internal constructor() {
    var expanded by mutableStateOf(false)
        private set

    internal val progress = Animatable(0f, visibilityThreshold = 0.001f)

    /** Flight progress 0..1 (it overshoots past 1 while the field lands); safe to read in composition. */
    val flightProgress: Float
        get() = progress.value

    /** The trigger's cover/re-form lifecycle, shared with menus. */
    internal val source = MenuMorphSource()

    /** Search button bounds in window coordinates, captured by [MorphingSearchButton]. */
    internal var sourceBounds by mutableStateOf<Rect?>(null)

    fun expand() {
        expanded = true
    }

    fun collapse() {
        expanded = false
    }
}

@Composable
fun rememberMorphingSearchState(): MorphingSearchState = remember { MorphingSearchState() }

/* ---- flight geometry (pure; behaviour tests in MorphingSearchTest) ---- */

data class SearchMorphFrame(
    val rect: Rect,
    val cornerRadius: Float,
    /** Placeholder, text and trailing slot. */
    val contentAlpha: Float,
)

/**
 * Placeholder, text and trailing slot: they arrive over the second half of the opening and drain
 * across the close — later than the old first-fifth drain, so the field keeps its text while the
 * shape is still visibly there (MOT-05), and still leaves earlier than it arrives.
 */
fun searchContentAlpha(progress: Float, opening: Boolean): Float {
    val t = progress.coerceIn(0f, 1f)
    return if (opening) smoothstep(0.35f, 0.85f, t) else smoothstep(0.4f, 1f, t)
}

/**
 * One frame of the circle→field flight. Both edges travel together from the circle to the field;
 * the shape is always a capsule (the radius is half the interpolated height). The text fades in
 * over the second half of the opening and drains across the close.
 */
fun searchMorphFrame(progress: Float, opening: Boolean, from: Rect, to: Rect): SearchMorphFrame {
    val t = progress.coerceIn(0f, 1f)
    val rect = Rect(
        left = from.left + (to.left - from.left) * t,
        top = from.top + (to.top - from.top) * t,
        right = from.right + (to.right - from.right) * t,
        bottom = from.bottom + (to.bottom - from.bottom) * t,
    )
    return SearchMorphFrame(
        rect = rect,
        cornerRadius = minOf(rect.width, rect.height).coerceAtLeast(0f) / 2f,
        contentAlpha = searchContentAlpha(t, opening),
    )
}

internal fun searchFlightIconOffsetPx(
    fromWidthPx: Float,
    iconSizePx: Float,
    fieldLeadingStartPx: Float,
    progress: Float,
): Float {
    val t = progress.coerceIn(0f, 1f)
    val circleOffset = (fromWidthPx - iconSizePx) / 2f
    return circleOffset + (fieldLeadingStartPx - circleOffset) * t
}

enum class SearchCloseAction { Clear, Close }

/** Kasane's close button: clears the text when there is text, closes only when empty. */
fun searchCloseAction(hasText: Boolean): SearchCloseAction =
    if (hasText) SearchCloseAction.Clear else SearchCloseAction.Close

/* ---- pieces ---- */

/**
 * The search circle: the trigger that captures its window bounds as the flight origin, hides under
 * the flying drop, and re-forms out of it when the drain-back lands (focus returns with it). The
 * shell puts it in the tab bar's trailing slot, in the nav rail, and at the bottom trailing corner
 * of pushed pages.
 */
@Composable
fun MorphingSearchButton(
    state: MorphingSearchState,
    glass: GlassContext?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = TsuzukiTheme.colors
    val resolvedDescription = contentDescription ?: LocalTsuzukiComponentLabels.current.search
    val focusRequester = remember { FocusRequester() }
    val pressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    Box(
        modifier
            .onGloballyPositioned {
                if (!state.source.covered && state.progress.value <= 0.001f) {
                    state.sourceBounds = it.boundsInWindow()
                }
            }
            .menuMorphSource(state.source)
            .then(
                if (pressState.enabled) {
                    Modifier.pressShapeScale(pressState)
                } else {
                    Modifier.pressScale(interaction)
                },
            ),
    ) {
        GlassSurface(
            context = glass,
            shape = CircleShape,
            material = GlassMaterial.Thick,
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = resolvedDescription,
                        onClick = state::expand,
                    )
                    .semantics { this.contentDescription = resolvedDescription },
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiIcon(TsuzukiIcons.Search, null, Modifier.size(22.dp), tint = colors.text)
            }
        }
    }
    // Focus returns to the search button when the drop lands back (Kasane: focus on close).
    LaunchedEffect(state.source.reformTick) {
        if (state.source.reformTick > 0) focusRequester.requestFocus()
    }
}

/**
 * The flying field. The host positions it: [targetRect] is where the field lands, in the host's own
 * pixel coordinates; [hostOrigin] is the host's window position, used to map the trigger's bounds
 * into the same space. Drives the flight spring, the predictive-back scrub, the keyboard, and the
 * trigger's cover/re-form lifecycle. The top row used it between the actions and the title; the
 * shell's bottom dock uses it for the bottom field, where the close sits in its own glass circle
 * beside the field ([inlineClose] = false keeps the field's inline × out).
 */
@Composable
fun MorphingSearchOverlay(
    search: TopBarSearch,
    glass: GlassContext?,
    targetRect: Rect,
    hostOrigin: Offset,
    modifier: Modifier = Modifier,
    inlineClose: Boolean = true,
) {
    val state = search.state
    val reduced = TsuzukiTheme.motion.reduced
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }

    fun close() {
        keyboard?.hide()
        // Sites clear their query state (Kasane clears the input on search(false)); the collapse
        // then runs the drain-back flight from wherever it is.
        search.onClose?.invoke()
        state.collapse()
    }

    val opening = state.expanded
    val composing by remember { derivedStateOf { state.progress.value > 0.001f || state.source.covered } }

    // The flight: both ways on Kasane's critically damped navigation curve; reverses from wherever it is.
    // The circle is hidden while the capsule holds its place and comes back the moment it lands home.
    LaunchedEffect(state.expanded, reduced) {
        if (state.expanded) {
            // Kasane `is-searching`: the bar holds its full expanded shape while the field is up,
            // so the close flight returns the complete bar — labels included — from its first
            // frame (N17) instead of the collapsed icon-only circle.
            glass?.searchActive = true
            glass?.expandNavigation()
            state.source.hide()
            state.progress.animateTo(1f, if (reduced) snap() else KasaneSprings.searchFlight<Float>())
        } else {
            // Same spring both ways (review, beta 9: the open felt slow and the back close abrupt).
            // Critically damped, so the drain never dips below the circle's own frame.
            state.progress.animateTo(0f, if (reduced) snap() else KasaneSprings.searchFlight<Float>())
            state.source.reveal(wobble = false)
            glass?.searchActive = false
        }
    }
    DisposableEffect(state, glass) {
        onDispose {
            glass?.searchActive = false
            if (state.source.covered) state.source.reveal(wobble = false)
            keyboard?.hide()
        }
    }

    // Focus the field once the flight actually mounts it (MOT-05): caret and keyboard lead the
    // shape, and the field rides the IME's animated inset to its landing spot. Drop them on close.
    // Requesting before `composing` flips would hit an unattached FocusRequester and lose focus.
    LaunchedEffect(state.expanded, composing) {
        if (state.expanded && composing) {
            fieldFocus.requestFocus()
            keyboard?.show()
        } else if (!state.expanded) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Predictive back scrubs the flight home; a cancel opens it again from where it stopped.
    // Reduced motion skips the scrub but back still closes the field, not the screen.
    PredictiveBackHandler(enabled = state.expanded) { events ->
        if (reduced) {
            events.collect()
            close()
            return@PredictiveBackHandler
        }
        var sought = false
        try {
            events.collect { event ->
                sought = true
                state.progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
        } catch (e: CancellationException) {
            if (sought) state.progress.animateTo(1f, KasaneSprings.searchFlight<Float>())
            throw e
        }
        close()
    }

    val from = state.sourceBounds?.let { bounds ->
        Rect(
            bounds.left - hostOrigin.x,
            bounds.top - hostOrigin.y,
            bounds.right - hostOrigin.x,
            bounds.bottom - hostOrigin.y,
        )
    } ?: return
    // WP6.3: composition only gates on the flight threshold; the frame itself is computed in the
    // layout/draw phases from the progress read there, so the flight never recomposes per frame.
    // The capsule stays until the trigger is revealed: dropping it at the threshold while the
    // trigger was still covered left one or more frames with no circle at all (beta 9 flicker).
    if (!composing) return

    val targetWidth = targetRect.width.roundToInt().coerceAtLeast(1)
    // The capsule stays a pill (half its minimum side) on every drawn frame.
    // The host hands us its full area; the capsule is placed inside it at the frame's rect. (Sizing the
    // overlay itself to the rect made Compose centre it inside the host's fixed constraints.)
    Box(modifier) {
    GlassSurface(
        context = glass,
        shape = SearchFlightShape,
        material = GlassMaterial.Thick,
        modifier = Modifier
            .layout { measurable, _ ->
                val frame = searchMorphFrame(state.progress.value, opening, from, targetRect)
                val w = frame.rect.width.roundToInt().coerceAtLeast(1)
                val h = frame.rect.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) {
                    placeable.place(frame.rect.left.roundToInt(), frame.rect.top.roundToInt())
                }
            },
    ) {
        // The magnifier rides the leading edge the whole way; the field's own icon takes over as the
        // text arrives.
        TsuzukiIcon(
            TsuzukiIcons.Search,
            null,
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = searchFlightIconOffsetPx(
                        fromWidthPx = from.width,
                        iconSizePx = 22.dp.toPx(),
                        fieldLeadingStartPx = 14.dp.toPx(),
                        progress = state.progress.value,
                    )
                    alpha = 1f - searchContentAlpha(state.progress.value, opening)
                }
                .size(22.dp),
            tint = TsuzukiTheme.colors.text,
        )
        // The field is laid out once at its landed width and revealed by the growing capsule, so
        // nothing inside reflows while it flies.
        Box(
            Modifier
                .graphicsLayer { alpha = searchContentAlpha(state.progress.value, opening) }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(targetWidth, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(IntOffset.Zero) }
                },
        ) {
            GlassSearchField(
                search = search,
                // Composed only while the capsule is visible (the gate above), so the text stays
                // live while the IME hides on close — disabling it greys the draining field (MOT-05).
                enabled = true,
                focusRequester = fieldFocus,
                close = if (inlineClose) ({ close() }) else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

/** The flying capsule's outline; computes capsule corner radius directly from the drawn size. */
private object SearchFlightShape : RoundedRectShape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(minOf(size.width, size.height).coerceAtLeast(0f) / 2f),
            ),
        )
}

/**
 * The landed field's content: glass-native (the flying [GlassSurface] is the field; no Material
 * filled surface). Text and placeholder colours come from roles. The inline × follows Kasane's
 * close rule when [close] is set (clears with text, closes when empty); with no [close] it only
 * clears. Echoes of the field's own edits are dropped so a trailing caller value never moves the
 * cursor; outside changes (a programmatic clear, a restore) replace the draft.
 */
@Composable
private fun GlassSearchField(
    search: TopBarSearch,
    enabled: Boolean,
    focusRequester: FocusRequester,
    close: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SearchFieldCore(
        query = search.query,
        onQueryChange = search.onQueryChange,
        onSearch = search.onSearch,
        placeholder = search.placeholder,
        enabled = enabled,
        iconTint = TsuzukiTheme.colors.secondary,
        modifier = modifier.padding(start = 14.dp, end = 6.dp),
        focusRequester = focusRequester,
        onClose = close,
        fieldPadding = PaddingValues(0.dp),
        trailing = search.trailing,
        trailingAfterClear = false,
    )
}
