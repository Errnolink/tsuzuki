package dev.errnolink.tsuzuki.ui.settings

import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.extension.model.Extension
import exh.source.MERGED_SOURCE_ID
import java.net.URI

data class NeededSource(val name: String, val id: Long?, val host: String?, val titles: Int)

data class ExtensionMatch(val needed: NeededSource, val extension: Extension.Available?, val title: String)

object RestoreExtensions {

    fun needed(backup: Backup, installed: List<NekoBackupMapper.SourceIdentity>): List<NeededSource> {
        val installedIds = installed.mapTo(hashSetOf()) { it.id }
        val names = backup.backupSources.associate { it.sourceId to it.name }
        val bySourceId = backup.backupManga
            .filter { it.source != MERGED_SOURCE_ID && it.source != 0L && it.source !in installedIds }
            .groupingBy { it.source }
            .eachCount()
            .map { (id, count) -> NeededSource(names[id]?.takeIf(String::isNotBlank) ?: id.toString(), id, null, count) }

        val nekoMerges = backup.backupManga.flatMap { manga ->
            val merges = manga.mergeMangaList.ifEmpty {
                manga.mergedMangaUrl?.takeIf(String::isNotBlank)?.let { url ->
                    listOf(dev.errnolink.tsuzuki.mangadex.backup.BackupMergeManga(url, manga.title, "", 0))
                }.orEmpty()
            }
            if (manga.source == MERGED_SOURCE_ID || manga.mergedMangaReferences.isNotEmpty()) return@flatMap emptyList()
            merges.map { NekoBackupMapper.sourceName(it.mergeType) to host(it.url) }.distinct()
        }
            .filter { (name, host) -> NekoBackupMapper.resolveSource(name, host, installed) == null }
            .groupBy({ it.first }, { it.second })
            .map { (name, hosts) -> NeededSource(name, null, hosts.firstNotNullOfOrNull { it }, hosts.size) }

        return (bySourceId + nekoMerges).sortedByDescending { it.titles }
    }

    fun match(needed: List<NeededSource>, available: List<Extension.Available>): List<ExtensionMatch> =
        needed.map { source ->
            val extension = find(source, available)
            ExtensionMatch(source, extension, title(source, extension))
        }

    private fun title(source: NeededSource, extension: Extension.Available?): String {
        val id = source.id ?: return source.name
        if (source.name != id.toString()) return source.name
        return extension?.sources?.firstOrNull { it.id == id }?.name ?: source.name
    }

    private fun find(source: NeededSource, available: List<Extension.Available>): Extension.Available? {
        source.id?.let { id -> available.firstOrNull { ext -> ext.sources.any { it.id == id } }?.let { return it } }
        val wanted = normalize(source.name)
        val named = available.flatMap { ext ->
            ext.sources.filter { normalize(it.name) == wanted }.map { Triple(ext, it.lang, host(it.baseUrl)) }
        }
        val candidates = named.filter { source.host != null && it.third == source.host }.ifEmpty { named }
        return (
            candidates.firstOrNull { it.second == "en" }
                ?: candidates.firstOrNull { it.second == "all" }
                ?: candidates.firstOrNull()
            )?.first
    }

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
}
