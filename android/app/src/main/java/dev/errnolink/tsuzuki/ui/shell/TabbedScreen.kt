package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import eu.kanade.presentation.components.AppBar
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val searchPlaceholder: String? = null,
    val searchQuery: (() -> String?)? = null,
    val onSearchQueryChange: ((String?) -> Unit)? = null,
    val searchTrailing: (@Composable RowScope.() -> Unit)? = null,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, toasts: ToastHostState) -> Unit,
)

internal fun selectionSwapTransform(reduced: Boolean): ContentTransform =
    if (reduced) {
        ContentTransform(
            fadeIn(M3Motion.reducedFade()),
            fadeOut(M3Motion.reducedFade()),
            0f,
            SizeTransform(clip = false),
        )
    } else {
        ContentTransform(
            fadeIn(tween(130, delayMillis = 70, easing = M3Motion.EmphasizedDecelerate)),
            fadeOut(tween(70, easing = M3Motion.EmphasizedAccelerate)),
            0f,
            SizeTransform(clip = false),
        )
    }
