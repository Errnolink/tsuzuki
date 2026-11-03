package exh.ui.smartsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Search source for entries, either search for entries to merge or search for recommending entry.
 */
class SmartSearchScreen(
    private val sourceId: Long,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SmartSearchScreenModel(sourceId, smartSearchConfig) }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val state by screenModel.state.collectAsState()
        LaunchedEffect(state) {
            val results = state
            if (results != null) {
                if (results is SmartSearchScreenModel.SearchResults.Found) {
                    navigator.replace(
                        MangaScreen(
                            results.manga.id,
                            // KMK -->
                            // Finding the entry to be merged to, so we don't want to expand description
                            // so that user can see the `Merge to another` button
                            false,
                            // KMK <--
                            smartSearchConfig,
                        ),
                    )
                } else {
                    if (results is SmartSearchScreenModel.SearchResults.NotFound) {
                        context.toast(SYMR.strings.could_not_find_entry)
                    } else {
                        context.toast(SYMR.strings.automatic_search_error)
                    }
                    navigator.replace(
                        BrowseSourceScreen(
                            sourceId = screenModel.source.id,
                            listingQuery = smartSearchConfig.origTitle,
                            smartSearchConfig = smartSearchConfig,
                        ),
                    )
                }
            }
        }

        // B34 legacy sweep: the Material Scaffold/AppBar is replaced by the Tsuzuki shell while
        // the automatic search keeps its navigation behaviour.
        ShellContentScaffold(
            title = screenModel.source.name,
            navigateUp = navigator::pop,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                TsuzukiText(
                    text = stringResource(SYMR.strings.searching_source),
                    style = TsuzukiTheme.typography.title3,
                )
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            }
        }
    }
}
