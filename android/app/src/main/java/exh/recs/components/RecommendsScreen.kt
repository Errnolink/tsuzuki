package exh.recs.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.browse.GlobalSearchCardRow
import dev.errnolink.tsuzuki.ui.shell.CoverRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import eu.kanade.presentation.util.formattedMessage
import exh.recs.RecommendationItemResult
import exh.recs.RecommendsScreenModel
import exh.recs.sources.RecommendationPagingSource
import kotlinx.collections.immutable.ImmutableMap
import nl.adaptivity.xmlutil.core.impl.multiplatform.name
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.i18n.stringResource

// B34 legacy sweep: the Material Scaffold/AppBar and the GlobalSearchResultItem rows are replaced
// by the Tsuzuki shell and the shared cover-card row; the recommendation sources and their
// loading/success/error states keep their upstream behaviour.
@Composable
fun RecommendsScreen(
    title: String,
    state: RecommendsScreenModel.State,
    navigateUp: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (RecommendationPagingSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    ShellContentScaffold(
        title = title,
        navigateUp = navigateUp,
    ) { paddingValues ->
        RecommendsContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun RecommendsContent(
    items: ImmutableMap<RecommendationPagingSource, RecommendationItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (RecommendationPagingSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items.forEach { (source, recResult) ->
            item(key = "${source::class.name}-${source.name}-${source.category.resourceId}") {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickSource(source) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TsuzukiText(
                                source.name,
                                style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Bold),
                            )
                            TsuzukiText(
                                stringResource(source.category),
                                style = TsuzukiTheme.typography.footnote,
                                color = colors.secondary,
                            )
                        }
                        TsuzukiText(
                            "See all",
                            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                        )
                    }
                    when (recResult) {
                        RecommendationItemResult.Loading -> {
                            CoverRowSkeleton()
                        }
                        is RecommendationItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = recResult.result,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                                selection = emptyList(),
                            )
                        }
                        is RecommendationItemResult.Error -> {
                            TsuzukiText(
                                with(LocalContext.current) {
                                    recResult.throwable.formattedMessage
                                },
                                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                TsuzukiTheme.typography.footnote,
                                colors.destructive,
                            )
                        }
                    }
                }
            }
        }
    }
}
