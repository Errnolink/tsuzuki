package exh.md

import exh.md.handlers.MangaDexDiscover
import exh.md.handlers.MangaDexDiscoverRow
import exh.md.handlers.MangaDexFeatures
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaDexDiscoverRowsTest {

    private fun discoverWithRatings(vararg ratings: String): Pair<MangaDexDiscover, MutableList<Long>> {
        val emissionTimes = mutableListOf<Long>()
        val features = mockk<MangaDexFeatures>()
        every { features.ratings } returns ratings.toList()
        return MangaDexDiscover(features) to emissionTimes
    }

    @Test
    fun `shelves arrive as each loader finishes instead of waiting for the slowest`() = runTest {
        val (discover, _) = discoverWithRatings("safe", "suggestive")
        val loaders = listOf(
            "Seasonal" to suspend {
                delay(300)
                MangaDexDiscoverRow("seasonal", "Seasonal")
            },
            "Staff picks" to suspend {
                delay(50)
                MangaDexDiscoverRow("staff", "Staff picks")
            },
            "Popular new titles" to suspend {
                delay(150)
                MangaDexDiscoverRow("popular", "Popular new titles")
            },
            "Latest chapters" to suspend {
                delay(10)
                error("MangaDex could not load this shelf")
            },
        )

        val rows = discover.rows(loaders).toList()

        assertEquals(
            listOf("Latest chapters", "Staff picks", "Popular new titles", "Seasonal"),
            rows.map { it.title },
        )
        assertEquals("MangaDex could not load this shelf", rows.first().error)
    }

    @Test
    fun `the first shelf is emitted long before the slowest loader finishes`() = runTest {
        val (discover, emissionTimes) = discoverWithRatings("safe")
        val loaders = listOf(
            "Seasonal" to suspend {
                delay(1_000)
                MangaDexDiscoverRow("seasonal", "Seasonal")
            },
            "Staff picks" to suspend {
                delay(20)
                MangaDexDiscoverRow("staff", "Staff picks")
            },
        )

        discover.rows(loaders).onEach { emissionTimes.add(testScheduler.currentTime) }.toList()

        assertEquals(20, emissionTimes.first())
        assertEquals(listOf(20L, 1_000L), emissionTimes)
    }
}
