package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class DeepLinkScreen(
    val query: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            DeepLinkScreenModel(query = query)
        }
        val state by screenModel.state.collectAsState()
        // B34 legacy sweep: the Material Scaffold/AppBar is replaced by the Tsuzuki shell for the
        // loading state; resolution still replaces the screen or opens the reader directly.
        ShellContentScaffold(
            title = stringResource(MR.strings.action_search_hint),
            navigateUp = navigator::pop,
        ) { contentPadding ->
            when (state) {
                is DeepLinkScreenModel.State.Loading -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }
                is DeepLinkScreenModel.State.NoResults -> {
                    navigator.replace(GlobalSearchScreen(query))
                }
                is DeepLinkScreenModel.State.Result -> {
                    val resultState = state as DeepLinkScreenModel.State.Result
                    if (resultState.chapterId == null) {
                        navigator.replace(
                            MangaScreen(
                                resultState.manga.id,
                                true,
                            ),
                        )
                    } else {
                        navigator.pop()
                        ReaderActivity.newIntent(
                            context,
                            resultState.manga.id,
                            resultState.chapterId,
                        ).also(context::startActivity)
                    }
                }
            }
        }
    }
}
