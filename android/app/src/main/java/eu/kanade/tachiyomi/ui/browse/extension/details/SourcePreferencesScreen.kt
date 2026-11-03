package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.browse.SourcePreferencesContent
import eu.kanade.presentation.util.Screen

class SourcePreferencesScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        SourcePreferencesContent(
            sourceId = sourceId,
            navigateUp = navigator::pop,
        )
    }
}
