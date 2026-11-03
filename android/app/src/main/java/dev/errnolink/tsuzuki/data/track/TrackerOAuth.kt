package dev.errnolink.tsuzuki.data.track

import eu.kanade.tachiyomi.util.PkceUtil
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackerOAuth(
    private val preferences: PreferenceStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Session(val state: String, val verifier: String)

    @Synchronized
    fun begin(host: String, verifier: String = ""): Session {
        val session = Session(PkceUtil.generateCodeVerifier(), verifier)
        pending(host).set("${now()}|${session.state}|${session.verifier}")
        return session
    }

    @Synchronized
    fun consume(host: String, state: String?): Session? {
        val preference = pending(host)
        val fields = preference.get().split('|', limit = 3)
        val startedAt = fields.firstOrNull()?.toLongOrNull() ?: return null
        val age = now() - startedAt
        if (age !in 0..600_000 || fields.size != 3) {
            preference.delete()
            return null
        }
        if (state.isNullOrBlank() || fields[1] != state) return null
        preference.delete()
        return Session(fields[1], fields[2])
    }

    private fun pending(host: String) = preferences.getString(
        Preference.privateKey("tracker_oauth_pending_$host"),
        "",
    )

    companion object {
        val instance: TrackerOAuth by lazy { TrackerOAuth(Injekt.get()) }
    }
}
