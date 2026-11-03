package dev.errnolink.tsuzuki.update

import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AppUpdateChecker(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
    private val preferences: UpdatePreferences = Injekt.get<UpdatePreferences>(),
) {

    suspend fun checkForUpdate(
        isUserPrompt: Boolean = false,
        applicationId: String = BuildConfig.APPLICATION_ID,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): CheckResult = withContext(Dispatchers.IO) {
        try {
            val headers = Headers.Builder()
                .set("User-Agent", USER_AGENT)
                .build()
            val request = GET(LATEST_RELEASE_URL, headers)
            val response = client.newCall(request).awaitSuccess()
            val release = response.body.string().let { UpdateLogic.parseReleases(it) }
                .let(UpdateLogic::latestAndroidRelease)

            preferences.lastAppCheck().set(System.currentTimeMillis())
            if (release == null) return@withContext CheckResult.NoNewUpdate

            val candidate = UpdateLogic.findReleaseCandidate(
                release = release,
                applicationId = applicationId,
                supportedAbis = supportedAbis,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )

            if (candidate != null) {
                CheckResult.NewUpdate(candidate)
            } else {
                CheckResult.NoNewUpdate
            }
        } catch (e: Exception) {
            CheckResult.Error(e)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Errnolink/tsuzuki/releases?per_page=30"
        const val USER_AGENT = "Tsuzuki-Android"
    }
}
