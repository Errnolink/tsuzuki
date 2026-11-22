package dev.errnolink.tsuzuki.ui.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeroPredictiveBackTest {

    @Test
    fun `missing target on scrolled page hides phantom cover while dragging and reveals late on close`() {
        // While dragging the scrolled page (progress=1.0), no target exists -> cover stays hidden (0f).
        assertEquals(0f, heroCoverAlpha(hasTarget = false, progress = 1f), 0.001f)
        // Once released and closing past mid-flight toward the library slot, it fades cleanly in.
        assertTrue(heroCoverAlpha(hasTarget = false, progress = 0.5f) in 0.1f..0.9f)
        assertEquals(1f, heroCoverAlpha(hasTarget = false, progress = 0.2f), 0.001f)
        // When a visible target exists on the page, alpha holds at 1 throughout.
        assertEquals(1f, heroCoverAlpha(hasTarget = true, progress = 1f), 0.001f)
    }

    @Test
    fun `predictive back drag fades detached top chrome early before card shrinks away`() {
        assertEquals(1f, heroChromeProgress(progress = 1f, cardT = 0f, inFlight = true), 0.001f)
        assertTrue(heroChromeProgress(progress = 1f, cardT = 0.18f, inFlight = true) in 0.1f..0.8f)
        assertEquals(0f, heroChromeProgress(progress = 1f, cardT = 0.35f, inFlight = true), 0.001f)
    }
}
