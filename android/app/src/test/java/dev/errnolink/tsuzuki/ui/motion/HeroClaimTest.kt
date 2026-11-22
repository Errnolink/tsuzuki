package dev.errnolink.tsuzuki.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * N13: every cover row or tile opens by zooming out of its cover, whichever part of the row was
 * tapped — the claim picks the cover containing the touch (smallest when covers nest), else the
 * cover nearest to it. Pure geometry over the registered bounds.
 */
class HeroClaimTest {

    private val shelfTile = Rect(100f, 200f, 220f, 380f)
    private val neighbouringTile = Rect(500f, 200f, 620f, 380f)
    private val listRowCover = Rect(0f, 1000f, 96f, 1240f)

    @Test
    fun `a tap on the cover claims that cover`() {
        assertEquals(shelfTile, pickClaimBounds(listOf(shelfTile, neighbouringTile), Offset(160f, 300f)))
    }

    @Test
    fun `a tap on the row's title still claims the row's own cover, not a distant one`() {
        // The title sits right of the list row's cover; the nearest cover is that row's.
        assertEquals(listRowCover, pickClaimBounds(listOf(shelfTile, listRowCover), Offset(480f, 1120f)))
    }

    @Test
    fun `a tap on a shelf tile's caption below it claims that tile`() {
        assertEquals(shelfTile, pickClaimBounds(listOf(shelfTile, neighbouringTile), Offset(150f, 410f)))
    }

    @Test
    fun `nested covers claim the smallest one containing the touch`() {
        val big = Rect(0f, 0f, 1000f, 2000f)
        val nested = Rect(100f, 100f, 400f, 700f)
        assertEquals(nested, pickClaimBounds(listOf(big, nested), Offset(200f, 300f)))
    }

    @Test
    fun `unregistered rows claim nothing and keep the push`() {
        assertNull(pickClaimBounds(emptyList(), Offset(10f, 10f)))
        assertNull(pickClaimBounds(listOf(Rect.Zero), Offset(10f, 10f)))
    }
}
