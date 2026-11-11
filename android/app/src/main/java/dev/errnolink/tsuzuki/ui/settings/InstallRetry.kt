package dev.errnolink.tsuzuki.ui.settings

import kotlinx.coroutines.delay

suspend fun <T> retrying(
    delaysMs: List<Long>,
    isFailure: (T) -> Boolean,
    attempt: suspend () -> T,
): T {
    var result = attempt()
    for (delayMs in delaysMs) {
        if (!isFailure(result)) return result
        delay(delayMs)
        result = attempt()
    }
    return result
}
