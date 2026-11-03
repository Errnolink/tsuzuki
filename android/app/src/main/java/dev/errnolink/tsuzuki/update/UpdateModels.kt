package dev.errnolink.tsuzuki.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long = 0L,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("versionCode") val altVersionCode: Long? = null,
)

data class ReleaseCandidate(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val assetName: String,
)

sealed interface InstallDecision {
    data object Success : InstallDecision
    data object PromptUser : InstallDecision
    data object FallbackPrompt : InstallDecision
    data object Aborted : InstallDecision
}

sealed interface CheckResult {
    data class NewUpdate(val candidate: ReleaseCandidate) : CheckResult
    data object NoNewUpdate : CheckResult
    data class Error(val throwable: Throwable) : CheckResult
}
