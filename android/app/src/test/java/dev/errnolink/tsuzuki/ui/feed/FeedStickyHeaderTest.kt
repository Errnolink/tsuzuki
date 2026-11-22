package dev.errnolink.tsuzuki.ui.feed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * N19: the sticky day header's pinned treatment — page background under the top row, content
 * pushed below it — applies strictly above the rest line, so the at-rest first header is a plain
 * row and nothing jumps when the list sits at its top.
 */
class FeedStickyHeaderTest {

    @Test
    fun `a header at its rest line is not pinned`() {
        assertFalse(stickyHeaderPinned(itemOffset = 200, beforeContentPadding = 200))
    }

    @Test
    fun `a header entering the band or stuck at the top is pinned`() {
        assertTrue(stickyHeaderPinned(itemOffset = 199, beforeContentPadding = 200))
        assertTrue(stickyHeaderPinned(itemOffset = 0, beforeContentPadding = 200))
        assertTrue(stickyHeaderPinned(itemOffset = -40, beforeContentPadding = 200))
    }

    @Test
    fun `a header deep in the list is not pinned`() {
        assertFalse(stickyHeaderPinned(itemOffset = 900, beforeContentPadding = 200))
    }

    @Test
    fun `without content padding nothing is pinned`() {
        assertFalse(stickyHeaderPinned(itemOffset = 0, beforeContentPadding = 0))
    }

    @Test
    fun `the pinned clearance is layout height, zero at rest`() {
        assertEquals(64f, feedDayStickyOffset(pinned = true, clearance = 64.dp).value, 0.01f)
        assertEquals(0f, feedDayStickyOffset(pinned = false, clearance = 64.dp).value, 0.01f)
    }
}
class FeedPreloadRangeTest {

    private val day = LocalDate.of(2026, 11, 16)
    private val groups = (0 until 30).map { index ->
        FeedTitleGroup(
            mangaId = index.toLong(),
            mangaTitle = "Title $index",
            coverUrl = "https://example/$index.jpg",
            chapters = emptyList(),
            isLibrary = true,
        )
    }
    private val dayGroups = mapOf(day to groups)

    @Test
    fun `groups after the last visible one preload, capped to the ahead window`() {
        val keys = setOf<Any?>("up-next", "day-header-$day", "${day}-id:0", "${day}-id:3")
        val ahead = FeedMerger.preloadGroupsAfter(dayGroups, keys, ahead = 4)
        assertEquals(listOf(4L, 5L, 6L, 7L), ahead.map { it.mangaId })
    }

    @Test
    fun `the end of the list returns only what is left`() {
        val keys = setOf<Any?>("${day}-id:28")
        val ahead = FeedMerger.preloadGroupsAfter(dayGroups, keys, ahead = 4)
        assertEquals(listOf(29L), ahead.map { it.mangaId })
    }

    @Test
    fun `no visible group or no ahead means nothing preloads`() {
        assertEquals(emptyList<FeedTitleGroup>(), FeedMerger.preloadGroupsAfter(dayGroups, setOf("up-next"), ahead = 4))
        assertEquals(emptyList<FeedTitleGroup>(), FeedMerger.preloadGroupsAfter(dayGroups, setOf("${day}-id:0"), ahead = 0))
        assertEquals(emptyList<FeedTitleGroup>(), FeedMerger.preloadGroupsAfter(emptyMap(), setOf("${day}-id:0"), ahead = 4))
    }
}
