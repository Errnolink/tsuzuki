package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.LocalInsideGlass
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.ui.reader.viewer.ReaderPageImageSource
import eu.kanade.presentation.manga.EditCoverAction
import dev.errnolink.tsuzuki.ui.shell.ShellToasts
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.manga.model.Manga

@Composable
fun MangaCoverDialog(
    manga: Manga,
    isCustomCover: Boolean,
    toasts: ToastHostState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editMenu by remember { mutableStateOf(false) }
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalInsideGlass provides false) {
        val context = LocalContext.current
        val glass = rememberGlassContext()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp
        val request = remember(manga, isCustomCover) {
            ImageRequest.Builder(context)
                .data(manga)
                .size(Size.ORIGINAL)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .crossfade(true)
                .build()
        }
        val imageSource = remember(request) {
            ReaderPageImageSource(request, context.imageLoader)
        }
        val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
        val imageState = rememberZoomableImageState(zoomableState)
        Box(modifier.fillMaxSize().background(TsuzukiTheme.colors.background)) {
            ZoomableImage(
                image = imageSource,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                state = imageState,
                onClick = { onDismissRequest() },
                modifier = Modifier.fillMaxSize().padding(top = top, bottom = bottom).glassSource(glass),
            )
            DetailGlassGroup(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), context = glass) {
                DetailIcon(stringResource(MR.strings.tsuzuki_close_cover), TsuzukiIcons.X, onDismissRequest)
                TsuzukiText(stringResource(MR.strings.tsuzuki_cover), Modifier.weight(1f).padding(horizontal = 12.dp), TsuzukiTheme.typography.headline)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onEditClick == null || !manga.favorite) {
                    GlassSurface(glass, shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)) {
                        TsuzukiText(
                            stringResource(MR.strings.tsuzuki_add_to_library_to_edit_cover),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            TsuzukiTheme.typography.caption1,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                }
                DetailGlassGroup(Modifier.fillMaxWidth(), context = glass) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailLabeledAction(stringResource(MR.strings.action_share), TsuzukiIcons.Share, onShareClick)
                        DetailLabeledAction(stringResource(MR.strings.action_save), TsuzukiIcons.Download, onSaveClick)
                        if (onEditClick != null && manga.favorite) {
                            Box {
                                DetailLabeledAction(stringResource(MR.strings.action_edit), TsuzukiIcons.Pencil, { editMenu = true })
                                if (editMenu) {
                                    GlassMenu(
                                        expanded = true,
                                        label = stringResource(MR.strings.tsuzuki_cover),
                                        actions = buildList {
                                            add(ContextMenuAction(stringResource(MR.strings.tsuzuki_choose_image), icon = { Icon(TsuzukiIcons.Images, null, tint = TsuzukiTheme.colors.text) }) { onEditClick(EditCoverAction.EDIT) })
                                            if (isCustomCover) {
                                                add(
                                                    ContextMenuAction(
                                                        stringResource(MR.strings.tsuzuki_reset_custom_cover),
                                                        icon = { Icon(TsuzukiIcons.Trash2, null, tint = TsuzukiTheme.colors.destructive) },
                                                        destructive = true,
                                                    ) { onEditClick(EditCoverAction.DELETE) },
                                                )
                                            }
                                        },
                                        onDismissRequest = { editMenu = false },
                                    )
                                }
                            }
                    }
                }
            }
            }
            ShellToasts(toasts, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
        }
        }
    }
}

