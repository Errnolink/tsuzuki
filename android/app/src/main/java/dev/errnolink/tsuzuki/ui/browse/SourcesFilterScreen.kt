package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiMinusMark
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji

@Composable
fun SourcesFilterContent(
    navigateUp: () -> Unit,
    state: SourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickSources: (Boolean, List<Source>) -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(
        title = stringResource(MR.strings.label_sources),
        navigateUp = navigateUp,
    ) {
        if (state.isEmpty) {
            item(key = "sources-filter-empty") {
                ShellEmpty("No sources to filter", "Install an extension to add sources first.")
            }
            return@SettingsScaffold
        }
        state.items.forEach { (language, sources) ->
            val enabled = language in state.enabledLanguages
            item(key = "source-filter-$language", contentType = "source-filter-group") {
                InsetGroupedList {
                    GroupedRow(
                        title = LocaleHelper.getSourceDisplayName(language, context) +
                            (
                                " (${LocaleHelper.getDisplayName(language)} ${FlagEmoji.getEmojiLangFlag(language)})"
                                    .takeIf { language !in listOf("all", "other") } ?: " (${FlagEmoji.getEmojiLangFlag(language)})"
                            ),
                        checked = enabled,
                        onCheckedChange = { onClickLanguage(language) },
                    )
                    if (enabled) {
                        val allEnabled = sources.none { it.id.toString() in state.disabledSources }
                        GroupedRow(
                            title = stringResource(SYMR.strings.pref_category_all_sources) +
                                " (${FlagEmoji.getEmojiLangFlag(language)})",
                            checked = allEnabled,
                            onCheckedChange = { onClickSources(!allEnabled, sources) },
                        )
                        sources.forEach { source ->
                            val sourceEnabled = source.id.toString() !in state.disabledSources
                            val extension = source.installedExtension
                            GroupedRow(
                                title = source.visualName +
                                    (extension?.let { ext -> " (${ext.name})".takeIf { ext.name != source.name } } ?: ""),
                                subtitle = listOfNotNull(
                                    FlagEmoji.getEmojiLangFlag(source.lang) + " " +
                                        LocaleHelper.getSourceDisplayName(source.lang, context),
                                    if (extension?.isNsfw == true) stringResource(MR.strings.ext_nsfw_short) else null,
                                ).joinToString(" · "),
                                leading = {
                                    SourceIcon(source = source, modifier = Modifier.size(32.dp))
                                },
                                onClick = { onClickSource(source) },
                                trailing = {
                                    if (sourceEnabled) TsuzukiCheckMark() else TsuzukiMinusMark()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
