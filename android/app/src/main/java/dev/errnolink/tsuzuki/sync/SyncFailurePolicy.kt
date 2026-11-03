package dev.errnolink.tsuzuki.sync

import java.io.IOException

/**
 * Decides when a failed sync should surface its error notification.
 *
 * The PC hub is a LAN machine that is often simply off or away: any transport-level
 * [IOException] (connection refused, timeout, unknown host, no route, reset, ...) is
 * an expected condition for a run started automatically and stays silent. Runs the
 * user started themselves still report, and protocol failures (bad key, HTTP errors —
 * thrown as non-IO exceptions) always report so a misconfiguration stays visible.
 */
object SyncFailurePolicy {

    /** True when [error] is a transport-level failure reaching or using the hub. */
    fun isHubUnreachable(error: Throwable): Boolean = error is IOException

    /** True when a sync failure should show its error notification. */
    fun shouldNotifySyncFailure(isAutomatic: Boolean, error: Throwable): Boolean =
        !isAutomatic || !isHubUnreachable(error)
}
