package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeroCardTest {

    private val screen = Size(1080f, 2400f)
    private val fullScreen = Rect(0f, 0f, screen.width, screen.height)
    private val tile = Rect(560f, 900f, 1020f, 1590f)

    // Apple header: the Details cover is larger than the grid tile, so the page's
    // own cover slot anchors the growing card.
    private val largeCover = Rect(240f, 300f, 840f, 1200f)

    // Current header: the Details cover is smaller than the tile, so the card grows
    // out of the tile itself while the cover flies to its slot inside the page.
    private val smallCover = Rect(372f, 420f, 708f, 924f)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.top, actual.top, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
        assertEquals(expected.bottom, actual.bottom, 0.5f)
    }

    private fun assertGrowsInsideScreen(tile: Rect, target: Rect?, fromCover: Boolean) {
        var previousArea = -1f
        var previousScale = 0f
        for (step in 0..10) {
            val p = step / 10f
            val card = heroCard(tile, target, screen, p)
            val bounds = card.bounds
            assertEquals(fromCover, card.fromCover)
            assertTrue(card.scale <= 1f + 1e-4f, "content scaled above 1 at p=$p")
            assertTrue(card.scale >= previousScale - 1e-4f, "scale shrank mid flight at p=$p")
            previousScale = card.scale
            assertTrue(bounds.left >= -0.5f && bounds.top >= -0.5f, "card exits the screen at p=$p: $bounds")
            assertTrue(bounds.right <= screen.width + 0.5f && bounds.bottom <= screen.height + 0.5f, "card exits the screen at p=$p: $bounds")
            val area = bounds.width * bounds.height
            assertTrue(area >= previousArea - 0.5f, "clip shrank mid flight at p=$p")
            previousArea = area
            if (!fromCover) {
                val cover = requireNotNull(card.cover)
                assertTrue(
                    cover.left >= bounds.left - 0.5f && cover.right <= bounds.right + 0.5f &&
                        cover.top >= bounds.top - 0.5f && cover.bottom <= bounds.bottom + 0.5f,
                    "flying cover escapes the growing card at p=$p",
                )
            }
        }
    }

    @Test
    fun `small cover header grows a card out of the tile without scaling content above one`() {
        val start = heroCard(tile, smallCover, screen, 0f)

        assertTrue(!start.fromCover, "small covers must grow the card from the tile, not anchor the page cover")
        assertTrue(start.scale <= 1f, "content must never start scaled above its laid-out size")
        assertRect(tile, start.bounds)
        assertRect(tile, requireNotNull(start.cover))

        val end = heroCard(tile, smallCover, screen, 1f)
        assertEquals(1f, end.scale, 1e-4f)
        assertRect(fullScreen, end.bounds)
        assertRect(smallCover, requireNotNull(end.cover))
    }

    @Test
    fun `large cover header anchors the card on the tile below one scale`() {
        val start = heroCard(tile, largeCover, screen, 0f)

        assertTrue(start.fromCover, "large covers grow the page out of the cover slot")
        assertTrue(start.scale <= 1f, "the card must start smaller than the page and grow")
        assertRect(tile, start.bounds)
        assertRect(tile, requireNotNull(start.cover))

        val end = heroCard(tile, largeCover, screen, 1f)
        assertEquals(1f, end.scale, 1e-4f)
        assertRect(fullScreen, end.bounds)
        assertRect(largeCover, requireNotNull(end.cover))
    }

    @Test
    fun `the card grows monotonically and stays inside the screen for both headers`() {
        assertGrowsInsideScreen(tile, smallCover, fromCover = false)
        assertGrowsInsideScreen(tile, largeCover, fromCover = true)
    }

    @Test
    fun `a tile shaped differently from the details cover starts as exactly the tile and lands in the slot`() {
        for (otherTile in listOf(Rect(560f, 900f, 1020f, 1360f), Rect(560f, 900f, 1020f, 1700f))) {
            val start = heroCard(otherTile, largeCover, screen, 0f)
            assertRect(otherTile, start.bounds)
            assertRect(otherTile, requireNotNull(start.cover))
            for (step in 0..10) {
                val card = heroCard(otherTile, largeCover, screen, step / 10f)
                val cover = requireNotNull(card.cover)
                val bounds = card.bounds
                assertTrue(
                    cover.left >= bounds.left - 0.5f && cover.right <= bounds.right + 0.5f &&
                        cover.top >= bounds.top - 0.5f && cover.bottom <= bounds.bottom + 0.5f,
                    "cover hangs outside the card at ${step / 10f} for $otherTile",
                )
            }
            val end = heroCard(otherTile, largeCover, screen, 1f)
            assertRect(fullScreen, end.bounds)
            assertRect(largeCover, requireNotNull(end.cover))
        }
    }

    @Test
    fun `the flying cover lands in the details slot exactly when the clip reaches the screen`() {
        for (step in 1..9) {
            val p = step / 10f
            val card = heroCard(tile, smallCover, screen, p)
            val cover = requireNotNull(card.cover)
            assertTrue(cover.left in minOf(tile.left, smallCover.left)..maxOf(tile.left, smallCover.left))
            assertTrue(cover.top in minOf(tile.top, smallCover.top)..maxOf(tile.top, smallCover.top))
        }
        assertRect(smallCover, requireNotNull(heroCard(tile, smallCover, screen, 1f).cover))
    }

    @Test
    fun `without a measured cover the page grows from the tile corner as before`() {
        val start = heroCard(tile, null, screen, 0f)

        assertTrue(start.fromCover)
        assertEquals(tile.width / screen.width, start.scale, 1e-4f)
        assertRect(tile, start.bounds)
        assertEquals(null, start.cover)

        val end = heroCard(tile, null, screen, 1f)
        assertEquals(1f, end.scale, 1e-4f)
        assertRect(fullScreen, end.bounds)
    }

    @Test
    fun `dragging back shrinks the page around its centre and carries it with the finger`() {
        val full = heroCard(tile, largeCover, screen, 1f)
        val finger = androidx.compose.ui.geometry.Offset(120f, 60f)
        val dragged = heroPose(full, 1f, finger)

        assertTrue(dragged.scale < full.scale, "the page must shrink while dragged")
        assertEquals(fullScreen.center.x + finger.x, dragged.bounds.center.x, 0.5f)
        assertEquals(fullScreen.center.y + finger.y, dragged.bounds.center.y, 0.5f)
        val cover = requireNotNull(dragged.cover)
        assertTrue(cover.width < largeCover.width, "the cover shrinks with the page")
        assertEquals(cover.width / cover.height, largeCover.width / largeCover.height, 1e-3f)
    }

    @Test
    fun `a cover scrolled off the top shrinks the page as it looks into the tile instead of anchoring on the hidden cover`() {
        val scrolledAway = Rect(240f, -2600f, 840f, -1700f)
        val landing = heroCard(tile, scrolledAway, screen, 0f)

        assertRect(tile, Rect(landing.bounds.left, landing.bounds.top, landing.bounds.right, landing.bounds.top + tile.height))
        assertEquals(tile.width / screen.width, landing.scale, 1e-4f)
        assertEquals(null, landing.cover)
    }

    @Test
    fun `the tile image hands over to the page in the first third of the flight and comes back for the last third`() {
        assertEquals(1f, flyingCoverAlpha(0f), 1e-4f)
        assertTrue(flyingCoverAlpha(0.15f) in 0.01f..0.99f)
        assertEquals(0f, flyingCoverAlpha(0.3f), 1e-4f)
        assertEquals(0f, flyingCoverAlpha(1f), 1e-4f)
    }
}
