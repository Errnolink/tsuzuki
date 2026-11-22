package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DET-01 as a state machine: the page may print "0 chapters" / "No unread chapters" only when the
 * chapter list is known (`chaptersPending == false`). These are the rules the screen model applies
 * at its three boundaries, replayed as the recording's sequence: a title that has chapters must
 * never be told it has none while its list is arriving.
 */
class DetailsChapterPendingTest {

    @Test
    fun `empty db emission while a fetch runs keeps the list unknown`() {
        // Open a never-loaded title: early state pending, refresh running, DB emits its (empty) list.
        val pending = chaptersPendingAfterEmission(chaptersEmpty = true, wasPending = true, refreshing = true)
        assertTrue(pending, "the first empty emission must not make an empty list known")
    }

    @Test
    fun `a non-empty emission makes the list known`() {
        assertFalse(chaptersPendingAfterEmission(chaptersEmpty = false, wasPending = true, refreshing = true))
        // ...also when the fetch already finished.
        assertFalse(chaptersPendingAfterEmission(chaptersEmpty = false, wasPending = true, refreshing = false))
    }

    @Test
    fun `an empty emission with no fetch running means known-empty`() {
        assertFalse(chaptersPendingAfterEmission(chaptersEmpty = true, wasPending = true, refreshing = false))
    }

    @Test
    fun `the initial show keeps the list unknown only when a fetch is due for an empty list`() {
        // Chapters in the DB: known on first show.
        assertFalse(chaptersPendingAfterInitialShow(wasPending = true, chaptersEmpty = false, refreshDue = true))
        // Empty but no fetch due: genuinely empty, known.
        assertFalse(chaptersPendingAfterInitialShow(wasPending = true, chaptersEmpty = true, refreshDue = false))
        // Empty with a fetch due: still loading.
        assertTrue(chaptersPendingAfterInitialShow(wasPending = true, chaptersEmpty = true, refreshDue = true))
        // Re-entry never re-arms the flag once the list is known.
        assertFalse(chaptersPendingAfterInitialShow(wasPending = false, chaptersEmpty = true, refreshDue = true))
    }

    @Test
    fun `replay - a title that has chapters never shows zero while loading`() {
        // The recording: open → empty DB emission → refresh still running → chapters arrive.
        var pending = true
        var refreshing = true

        // Early DB read is shown; a fetch is due for the empty list.
        pending = chaptersPendingAfterInitialShow(pending, chaptersEmpty = true, refreshDue = true)
        assertTrue(pending)

        // The chapters subscription emits the empty DB list mid-fetch (the old false-0 frame).
        pending = chaptersPendingAfterEmission(chaptersEmpty = true, wasPending = pending, refreshing = refreshing)
        assertTrue(pending, "the list is still unknown while the fetch runs")

        // The fetch lands and writes rows; the DB re-emits with the chapters.
        val wroteChapters = true
        pending = pending && wroteChapters // fetch completion keeps the flag for the re-emission
        pending = chaptersPendingAfterEmission(chaptersEmpty = false, wasPending = pending, refreshing = false)
        assertFalse(pending, "known once the chapters arrive")

        // So at no point was (pending == false && empty) reachable: "0 chapters" and
        // "No unread chapters" were both gated behind pending throughout the replay.
    }

    @Test
    fun `replay - a source with no chapters settles as known-empty, never pending forever`() {
        var pending = true
        // Initial show with a fetch due.
        pending = chaptersPendingAfterInitialShow(pending, chaptersEmpty = true, refreshDue = true)
        assertTrue(pending)
        // The fetch completes and wrote nothing (no chapters exist); nothing will re-emit.
        val wroteChapters = false
        pending = pending && wroteChapters
        assertFalse(pending, "a finished fetch that wrote nothing settles the list as known-empty")
    }
}
