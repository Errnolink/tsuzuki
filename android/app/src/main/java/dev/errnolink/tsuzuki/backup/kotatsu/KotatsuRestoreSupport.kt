// On-device wiring for the Kotatsu converter: content detection, source
// identities, and the MangaDex chapter resolver (local database first, then
// one MangaDex fetch). Format: docs/kotatsu-backup.md.
package dev.errnolink.tsuzuki.backup.kotatsu

import android.content.Context
import android.net.Uri
import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper.SourceIdentity
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.utils.MdUtil
import exh.source.getMainSource
import kotlinx.coroutines.flow.first
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object KotatsuRestoreSupport {

    /** Whether the picked file is a Kotatsu backup zip (by content). */
    fun isKotatsuBackup(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { KotatsuBackupReader.isKotatsuBackup(it) } ?: false
    }.getOrDefault(false)

    /**
     * Converts a Kotatsu backup uri into the app's [eu.kanade.tachiyomi.data.backup.models.Backup]
     * model. `resolveProgress = false` skips the MangaDex chapter fetches (the
     * restore screen only needs sources and counts); `requireSources = false`
     * answers before the source manager finished loading (validation only —
     * installed sources seen then are matched, the rest count as unmatched).
     */
    suspend fun convert(
        context: Context,
        uri: Uri,
        resolveProgress: Boolean = true,
        requireSources: Boolean = true,
    ): KotatsuConversion {
        val backup = context.contentResolver.openInputStream(uri)?.use { KotatsuBackupReader.read(it) }
            ?: throw IllegalStateException("Kotatsu backup could not be read")

        val sourceManager: SourceManager = Injekt.get()
        if (requireSources) sourceManager.isInitialized.first { it }
        val sources = (sourceManager.getAll() + sourceManager.getStubSources())
            .distinctBy { it.id }
            .map { SourceIdentity(it.id, it.name, it.lang, (it as? HttpSource)?.baseUrl) }

        val mangaDex = mangaDexIdentity(sourceManager)

        val converter = KotatsuBackupConverter(
            sources = sources,
            mangaDex = mangaDex,
            resolveChapters = if (resolveProgress) {
                { manga -> resolveMangaDexChapters(manga, mangaDex) }
            } else {
                { emptyList() }
            },
        )
        return runCatching { converter.convert(backup) }
            .onFailure { throw IllegalStateException("Kotatsu backup is not valid: ${it.message}", it) }
            .getOrThrow()
    }

    /** The app's own MangaDex source identity, resolved at runtime — never a literal id. */
    private fun mangaDexIdentity(sourceManager: SourceManager): SourceIdentity? {
        val enabled = MdUtil.getEnabledMangaDex(sourceManager = sourceManager)
        val mangaDex = enabled
            ?: sourceManager.getOnlineSources()
                .asSequence()
                .mapNotNull { it.getMainSource<MangaDex>() }
                .firstOrNull()
        return mangaDex?.let { SourceIdentity(it.id, it.name, it.lang, it.baseUrl) }
    }

    /**
     * Chapters of one MangaDex title in the app's url form, newest first (the source's own order,
     * `sourceOrder` 0 = newest) with recognised chapter numbers: the read sweep compares numbers,
     * and the MangaDex feed leaves many at -1. Known titles are answered from the database; new
     * titles cost one MangaDex chapter-list fetch through the source's own rate-limited client.
     */
    private suspend fun resolveMangaDexChapters(
        manga: KotatsuManga,
        mangaDex: SourceIdentity?,
    ): List<KotatsuChapter> {
        val source = mangaDex ?: return emptyList()
        val uuid = MdUtil.getMangaId(manga.url.ifBlank { manga.publicUrl })
        if (uuid.isBlank()) return emptyList()
        val url = MdUtil.buildMangaUrl(uuid)

        val existing = Injekt.get<GetMangaByUrlAndSourceId>().await(url, source.id)
        if (existing != null) {
            val chapters = Injekt.get<GetChaptersByMangaId>().await(existing.id)
            if (chapters.isNotEmpty()) {
                return chapters.sortedBy { it.sourceOrder }.map {
                    KotatsuChapter(
                        url = it.url,
                        name = it.name,
                        chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, it.name, it.chapterNumber),
                        scanlator = it.scanlator,
                        dateUpload = it.dateUpload,
                    )
                }
            }
        }

        val mdSource: MangaDex = MdUtil.getEnabledMangaDex(sourceManager = Injekt.get())
            ?: return emptyList()
        val request = SManga.create()
        request.url = url
        val fetched = runCatching {
            mdSource.getMangaUpdate(
                request,
                chapters = emptyList(),
                fetchDetails = false,
                fetchChapters = true,
            ).chapters
        }.getOrDefault(emptyList())
        return fetched.map {
            KotatsuChapter(
                url = it.url,
                name = it.name,
                chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, it.name, it.chapter_number.toDouble()),
                scanlator = it.scanlator,
                dateUpload = it.date_upload,
            )
        }
    }
}
