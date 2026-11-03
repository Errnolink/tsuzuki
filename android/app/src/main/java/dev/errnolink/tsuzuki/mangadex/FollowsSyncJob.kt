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
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.manga.interactor.UpdateManga
import exh.md.MangaDexPreferences
import exh.md.service.MangaDexAuthService
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.source.mangaDexSourceIds
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.TimeUnit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FollowsSyncJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var completed = 0
    private var total = 0
    private var label = ""

    override suspend fun doWork(): Result = withIOContext {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val preferences = MangaDexPreferences()
        val source = MdUtil.getEnabledMangaDex(sourceManager = Injekt.get())
        if (source == null || !shouldRunFollowsSync(source.isLogged(), preferences.followsSyncEnabled().get(), manual)) {
            return@withIOContext Result.success()
        }
        setForegroundSafely()
        val direction = when (inputData.getString(KEY_DIRECTION)) {
            DIRECTION_PULL -> FollowsSyncDirection.PULL
            DIRECTION_PUSH -> FollowsSyncDirection.PUSH
            else -> FollowsSyncDirection.BOTH
        }
        var pushed = 0
        var added = 0
        var favorited = 0
        if (direction == FollowsSyncDirection.PULL || direction == FollowsSyncDirection.BOTH) {
            val outcome = pull(source, preferences)
            added = outcome.addedIds.size
            favorited = outcome.favoritedIds.size
        }
        if (direction == FollowsSyncDirection.PUSH || direction == FollowsSyncDirection.BOTH) {
            pushed = push(source)
        }
        Result.success(
            workDataOf(
                ADDED to added,
                FAVORITED to favorited,
                PUSHED to pushed,
            ),
        )
    }

    private suspend fun reportProgress(title: String, done: Int, size: Int) {
        completed = done
        total = size
        label = title
        setProgress(progressData())
        runCatching { setForeground(getForegroundInfo()) }
    }

    private fun progressData() = workDataOf(COMPLETED to completed, TOTAL to total)

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID,
        NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_LIBRARY_PROGRESS)
            .setSmallIcon(R.drawable.ic_tsuzuki)
            .setContentTitle("MangaDex follows sync")
            .setContentText(if (total == 0) "Preparing" else "$label · $completed of $total titles")
            .setProgress(total, completed, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", applicationContext.workManager.createCancelPendingIntent(id))
            .build(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )

    private suspend fun pull(
        source: eu.kanade.tachiyomi.source.online.all.MangaDex,
        preferences: MangaDexPreferences,
    ): FollowsPullOutcome {
        val follows = source.fetchAllFollows().map { (manga, metadata) ->
            MangaDexFollow(manga.url, manga.title, FollowStatus.fromLong((metadata.followStatus ?: 0).toLong()))
        }
        val selectedStatuses = preferences.followsPullStatuses().get()
            .mapTo(mutableSetOf()) { FollowStatus.fromDex(it) }
        val categories = Injekt.get<GetCategories>().await()
        val defaultCategoryPref = Injekt.get<LibraryPreferences>().defaultCategory().get().toLong()
        val defaultCategoryId = categories.find { it.id == defaultCategoryPref }?.id

        val getManga = Injekt.get<GetManga>()
        val updateManga = Injekt.get<UpdateManga>()
        val networkToLocalManga = Injekt.get<NetworkToLocalManga>()
        val setMangaCategories = Injekt.get<SetMangaCategories>()

        return pullFollowsToLibrary(
            follows = follows,
            selectedStatuses = selectedStatuses,
            defaultCategoryId = defaultCategoryId,
            findManga = { url -> getManga.await(url, source.id)?.let { it.id to it.favorite } },
            insertFavorite = { url, title ->
                networkToLocalManga(
                    Manga.create().copy(
                        url = url,
                        ogTitle = title,
                        source = source.id,
                        favorite = true,
                        dateAdded = System.currentTimeMillis(),
                    ),
                    updateInfo = false,
                ).id
            },
            markFavorite = { id -> updateManga.awaitUpdateFavorite(id, true) },
            assignCategory = { id, categoryId -> setMangaCategories.await(id, listOf(categoryId)) },
            onProgress = ::reportProgress,
        )
    }

    private suspend fun push(source: eu.kanade.tachiyomi.source.online.all.MangaDex): Int {
        val authService = MangaDexAuthService(source.baseHttpClient, MangaDexFeatureRequests.headers)
        val remoteStatuses = authService.readingStatusAllManga().statuses
        val library = Injekt.get<GetFavorites>().await()
            .filter { it.source in mangaDexSourceIds }
            .mapNotNull { manga ->
                MdUtil.getMangaId(manga.url).takeIf { it.isNotBlank() }?.let {
                    MangaDexLibraryTitle(manga.id, it, manga.title)
                }
            }

        return pushReadingStatuses(
            library = library,
            remoteStatuses = remoteStatuses,
            push = { uuid, status -> source.updateFollowStatus(uuid, status) },
            onProgress = ::reportProgress,
        )
    }

    companion object {
        private const val NAME = "tsuzuki_follows_sync"
        private const val PERIODIC_NAME = "tsuzuki_follows_sync_periodic"
        private const val NOTIFICATION_ID = -1806
        private const val KEY_DIRECTION = "direction"
        private const val KEY_MANUAL = "manual"
        const val DIRECTION_PULL = "pull"
        const val DIRECTION_PUSH = "push"
        const val COMPLETED = "completed"
        const val TOTAL = "total"
        const val ADDED = "added"
        const val FAVORITED = "favorited"
        const val PUSHED = "pushed"

        fun startNow(context: Context, direction: String) {
            context.workManager.enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FollowsSyncJob>()
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .setInputData(workDataOf(KEY_DIRECTION to direction, KEY_MANUAL to true))
                    .build(),
            )
        }

        fun schedulePeriodic(context: Context, enabled: Boolean) {
            if (!enabled) {
                context.workManager.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            context.workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FollowsSyncJob>(24, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints(
                            requiredNetworkType = NetworkType.CONNECTED,
                            requiresBatteryNotLow = true,
                        ),
                    )
                    .build(),
            )
        }
    }
}
