package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.LocalSubmenuHolder
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.PullDownDivider
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuOption
import dev.errnolink.tsuzuki.designsystem.PullDownToggleItem
import dev.errnolink.tsuzuki.designsystem.PullDownTriStateItem
import dev.errnolink.tsuzuki.designsystem.SubmenuData
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.rememberMangaDexChapterPresentation
import dev.errnolink.tsuzuki.ui.library.toPullDownTriState
import dev.errnolink.tsuzuki.ui.library.toTriState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.downloadedFilter
import exh.md.MangaDexPreferences
import exh.md.utils.MdLang
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ChapterSettingsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    manga: Manga?,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnreadFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    scanlatorFilterActive: Boolean,
    onScanlatorFilterClicked: () -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    onSortOrderChanged: ((Boolean) -> Unit)? = null,
) {
    var confirmDefaults by rememberSaveable { mutableStateOf(false) }
    val downloadedOnlyPreference = remember { Injekt.get<BasePreferences>().downloadedOnly() }
    val downloadedOnly by downloadedOnlyPreference.collectAsState()
    val sortOptions = listOf(
        stringResource(MR.strings.sort_by_source) to Manga.CHAPTER_SORTING_SOURCE,
        stringResource(MR.strings.sort_by_number) to Manga.CHAPTER_SORTING_NUMBER,
        stringResource(MR.strings.sort_by_upload_date) to Manga.CHAPTER_SORTING_UPLOAD_DATE,
        stringResource(MR.strings.action_sort_alpha) to Manga.CHAPTER_SORTING_ALPHABET,
    )
    val languages = manga?.let { rememberMangaDexChapterPresentation(it.id, emptyList()) }
    val showByNumber = manga?.displayMode == Manga.CHAPTER_DISPLAY_NUMBER

    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        paneTitle = "Chapter options",
        glueToAnchor = true,
        width = 280.dp,
    ) {
        val submenuHolder = LocalSubmenuHolder.current
        PullDownSubmenuItem(
            title = "Sort by",
            value = sortOptions.firstOrNull { it.second == manga?.sorting }?.first,
            trailing = onSortOrderChanged?.let { onOrder ->
                {
                    val descending = manga?.sortDescending() ?: false
                    val colors = TsuzukiTheme.colors
                    Box(
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = if (descending) "Sort ascending" else "Sort descending",
                            ) { onOrder(!descending) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(colors.separator.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                imageVector = if (descending) TsuzukiIcons.ArrowDown else TsuzukiIcons.ArrowUp,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colors.accent),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            },
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("Sort by") {
                    sortOptions.forEachIndexed { i, (label, mode) ->
                        if (i > 0) PullDownDivider()
                        PullDownSubmenuOption(label, manga?.sorting == mode, {
                            onSortModeChanged(mode)
                            submenuHolder?.activeSubmenu = null
                        })
                    }
                }
            },
        )
        PullDownSubmenuItem(
            title = "Show",
            value = if (showByNumber) "Chapter number" else "Title",
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("Show") {
                    PullDownSubmenuOption("Title", !showByNumber, {
                        onDisplayModeChanged(Manga.CHAPTER_DISPLAY_NAME)
                        submenuHolder?.activeSubmenu = null
                    })
                    PullDownDivider()
                    PullDownSubmenuOption("Chapter number", showByNumber, {
                        onDisplayModeChanged(Manga.CHAPTER_DISPLAY_NUMBER)
                        submenuHolder?.activeSubmenu = null
                    })
                }
            },
        )
        PullDownSectionDivider()
        val downloaded = if (downloadedOnly) TriState.ENABLED_IS else manga?.downloadedFilter ?: TriState.DISABLED
        PullDownTriStateItem(
            label = "Downloaded",
            state = downloaded.toPullDownTriState(),
            enabled = !downloadedOnly,
            onSet = { onDownloadFilterChanged(it.toTriState()) },
            onCycle = { if (!downloadedOnly) onDownloadFilterChanged(downloaded.next()) },
        )
        val unread = manga?.unreadFilter ?: TriState.DISABLED
        PullDownTriStateItem(
            label = "Unread",
            state = unread.toPullDownTriState(),
            onSet = { onUnreadFilterChanged(it.toTriState()) },
            onCycle = { onUnreadFilterChanged(unread.next()) },
        )
        val bookmarked = manga?.bookmarkedFilter ?: TriState.DISABLED
        PullDownTriStateItem(
            label = "Bookmarked",
            state = bookmarked.toPullDownTriState(),
            onSet = { onBookmarkedFilterChanged(it.toTriState()) },
            onCycle = { onBookmarkedFilterChanged(bookmarked.next()) },
        )
        PullDownMenuItem(
            text = "Scanlators…",
            subtitle = if (scanlatorFilterActive) "Filtered" else null,
            onClick = {
                onDismissRequest()
                onScanlatorFilterClicked()
            },
        )
        val mangaUuid = languages?.mangaUuid
        if (languages != null && mangaUuid != null && languages.languages.size > 1) {
            val selected = languages.selected.ifEmpty { languages.languages }
            PullDownSubmenuItem(
                title = "Languages",
                value = if (selected.size == languages.languages.size) "All" else "${selected.size} of ${languages.languages.size}",
                onClick = {
                    submenuHolder?.activeSubmenu = SubmenuData("Languages") {
                        languages.languages.forEach { language ->
                            val label = MdLang.fromIsoCode(language)?.name?.lowercase()?.replace('_', ' ')
                                ?.replaceFirstChar(Char::uppercase) ?: language.uppercase()
                            PullDownToggleItem(
                                label = label,
                                checked = language in selected,
                                onChecked = { checked ->
                                    val next = if (checked) selected + language else selected - language
                                    if (next.isNotEmpty()) MangaDexPreferences().mangaLanguages(mangaUuid).set(next)
                                },
                            )
                        }
                        PullDownDivider()
                        PullDownMenuItem(
                            text = "Show all languages",
                            onClick = { MangaDexPreferences().mangaLanguages(mangaUuid).delete() },
                        )
                    }
                },
            )
        }
        PullDownSectionDivider()
        PullDownMenuItem(
            text = "Save as defaults…",
            onClick = {
                confirmDefaults = true
                onDismissRequest()
            },
        )
        PullDownMenuItem(
            text = "Reset to defaults",
            destructive = true,
            onClick = {
                onResetToDefault()
                onDismissRequest()
            },
        )
    }

    if (confirmDefaults) {
        var applyExisting by rememberSaveable { mutableStateOf(false) }
        DetentSheet(true, "Default chapter settings", { confirmDefaults = false }) {
            TsuzukiText(stringResource(MR.strings.confirm_set_chapter_settings))
            GroupedRow(
                stringResource(MR.strings.also_set_chapter_settings_for_library),
                checked = applyExisting,
                onCheckedChange = { applyExisting = it },
            )
            PillButton("Save defaults", {
                onSetAsDefault(applyExisting)
                confirmDefaults = false
            })
            PillButton("Cancel", { confirmDefaults = false }, prominent = false)
        }
    }
}
