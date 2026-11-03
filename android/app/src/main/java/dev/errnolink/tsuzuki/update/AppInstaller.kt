package dev.errnolink.tsuzuki.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import eu.kanade.tachiyomi.util.storage.getUriCompat
import java.io.File

object AppInstaller {

    fun install(context: Context, apkFile: File) {
        val fileUri = apkFile.getUriCompat(context)
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            session.openWrite("tsuzuki_update", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            val statusIntent = Intent(context, AppUpdateBroadcast::class.java).apply {
                action = AppUpdateBroadcast.ACTION_INSTALL_STATUS
                putExtra(AppUpdateBroadcast.EXTRA_FILE_URI, fileUri.toString())
                putExtra(AppUpdateBroadcast.EXTRA_SESSION_ID, sessionId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val statusReceiver = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                flags,
            ).intentSender

            session.commit(statusReceiver)
        } catch (_: Exception) {
            UpdateNotifier(context).promptInstallFallback(fileUri)
        }
    }
}
