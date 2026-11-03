package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.mangadex.MangaDexAboutRows
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.manga.components.SearchMetadataChips
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.Manga

/**
 * Full-description sheet: description (markdown), all tags (today's tag menus), EH metadata
 * chips, and — for MangaDex titles — the rating histogram + info table + creators from
 * DetailsParts' [MangaDexAboutRows].
 */
@Composable
fun DetailsAboutSheet(
    visible: Boolean,
    manga: Manga,
    searchMetadataChips: SearchMetadataChips?,
    showMangaDexSection: Boolean,
    mangaId: Long,
    onDismissRequest: () -> Unit,
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (String) -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
) {
    DetentSheet(
        visible = visible,
        title = "About",
        onDismissRequest = onDismissRequest,
        initialDetent = SheetDetent.Large,
    ) {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val loadDescriptionImages = remember { uiPreferences.imagesInDescription().get() }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            manga.description?.takeIf { it.isNotBlank() }?.let { description ->
                SelectionContainer { MarkdownRender(content = description, loadImages = loadDescriptionImages) }
            }
            aboutTagSections(manga.genre.orEmpty(), searchMetadataChips).forEach { section ->
                TsuzukiText(section.title, style = TsuzukiTheme.typography.sectionHeader, color = TsuzukiTheme.colors.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    section.chips.forEach { tag ->
                        TagChip(tag.label, tag.query, onTagSearch, onCopyTagToClipboard, doSearch)
                    }
                }
            }
            if (showMangaDexSection) {
                MangaDexAboutRows(mangaId)
            }
        }
    }
}

internal data class AboutTagChip(val label: String, val query: String)

internal data class AboutTagSection(val title: String, val chips: List<AboutTagChip>)

/** Tags appear once: grouped by namespace when namespace grouping exists, else one flat deduplicated section. */
internal fun aboutTagSections(genre: List<String>, grouped: SearchMetadataChips?): List<AboutTagSection> = when {
    grouped != null -> grouped.tags.map { (namespace, values) ->
        AboutTagSection(namespace, values.map { AboutTagChip(it.text.substringAfter(':').trim(), it.search) })
    }
    genre.isNotEmpty() -> listOf(AboutTagSection("Tags", genre.map { AboutTagChip(it.substringAfter(':').trim(), it) }.distinct()))
    else -> emptyList()
}
