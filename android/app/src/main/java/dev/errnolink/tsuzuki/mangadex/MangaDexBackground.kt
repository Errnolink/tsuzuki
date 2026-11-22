package dev.errnolink.tsuzuki.mangadex

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.Request

/**
 * Marks MangaDex calls made by background work (the library update). Requests built inside it carry
 * an OkHttp tag (never sent) and queue behind every untagged call in the shared API limiter, so a
 * title the user opens during an update loads first.
 */
object MangaDexBackground : AbstractCoroutineContextElement(MangaDexBackgroundKey) {
    const val PRIORITY = 0

    fun isBackground(request: Request): Boolean = request.tag(MangaDexBackground::class.java) != null

    suspend fun tagIfBackground(request: Request): Request =
        if (currentCoroutineContext()[MangaDexBackgroundKey] != null) {
            request.newBuilder().tag(MangaDexBackground::class.java, MangaDexBackground).build()
        } else {
            request
        }
}

/** A separate key: an object cannot pass itself to its own superclass constructor (it is still null there). */
object MangaDexBackgroundKey : CoroutineContext.Key<MangaDexBackground>