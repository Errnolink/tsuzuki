package dev.errnolink.tsuzuki.mangadex

import exh.md.utils.FollowStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FollowsSyncProcessorTest {

    @Test
    fun `pull maps each follow status to the right category and favorite state`() = runTest {
        val knownManga = mapOf(
            "url-plan" to (10L to false),
            "url-rereading" to (11L to true),
            "url-completed" to (12L to false),
            "url-unfollowed" to (13L to true),
        )
        val inserts = mutableListOf<Pair<String, String>>()
        val favorites = mutableListOf<Long>()
        val categoryAssignments = mutableListOf<Pair<Long, Long>>()

        val follows = listOf(
            MangaDexFollow("url-reading", "New reading follow", FollowStatus.READING),
            MangaDexFollow("url-plan", "Unfavorited plan follow", FollowStatus.PLAN_TO_READ),
            MangaDexFollow("url-rereading", "Already favorited rereading", FollowStatus.RE_READING),
            MangaDexFollow("url-on-hold", "On hold", FollowStatus.ON_HOLD),
            MangaDexFollow("url-dropped", "Dropped", FollowStatus.DROPPED),
            MangaDexFollow("url-completed", "Completed", FollowStatus.COMPLETED),
            MangaDexFollow("url-unfollowed", "Unfollowed", FollowStatus.UNFOLLOWED),
        )

        val outcome = pullFollowsToLibrary(
            follows = follows,
            selectedStatuses = setOf(FollowStatus.READING, FollowStatus.PLAN_TO_READ, FollowStatus.RE_READING),
            defaultCategoryId = 7L,
            findManga = { url -> knownManga[url] },
            insertFavorite = { url, title ->
                inserts += url to title
                (100L + inserts.size)
            },
            markFavorite = { id -> favorites += id },
            assignCategory = { id, categoryId -> categoryAssignments += id to categoryId },
        )

        assertEquals(listOf("url-reading" to "New reading follow"), inserts)
        assertEquals(listOf(101L), outcome.addedIds)
        assertEquals(listOf(10L), favorites)
        assertEquals(listOf(10L), outcome.favoritedIds)
        assertEquals(listOf(101L to 7L, 10L to 7L), categoryAssignments)
        assertEquals(4, outcome.skippedStatuses)
    }

    @Test
    fun `pull without a default category leaves categories untouched`() = runTest {
        val categoryAssignments = mutableListOf<Pair<Long, Long>>()

        val outcome = pullFollowsToLibrary(
            follows = listOf(MangaDexFollow("url-reading", "Title", FollowStatus.READING)),
            selectedStatuses = setOf(FollowStatus.READING),
            defaultCategoryId = null,
            findManga = { null },
            insertFavorite = { _, _ -> 1L },
            markFavorite = {},
            assignCategory = { id, categoryId -> categoryAssignments += id to categoryId },
        )

        assertEquals(listOf(1L), outcome.addedIds)
        assertEquals(emptyList<Pair<Long, Long>>(), categoryAssignments)
    }

    @Test
    fun `push follows only unfollowed titles and never overwrites an existing MangaDex status`() = runTest {
        val pushed = mutableListOf<Pair<String, FollowStatus>>()
        val remoteStatuses = mapOf(
            "uuid-reading" to "reading",
            "uuid-plan" to "plan_to_read",
            "uuid-completed" to "completed",
        )
        val library = listOf(
            MangaDexLibraryTitle(1L, "uuid-reading", "Already reading"),
            MangaDexLibraryTitle(2L, "uuid-plan", "Plan to read"),
            MangaDexLibraryTitle(3L, "uuid-completed", "Completed remotely"),
            MangaDexLibraryTitle(4L, "uuid-absent", "Not followed remotely"),
            MangaDexLibraryTitle(5L, "uuid-absent", "Merged duplicate of absent"),
        )

        val count = pushReadingStatuses(
            library = library,
            remoteStatuses = remoteStatuses,
            push = { uuid, status ->
                pushed += uuid to status
                true
            },
        )

        assertEquals(1, count)
        assertEquals(listOf("uuid-absent" to FollowStatus.READING), pushed)
    }

    @Test
    fun `a signed-out user runs nothing`() {
        assertFalse(shouldRunFollowsSync(signedIn = false, enabled = true, manual = true))
        assertFalse(shouldRunFollowsSync(signedIn = false, enabled = true, manual = false))
        assertFalse(shouldRunFollowsSync(signedIn = false, enabled = false, manual = false))
        assertFalse(shouldRunFollowsSync(signedIn = true, enabled = false, manual = false))
        assertTrue(shouldRunFollowsSync(signedIn = true, enabled = true, manual = false))
        assertTrue(shouldRunFollowsSync(signedIn = true, enabled = false, manual = true))
    }
}
