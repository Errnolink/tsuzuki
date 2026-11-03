// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.utils.MdUtil
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.repository.MangaMergeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object MangaDexDynamicCovers {
    private val lastChapter = mutableMapOf<Long, Long>()

    suspend fun update(manga: Manga, chapter: Chapter, source: MangaDex) {
        val preferences = MangaDexPreferences()
        if (lastChapter[manga.id] == chapter.id) return
        val merges = Injekt.get<MangaMergeRepository>()
        val targets = buildList {
            if (manga.favorite) add(manga)
            merges.getMergedManga().filter { it.favorite }.forEach { parent ->
                if (merges.getReferencesById(parent.id).any { it.mangaId == manga.id }) add(parent)
            }
        }.filterNot { preferences.manualCover(it.id).get() }
        if (targets.isEmpty()) {
            lastChapter[manga.id] = chapter.id
            return
        }
        val id = MdUtil.getMangaId(manga.url)
        val dto = MangaDexArtworkStore.chapters(id).firstOrNull { it.id == MdUtil.getChapterId(chapter.url) }
            ?: return
        val covers = MangaDexArtworkStore.covers(id).ifEmpty { MangaDexFeatures(source).covers(id) }
        val cover = select(covers, dto.attributes.volume, dto.attributes.translatedLanguage) ?: return
        val cleanBaseUrl = exh.md.utils.MdCoverVariantUtil.normalizeCoverUrl(cover.url)
        val updates = targets.filter { it.ogThumbnailUrl != cleanBaseUrl }.map {
            MangaUpdate(id = it.id, thumbnailUrl = cleanBaseUrl, coverLastModified = System.currentTimeMillis())
        }
        if (updates.isNotEmpty() && !Injekt.get<MangaRepository>().updateAll(updates)) return
        preferences.dynamicCoverUrl(id).set(cleanBaseUrl)
        lastChapter[manga.id] = chapter.id
    }

    fun select(covers: List<MangaDexArtwork>, volume: String?, language: String): MangaDexArtwork? = covers
        .filter { it.volume?.toDoubleOrNull() == volume?.toDoubleOrNull() && !volume.isNullOrBlank() }
        .minByOrNull { if (it.locale == language) 0 else 1 }
}
