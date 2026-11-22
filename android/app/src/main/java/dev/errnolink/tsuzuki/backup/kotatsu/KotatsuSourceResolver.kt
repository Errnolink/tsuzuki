// Source matching for Kotatsu backups. The curated parser->Mihon-name map and
// the deterministic Mihon source-id derivation are ported from galpt/mk-bkconv
// (MIT, https://github.com/galpt/mk-bkconv/blob/main/pkg/convert/source_mapping.go);
// see docs/THIRD-PARTY-NOTICES.md. Matching against installed sources follows
// the same name/domain rules the Neko restore uses (NekoBackupMapper.resolveSource).
package dev.errnolink.tsuzuki.backup.kotatsu

import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import java.net.URI
import java.security.MessageDigest

/** A source identity the converter resolved for one Kotatsu parser name. */
data class KotatsuSourceMatch(
    val id: Long,
    val name: String,
    /** True when no installed/stub source carries this id; entries restore as stubs. */
    val unmatched: Boolean,
)

object KotatsuSourceResolver {

    /** Kotatsu parser name (and language) of a real Mihon source, from mk-bkconv. */
    private val CURATED: Map<String, Pair<String, String>> = mapOf(
        "MANGAPARK" to ("MangaPark" to "en"),
        "MANGAFIRE_EN" to ("mangafire" to "en"),
        "MADARA" to ("madara" to "all"),
        "MANGABOX" to ("mangabox" to "all"),
        "MANGACATALOG" to ("mangacatalog" to "all"),
        "MANGADVENTURE" to ("mangadventure" to "all"),
        "MANGAESP" to ("mangaesp" to "es"),
        "MANGAHUB" to ("mangahub" to "all"),
        "MANGARAW" to ("mangaraw" to "all"),
        "MANGAREADER" to ("mangareader" to "all"),
        "MANGATHEMESIA" to ("mangathemesia" to "all"),
        "MANGAWORLD" to ("mangaworld" to "all"),
        "MANHWAZ" to ("manhwaz" to "all"),
        "MMLOOK" to ("mmlook" to "all"),
        "MMRCMS" to ("mmrcms" to "all"),
        "MONOCHROME" to ("monochrome" to "all"),
        "MULTICHAN" to ("multichan" to "all"),
        "PAPRIKA" to ("paprika" to "all"),
        "PEACHSCAN" to ("peachscan" to "all"),
        "PIZZAREADER" to ("pizzareader" to "all"),
        "READERFRONT" to ("readerfront" to "all"),
        "SCANR" to ("scanr" to "all"),
        "SENKURO" to ("senkuro" to "all"),
        "TERRASCAN" to ("terrascan" to "all"),
        "VERCOMICS" to ("vercomics" to "all"),
        "WPCOMICS" to ("wpcomics" to "all"),
        "YUYU" to ("yuyu" to "all"),
        "ZBULU" to ("zbulu" to "all"),
        "ZEISTMANGA" to ("zeistmanga" to "all"),
        "ZMANGA" to ("zmanga" to "all"),
        "COMICIVIEWER" to ("comiciviewer" to "all"),
        "FANSUBSCAT" to ("fansubscat" to "all"),
        "GIGAVIEWER" to ("gigaviewer" to "all"),
        "GMANGA" to ("gmanga" to "all"),
        "MADTHEME" to ("madtheme" to "all"),
        "LILIANA" to ("liliana" to "all"),
        "HENTAIFOX" to ("HentaiFox" to "all"),
        "DEMONICSCANS" to ("Manga Demon" to "en"),
        "AQUAMANGA" to ("Aqua Manga" to "en"),
        "LIKEMANGA" to ("LikeManga" to "en"),
        "OMEGASCANS" to ("Omega Scans" to "en"),
        "MANGAEFFECT" to ("MangaRead.org" to "en"),
        "HARIMANGA" to ("Harimanga" to "en"),
        "DARK_SCANS" to ("Dark Scans" to "en"),
        "ASURASCANS" to ("Asura Scans" to "en"),
        "FLAMECOMICS" to ("Flame Comics" to "en"),
        "MANGAGEKO" to ("MangaGeko" to "en"),
        "ENTHUNDERSCANS" to ("Thunder Scans" to "en"),
        "COMICK_FUN" to ("Comick (Unoriginal)" to "en"),
        "FREEMANGATOP" to ("FreeMangaTop" to "en"),
        "MANGATOWN" to ("MangaTown" to "en"),
        "MAGUSMANGA" to ("Magus Manga" to "en"),
        "NIGHTSCANS" to ("Qi Scans" to "en"),
        "MANHUAFASTNET" to ("ManhuaFast.net (unoriginal)" to "en"),
        "MANHUAFAST" to ("ManhuaFast" to "en"),
        "TOONILY" to ("Toonily" to "en"),
        "DRAKESCANS" to ("Drake Scans" to "en"),
        "MANHWA18CC" to ("Manhwa18.cc" to "all"),
    )

