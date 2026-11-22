package dev.errnolink.tsuzuki.designsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by rememberSaveable { mutableStateOf(intent.getStringExtra("theme") == "dark") }
            var opaque by rememberSaveable { mutableStateOf(intent.getBooleanExtra("opaque", false)) }
            var reduced by rememberSaveable { mutableStateOf(intent.getBooleanExtra("reducedMotion", false)) }
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            TsuzukiTheme(dark = dark, reducedTransparency = opaque, reducedMotion = reduced) {
                Gallery(dark, { dark = it }, opaque, { opaque = it }, reduced, { reduced = it })
            }
        }
    }
}

@Composable
private fun Gallery(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    opaque: Boolean,
    onOpaque: (Boolean) -> Unit,
    reduced: Boolean,
    onReduced: (Boolean) -> Unit,
) {
    val glass = rememberGlassContext()
    val arrivals = rememberCoverArrivalState()
    val toasts = remember { ToastHostState() }
    val galleryScope = rememberCoroutineScope()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var downloaded by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>("Reading position saved") }
    var switchOn by rememberSaveable { mutableStateOf(true) }
    var switchOff by rememberSaveable { mutableStateOf(false) }
    var pairSegment by rememberSaveable { mutableIntStateOf(0) }
    var fourSegment by rememberSaveable { mutableIntStateOf(0) }
    var sliderValue by rememberSaveable { mutableIntStateOf(40) }
    var steppedValue by rememberSaveable { mutableIntStateOf(25) }
    var filledQuery by rememberSaveable { mutableStateOf("Papers, please") }
    var inlineQuery by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    var filterChip by rememberSaveable { mutableIntStateOf(0) }
    var multiChips by rememberSaveable { mutableStateOf(listOf(true, false, false)) }
    var tagChips by rememberSaveable { mutableStateOf(listOf("Mina", "Slice of life")) }
    var triState by rememberSaveable { mutableStateOf(ChipTriState.Include) }
    var checkOn by rememberSaveable { mutableStateOf(true) }
    var checkOff by rememberSaveable { mutableStateOf(false) }
    var checkTri by rememberSaveable { mutableStateOf(false) }
    var radioPick by rememberSaveable { mutableIntStateOf(0) }
    var stepValue by rememberSaveable { mutableIntStateOf(24) }
    var singleGroup by rememberSaveable { mutableIntStateOf(0) }
    var multiGroup by rememberSaveable { mutableStateOf(listOf(true, false, true)) }
    var roundToggle by rememberSaveable { mutableStateOf(false) }
    var squareToggle by rememberSaveable { mutableStateOf(true) }
    var downloadBusy by rememberSaveable { mutableStateOf(false) }
    var downloadResult by rememberSaveable { mutableStateOf(LoadingButtonResult.None) }
    var syncBusy by rememberSaveable { mutableStateOf(false) }
    var syncResult by rememberSaveable { mutableStateOf(LoadingButtonResult.None) }
    val demoScope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Design gallery",
            glassContext = glass,
            bottomBar = null,
        ) {
        item {
            GallerySection("Made for the story") {
                TsuzukiText("Original Apple-inspired controls. Inter typography, quiet content and one floating functional layer.", color = TsuzukiTheme.colors.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { index ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoverImage(
                                "gallery-cover-$index", listOf(0.67f, 0.78f, 0.7f)[index], loaded = true, arrivals = arrivals,
                                onClick = { notice = "Cover ${index + 1} opened" }, label = "Original sample cover ${index + 1}",
                            ) { CoverArtwork(index) }
                            TsuzukiText(listOf("A quiet place", "The long way", "After hours")[index], style = TsuzukiTheme.typography.footnote)
                        }
                    }
                }
            }
        }
        item {
            GallerySection("Search & selection") {
                SearchField(query, { query = it }, { keyboard?.hide(); notice = "Search submitted: $query" }, Modifier.fillMaxWidth(), placeholder = "Title or MangaDex link")
                SegmentedControl(listOf("All", "Reading", "Finished"), segment, { segment = it }, Modifier.fillMaxWidth())
            }
        }
        item {
            GallerySection("Controls from Kasane") {
                InsetGroupedList(title = "Switch", footer = "Held, the thumb stretches into a glass lens; drag it to toggle.") {
                    GroupedRow("With check icon", checked = switchOn, onCheckedChange = { switchOn = it }, thumbIcon = true, divider = false)
                    GroupedRow("Off", checked = switchOff, onCheckedChange = { switchOff = it }, divider = false)
                    GroupedRow("Disabled", checked = true, onCheckedChange = {}, enabled = false, divider = false)
                }
                SliderRow("Continuous slider", sliderValue, sliderValue.toString(), 0..100, true, { sliderValue = it }, Modifier.fillMaxWidth(), startLabel = "0", endLabel = "100")
                SliderRow("Stepped slider", steppedValue, "$steppedValue%", 0..100 step 25, true, { steppedValue = it }, Modifier.fillMaxWidth(), startLabel = "0%", endLabel = "100%")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TsuzukiText("Segmented", style = TsuzukiTheme.typography.title3)
                    SegmentedControl(listOf("On", "Off"), pairSegment, { pairSegment = it }, Modifier.fillMaxWidth())
                    SegmentedControl(listOf("All", "Reading", "Finished"), segment, { segment = it }, Modifier.fillMaxWidth())
                    SegmentedControl(listOf("Chapters", "Similar", "Comments", "More"), fourSegment, { fourSegment = it }, Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TsuzukiText("Search", style = TsuzukiTheme.typography.title3)
                    SearchField(query, { query = it }, { keyboard?.hide() }, Modifier.fillMaxWidth(), placeholder = "Empty field")
                    SearchField(filledQuery, { filledQuery = it }, { keyboard?.hide() }, Modifier.fillMaxWidth(), placeholder = "Filled field")
                    SearchField(
                        inlineQuery, { inlineQuery = it }, { keyboard?.hide() }, Modifier.fillMaxWidth(),
                        placeholder = "Inline search", inline = true, onCancel = { notice = "Inline search cancelled" },
                    )
                }
            }
        }
        item {
            GallerySection("Actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Continue reading", { notice = "Continue selected" })
                    PillButton("More options", { sheet = true }, prominent = false)
                    PillButton("Unavailable", {}, enabled = false)
                    IconButton("Open reading settings", { sheet = true }) { GalleryIcon(3, false) }
                }
                GlassExample { notice = "Glass action selected" }
            }
        }
        item {
            GallerySection("Kasane buttons") {
                TsuzukiText("Size ladder, XS to XL", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsuzukiButton("Extra small", { notice = "XS pressed" }, size = TsuzukiButtonSize.Xs, icon = { GalleryIcon(4, false) })
                    TsuzukiButton("Small", { notice = "S pressed" }, size = TsuzukiButtonSize.Sm, icon = { GalleryIcon(4, false) })
                    TsuzukiButton("Medium", { notice = "M pressed" }, size = TsuzukiButtonSize.Md, icon = { GalleryIcon(4, false) })
                    TsuzukiButton("Large", { notice = "L pressed" }, size = TsuzukiButtonSize.Lg, icon = { GalleryIcon(4, false) })
                    TsuzukiButton("Hero", { notice = "XL pressed" }, size = TsuzukiButtonSize.Xl, icon = { GalleryIcon(4, false) })
                }
                TsuzukiText("Variants", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsuzukiButton("Filled", { notice = "Filled" }, variant = TsuzukiButtonVariant.Filled)
                    TsuzukiButton("Tonal", { notice = "Tonal" }, variant = TsuzukiButtonVariant.Tonal)
                    TsuzukiButton("Soft", { notice = "Soft" }, variant = TsuzukiButtonVariant.Soft)
                    TsuzukiButton("Container", { notice = "Container" }, variant = TsuzukiButtonVariant.Container)
                    TsuzukiButton("Outline", { notice = "Outline" }, variant = TsuzukiButtonVariant.Outline)
                    TsuzukiButton("Text", { notice = "Text" }, variant = TsuzukiButtonVariant.Text)
                    TsuzukiButton("Delete", { notice = "Danger" }, variant = TsuzukiButtonVariant.Danger)
                    TsuzukiButton("Disabled", {}, enabled = false)
                }
                TsuzukiText("Toggles — round goes square, square goes round", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsuzukiButton(
                        "Bookmark", { roundToggle = !roundToggle },
                        icon = { GalleryIcon(3, roundToggle) }, selected = roundToggle,
                    )
                    TsuzukiButton(
                        "Saved", { roundToggle = !roundToggle },
                        icon = { GalleryIcon(2, false) }, selected = !roundToggle,
                    )
                    TsuzukiButton(
                        "Like", { squareToggle = !squareToggle },
                        square = true, selected = squareToggle, icon = { GalleryIcon(1, squareToggle) },
                    )
                    TsuzukiButton(
                        "Muted", { squareToggle = !squareToggle },
                        square = true, selected = !squareToggle, icon = { GalleryIcon(0, false) },
                    )
                }
                TsuzukiText("Loading — label, spinner, check", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsuzukiButton(
                        "Download volume", {
                            demoScope.launch {
                                downloadBusy = true
                                downloadResult = LoadingButtonResult.None
                                delay(1200)
                                downloadBusy = false
                                downloadResult = LoadingButtonResult.Success
                                delay(1800)
                                downloadResult = LoadingButtonResult.None
                            }
                        },
                        busy = downloadBusy, result = downloadResult,
                        icon = { GalleryIcon(4, false) },
                    )
                    TsuzukiButton(
                        "Sync (fails)", {
                            demoScope.launch {
                                syncBusy = true
                                syncResult = LoadingButtonResult.None
                                delay(1000)
                                syncBusy = false
                                syncResult = LoadingButtonResult.Failure
                                delay(900)
                                syncResult = LoadingButtonResult.None
                            }
                        },
                        busy = syncBusy, result = syncResult,
                        variant = TsuzukiButtonVariant.Tonal,
                    )
                    TsuzukiButton("Busy, untappable", {}, busy = true, enabled = false)
                }
            }
        }
        item {
            GallerySection("Button groups") {
                TsuzukiText("Standard — press one, it grows", style = TsuzukiTheme.typography.title3)
                ButtonGroup {
                    item(onClick = { notice = "Save" }) {
                        GalleryIcon(3, false)
                        TsuzukiText("Save")
                    }
                    item(onClick = { notice = "Download" }) {
                        GalleryIcon(4, false)
                        TsuzukiText("Download")
                    }
                    item(onClick = { notice = "More" }) {
                        GalleryIcon(5, false)
                    }
                }
                TsuzukiText("Connected, single select", style = TsuzukiTheme.typography.title3)
                val modes = listOf("Paged", "Scroll", "Webtoon", "Spread")
                ButtonGroup(connected = true, height = 40.dp) {
                    modes.forEachIndexed { index, mode ->
                        item(onClick = { singleGroup = index }, selected = singleGroup == index) {
                            Row(Modifier.padding(horizontal = 14.dp)) {
                                TsuzukiText(mode, style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
                TsuzukiText("Connected, multi select", style = TsuzukiTheme.typography.title3)
                val filters = listOf("Unread", "Downloaded", "Finished")
                ButtonGroup(connected = true, height = 40.dp) {
                    filters.forEachIndexed { index, filter ->
                        item(
                            onClick = { multiGroup = multiGroup.toMutableList().also { it[index] = !it[index] } },
                            selected = multiGroup[index],
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp)) {
                                TsuzukiText(filter, style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
                TsuzukiText("Connected, fill the row", style = TsuzukiTheme.typography.title3)
                val fonts = listOf("Literata", "Source", "Legible")
                ButtonGroup(connected = true, fill = true, height = 40.dp) {
                    fonts.forEachIndexed { index, font ->
                        item(onClick = { singleGroup = index }, selected = singleGroup == index) {
                            TsuzukiText(font, style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        item {
            GallerySection("Chips") {
                TsuzukiText("Filter, single select", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Manga", "Novels").forEachIndexed { index, label ->
                        Chip(label, filterChip == index, { filterChip = index }, enabled = true)
                    }
                    Chip("Unavailable", false, {}, enabled = false)
                }
                TsuzukiText("Filter, multi select", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Unread", "Downloaded", "Finished").forEachIndexed { index, label ->
                        Chip(
                            label, multiChips[index],
                            { multiChips = multiChips.toMutableList().also { it[index] = !it[index] } },
                            role = Role.Checkbox,
                        )
                    }
                }
                TsuzukiText("Assist and suggestion", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Recently read", false, { notice = "Assist chip" }, tone = ChipTone.Neutral, leading = {
                        GalleryIcon(2, false)
                    })
                    Chip("Try “ramen”", false, { notice = "Suggestion chip" }, tone = ChipTone.Neutral)
                }
                TsuzukiText("Input — remove collapses the chip", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tagChips.forEach { tag ->
                        Chip(
                            tag, false, {},
                            role = Role.Button,
                            avatar = {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(TsuzukiTheme.colors.accent.copy(alpha = 0.24f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TsuzukiText(tag.take(1), style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold), color = TsuzukiTheme.colors.accent)
                                }
                            },
                            onRemove = {
                                notice = "Removed $tag"
                                tagChips = tagChips - tag
                            },
                        )
                    }
                    if (tagChips.isEmpty()) {
                        Chip("Restore tags", false, { tagChips = listOf("Mina", "Slice of life") }, tone = ChipTone.Neutral)
                    }
                }
                TsuzukiText("Tri-state", style = TsuzukiTheme.typography.title3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TriStateChip("Library", triState, {
                        triState = when (triState) {
                            ChipTriState.Off -> ChipTriState.Include
                            ChipTriState.Include -> ChipTriState.Exclude
                            ChipTriState.Exclude -> ChipTriState.Off
                        }
                    })
                }
            }
        }
        item {
            GallerySection("Checkbox & radio") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CheckboxRow("Mark earlier chapters as read", checkOn, { checkOn = it })
                    CheckboxRow("Skip duplicate chapters", checkOff, { checkOff = it })
                    CheckboxRow("Some volumes selected", checkTri, { checkTri = it }, indeterminate = true)
                    CheckboxRow("Disabled", true, {}, enabled = false)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Right to left", "Left to right", "Vertical").forEachIndexed { index, label ->
                        RadioRow(label, radioPick == index, { radioPick = index })
                    }
                    RadioRow("Disabled", false, {}, enabled = false)
                }
            }
        }
        item {
            GallerySection("Stepper") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper(
                        stepValue, { stepValue = it }, 0..64,
                        step = 4,
                        format = { "$it pt" },
                    )
                    TsuzukiText("Margins", style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper(1, {}, 1..1, enabled = false)
                    TsuzukiText("Disabled at the bounds", style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
                }
            }
        }
        item {
            InsetGroupedList(title = "Appearance", footer = "Opaque glass also follows power saving and high-contrast text. Reduced motion removes spatial feedback.") {
                GroupedRow("Dark appearance", checked = dark, onCheckedChange = onDark)
                GroupedRow("Reduce transparency", checked = opaque, onCheckedChange = onOpaque)
                GroupedRow("Reduce motion", checked = reduced, onCheckedChange = onReduced)
                GroupedRow("Reading settings", value = "Medium / Large", chevron = true, onClick = { sheet = true })
                GroupedRow("Download quality", subtitle = "Stored only on this device", value = "Original", divider = false)
            }
        }
        item {
            GallerySection("Context & progress") {
                ContextMenu(
                    label = "Sample chapter",
                    actions = listOf(
                        ContextMenuAction("Read chapter") { notice = "Read chapter selected" },
                        ContextMenuAction(if (downloaded) "Remove download" else "Download chapter") { downloaded = !downloaded },
                        ContextMenuAction("Chapter details") { sheet = true },
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsuzukiTheme.colors.grouped),
                    onClick = { notice = "Chapter opened; long-press for actions" },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProgressRing(if (downloaded) 1f else 0.64f, if (downloaded) "Downloaded" else "64 percent downloaded")
                        Column {
                            TsuzukiText("Chapter 12", style = TsuzukiTheme.typography.headline)
                            TsuzukiText("Long-press for chapter actions", style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
                        }
                    }
                }
                Banner("A connection could not be made. Your saved chapters are still available.", tone = NoticeTone.Error,
                    actionLabel = "Retry", onAction = { notice = "Retry selected" })
                LaunchedEffect(notice) {
                    notice?.let {
                        toasts.show(it)
                        notice = null
                    }
                }
            }
        }
        item {
            GallerySection("Feedback & progress") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingMark(Modifier.size(24.dp), size = 24.dp)
                    LoadingMark(Modifier.size(48.dp), size = 48.dp)
                    ContainedLoadingMark(Modifier.size(64.dp), size = 64.dp)
                }
                TsuzukiText(
                    "Wavy progress: flat at the ends, travelling while active; reduced motion flattens the wave.",
                    style = TsuzukiTheme.typography.footnote,
                    color = TsuzukiTheme.colors.secondary,
                )
                WavyProgress(0.38f, Modifier.fillMaxWidth())
                WavyProgress(null, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    WavyRing(0.64f, Modifier.size(48.dp))
                    WavyRing(null, Modifier.size(48.dp))
                    WavyRing(1f, Modifier.size(48.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Toast: saved", { toasts.show("Reading position saved") })
                    PillButton("Toast: success", {
                        toasts.show("Chapter 116 downloaded", ToastKind.Success, description = "4.1 MB, available offline")
                    })
                    PillButton("Toast: failure", { toasts.show("Couldn't reach the source", ToastKind.Error) })
                    PillButton("Toast: promise", {
                        val handle = toasts.show("Syncing library…", ToastKind.Loading)
                        galleryScope.launch {
                            delay(1600)
                            handle.update("Library synced", ToastKind.Success)
                        }
                    })
                    PillButton("Toast: action", {
                        toasts.show(
                            "Moved to Archive",
                            action = ToastAction("Undo") { toasts.show("Restored", ToastKind.Success) },
                        )
                    })
                    PillButton("Toast: stack", { repeat(3) { toasts.show("Chapter ${116 - it} downloaded") } })
                }
                SkeletonClock {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonCover(Modifier.width(96.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonTitle(Modifier.width(190.dp))
                            SkeletonText(Modifier.width(220.dp))
                            SkeletonText(Modifier.width(150.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SkeletonCircle(size = 24.dp)
                                SkeletonCard(Modifier.width(120.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            EmptyState("Your next story awaits", "Add a title to keep your reading in one place.",
                icon = { GalleryIcon(0, false, Modifier.size(40.dp)) },
                action = { PillButton("Browse titles", { notice = "Browse titles clicked" }) })
        }
        item {
            GallerySection("Swipe rows") {
                var swipedRead by rememberSaveable { mutableStateOf(false) }
                SwipeRow(
                    listOf(
                        SwipeActionSpec(
                            TsuzukiIcons.Check,
                            "Mark read",
                            TsuzukiTheme.colors.accent,
                            onClick = { swipedRead = !swipedRead },
                            full = true,
                        ),
                    ),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.group)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(TsuzukiTheme.colors.grouped).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            TsuzukiText("Kitsune Courier · Ch. 204", style = TsuzukiTheme.typography.headline)
                            TsuzukiText(
                                if (swipedRead) "Read — the row came home" else "Swipe left to mark read",
                                style = TsuzukiTheme.typography.footnote,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                    }
                }
                var pinnedRemoved by rememberSaveable { mutableStateOf(false) }
                SwipeRow(
                    listOf(
                        SwipeActionSpec(
                            TsuzukiIcons.Bookmark,
                            "Pin",
                            TsuzukiTheme.colors.accent,
                            onClick = { pinnedRemoved = true },
                        ),
                        SwipeActionSpec(
                            TsuzukiIcons.Trash2,
                            "Remove",
                            TsuzukiTheme.colors.destructive,
                            onClick = {
                                pinnedRemoved = false
                                toasts.show("Row removed (demo restores it)", ToastKind.Success)
                            },
                            full = true,
                            keep = false,
                        ),
                    ),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.group)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(TsuzukiTheme.colors.grouped).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            TsuzukiText("Moonlit Ramen Club · Ch. 118", style = TsuzukiTheme.typography.headline)
                            TsuzukiText(
                                if (pinnedRemoved) "Pinned" else "Two actions: pin, or a full swipe removes",
                                style = TsuzukiTheme.typography.footnote,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                    }
                }
            }
        }
        item {
            GallerySection("Type ramp") {
                val typography = TsuzukiTheme.typography
                listOf(
                    "Large Title · 34" to typography.largeTitle, "Title 1 · 28" to typography.title1,
                    "Title 2 · 22" to typography.title2, "Title 3 · 20" to typography.title3,
                    "Headline · 17" to typography.headline, "Body · 17" to typography.body,
                    "Callout · 16" to typography.callout, "Subhead · 15" to typography.subhead,
                    "Footnote · 13" to typography.footnote, "Caption 1 · 12" to typography.caption1,
                    "Caption 2 · 11" to typography.caption2,
                ).forEach { (name, style) -> TsuzukiText(name, style = style) }
            }
        }
        }
        ToastHost(
            toasts,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = 24.dp),
        )
    }
    DetentSheet(sheet, "Reading settings", { sheet = false }) {
        TsuzukiText("Drag or tap the grabber to resize. Close is on the leading edge.", color = TsuzukiTheme.colors.secondary)
        SegmentedControl(listOf("Paged", "Continuous"), segment.coerceAtMost(1), { segment = it }, Modifier.fillMaxWidth())
        GroupedRow("Keep downloaded", checked = downloaded, onCheckedChange = { downloaded = it })
        SearchField(query, { query = it }, { keyboard?.hide() }, Modifier.fillMaxWidth(), placeholder = "Find a chapter")
        repeat(12) { index ->
            GroupedRow("Chapter ${index + 1}", value = "Available", chevron = true, onClick = { notice = "Chapter ${index + 1} selected" })
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = TsuzukiSpacing.gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TsuzukiText(title, style = TsuzukiTheme.typography.title3)
        content()
    }
}

@Composable
private fun GlassExample(onClick: () -> Unit) {
    val context = rememberGlassContext(enableHeroLens = true)
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp))) {
        Box(Modifier.matchParentSize().glassSource(context)) { CoverArtwork(1) }
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(context, onClick, hero = true) {
                TsuzukiText("Hero glass", style = TsuzukiTheme.typography.headline)
            }
            GlassButton(context, onClick) {
                TsuzukiText("Regular glass", style = TsuzukiTheme.typography.headline)
            }
        }
    }
}

@Composable
private fun CoverArtwork(index: Int) {
    val paper = listOf(Color(0xFFC5D8CB), Color(0xFFDFB590), Color(0xFFC3CEE2))[index]
    val ink = listOf(Color(0xFF30574C), Color(0xFF583E32), Color(0xFF394765))[index]
    Canvas(Modifier.fillMaxSize().background(paper)) {
        drawCircle(ink.copy(alpha = 0.25f), size.minDimension * 0.58f, Offset(size.width * 0.84f, size.height * 0.22f))
        drawRect(ink, Offset(size.width * 0.15f, size.height * 0.32f), Size(size.width * 0.17f, size.height * 0.7f))
        drawRect(ink.copy(alpha = 0.8f), Offset(size.width * 0.45f, size.height * 0.16f), Size(size.width * 0.14f, size.height * 0.84f))
        drawLine(paper, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.35f), size.width * 0.08f)
    }
}

@Composable
private fun GalleryIcon(kind: Int, selected: Boolean, modifier: Modifier = Modifier.size(22.dp)) {
    val color = LocalTsuzukiContentColor.current
    Canvas(modifier) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val line = Stroke(1.8f, cap = StrokeCap.Round)
            when (kind) {
                0 -> {
                    drawRoundRect(color, Offset(3f, 4f), Size(7f, 16f), CornerRadius(1f), style = if (selected) Fill else line)
                    drawRoundRect(color, Offset(14f, 4f), Size(7f, 16f), CornerRadius(1f), style = if (selected) Fill else line)
                }
                1 -> {
                    drawCircle(color, 7f, Offset(10f, 10f), style = if (selected) Fill else line)
                    drawLine(color, Offset(16f, 16f), Offset(22f, 22f), 2f, StrokeCap.Round)
                }
                2 -> {
                    drawLine(color, Offset(12f, 3f), Offset(12f, 15f), if (selected) 3f else 1.8f, StrokeCap.Round)
                    val arrow = Path().apply { moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f) }
                    drawPath(arrow, color, style = line)
                    val tray = Path().apply { moveTo(3f, 16f); lineTo(3f, 21f); lineTo(21f, 21f); lineTo(21f, 16f) }
                    drawPath(tray, color, style = line)
                }
                3 -> {
                    repeat(3) { row ->
                        val y = 5f + row * 7f
                        val x = if (row == 1) 15f else 8f
                        drawLine(color, Offset(2f, y), Offset(22f, y), 1.8f, StrokeCap.Round)
                        drawCircle(color, if (selected) 3.2f else 2.3f, Offset(x, y), style = if (selected) Fill else line)
                    }
                }
                4 -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = if (selected) Fill else line)
                    if (!selected) repeat(4) { ray ->
                        val a = ray * Math.PI / 2
                        val direction = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        drawLine(color, Offset(12f, 12f) + direction * 10f, Offset(12f, 12f) + direction * 12f, 1.6f)
                    }
                }
                else -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = line)
                    drawLine(color, Offset(12f, 6f), Offset(12f, 12f), if (selected) 3f else 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(12f, 12f), Offset(17f, 15f), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}
