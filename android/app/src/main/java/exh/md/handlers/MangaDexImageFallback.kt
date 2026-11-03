package exh.md.handlers

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MangaDexImageFallback {
    fun url(imageUrl: String?, pageUrl: String): String? {
        if (!pageUrl.contains("https://api.mangadex.org/at-home/server/")) return null
        val raw = imageUrl ?: return null
        val relative = raw.startsWith("/")
        val url = (if (relative) "https://uploads.mangadex.org$raw" else raw).toHttpUrlOrNull() ?: return null
        if (!relative && url.host == "uploads.mangadex.org") return null
        val path = url.encodedPath
        if (!path.startsWith("/data/") && !path.startsWith("/data-saver/")) return null
        return url.newBuilder().scheme("https").host("uploads.mangadex.org").port(443).query(null).build().toString()
    }
}
