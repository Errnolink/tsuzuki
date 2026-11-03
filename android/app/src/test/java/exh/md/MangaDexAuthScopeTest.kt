package exh.md

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.md.network.MangaDexAuthInterceptor
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class MangaDexAuthScopeTest {
    @Test
    fun `seasonal and cover requests never receive a MangaDex bearer token`() {
        val preferences = mockk<TrackPreferences>()
        val mdList = mockk<MdList>()
        val token = mockk<Preference<String>>()
        every { preferences.trackToken(mdList) } returns token
        every { token.get() } returns "not-a-live-token"
        val client = OkHttpClient.Builder().addInterceptor(MangaDexAuthInterceptor(preferences, mdList)).addInterceptor { chain ->
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("".toResponseBody()).build()
        }.build()
        try {
            listOf("https://antsylich.github.io/mangadex-seasonal/seasonal-list.min.json", "https://uploads.mangadex.org/covers/test.jpg").forEach {
                client.newCall(Request.Builder().url(it).build()).execute().close()
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
