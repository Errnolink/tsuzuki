package dev.errnolink.tsuzuki.mangadex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.md.dto.ChapterDataDto
import exh.md.handlers.MangaDexBlockChange
import exh.md.handlers.MangaDexBlocks
import exh.md.handlers.MangaDexFeatureMapper
import exh.md.handlers.MangaDexFeatures
import exh.md.service.MangaDexFeatureRequests
import exh.md.service.MangaDexService
import exh.md.utils.MdUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.chapter.repository.ChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexChapterScreen(private val mangaId: Long, private val chapterId: Long, private val openComments: Boolean = false) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { ChapterActionsModel(mangaId, chapterId, openComments) }
        val state by model.state.collectAsState()
        LaunchedEffect(model) {
            model.urls.collect { url ->
                context.startActivity(WebViewActivity.newIntent(context, url, model.state.value.source?.source?.id, "MangaDex"))
            }
        }
        ShellList("Chapter actions", navigateUp = navigator::pop) {
            state.undo?.let { item { Banner("Blocked globally", actionLabel = "Undo", onAction = model::undo) } }
            if (state.loading) item { MangaDexLoading("Loading chapter…") }
            state.chapter?.let { chapter ->
                item {
                    InsetGroupedList(title = MangaDexFeatureMapper.chapterName(chapter)) {
                        GroupedRow("Language", value = chapter.attributes.translatedLanguage.uppercase())
                        MangaDexFeatureMapper.chapterAccess(chapter)?.let { GroupedRow("Reading availability", subtitle = it) }
                        GroupedRow("Comments", chevron = true, onClick = { model.comments(false) }, enabled = !state.working)
                        GroupedRow("Open WebView", chevron = true, divider = false, onClick = { model.openWeb() })
                    }
                }
                if (state.noThread) item {
                    InsetGroupedList(footer = "MangaDex creates forum discussions only for signed-in accounts.") {
                        if (state.source?.source?.isLogged() == true) {
                            GroupedRow("Start chapter discussion", subtitle = "Create a MangaDex forum thread", onClick = { model.comments(true) }, enabled = !state.working, divider = false)
                        } else {
                            GroupedRow("Sign in to discuss", onClick = { openMangaDexSignIn(context) }, divider = false)
                        }
                    }
                }
                item {
                    InsetGroupedList(title = "Block globally", footer = "Blocks apply to all MangaDex sources and feeds. You can unblock in MangaDex settings.") {
                        chapter.relationships.filter { it.type == "scanlation_group" || it.type == "user" }.forEach { relationship ->
                            val uploader = relationship.type == "user"
                            val name = if (uploader) relationship.attributes?.username ?: "Deleted uploader" else relationship.attributes?.name ?: relationship.id
                            GroupedRow(
                                "Block ${if (uploader) "uploader" else "group"}", subtitle = name,
                                enabled = !state.working, onClick = { model.block(relationship.id, name, uploader) },
                            )
                        }
                    }
                }
            }
            state.error?.let { item { MangaDexEmptyState("Action unavailable", it) } }
        }
    }
}

private data class ChapterActionsState(
    val source: MangaDexContext? = null,
    val chapter: ChapterDataDto? = null,
    val loading: Boolean = true,
    val working: Boolean = false,
    val noThread: Boolean = false,
    val error: String? = null,
    val undo: MangaDexBlockChange? = null,
)

private class ChapterActionsModel(mangaId: Long, chapterId: Long, openComments: Boolean) : StateScreenModel<ChapterActionsState>(ChapterActionsState()) {
    private val navigation = Channel<String>(Channel.BUFFERED)
    val urls = navigation.receiveAsFlow()
    private val blocks = MangaDexBlocks()

    init {
        action {
            val local = Injekt.get<ChapterRepository>().getChapterById(chapterId) ?: error("Chapter not found")
            require(local.mangaId == mangaId) { "Chapter does not belong to this title" }
            val resolved = resolveMangaDex(local.mangaId) ?: error("This chapter is not from MangaDex")
            val dto = MangaDexService(resolved.source.client, MangaDexFeatureRequests.headers).viewChapter(MdUtil.getChapterId(local.url)).data
            mutableState.update { it.copy(source = resolved, chapter = dto) }
            if (openComments) loadComments(false)
        }
    }

    fun comments(create: Boolean) = action { loadComments(create) }

    private suspend fun loadComments(create: Boolean) {
        val state = state.value
        val thread = MangaDexFeatures(requireNotNull(state.source).source).chapterThread(requireNotNull(state.chapter).id, create)
        if (thread == null) mutableState.update { it.copy(noThread = true) }
        else navigation.send("https://forums.mangadex.org/threads/$thread")
    }

    fun openWeb() = action {
        val chapter = requireNotNull(state.value.chapter)
        navigation.send(chapter.attributes.externalUrl ?: "https://mangadex.org/chapter/${chapter.id}")
    }

    fun block(id: String, name: String, uploader: Boolean) = action {
        val change = blocks.block(id, name, uploader)
        mutableState.update { it.copy(undo = change) }
        refresh()
    }

    fun undo() = action {
        state.value.undo?.let(blocks::undo)
        mutableState.update { it.copy(undo = null) }
        refresh()
    }

    private suspend fun refresh() {
        val title = requireNotNull(state.value.source).manga
        Injekt.get<UpdateMangaFromRemote>()(title, fetchDetails = false, fetchChapters = true).getOrThrow()
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true, error = null) }
        screenModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "MangaDex could not complete this action") }
            } finally {
                mutableState.update { it.copy(loading = false, working = false) }
            }
        }
    }
}
