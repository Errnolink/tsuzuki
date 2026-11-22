package dev.errnolink.tsuzuki.ui.motion

import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.manga.MangaScreen

/**
 * The one way to open a title page (N13): every row or tile that shows a cover zooms out of that
 * cover — whichever part of the row was tapped — because [MangaHero.claim] picks the cover nearest
 * the touch. Opening with no cover on screen (deep links, notifications, merges) claims nothing and
 * keeps the plain push, as screens without a source image (Trackers, Settings) always do.
 */
fun openTitle(navigator: Navigator, mangaId: Long, fromSource: Boolean = false) {
    MangaHero.claim(mangaId)
    navigator.push(MangaScreen(mangaId, fromSource))
}
