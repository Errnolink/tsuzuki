package dev.errnolink.tsuzuki.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class AppUpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = Injekt.get<UpdatePreferences>()
        if (!preferences.autoUpdate().get()) {
            return@withContext Result.success()
        }

        val checker = AppUpdateChecker(preferences = preferences)
        when (val result = checker.checkForUpdate(isUserPrompt = false)) {
            is CheckResult.NewUpdate -> {
                AppDownloadWorker.start(context, result.candidate.downloadUrl)
                Result.success()
            }
            is CheckResult.NoNewUpdate -> {
                Result.success()
            }
            is CheckResult.Error -> {
                Result.success()
            }
        }
    }
}

object AppUpdateJob {
    private const val TAG_DAILY = "AppUpdateJob:daily"
    private const val TAG_APP_START = "AppUpdateJob:app_start"
    private const val DEBOUNCE_INTERVAL_MS = 6L * 60L * 60L * 1000L

    fun setupDailyTask(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(TAG_DAILY)
            .build()

        context.workManager.enqueueUniquePeriodicWork(
            TAG_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun checkOnAppStart(context: Context) {
        val preferences = Injekt.get<UpdatePreferences>()
        if (!preferences.autoUpdate().get()) return

        val lastCheck = preferences.lastAppCheck().get()
        val now = System.currentTimeMillis()
        if (now - lastCheck < DEBOUNCE_INTERVAL_MS) return

        val request = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .addTag(TAG_APP_START)
            .build()

        context.workManager.enqueueUniqueWork(
            TAG_APP_START,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
