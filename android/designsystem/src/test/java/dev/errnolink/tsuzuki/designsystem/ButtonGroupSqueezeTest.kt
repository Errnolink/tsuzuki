package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import androidx.compose.ui.unit.dp

/** Kasane standard-group squeeze: the pressed item grows, neighbours give way, the group never reflows. */
class ButtonGroupSqueezeTest {

    private fun assertConservesWidth(deltas: List<Float>) {
        assertEquals(0f, deltas.sum(), 0.001f, "deltas must sum to zero: $deltas")
    }

    @Test
    fun `a middle press splits the loss evenly between its two neighbours`() {
        val deltas = squeezeDeltas(listOf(100f, 100f, 100f), pressedIndex = 1)
        assertConservesWidth(deltas)
        assertEquals(0.15f * 100f, deltas[1])
        assertEquals(deltas[0], deltas[2], 0.001f)
        assertEquals(-deltas[0] - deltas[2], deltas[1], 0.001f)
        assertTrue(deltas[0] < 0f && deltas[2] < 0f)
    }

    @Test
    fun `an edge press takes its growth from the one neighbour`() {
        val deltas = squeezeDeltas(listOf(100f, 100f, 100f), pressedIndex = 0)
        assertConservesWidth(deltas)
        assertEquals(15f, deltas[0], 0.001f)
        assertEquals(-15f, deltas[1], 0.001f)
        assertEquals(0f, deltas[2], "the far item must not move")
    }

    @Test
    fun `growth is proportional to the pressed item's own width`() {
        val deltas = squeezeDeltas(listOf(60f, 160f, 60f), pressedIndex = 1)
        assertEquals(160f * 0.15f, deltas[1], 0.001f)
        assertConservesWidth(deltas)
    }

    @Test
    fun `a narrow neighbour never gives past its floor`() {
        // 200 px pressed wants 30 px; the right neighbour can only give 200 - max(32, 120) = 80, fine,
        // but a 35 px neighbour can only give 35 - max(32, 21) = 3 px before the floor bites
        val deltas = squeezeDeltas(listOf(200f, 35f), pressedIndex = 0)
        assertConservesWidth(deltas)
        assertTrue(deltas[0] > 0f)
        assertEquals(-deltas[0], deltas[1], 0.001f)
        assertTrue(35f + deltas[1] >= 32f, "neighbour must keep its floor: ${35f + deltas[1]}")
    }

    @Test
    fun `no press and single-item groups never move`() {
        assertEquals(listOf(0f, 0f, 0f), squeezeDeltas(listOf(100f, 100f, 100f), -1))
        assertEquals(listOf(0f), squeezeDeltas(listOf(100f), 0))
        assertEquals(emptyList<Float>(), squeezeDeltas(emptyList(), 0))
    }

    @Test
    fun `an out-of-range index is a no-op`() {
        assertEquals(listOf(0f, 0f), squeezeDeltas(listOf(100f, 100f), 2))
        assertEquals(listOf(0f, 0f), squeezeDeltas(listOf(100f, 100f), -3))
    }

    // connected corners

    @Test
    fun `selected items round into a full pill`() {
        val radii = connectedGroupRadii(index = 1, count = 3, height = 40.dp, selected = true, pressFraction = 0f)
        assertTrue(radii.all { it == 20.dp })
    }

    @Test
    fun `ends stay full while inner corners tighten on press`() {
        val rest = connectedGroupRadii(0, 3, 40.dp, false, 0f)
        assertEquals(20.dp, rest[0], "outer leading corner")
        assertTrue(rest[1] > 4.dp && rest[1] < 20.dp, "inner corner between the ends and the capsule")
        val held = connectedGroupRadii(0, 3, 40.dp, false, 1f)
        assertTrue(held[0] == rest[0], "outer corner never moves")
        assertTrue(held[1] < rest[1], "inner corners tighten while held")
    }

    @Test
    fun `middle items have no outer corner`() {
        val radii = connectedGroupRadii(1, 3, 40.dp, false, 0f)
        assertTrue(radii.all { it < 20.dp })
        assertTrue(radii.all { it >= 4.dp })
    }
}
