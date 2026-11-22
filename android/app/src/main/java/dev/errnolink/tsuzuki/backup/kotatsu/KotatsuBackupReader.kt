package dev.errnolink.tsuzuki.backup.kotatsu

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Reads the zip container of a Kotatsu backup. Entry names and shapes:
 * docs/kotatsu-backup.md (Kotatsu `BackupSection`, copyleft, read as
 * documentation only; mk-bkconv MIT).
 */
object KotatsuBackupReader {

    /** Section entry names a Kotatsu backup writes (lowercase, as on the wire). */
    val SECTION_NAMES = setOf(
        "index", "history", "categories", "favourites", "settings",
        "reader_grid", "bookmarks", "sources", "scrobbling", "statistics",
        "saved_filters",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
    }

    /**
     * Whether the stream is a zip with Kotatsu sections. Content-based: a
     * `.tachibk`/`.proto.gz` (protobuf or gzip) or a cbz never matches.
     */
    fun isKotatsuBackup(input: InputStream): Boolean {
        val header = ByteArray(4)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n < 0) return false
            read += n
        }
        // Zip local-file header magic; a gzip backup starts 0x1f 0x8b instead.
        if (!(header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
                header[2] == 3.toByte() && header[3] == 4.toByte())
        ) {
            return false
        }
        val pushback = PushbackInputStream(input, header.size)
        pushback.unread(header)
        var sections = 0
        var hasPayload = false
        ZipInputStream(pushback).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                val name = entry.name.substringBefore('/').lowercase(Locale.ROOT)
                if (name in SECTION_NAMES) {
                    sections++
                    if (name == "favourites" || name == "history" || name == "bookmarks") hasPayload = true
                }
                zip.closeEntry()
            }
        }
        return sections >= 2 && hasPayload
    }

    /** Decodes the sections Tsuzuki restores; missing sections stay empty. */
    fun read(input: InputStream): KotatsuBackup {
        var favourites: List<KotatsuFavourite> = emptyList()
        var history: List<KotatsuHistoryEntry> = emptyList()
        var categories: List<KotatsuCategory> = emptyList()
        var bookmarks: List<KotatsuBookmarkGroup> = emptyList()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name.lowercase(Locale.ROOT)) {
                    "favourites" -> favourites = json.decodeSection(zip, KotatsuFavourite.serializer())
                    "history" -> history = json.decodeSection(zip, KotatsuHistoryEntry.serializer())
                    "categories" -> categories = json.decodeSection(zip, KotatsuCategory.serializer())
                    "bookmarks" -> bookmarks = json.decodeSection(zip, KotatsuBookmarkGroup.serializer())
                }
                zip.closeEntry()
            }
        }
        return KotatsuBackup(favourites, history, categories, bookmarks)
    }

    /**
     * Decodes one section's JSON array. A section is decoded whole: a large
     * favourites list is a few MB, and the sections Tsuzuki skips (settings,
     * statistics, …) are never materialised.
     */
    private fun <T> Json.decodeSection(input: InputStream, serializer: KSerializer<T>): List<T> =
        decodeFromString(ListSerializer(serializer), input.readBytes().decodeToString())
}
