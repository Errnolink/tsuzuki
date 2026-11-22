package dev.errnolink.tsuzuki.ui.browse

import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class GlobalSearchLogicTest {

    private fun fakeSource(id: Long, name: String): Source = object : Source {
        override val id: Long = id
        override val name: String = name
        override val supportsLatest: Boolean = false
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }

    private fun fakeManga(id: Long, favorite: Boolean): Manga =
        Manga.create().copy(id = id, source = 1L, url = "/m/$id", ogTitle = "Title $id", favorite = favorite)

    @Test
    fun `empty kind distinguishes initial prompt, no sources, in-flight filter, and zero matches`() {
        val s1 = fakeSource(1L, "Alpha")
        val s2 = fakeSource(2L, "Beta")
        val fav = fakeManga(10L, favorite = true)

        assertEquals(
            GlobalSearchEmptyKind.NO_QUERY,
            resolveGlobalSearchEmptyKind(
                searchQuery = "   ",
                allItems = emptyMap(),
                filteredItems = emptyMap(),
                libraryFilter = GlobalLibraryFilter.ALL,
                onlyShowHasResults = false,
                visibleResultCount = 0,
            ),
        )

        assertEquals(
            GlobalSearchEmptyKind.NO_SOURCES,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = emptyMap(),
                filteredItems = emptyMap(),
                libraryFilter = GlobalLibraryFilter.ALL,
                onlyShowHasResults = false,
                visibleResultCount = 0,
            ),
        )

        val stillLoadingAll = mapOf(
            s1 to SearchItemResult.Success(emptyList()),
            s2 to SearchItemResult.Loading,
        )
        assertEquals(
            GlobalSearchEmptyKind.SEARCHING_FILTERED,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = stillLoadingAll,
                filteredItems = emptyMap(),
                libraryFilter = GlobalLibraryFilter.ALL,
                onlyShowHasResults = true,
                visibleResultCount = 0,
            ),
        )

        assertEquals(
            GlobalSearchEmptyKind.NO_RESULTS,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = mapOf(s1 to SearchItemResult.Success(emptyList())),
                filteredItems = emptyMap(),
                libraryFilter = GlobalLibraryFilter.ALL,
                onlyShowHasResults = true,
                visibleResultCount = 0,
            ),
        )

        assertEquals(
            GlobalSearchEmptyKind.NONE,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = mapOf(s1 to SearchItemResult.Error(IllegalStateException("timeout"))),
                filteredItems = mapOf(s1 to SearchItemResult.Error(IllegalStateException("timeout"))),
                libraryFilter = GlobalLibraryFilter.ALL,
                onlyShowHasResults = false,
                visibleResultCount = 0,
            ),
        )

        assertEquals(
            GlobalSearchEmptyKind.NO_LIBRARY_FILTER_MATCHES,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = mapOf(s1 to SearchItemResult.Success(listOf(fav))),
                filteredItems = mapOf(s1 to SearchItemResult.Success(listOf(fav))),
                libraryFilter = GlobalLibraryFilter.NOT_IN_LIBRARY,
                onlyShowHasResults = false,
                visibleResultCount = 0,
            ),
        )

        assertEquals(
            GlobalSearchEmptyKind.NONE,
            resolveGlobalSearchEmptyKind(
                searchQuery = "one piece",
                allItems = mapOf(s1 to SearchItemResult.Success(listOf(fav))),
                filteredItems = mapOf(s1 to SearchItemResult.Success(listOf(fav))),
                libraryFilter = GlobalLibraryFilter.IN_LIBRARY,
                onlyShowHasResults = false,
                visibleResultCount = 1,
            ),
        )
    }

    @Test
    fun `visibleGlobalSearchMangas respects library filter and ignores non-success sources`() {
        val s1 = fakeSource(1L, "Alpha")
        val s2 = fakeSource(2L, "Beta")
        val s3 = fakeSource(3L, "Gamma")
        val fav = fakeManga(1L, favorite = true)
        val nonFav = fakeManga(2L, favorite = false)

        val items = mapOf(
            s1 to SearchItemResult.Success(listOf(fav, nonFav)),
            s2 to SearchItemResult.Loading,
            s3 to SearchItemResult.Error(RuntimeException("fail")),
        )

        assertEquals(listOf(1L, 2L), visibleGlobalSearchMangas(items, GlobalLibraryFilter.ALL).map { it.id })
        assertEquals(listOf(1L), visibleGlobalSearchMangas(items, GlobalLibraryFilter.IN_LIBRARY).map { it.id })
        assertEquals(listOf(2L), visibleGlobalSearchMangas(items, GlobalLibraryFilter.NOT_IN_LIBRARY).map { it.id })
    }

    @Test
    fun `markSelectedMangasRead deduplicates manga ids and counts only successful updates`() = runBlocking {
        val m1 = fakeManga(1L, favorite = true)
        val m2 = fakeManga(2L, favorite = false)
        val m3 = fakeManga(3L, favorite = true)
        val calledIds = mutableListOf<Long>()

        val count = markSelectedMangasRead(listOf(m1, m2, m1, m3)) { manga ->
            calledIds.add(manga.id)
            when (manga.id) {
                1L -> SetReadStatus.Result.Success
                2L -> SetReadStatus.Result.NoChapters
                else -> SetReadStatus.Result.InternalError(IllegalStateException("db"))
            }
        }

        assertEquals(listOf(1L, 2L, 3L), calledIds)
        assertEquals(1, count)
    }
}
