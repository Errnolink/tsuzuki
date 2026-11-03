package dev.errnolink.tsuzuki.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PairLinkParserTest {

    @Test
    fun `parses a valid link with name`() {
        assertEquals(
            PairRequest(
                host = "http://192.168.1.5:7351",
                key = "K7Q2-M9XD-4HPT",
                name = "Living room PC",
            ),
            parsePairLink(
                "tsuzuki://pair?host=http%3A%2F%2F192.168.1.5%3A7351&key=K7Q2-M9XD-4HPT&name=Living%20room%20PC",
            ),
        )
    }

    @Test
    fun `parses a url-encoded host without a name`() {
        assertEquals(
            PairRequest(host = "http://10.0.0.2:8080", key = "abc123", name = null),
            parsePairLink("tsuzuki://pair?host=http%3A%2F%2F10.0.0.2%3A8080&key=abc123"),
        )
    }

    @Test
    fun `accepts a raw unencoded host`() {
        assertEquals(
            PairRequest(host = "http://192.168.1.20:7351", key = "k", name = null),
            parsePairLink("tsuzuki://pair?host=http://192.168.1.20:7351&key=k"),
        )
    }

    @Test
    fun `strips a trailing slash from the host`() {
        assertEquals(
            PairRequest(host = "http://192.168.1.5:7351", key = "abc123", name = null),
            parsePairLink("tsuzuki://pair?host=http%3A%2F%2F192.168.1.5%3A7351%2F&key=abc123"),
        )
    }

    @Test
    fun `missing key is rejected`() {
        assertNull(parsePairLink("tsuzuki://pair?host=http%3A%2F%2F192.168.1.5%3A7351&name=PC"))
        assertNull(parsePairLink("tsuzuki://pair?host=http%3A%2F%2F192.168.1.5%3A7351&key=&name=PC"))
    }

    @Test
    fun `non-http host is rejected`() {
        assertNull(parsePairLink("tsuzuki://pair?host=ftp%3A%2F%2F192.168.1.5&key=abc123"))
        assertNull(parsePairLink("tsuzuki://pair?host=http%3A%2F%2F&key=abc123"))
    }

    @Test
    fun `wrong scheme is rejected`() {
        assertNull(
            parsePairLink(
                "syncyomi://pair?host=http%3A%2F%2F192.168.1.5%3A7351&key=abc123",
            ),
        )
        assertNull(
            parsePairLink(
                "https://example.com/pair?host=http%3A%2F%2F192.168.1.5%3A7351&key=abc123",
            ),
        )
    }

    @Test
    fun `wrong link host is rejected`() {
        assertNull(parsePairLink("tsuzuki://device?host=http%3A%2F%2F192.168.1.5&key=abc123"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(parsePairLink(""))
        assertNull(parsePairLink("not a link"))
        assertNull(parsePairLink("tsuzuki://pair"))
    }
}
