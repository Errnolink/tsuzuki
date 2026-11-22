package eu.kanade.tachiyomi.ui.home

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.errnolink.tsuzuki.ui.shell.HomeShell
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object HomeScreen : Screen() {
    private fun readResolve(): Any = HomeScreen
    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()
    private val reselect = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reselectEvents = reselect.asSharedFlow()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(tab = LibraryTab, key = "HomeTabs") { tabNavigator ->
            HomeShell(
                tabNavigator = tabNavigator,
                navigator = navigator,
                librarySearchEvents = librarySearchEvent,
                openTabEvents = openTabEvent,
                showBottomNavEvents = showBottomNavEvent,
                reselect = reselect,
            )
        }
    }

    suspend fun search(query: String) { librarySearchEvent.send(query) }
    suspend fun openTab(tab: Tab) { openTabEvent.send(tab) }
    suspend fun showBottomNav(show: Boolean) { showBottomNavEvent.send(show) }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(val toDownloads: Boolean, val toLibraryUpdateErrors: Boolean = false) : Tab
    }
}
