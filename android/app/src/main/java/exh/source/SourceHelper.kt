package exh.source

import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.online.all.MangaDex

fun handleSourceLibrary() {
    mangaDexSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == MangaDex::class
        }
        .map { it.value.sourceId }
        .sorted()
}
