// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class ScanlatorFilterOption(val matchAll: Long) {
    ALL(1),
    ANY(0);

    companion object {
        fun observe(handler: DatabaseHandler = Injekt.get()) = handler.subscribeToOne {
            tsuzuki_chapter_filterQueries.getMatchAll()
        }.map { if (it == 1L) ALL else ANY }

        suspend fun set(option: ScanlatorFilterOption, handler: DatabaseHandler = Injekt.get()) {
            handler.await { tsuzuki_chapter_filterQueries.setMatchAll(option.matchAll) }
        }
    }
}
