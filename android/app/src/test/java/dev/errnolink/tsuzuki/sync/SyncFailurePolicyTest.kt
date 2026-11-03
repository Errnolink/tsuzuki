package dev.errnolink.tsuzuki.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncFailurePolicyTest {

    // Connection refused, connect timeout, DNS failure, no route, and the catch-all
    // transport IOException (reset mid-connection).
    private val connectionErrors = listOf(
        ConnectException("Connection refused"),
        SocketTimeoutException("connect timed out"),
        UnknownHostException("Unable to resolve host"),
        NoRouteToHostException("Host unreachable"),
        IOException("Connection reset by peer"),
    )

    // 401 and other HTTP failures arrive as plain exceptions, never IOExceptions.
    private val protocolErrors = listOf(
        IllegalStateException("SyncYomi authentication failed (401): check the API key"),
        RuntimeException("SyncYomi v2 merge failed (500)"),
    )

    @Test
    fun `automatic runs stay quiet for connection errors`() {
        connectionErrors.forEach { error ->
            assertFalse(
                SyncFailurePolicy.shouldNotifySyncFailure(isAutomatic = true, error = error),
                "expected silence for ${error::class.simpleName}",
            )
        }
    }

    @Test
    fun `manual runs still report connection errors`() {
        connectionErrors.forEach { error ->
            assertTrue(
                SyncFailurePolicy.shouldNotifySyncFailure(isAutomatic = false, error = error),
                "expected a notification for ${error::class.simpleName}",
            )
        }
    }

    @Test
    fun `automatic runs still report protocol failures`() {
        protocolErrors.forEach { error ->
            assertTrue(
                SyncFailurePolicy.shouldNotifySyncFailure(isAutomatic = true, error = error),
                "expected a notification for ${error::class.simpleName}",
            )
        }
    }

    @Test
    fun `manual runs still report protocol failures`() {
        protocolErrors.forEach { error ->
            assertTrue(
                SyncFailurePolicy.shouldNotifySyncFailure(isAutomatic = false, error = error),
                "expected a notification for ${error::class.simpleName}",
            )
        }
    }

    @Test
    fun `hub reachability follows the transport error type`() {
        assertTrue(SyncFailurePolicy.isHubUnreachable(SocketTimeoutException("read timed out")))
        assertFalse(SyncFailurePolicy.isHubUnreachable(IllegalStateException("missing X-Sync-Cursor")))
    }
}
