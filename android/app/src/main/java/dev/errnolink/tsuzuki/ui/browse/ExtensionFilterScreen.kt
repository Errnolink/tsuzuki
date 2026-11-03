package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExtensionFilterContent(
    navigateUp: () -> Unit,
    state: ExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(
        title = stringResource(MR.strings.label_extensions),
        navigateUp = navigateUp,
    ) {
        if (state.isEmpty) {
            item(key = "extension-filter-empty") {
                ShellEmpty("Nothing to filter", "Install an extension first, then pick its languages here.")
            }
            return@SettingsScaffold
        }
        item(key = "extension-filter-languages") {
            InsetGroupedList {
                state.languages.forEach { language ->
                    val iconResId = getLanguageIconID(language) ?: R.drawable.globe
                    GroupedRow(
                        title = LocaleHelper.getSourceDisplayName(language, context) +
                            (
                                " (${LocaleHelper.getDisplayName(language)})"
                                    .takeIf { language !in listOf("all", "other") } ?: ""
                            ),
                        leading = {
                            Icon(
                                painter = painterResource(id = iconResId),
                                tint = Color.Unspecified,
                                contentDescription = language,
                                modifier = Modifier.size(width = 36.dp, height = 24.dp),
                            )
                        },
                        checked = language in state.enabledLanguages,
                        onCheckedChange = { onClickToggle(language) },
                    )
                }
            }
        }
    }
}
