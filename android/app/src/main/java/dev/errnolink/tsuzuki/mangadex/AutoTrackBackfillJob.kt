package dev.errnolink.tsuzuki.mangadex

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import exh.md.MangaDexPreferences
import exh.md.handlers.AutoAddTrackers
import exh.md.handlers.AutoTrackBinder
import exh.md.handlers.AutoTrackResult
import exh.source.MANGADEX_IDS
import exh.source.MERGED_SOURCE_ID
import exh.source.mangaDexSourceIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.repository.TrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AutoTrackBackfillJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var done = 0
    private var total = 0

    private val pacer = AutoTrackPacer(FETCH_INTERVAL_MS)

    override suspend fun doWork(): Result = withIOContext {
        val state = AutoTrackState()
        val preferences = MangaDexPreferences()
        val mangaRepository: MangaRepository = Injekt.get()
        val merges: MangaMergeRepository = Injekt.get()
        val trackRepository: TrackRepository = Injekt.get()
        val trackerIdsByService = AutoAddTrackers.serviceTrackerIds()
        val signedIn = AutoAddTrackers.signedInServiceCodes()
        val effective = MangaDexPreferences.effectiveServices(
            signedIn,
            preferences.autoTrackDisabled().get(),
        )
        val candidates = collectCandidates(
            mangaRepository,
            merges,
            trackRepository,
            trackerIdsByService,
            signedIn,
            preferences.autoTrackDisabled().get(),
        )
        logcat(LogPriority.INFO) {
            "Auto-track backfill: ${candidates.size} candidates, services ${effective.sorted()}"
        }
        if (effective.isEmpty()) return@withIOContext Result.success()
        setForegroundSafely()
        val todo = remainingAfter(candidates, state.cursor())
        total = candidates.size
        done = candidates.size - todo.size
        val linkedByService = mutableMapOf<String, Int>()
        var alreadyLinked = 0
        var noLinkTitles = 0
        var skippedByRating = 0
        val binder: AutoTrackBinder = AutoAddTrackers.newBinder { work ->
            pacer.gate()
            work()
        }
        for (batch in todo.chunked(BATCH_SIZE)) {
            for (candidate in batch) {
                val result = try {
                    binder.bind(candidate.mangaId, candidate.services)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Auto-track backfill failed for ${candidate.mangaId}" }
                    AutoTrackResult.Failed(retryable = true)
                }
                when (result) {
                    is AutoTrackResult.Linked -> {
                        if (result.services.isEmpty() && result.noLink.isEmpty()) {
                            alreadyLinked++
                        } else {
                            result.services.forEach { service -> linkedByService.merge(service, 1, Int::plus) }
                            state.cacheNoLink(candidate.mangaId, result.noLink, System.currentTimeMillis())
                        }
                    }
                    is AutoTrackResult.NoLink -> {
                        noLinkTitles++
                        state.cacheNoLink(candidate.mangaId, result.services, System.currentTimeMillis())
                    }
                    AutoTrackResult.SkippedByRating -> skippedByRating++
                    AutoTrackResult.NotMangaDex -> Unit
                    is AutoTrackResult.Failed -> {
                        if (!result.retryable) {
                            noLinkTitles++
                            state.cacheNoLink(candidate.mangaId, candidate.services, System.currentTimeMillis())
                        }
                    }
                }
            }
            state.setCursor(batch.last().mangaId)
            done += batch.size
            setProgress(progressData())
            runCatching { setForeground(getForegroundInfo()) }
            SyncDataJob.startNow(applicationContext)
        }
        state.setSummary(buildSummary(linkedByService, alreadyLinked, noLinkTitles, skippedByRating))
        state.setCursor(0)
        Result.success(progressData())
    }

    private suspend fun collectCandidates(
        mangaRepository: MangaRepository,
        merges: MangaMergeRepository,
        trackRepository: TrackRepository,
        trackerIdsByService: Map<String, Long>,
        signedIn: Set<String>,
        disabled: Set<String>,
    ): List<AutoTrackCandidate> {
        val favorites = mangaRepository.getFavorites()
        val mdSourceIds = mangaDexSourceIds.toSet() + MANGADEX_IDS
        val mergedMdChildIds = favorites
            .filter { it.source == MERGED_SOURCE_ID }
            .filter { merged -> merges.getMergedMangaById(merged.id).any { it.source in mdSourceIds } }
            .map { it.id }
            .toSet()
        val tracksByMangaId = trackRepository.getTracks()
            .groupBy({ it.mangaId }, { it.trackerId })
            .mapValues { it.value.toSet() }
        val state = AutoTrackState()
        return buildCandidates(
            favorites = favorites.map { it.id to it.source },
            mergedMdChildIds = mergedMdChildIds,
            mdSourceIds = mdSourceIds,
            mergedSourceId = MERGED_SOURCE_ID,
            tracksByMangaId = tracksByMangaId,
            trackerIdsByService = trackerIdsByService,
            signedIn = signedIn,
            disabled = disabled,
            activeNoLink = state.activeNoLink(System.currentTimeMillis()),
        )
    }

    private fun progressData() = workDataOf(COMPLETED to done, TOTAL to total)

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID,
        NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_LIBRARY_PROGRESS)
            .setSmallIcon(R.drawable.ic_tsuzuki)
            .setContentTitle("Linking trackers")
            .setContentText("${groupedCount(done)} / ${groupedCount(total)}")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", applicationContext.workManager.createCancelPendingIntent(id))
            .build(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )

    companion object {
        private const val NAME = "tsuzuki_auto_track_backfill"
        private const val NOTIFICATION_ID = -1804
        private const val BATCH_SIZE = 50
        private const val FETCH_INTERVAL_MS = 1000L
        const val COMPLETED = "completed"
        const val TOTAL = "total"

        fun observe(context: Context) = context.workManager.getWorkInfosForUniqueWorkFlow(NAME)
            .map { work -> work.firstOrNull { !it.state.isFinished } ?: work.maxByOrNull { it.generation } }

        fun tryStart(context: Context): Boolean {
            if (AutoAddTrackers.effectiveServicesNow().isEmpty()) return false
            context.workManager.enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AutoTrackBackfillJob>()
                    .setConstraints(
                        Constraints(
                            requiredNetworkType = NetworkType.CONNECTED,
                            requiresBatteryNotLow = true,
                        ),
                    )
                    .build(),
            )
            return true
        }

        fun cancel(context: Context) = context.workManager.cancelUniqueWork(NAME)

        fun progressText(work: WorkInfo?): String? {
            if (work == null) return null
            val data = if (work.state.isFinished) work.outputData else work.progress
            val done = data.getInt(COMPLETED, 0)
            val total = data.getInt(TOTAL, 0)
            return when (work.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting for a network connection"
                WorkInfo.State.RUNNING -> "Linking trackers ${groupedCount(done)} / ${groupedCount(total)}"
                else -> null
            }
        }
    }
}

object AutoTrackStartup {
    fun observeTrackerSignIns(app: Context, scope: CoroutineScope) {
        scope.launchIO {
            MangaDexPreferences().migrateAutoTrackSelection()
            val serviceFlows = AutoAddTrackers.serviceTrackers().map { (code, tracker) ->
                tracker.isLoggedInFlow.map { signedIn -> code to signedIn }
            }
            combine(serviceFlows) { states -> states.filter { it.second }.map { it.first }.toSet() }
                .drop(1)
                .distinctUntilChanged()
                .collect { AutoTrackBackfillJob.tryStart(app) }
        }
    }
}