    /** Kotatsu parser name for MangaDex (`@MangaSourceParser("MANGADEX", …)`). */
    const val MANGADEX_PARSER = "MANGADEX"

    /**
     * Resolves one parser name onto a source identity.
     *
     * @param parserName the `manga.source` string from the backup
     * @param publicUrl the manga's absolute url, used for domain matching
     * @param sources installed and stub source identities
     * @param mangaDex the app's MangaDex source identity, resolved at runtime
     */
    fun resolve(
        parserName: String,
        publicUrl: String?,
        sources: List<NekoBackupMapper.SourceIdentity>,
        mangaDex: NekoBackupMapper.SourceIdentity?,
    ): KotatsuSourceMatch {
        if (parserName.equals(MANGADEX_PARSER, ignoreCase = true)) {
            return mangaDexMatch(mangaDex)
                ?: KotatsuSourceMatch(mihonSourceId("MangaDex", "all", 1), "MangaDex", unmatched = true)
        }

        val host = host(publicUrl)

        // Exact name match on an installed source (host, when known, breaks ties).
        NekoBackupMapper.resolveSource(parserName, host, sources)?.let {
            return KotatsuSourceMatch(it.id, it.name, unmatched = false)
        }

        val curated = CURATED[parserName.uppercase()]

        // The site domain of the backup decides between same-named sources.
        if (host != null) {
            val byHost = sources.filter { host(it.baseUrl) == host }
            (byHost.singleOrNull() ?: byHost.filter { it.language == "en" }.singleOrNull())?.let {
                return KotatsuSourceMatch(it.id, it.name, unmatched = false)
            }
        }

        // A curated Mihon name may still be installed under that name.
        if (curated != null) {
            NekoBackupMapper.resolveSource(curated.first, host, sources)?.let {
                return KotatsuSourceMatch(it.id, it.name, unmatched = false)
            }
            return KotatsuSourceMatch(mihonSourceId(curated.first, curated.second, 1), curated.first, unmatched = true)
        }

        val name = humanise(parserName)
        return KotatsuSourceMatch(mihonSourceId(name, langSuffix(parserName) ?: "en", 1), name, unmatched = true)
    }

    private fun mangaDexMatch(mangaDex: NekoBackupMapper.SourceIdentity?): KotatsuSourceMatch? =
        mangaDex?.let { KotatsuSourceMatch(it.id, it.name, unmatched = false) }

    /**
     * Mihon's source-id convention: the first 8 bytes of
     * `MD5("<name.lowercase()>/<lang>/<versionId>")` big-endian, sign bit
     * cleared (mk-bkconv `GenerateMihonSourceID`).
     */
    fun mihonSourceId(name: String, lang: String, versionId: Int): Long {
        val digest = MessageDigest.getInstance("MD5")
            .digest("${name.lowercase()}/$lang/$versionId".toByteArray(Charsets.UTF_8))
        var id = 0L
        for (i in 0 until 8) {
            id = id or (digest[i].toLong() and 0xff shl (8 * (7 - i)))
        }
        return id and 0x7fffffffffffffffL
    }

    /** "MANGAFIRE_EN" -> "Mangafire"; "READMANGARU" -> "Readmangaru". */
    internal fun humanise(parserName: String): String =
        parserName.substringBeforeLast('_').replace('_', ' ')
            .lowercase().replaceFirstChar { it.uppercase() }

    /** "MANGAFIRE_EN" -> "en", "MANGA_DUNGEON" -> null. */
    internal fun langSuffix(parserName: String): String? {
        val suffix = parserName.substringAfterLast('_', "")
        return suffix.takeIf { it.length in 2..5 && suffix.all(Char::isLetter) }?.lowercase()
    }

    private fun host(url: String?): String? =
        url?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }
}
