// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.buildNotificationChannel
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object MangaDexSessionNotification {
    private const val CHANNEL = "mangadex_authentication"
    private const val ID = 1901

    fun isSessionRejected(status: Int): Boolean = status == 400 || status == 401 || status == 403

    fun show() {
        val context = Injekt.get<Application>()
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(buildNotificationChannel(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT) { setName("MangaDex authentication") }),
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MangaDexIntents.SETTINGS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.notify(ID, CHANNEL) {
            setSmallIcon(R.drawable.ic_tsuzuki)
            setContentTitle("MangaDex session expired")
            setContentText("Sign in again to resume follows and chapter read sync.")
            setContentIntent(PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setAutoCancel(true)
            setOnlyAlertOnce(true)
        }
    }

    fun dismiss() = Injekt.get<Application>().cancelNotification(ID)
}
