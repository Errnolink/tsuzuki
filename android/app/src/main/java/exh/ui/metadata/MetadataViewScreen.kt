package exh.ui.metadata

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

class MetadataViewScreen(
    private val mangaId: Long,
    private val sourceId: Long,
) : Screen() {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { MetadataViewScreenModel(mangaId, sourceId) }
        val navigator = LocalNavigator.currentOrThrow

        val state by screenModel.state.collectAsState()

        // B34 legacy sweep: the Material Scaffold/AppBar (and the extra theme wrapper) are
        // replaced by the Tsuzuki shell; the metadata key-value list keeps long-press copy.
        ShellContentScaffold(
            title = screenModel.manga.collectAsState().value?.title.orEmpty(),
            navigateUp = navigator::pop,
        ) { paddingValues ->
            when (val state = state) {
                MetadataViewState.Loading -> LoadingScreen()
                MetadataViewState.MetadataNotFound -> EmptyScreen(MR.strings.no_results_found)
                MetadataViewState.SourceNotFound -> EmptyScreen(MR.strings.source_empty_screen)
                is MetadataViewState.Success -> {
                    val context = LocalContext.current
                    val items = remember(state.meta) { state.meta.getExtraInfoPairs(context) }
                    val colors = TsuzukiTheme.colors
                    ScrollbarLazyColumn(
                        contentPadding =
                        paddingValues + WindowInsets.navigationBars.asPaddingValues(),
                    ) {
                        items(items) { (title, text) ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onLongClick = {
                                            context.copyToClipboard(
                                                title,
                                                text,
                                            )
                                        },
                                        onClick = {},
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                TsuzukiText(
                                    title,
                                    style = TsuzukiTheme.typography.footnote,
                                    color = colors.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TsuzukiText(
                                    text,
                                    style = TsuzukiTheme.typography.subhead,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
