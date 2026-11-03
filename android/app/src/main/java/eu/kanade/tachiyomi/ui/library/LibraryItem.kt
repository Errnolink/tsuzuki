package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.source.model.Source

internal const val LOCAL_SOURCE_ID_ALIAS = "local"

@Immutable
data class LibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Long = -1,
    val hasDownloads: Boolean = downloadCount > 0,
    val unreadCount: Long = -1,
    val isLocal: Boolean = false,
    val sourceLanguage: String = "",
    val useLangIcon: Boolean = true,
    val source: Source? = null,
) {
    val id: Long = libraryManga.id
}
