package dev.errnolink.tsuzuki.data.listing

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class ListingCache(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Kind(val lifetimeMillis: Long) {
        Popular(30 * 60_000L),
        Latest(30 * 60_000L),
        Search(10 * 60_000L),
    }

    data class Key(val sourceId: Long, val kind: Kind, val page: Int, val query: String = "", val filters: String = "") {
        val fileName: String
            get() = MessageDigest.getInstance("SHA-1")
                .digest("$sourceId|$kind|$page|$query|$filters".toByteArray())
                .joinToString("") { "%02x".format(it) } + ".json"
    }

    @Serializable
    private data class Entry(val savedAt: Long, val hasNextPage: Boolean, val mangas: List<Item>)

    @Serializable
    private data class Item(
        val url: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: String? = null,
        val status: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun page(key: Key, fetch: suspend () -> MangasPage): MangasPage {
        val file = File(sourceDir(key.sourceId), key.fileName)
        val cached = read(file)
        if (cached != null && now() - cached.savedAt < key.kind.lifetimeMillis) return cached.toPage()
        val fresh = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return cached?.toPage() ?: throw e
        }
        if (fresh !is MetadataMangasPage && fresh.mangas.isNotEmpty()) write(file, fresh)
        return fresh
    }

    fun forget(sourceId: Long) {
        sourceDir(sourceId).deleteRecursively()
    }

    private fun sourceDir(sourceId: Long) = File(dir, sourceId.toString())

    private fun read(file: File): Entry? = runCatching { json.decodeFromString<Entry>(file.readText()) }.getOrNull()

    private fun write(file: File, page: MangasPage) {
        runCatching {
            file.parentFile?.mkdirs()
            val entry = Entry(
                savedAt = now(),
                hasNextPage = page.hasNextPage,
                mangas = page.mangas.map {
                    Item(it.url, it.title, it.thumbnail_url, it.author, it.artist, it.description, it.genre, it.status)
                },
            )
            val temp = File(file.parentFile, file.name + ".part")
            temp.writeText(json.encodeToString(Entry.serializer(), entry))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private fun Entry.toPage() = MangasPage(
        mangas = mangas.map { item ->
            SManga.create().apply {
                url = item.url
                title = item.title
                thumbnail_url = item.thumbnailUrl
                author = item.author
                artist = item.artist
                description = item.description
                genre = item.genre
                status = item.status
            }
        },
        hasNextPage = hasNextPage,
    )

    companion object {
        fun fingerprint(filters: FilterList): String = filters.joinToString(";") { it.fingerprint() }

        private fun Filter<*>.fingerprint(): String = when (val value = state) {
            is List<*> -> "$name[" + value.joinToString(",") { (it as? Filter<*>)?.fingerprint() ?: it.toString() } + "]"
            else -> "$name=$value"
        }
    }
}
