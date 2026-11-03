package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import dev.errnolink.tsuzuki.sync.SyncFailurePolicy
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.data.sync.chapterDeltaCapEnabled
import eu.kanade.tachiyomi.data.sync.shouldReprobeCapabilities
import eu.kanade.tachiyomi.data.sync.decodePushedManga
import eu.kanade.tachiyomi.data.sync.selectSyncDelta
import eu.kanade.tachiyomi.data.sync.selectSyncSections
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.system.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    private val protoBuf: ProtoBuf = Injekt.get(),
    private val probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
) : SyncService(context, json, syncPreferences) {

    // KMK -->
    companion object {
        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        // KMK -->
        internal const val PROBE_TIMEOUT_MS = 5_000L
        internal val FULL_SYNC_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
        // KMK <--
    }
    // KMK <--

    private class SyncYomiException(message: String?) : Exception(message)

    @Serializable
    private data class SyncEvent(
        val event: SyncEventStatus,
        @SerialName("device_id")
        val deviceId: String? = null,
        @SerialName("device_name")
        val deviceName: String? = null,
        val message: String? = null,
    )

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    @Serializable
    private data class Capabilities(
        val version: Int = 0,
        val merge: String? = null,
        val snapshot: String? = null,
        // KMK -->
        val caps: List<String> = emptyList(),
        // KMK <--
    )

    // KMK -->
    @Serializable
    private data class HubVersion(val seq: Long = -1)

    /**
     * R2 cheap poll: the hub's store seq, or null when the hub predates the version
     * endpoint (or the probe fails) so the caller falls back to a full sync.
     *
     * Strictly bounded to [probeTimeoutMs]: a hub that accepts the connection but
     * never answers must not hold the whole sync pipeline hostage — on timeout this
     * returns null and the caller proceeds with a normal merge.
     */
    suspend fun probeHubVersion(): Long? {
        val version = withTimeoutOrNull(probeTimeoutMs) {
            try {
                val request = GET(
                    url = "${clientHost()}/api/sync/v2/version",
                    headers = Headers.Builder().add("X-API-Token", syncPreferences.clientAPIKey().get()).build(),
                )
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) return@withTimeoutOrNull null
                    json.decodeFromString(HubVersion.serializer(), response.body.string()).seq.takeIf { it >= 0 }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "Hub version probe unavailable: ${e.message}" }
                null
            }
        }
        if (version == null) {
            logcat(LogPriority.DEBUG) {
                "Hub version probe unavailable or timed out after $probeTimeoutMs ms; proceeding with a normal merge"
            }
        }
        return version
    }
    // KMK <--

    override suspend fun doSync(syncData: SyncData): SyncResult {
        reportSyncEvent(SyncEventStatus.SYNC_STARTED)

        try {
            if (syncPreferences.prepareSyncIdentity() != syncData.identity) {
                throw SyncYomiException("Hub pairing changed during sync")
            }
            requireV2Capabilities()

            val backup = requireNotNull(syncData.backup) { "Local backup snapshot is missing" }
            val cursor = syncPreferences.syncCursor().get()
            val fullUpload = syncData.fullUpload
            val deletedCategories = syncPreferences.categoryState.deleted()

            val payload: Backup
            val sectionDigests: Set<String>
            if (fullUpload) {
                payload = backup
                sectionDigests = selectSyncSections(backup, emptySet(), full = true).digests
            } else {
                val delta = selectSyncDelta(backup, syncPreferences.lastPushedAt().get(), decodePushedManga(syncPreferences.pushedManga().get()))
                val sections = selectSyncSections(delta, syncPreferences.sectionDigests().get(), full = false)
                payload = sections.backup
                sectionDigests = sections.digests
            }

            val host = clientHost()
            // KMK -->
            val chapterDelta = chapterDeltaCapEnabled(syncPreferences.syncCaps().get())
            // KMK <--
            val headers = Headers.Builder()
                .add("X-API-Token", syncPreferences.clientAPIKey().get())
                .add("X-Device-ID", syncData.deviceId)
                .add("X-Device-Name", android.os.Build.MODEL)
                .add("X-Sync-Cursor", cursor.toString())
                .add("X-Sync-Full", fullUpload.toString())
                .apply {
                    if (deletedCategories.isNotEmpty()) {
                        add("X-Sync-Deleted-Categories", deletedCategories.joinToString(","))
                    }
                    // KMK -->
                    if (chapterDelta) {
                        add("X-Sync-Caps", "chapter-delta")
                    }
                    // KMK <--
                }
                .build()

            val request = POST(
                url = "$host/api/sync/v2/merge",
                headers = headers,
                body = BackupRequestBody(payload, protoBuf),
            )

            var responseBackup: Backup? = null
            var responseCursor: Long? = null
            var changed = false
            var fullRequested = false

            var attempt = 0
            while (true) {
                attempt++
                val responseResult = try {
                    client.newCall(request).await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (attempt < 2 && (e is java.io.IOException || e is java.net.SocketTimeoutException)) {
                        logcat(LogPriority.WARN, e) { "Sync merge transient network error, retrying" }
                        kotlinx.coroutines.delay(1500L + kotlin.random.Random.nextLong(1500))
                        continue
                    }
                    throw e
                }

                val shouldRetry = responseResult.use { response ->
                    if (response.code == HttpStatus.SC_UNAUTHORIZED) {
                        throw SyncYomiException("SyncYomi authentication failed (401): check the API key")
                    }
                    if (response.code in 500..599 && attempt < 2) {
                        logcat(LogPriority.WARN) { "Sync merge server error (${response.code}), retrying" }
                        return@use true
                    }
                    if (!response.isSuccessful) {
                        val responseBody = response.body.string()
                        notifier.showSyncError("Failed to sync with SyncYomi: $responseBody")
                        logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
                        throw SyncYomiException("SyncYomi v2 merge failed (${response.code})")
                    }

                    responseCursor = response.headers["X-Sync-Cursor"]?.toLongOrNull()
                        ?: throw SyncYomiException("Missing X-Sync-Cursor in merge response")
                    changed = response.headers["X-Sync-Changed"]?.equals("true", ignoreCase = true) == true
                    fullRequested = response.headers["X-Sync-Full-Requested"]?.equals("true", ignoreCase = true) == true

                    val bytes = response.body.byteStream().use { it.readBytes() }
                    responseBackup = if (bytes.isEmpty()) {
                        Backup()
                    } else {
                        try {
                            protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
                        } catch (e: SerializationException) {
                            logcat(LogPriority.ERROR, throwable = e) { "Bad content responded from server" }
                            throw SyncYomiException("SyncYomi returned an undecodable backup")
                        }
                    }
                    false
                }

                if (shouldRetry) {
                    kotlinx.coroutines.delay(1500L + kotlin.random.Random.nextLong(1500))
                    continue
                }
                break
            }

            val endCursor = responseCursor ?: 0L
            if (shouldReprobeCapabilities(
                    lastProbedAt = syncPreferences.capabilitiesProbedAt().get(),
                    now = System.currentTimeMillis(),
                    lastCursor = cursor,
                    responseCursor = endCursor,
                )
            ) {
                try {
                    requireV2Capabilities(force = true)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Post-merge capability re-probe failed" }
                }
            }

            logcat(LogPriority.DEBUG) {
                "SyncYomi v2 merge completed: cursor=$responseCursor changed=$changed fullRequested=$fullRequested"
            }

            return SyncResult(
                backup = responseBackup ?: Backup(),
                changed = changed,
                // KMK -->
                chapterDelta = chapterDelta,
                // KMK <--
                acknowledgement = SyncAcknowledgement(
                    cursor = responseCursor ?: throw SyncYomiException("Missing cursor in merge response"),
                    fullRequested = fullRequested,
                    fullUpload = fullUpload,
                    deletedCategories = deletedCategories,
                    snapshotStartedAt = syncData.snapshotStartedAt,
                    sectionDigests = sectionDigests,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            // KMK -->
            // Transport failures reaching the hub stay quiet here: the worker decides
            // by run origin (manual runs notify, automatic ones do not). Protocol
            // errors still surface immediately.
            if (!SyncFailurePolicy.isHubUnreachable(e)) {
                notifier.showSyncError(e.message)
            }
            // KMK <--
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, e.message)
            throw e
        }
    }

    suspend fun reportSyncSuccess() = reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)

    suspend fun reportSyncFailed(message: String? = null) = reportSyncEvent(SyncEventStatus.SYNC_FAILED, message)

    private fun clientHost(): String {
        return syncPreferences.clientHost().get().trim().trimEnd('/')
    }

    private suspend fun requireV2Capabilities(force: Boolean = false) {
        // KMK -->
        val now = System.currentTimeMillis()
        val lastProbed = syncPreferences.capabilitiesProbedAt().get()
        val hasCached = syncPreferences.capabilities().get() == 2 && syncPreferences.syncCaps().get().isNotEmpty()
        if (!force && hasCached && !shouldReprobeCapabilities(lastProbed, now)) return
        // KMK <--

        val request = GET(
            url = "${clientHost()}/api/sync/v2/capabilities",
            headers = Headers.Builder().add("X-API-Token", syncPreferences.clientAPIKey().get()).build(),
        )

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw SyncYomiException("SyncYomi v2 capabilities probe failed (HTTP ${response.code})")
            }
            val capabilities = json.decodeFromString(Capabilities.serializer(), response.body.string())
            if (capabilities.version < 2) {
                throw SyncYomiException("SyncYomi server speaks protocol v${capabilities.version}, v2 required")
            }
            syncPreferences.capabilities().set(2)
            // KMK -->
            syncPreferences.syncCaps().set(capabilities.caps.joinToString(","))
            syncPreferences.capabilitiesProbedAt().set(System.currentTimeMillis())
            // KMK <--
        }
    }

    private suspend fun reportSyncEvent(event: SyncEventStatus, message: String? = null) {
        withContext(NonCancellable) {
            try {
                val headers = Headers.Builder()
                    .add("X-API-Token", syncPreferences.clientAPIKey().get())
                    .add("X-Device-ID", syncPreferences.uniqueDeviceID())
                    .build()

                val bodyObj = SyncEvent(
                    event = event,
                    deviceId = syncPreferences.uniqueDeviceID(),
                    deviceName = android.os.Build.MODEL,
                    message = message,
                )

                val jsonBody = json.encodeToString(SyncEvent.serializer(), bodyObj)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = POST(
                    url = "${clientHost()}/api/sync/event",
                    headers = headers,
                    body = requestBody,
                )

                client.newCall(request).await().close()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to report sync event: ${e.message}" }
            }
        }
    }
}
