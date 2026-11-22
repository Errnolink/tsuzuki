package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Kasane button behaviour: the shape ladder (shape tells you state), the squeeze ceiling and the
 * loading phases. Radius and scale relations, not raw constants.
 */
class KasaneButtonBehaviourTest {

    private val sizes = TsuzukiButtonSize.entries.toList()

    @Test
    fun `rest shape is a capsule of the ladder height`() {
        for (size in sizes) {
            assertEquals(size.height / 2, buttonRestRadius(size, square = false, selected = false))
        }
    }

    @Test
    fun `square rest is tighter than its capsule`() {
        for (size in sizes) {
            assertTrue(
                buttonRestRadius(size, square = true, selected = false) < size.height / 2,
                "square rest ${size.name}",
            )
        }
    }

    @Test
    fun `selection flips the rest shape - round goes squarer, square goes round`() {
        for (size in sizes) {
            val round = buttonRestRadius(size, square = false, selected = true)
            assertEquals(size.squareRadius, round)
            assertTrue(round < size.height / 2)
            val square = buttonRestRadius(size, square = true, selected = true)
            assertEquals(size.height / 2, square)
            assertTrue(square > size.squareRadius)
        }
    }

    @Test
    fun `holding tightens the corners monotonically to the pressed radius`() {
        for (size in sizes) {
            val rest = buttonCornerRadius(size, square = false, selected = false, pressFraction = 0f).value
            var step = 0.1f
            var previous = rest
            while (step <= 1.0001f) {
                val radius = buttonCornerRadius(size, square = false, selected = false, step).value
                assertTrue(radius < previous, "corners must tighten, ${size.name} at $step")
                previous = radius
                step += 0.1f
            }
            assertEquals(size.pressedRadius.value, previous, 0.001f)
        }
    }

    @Test
    fun `pressed wins over selected`() {
        for (size in sizes) {
            val pressedSelected = buttonCornerRadius(size, square = false, selected = true, pressFraction = 1f)
            assertEquals(size.pressedRadius, pressedSelected)
            assertEquals(buttonCornerRadius(size, false, false, 1f), pressedSelected)
        }
    }

    @Test
    fun `press fraction at rest leaves the selected shape untouched`() {
        for (size in sizes) {
            assertEquals(
                buttonRestRadius(size, square = true, selected = true),
                buttonCornerRadius(size, square = true, selected = true, pressFraction = 0f),
            )
        }
    }

    @Test
    fun `the body never squeezes past its ceiling`() {
        for (size in sizes) {
            var f = 0f
            while (f <= 1.0001f) {
                val scale = buttonPressScale(size, f)
                assertTrue(scale >= size.squeeze, "below squeeze at $f for ${size.name}")
                assertTrue(scale <= 1f, "above rest at $f for ${size.name}")
                f += 0.05f
            }
            assertEquals(size.squeeze, buttonPressScale(size, 1f))
            assertEquals(1f, buttonPressScale(size, 0f))
        }
    }

    @Test
    fun `a springy release overshoot never scales past the squeeze`() {
        for (size in sizes) {
            assertEquals(size.squeeze, buttonPressScale(size, 1.4f), "overshoot clamped ${size.name}")
            assertEquals(size.squeeze, buttonPressScale(size, 2f))
            assertEquals(1f, buttonPressScale(size, -0.3f), "undershoot clamps to rest ${size.name}")
        }
    }

    // loading phases

    @Test
    fun `busy shows the spinner before anything else`() {
        assertEquals(
            LoadingButtonPhase.Spinner,
            loadingButtonPhase(busy = true, result = LoadingButtonResult.None),
        )
        assertEquals(
            LoadingButtonPhase.Spinner,
            loadingButtonPhase(busy = true, result = LoadingButtonResult.Success),
        )
    }

    @Test
    fun `success shows the check only after the busy state clears`() {
        assertEquals(
            LoadingButtonPhase.Check,
            loadingButtonPhase(busy = false, result = LoadingButtonResult.Success),
        )
    }

    @Test
    fun `failure and idle return to the label`() {
        assertEquals(
            LoadingButtonPhase.Label,
            loadingButtonPhase(busy = false, result = LoadingButtonResult.Failure),
        )
        assertEquals(
            LoadingButtonPhase.Label,
            loadingButtonPhase(busy = false, result = LoadingButtonResult.None),
        )
    }

    @Test
    fun `the failure shake starts and ends quiet`() {
        assertEquals(0f, failureShake(0f))
        assertEquals(0f, failureShake(1f))
        assertEquals(0f, failureShake(-0.2f))
        assertEquals(0f, failureShake(1.2f))
    }

    @Test
    fun `the failure shake stays bounded and swings more than once`() {
        val samples = (1..99).map { failureShake(it / 100f) }
        assertTrue(samples.none { it.isNaN() })
        assertTrue(samples.none { kotlin.math.abs(it) > 1f }, "shake must stay within one width unit")
        val crossings = samples.zipWithNext().count { (a, b) -> (a > 0f) != (b > 0f) }
        assertTrue(crossings >= 4, "three decaying swings expected, got $crossings crossings")
        val early = samples.take(10).maxOf { abs(it) }
        val late = samples.takeLast(10).maxOf { abs(it) }
        assertTrue(early > late, "shake must decay")
    }
}
