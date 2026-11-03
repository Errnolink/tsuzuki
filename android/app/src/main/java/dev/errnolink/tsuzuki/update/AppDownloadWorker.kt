package dev.errnolink.tsuzuki.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Request
import okio.buffer
import okio.sink
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class AppDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = UpdateNotifier(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return notifier.createDownloadForegroundInfo(progress = 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        setForegroundSafely()

        val apkFile = File(context.cacheDir, "update.apk")
        if (apkFile.exists()) apkFile.delete()

        val progressListener = object : ProgressListener {
            private var lastProgress = -1
            private var lastUpdate = 0L

            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                if (contentLength <= 0L) return
                val progress = ((bytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                val now = System.currentTimeMillis()
                if (progress != lastProgress && (progress - lastProgress >= 2 || now - lastUpdate >= 250L || done)) {
                    lastProgress = progress
                    lastUpdate = now
                    notifier.onDownloadProgress(progress)
                }
            }
        }

        try {
            val networkHelper = Injekt.get<NetworkHelper>()
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", AppUpdateChecker.USER_AGENT)
                .build()

            val call = networkHelper.client.newCachelessCallWithProgress(request, progressListener)
            val response = call.awaitSuccess()

            apkFile.sink().buffer().use { sink ->
                response.body.source().use { source ->
                    sink.writeAll(source)
                }
            }

            notifier.cancelProgress()
            AppInstaller.install(context, apkFile)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.cancelProgress()
            notifier.showDownloadError()
            Result.failure()
        }
    }

    companion object {
        const val TAG = "AppDownloadWorker"
        const val KEY_DOWNLOAD_URL = "download_url"

        fun start(context: Context, downloadUrl: String) {
            val inputData = Data.Builder()
                .putString(KEY_DOWNLOAD_URL, downloadUrl)
                .build()

            val request = OneTimeWorkRequestBuilder<AppDownloadWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
