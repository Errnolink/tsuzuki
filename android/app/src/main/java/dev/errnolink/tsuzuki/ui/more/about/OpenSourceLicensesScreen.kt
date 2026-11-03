package dev.errnolink.tsuzuki.ui.more.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLibraryLicenseScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class TsuzukiOpenSourceLicensesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val libraries by produceLibraries(R.raw.aboutlibraries)
        val libs = libraries?.libraries.orEmpty()
        SettingsScaffold(
            title = stringResource(MR.strings.licenses),
            navigateUp = { navigator.pop() },
            itemSpacing = 0.dp,
        ) {
            items(
                count = libs.size,
                key = { index -> libs[index].uniqueId },
                contentType = { "license-library" },
            ) { index ->
                val library = libs[index]
                val shape = remember(index, libs.size) {
                    RoundedCornerShape(
                        topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                        topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                        bottomStart = if (index == libs.lastIndex) TsuzukiCorners.group else 0.dp,
                        bottomEnd = if (index == libs.lastIndex) TsuzukiCorners.group else 0.dp,
                    )
                }
                Column(
                    Modifier
                        .padding(horizontal = TsuzukiSpacing.gutter)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(TsuzukiTheme.colors.grouped),
                ) {
                    if (index > 0) GroupDivider()
                    GroupedRow(
                        title = library.name,
                        value = library.artifactVersion,
                        chevron = true,
                        divider = false,
                        onClick = {
                            navigator.push(
                                OpenSourceLibraryLicenseScreen(
                                    name = library.name,
                                    website = library.website,
                                    license = library.licenses.firstOrNull()?.htmlReadyLicenseContent.orEmpty(),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
