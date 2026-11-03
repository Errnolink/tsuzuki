package dev.errnolink.tsuzuki.sync

import java.net.URI
import java.net.URLDecoder
import java.net.URISyntaxException

/**
 * A PC hub pairing request decoded from a `tsuzuki://pair` link.
 *
 * @param host http(s) base URL of the SyncYomi hub, without a trailing slash.
 * @param key SyncYomi API key.
 * @param name display name of the PC, when the link carried one.
 */
data class PairRequest(
    val host: String,
    val key: String,
    val name: String?,
)

/**
 * Parses the pair link the Windows hub shows for QR pairing:
 * `tsuzuki://pair?host=<url-encoded http://ip:port>&key=<key>&name=<url-encoded PC name>`.
 *
 * Only that scheme and link host are accepted. The `host` parameter must be an
 * http or https URL with a hostname (any trailing slash is stripped), the `key`
 * must be non-empty, and `name` is optional. Anything else returns null.
 */
fun parsePairLink(raw: String): PairRequest? {
    val link = try {
        URI(raw.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    if (!link.scheme.equals("tsuzuki", ignoreCase = true)) return null
    if (!link.host.equals("pair", ignoreCase = true)) return null

    var hostParam: String? = null
    var keyParam: String? = null
    var nameParam: String? = null
    for (entry in link.rawQuery?.split('&') ?: return null) {
        if (entry.isEmpty()) continue
        val equals = entry.indexOf('=')
        val name = if (equals < 0) entry else entry.substring(0, equals)
        val value = percentDecode(if (equals < 0) "" else entry.substring(equals + 1)) ?: return null
        when (name) {
            "host" -> hostParam = value
            "key" -> keyParam = value
            "name" -> nameParam = value
        }
    }

    val key = keyParam?.takeIf { it.isNotEmpty() } ?: return null
    val host = normalizeHubHost(hostParam ?: return null) ?: return null
    return PairRequest(
        host = host,
        key = key,
        name = nameParam?.takeIf { it.isNotEmpty() },
    )
}

private fun normalizeHubHost(raw: String): String? {
    val url = try {
        URI(raw)
    } catch (_: URISyntaxException) {
        return null
    }
    if (!url.scheme.equals("http", ignoreCase = true) && !url.scheme.equals("https", ignoreCase = true)) return null
    if (url.host.isNullOrEmpty()) return null
    return raw.trim().trimEnd('/')
}

// The link is emitted with %XX encoding; '+' is kept literal because API keys
// may contain it (URLDecoder alone would read it as a space).
private fun percentDecode(value: String): String? {
    if ('%' !in value) return value
    return try {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
