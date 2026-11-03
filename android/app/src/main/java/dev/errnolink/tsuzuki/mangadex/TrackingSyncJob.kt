// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.repository.TrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class TrackingSyncJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var completed = 0
    private var total = 0
    private var failed = 0

    override suspend fun doWork(): Result {
        if (tags.contains(PERIODIC_NAME) && !Injekt.get<TrackPreferences>().autoRefreshTrackingMetadata().get()) {
            applicationContext.workManager.cancelUniqueWork(PERIODIC_NAME)
            return Result.success()
        }
        return doRefresh()
    }

    private suspend fun doRefresh(): Result = withIOContext {
        setForegroundSafely()
        val favoriteIds = Injekt.get<MangaRepository>().getFavorites().mapTo(HashSet()) { it.id }
        val trackers = Injekt.get<TrackerManager>()
        val mangaIds = Injekt.get<TrackRepository>().getTracks().asSequence()
            .filter { it.mangaId in favoriteIds && trackers.get(it.trackerId)?.isLoggedIn == true }
            .map { it.mangaId }
            .distinct()
            .toList()
        total = mangaIds.size
        val refresh = Injekt.get<RefreshTracks>()
        val enhancedOnly = !Injekt.get<TrackPreferences>().autoSyncProgressFromTrackers().get()
        val summary = refreshTrackingMetadata(
            mangaIds,
            refresh = { refresh.await(it, enhancedTrackersOnly = enhancedOnly, notifyProgress = false).isEmpty() },
            progress = { done, failures ->
                completed = done
                failed = failures
                setProgress(progressData())
                setForeground(getForegroundInfo())
            },
        )
        completed = summary.completed
        failed = summary.failed
        if (failed == 0) Result.success(progressData()) else Result.failure(progressData())
    }

    private fun progressData() = workDataOf(COMPLETED to completed, TOTAL to total, FAILED to failed)

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID,
        NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_LIBRARY_PROGRESS)
            .setSmallIcon(R.drawable.ic_tsuzuki)
            .setContentTitle("Refresh tracking metadata")
            .setContentText("$completed of $total titles · $failed failed")
            .setProgress(total, completed, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", applicationContext.workManager.createCancelPendingIntent(id))
            .build(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )

    companion object {
        private const val NAME = "tsuzuki_tracking_sync"
        private const val PERIODIC_NAME = "tsuzuki_tracking_sync_periodic"
        private const val NOTIFICATION_ID = -1802
        const val COMPLETED = "completed"
        const val TOTAL = "total"
        const val FAILED = "failed"

        fun observe(context: Context) = context.workManager.getWorkInfosForUniqueWorkFlow(NAME)
            .map { work -> work.firstOrNull { !it.state.isFinished } ?: work.maxByOrNull { it.generation } }

        fun start(context: Context) {
            context.workManager.enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TrackingSyncJob>()
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .build(),
            )
        }

        fun ensureScheduled(context: Context) {
            schedulePeriodic(context, Injekt.get<TrackPreferences>().autoRefreshTrackingMetadata().get())
        }

        fun schedulePeriodic(context: Context, enabled: Boolean) {
            if (!enabled) {
                context.workManager.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            context.workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrackingSyncJob>(24, TimeUnit.HOURS)
                    .addTag(PERIODIC_NAME)
                    .setConstraints(
                        Constraints(
                            requiredNetworkType = NetworkType.CONNECTED,
                            requiresBatteryNotLow = true,
                        ),
                    )
                    .build(),
            )
        }

        fun cancel(context: Context) = context.workManager.cancelUniqueWork(NAME)

        fun status(work: WorkInfo?): String {
            if (work == null) return "Refresh status, score and progress for tracked library titles"
            val data = if (work.state.isFinished) work.outputData else work.progress
            val done = data.getInt(COMPLETED, 0)
            val total = data.getInt(TOTAL, 0)
            val failed = data.getInt(FAILED, 0)
            return when (work.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting for a network connection · Tap to cancel"
                WorkInfo.State.RUNNING -> "$done of $total titles · $failed failed · Tap to cancel"
                WorkInfo.State.CANCELLED -> "Cancelled · Tap to refresh again"
                WorkInfo.State.FAILED -> "Refreshed $done of $total titles · $failed failed · Tap to retry"
                WorkInfo.State.SUCCEEDED -> "Refreshed $done titles · Tap to refresh again"
            }
        }
    }
}

internal data class TrackingRefreshSummary(val completed: Int, val failed: Int)

internal suspend fun refreshTrackingMetadata(
    mangaIds: List<Long>,
    refresh: suspend (Long) -> Boolean,
    progress: suspend (Int, Int) -> Unit,
): TrackingRefreshSummary {
    var completed = 0
    var failed = 0
    progress(completed, failed)
    for (mangaId in mangaIds) {
        currentCoroutineContext().ensureActive()
        val succeeded = try {
            refresh(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!succeeded) failed++
        completed++
        progress(completed, failed)
    }
    return TrackingRefreshSummary(completed, failed)
}
