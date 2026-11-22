// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/model/ReaderChapter.kt
package dev.errnolink.tsuzuki.reader.engine.model

import dev.errnolink.tsuzuki.reader.engine.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.chapter.model.Chapter

data class ReaderChapter(val chapter: Chapter) {

    var state: State = State.Wait
        set(value) {
            field = value
            _stateFlow.value = value
        }

    private val _stateFlow = MutableStateFlow(state)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0

    var references = 0
        private set

    fun ref() {
        references++
    }

    fun unref() {
        if (references == 0) {
            logcat(LogPriority.WARN) { "Chapter ${chapter.name} released with no references left" }
            return
        }
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat(LogPriority.DEBUG) { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            state = State.Wait
        }
    }

    fun urlAndName(): String {
        return this.chapter.url + " - " + this.chapter.name
    }

    override fun equals(other: Any?): Boolean {
        return (other is ReaderChapter) && other.urlAndName() == this.urlAndName()
    }

    override fun hashCode(): Int {
        return urlAndName().hashCode()
    }

    sealed class State {
        object Wait : State()

        object Loading : State()

        data class Error(val error: Throwable) : State()

        data class Loaded(val pages: List<ReaderPage>) : State()
    }
}
