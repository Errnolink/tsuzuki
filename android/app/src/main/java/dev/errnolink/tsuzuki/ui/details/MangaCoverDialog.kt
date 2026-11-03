package dev.errnolink.tsuzuki.ui.details

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updatePadding
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.LocalInsideGlass
import dev.errnolink.tsuzuki.designsystem.Pencil
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import eu.kanade.presentation.manga.EditCoverAction
import dev.errnolink.tsuzuki.ui.shell.ShellSnackbar
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import tachiyomi.domain.manga.model.Manga

@Composable
fun MangaCoverDialog(
    manga: Manga,
    isCustomCover: Boolean,
    snackbarHostState: SnackbarHostState,
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
        val view = remember(context) {
            ReaderPageImageView(context).apply { clipToPadding = false; clipChildren = false }
        }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp
        val topPx = with(LocalDensity.current) { top.roundToPx() }
        val bottomPx = with(LocalDensity.current) { bottom.roundToPx() }
        DisposableEffect(view, manga) {
            val isMd = manga.thumbnailUrl?.let { exh.md.utils.MdCoverVariantUtil.isMangaDexCover(it) } == true
            val smallUrl = if (isMd && !isCustomCover) manga.thumbnailUrl?.let { exh.md.utils.MdCoverVariantUtil.getVariantUrl(it, exh.md.utils.MangaDexCoverVariant.MEDIUM) } else null
            var originalArrived = false
            val placeholderLoad = if (smallUrl != null) {
                val placeholderReq = ImageRequest.Builder(context)
                    .data(smallUrl)
                    .target { image ->
                        if (!originalArrived) {
                            val drawable = image.asDrawable(context.resources)
                            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                            val config = bitmap?.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
                            val owned = bitmap?.copy(config, false)?.toDrawable(context.resources) ?: drawable
                            view.setImage(owned, ReaderPageImageView.Config(zoomDuration = 0))
                        }
                    }
                    .build()
                context.imageLoader.enqueue(placeholderReq)
            } else null

            val request = ImageRequest.Builder(context)
                .data(manga).size(Size.ORIGINAL).memoryCachePolicy(CachePolicy.DISABLED)
                .target { image ->
                    originalArrived = true
                    val drawable = image.asDrawable(context.resources)
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    val config = bitmap?.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
                    val owned = bitmap?.copy(config, false)?.toDrawable(context.resources) ?: drawable
                    view.setImage(owned, ReaderPageImageView.Config(zoomDuration = 0))
                }.build()
            val load = context.imageLoader.enqueue(request)
            onDispose {
                placeholderLoad?.dispose()
                load.dispose()
            }
        }
        Box(modifier.fillMaxSize().background(TsuzukiTheme.colors.background)) {
            AndroidView(
                factory = { view },
                update = { it.updatePadding(top = topPx, bottom = bottomPx); it.onViewClicked = onDismissRequest },
                modifier = Modifier.fillMaxSize().glassSource(glass),
            )
            DetailGlassGroup(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), context = glass) {
                DetailIcon("Close cover", TsuzukiIcons.X, onDismissRequest)
                TsuzukiText("Cover", Modifier.weight(1f).padding(horizontal = 12.dp), TsuzukiTheme.typography.headline)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onEditClick == null || !manga.favorite) {
                    GlassSurface(glass, shape = RoundedCornerShape(50)) {
                        TsuzukiText(
                            "Add to library to edit cover",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            TsuzukiTheme.typography.caption1,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                }
                DetailGlassGroup(Modifier.fillMaxWidth(), context = glass) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailLabeledAction("Share", TsuzukiIcons.Share, onShareClick)
                        DetailLabeledAction("Save", TsuzukiIcons.Download, onSaveClick)
                        if (onEditClick != null && manga.favorite) {
                            Box {
                                DetailLabeledAction("Edit", TsuzukiIcons.Pencil, { editMenu = true })
                                if (editMenu) {
                                    GlassMenu(
                                        expanded = true,
                                        label = "Cover",
                                        actions = buildList {
                                            add(ContextMenuAction("Choose image", icon = { Icon(TsuzukiIcons.Images, null, tint = TsuzukiTheme.colors.text) }) { onEditClick(EditCoverAction.EDIT) })
                                            if (isCustomCover) {
                                                add(
                                                    ContextMenuAction(
                                                        "Reset custom cover",
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
            ShellSnackbar(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
        }
        }
    }
}

