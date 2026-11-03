package dev.errnolink.tsuzuki.data.track

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal const val TRACKER_LOGIN_TIMEOUT_MILLIS = 20_000L

internal suspend fun <T> trackerLogin(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T,
): T = withTimeout(TRACKER_LOGIN_TIMEOUT_MILLIS) {
    withContext(dispatcher) { block() }
}
