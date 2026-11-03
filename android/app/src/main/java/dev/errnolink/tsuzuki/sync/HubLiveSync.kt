package dev.errnolink.tsuzuki.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HubLiveSync(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val random: Random = Random.Default,
) : DefaultLifecycleObserver {

    private var loop: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        loop?.cancel()
        loop = owner.lifecycleScope.launch(Dispatchers.IO) { watch() }
    }

    override fun onStop(owner: LifecycleOwner) {
        loop?.cancel()
        loop = null
    }

    private suspend fun watch() {
        var failures = 0
        var capsChecked = false
        while (currentCoroutineContext().isActive) {
            if (!capsChecked && syncPreferences.syncService().get() == SyncManager.SyncService.SYNCYOMI.value) {
                capsChecked = try {
                    refreshCaps()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            if (!liveSyncAvailable()) {
                delay(IDLE_RECHECK_MS)
                continue
            }
            val cursor = syncPreferences.syncCursor().get()
            val seq = try {
                waitForHubChange(cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                delay(retryDelayMs(failures, random))
                continue
            }
            if (seq == null || seq <= cursor) {
                failures = 0
                continue
            }
            try {
                SyncDataJob.startNow(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                delay(retryDelayMs(failures, random))
                continue
            }
            val advanced = withTimeoutOrNull(CATCH_UP_TIMEOUT_MS) {
                syncPreferences.syncCursor().changes().first { it >= seq }
            } != null
            if (advanced) {
                failures = 0
            } else {
                failures++
                delay(retryDelayMs(failures, random))
            }
        }
    }

    private fun liveSyncAvailable(): Boolean =
        syncPreferences.syncService().get() == SyncManager.SyncService.SYNCYOMI.value &&
            syncPreferences.syncCursor().get() > 0 &&
            syncPreferences.syncCaps().get().split(',').any { it.trim() == CAP_VERSION_WAIT }

    private suspend fun refreshCaps() {
        val host = syncPreferences.clientHost().get().trim().trimEnd('/')
        val request = GET(
            url = "$host/api/sync/v2/capabilities",
            headers = Headers.Builder().add("X-API-Token", syncPreferences.clientAPIKey().get()).build(),
        )
        val caps = client.newCall(request).await().use { response ->
            check(response.isSuccessful) { "Hub capabilities failed (HTTP ${response.code})" }
            Json.parseToJsonElement(response.body.string()).jsonObject["caps"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: return
        }
        syncPreferences.syncCaps().set(caps.joinToString(","))
    }

    private suspend fun waitForHubChange(cursor: Long): Long? {
        val host = syncPreferences.clientHost().get().trim().trimEnd('/')
        val request = GET(
            url = "$host/api/sync/v2/version?since=$cursor&wait=$WAIT_SECONDS",
            headers = Headers.Builder().add("X-API-Token", syncPreferences.clientAPIKey().get()).build(),
        )
        return client.newCall(request).await().use { response ->
            check(response.isSuccessful) { "Hub version wait failed (HTTP ${response.code})" }
            Json.parseToJsonElement(response.body.string()).jsonObject["seq"]?.jsonPrimitive?.longOrNull
        }
    }

    companion object {
        private const val CAP_VERSION_WAIT = "version-wait"
        private const val WAIT_SECONDS = 25
        private const val IDLE_RECHECK_MS = 30_000L
        private const val CATCH_UP_TIMEOUT_MS = 60_000L
        private const val MAX_RETRY_MS = 60_000L

        internal fun retryDelayMs(failures: Int, random: Random): Long {
            val cap = (2_500L shl failures.coerceIn(1, 5)).coerceAtMost(MAX_RETRY_MS)
            return random.nextLong(2_500L, cap + 1)
        }

        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(WAIT_SECONDS + 15L, TimeUnit.SECONDS)
                .build()
        }
    }
}
