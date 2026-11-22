package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.coverShape
import tachiyomi.domain.manga.model.MangaCover

/** The in-library check tint on shelf tiles (no colour-role token carries this green today). */
internal val InLibraryCheckTint = Color(0xFF7EE0A5)

/**
 * One cover tile: the cover box every shelf and search tile shares — clip, aspect ratio, the
 * optional in-library mark and an overlay slot for selection marks and pills. The library grid
 * tile keeps its own overlay stack (badges, unread pill, continue button).
 */
@Composable
fun CoverTile(
    cover: MangaCover,
    title: String,
    modifier: Modifier = Modifier,
    fixedRatio: Float? = 2f / 3f,
    shape: Shape? = coverShape(),
    coverModifier: Modifier = Modifier.fillMaxSize(),
    inLibraryCheck: Boolean = false,
    inLibraryBadge: String? = null,
    hero: Boolean = true,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(modifier.then(if (shape != null) Modifier.clip(shape) else Modifier)) {
        ShellCover(cover, title, coverModifier, fixedRatio = fixedRatio, hero = hero)
        if (inLibraryCheck) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Check, null, Modifier.size(12.dp), tint = InLibraryCheckTint)
            }
        }
        if (inLibraryBadge != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { ShellBadge(inLibraryBadge) }
        }
        overlay?.invoke(this)
    }
}
