package exh.md

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.dto.MarkStatusDto
import exh.md.handlers.MangaDexBlocks
import exh.md.handlers.StatusHandler
import exh.md.utils.MdUtil
import exh.source.MERGED_SOURCE_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

internal class MangaDexTestPreferences : PreferenceStore by InMemoryPreferenceStore() {
    private val booleans = mutableMapOf<String, Preference<Boolean>>()
    private val integers = mutableMapOf<String, Preference<Int>>()
    private val strings = mutableMapOf<String, Preference<String>>()
    private val sets = mutableMapOf<String, Preference<Set<String>>>()
    override fun getBoolean(key: String, defaultValue: Boolean) = booleans.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    override fun getInt(key: String, defaultValue: Int) = integers.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    override fun getString(key: String, defaultValue: String) = strings.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
    override fun getStringSet(key: String, defaultValue: Set<String>) = sets.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, defaultValue) }
}

class MangaDexReadSyncTest {
    @Test
    fun `reading sync defaults off and language opt-in preserves extension behavior`() {
        val preferences = MangaDexPreferences(MangaDexTestPreferences())
        assertFalse(preferences.readingSync().get())
        assertEquals("reading_sync_bool", preferences.readingSync().key())
        assertEquals(0, preferences.autoAddToLibrary().get())
        assertEquals("auto_add_to_mangadex_library", preferences.autoAddToLibrary().key())
        assertEquals(listOf("ja"), preferences.languages("ja"))
        preferences.multiLanguage().set(true)
        preferences.chapterLanguages().set(setOf("ja", "en"))
        assertEquals(listOf("en", "ja"), preferences.languages("fr"))
        preferences.mangaLanguages("m").set(setOf("ja"))
        assertEquals(listOf("ja"), preferences.languages("fr", "m"))
    }

    @Test
    fun `global block undo preserves unrelated blocks and preexisting blocks`() {
        val preferences = MangaDexPreferences(MangaDexTestPreferences())
        val blocks = MangaDexBlocks(preferences)
        val first = blocks.block("g", "Group", false)
        blocks.block("other", "Other", false)
        blocks.undo(first)
        assertEquals(setOf("other"), preferences.blockedGroups().get())
        val existing = blocks.block("other", "Other", false)
        blocks.undo(existing)
        assertTrue(blocks.isBlocked("other", false))
        blocks.block("u", "Uploader", true)
        assertTrue(blocks.isBlocked("u", true))
        assertFalse(blocks.isBlocked("u", false))
    }

    @Test
    fun `disabled reading sync does not push or pull even when signed in`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val chapters = mockk<ChapterRepository>(relaxed = true)
            val trackers = mockk<TrackerManager>(relaxed = true)
            every { trackers.mdList.isLoggedIn } returns true
            val handler = StatusHandler(chapters, mockk(), mockk(), trackers, MangaDexPreferences(MangaDexTestPreferences()), mockk(), scope)
            handler.onReadStatusChanged(listOf(ChapterUpdate(1, read = true)))
            handler.pull(mockk())
            coVerify(exactly = 0) { chapters.getChapterById(any()) }
            coVerify(exactly = 0) { chapters.getChapterByMangaId(any()) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `merged parent pushes only MangaDex owned chapter ids and snapshots requested unread state`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = Channel<MarkStatusDto>(Channel.UNLIMITED)
        val chapterId = "566d375f-9489-45e5-aff9-7aa13219a1b5"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            requests.trySend(MdUtil.jsonParser.decodeFromString<MarkStatusDto>(buffer.readUtf8()))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"result\":\"ok\"}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val preferences = MangaDexPreferences(MangaDexTestPreferences()).apply { readingSync().set(true) }
            val chapters = mockk<ChapterRepository>()
            val titles = mockk<MangaRepository>()
            val merges = mockk<MangaMergeRepository>()
            val sources = mockk<SourceManager>()
            val trackers = mockk<TrackerManager>(relaxed = true)
            val source = mockk<MangaDex>()
            every { source.lang } returns "en"
            every { source.isLogged() } returns true
            every { source.baseHttpClient } returns client
            every { trackers.mdList.isLoggedIn } returns true
            val parent = mockk<Manga>()
            every { parent.source } returns MERGED_SOURCE_ID
            val child = mockk<Manga>()
            every { child.id } returns 10
            every { child.source } returns 42
            every { child.url } returns "/manga/f9c33607-9180-4ba6-b85c-e4b5faee7192"
            val chapter = Chapter.create().copy(id = 1, mangaId = 20, read = true, url = "/chapter/$chapterId")
            val external = chapter.copy(id = 2, url = "/external/chapter/7")
            coEvery { chapters.getChapterById(1) } returns chapter
            coEvery { chapters.getChapterById(2) } returns external
            coEvery { chapters.getChapterByMangaId(10) } returns listOf(chapter.copy(mangaId = 10))
            coEvery { titles.getMangaById(20) } returns parent
            coEvery { merges.getMergedMangaById(20) } returns listOf(child)
            every { sources.get(42) } returns source
            val handler = StatusHandler(chapters, titles, sources, trackers, preferences, merges, scope)
            handler.onReadStatusChanged(listOf(ChapterUpdate(1, read = false), ChapterUpdate(2, read = true)))
            val body = withTimeout(5000) { requests.receive() }
            assertEquals(emptyList<String>(), body.chapterIdsRead)
            assertEquals(listOf(chapterId), body.chapterIdsUnread)
        } finally {
            scope.cancel()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
