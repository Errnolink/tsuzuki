package dev.errnolink.tsuzuki.sync

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Routes `tsuzuki://pair` links — scanned with the phone camera (deep link) or with
 * the in-app QR button — to one confirmation prompt hosted by the activity composition.
 */
object PairDeepLink {

    /** The pair link awaiting user confirmation, if any. */
    val pending = MutableStateFlow<PairRequest?>(null)

    /**
     * Offers a scanned pair link for confirmation; returns false when [raw] is not a
     * valid pair link so the caller can keep its old behaviour.
     */
    fun handle(raw: String?): Boolean {
        val request = raw?.let { parsePairLink(it) } ?: return false
        pending.value = request
        return true
    }

    fun handle(intent: Intent): Boolean =
        if (intent.action == Intent.ACTION_VIEW) handle(intent.dataString) else false

    /** Offers an already-parsed pair link for confirmation. */
    fun propose(request: PairRequest) {
        pending.value = request
    }

    fun dismiss() {
        pending.value = null
    }
}
