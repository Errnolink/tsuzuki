package dev.errnolink.tsuzuki.ui.details

/**
 * DET-01: the rules that decide whether a title's chapter list is still unknown (`chaptersPending`).
 * While the flag is set, the page shows skeletons and no count — the strings "0 chapters" and
 * "No unread chapters" are unreachable, so a title that has chapters can never be told it has none
 * while its list is loading.
 *
 * Pure; the screen model applies them at its three state boundaries (DB emission, initial show,
 * fetch completion).
 */

/**
 * A chapters subscription emission arrived. The list is known the moment it is non-empty; an empty
 * emission only means "nothing in the DB yet" — the flag survives it while a fetch is running (the
 * first empty emission used to clear it, which printed the false "0 chapters" frame).
 */
fun chaptersPendingAfterEmission(chaptersEmpty: Boolean, wasPending: Boolean, refreshing: Boolean): Boolean =
    chaptersEmpty && wasPending && refreshing

/**
 * The early DB read has been shown. The list is unknown only when it is empty **and** a chapter
 * fetch is due for it; with chapters in hand, or with no fetch coming, it is known (possibly
 * known-empty).
 */
fun chaptersPendingAfterInitialShow(wasPending: Boolean, chaptersEmpty: Boolean, refreshDue: Boolean): Boolean =
    wasPending && chaptersEmpty && refreshDue
