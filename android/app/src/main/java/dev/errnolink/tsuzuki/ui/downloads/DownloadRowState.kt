package dev.errnolink.tsuzuki.ui.downloads

import eu.kanade.tachiyomi.data.download.model.Download

data class DownloadRowView(
    val subtitle: String,
    val progress: Float?,
    val indeterminate: Boolean,
    val failed: Boolean,
)

fun downloadRowState(
    status: Download.State,
    running: Boolean,
    progressPercent: Int,
    downloadedPages: Int,
    totalPages: Int?,
    chapterName: String,
): DownloadRowView {
    val detail = when (status) {
        Download.State.DOWNLOADING ->
            if (totalPages == null) "downloading" else "$downloadedPages of $totalPages pages"
        Download.State.QUEUE, Download.State.NOT_DOWNLOADED -> if (running) "waiting" else "paused"
        Download.State.DOWNLOADED -> "downloaded"
        Download.State.ERROR -> "download failed · retry available"
    }
    return DownloadRowView(
        subtitle = "$chapterName · $detail",
        progress = if (status == Download.State.DOWNLOADING && totalPages != null) (progressPercent / 100f).coerceIn(0f, 1f) else null,
        indeterminate = status == Download.State.DOWNLOADING && totalPages == null,
        failed = status == Download.State.ERROR,
    )
}
