package dev.errnolink.tsuzuki.designsystem

import android.os.VibrationEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeGateway(
    override val sdkInt: Int,
    override val available: Boolean = true,
    override val amplitudeControl: Boolean = false,
    private val primitives: Boolean = false,
) : VibratorGateway {
    var vibrateCalls = 0
    var lastPattern: RenderedPattern? = null
    var failWithSecurity = false

    override fun primitivesSupported(vararg primitiveIds: Int): Boolean =
        primitives && primitiveIds.all { id ->
            id == VibrationEffect.Composition.PRIMITIVE_TICK || id == VibrationEffect.Composition.PRIMITIVE_CLICK
        }

    override fun vibrate(pattern: RenderedPattern) {
        if (failWithSecurity) throw SecurityException("blocked")
        vibrateCalls++
        lastPattern = pattern
    }
}

private fun alwaysGate(enabled: Boolean) = HapticGate { enabled }

class HapticsTest {

    @Test
    fun `every pattern is a two beat figure with ordered strengths and short gaps`() {
        for (haptic in TsuzukiHaptic.entries) {
            val beats = rhythmOf(haptic)
            assertEquals(2, beats.size, "$haptic")
            assertTrue(beats[0].gapAfterMs in 14..20, "$haptic gap")
            assertEquals(0, beats[1].gapAfterMs, "$haptic trailing gap")
            assertTrue(beats[0].strength in 0.3f..0.9f, "$haptic lead strength")
        }
        assertTrue(rhythmOf(TsuzukiHaptic.ToggleOn)[1].strength > rhythmOf(TsuzukiHaptic.Select)[1].strength)
        assertTrue(rhythmOf(TsuzukiHaptic.ChapterArm)[1].strength == 1.0f)
        assertTrue(rhythmOf(TsuzukiHaptic.ToggleOff)[0].strength > rhythmOf(TsuzukiHaptic.ToggleOff)[1].strength)
        assertTrue(rhythmOf(TsuzukiHaptic.ToggleOn)[1].strength > rhythmOf(TsuzukiHaptic.ToggleOn)[0].strength)
    }

    @Test
    fun `toggle off is the mirror of toggle on`() {
        val on = rhythmOf(TsuzukiHaptic.ToggleOn)
        val off = rhythmOf(TsuzukiHaptic.ToggleOff)
        assertEquals(on[1].kind, off[0].kind)
        assertEquals(on[1].strength, off[0].strength)
        assertEquals(on[0].kind, off[1].kind)
        assertEquals(on[0].strength, off[1].strength)
        assertEquals(on[0].gapAfterMs, off[0].gapAfterMs)
    }

    @Test
    fun `patterns stay under a tap length on the amplitude tier`() {
        for (haptic in TsuzukiHaptic.entries) {
            val rendered = amplitudeWaveform(rhythmOf(haptic))
            val total = rendered.timingsMs.sum()
            assertTrue(total < 45L, "$haptic total $total")
            assertEquals(rendered.timingsMs.size, rendered.amplitudes!!.size)
        }
    }

    @Test
    fun `amplitude tier alternates silence and clamped pulse amplitudes`() {
        val rendered = amplitudeWaveform(rhythmOf(TsuzukiHaptic.Select))
        assertEquals(listOf(0L, PulseKind.Tick.milliseconds, 18L, PulseKind.Press.milliseconds), rendered.timingsMs)
        assertEquals(
            listOf(0, (PulseKind.Tick.nominalAmplitude * 0.45f).toInt(), 0, (PulseKind.Press.nominalAmplitude * 0.75f).toInt()),
            rendered.amplitudes,
        )
    }

    @Test
    fun `coarse tier floors gaps and stretches pulses within bounds`() {
        for (haptic in TsuzukiHaptic.entries) {
            val rendered = coarseWaveform(rhythmOf(haptic))
            assertEquals(null, rendered.amplitudes)
            val timings = rendered.timingsMs
            assertEquals(0L, timings[0])
            for (i in 1 until timings.size) {
                if (i % 2 == 0) {
                    assertTrue(timings[i] >= 18L, "$haptic gap at $i")
                } else {
                    assertTrue(timings[i] in 16L..36L, "$haptic pulse at $i")
                }
            }
        }
    }

