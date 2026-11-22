package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** TOP-02: the large title collapses over a 48 dp scroll range and never at rest. */
class LargeTitleCollapseTest {

    @Test
    fun `an unscrolled page shows the large title whatever its height`() {
        assertEquals(0f, largeTitleCollapseFraction(titleOffsetPx = 0f, titleSizePx = 56f, collapsedAtPx = 24f, rangePx = 48f))
        assertEquals(0f, largeTitleCollapseFraction(titleOffsetPx = 0f, titleSizePx = 200f, collapsedAtPx = 24f, rangePx = 48f))
    }

    @Test
    fun `a short title collapses over the first 48 dp of scroll`() {
        assertEquals(0.5f, largeTitleCollapseFraction(-24f, 56f, 24f, 48f), 0.001f)
        assertEquals(1f, largeTitleCollapseFraction(-48f, 56f, 24f, 48f))
    }

    @Test
    fun `a tall title collapses as its last 72 dp pass and ends 24 dp early`() {
        assertEquals(0f, largeTitleCollapseFraction(-128f, 200f, 24f, 48f))
        assertEquals(0.5f, largeTitleCollapseFraction(-152f, 200f, 24f, 48f), 0.001f)
        assertEquals(1f, largeTitleCollapseFraction(-176f, 200f, 24f, 48f))
    }

    @Test
    fun `a title no longer visible is fully collapsed`() {
        assertEquals(1f, largeTitleCollapseFraction(titleOffsetPx = null, titleSizePx = 0f, collapsedAtPx = 24f, rangePx = 48f))
    }
}
