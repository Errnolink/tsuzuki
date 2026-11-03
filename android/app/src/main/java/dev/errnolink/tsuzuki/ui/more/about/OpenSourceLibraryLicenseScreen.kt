package dev.errnolink.tsuzuki.ui.more.about

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.textview.MaterialTextView
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class TsuzukiOpenSourceLibraryLicenseScreen(
    private val name: String,
    private val website: String?,
    private val license: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        SettingsScaffold(
            title = name,
            navigateUp = { navigator.pop() },
            actions = if (!website.isNullOrEmpty()) {
                listOf(ShellTopAction(stringResource(MR.strings.website), TsuzukiIcons.Globe, { uriHandler.openUri(website) }))
            } else {
                emptyList()
            },
        ) {
            item(key = "license-text") {
                Column(Modifier.padding(horizontal = TsuzukiSpacing.gutter)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TsuzukiCorners.group))
                            .background(TsuzukiTheme.colors.grouped)
                            .padding(16.dp),
                    ) {
                        HtmlLicenseText(html = license)
                    }
                }
            }
        }
    }

    @Composable
    private fun HtmlLicenseText(html: String) {
        val textColor = TsuzukiTheme.colors.text.toArgb()
        AndroidView(
            factory = { context ->
                MaterialTextView(context).apply {
                    typeface = Typeface.MONOSPACE
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                }
            },
            update = {
                it.setTextColor(textColor)
                it.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            },
        )
    }
}
