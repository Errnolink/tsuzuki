package dev.errnolink.tsuzuki.designsystem

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class TsuzukiHaptic {
    Select,
    ToggleOn,
    ToggleOff,
    PageTurn,
    ChapterArm,
}

internal enum class PulseKind(val milliseconds: Long, val nominalAmplitude: Int) {
    Tick(7L, 95),
    Flick(10L, 130),
    Press(13L, 205),
    Thump(16L, 255),
}

internal data class Pulse(val kind: PulseKind, val strength: Float, val gapAfterMs: Int)

internal fun rhythmOf(haptic: TsuzukiHaptic): List<Pulse> = when (haptic) {
    TsuzukiHaptic.Select -> listOf(
        Pulse(PulseKind.Tick, 0.45f, 18),
        Pulse(PulseKind.Press, 0.75f, 0),
    )
    TsuzukiHaptic.ToggleOn -> listOf(
        Pulse(PulseKind.Tick, 0.40f, 16),
        Pulse(PulseKind.Thump, 0.85f, 0),
    )
    TsuzukiHaptic.ToggleOff -> listOf(
        Pulse(PulseKind.Thump, 0.85f, 16),
        Pulse(PulseKind.Tick, 0.40f, 0),
    )
    TsuzukiHaptic.PageTurn -> listOf(
        Pulse(PulseKind.Flick, 0.50f, 14),
        Pulse(PulseKind.Flick, 0.65f, 0),
    )
    TsuzukiHaptic.ChapterArm -> listOf(
        Pulse(PulseKind.Tick, 0.55f, 20),
        Pulse(PulseKind.Thump, 1.00f, 0),
    )
}

internal data class PrimitivePulse(val primitiveId: Int, val scale: Float, val delayMs: Int)

internal sealed interface RenderedPattern {
    data class Composition(val primitives: List<PrimitivePulse>) : RenderedPattern
    data class Waveform(val timingsMs: List<Long>, val amplitudes: List<Int>?) : RenderedPattern
    data class OneShot(val milliseconds: Long) : RenderedPattern
}

internal fun renderPrimitiveComposition(
    beats: List<Pulse>,
    lowTickSupported: Boolean,
    thudSupported: Boolean,
): List<PrimitivePulse> {
    var delay = 0
    return beats.map { beat ->
        val id = when (beat.kind) {
            PulseKind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
            PulseKind.Flick -> if (lowTickSupported) {
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            } else {
                VibrationEffect.Composition.PRIMITIVE_TICK
            }
            PulseKind.Press -> VibrationEffect.Composition.PRIMITIVE_CLICK
            PulseKind.Thump -> if (thudSupported) {
                VibrationEffect.Composition.PRIMITIVE_THUD
            } else {
                VibrationEffect.Composition.PRIMITIVE_CLICK
            }
        }
        val spec = PrimitivePulse(id, beat.strength.coerceIn(0f, 1f), delay)
        delay += beat.gapAfterMs
        spec
    }
}

internal fun amplitudeWaveform(beats: List<Pulse>): RenderedPattern.Waveform {
    val timings = mutableListOf(0L)
    val amplitudes = mutableListOf(0)
    beats.forEach { beat ->
        timings += beat.kind.milliseconds
        amplitudes += (beat.kind.nominalAmplitude * beat.strength).toInt().coerceIn(1, 255)
        if (beat.gapAfterMs > 0) {
            timings += beat.gapAfterMs.toLong()
            amplitudes += 0
        }
    }
    return RenderedPattern.Waveform(timings, amplitudes)
}

internal fun coarseStretch(beat: Pulse): Long =
    (beat.kind.milliseconds * 2.2f * (0.7f + 0.3f * beat.strength)).toInt().coerceIn(16, 36).toLong()

internal fun coarseWaveform(beats: List<Pulse>): RenderedPattern.Waveform {
    val rendered = if (beats.size > 2) listOf(beats.first(), beats.last()) else beats
    val timings = mutableListOf(0L)
    rendered.forEachIndexed { index, beat ->
        if (index > 0) timings += maxOf(18L, rendered[index - 1].gapAfterMs.toLong())
        timings += coarseStretch(beat)
    }
    return RenderedPattern.Waveform(timings, null)
}

internal fun oneShot(beats: List<Pulse>): RenderedPattern.OneShot =
    RenderedPattern.OneShot(coarseStretch(beats.first()))

internal enum class HapticTier { Composition, AmplitudeWaveform, CoarseWaveform, OneShot }

internal fun resolveTier(
    sdkInt: Int,
    available: Boolean,
    amplitudeControl: Boolean,
    primitivesSupported: Boolean,
): HapticTier = when {
    !available -> HapticTier.OneShot
    sdkInt >= 31 && primitivesSupported -> HapticTier.Composition
    sdkInt >= 26 && amplitudeControl -> HapticTier.AmplitudeWaveform
    sdkInt >= 26 -> HapticTier.CoarseWaveform
    else -> HapticTier.OneShot
}

