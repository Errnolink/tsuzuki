package eu.kanade.presentation.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DuplicateMangaDialog(
    duplicates: List<MangaWithChapterCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: (manga: Manga) -> Unit,
    onMigrate: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
    targetManga: Manga,
    bulkFavoriteManga: Manga? = null,
    onAllowAllDuplicate: () -> Unit = {},
    onSkipAllDuplicate: () -> Unit = {},
    onSkipDuplicate: () -> Unit = {},
    stopRunning: () -> Unit = {},
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.possible_duplicates_title),
        onDismissRequest = {
            stopRunning()
            onDismissRequest()
        },
        modifier = modifier,
        kind = SheetKind.Picker,
    ) {
        TsuzukiText(
            targetManga.title,
            Modifier.padding(horizontal = TsuzukiSpacing.row),
            TsuzukiTheme.typography.headline,
        )
        TsuzukiText(
            stringResource(MR.strings.possible_duplicates_summary),
            Modifier.padding(horizontal = TsuzukiSpacing.row),
            TsuzukiTheme.typography.footnote,
            TsuzukiTheme.colors.secondary,
        )
        InsetGroupedList {
            duplicates.forEachIndexed { index, duplicate ->
                val manga = duplicate.manga
                val source = sourceManager.getOrStub(manga.source)
                if (index > 0) GroupDivider()
                GroupedRow(
                    title = manga.title,
                    subtitle = buildString {
                        append(source.name)
                        append(" · ")
                        append(
                            pluralStringResource(
                                MR.plurals.manga_num_chapters,
                                duplicate.chapterCount.toInt(),
                                duplicate.chapterCount,
                            ),
                        )
                    },
                    leading = {
                        MangaCover.Book(
                            data = manga,
                            modifier = Modifier.width(48.dp),
                            shape = RoundedCornerShape(TsuzukiCorners.cover),
                        )
                    },
                    trailing = {
                        TsuzukiText(
                            stringResource(MR.strings.migrate),
                            Modifier
                                .clickable { onMigrate(manga) }
                                .padding(horizontal = TsuzukiSpacing.small, vertical = TsuzukiSpacing.small),
                            TsuzukiTheme.typography.subhead,
                            TsuzukiTheme.colors.accent,
                        )
                    },
                    chevron = true,
                    onClick = { onOpenManga(manga) },
                )
            }
        }
        InsetGroupedList {
            GroupedRow(
                title = stringResource(MR.strings.action_add_anyway),
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            )
            if (bulkFavoriteManga != null) {
                GroupedRow(
                    title = stringResource(KMR.strings.action_allow_all_duplicate_manga),
                    onClick = {
                        onDismissRequest()
                        onAllowAllDuplicate()
                    },
                )
                GroupedRow(
                    title = stringResource(KMR.strings.action_skip_duplicate_manga),
                    onClick = {
                        onDismissRequest()
                        onSkipDuplicate()
                    },
                )
                GroupedRow(
                    title = stringResource(KMR.strings.action_skip_all_duplicate_manga),
                    onClick = {
                        onDismissRequest()
                        onSkipAllDuplicate()
                    },
                )
                GroupedRow(
                    title = stringResource(MR.strings.action_show_manga),
                    onClick = { onOpenManga(bulkFavoriteManga) },
                )
            }
            GroupedRow(
                title = stringResource(MR.strings.action_cancel),
                onClick = onDismissRequest,
            )
        }
    }
}
