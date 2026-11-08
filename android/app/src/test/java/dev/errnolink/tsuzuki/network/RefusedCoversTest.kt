package dev.errnolink.tsuzuki.network

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class RefusedCoversTest {

    private var clock = 0L
    private val covers = RefusedCovers(holdMillis = 30 * 60_000L, now = { clock })
    private val url = "https://img.example.org/cover.jpg"

    @Test
    fun `a cover the site refused is not requested again for half an hour`() {
        covers.onResponse(url, 403)
        clock += 29 * 60_000L
        assertThrows<IOException> { covers.check(url) }
        clock += 2 * 60_000L
        assertDoesNotThrow { covers.check(url) }
    }

    @Test
    fun `missing and gone covers are held back too`() {
        covers.onResponse(url, 404)
        assertThrows<IOException> { covers.check(url) }
        covers.onResponse("$url?2", 410)
        assertThrows<IOException> { covers.check("$url?2") }
    }

    @Test
    fun `a broken certificate holds the cover back`() {
        covers.onFailure(url, SSLHandshakeException("bad cert"))
        assertThrows<IOException> { covers.check(url) }
    }

    @Test
    fun `server errors and timeouts are retried right away`() {
        covers.onResponse(url, 503)
        covers.onFailure("$url?t", IOException("timeout"))
        assertDoesNotThrow { covers.check(url) }
        assertDoesNotThrow { covers.check("$url?t") }
    }

    @Test
    fun `tapping retry asks again immediately`() {
        covers.onResponse(url, 403)
        covers.forget(url)
        assertDoesNotThrow { covers.check(url) }
    }

    @Test
    fun `tapping retry on a cover also clears its smaller size variants`() {
        covers.onResponse("$url.256.jpg", 403)
        covers.forget(url)
        assertDoesNotThrow { covers.check("$url.256.jpg") }
    }
}
