package dev.errnolink.tsuzuki.mangadex

import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.flow.first
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal data class MangaDexContext(val manga: Manga, val source: MangaDex)

internal suspend fun resolveMangaDex(mangaId: Long): MangaDexContext? {
    val sources = Injekt.get<SourceManager>()
    sources.isInitialized.first { it }
    val manga = Injekt.get<MangaRepository>().getMangaById(mangaId)
    val titles = if (manga.source == MERGED_SOURCE_ID) Injekt.get<MangaMergeRepository>().getMergedMangaById(mangaId) else listOf(manga)
    return titles.firstNotNullOfOrNull { title ->
        sources.get(title.source)?.getMainSource<MangaDex>()?.let { MangaDexContext(title, it) }
    }
}
