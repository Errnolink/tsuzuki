package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.CoverArrivalState
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.mangadex.MangaDexDestination
import dev.errnolink.tsuzuki.mangadex.MangaDexFeedListItems
import dev.errnolink.tsuzuki.mangadex.MangaDexNavigation
import dev.errnolink.tsuzuki.mangadex.MangaDexNavigationEffect
import dev.errnolink.tsuzuki.mangadex.MangaDexNoticeOverlay
import dev.errnolink.tsuzuki.mangadex.MangaDexResumeEffect
import dev.errnolink.tsuzuki.mangadex.MangaDexScreenModel
import dev.errnolink.tsuzuki.mangadex.MangaDexState
import dev.errnolink.tsuzuki.ui.history.rememberHistoryFeedContent
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.SkeletonCrossfade
import dev.errnolink.tsuzuki.ui.shell.lateralScrollTo
import dev.errnolink.tsuzuki.ui.updates.rememberUpdatesFeedContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SegmentOptions = listOf("MangaDex", "Updates", "History")

class ClassicFeedScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MangaDexScreenModel(MangaDexDestination.Feed) }
        val state by screenModel.state.collectAsState()
        MangaDexNavigationEffect(screenModel) { navigation ->
            when (navigation) {
                is MangaDexNavigation.Title -> navigator.push(MangaScreen(navigation.id, true))
                is MangaDexNavigation.Chapter -> context.startActivity(
                    ReaderActivity.newIntent(context, navigation.mangaId, navigation.chapterId),
                )
            }
        }
        MangaDexResumeEffect(screenModel::onResume)
        val arrivals = rememberCoverArrivalState()
        val updatesContent = rememberUpdatesFeedContent()
        val historyContent = rememberHistoryFeedContent()
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState { SegmentOptions.size }
        val motion = TsuzukiTheme.motion
        val firstSegmentJump = remember { mutableStateOf(true) }
        LaunchedEffect(FeedTab.pendingSegment) {
            if (FeedTab.pendingSegment >= 0) {
                val target = FeedTab.pendingSegment
                FeedTab.pendingSegment = -1
                if (motion.reduced || firstSegmentJump.value) {
                    pagerState.scrollToPage(target)
                } else {
                    pagerState.lateralScrollTo(target, motion.reduced)
                }
                firstSegmentJump.value = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            ShellContentScaffold(
                title = "Feed",
                header = {
                    SegmentedControl(
                        options = SegmentOptions,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { target ->
                            scope.launch {
                                pagerState.lateralScrollTo(target, motion.reduced)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = 8.dp),
                    )
                },
            ) { padding ->
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        FeedTab.SEGMENT_UPDATES -> FeedSegmentList(
                            rememberLazyListState(),
                            padding,
                            loading = updatesContent.loading,
                            content = updatesContent.content,
                            skeleton = updatesContent.skeleton,
                        )
                        FeedTab.SEGMENT_HISTORY -> FeedSegmentList(
                            rememberLazyListState(),
                            padding,
                            loading = false,
                            content = historyContent,
                        )
                        else -> FeedPage(state, arrivals, screenModel, padding)
                    }
                }
            }
            if (pagerState.settledPage == FeedTab.SEGMENT_FEED && state.entries.isNotEmpty() && (state.error != null || state.notice != null)) {
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding()) {
                    MangaDexNoticeOverlay(state)
                }
            }
        }
    }
}

@Composable
fun ClassicFeedContent() {
    ClassicFeedScreen().Content()
}

@Composable
private fun FeedSegmentList(
    state: LazyListState,
    contentPadding: PaddingValues,
    loading: Boolean,
    content: LazyListScope.() -> Unit,
    skeleton: LazyListScope.() -> Unit = content,
) {
    SkeletonCrossfade(
        loading = loading,
        skeleton = {
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = contentPadding,
            ) {
                skeleton()
            }
        },
        content = {
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = contentPadding,
            ) {
                content()
            }
        },
    )
}

@Composable
private fun FeedPage(
    state: MangaDexState,
    arrivals: CoverArrivalState,
    screenModel: MangaDexScreenModel,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.hasNext) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (total > 0 && last >= total - 4) screenModel.loadMore()
        }
    }
    LazyColumn(
        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.section),
        contentPadding = contentPadding,
    ) {
        MangaDexFeedListItems(state, arrivals, screenModel)
    }
}
