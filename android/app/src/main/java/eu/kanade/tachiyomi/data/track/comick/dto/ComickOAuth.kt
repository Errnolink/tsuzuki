package eu.kanade.tachiyomi.data.track.comick.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComickOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("refresh_token")
    val refreshToken: String,
    val scope: String,
) {
    fun isExpired() = System.currentTimeMillis() / 1000 + 60 >= expiresAt
}
