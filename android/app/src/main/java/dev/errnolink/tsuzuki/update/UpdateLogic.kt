package dev.errnolink.tsuzuki.update

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object UpdateLogic {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val tagVersionCodeRegex = Regex("""^v?(\d+)-(.+)$""")
    private val tagNumericOnlyRegex = Regex("""^v?(\d+)$""")
    private val semverPrefixRegex = Regex("""^v?(\d+(?:\.\d+)*)""")

    const val ANDROID_TAG_PREFIX = "android-"

    fun parseRelease(jsonString: String): GithubRelease {
        return json.decodeFromString(GithubRelease.serializer(), jsonString)
    }

    fun parseReleases(jsonString: String): List<GithubRelease> {
        return json.decodeFromString(ListSerializer(GithubRelease.serializer()), jsonString)
    }

    fun latestAndroidRelease(releases: List<GithubRelease>): GithubRelease? {
        return releases
            .filter { !it.draft && !it.prerelease && it.tagName.startsWith(ANDROID_TAG_PREFIX) }
            .maxWithOrNull { a, b -> compareSemver(versionTag(a.tagName), versionTag(b.tagName)) }
    }

    private fun versionTag(tagName: String): String = tagName.removePrefix(ANDROID_TAG_PREFIX)

    fun extractVersionCode(tagName: String, assetVersionCode: Long? = null): Long? {
        if (assetVersionCode != null && assetVersionCode > 0L) {
            return assetVersionCode
        }
        val matchComposite = tagVersionCodeRegex.matchEntire(tagName)
        if (matchComposite != null) {
            return matchComposite.groupValues[1].toLongOrNull()
        }
        val matchNumeric = tagNumericOnlyRegex.matchEntire(tagName)
        if (matchNumeric != null) {
            return matchNumeric.groupValues[1].toLongOrNull()
        }
        return null
    }

    fun extractVersionName(tagName: String): String {
        val matchComposite = tagVersionCodeRegex.matchEntire(tagName)
        if (matchComposite != null) {
            return matchComposite.groupValues[2]
        }
        return tagName.removePrefix("v")
    }

    fun isNewerVersion(
        remoteTag: String,
        remoteAssetVersionCode: Long? = null,
        currentVersionCode: Long,
        currentVersionName: String,
    ): Boolean {
        val remoteVersionCode = extractVersionCode(remoteTag, remoteAssetVersionCode)
        if (remoteVersionCode != null) {
            return remoteVersionCode > currentVersionCode
        }
        return compareSemver(remoteTag, currentVersionName) > 0
    }

    fun compareSemver(remoteTag: String, currentVersionName: String): Int {
        val remoteParts = parseSemverParts(remoteTag)
        val currentParts = parseSemverParts(currentVersionName)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0L }
            val c = currentParts.getOrElse(i) { 0L }
            if (r != c) {
                return r.compareTo(c)
            }
        }
        return 0
    }

    private fun parseSemverParts(version: String): List<Long> {
        val match = semverPrefixRegex.find(version) ?: return emptyList()
        val digitsOnly = match.groupValues[1]
        return digitsOnly.split('.').mapNotNull { it.toLongOrNull() }
    }

    fun selectAsset(
        assets: List<GithubAsset>,
        applicationId: String,
        supportedAbis: Array<String>,
    ): GithubAsset? {
        val prefix = if (applicationId.endsWith(".dev")) "tsuzuki-dev-" else "tsuzuki-"
        val apkAssets = assets.filter { it.name.startsWith(prefix) && it.name.endsWith(".apk") }
        if (apkAssets.isEmpty()) return null

        for (abi in supportedAbis) {
            val targetName = "${prefix}${abi}.apk"
            val match = apkAssets.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
            if (match != null) return match
        }

        val universalName = "${prefix}universal.apk"
        val universalMatch = apkAssets.firstOrNull { it.name.equals(universalName, ignoreCase = true) }
        if (universalMatch != null) return universalMatch

        return apkAssets.firstOrNull()
    }

    fun evaluateInstallStatus(status: Int): InstallDecision {
        return when (status) {
            0 -> InstallDecision.Success
            -1 -> InstallDecision.PromptUser
            3 -> InstallDecision.Aborted
            else -> InstallDecision.FallbackPrompt
        }
    }

    fun findReleaseCandidate(
        release: GithubRelease,
        applicationId: String,
        supportedAbis: Array<String>,
        currentVersionCode: Long,
        currentVersionName: String,
    ): ReleaseCandidate? {
        val asset = selectAsset(release.assets, applicationId, supportedAbis) ?: return null
        val assetVc = asset.versionCode ?: asset.altVersionCode
        val tag = versionTag(release.tagName)
        val isNewer = isNewerVersion(
            remoteTag = tag,
            remoteAssetVersionCode = assetVc,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
        )
        if (!isNewer) return null
        val targetVersionCode = extractVersionCode(tag, assetVc)
            ?: (currentVersionCode + 1L)
        val targetVersionName = extractVersionName(tag)
        return ReleaseCandidate(
            versionCode = targetVersionCode,
            versionName = targetVersionName,
            downloadUrl = asset.downloadUrl,
            releaseUrl = release.htmlUrl,
            releaseNotes = release.body.orEmpty(),
            assetName = asset.name,
        )
    }
}
