package dev.errnolink.tsuzuki.ui.details

import dev.errnolink.tsuzuki.reader.ReaderOrigins
import dev.errnolink.tsuzuki.reader.readerOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.LiquidCard
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.Link
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Tag
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.rememberMenuMorphSource
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal val EdgeInset = 20.dp
internal val ActionGap = 10.dp
internal val CapsuleHeight = 50.dp
private val ContinueIconSize = 20.dp
private val ContinuePad = 18.dp
private val ContinueIconGap = 8.dp

@Composable
fun DetailsActionRow(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    glass: GlassContext?,
    continueLabel: String?,
    continueEnabled: Boolean,
    actionColor: () -> Color,
    onContinueClick: () -> Unit,
    inLibrary: Boolean,
    onLibraryClick: () -> Unit,
    hasLoggedInTrackers: Boolean,
    onOpenTrackingSettings: () -> Unit,
    trackingCount: Int,
    mergedActive: Boolean,
    onMergeClick: () -> Unit,
    coversAvailable: Boolean,
    onCoversClick: (() -> Unit)?,
    notesEditor: @Composable (onDone: () -> Unit) -> Unit,
    onEditCategories: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onSaveCover: (() -> Unit)? = null,
    onShareCover: (() -> Unit)? = null,
    onSetCustomCover: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var libraryMenuOpen by remember { mutableStateOf(false) }
    var trackingMenuOpen by remember { mutableStateOf(false) }
    var coversMenuOpen by remember { mutableStateOf(false) }
    var trackingSheetVisible by remember { mutableStateOf(false) }
    val librarySource = rememberMenuMorphSource()
    val trackingSource = rememberMenuMorphSource()
    val coversSource = rememberMenuMorphSource()
    val notesSource = rememberMenuMorphSource()
    var notesOpen by remember { mutableStateOf(false) }
    val trackingState = rememberTrackingPanelState(mangaId)

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = EdgeInset),
            verticalArrangement = Arrangement.spacedBy(ActionGap),
        ) {
            ContinueCapsule(
                label = continueLabel,
                enabled = continueEnabled,
                color = actionColor,
                onClick = onContinueClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                val libraryActions = listOf(
                    onEditCategories?.let { PullDownAction(stringResource(MR.strings.action_edit_categories), icon = TsuzukiIcons.Tag, group = 0, onClick = it) },
                    onRemoveFromLibrary?.let {
                        PullDownAction(stringResource(MR.strings.remove_from_library), icon = TsuzukiIcons.Trash2, group = 1, destructive = true, onClick = it)
                    },
                ).filterNotNull()
                LabeledAction(
                    if (inLibrary) stringResource(MR.strings.tsuzuki_in_library) else stringResource(MR.strings.tsuzuki_add),
                    Modifier.weight(1f),
                ) {
                    Box(Modifier.menuMorphSource(librarySource)) {
                        ActionCircle(
                            glass,
                            if (inLibrary) TsuzukiIcons.BookmarkFilled else TsuzukiIcons.Bookmark,
                            stringResource(MR.strings.label_library),
                            active = inLibrary,
                            isToggle = true,
                            pulseOnClick = !inLibrary || libraryActions.isEmpty(),
                        ) {
                            when {
                                !inLibrary || libraryActions.isEmpty() -> onLibraryClick()
                                else -> libraryMenuOpen = true
                            }
                        }
                        if (inLibrary && libraryActions.isNotEmpty()) {
                            PullDownMenu(
                                expanded = libraryMenuOpen,
                                onDismissRequest = { libraryMenuOpen = false },
                                morphSource = librarySource,
                                paneTitle = stringResource(MR.strings.label_library),
                                actions = libraryActions,
                            )
                        }
                    }
                }
                LabeledAction(if (trackingCount > 0) stringResource(MR.strings.action_filter_tracked) else stringResource(MR.strings.manga_tracking_tab), Modifier.weight(1f)) {
                    Box(Modifier.menuMorphSource(trackingSource)) {
                        ActionCircle(
                            glass,
                            TsuzukiIcons.RefreshCw,
                            stringResource(MR.strings.manga_tracking_tab),
                            active = trackingCount > 0,
                            checked = trackingCount > 0,
                            isToggle = true,
                            pulseOnClick = !hasLoggedInTrackers,
                        ) {
                            if (!hasLoggedInTrackers) onOpenTrackingSettings() else trackingMenuOpen = true
                        }
                        if (hasLoggedInTrackers) {
                            PullDownMenu(
                                expanded = trackingMenuOpen,
                                onDismissRequest = { trackingMenuOpen = false },
                                morphSource = trackingSource,
                                width = 320.dp,
                                paneTitle = stringResource(MR.strings.manga_tracking_tab),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TsuzukiText(stringResource(MR.strings.manga_tracking_tab), Modifier.weight(1f), TsuzukiTheme.typography.headline)
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(TsuzukiCorners.field))
                                                .clickable(onClick = { trackingMenuOpen = false })
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            TsuzukiText(stringResource(MR.strings.tsuzuki_done), style = TsuzukiTheme.typography.subhead, color = TsuzukiTheme.colors.accent)
                                        }
                                    }
                                    TrackingPanel(
                                        state = trackingState,
                                        onOpenTracker = {
                                            trackingMenuOpen = false
                                            trackingSheetVisible = true
                                        },
                                        onAddTracker = {
                                            trackingMenuOpen = false
                                            trackingSheetVisible = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                LabeledAction(stringResource(MR.strings.tsuzuki_merge), Modifier.weight(1f)) {
                    ActionCircle(glass, TsuzukiIcons.Link, stringResource(MR.strings.tsuzuki_merge), active = mergedActive, onClick = onMergeClick)
                }
                if (coversAvailable && onCoversClick != null) {
                    val coverActions = listOf(
                        PullDownAction(stringResource(MR.strings.tsuzuki_cover_gallery), icon = TsuzukiIcons.Images, group = 0, onClick = onCoversClick),
                        onSaveCover?.let { PullDownAction(stringResource(MR.strings.action_save), icon = TsuzukiIcons.Download, group = 1, onClick = it) },
                        onShareCover?.let { PullDownAction(stringResource(MR.strings.action_share), icon = TsuzukiIcons.Share, group = 1, onClick = it) },
                        onSetCustomCover?.let { PullDownAction(stringResource(MR.strings.tsuzuki_choose_image), icon = TsuzukiIcons.Images, group = 1, onClick = it) },
                    ).filterNotNull()
                    LabeledAction(stringResource(MR.strings.tsuzuki_covers), Modifier.weight(1f)) {
                        Box(Modifier.menuMorphSource(coversSource)) {
                            ActionCircle(glass, TsuzukiIcons.Images, stringResource(MR.strings.tsuzuki_covers), pulseOnClick = false) { coversMenuOpen = true }
                            PullDownMenu(
                                expanded = coversMenuOpen,
                                onDismissRequest = { coversMenuOpen = false },
                                morphSource = coversSource,
                                paneTitle = stringResource(MR.strings.tsuzuki_covers),
                                actions = coverActions,
                            )
                        }
                    }
                }
                LabeledAction(stringResource(MR.strings.action_notes), Modifier.weight(1f)) {
                    Box(Modifier.menuMorphSource(notesSource)) {
                        ActionCircle(glass, TsuzukiIcons.BookOpen, stringResource(MR.strings.action_notes), pulseOnClick = false) { notesOpen = true }
                    }
                }
            }
        }
        LiquidCard(
            visible = notesOpen,
            source = notesSource,
            onDismissRequest = { notesOpen = false },
        ) {
            notesEditor { notesOpen = false }
        }
        TrackingSheet(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            sourceId = sourceId,
            visible = trackingSheetVisible,
            onDismissRequest = { trackingSheetVisible = false },
        )
    }
}

@Composable
private fun LabeledAction(label: String, modifier: Modifier = Modifier, circle: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        circle()
        TsuzukiText(
            label,
            style = TsuzukiTheme.typography.caption2,
            color = TsuzukiTheme.colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContinueCapsule(
    label: String?,
    enabled: Boolean,
    color: () -> Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabledFill = TsuzukiTheme.colors.selectedFill
    val labelColor = if (enabled) Color.White else TsuzukiTheme.colors.secondary
    val interaction = remember { MutableInteractionSource() }
    val pressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(
        modifier
            .readerOrigin()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .height(CapsuleHeight)
            .then(
                if (pressState.enabled) {
                    Modifier.pressShapeScale(pressState)
                } else {
                    Modifier.pressScale(interaction)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    ReaderOrigins.markActive(bounds)
                    onClick()
                },
            )
            .clip(pressState.shape)
            .drawBehind { drawRect(if (enabled) color() else disabledFill) }
            .semantics { label?.let { contentDescription = it } }
            .padding(horizontal = ContinuePad),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ContinueIconGap),
        ) {
            Icon(TsuzukiIcons.Play, null, Modifier.size(ContinueIconSize), tint = labelColor)
            val reduced = TsuzukiTheme.motion.reduced
            androidx.compose.animation.AnimatedContent(
                targetState = label,
                transitionSpec = {
                    if (reduced) {
                        // reduced motion is a short fade, never a hard cut
                        androidx.compose.animation.fadeIn(M3Motion.reducedFade())
                            .togetherWith(androidx.compose.animation.fadeOut(M3Motion.reducedFade()))
                    } else {
                        // DET-01: the old label is fully gone before the new one arrives — the
                        // previous cross-fade drew both at once (a double-exposed label). The
                        // capsule's fixed height keeps the row stable across the swap.
                        androidx.compose.animation.fadeIn(
                            androidx.compose.animation.core.tween(
                                M3Motion.Short3,
                                delayMillis = M3Motion.Short3,
                                easing = M3Motion.Standard,
                            ),
                        )
                            .togetherWith(
                                androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(
                                        M3Motion.Short3,
                                        easing = M3Motion.Standard,
                                    ),
                                ),
                            )
                    }
                },
                label = "continueLabel",
            ) { text ->
                if (text != null) {
                    TsuzukiText(
                        text,
                        style = TsuzukiTheme.typography.headline,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCircle(
    glass: GlassContext?,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    checked: Boolean = false,
    isToggle: Boolean = false,
    pulseOnClick: Boolean = true,
    onPositioned: ((Offset) -> Unit)? = null,
    onClick: () -> Unit,
) {
    GrowPulseCircle(
        glass = glass,
        icon = icon,
        label = label,
        modifier = modifier,
        active = active,
        checked = checked,
        isToggle = isToggle,
        pulseOnClick = pulseOnClick,
        onPositioned = onPositioned,
        onClick = onClick,
    )
}

