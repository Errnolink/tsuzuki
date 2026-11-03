package dev.errnolink.tsuzuki.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.net.toUri
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import java.io.File

class AppUpdateBroadcast : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifier = UpdateNotifier(context)
        if (intent.action == ACTION_INSTALL_STATUS) {
            val extras = intent.extras ?: return
            val status = extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val fileUri = extras.getString(EXTRA_FILE_URI)?.toUri()

            when (UpdateLogic.evaluateInstallStatus(status)) {
                InstallDecision.Success -> {
                    notifier.cancelAll()
                    cleanCachedApk(context)
                }
                InstallDecision.PromptUser -> {
                    val confirmIntent = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                    notifier.promptInstallUserAction(confirmIntent, fileUri)
                }
                InstallDecision.FallbackPrompt -> {
                    notifier.promptInstallFallback(fileUri)
                }
                InstallDecision.Aborted -> {
                    notifier.cancelAll()
                }
            }
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            notifier.cancelAll()
            cleanCachedApk(context)
        }
    }

    private fun cleanCachedApk(context: Context) {
        try {
            val apk = File(context.cacheDir, "update.apk")
            if (apk.exists()) apk.delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "dev.errnolink.tsuzuki.update.INSTALL_STATUS"
        const val EXTRA_FILE_URI = "dev.errnolink.tsuzuki.update.EXTRA_FILE_URI"
        const val EXTRA_SESSION_ID = "dev.errnolink.tsuzuki.update.EXTRA_SESSION_ID"
    }
}
