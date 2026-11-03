package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.more.statsSections
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsState()

        // SY -->
        val allRead by screenModel.allRead.collectAsState()
        val filterLabel = stringResource(
            if (allRead) {
                SYMR.strings.ignore_non_library_entries
            } else {
                SYMR.strings.include_all_read_entries
            },
        )
        SettingsScaffold(
            title = stringResource(MR.strings.label_stats),
            navigateUp = navigator::pop,
            actions = listOf(
                dev.errnolink.tsuzuki.ui.shell.ShellTopAction(
                    label = filterLabel,
                    icon = dev.errnolink.tsuzuki.designsystem.TsuzukiIcons.SlidersHorizontal,
                    active = allRead,
                    onClick = screenModel::toggleReadManga,
                ),
            ),
        ) {
            // SY <--
            when (val resolved = state) {
                is StatsScreenState.Loading -> {
                    item(key = "stats-loading") {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgressRing(0f, stringResource(MR.strings.loading))
                            TsuzukiText(stringResource(MR.strings.loading), color = TsuzukiTheme.colors.secondary)
                        }
                    }
                }
                is StatsScreenState.Success -> {
                    statsSections(resolved)
                }
            }
        }
    }
}
