package dev.errnolink.tsuzuki.ui.details

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt

@Stable
class HeroTitleAnchor {
    private var heroTop by mutableFloatStateOf(Float.NaN)
    private var titleBottom by mutableFloatStateOf(Float.NaN)

    val titleBottomInHero: Int?
        get() = if (heroTop.isNaN() || titleBottom.isNaN()) null else (titleBottom - heroTop).roundToInt()

    fun hero(modifier: Modifier = Modifier) = modifier.onGloballyPositioned { heroTop = it.positionInWindow().y }

    fun title(modifier: Modifier = Modifier) = modifier.onGloballyPositioned { titleBottom = it.positionInWindow().y + it.size.height }
}

val LocalHeroTitleAnchor = staticCompositionLocalOf<HeroTitleAnchor?> { null }
