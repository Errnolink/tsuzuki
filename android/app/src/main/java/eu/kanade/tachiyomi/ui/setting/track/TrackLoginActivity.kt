package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import dev.errnolink.tsuzuki.data.track.TrackerOAuth
import dev.errnolink.tsuzuki.data.track.trackerLogin
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

class TrackLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(uri: Uri) {
        val data = sequenceOf(uri.encodedQuery, uri.encodedFragment)
            .filterNotNull()
            .flatMap { it.split('&').asSequence() }
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split('=', limit = 2)
                Uri.decode(parts[0]) to parts.getOrNull(1)?.let(Uri::decode)
            }

        lifecycleScope.launch {
            try {
                val host = uri.host ?: error("Unrecognized tracker callback.")
                val session = TrackerOAuth.instance.consume(host, data["state"])
                    ?: error("This sign-in expired or started in another app. Start again in this app.")
                val tracker = when (host) {
                    "anilist-auth" -> trackerManager.aniList
                    "bangumi-auth" -> trackerManager.bangumi
                    "myanimelist-auth" -> trackerManager.myAnimeList
                    "shikimori-auth" -> trackerManager.shikimori
                    else -> error("Unrecognized tracker callback.")
                }
                val code = data[if (host == "anilist-auth") "access_token" else "code"]
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Sign-in was cancelled or authorization was denied.")
                trackerLogin { tracker.login(session.verifier, code) }
            } catch (e: TimeoutCancellationException) {
                toast("Sign-in timed out. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Sign-in failed. Please try again.")
            } finally {
                returnToSettings()
            }
        }
    }
}
