package exh.md

import dev.errnolink.tsuzuki.mangadex.MangaDexSessionNotification
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.md.network.MangaDexAuthInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedDeque

class MangaDexRefreshHardeningTest {

    private val apiRequests = ConcurrentLinkedDeque<Request>()
    private val refreshRequests = ConcurrentLinkedDeque<Request>()
    private val refreshResponses = ConcurrentLinkedDeque<(Request) -> Response>()
    private val apiResponses = ConcurrentLinkedDeque<(Request) -> Response>()

    private var notificationShown = false
    private var notificationDismissed = false
    private var storedToken: String? = null
    private var tokenDeleted = false

    private lateinit var client: OkHttpClient

    private fun expiredOauthJson(accessToken: String): String =
        """{"token_type":"Bearer","refresh_token":"r1","access_token":"$accessToken","expires_in":3600,"created_at":${System.currentTimeMillis() / 1000 - 7200}}"""

    private fun freshOauthJson(accessToken: String): String =
        """{"token_type":"Bearer","refresh_token":"r2","access_token":"$accessToken","expires_in":3600,"created_at":${System.currentTimeMillis() / 1000}}"""

    private fun respond(request: Request, code: Int, body: String, headers: Map<String, String> = emptyMap()): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("R")
            .headers(okhttp3.Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())
            .body(body.toResponseBody()).build()

    @BeforeEach
    fun setUp() {
        mockkObject(MangaDexSessionNotification)
        every { MangaDexSessionNotification.show() } answers { notificationShown = true }
        every { MangaDexSessionNotification.dismiss() } answers { notificationDismissed = true }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun buildClient(savedToken: String?) {
        storedToken = savedToken
        val preferences = mockk<TrackPreferences>()
        val mdList = mockk<MdList>()
        val token = mockk<Preference<String>>()
        every { preferences.trackToken(mdList) } returns token
        every { token.get() } answers { storedToken ?: "" }
        every { token.set(any()) } answers { storedToken = firstArg() }
        every { token.delete() } answers { tokenDeleted = true; storedToken = null }
        val interceptor = MangaDexAuthInterceptor(preferences, mdList)
        val script = Interceptor { chain ->
            val request = chain.request()
            if (request.url.host == "auth.mangadex.org") {
                refreshRequests.add(request)
                refreshResponses.poll()?.invoke(request)
                    ?: respond(request, 200, freshOauthJson("unexpected-refresh"))
            } else {
                apiRequests.add(request)
                apiResponses.poll()?.invoke(request) ?: respond(request, 200, "{}")
            }
        }
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(script)
            .build()
    }

    private fun apiCall() {
        client.newCall(Request.Builder().url("https://api.mangadex.org/user/follows/manga").build())
            .execute().close()
    }

    @Test
    fun `transient refresh failures are retried and then succeed`() {
        buildClient(expiredOauthJson("stale-access"))
        repeat(3) {
            refreshResponses.add { request -> respond(request, if (it == 0) 500 else if (it == 1) 503 else 429, "err") }
        }
        refreshResponses.add { request -> respond(request, 200, freshOauthJson("fresh-access")) }

        apiCall()

        assertEquals(4, refreshRequests.size)
        assertEquals("Bearer fresh-access", apiRequests.first().header("Authorization"))
        assertTrue(storedToken!!.contains("fresh-access"), "refreshed oauth must be persisted")
        assertTrue(!notificationShown, "a successful refresh must not warn")
        assertTrue(notificationDismissed, "a successful refresh dismisses the session notice")
    }

    @Test
    fun `transient exhaustion keeps the session and throttles further refreshes`() {
        buildClient(expiredOauthJson("stale-access"))
        repeat(4) { refreshResponses.add { request -> respond(request, 500, "err") } }
        apiResponses.add { request -> respond(request, 200, "{}") }

        assertThrows(IOException::class.java) { apiCall() }
        assertEquals(4, refreshRequests.size)
        assertTrue(!notificationShown, "transient exhaustion is not a rejection")
        assertTrue(!tokenDeleted, "session must survive transient exhaustion")
        assertEquals("stale-access", storedToken?.let { Regex(""""access_token":"([^"]+)"""").find(it)!!.groupValues[1] })

        apiResponses.add { request -> respond(request, 200, "{}") }
        apiCall()

        assertEquals(4, refreshRequests.size, "the 15 minute throttle must block another refresh attempt")
        assertEquals("Bearer stale-access", apiRequests.last().header("Authorization"))
    }

    @Test
    fun `persistent rejection from the auth server signs the user out without retrying`() {
        listOf(400, 401, 403).forEach { code ->
            apiRequests.clear()
            refreshRequests.clear()
            notificationShown = false
            tokenDeleted = false
            buildClient(expiredOauthJson("stale-access"))
            refreshResponses.add { request -> respond(request, code, "rejected") }

            assertThrows(IOException::class.java) { apiCall() }

            assertEquals(1, refreshRequests.size, "HTTP $code must not be retried")
            assertTrue(notificationShown, "HTTP $code must post the session-expired notification")
            assertTrue(tokenDeleted, "HTTP $code must clear the stored session")
            assertEquals(null, storedToken)
        }
    }

    @Test
    fun `a session refreshed inside the throttle window is not refreshed again`() {
        buildClient(expiredOauthJson("stale-access"))
        refreshResponses.add { request -> respond(request, 200, freshOauthJson("fresh-access")) }
        apiCall()
        assertEquals(1, refreshRequests.size)

        val expiredHeader = mapOf(
            "www-authenticate" to """Bearer error="invalid_token", error_description="The access token expired"""",
        )
        apiResponses.add { request -> respond(request, 401, "err", expiredHeader) }
        apiResponses.add { request -> respond(request, 200, "{}") }
        apiCall()

        assertEquals(1, refreshRequests.size, "the retry path must respect the 15 minute throttle")
        assertEquals(2, apiRequests.size)
        assertEquals("Bearer fresh-access", apiRequests.last().header("Authorization"))
    }
}
