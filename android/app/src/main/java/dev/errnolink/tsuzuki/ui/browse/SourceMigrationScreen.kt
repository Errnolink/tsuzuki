package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab

class SourceMigrationScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        BrowseTabWrapper(migrateSourceTab(), navigator::pop)
    }
}
