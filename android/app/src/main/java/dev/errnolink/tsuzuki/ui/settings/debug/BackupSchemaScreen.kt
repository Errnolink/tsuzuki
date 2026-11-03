package dev.errnolink.tsuzuki.ui.settings.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class TsuzukiBackupSchemaScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val schema = remember { ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor) }
        SettingsScaffold(
            title = TITLE,
            navigateUp = { navigator.pop() },
            itemSpacing = 0.dp,
            actions = listOf(
                ShellTopAction(
                    stringResource(MR.strings.action_copy_to_clipboard),
                    TsuzukiIcons.Copy,
                    { context.copyToClipboard(TITLE, schema) },
                ),
            ),
        ) {
            item(key = "backup-schema") {
                Column(Modifier.padding(horizontal = TsuzukiSpacing.gutter)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TsuzukiCorners.group))
                            .background(TsuzukiTheme.colors.grouped),
                    ) {
                        TsuzukiText(
                            schema,
                            Modifier
                                .padding(16.dp)
                                .horizontalScroll(rememberScrollState()),
                            TsuzukiTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace),
                            TsuzukiTheme.colors.text,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val TITLE = "Backup file schema"
    }
}
