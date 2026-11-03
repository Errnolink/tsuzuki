package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.app.Application
import android.content.Context
import androidx.paging.PagingSource
import androidx.work.impl.WorkManagerImpl
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.Backup
import exh.EXHMigrations
import exh.source.EH_OLD_ID
import exh.source.EH_SOURCE_ID
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream

class BackupRestoreEhCompatTest {

    private val backupFile = File(
        System.getProperty("user.home") + "/AppData/Local/Tsuzuki/phone-backups/eu.kanade.tachiyomi.sy_2026-09-10_12-14.tachibk",
    )

    @Test
    fun `real SY backup decodes and restores end to end`() = runBlocking<Unit> {
        assumeTrue(backupFile.isFile, "SY backup not present: $backupFile")
        val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), backupFile.gunzipped())

        backup.backupManga.size.shouldBeBetween(400, 900)
        backup.backupManga.count { it.favorite }.shouldBeBetween(200, 400)
        backup.backupCategories.size.shouldBeBetween(1, 50)
        backup.backupSourcePreferences.size.shouldBeBetween(1, 50)
        backup.backupManga.sumOf { it.chapters.size }.shouldBeBetween(60_000, 150_000)
        backup.backupManga.sumOf { it.history.size }.shouldBeBetween(15_000, 60_000)

        val preferences = MapPreferenceStore()
        val customMangaRepository = mockk<CustomMangaRepository>()
        every { customMangaRepository.get(any()) } returns null
        Injekt.addSingleton(GetCustomMangaInfo(customMangaRepository))
        Injekt.addSingleton(
            mockk<Application>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
            },
        )
        Injekt.addSingleton(LibraryPreferences(preferences))
        Injekt.addSingleton(BackupPreferences(preferences))
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns mockk(relaxed = true)

        val handler = FakeRestoreHandler()
        val getChaptersByMangaId = GetChaptersByMangaId(ChapterRepositoryImpl(handler))
        val mangaRepository = MangaRepositoryImpl(handler)
        val fetchInterval = FetchInterval(getChaptersByMangaId)
        val mangaRestorer = MangaRestorer(
            handler = handler,
            getCategories = GetCategories(CategoryRepositoryImpl(handler)),
            getMangaByUrlAndSourceId = GetMangaByUrlAndSourceId(mangaRepository),
            getChaptersByMangaId = getChaptersByMangaId,
            updateManga = UpdateManga(mangaRepository, fetchInterval),
            getTracks = GetTracks(TrackRepositoryImpl(handler)),
            insertTrack = InsertTrack(TrackRepositoryImpl(handler)),
            fetchInterval = fetchInterval,
            setCustomMangaInfo = mockk(relaxed = true),
            insertFlatMetadata = InsertFlatMetadata(MangaMetadataRepositoryImpl(handler)),
            getFlatMetadataById = GetFlatMetadataById(MangaMetadataRepositoryImpl(handler)),
        )
        val restoreSample = backup.backupManga.take(50)
        restoreSample.forEach { mangaRestorer.restore(it, backup.backupCategories) }
        handler.insertedMangaCount shouldBe restoreSample.size.toLong()

        val preferenceRestorer = PreferenceRestorer(
            context = mockk(relaxed = true),
            getCategories = GetCategories(CategoryRepositoryImpl(handler)),
            preferenceStore = preferences,
        )
        preferenceRestorer.restoreApp(backup.backupPreferences, backup.backupCategories)
        preferenceRestorer.restoreSource(backup.backupSourcePreferences)

        EXHMigrations.migrateSourceIds(setOf(EH_OLD_ID.toString(), EXH_OLD_ID.toString())) shouldBe
            setOf(EH_SOURCE_ID.toString(), EXH_SOURCE_ID.toString())
    }

    private fun File.gunzipped(): ByteArray {
        val bytes = readBytes()
        return if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else {
            bytes
        }
    }

    private class FakeRestoreHandler : DatabaseHandler {

        val database = mockk<Database>(relaxed = true)

        var insertedMangaCount = 0L

        override suspend fun <T> await(inTransaction: Boolean, block: suspend Database.() -> T): T {
            return block(database)
        }

        override suspend fun <T : Any> awaitList(inTransaction: Boolean, block: suspend Database.() -> Query<T>): List<T> {
            return emptyList()
        }

        override suspend fun <T : Any> awaitListExecutable(
            inTransaction: Boolean,
            block: suspend Database.() -> ExecutableQuery<T>,
        ): List<T> {
            return emptyList()
        }

        override suspend fun <T : Any> awaitOne(inTransaction: Boolean, block: suspend Database.() -> Query<T>): T {
            error("awaitOne is not expected in the restore path")
        }

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend Database.() -> ExecutableQuery<T>,
        ): T {
            block(database)
            @Suppress("UNCHECKED_CAST")
            return ++insertedMangaCount as T
        }

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend Database.() -> Query<T>,
        ): T? {
            return null
        }

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend Database.() -> ExecutableQuery<T>,
        ): T? {
            return null
        }

        override fun <T : Any> subscribeToList(
            debounceMillis: Long,
            block: Database.() -> Query<T>,
        ): Flow<List<T>> {
            return flowOf(emptyList())
        }

        override fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T> {
            error("subscribeToOne is not expected in the restore path")
        }

        override fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?> {
            return flowOf(null)
        }

        override fun <T : Any> subscribeToPagingSource(
            countQuery: Database.() -> Query<Long>,
            queryProvider: Database.(Long, Long) -> Query<T>,
        ): PagingSource<Long, T> {
            error("subscribeToPagingSource is not expected in the restore path")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class MapPreferenceStore : PreferenceStore {

        private val values = HashMap<String, Any?>()

        private inner class Pref<T>(private val prefKey: String, private val default: T) : Preference<T> {
            override fun key(): String = prefKey

            override fun get(): T = values[prefKey] as? T ?: default

            override fun set(value: T) {
                values[prefKey] = value
            }

            override fun isSet(): Boolean = values.containsKey(prefKey)

            override fun delete() {
                values.remove(prefKey)
            }

            override fun defaultValue(): T = default

            override fun changes(): Flow<T> = MutableStateFlow(get())

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
        }

        override fun getString(key: String, defaultValue: String): Preference<String> = Pref(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> = Pref(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> = Pref(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = Pref(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = Pref(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            Pref(key, defaultValue)

        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = Pref(key, defaultValue)

        override fun <T> getObjectFromInt(
            key: String,
            defaultValue: T,
            serializer: (T) -> Int,
            deserializer: (Int) -> T,
        ): Preference<T> = Pref(key, defaultValue)

        override fun getAll(): Map<String, *> = values
    }
}
