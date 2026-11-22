package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassTintPresetTest {

    @Test
    fun `presets map cleanly to nearest bucket and custom slider values summarize accurately`() {
        assertEquals(0, GlassTintPreset.nearestIndex(0f))
        assertEquals(0, GlassTintPreset.nearestIndex(0.18f))
        assertEquals(1, GlassTintPreset.nearestIndex(0.45f))
        assertEquals(2, GlassTintPreset.nearestIndex(0.75f))
        assertEquals(3, GlassTintPreset.nearestIndex(1f))

        assertEquals("Clear · 0%", GlassTintPreset.summaryLabel(0f))
        assertEquals("Tinted · 45%", GlassTintPreset.summaryLabel(GlassTintPreset.Tinted.strength))
        assertEquals("Frosted · 75%", GlassTintPreset.summaryLabel(GlassTintPreset.Frosted.strength))
        assertEquals("Custom · 31%", GlassTintPreset.summaryLabel(0.31f))
        assertEquals("Opaque", GlassTintPreset.summaryLabel(1f))
    }

    @Test
    fun `muted liquid glass default gives stronger contrast tint than clear`() {
        val clearDark = GlassMaterial.Thick.kasaneTintAlpha(dark = true, strength = GlassTintPreset.Clear.strength)
        val tintedDark = GlassMaterial.Thick.kasaneTintAlpha(dark = true, strength = GlassTintPreset.Tinted.strength)
        val frostedDark = GlassMaterial.Thick.kasaneTintAlpha(dark = true, strength = GlassTintPreset.Frosted.strength)
        assertTrue(tintedDark > clearDark, "tinted=$tintedDark clear=$clearDark")
        assertTrue(frostedDark > tintedDark, "frosted=$frostedDark tinted=$tintedDark")
    }
}
