@file:Suppress("PropertyName")

package exh.source

import eu.kanade.tachiyomi.source.Source

var mangaDexSourceIds: List<Long> = emptyList()

val LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(PURURIN_SOURCE_ID, NHENTAI_SOURCE_ID)

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in MANGADEX_IDS ||
    mangaDexSourceIds.binarySearch(source) >= 0

fun Source.isMdBasedSource() = id in mangaDexSourceIds

fun Source.getMainSource(): Source = if (this is EnhancedHttpSource) {
    this.source()
} else {
    this
}

@JvmName("getMainSourceInline")
inline fun <reified T : Source> Source.getMainSource(): T? = if (this is EnhancedHttpSource) {
    this.source() as? T
} else {
    this as? T
}

fun Source.getOriginalSource(): Source = if (this is EnhancedHttpSource) {
    this.originalSource
} else {
    this
}

fun Source.getEnhancedSource(): Source = if (this is EnhancedHttpSource) {
    this.enhancedSource
} else {
    this
}

inline fun <reified T> Source.anyIs(): Boolean {
    return if (this is EnhancedHttpSource) {
        originalSource is T || enhancedSource is T
    } else {
        this is T
    }
}
