package dev.errnolink.tsuzuki.ui.library

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ContinueCardLogicTest {

    @Test
    fun `continueCardWidthDp reserves next-card peek and clamps extremes`() {
        assertEquals(240f, continueCardWidthDp(screenWidthDp = 300f, gutterDp = 20f))
        assertEquals(304f, continueCardWidthDp(screenWidthDp = 380f, gutterDp = 20f))
        assertEquals(340f, continueCardWidthDp(screenWidthDp = 800f, gutterDp = 24f))
    }

    @Test
    fun `continueRelativeRecency formats recent timestamps and drops old ones`() {
        val now = 1_700_000_000_000L
        assertNull(continueRelativeRecency(null, now))
        assertNull(continueRelativeRecency(0L, now))
        assertEquals("Just now", continueRelativeRecency(now - 30_000L, now))
        assertEquals("12m ago", continueRelativeRecency(now - 12 * 60_000L, now))
        assertEquals("2h ago", continueRelativeRecency(now - 2 * 3_600_000L, now))
        assertEquals("Yesterday", continueRelativeRecency(now - 30 * 3_600_000L, now))
        assertEquals("3d ago", continueRelativeRecency(now - 3 * 86_400_000L, now))
        assertNull(continueRelativeRecency(now - 10 * 86_400_000L, now))
    }

    @Test
    fun `continueRelativeRecency formats using custom localized labels`() {
        val now = 1_700_000_000_000L
        val labels = ContinueRecencyLabels(
            justNow = "Maintenant",
            minutesAgo = { mins -> "il y a $mins min" },
            hoursAgo = { hrs -> "il y a $hrs h" },
            yesterday = "Hier",
            daysAgo = { days -> "il y a $days j" },
        )
        assertEquals("Maintenant", continueRelativeRecency(now - 20_000L, now, labels))
        assertEquals("il y a 5 min", continueRelativeRecency(now - 5 * 60_000L, now, labels))
        assertEquals("il y a 3 h", continueRelativeRecency(now - 3 * 3_600_000L, now, labels))
        assertEquals("Hier", continueRelativeRecency(now - 30 * 3_600_000L, now, labels))
        assertEquals("il y a 4 j", continueRelativeRecency(now - 4 * 86_400_000L, now, labels))
        assertNull(continueRelativeRecency(now - 10 * 86_400_000L, now, labels))
    }

    @Test
    fun `continueProgressPercent handles missing, fractional, and clamped values`() {
        assertNull(continueProgressPercent(null))
        assertNull(continueProgressPercent(Float.NaN))
        assertEquals(0, continueProgressPercent(0f))
        assertEquals(58, continueProgressPercent(14f / 24f))
        assertEquals(100, continueProgressPercent(1f))
        assertEquals(100, continueProgressPercent(1.4f))
    }

    @Test
    fun `continueCardAccent derives cover tint and pill foreground maintains contrast`() {
        val fallback = Color(0xFF2B6CE8)
        assertEquals(fallback, continueCardAccent(coverSeed = null, fallbackAccent = fallback, isDark = true))

        val darkAccent = continueCardAccent(coverSeed = Color(0xFF1B4F9C), fallbackAccent = fallback, isDark = true)
        assertNotEquals(fallback, darkAccent)

        assertEquals(Color(0xFF0B1220), continuePillOnAccent(Color(0xFF8FB6FF)))
        assertEquals(Color.White, continuePillOnAccent(Color(0xFF1A3A75)))
    }
}
