package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.ui.UiPreferences
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    selection: List<Manga>,
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }
    val selected = remember(selection) { selection.mapTo(HashSet()) { it.id } }
    LazyRow(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(titles, key = { it.id }) { item ->
            val manga by getManga(item)
            MangaItem(manga.title, manga.asMangaCover(), manga.favorite, { onClick(manga) }, { onLongClick(manga) }, manga.id in selected)
        }
    }
}

@Composable
internal fun MangaItem(
    title: String,
    cover: MangaCover,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    usePanoramaCover: Boolean? = null,
) {
    val panorama = usePanoramaCover ?: Injekt.get<UiPreferences>().usePanoramaCoverFlow().collectAsState().value
    Column(
        Modifier.width(if (panorama && (cover.ratio ?: 2f / 3f) > 1f) 200.dp else 120.dp)
            .background(if (isSelected) TsuzukiTheme.colors.selectedFill else Color.Transparent, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            ShellCover(cover, title, fixedRatio = if (panorama) null else 2f / 3f)
            if (isFavorite) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { ShellBadge("In library") }
        }
        TsuzukiText(title, style = TsuzukiTheme.typography.subhead, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun EmptyResultItem() {
    ShellCaption(stringResource(MR.strings.no_results_found), Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}
