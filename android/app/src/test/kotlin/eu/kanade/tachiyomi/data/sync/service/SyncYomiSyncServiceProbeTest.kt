package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.domain.sync.SyncPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/**
 * Behaviour of the R2 hub-version probe at the transport level: a live endpoint is
 * read, and an endpoint that accepts the connection but never answers must yield null
 * within the probe budget instead of holding the sync pipeline for the client's full
 * read timeout (beta.28 stall).
 */
class SyncYomiSyncServiceProbeTest {

    private var server: ServerSocket? = null

    @AfterEach
    fun tearDown() {
        server?.close()
    }

    private fun service(host: String, probeTimeoutMs: Long): SyncYomiSyncService {
        val prefs = mockk<SyncPreferences>(relaxed = true)
        val hostPref = mockk<Preference<String>> { every { get() } returns host }
        val keyPref = mockk<Preference<String>> { every { get() } returns "test-key" }
        every { prefs.clientHost() } returns hostPref
        every { prefs.clientAPIKey() } returns keyPref
        return SyncYomiSyncService(
            context = mockk(relaxed = true),
            json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
            syncPreferences = prefs,
            notifier = mockk(relaxed = true),
            protoBuf = ProtoBuf,
            probeTimeoutMs = probeTimeoutMs,
        )
    }

    /** Accepts one connection, reads the request head, then either answers or holds. */
    private fun serve(response: String?): ServerSocket {
        val socket = ServerSocket(0)
        server = socket
        thread(name = "probe-test-server", isDaemon = true) {
            val client: Socket = runCatching { socket.accept() }.getOrNull() ?: return@thread
            try {
                client.getInputStream().bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                }
                if (response != null) {
                    client.getOutputStream().write(response.toByteArray())
                    client.getOutputStream().flush()
                } else {
                    // Hold the socket open without answering, like a wedged server.
                    while (!client.isClosed) {
                        if (client.getInputStream().read() == -1) break
                    }
                }
            } finally {
                runCatching { client.close() }
            }
        }
        return socket
    }

    @Test
    fun `reads the hub seq from a live version endpoint`() = runBlocking {
        val socket = serve(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: 10\r\nConnection: close\r\n\r\n{\"seq\":31}",
        )
        val probe = service("http://127.0.0.1:${socket.localPort}", probeTimeoutMs = 5_000)

        probe.probeHubVersion() shouldBe 31L
    }

    @Test
    fun `an endpoint that never answers yields null within the probe budget`() = runBlocking {
        val socket = serve(response = null)
        val probe = service("http://127.0.0.1:${socket.localPort}", probeTimeoutMs = 400)

        val elapsed = measureTimeMillis {
            probe.probeHubVersion() shouldBe null
        }

        // The strict cap fires at the budget, not at the client's 120 s read timeout.
        (elapsed < 60_000) shouldBe true
        (elapsed >= 300) shouldBe true
    }

    @Test
    fun `an http error still falls through with null`() = runBlocking {
        val body = "{\"error\":\"nope\"}"
        val socket = serve(
            "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body",
        )
        val probe = service("http://127.0.0.1:${socket.localPort}", probeTimeoutMs = 5_000)

        probe.probeHubVersion() shouldBe null
    }
}
