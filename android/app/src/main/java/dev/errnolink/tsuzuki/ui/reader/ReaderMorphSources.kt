package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.errnolink.tsuzuki.designsystem.MenuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource

/**
 * Shared Liquid Glass morph origins for reader triggers so popovers (Chapters, Lighting, Settings)
 * pour smoothly out of whichever top/dock button the reader tapped.
 */
@Stable
internal class ReaderMorphSources(
    val chaptersTop: MenuMorphSource,
    val chaptersDock: MenuMorphSource,
    val lighting: MenuMorphSource,
    val settings: MenuMorphSource,
) {
    var activeChaptersSource by mutableStateOf(chaptersDock)
}

internal val LocalReaderMorphSources = compositionLocalOf<ReaderMorphSources?> { null }

@Composable
internal fun rememberReaderMorphSources(): ReaderMorphSources {
    val chaptersTop = rememberMenuMorphSource(borderless = true)
    val chaptersDock = rememberMenuMorphSource(borderless = true)
    val lighting = rememberMenuMorphSource(borderless = true)
    val settings = rememberMenuMorphSource(borderless = true)
    return remember(chaptersTop, chaptersDock, lighting, settings) {
        ReaderMorphSources(
            chaptersTop = chaptersTop,
            chaptersDock = chaptersDock,
            lighting = lighting,
            settings = settings,
        )
    }
}
