// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/PagerImageLoadErrors.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import logcat.LogPriority
import logcat.logcat

/**
 * Images of one pager item that Coil failed to load. A page that fails to decode stays READY, so
 * its status never shows the failure and the error overlay reads it from here.
 */
@Stable
class PagerImageLoadErrors {
    private val messages = mutableStateMapOf<ReaderPage, String>()

    val hasError: Boolean
        get() = messages.isNotEmpty()

    val message: String?
        get() = messages.values.firstOrNull()

    fun onError(page: ReaderPage, throwable: Throwable) {
        logcat(LogPriority.ERROR) { "Failed to load pager image for page ${page.number}" }
        messages[page] = throwable.message ?: throwable.javaClass.simpleName
    }

    fun onSuccess(page: ReaderPage) {
        messages.remove(page)
    }

    fun clear() {
        messages.clear()
    }
}
