package eu.kanade.tachiyomi.data.coil

import android.app.Application
import android.graphics.BitmapFactory
import dev.errnolink.tsuzuki.data.coil.CoverRatioStore
import eu.kanade.tachiyomi.data.cache.CoverCache
import okio.BufferedSource
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MangaCoverMetadata {
    private val preferences by injectLazy<LibraryPreferences>()
    private val coverCache by injectLazy<CoverCache>()
    private val store by lazy { CoverRatioStore(Injekt.get<Application>()) }
    private val coverKeys = ConcurrentHashMap<Long, String>()
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        store.load().forEach { (id, entry) ->
            coverKeys[id] = entry.first
            MangaCover.coverRatioMap.putIfAbsent(id, entry.second)
        }
        preferences.coverRatios().get().forEach { entry ->
            val id = entry.substringBefore('|').toLongOrNull() ?: return@forEach
            val ratio = entry.substringAfter('|').toFloatOrNull() ?: return@forEach
            if (ratio.isFinite() && ratio > 0f && MangaCover.coverRatioMap.putIfAbsent(id, ratio) == null) {
                store.put(id, "", ratio)
            }
        }
        preferences.coverRatios().delete()
        preferences.coverColors().delete()
        loaded = true
    }

    fun setRatio(
        mangaCover: MangaCover,
        bufferedSource: BufferedSource? = null,
        ogFile: File? = null,
    ) {
        if (!mangaCover.isMangaFavorite) return
        load()
        val key = "${mangaCover.url}|${mangaCover.lastModified}"
        if (coverKeys[mangaCover.mangaId] == key) return
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (bufferedSource != null) {
            bufferedSource.peek().use { BitmapFactory.decodeStream(it.inputStream(), null, options) }
        } else {
            val file = ogFile
                ?: coverCache.getCustomCoverFile(mangaCover.mangaId).takeIf { it.exists() }
                ?: coverCache.getCoverFile(mangaCover.url)
            if (file?.exists() != true) return
            BitmapFactory.decodeFile(file.path, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return
        val ratio = options.outWidth.toFloat() / options.outHeight
        mangaCover.ratio = ratio
        store.put(mangaCover.mangaId, key, ratio)
        coverKeys[mangaCover.mangaId] = key
    }
}
