package dev.errnolink.tsuzuki.mangadex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.md.utils.MdUtil

class MangaDexHomeScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MangaDexScreenModel(MangaDexDestination.Home) }
        val state by model.state.collectAsState()
        val arrivals = rememberCoverArrivalState()
        MangaDexResumeEffect(model::onResume)
        MangaDexNavigationEffect(model) { event ->
            if (event is MangaDexNavigation.Title) navigator.push(MangaScreen(event.id, true))
        }
        ShellList(
            title = "MangaDex",
            navigateUp = navigator::pop,
            actions = listOf(ShellTopAction("Refresh discover", Icons.Outlined.Refresh, model::refresh)),
        ) {
            item {
                InsetGroupedList {
                    GroupedRow("Your feed", subtitle = "Chapters from titles you follow", chevron = true, onClick = { navigator.push(MangaDexFeedScreen()) })
                    GroupedRow("MD Lists", subtitle = "Your MangaDex collections", chevron = true, onClick = { navigator.push(MangaDexListsScreen()) })
                    GroupedRow("Search creators and groups", chevron = true, divider = false, onClick = { navigator.push(MangaDexLookupScreen()) })
                }
            }
            if (!state.sourceAvailable) item {
                MangaDexEmptyState("MangaDex is unavailable", "Enable a MangaDex source in Browse first.")
            }
            state.rows.forEach { row ->
                item(key = row.key) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TsuzukiText(row.title, Modifier.weight(1f), TsuzukiTheme.typography.title3)
                            if (row.error == null) {
                                PillButton("See all", { navigator.push(MangaDexShelfScreen(row.key, row.title)) }, prominent = false)
                            }
                        }
                        if (row.error != null) {
                            InsetGroupedList { GroupedRow("Could not load this shelf", subtitle = row.error, divider = false) }
                        } else if (row.manga.isEmpty()) {
                            TsuzukiText("No titles match your content settings.", Modifier.padding(horizontal = 20.dp), color = TsuzukiTheme.colors.secondary)
                        } else {
                            LazyRow(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(row.manga, key = { it.id }) { manga ->
                                val title = MdUtil.getTitleFromManga(manga.attributes, "en", true)
                                val file = manga.relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
                                Column(Modifier.width(138.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    MangaDexRemoteCover(
                                        file?.let { MdUtil.cdnCoverUrl(manga.id, it) }, title,
                                        Modifier.fillMaxWidth(), arrivals, onClick = { model.open(manga) },
                                    )
                                    TsuzukiText(title, style = TsuzukiTheme.typography.subhead, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            } }
                        }
                    }
                }
            }
            if (state.loading) {
                item(key = "home-loading-a") { dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton() }
                item(key = "home-loading-b") { dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton() }
            }
            if (state.error != null) item {
                MangaDexEmptyState("MangaDex unavailable", state.error!!, action = { PillButton("Try again", model::refresh) })
            }
        }
    }
}

fun openMangaDexDestination(navigator: Navigator, destination: MangaDexDestination) {
    navigator.push(
        when (destination) {
            MangaDexDestination.Home -> MangaDexHomeScreen()
            MangaDexDestination.Feed -> MangaDexFeedScreen()
            MangaDexDestination.Lists -> MangaDexListsScreen()
            else -> error("This destination requires a manga or list identifier")
        },
    )
}

fun openMangaDexCovers(navigator: Navigator, mangaId: Long) {
    navigator.push(MangaDexCoverGalleryScreen(mangaId))
}
