// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import android.app.Application
import android.util.AtomicFile
import exh.md.dto.ChapterDataDto
import exh.md.utils.MdUtil
import kotlinx.serialization.encodeToString
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object MangaDexArtworkStore {
    private fun file(kind: String, id: String): AtomicFile {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")))
        val directory = File(Injekt.get<Application>().filesDir, "mangadex").apply { mkdirs() }
        return AtomicFile(File(directory, "$kind-$id.json"))
    }

    @Synchronized
    fun saveChapters(id: String, chapters: List<ChapterDataDto>) {
        save(file("chapters", id), MdUtil.jsonParser.encodeToString((chapters + this.chapters(id)).distinctBy { it.id }))
    }

    @Synchronized
    fun chapters(id: String): List<ChapterDataDto> {
        val file = file("chapters", id)
        if (!file.baseFile.exists()) return emptyList()
        return MdUtil.jsonParser.decodeFromString(file.readFully().decodeToString())
    }

    @Synchronized
    fun saveCovers(id: String, covers: List<MangaDexArtwork>) {
        save(file("covers", id), MdUtil.jsonParser.encodeToString(covers))
    }

    @Synchronized
    fun covers(id: String): List<MangaDexArtwork> {
        val file = file("covers", id)
        if (!file.baseFile.exists()) return emptyList()
        return MdUtil.jsonParser.decodeFromString(file.readFully().decodeToString())
    }

    private fun save(file: AtomicFile, value: String) {
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
