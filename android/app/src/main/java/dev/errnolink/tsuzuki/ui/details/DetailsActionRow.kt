package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.Link
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import kotlin.math.roundToInt

private val CircleSize = 48.dp
private val EdgeInset = 20.dp
private val ActionGap = 10.dp
private val CapsuleHeight = 50.dp
private val ContinueIconSize = 20.dp
private val ContinuePad = 18.dp
private val ContinueIconGap = 8.dp

private enum class RowPanel { Tracking }

@Composable
fun DetailsActionRow(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    glass: GlassContext?,
    continueLabel: String,
    continueEnabled: Boolean,
    actionColor: Color,
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
    onNotesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var panel by remember { mutableStateOf<RowPanel?>(null) }
    var trackingSheetVisible by remember { mutableStateOf(false) }
    var trackingAnchor by remember { mutableStateOf(Offset.Zero) }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val trackingState = rememberTrackingPanelState(mangaId)

    Box(modifier.fillMaxWidth().onGloballyPositioned { rowOrigin = it.boundsInWindow().topLeft }) {
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
                LabeledAction(if (inLibrary) "In library" else "Add", Modifier.weight(1f)) {
                    GrowPulseCircle(glass, if (inLibrary) TsuzukiIcons.BookmarkFilled else TsuzukiIcons.Bookmark, "Library", active = inLibrary) {
                        onLibraryClick()
                    }
                }
                LabeledAction(if (trackingCount > 0) "Tracked" else "Tracking", Modifier.weight(1f)) {
                    GrowPulseCircle(
                        glass,
                        TsuzukiIcons.RefreshCw,
                        "Tracking",
                        active = trackingCount > 0,
                        checked = trackingCount > 0,
                        onPositioned = { trackingAnchor = it },
                    ) {
                        if (!hasLoggedInTrackers) onOpenTrackingSettings() else panel = RowPanel.Tracking
                    }
                }
                LabeledAction("Merge", Modifier.weight(1f)) {
                    GrowPulseCircle(glass, TsuzukiIcons.Link, "Merge", active = mergedActive, onClick = onMergeClick)
                }
                if (coversAvailable && onCoversClick != null) {
                    LabeledAction("Covers", Modifier.weight(1f)) {
                        GrowPulseCircle(glass, TsuzukiIcons.Images, "Covers", onClick = onCoversClick)
                    }
                }
                LabeledAction("Notes", Modifier.weight(1f)) {
                    GrowPulseCircle(glass, TsuzukiIcons.BookOpen, "Notes", onClick = onNotesClick)
                }
            }
        }
        TrackingGrowPanel(
            visible = panel == RowPanel.Tracking,
            anchor = trackingAnchor - rowOrigin,
            trackingState = trackingState,
            glass = glass,
            onOpenTracker = { panel = null; trackingSheetVisible = true },
            onAddTracker = { panel = null; trackingSheetVisible = true },
            onDismiss = { panel = null },
        )
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
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (enabled) color else TsuzukiTheme.colors.selectedFill
    val labelColor = if (enabled) Color.White else TsuzukiTheme.colors.secondary
    Box(
        modifier
            .height(CapsuleHeight)
            .clip(RoundedCornerShape(CapsuleHeight / 2))
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = ContinuePad),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ContinueIconGap),
        ) {
            Icon(TsuzukiIcons.Play, null, Modifier.size(ContinueIconSize), tint = labelColor)
            TsuzukiText(
                label,
                style = TsuzukiTheme.typography.headline,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 44dp glass circle with a press "grow" pulse used when the tap opens a sheet/screen. */
@Composable
private fun GrowPulseCircle(
    glass: GlassContext?,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    checked: Boolean = false,
    onPositioned: ((Offset) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    var pulsing by remember { mutableStateOf(false) }
    val motion = TsuzukiTheme.motion
    val pulse by animateFloatAsState(
        if (pulsing) 1.14f else 1f,
        if (motion.standardTokens) androidx.compose.animation.core.tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short3, easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard) else motion.tabSpring(),
        label = "Row pulse",
    )
    LaunchedEffect(pulsing) {
        if (pulsing) {
            kotlinx.coroutines.delay(140)
            pulsing = false
        }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .onGloballyPositioned { onPositioned?.invoke(it.boundsInWindow().topLeft) }
            .size(CircleSize),
    ) {
        GlassSurface(
            glass,
            Modifier.fillMaxSize(),
            shape = CircleShape,
            material = GlassMaterial.Thick,
        ) {
            Box(
                Modifier.fillMaxSize()
                    .clickable(role = Role.Button) { pulsing = true; onClick() }
                    .semantics { contentDescription = if (checked) "$label, on" else label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.size(20.dp),
                    tint = if (active) colors.accent else colors.text,
                )
            }
        }
        if (checked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Check, null, Modifier.size(8.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun TrackingGrowPanel(
    visible: Boolean,
    anchor: Offset,
    trackingState: TrackingPanelState,
    glass: GlassContext?,
    onOpenTracker: (eu.kanade.tachiyomi.ui.manga.track.TrackItem) -> Unit,
    onAddTracker: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = TsuzukiTheme.motion
    var shown by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        if (motion.reduced) snap() else motion.tabSpring(),
        label = "Tracking panel grow",
    )
    LaunchedEffect(progress, visible) {
        shown = visible || progress > 0.01f
    }
    if (!shown) return
    val density = LocalDensity.current
    val panelWidth = with(density) { 320.dp.toPx().coerceAtMost(LocalConfiguration.current.screenWidthDp.dp.toPx() - 32.dp.toPx()).toDp() }
    val panelLeftPx = with(density) { (anchor.x - 40.dp.toPx()).coerceAtLeast(16.dp.toPx()) }
    val pivotX = with(density) { ((anchor.x - panelLeftPx) / panelWidth.toPx()).coerceIn(0f, 1f) }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(panelLeftPx.roundToInt(), with(density) { (anchor.y + 10.dp.toPx()).roundToInt() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .width(panelWidth)
                .graphicsLayer {
                    scaleX = progress
                    scaleY = progress
                    alpha = progress.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivotX, 0f)
                },
        ) {
            GlassSurface(glass, Modifier, shape = RoundedCornerShape(TsuzukiCorners.group), material = GlassMaterial.Thick) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TsuzukiText("Tracking", Modifier.weight(1f), TsuzukiTheme.typography.headline)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            TsuzukiText("Done", style = TsuzukiTheme.typography.subhead, color = TsuzukiTheme.colors.accent)
                        }
                    }
                    TrackingPanel(
                        state = trackingState,
                        onOpenTracker = onOpenTracker,
                        onAddTracker = onAddTracker,
                    )
                }
            }
        }
    }
}
