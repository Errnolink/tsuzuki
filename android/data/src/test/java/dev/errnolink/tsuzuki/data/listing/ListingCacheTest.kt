package dev.errnolink.tsuzuki.data.listing

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class ListingCacheTest {

    @TempDir
    lateinit var dir: File

    private var clock = 0L
    private var requests = 0

    private fun cache() = ListingCache(dir, now = { clock })

    private fun page(vararg titles: String) = MangasPage(
        titles.map { t -> SManga.create().apply { url = "/$t"; title = t; thumbnail_url = "https://img/$t.jpg" } },
        hasNextPage = true,
    )

    private suspend fun ListingCache.popular(result: () -> MangasPage) =
        page(ListingCache.Key(1L, ListingCache.Kind.Popular, page = 1)) { requests++; result() }

    @Test
    fun `scrolling a popular list twice within half an hour makes one request`() = runBlocking {
        cache().popular { page("A", "B") }
        clock += 29 * 60_000L
        val second = cache().popular { page("C") }
        assertEquals(1, requests)
        assertEquals(listOf("A", "B"), second.mangas.map { it.title })
        assertEquals("https://img/A.jpg", second.mangas.first().thumbnail_url)
    }

    @Test
    fun `an old list is fetched again`() = runBlocking {
        cache().popular { page("A") }
        clock += 31 * 60_000L
        val second = cache().popular { page("New") }
        assertEquals(2, requests)
        assertEquals(listOf("New"), second.mangas.map { it.title })
    }

    @Test
    fun `searches expire after ten minutes`() = runBlocking {
        val key = ListingCache.Key(1L, ListingCache.Kind.Search, page = 1, query = "soccer")
        cache().page(key) { requests++; page("A") }
        clock += 11 * 60_000L
        cache().page(key) { requests++; page("B") }
        assertEquals(2, requests)
    }

    @Test
    fun `offline shows the saved list even when it is old`() = runBlocking {
        cache().popular { page("A") }
        clock += 5 * 60 * 60_000L
        val offline = cache().popular { throw IOException("no network") }
        assertEquals(listOf("A"), offline.mangas.map { it.title })
    }

    @Test
    fun `offline with nothing saved still reports the error`() {
        assertThrows<IOException> { runBlocking { cache().popular { throw IOException("no network") } } }
    }

    @Test
    fun `a deliberate reload fetches fresh`() = runBlocking {
        val cache = cache()
        cache.popular { page("A") }
        cache.forget(1L)
        val reloaded = cache.popular { page("B") }
        assertEquals(2, requests)
        assertEquals(listOf("B"), reloaded.mangas.map { it.title })
    }

    @Test
    fun `different searches and pages are kept apart`() = runBlocking {
        val cache = cache()
        cache.page(ListingCache.Key(1L, ListingCache.Kind.Search, 1, query = "a")) { page("A") }
        val other = cache.page(ListingCache.Key(1L, ListingCache.Kind.Search, 1, query = "b")) { page("B") }
        val page2 = cache.page(ListingCache.Key(1L, ListingCache.Kind.Search, 2, query = "a")) { page("A2") }
        assertEquals(listOf("B"), other.mangas.map { it.title })
        assertEquals(listOf("A2"), page2.mangas.map { it.title })
    }
}
