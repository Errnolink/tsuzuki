package dev.errnolink.tsuzuki.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.animation.TimeInterpolator
import android.widget.ImageView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.geometry.Rect
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.isNightMode
import dev.errnolink.tsuzuki.ui.motion.MangaHero
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

data class ReaderPose(
    val clip: Rect,
    val radius: Float,
    val scale: Float,
    val contentAlpha: Float,
    val scrimAlpha: Float,
    val veilAlpha: Float,
    val contentBlur: Float,
)

/**
 * The Continue-to-reader container transform. The origin (a button or a cover) grows into the
 * screen filled with the reader's canvas colour — the same colour the page will sit on, so the
 * open is one movement with no dark-to-white cut; the reader arrives blurred inside that colour
 * and sharpens as it lands. Closing is the same path in reverse, so the reader returns into the
 * button it came from.
 */
fun readerPose(origin: Rect, full: Rect, progress: Float, cornerPx: Float): ReaderPose {
    val p = progress.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a + (b - a) * p
    val reveal = smoothstep(ContentFadeStart, ContentFadeEnd, p)
    return ReaderPose(
        clip = Rect(lerp(origin.left, full.left), lerp(origin.top, full.top), lerp(origin.right, full.right), lerp(origin.bottom, full.bottom)),
        radius = cornerPx * (1f - (p / CornerSettle).coerceIn(0f, 1f)),
        scale = lerp(StartScale, 1f),
        contentAlpha = reveal,
        scrimAlpha = ScrimPeak * (p / ScrimFadeEnd).coerceIn(0f, 1f),
        veilAlpha = smoothstep(0f, VeilFadeInEnd, p) * (1f - smoothstep(VeilFadeStart, VeilFadeEnd, p)),
        contentBlur = 1f - smoothstep(ContentFadeStart, BlurClearEnd, p),
    )
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val StartScale = 0.92f
private const val CornerSettle = 0.85f
private const val ContentFadeStart = 0.14f
private const val ContentFadeEnd = 0.45f
private const val VeilFadeInEnd = 0.06f
private const val VeilFadeStart = 0.45f
private const val VeilFadeEnd = 0.72f
private const val BlurClearEnd = 0.95f
private const val BlurMaxDp = 24f
private const val ScrimFadeEnd = 0.35f
private const val ScrimPeak = 0.42f
private const val OpenMillis = 380L
private const val CloseMillis = 340L
private const val SpringOmega = 7.8f

fun criticalSpring(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    fun raw(v: Float) = 1f - (1f + SpringOmega * v) * kotlin.math.exp(-SpringOmega * v)
    return raw(x) / raw(1f)
}

/**
 * A predictive back drag maps into the open's progress in reverse: the whole gesture brings the
 * reader [BackScrubSpan] of the way home (a card clearly on its way back, not a sliver at the
 * button); committing continues the same flight from wherever the finger left it.
 */
fun readerBackPose(backProgress: Float): Float = 1f - BackScrubSpan * backProgress.coerceIn(0f, 1f)

private const val BackScrubSpan = 0.55f

private val CriticalSpring = TimeInterpolator { criticalSpring(it) }
private const val CornerDp = 18f
/** The veil fill: the reader's canvas colour, resolved once like the loading surface and the page. */
private fun canvasVeil(context: Context): Int =
    readerCanvasArgb(
        Injekt.get<ReaderPreferences>().readerTheme().get(),
        context.isNightMode(),
    )

private const val SnapshotTimeoutMillis = 80L
private const val EXTRA_TRANSITION = "dev.errnolink.tsuzuki.reader.transition"

object ReaderTransition {
    internal class Entry(val snapshot: Bitmap, val origin: Rect, val width: Int, val height: Int)

    private var pending: Entry? = null

    fun open(context: Context, intent: Intent) {
        val activity = context.findActivity()
        val origin = ReaderOrigins.pick(MangaHero.recentTouch())
        val decor = activity?.window?.decorView
        if (activity == null || origin == null || decor == null || decor.width <= 0 || decor.height <= 0 || reduced(activity)) {
            context.startActivity(intent)
            return
        }
        val snapshot = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        var launched = false
        fun launch(ok: Boolean) {
            if (launched) return
            launched = true
            if (ok) {
                pending = Entry(snapshot, origin, decor.width, decor.height)
                intent.putExtra(EXTRA_TRANSITION, true)
                activity.startActivity(intent, ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
            } else {
                snapshot.recycle()
                activity.startActivity(intent)
            }
        }
        try {
            PixelCopy.request(activity.window, snapshot, { result -> launch(result == PixelCopy.SUCCESS) }, handler)
            handler.postDelayed({ launch(false) }, SnapshotTimeoutMillis)
        } catch (e: IllegalArgumentException) {
            launch(false)
        }
    }

    fun take(activity: Activity): Stage? {
        if (!activity.intent.getBooleanExtra(EXTRA_TRANSITION, false)) return null
        activity.intent.removeExtra(EXTRA_TRANSITION)
        val entry = pending ?: return null
        pending = null
        if (reduced(activity)) return null
        return Stage(activity, entry)
    }

    private fun reduced(context: Context): Boolean =
        android.animation.ValueAnimator.areAnimatorsEnabled().not() ||
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f

    class Stage internal constructor(private val activity: Activity, private val entry: Entry) {
        private val host: ViewGroup = activity.findViewById(android.R.id.content)
        private val cornerPx = CornerDp * activity.resources.displayMetrics.density
        private val veilColor: Int = canvasVeil(activity)
        private var content: View? = null
        private var backdrop: ImageView? = null
        private var scrim: View? = null
        private var veil: View? = null
        private var pose = readerPose(entry.origin, Rect(0f, 0f, entry.width.toFloat(), entry.height.toFloat()), 0f, cornerPx)
        private var progress = 0f
        private var flight: ValueAnimator? = null
        private var exiting = false
        val isExiting: Boolean get() = exiting

        fun enter(target: View) {
            content = target
            attach()
            apply(0f)
            target.doOnPreDraw {
                target.postOnAnimation {
                    if (!exiting && content === target) {
                        run(0f, 1f, OpenMillis) { detach() }
                    }
                }
            }
        }

        fun canExit(): Boolean = !exiting && content != null && host.width == entry.width && host.height == entry.height

        /**
         * A predictive back drag scrubs the close: the reader shrinks toward its origin with the
         * title page (the open's snapshot backdrop) visible behind, from wherever it currently is.
         */
        fun beginScrub() {
            if (!canExit()) return
            flight?.cancel()
            attach()
        }

        fun scrubTo(target: Float) {
            if (exiting || content == null) return
            flight?.cancel()
            apply(target)
        }

        /** The gesture was cancelled: spring back to the resting reader from the released pose. */
        fun releaseScrub() {
            if (exiting || content == null) return
            run(progress, 1f, CloseMillis) { detach() }
        }

        fun exit(done: () -> Unit) {
            exiting = true
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            attach()
            run(progress, 0f, CloseMillis, done)
        }

        private fun attach() {
            val view = content ?: return
            if (backdrop == null) {
                backdrop = ImageView(activity).apply {
                    setImageBitmap(entry.snapshot)
                    scaleType = ImageView.ScaleType.FIT_XY
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                scrim = View(activity).apply {
                    setBackgroundColor(Color.BLACK)
                    alpha = 0f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                veil = View(activity).apply {
                    setBackgroundColor(veilColor)
                    elevation = 8f * activity.resources.displayMetrics.density
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, outline: Outline) {
                            val c = pose.clip
                            outline.setRoundRect(c.left.roundToInt(), c.top.roundToInt(), c.right.roundToInt(), c.bottom.roundToInt(), pose.radius)
                        }
                    }
                    clipToOutline = true
                }
                val match = ViewGroup.LayoutParams.MATCH_PARENT
                host.addView(backdrop, 0, ViewGroup.LayoutParams(match, match))
                host.addView(scrim, 1, ViewGroup.LayoutParams(match, match))
                host.addView(veil, ViewGroup.LayoutParams(match, match))
            }
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    val s = pose.scale
                    val px = v.pivotX
                    val py = v.pivotY
                    val c = pose.clip
                    outline.setRoundRect(
                        ((c.left - px) / s + px).roundToInt(),
                        ((c.top - py) / s + py).roundToInt(),
                        ((c.right - px) / s + px).roundToInt(),
                        ((c.bottom - py) / s + py).roundToInt(),
                        pose.radius / s,
                    )
                }
            }
            view.clipToOutline = true
            view.pivotX = entry.origin.center.x
            view.pivotY = entry.origin.center.y
        }

        private fun detach() {
            val view = content ?: return
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
            backdrop?.let(host::removeView)
            scrim?.let(host::removeView)
            veil?.let(host::removeView)
            backdrop = null
            scrim = null
            veil = null
        }

        private fun apply(target: Float) {
            val view = content ?: return
            progress = target
            pose = readerPose(entry.origin, Rect(0f, 0f, entry.width.toFloat(), entry.height.toFloat()), target, cornerPx)
            val depthFraction = (pose.scrimAlpha / ScrimPeak).coerceIn(0f, 1f)
            val backdropScale = 1f - 0.035f * depthFraction
            backdrop?.scaleX = backdropScale
            backdrop?.scaleY = backdropScale
            view.scaleX = pose.scale
            view.scaleY = pose.scale
            view.alpha = pose.contentAlpha
            scrim?.alpha = pose.scrimAlpha
            veil?.alpha = pose.veilAlpha
            veil?.invalidateOutline()
            view.invalidateOutline()
        }

        private fun run(from: Float, to: Float, millis: Long, end: () -> Unit) {
            flight?.cancel()
            ValueAnimator.ofFloat(from, to).apply {
                duration = millis
                interpolator = CriticalSpring
                addUpdateListener { apply(it.animatedValue as Float) }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }
                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) end()
                    }
                })
                start()
                flight = this
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