internal fun renderForTier(haptic: TsuzukiHaptic, tier: HapticTier, lowTick: Boolean, thud: Boolean): RenderedPattern {
    val beats = rhythmOf(haptic)
    return when (tier) {
        HapticTier.Composition -> RenderedPattern.Composition(renderPrimitiveComposition(beats, lowTick, thud))
        HapticTier.AmplitudeWaveform -> amplitudeWaveform(beats)
        HapticTier.CoarseWaveform -> coarseWaveform(beats)
        HapticTier.OneShot -> oneShot(beats)
    }
}

internal class RenderCache {
    var compileCount = 0
        private set
    private val rendered = ConcurrentHashMap<TsuzukiHaptic, RenderedPattern>()

    fun getOrRender(haptic: TsuzukiHaptic, tier: HapticTier, lowTick: Boolean, thud: Boolean): RenderedPattern =
        rendered.computeIfAbsent(haptic) {
            compileCount++
            renderForTier(haptic, tier, lowTick, thud)
        }
}

internal fun interface HapticGate {
    fun enabled(): Boolean
}

internal class SystemHapticGate(context: Context) : HapticGate {
    private val resolver = context.contentResolver
    @Volatile
    private var cachedEnabled = readSetting()

    init {
        try {
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED),
                false,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        cachedEnabled = readSetting()
                    }
                },
            )
        } catch (e: Exception) {
            // Ignore registration errors on custom environments
        }
    }

    private fun readSetting(): Boolean = try {
        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (e: Exception) {
        true
    }

    override fun enabled(): Boolean = cachedEnabled
}

internal interface VibratorGateway {
    val sdkInt: Int
    val available: Boolean
    val amplitudeControl: Boolean

    fun primitivesSupported(vararg primitiveIds: Int): Boolean

    fun vibrate(pattern: RenderedPattern)
}

private class AndroidVibratorGateway(context: Context) : VibratorGateway {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val legacyAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override val sdkInt: Int = Build.VERSION.SDK_INT

    override val available: Boolean
        get() = vibrator?.hasVibrator() == true

    override val amplitudeControl: Boolean
        get() = vibrator?.hasAmplitudeControl() == true

    override fun primitivesSupported(vararg primitiveIds: Int): Boolean =
        vibrator?.areAllPrimitivesSupported(*primitiveIds) == true

    override fun vibrate(pattern: RenderedPattern) {
        val vibrator = vibrator ?: return
        val effect = when (pattern) {
            is RenderedPattern.Composition -> {
                val composition = VibrationEffect.startComposition()
                pattern.primitives.forEach { composition.addPrimitive(it.primitiveId, it.scale, it.delayMs) }
                composition.compose()
            }
            is RenderedPattern.Waveform -> pattern.amplitudes
                ?.let { VibrationEffect.createWaveform(pattern.timingsMs.toLongArray(), it.toIntArray(), -1) }
                ?: VibrationEffect.createWaveform(pattern.timingsMs.toLongArray(), -1)
            is RenderedPattern.OneShot -> VibrationEffect.createOneShot(pattern.milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, legacyAttributes)
        }
    }
}

class Haptics internal constructor(
    private val gateway: VibratorGateway,
    private val gate: HapticGate,
) {
    private val disabled = AtomicBoolean(false)
    private val primitiveProbeAllowed = gateway.sdkInt >= 31
    private val primitivesSupported = primitiveProbeAllowed &&
        gateway.primitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
        )
    private val lowTick = primitiveProbeAllowed && gateway.primitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
    private val thud = primitiveProbeAllowed && gateway.primitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
    private val tier = resolveTier(
        sdkInt = gateway.sdkInt,
        available = gateway.available,
        amplitudeControl = gateway.amplitudeControl,
        primitivesSupported = primitivesSupported,
    )
    private val cache = RenderCache()

    internal val isAvailable: Boolean
        get() = gateway.available

    fun play(haptic: TsuzukiHaptic) {
        if (disabled.get()) return
        if (!gate.enabled()) return
        if (!gateway.available) return
        val pattern = cache.getOrRender(haptic, tier, lowTick, thud)
        try {
            gateway.vibrate(pattern)
        } catch (e: SecurityException) {
            disabled.set(true)
        }
    }

    fun select() = play(TsuzukiHaptic.Select)

    fun toggleOn() = play(TsuzukiHaptic.ToggleOn)

    fun toggleOff() = play(TsuzukiHaptic.ToggleOff)

    fun pageTurn() = play(TsuzukiHaptic.PageTurn)

    fun chapterArm() = play(TsuzukiHaptic.ChapterArm)

    internal val renderCount: Int
        get() = cache.compileCount

    internal val resolvedTier: HapticTier
        get() = tier

    internal val isDisabled: Boolean
        get() = disabled.get()

    companion object {
        fun of(context: Context): Haptics = Haptics(AndroidVibratorGateway(context), SystemHapticGate(context))
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics.of(context) }
}
