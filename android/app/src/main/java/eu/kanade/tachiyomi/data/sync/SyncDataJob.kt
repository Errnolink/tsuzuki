package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import dev.errnolink.tsuzuki.sync.SyncFailurePolicy
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.core.common.util.lang.launchIO
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncDataJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = SyncNotifier(context)

    // KMK -->
    private val syncStatus: SyncStatus = Injekt.get()
    // KMK <--

    override suspend fun doWork(): Result {
        if (tags.contains(TAG_AUTO)) {
            if (!context.isOnline()) {
                return Result.retry()
            }
            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(TAG_MANUAL)) {
                return Result.retry()
            }
        }

        // KMK -->
        syncStatus.start()
        // KMK <--

        setForegroundSafely()

        var syncRan = false
        return try {
            syncLock.withLock { SyncManager(context).syncData() }
            syncRan = true
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            // KMK -->
            // The hub being unreachable is expected for automatic runs (PC off or
            // away): stay quiet. Manual runs and protocol failures still report.
            if (SyncFailurePolicy.shouldNotifySyncFailure(tags.contains(TAG_AUTO), e)) {
                notifier.showSyncError(e.message)
            }
            // KMK <--
            Result.success() // try again next time
        } finally {
            // KMK -->
            syncStatus.stop()
            if (syncRan) {
                runFollowUpIfNeeded(context)
            }
            // KMK <--
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.showSyncProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_JOB = "SyncDataJob"
        private const val TAG_AUTO = "$TAG_JOB:auto"
        const val TAG_MANUAL = "$TAG_JOB:manual"
        private const val UNIQUE_PERIODIC = "$TAG_JOB:periodic"
        private const val UNIQUE_PUSH = "$TAG_JOB:push"
        private const val PUSH_DELAY_SECONDS = 0L
        const val OPEN_DELAY_SECONDS = 5L
        private const val MIN_PERIODIC_MINUTES = 15L
        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // KMK -->
        private val pendingFollowUp = AtomicBoolean(false)
        // KMK <--
        private val syncLock = Mutex()

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_JOB)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = Injekt.get<SyncPreferences>()
            val interval = prefInterval ?: syncPreferences.syncInterval().get()
            val wm = context.workManager
            wm.cancelUniqueWork(TAG_AUTO)

            if (interval > 0) {
                val request = PeriodicWorkRequestBuilder<SyncDataJob>(
                    interval.toLong().coerceAtLeast(MIN_PERIODIC_MINUTES),
                    TimeUnit.MINUTES,
                    5,
                    TimeUnit.MINUTES,
                )
                    .setConstraints(networkConstraint)
                    .addTag(TAG_JOB)
                    .addTag(TAG_AUTO)
                    .build()

                wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
            }
        }

        fun startNow(context: Context, manual: Boolean = false, delaySeconds: Long = if (manual) 0 else PUSH_DELAY_SECONDS) {
            val wm = context.workManager
            if (wm.isRunning(TAG_JOB)) {
                // KMK -->
                pendingFollowUp.set(true)
                // KMK <--
                return
            }
            val request = OneTimeWorkRequestBuilder<SyncDataJob>()
                .addTag(TAG_JOB)
                .addTag(if (manual) TAG_MANUAL else TAG_AUTO)
                .apply { if (!manual) setConstraints(networkConstraint) }
                .apply {
                    if (delaySeconds > 0) {
                        setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    } else {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            wm.enqueueUniqueWork(if (manual) TAG_MANUAL else UNIQUE_PUSH, ExistingWorkPolicy.KEEP, request)
        }

        // KMK -->
        private fun runFollowUpIfNeeded(context: Context) {
            if (pendingFollowUp.compareAndSet(true, false)) {
                startNow(context)
            }
        }
        // KMK <--

        fun stop(context: Context) {
            val wm = context.workManager
            wm.cancelAllWorkByTag(TAG_JOB)
            val syncPreferences = Injekt.get<SyncPreferences>()
            val syncEnabled = syncPreferences.isSyncEnabled()
            val syncStatus: SyncStatus = Injekt.get()
            tachiyomi.core.common.util.lang.launchIO {
                syncStatus.stop()
                if (syncEnabled) {
                    setupTask(context)
                }
            }
        }
    }
}
