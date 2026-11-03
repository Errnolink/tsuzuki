// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.network

import dev.errnolink.tsuzuki.mangadex.MangaDexSessionNotification
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.parseAs
import exh.md.utils.MdUtil
import exh.util.nullIfBlank
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MangaDexAuthInterceptor(
    private val trackPreferences: TrackPreferences,
    private val mdList: MdList,
) : Interceptor {

    var token = trackPreferences.trackToken(mdList).get().nullIfBlank()

    private var oauth: MALOAuth? = null
    private val refreshLock = Object()
    private var lastRefreshAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.url.host != "api.mangadex.org") return chain.proceed(originalRequest)

        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }
        if (oauth == null) {
            oauth = MdUtil.loadOAuth(trackPreferences, mdList)
        }
        // Refresh access token if expired
        if (oauth != null && oauth!!.isExpired()) {
            setAuth(refreshToken(chain, oauth!!))
        }

        if (oauth == null) {
            throw IOException("No authentication token")
        }

        // Add the authorization header to the original request
        val issued = oauth!!
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${issued.accessToken}")
            .build()

        val response = chain.proceed(authRequest)
        val tokenIsExpired = response.headers["www-authenticate"]
            ?.contains("The access token expired") ?: false

        // Retry the request once with a new token in case it was not already refreshed
        // by the is expired check before.
        if (response.code == 401 && tokenIsExpired) {
            val newToken = refreshToken(chain, issued)
            if (newToken != null && newToken !== issued) {
                setAuth(newToken)
                response.close()
                val newRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer ${newToken.accessToken}")
                    .build()
                return chain.proceed(newRequest)
            }
        }

        return response
    }

    /**
     * Called when the user authenticates with MangaDex for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: MALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        MdUtil.saveOAuth(trackPreferences, mdList, oauth)
        if (oauth != null) MangaDexSessionNotification.dismiss()
    }

    private fun wasTokenRefreshedRecently(): Boolean {
        return lastRefreshAt != 0L &&
            System.currentTimeMillis() - lastRefreshAt < REFRESH_THROTTLE_MILLIS
    }

    private fun refreshToken(chain: Interceptor.Chain, stale: MALOAuth): MALOAuth? = synchronized(refreshLock) {
        val current = oauth ?: return null
        if (current !== stale) return current
        if (wasTokenRefreshedRecently()) return current

        var attempt = 0
        while (attempt < MAX_REFRESH_ATTEMPTS) {
            try {
                chain.proceed(MdUtil.refreshTokenRequest(current)).use { response ->
                    if (response.isSuccessful) {
                        val refreshed = with(MdUtil.jsonParser) { response.parseAs<MALOAuth>() }
                        lastRefreshAt = System.currentTimeMillis()
                        return refreshed
                    }
                    if (MangaDexSessionNotification.isSessionRejected(response.code)) {
                        MangaDexSessionNotification.show()
                        return null
                    }
                }
            } catch (_: IOException) {
            }
            if (attempt < MAX_REFRESH_ATTEMPTS - 1) {
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS shl attempt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            attempt++
        }

        lastRefreshAt = System.currentTimeMillis()
        throw IOException(
            "MangaDex token refresh failed after $MAX_REFRESH_ATTEMPTS attempts; sign-in has been preserved",
        )
    }

    companion object {
        const val MAX_REFRESH_ATTEMPTS = 4
        const val RETRY_BACKOFF_MILLIS = 500L
        const val REFRESH_THROTTLE_MILLIS = 15 * 60 * 1000L
    }
}