    @Test
    fun `coarse tier keeps two beats and reduces longer figures to their outer beats`() {
        val two = rhythmOf(TsuzukiHaptic.Select)
        assertEquals(
            listOf(0L, coarseStretch(two[0]), 18L, coarseStretch(two[1])),
            coarseWaveform(two).timingsMs,
        )

        val three = listOf(
            Pulse(PulseKind.Tick, 0.5f, 10),
            Pulse(PulseKind.Press, 0.8f, 10),
            Pulse(PulseKind.Thump, 1f, 0),
        )
        val reduced = coarseWaveform(three)
        assertEquals(listOf(0L, coarseStretch(three[0]), 18L, coarseStretch(three[2])), reduced.timingsMs)
    }

    @Test
    fun `composition tier maps kinds to primitives with cumulative delays`() {
        val beats = rhythmOf(TsuzukiHaptic.ChapterArm)
        val rendered = renderPrimitiveComposition(beats, lowTickSupported = true, thudSupported = true)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, rendered[0].primitiveId)
        assertEquals(0, rendered[0].delayMs)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, rendered[1].primitiveId)
        assertEquals(beats[0].gapAfterMs, rendered[1].delayMs)
        assertEquals(beats[0].strength, rendered[0].scale)

        val pageTurn = renderPrimitiveComposition(rhythmOf(TsuzukiHaptic.PageTurn), true, false)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, pageTurn[0].primitiveId)

        val fallback = renderPrimitiveComposition(rhythmOf(TsuzukiHaptic.PageTurn), false, false)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, fallback[0].primitiveId)

        val thudFallback = renderPrimitiveComposition(rhythmOf(TsuzukiHaptic.ChapterArm), false, false)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, thudFallback[1].primitiveId)
    }

    @Test
    fun `tier resolution follows sdk and capability`() {
        assertEquals(HapticTier.OneShot, resolveTier(25, true, false, false))
        assertEquals(HapticTier.AmplitudeWaveform, resolveTier(26, true, true, true))
        assertEquals(HapticTier.CoarseWaveform, resolveTier(26, true, false, true))
        assertEquals(HapticTier.AmplitudeWaveform, resolveTier(30, true, true, true))
        assertEquals(HapticTier.Composition, resolveTier(31, true, true, true))
        assertEquals(HapticTier.OneShot, resolveTier(34, false, true, true))
    }

    @Test
    fun `second play of the same pattern reuses the compiled rendering`() {
        val gateway = FakeGateway(sdkInt = 34, amplitudeControl = true, primitives = true)
        val haptics = Haptics(gateway, alwaysGate(true))
        haptics.select()
        haptics.select()
        haptics.toggleOn()
        assertEquals(3, gateway.vibrateCalls)
        assertEquals(2, haptics.renderCount)
    }

    @Test
    fun `security exception disables the engine for the process`() {
        val gateway = FakeGateway(sdkInt = 34, amplitudeControl = true, primitives = true)
        gateway.failWithSecurity = true
        val haptics = Haptics(gateway, alwaysGate(true))
        haptics.pageTurn()
        assertTrue(haptics.isDisabled)
        gateway.failWithSecurity = false
        haptics.pageTurn()
        haptics.select()
        assertEquals(0, gateway.vibrateCalls)
    }

    @Test
    fun `system gate off silences play and unset default plays`() {
        val off = FakeGateway(sdkInt = 34, amplitudeControl = true)
        Haptics(off, alwaysGate(false)).select()
        assertEquals(0, off.vibrateCalls)

        val on = FakeGateway(sdkInt = 34, amplitudeControl = true)
        Haptics(on, alwaysGate(true)).toggleOff()
        assertEquals(1, on.vibrateCalls)
    }

    @Test
    fun `device without vibrator is a silent no-op engine`() {
        val gateway = FakeGateway(sdkInt = 34, available = false, amplitudeControl = false)
        val haptics = Haptics(gateway, alwaysGate(true))
        assertFalse(haptics.isAvailable)
        haptics.select()
        haptics.chapterArm()
        assertEquals(0, gateway.vibrateCalls)
        assertEquals(0, haptics.renderCount)
    }

    @Test
    fun `conveniences route to their patterns`() {
        val gateway = FakeGateway(sdkInt = 34, amplitudeControl = true)
        val haptics = Haptics(gateway, alwaysGate(true))
        haptics.select()
        haptics.toggleOn()
        haptics.toggleOff()
        haptics.pageTurn()
        haptics.chapterArm()
        assertEquals(5, gateway.vibrateCalls)
        assertTrue(gateway.lastPattern is RenderedPattern.Waveform)
    }
}
