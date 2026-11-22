package dev.errnolink.tsuzuki.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The title zoom as pure geometry, held to the outside review's checklist (2026-11-19): the cover
 * is the one continuous object, the page is never a shrunk copy of itself, the card's clip grows
 * from the tile, and the page's content is gone early on the way home so only the cover lands.
 */
class ZoomGeometryTest {

    private val screen = Size(1080f, 2400f)
    private val fullScreen = Rect(0f, 0f, screen.width, screen.height)

    // Library tile cover and details cover sharing the same 2:3 artwork aspect.
    private val tile = Rect(560f, 900f, 1020f, 1590f)
    private val cover = Rect(240f, 300f, 840f, 1200f)

    private val tileRadius = 36f
    private val flightRadius = 90f
    private val screenRadius = 0f

    private fun pose(p: Float, follow: Offset = Offset.Zero) =
        zoomPose(tile, screen, p, tileRadius, screenRadius, flightRadius, follow)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.top, actual.top, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
        assertEquals(expected.bottom, actual.bottom, 0.5f)
    }

    @Test
    fun `frame zero is the tile, with the card and the flying cover exactly on it`() {
        val start = pose(0f)
        assertRect(tile, start.bounds)
        assertRect(tile, zoomCoverRect(tile, cover, 0f))
        assertEquals(tileRadius, start.corner, 1e-4f)
    }

    @Test
    fun `frame one is the page at rest with the cover on the page's own cover`() {
        val end = pose(1f)
        assertEquals(Offset.Zero, end.translation)
        assertRect(fullScreen, end.bounds)
        assertRect(cover, zoomCoverRect(tile, cover, 1f))
        assertEquals(screenRadius, end.corner, 1e-4f)
    }

    @Test
    fun `no frame shows a shrunken page`() {
        for (step in 0..20) {
            assertEquals(1f, pose(step / 20f).scale, 1e-6f, "page scaled at p=${step / 20f}")
        }
    }

    @Test
    fun `the card grows from the tile to the screen without closing again`() {
        var previous = pose(0f).bounds
        for (step in 1..20) {
            val frame = pose(step / 20f).bounds
            assertTrue(frame.width >= previous.width && frame.height >= previous.height, "card shrank at p=${step / 20f}")
            assertTrue(frame.left <= previous.left + 1e-3f && frame.top <= previous.top + 1e-3f, "card edge moved inward at p=${step / 20f}")
            previous = frame
        }
    }

    @Test
    fun `the cover moves on one path with no size jump at either end`() {
        val near0 = zoomCoverRect(tile, cover, 0.01f)
        val near1 = zoomCoverRect(tile, cover, 0.99f)
        assertEquals(tile.width, near0.width, tile.width * 0.02f)
        assertEquals(cover.width, near1.width, cover.width * 0.02f)
        val mid = zoomCoverRect(tile, cover, 0.5f)
        assertEquals((tile.center.x + cover.center.x) / 2f, mid.center.x, 0.5f)
        assertEquals((tile.center.y + cover.center.y) / 2f, mid.center.y, 0.5f)
    }

    @Test
    fun `spring overshoot past the ends holds the end poses`() {
        assertRect(fullScreen, pose(1.05f).bounds)
        assertRect(cover, zoomCoverRect(tile, cover, 1.05f))
        assertRect(tile, pose(-0.03f).bounds)
    }

    @Test
    fun `corners run tile then flight card then the screen's own corners`() {
        assertEquals(tileRadius, zoomCorner(tileRadius, screenRadius, flightRadius, 0f), 1e-4f)
        assertTrue(zoomCorner(tileRadius, screenRadius, flightRadius, 0.6f) >= flightRadius - 1e-4f)
        assertEquals(screenRadius, zoomCorner(tileRadius, screenRadius, flightRadius, 1f), 1e-4f)
    }

    @Test
    fun `the page's content is hidden at the start of an open, so only the cover reads as the tile`() {
        for (section in ZoomReveal.entries) {
            assertEquals(0f, zoomRevealAlpha(0.15f, section.start, section.end, closing = false), 1e-6f, "$section shows at p=0.15")
            assertEquals(1f, zoomRevealAlpha(1f, section.start, section.end, closing = false), 1e-6f, "$section missing at rest")
        }
        assertTrue(ZoomReveal.Header.start < ZoomReveal.Actions.start && ZoomReveal.Actions.start < ZoomReveal.Body.start)
    }

    @Test
    fun `on the way home the page's content is gone by the first 40 percent, so only the cover lands`() {
        for (section in ZoomReveal.entries) {
            assertEquals(1f, zoomRevealAlpha(1f, section.start, section.end, closing = true), 1e-6f)
            assertEquals(0f, zoomRevealAlpha(0.6f, section.start, section.end, closing = true), 1e-6f, "$section still visible at p=0.6 of a close")
        }
    }

    @Test
    fun `the tile caption fades out at the start of an open and back in at the end of a close`() {
        assertEquals(1f, zoomTileLabelAlpha(0f), 1e-6f)
        assertEquals(0f, zoomTileLabelAlpha(0.15f), 1e-6f)
        assertEquals(0f, zoomTileLabelAlpha(1f), 1e-6f)
        assertTrue(zoomTileLabelAlpha(0.05f) in 0.01f..0.99f)
    }

    @Test
    fun `the finger follow moves the page but frame zero still lands on the tile`() {
        val follow = Offset(24f, 40f)
        val followed = pose(1f, follow)
        assertEquals(24f, followed.translation.x, 1e-4f)
        assertEquals(40f, followed.translation.y, 1e-4f)
        assertRect(tile, pose(0f, follow).bounds)
        assertRect(tile, followed.mapRect(zoomCoverRect(tile, cover, 0f, follow)))
    }

    @Test
    fun `the predictive-back card shrinks to 86 percent about its centre and is device-round by a tenth of the gesture`() {
        val rest = pose(1f)
        assertEquals(rest.scale, zoomCardPose(rest, screen, 0f, flightRadius).scale, 1e-6f)
        val full = zoomCardPose(rest, screen, 1f, flightRadius)
        assertEquals(0.86f, full.scale, 1e-4f)
        val centre = Offset(screen.width / 2f, screen.height / 2f)
        assertEquals(rest.map(centre), full.map(centre), "the card must shrink about its own centre")
        assertEquals(flightRadius, zoomCardPose(rest, screen, 0.1f, flightRadius).corner, 1e-3f)
        var previous = rest.scale
        for (step in 1..9) {
            val scale = zoomCardPose(rest, screen, step / 10f, flightRadius).scale
            assertTrue(scale < previous, "card scale not monotonic at ${step / 10f}")
            previous = scale
        }
    }

    @Test
    fun `the scrim is a plain dim that follows progress and lifts to a tenth while the card is held`() {
        assertEquals(0f, HeroScrimAlpha * zoomBackdrop(0f), 1e-4f)
        assertEquals(0.25f, HeroScrimAlpha * zoomBackdrop(1f), 1e-4f)
        assertEquals(0.10f, HeroScrimAlpha * zoomBackdrop(1f, 1f), 1e-4f)
        var previous = 0f
        for (step in 0..20) {
            val level = zoomBackdrop(step / 20f)
            assertTrue(level >= previous - 1e-4f, "scrim not monotonic at ${step / 20f}")
            previous = level
        }
    }

    @Test
    fun `release commits past a quarter of the travel or on a flick, never on a slow short drag`() {
        assertTrue(zoomDismissCommits(0.25f, 0f))
        assertTrue(zoomDismissCommits(0.1f, 900f))
        assertFalse(zoomDismissCommits(0.2f, 100f))
    }

    @Test
    fun `the release speed becomes progress speed toward the tile`() {
        assertEquals(-2f, zoomReleaseVelocity(2000f, 1000f), 1e-5f)
        assertEquals(0f, zoomReleaseVelocity(2000f, 0f), 1e-5f)
    }

    @Test
    fun `the flight leaves from exactly the pose the gesture released`() {
        val card = 0.4f
        val follow = Offset(48f, 16f)
        val held = zoomCardPose(pose(1f, follow), screen, card, flightRadius)
        val firstFlightFrame = zoomCardPose(pose(0.96f, follow * 0.9f), screen, card * 0.9f, flightRadius)
        val centre = Offset(screen.width / 2f, screen.height / 2f)
        assertEquals(held.map(centre).x, firstFlightFrame.map(centre).x, screen.width * 0.05f)
        assertTrue(firstFlightFrame.bounds.width < held.bounds.width, "the flight must move toward the tile, not freeze")
    }

    @Test
    fun `the ghost card settles as the full page at rest and shrinks about its own centre`() {
        val corner = 90f
        val rest = zoomGhostPose(1f, screen, corner)
        assertEquals(1f, rest.scale, 1e-4f)
        assertEquals(0f, rest.corner, 1e-4f)
        val shrunk = zoomGhostPose(0f, screen, corner)
        assertEquals(ZoomGhostShrink, shrunk.scale, 1e-4f)
        assertEquals(corner, shrunk.corner, 1e-4f)
        val centre = Offset(screen.width / 2f, screen.height / 2f)
        val moved = shrunk.map(centre)
        assertEquals(centre.x, moved.x, 2f)
        assertEquals(centre.y, moved.y, 2f)
    }

    @Test
    fun `the ghost card is fully gone before the flight ends`() {
        assertEquals(0f, zoomGhostAlpha(0f), 1e-4f)
        assertEquals(0f, zoomGhostAlpha(0.12f), 1e-4f)
        assertEquals(1f, zoomGhostAlpha(0.45f), 1e-4f)
        assertEquals(1f, zoomGhostAlpha(1f), 1e-4f)
    }
}
