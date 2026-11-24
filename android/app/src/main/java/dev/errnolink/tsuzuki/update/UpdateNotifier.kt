package dev.errnolink.tsuzuki.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ForegroundInfo
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class UpdateNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun createDownloadForegroundInfo(progress: Int): ForegroundInfo {
        val notification = createDownloadNotification(progress).build()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(ID_UPDATER_PROGRESS, notification, foregroundServiceType)
    }

    fun createDownloadNotification(progress: Int): NotificationCompat.Builder {
        return context.notificationBuilder(Notifications.CHANNEL_COMMON) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setContentTitle(context.stringResource(MR.strings.app_name))
            setContentText(context.stringResource(MR.strings.update_check_notification_download_in_progress))
            setProgress(100, progress, progress == 0)
            setOngoing(true)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
        }
    }

    fun onDownloadProgress(progress: Int) {
        notificationManager.notify(ID_UPDATER_PROGRESS, createDownloadNotification(progress).build())
    }

    fun promptInstallUserAction(confirmIntent: Intent?, fileUri: Uri?) {
        val intent = confirmIntent ?: fileUri?.let { createSystemInstallIntent(it) } ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        promptInstall(intent)
    }

    fun promptInstallFallback(fileUri: Uri?) {
        promptInstall(createSystemInstallIntent(fileUri ?: return))
    }

    private fun promptInstall(intent: Intent) {
        cancelProgress()
        val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inForeground && runCatching { context.startActivity(intent) }.isSuccess) {
            notificationManager.cancel(ID_UPDATER_PROMPT)
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_APP_UPDATE, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.stringResource(MR.strings.channel_app_updates))
                .build(),
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
        val builder = context.notificationBuilder(CHANNEL_APP_UPDATE) {
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentTitle(context.stringResource(MR.strings.app_name))
            setContentText(context.stringResource(MR.strings.update_check_notification_download_complete))
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setOngoing(false)
        }
        notificationManager.notify(ID_UPDATER_PROMPT, builder.build())
    }

    fun showDownloadError() {
        val builder = context.notificationBuilder(Notifications.CHANNEL_COMMON) {
            setSmallIcon(android.R.drawable.stat_notify_error)
            setContentTitle(context.stringResource(MR.strings.app_name))
            setContentText(context.stringResource(MR.strings.update_check_notification_download_error))
            setAutoCancel(true)
            setOngoing(false)
        }
        notificationManager.notify(ID_UPDATER_PROMPT, builder.build())
    }

    private fun createSystemInstallIntent(fileUri: Uri): Intent {
        @Suppress("DEPRECATION")
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(fileUri, APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun cancelProgress() {
        notificationManager.cancel(ID_UPDATER_PROGRESS)
    }

    fun cancelAll() {
        notificationManager.cancel(ID_UPDATER_PROGRESS)
        notificationManager.cancel(ID_UPDATER_PROMPT)
    }

    companion object {
        const val ID_UPDATER_PROGRESS = -601
        const val ID_UPDATER_PROMPT = -602
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val CHANNEL_APP_UPDATE = "app_update_channel"
    }
}
