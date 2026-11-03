package exh.recs.sources

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.handlers.MangaDexFeatures
import exh.md.utils.MdUtil
import exh.source.getMainSource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class MangaDexRecommendedPagingSource(
    manga: Manga,
    private val recommendationSource: RecommendationSource,
) : RecommendationPagingSource(manga, recommendationSource) {
    override val name = "MangaDex · Recommended"
    override val associatedSourceId = recommendationSource.id

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val source = Injekt.get<SourceManager>().get(associatedSourceId)?.getMainSource<MangaDex>() ?: throw NoResultsException()
        val features = MangaDexFeatures(source)
        val result = features.recommendations(MdUtil.getMangaId(manga.url))
        if (result.isEmpty()) throw NoResultsException()
        return MangasPage(result.map { MdUtil.createMangaEntry(it, features.language) }, false)
    }
}
