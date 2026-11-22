package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.errnolink.tsuzuki.ui.motion.MangaHero
import tachiyomi.domain.history.interactor.GetHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun rememberLastReadSeed(enabled: Boolean): Color? {
    if (!enabled) return null
    val getHistory = remember { Injekt.get<GetHistory>() }
    val recent by remember(enabled) { getHistory.subscribeRecent(1) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val mangaId = recent.firstOrNull()?.mangaId
    LaunchedEffect(mangaId) { mangaId?.let(MangaHero::requestCardColor) }
    return mangaId?.let { MangaHero.peekCardColor(it) }
}
