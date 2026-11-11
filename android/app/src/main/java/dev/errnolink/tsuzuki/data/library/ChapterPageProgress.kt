package dev.errnolink.tsuzuki.data.library

fun chapterPageProgress(lastPageRead: Long, totalPages: Int?, read: Boolean): Float? = when {
    read -> 1f
    totalPages == null || totalPages <= 0 -> null
    lastPageRead <= 0 -> 0f
    else -> ((lastPageRead + 1).coerceAtMost(totalPages.toLong())).toFloat() / totalPages
}
