package dev.errnolink.tsuzuki.ui.next

data class NextTileProgress(val caption: String, val fraction: Float?) {
    companion object {
        fun of(read: Long, total: Long, unread: Long): NextTileProgress {
            val safeTotal = total.coerceAtLeast(0)
            val safeRead = read.coerceIn(0, safeTotal)
            return when {
                safeTotal == 0L -> NextTileProgress("No chapters yet", null)
                safeRead == 0L -> NextTileProgress(chapters(safeTotal), null)
                unread <= 0L -> NextTileProgress("Caught up · ${chapters(safeTotal)}", null)
                else -> NextTileProgress("$unread unread · $safeRead of $safeTotal read", safeRead.toFloat() / safeTotal)
            }
        }

        private fun chapters(count: Long) = if (count == 1L) "1 chapter" else "$count chapters"
    }
}
