package dev.errnolink.tsuzuki.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateLogicTest {

    private val sampleReleaseJson = """
    {
      "tag_name": "v105-1.0.0",
      "name": "Tsuzuki v1.0.0",
      "body": "Changelog:\n- Initial release\n- Bug fixes",
      "html_url": "https://github.com/Errnolink/tsuzuki-android/releases/tag/v105-1.0.0",
      "assets": [
        {
          "name": "tsuzuki-arm64-v8a.apk",
          "browser_download_url": "https://github.com/Errnolink/tsuzuki-android/releases/download/v105-1.0.0/tsuzuki-arm64-v8a.apk",
          "size": 15000000,
          "version_code": 105
        },
        {
          "name": "tsuzuki-x86_64.apk",
          "browser_download_url": "https://github.com/Errnolink/tsuzuki-android/releases/download/v105-1.0.0/tsuzuki-x86_64.apk",
          "size": 16000000,
          "version_code": 105
        },
        {
          "name": "tsuzuki-universal.apk",
          "browser_download_url": "https://github.com/Errnolink/tsuzuki-android/releases/download/v105-1.0.0/tsuzuki-universal.apk",
          "size": 30000000,
          "version_code": 105
        },
        {
          "name": "tsuzuki-dev-arm64-v8a.apk",
          "browser_download_url": "https://github.com/Errnolink/tsuzuki-android/releases/download/v105-1.0.0/tsuzuki-dev-arm64-v8a.apk",
          "size": 15500000,
          "version_code": 105
        },
        {
          "name": "tsuzuki-dev-universal.apk",
          "browser_download_url": "https://github.com/Errnolink/tsuzuki-android/releases/download/v105-1.0.0/tsuzuki-dev-universal.apk",
          "size": 31000000,
          "version_code": 105
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `parses release json correctly`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        assertEquals("v105-1.0.0", release.tagName)
        assertEquals("Tsuzuki v1.0.0", release.name)
        assertEquals(5, release.assets.size)
        assertEquals("tsuzuki-arm64-v8a.apk", release.assets[0].name)
        assertEquals(105L, release.assets[0].versionCode)
    }

    @Test
    fun `extracts version code and version name from composite tag`() {
        assertEquals(105L, UpdateLogic.extractVersionCode("v105-1.0.0"))
        assertEquals(105L, UpdateLogic.extractVersionCode("105-1.0.0"))
        assertEquals("1.0.0", UpdateLogic.extractVersionName("v105-1.0.0"))
        assertEquals("1.0.0", UpdateLogic.extractVersionName("105-1.0.0"))
    }

    @Test
    fun `extracts version code from numeric tag`() {
        assertEquals(42L, UpdateLogic.extractVersionCode("v42"))
        assertEquals(42L, UpdateLogic.extractVersionCode("42"))
        assertEquals("42", UpdateLogic.extractVersionName("v42"))
    }

    @Test
    fun `handles plain semver tag extraction`() {
        assertNull(UpdateLogic.extractVersionCode("v1.2.3"))
        assertEquals("1.2.3", UpdateLogic.extractVersionName("v1.2.3"))
        assertNull(UpdateLogic.extractVersionCode("0.1.0"))
        assertEquals("0.1.0", UpdateLogic.extractVersionName("0.1.0"))
    }

    @Test
    fun `asset version code overrides tag parsing`() {
        assertEquals(200L, UpdateLogic.extractVersionCode("v100-1.0.0", 200L))
        assertEquals(200L, UpdateLogic.extractVersionCode("v1.0.0", 200L))
    }

    @Test
    fun `version comparison with composite tag`() {
        assertTrue(UpdateLogic.isNewerVersion("v105-1.0.0", null, 1L, "0.1.0"))
        assertFalse(UpdateLogic.isNewerVersion("v105-1.0.0", null, 105L, "1.0.0"))
        assertFalse(UpdateLogic.isNewerVersion("v105-1.0.0", null, 200L, "2.0.0"))
    }

    @Test
    fun `version comparison with semver tag`() {
        assertTrue(UpdateLogic.isNewerVersion("v0.2.0", null, 1L, "0.1.0"))
        assertTrue(UpdateLogic.isNewerVersion("v1.0.0", null, 1L, "0.9.9"))
        assertTrue(UpdateLogic.isNewerVersion("v1.10.0", null, 1L, "1.9.0"))
        assertFalse(UpdateLogic.isNewerVersion("v0.1.0", null, 1L, "0.1.0"))
        assertFalse(UpdateLogic.isNewerVersion("v0.0.9", null, 1L, "0.1.0"))
        assertFalse(UpdateLogic.isNewerVersion("v1.0.0", null, 1L, "1.0.1"))
    }

    @Test
    fun `version comparison with asset version code`() {
        assertTrue(UpdateLogic.isNewerVersion("v1.0.0", 150L, 100L, "1.0.0"))
        assertFalse(UpdateLogic.isNewerVersion("v1.0.0", 100L, 100L, "1.0.0"))
        assertFalse(UpdateLogic.isNewerVersion("v1.0.0", 50L, 100L, "1.0.0"))
    }

    @Test
    fun `asset selection selects matching abi for release package`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        val selectedArm64 = UpdateLogic.selectAsset(
            release.assets,
            "dev.errnolink.tsuzuki",
            arrayOf("arm64-v8a", "armeabi-v7a"),
        )
        assertNotNull(selectedArm64)
        assertEquals("tsuzuki-arm64-v8a.apk", selectedArm64?.name)

        val selectedX86 = UpdateLogic.selectAsset(
            release.assets,
            "dev.errnolink.tsuzuki",
            arrayOf("x86_64"),
        )
        assertNotNull(selectedX86)
        assertEquals("tsuzuki-x86_64.apk", selectedX86?.name)
    }

    @Test
    fun `asset selection falls back to universal when abi not found`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        val selectedFallback = UpdateLogic.selectAsset(
            release.assets,
            "dev.errnolink.tsuzuki",
            arrayOf("armeabi-v7a"),
        )
        assertNotNull(selectedFallback)
        assertEquals("tsuzuki-universal.apk", selectedFallback?.name)
    }

    @Test
    fun `asset selection selects dev variant for dev package`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        val selectedDev = UpdateLogic.selectAsset(
            release.assets,
            "dev.errnolink.tsuzuki.dev",
            arrayOf("arm64-v8a"),
        )
        assertNotNull(selectedDev)
        assertEquals("tsuzuki-dev-arm64-v8a.apk", selectedDev?.name)

        val selectedDevUniversal = UpdateLogic.selectAsset(
            release.assets,
            "dev.errnolink.tsuzuki.dev",
            arrayOf("mips"),
        )
        assertNotNull(selectedDevUniversal)
        assertEquals("tsuzuki-dev-universal.apk", selectedDevUniversal?.name)
    }

    @Test
    fun `asset selection returns null when no matching package assets exist`() {
        val assets = listOf(
            GithubAsset("other-app-arm64.apk", "https://url"),
        )
        val selected = UpdateLogic.selectAsset(assets, "dev.errnolink.tsuzuki", arrayOf("arm64-v8a"))
        assertNull(selected)
    }

    @Test
    fun `findReleaseCandidate produces valid candidate when newer`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        val candidate = UpdateLogic.findReleaseCandidate(
            release = release,
            applicationId = "dev.errnolink.tsuzuki",
            supportedAbis = arrayOf("arm64-v8a"),
            currentVersionCode = 1L,
            currentVersionName = "0.1.0",
        )
        assertNotNull(candidate)
        assertEquals(105L, candidate?.versionCode)
        assertEquals("1.0.0", candidate?.versionName)
        assertEquals("tsuzuki-arm64-v8a.apk", candidate?.assetName)
    }

    @Test
    fun `findReleaseCandidate returns null when current version is equal or newer`() {
        val release = UpdateLogic.parseRelease(sampleReleaseJson)
        val candidate = UpdateLogic.findReleaseCandidate(
            release = release,
            applicationId = "dev.errnolink.tsuzuki",
            supportedAbis = arrayOf("arm64-v8a"),
            currentVersionCode = 105L,
            currentVersionName = "1.0.0",
        )
        assertNull(candidate)
    }

    @Test
    fun `monorepo picks the newest published android release and ignores windows, drafts and prereleases`() {
        fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false) = GithubRelease(
            tagName = tag,
            htmlUrl = "https://github.com/Errnolink/tsuzuki/releases/tag/$tag",
            draft = draft,
            prerelease = prerelease,
            assets = listOf(GithubAsset("tsuzuki-arm64-v8a.apk", "https://example.invalid/$tag.apk")),
        )
        val releases = listOf(
            release("windows-v0.3.0"),
            release("android-v0.3.0", draft = true),
            release("android-v0.3.0-rc1", prerelease = true),
            release("android-v0.2.0"),
            release("android-v0.2.1"),
        )

        val picked = UpdateLogic.latestAndroidRelease(releases)
        assertEquals("android-v0.2.1", picked?.tagName)

        val fromBeta = UpdateLogic.findReleaseCandidate(picked!!, "dev.errnolink.tsuzuki", arrayOf("arm64-v8a"), 32L, "0.1-beta.30")
        assertEquals("0.2.1", fromBeta?.versionName)
        assertEquals("tsuzuki-arm64-v8a.apk", fromBeta?.assetName)
        assertNull(UpdateLogic.findReleaseCandidate(picked, "dev.errnolink.tsuzuki", arrayOf("arm64-v8a"), 35L, "0.2.1"))
        assertNull(UpdateLogic.latestAndroidRelease(listOf(release("windows-v0.3.0"))))
    }

    @Test
    fun `decision function handles install status codes`() {
        assertEquals(InstallDecision.Success, UpdateLogic.evaluateInstallStatus(0))
        assertEquals(InstallDecision.PromptUser, UpdateLogic.evaluateInstallStatus(-1))
        assertEquals(InstallDecision.Aborted, UpdateLogic.evaluateInstallStatus(3))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(1))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(2))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(4))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(5))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(6))
        assertEquals(InstallDecision.FallbackPrompt, UpdateLogic.evaluateInstallStatus(7))
    }
}
