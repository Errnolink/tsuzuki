package eu.kanade.tachiyomi.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.errnolink.tsuzuki.ui.feed.FeedTabContent
import eu.kanade.presentation.util.Tab

data object FeedTab : Tab {

    const val SEGMENT_FEED = 0
    const val SEGMENT_UPDATES = 1
    const val SEGMENT_HISTORY = 2

    var pendingSegment by mutableIntStateOf(-1)

    fun openSegment(value: Int) {
        pendingSegment = value.coerceIn(SEGMENT_FEED, SEGMENT_HISTORY)
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 1u, title = "Feed", icon = null)

    @Composable
    override fun Content() {
        FeedTabContent()
    }
}
