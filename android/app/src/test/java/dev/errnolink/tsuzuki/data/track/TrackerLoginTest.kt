package dev.errnolink.tsuzuki.data.track

import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.util.PkceUtil
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerLoginTest {
    private fun preferences(): PreferenceStore {
        val entries = mutableMapOf<String, Preference<String>>()
        return mockk<PreferenceStore>().apply {
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                val default = secondArg<String>()
                entries.getOrPut(key) { InMemoryPreferenceStore.InMemoryPreference(key, null, default) }
            }
        }
    }

    @Test
    fun `MAL verifier is fresh RFC7636 text and matches its plain challenge after process recreation`() {
        val preferences = preferences()
        val sessions = TrackerOAuth(preferences) { 1_000 }
        val first = sessions.begin("myanimelist-auth", PkceUtil.generateCodeVerifier())
        val second = sessions.begin("myanimelist-auth", PkceUtil.generateCodeVerifier())
        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.state, second.state)
        assertTrue(second.verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")))
        val request = MyAnimeListApi.authorizationUrl(second)
        assertEquals("plain", request.queryParameter("code_challenge_method"))
        assertEquals(second.state, request.queryParameter("state"))
        val restored = TrackerOAuth(preferences) { 2_000 }.consume("myanimelist-auth", second.state)
        assertNotNull(restored)
        assertEquals(restored!!.verifier, request.queryParameter("code_challenge"))
        assertNull(sessions.consume("myanimelist-auth", second.state))
    }

    @Test
    fun `wrong app provider missing nonce and replay cannot consume a pending login`() {
        val sessions = TrackerOAuth(preferences()) { 1_000 }
        val pending = sessions.begin("anilist-auth")
        assertNull(TrackerOAuth(preferences()) { 1_000 }.consume("anilist-auth", pending.state))
        assertNull(sessions.consume("bangumi-auth", pending.state))
        assertNull(sessions.consume("anilist-auth", null))
        assertNull(sessions.consume("anilist-auth", "wrong"))
        assertEquals(pending, sessions.consume("anilist-auth", pending.state))
        assertNull(sessions.consume("anilist-auth", pending.state))
    }

    @Test
    fun `expired replaced or future dated login fails closed`() {
        var now = 1_000L
        val sessions = TrackerOAuth(preferences()) { now }
        val first = sessions.begin("anilist-auth")
        val second = sessions.begin("anilist-auth")
        assertNull(sessions.consume("anilist-auth", first.state))
        now += 600_001
        assertNull(sessions.consume("anilist-auth", second.state))
        val future = sessions.begin("anilist-auth")
        now--
        assertNull(sessions.consume("anilist-auth", future.state))
    }

    @Test
    fun `blocked login times out at twenty seconds and cancels network work`() = runTest {
        var cleanedUp = false
        val failure = runCatching {
            trackerLogin(StandardTestDispatcher(testScheduler)) {
                try {
                    awaitCancellation()
                } finally {
                    cleanedUp = true
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertTrue(cleanedUp)
        assertEquals(TRACKER_LOGIN_TIMEOUT_MILLIS, currentTime)
    }

    @Test
    fun `dismissing login propagates cancellation instead of reporting success`() = runTest {
        var cancellation: Throwable? = null
        val job = backgroundScope.launch {
            cancellation = runCatching {
                trackerLogin(StandardTestDispatcher(testScheduler)) { awaitCancellation() }
            }.exceptionOrNull()
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(cancellation is CancellationException)
    }

    @Test
    fun `successful login returns immediately without waiting for timeout`() = runTest {
        val result = trackerLogin(StandardTestDispatcher(testScheduler)) {
            delay(123)
            "signed in"
        }
        assertEquals("signed in", result)
        assertEquals(123, currentTime)
    }
}
