package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The app's one spring vocabulary (02-MOTION-SPEC §2). Every named spring lives here — Kasane's
 * response/damping conversions, the M3 spatial pair, and the physical TsuzukiMotion trio — so call
 * sites never define their own. Values are frozen at each spring's historical numbers; changing a
 * feel means changing it here, once.
 */
object KasaneSprings {
    /** lens.js FAST — M3E FastSpatial (k 800, ζ .6): presses and thumb moves, one visible bounce. */
    const val FastSpatialDamping = 0.6f
    const val FastSpatialStiffness = 801f

    /** Kasane `.bouncy` — control selection moves (the segmented lens). Response 0.42. */
    const val BouncyDamping = 0.68f
    const val BouncyStiffness = 224f

    /** Kasane `.snap` — back to rest after a cancelled gesture. Response 0.32. */
    const val SnapDamping = 0.86f
    const val SnapStiffness = 386f

    /** Kasane `.nav` — bar folds and the accessory move: critically damped, response 0.42. */
    const val NavDamping = 1f
    const val NavStiffness = 224f

    /** Kasane loader morph — 200 / 0.6, the M3E shape spring (may overshoot). */
    const val MorphDamping = 0.6f
    const val MorphStiffness = 987f

    /** Kasane nav `.nav` for bar folds; reduced motion snaps. */
    fun <T> nav(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = NavDamping, stiffness = NavStiffness)

    fun <T> fastSpatial(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = FastSpatialDamping, stiffness = FastSpatialStiffness)

    fun <T> bouncy(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = BouncyDamping, stiffness = BouncyStiffness)

    fun <T> settle(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = SnapDamping, stiffness = SnapStiffness)

    /** M3 spatial fast (k 700, ζ 0.9): presses, toggles, thumb moves, menu close. */
    fun <T> spatialFast(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 700f)

    /** M3 spatial soft (k 380, ζ 0.9): menus, morphs, search, selection bar. */
    fun <T> spatialSoft(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 380f)

    /** The press shape's release: the spatial-fast numbers under a name that says what it is for. */
    fun <T> pressRelease(reduced: Boolean): FiniteAnimationSpec<T> = spatialFast(reduced)

    /** Effects never overshoot: a Short4 Standard tween that rides spring-driven geometry. */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(M3Motion.Short4, easing = M3Motion.Standard)

    /**
     * TsuzukiMotion's physical springs, kept at their k/c/mass numbers: press is stiffness 420,
     * damping 34, mass 0.7 (k 600, ζ ≈ 0.991 — critically damped); tab 400/38 (ζ 0.95);
     * sheet 500/45 (ζ ≈ 1.006).
     */
    fun <T> press(): SpringSpec<T> = physicalSpring(420f, 34f, 0.7f)

    fun <T> tab(): SpringSpec<T> = physicalSpring(400f, 38f)

    fun <T> sheet(): SpringSpec<T> = physicalSpring(500f, 45f)

    /** Library filter menu rows (former MenuRowSpring): k 400 (Spring.StiffnessMediumLow), ζ 0.9. */
    fun <T> menuRow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 400f)

    /** Details hero glow pop (former GlowPopSpec): k 250, ζ 0.85. */
    fun <T> glowPop(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 250f)

    /** Post-merge menu wobble (former WobbleSpring): k 900, ζ 0.5 — one bulge that rings out. */
    fun <T> wobble(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 900f)

    /** Selection-mode check morph (former SelectionCheckSpring): k 816 (response 0.22 s), ζ 1. */
    fun <T> selectionCheck(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 816f)

    /** Morphing search flight (former SearchFlightSpring): k 380, ζ 1 — never dips past either end. */
    fun <T> searchFlight(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 380f)

    /**
     * Kasane's SwiftUI-style springs (response in seconds, damping ratio) as Compose stiffness:
     * k = (2π / response)².
     */
    fun kasaneStiffness(responseSeconds: Float): Float =
        (2.0 * PI / responseSeconds.toDouble()).pow(2.0).toFloat()

    /**
     * The title zoom (outside review 2026-11-19, from Apple's duration/bounce springs): no bounce,
     * one progress value for every layer. Snappy 0.36 s open and 0.30 s close.
     */
    fun <T> zoomOpen(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = kasaneStiffness(0.36f))

    /** Close from the back button: 0.30 s, no bounce. */
    fun <T> zoomBack(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = kasaneStiffness(0.30f))

    /** Close after a committed swipe: 0.28 s with a touch of bounce (0.1), carrying the finger's speed. */
    fun <T> zoomCommit(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = kasaneStiffness(0.28f))

    /** A cancelled swipe snaps back: 0.26 s, no bounce. */
    fun <T> zoomSnap(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = kasaneStiffness(0.26f))

    @Composable
    fun <T> bouncy(): FiniteAnimationSpec<T> = bouncy(TsuzukiTheme.motion.reduced)

    private fun <T> physicalSpring(stiffness: Float, damping: Float, mass: Float = 1f): SpringSpec<T> =
        spring(stiffness = stiffness / mass, dampingRatio = damping / (2f * sqrt(stiffness * mass)))
}
