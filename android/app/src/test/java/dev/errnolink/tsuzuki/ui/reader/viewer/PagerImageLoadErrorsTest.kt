// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/org/nekomanga/presentation/screens/reader/viewer/PagerImageLoadErrorsTest.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagerImageLoadErrorsTest {

    private val page = ReaderPage(index = 0, url = "url_0", imageUrl = "img_0")
    private val extraPage = ReaderPage(index = 1, url = "url_1", imageUrl = "img_1")

    @Test
    fun `no error before any image fails`() {
        val errors = PagerImageLoadErrors()

        assertFalse(errors.hasError)
        assertNull(errors.message)
    }

    @Test
    fun `failed image reports the exception message`() {
        val errors = PagerImageLoadErrors()

        errors.onError(page, IllegalStateException("Failed to initialize decoder."))

        assertTrue(errors.hasError)
        assertEquals("Failed to initialize decoder.", errors.message)
    }

    @Test
    fun `exception without a message reports its class name`() {
        val errors = PagerImageLoadErrors()

        errors.onError(page, OutOfMemoryError())

        assertEquals("OutOfMemoryError", errors.message)
    }

    @Test
    fun `other page loading keeps the failed page's error`() {
        val errors = PagerImageLoadErrors()

        errors.onError(extraPage, IllegalStateException("broken"))
        errors.onSuccess(page)

        assertTrue(errors.hasError)
        assertEquals("broken", errors.message)
    }

    @Test
    fun `failed page loading later clears its error`() {
        val errors = PagerImageLoadErrors()

        errors.onError(page, IllegalStateException("broken"))
        errors.onSuccess(page)

        assertFalse(errors.hasError)
        assertNull(errors.message)
    }

    @Test
    fun `clear drops the errors of every page`() {
        val errors = PagerImageLoadErrors()

        errors.onError(page, IllegalStateException("first"))
        errors.onError(extraPage, IllegalStateException("second"))
        errors.clear()

        assertFalse(errors.hasError)
        assertNull(errors.message)
    }
}
