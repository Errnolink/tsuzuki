package dev.errnolink.tsuzuki.mangadex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import exh.md.MangaDexPreferences
import exh.md.dto.ChapterDataDto
import exh.md.handlers.MangaDexArtworkStore
import exh.md.handlers.MangaDexFeatureMapper
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.model.Chapter
import kotlinx.coroutines.flow.combine

class MangaDexChapterPresentation internal constructor(
    val mangaUuid: String? = null,
    private val chapters: Map<String, ChapterDataDto> = emptyMap(),
    val selected: Set<String> = emptySet(),
    private val blockedGroups: Set<String> = emptySet(),
    private val blockedUploaders: Set<String> = emptySet(),
) {
    val languages: Set<String> = chapters.values.map { it.attributes.translatedLanguage }.toSortedSet()

    fun isVisible(chapter: Chapter): Boolean {
        val data = chapters[chapter.url] ?: return true
        if (data.relationships.any { (it.type == "scanlation_group" && it.id in blockedGroups) || (it.type == "user" && it.id in blockedUploaders) }) return false
        return selected.isEmpty() || data.attributes.translatedLanguage in selected
    }

    fun languageLabel(chapter: Chapter): String? = chapters[chapter.url]?.attributes?.translatedLanguage?.uppercase()
    fun unavailable(chapter: Chapter): Boolean =
        chapters[chapter.url]?.attributes?.isUnavailable ?: NekoBackupMapper.isUnavailable(chapter.memo)
    fun accessMessage(chapter: Chapter): String? {
        val data = chapters[chapter.url]
        return if (data != null) MangaDexFeatureMapper.chapterAccess(data) else NekoBackupMapper.accessMessage(chapter.memo)
    }
}

@Composable
fun MangaDexLanguageFilter(mangaId: Long) {
    MangaDexLanguageFilter(rememberMangaDexChapterPresentation(mangaId, emptyList()))
}

@Composable
fun rememberMangaDexChapterPresentation(mangaId: Long, chapters: List<Chapter>): MangaDexChapterPresentation {
    val state by produceState(MangaDexChapterPresentation(), mangaId, chapters) {
        withContext(Dispatchers.IO) {
            val primary = resolveMangaDex(mangaId) ?: return@withContext
            val mangaUuid = MdUtil.getMangaId(primary.manga.url)
            val data = chapters.map { it.mangaId }.plus(primary.manga.id).distinct().mapNotNull { resolveMangaDex(it) }
                .distinctBy { it.manga.id }.flatMap { MangaDexArtworkStore.chapters(MdUtil.getMangaId(it.manga.url)) }
                .associateBy { "/chapter/${it.id}" }
            val preferences = MangaDexPreferences()
            combine(
                preferences.mangaLanguages(mangaUuid).changes(),
                preferences.blockedGroups().changes(),
                preferences.blockedUploaders().changes(),
            ) { selected, groups, uploaders -> MangaDexChapterPresentation(mangaUuid, data, selected, groups, uploaders) }
                .collect { value = it }
        }
    }
    return state
}

@Composable
fun MangaDexLanguageFilter(state: MangaDexChapterPresentation) {
    val id = state.mangaUuid ?: return
    if (state.languages.size < 2) return
    val selected = state.selected.ifEmpty { state.languages }
    InsetGroupedList(title = "Chapter languages", footer = "Only filters this title's display. Read progress and downloaded chapters stay intact.") {
        state.languages.forEachIndexed { index, language ->
            val label = MdLang.fromIsoCode(language)?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar(Char::uppercase) ?: language.uppercase()
            GroupedRow(
                label,
                checked = language in selected,
                divider = index != state.languages.size - 1,
                onCheckedChange = { checked ->
                    val next = if (checked) selected + language else selected - language
                    if (next.isNotEmpty()) MangaDexPreferences().mangaLanguages(id).set(next)
                },
            )
        }
        GroupedRow("Show all languages", divider = false, onClick = { MangaDexPreferences().mangaLanguages(id).delete() })
    }
}
