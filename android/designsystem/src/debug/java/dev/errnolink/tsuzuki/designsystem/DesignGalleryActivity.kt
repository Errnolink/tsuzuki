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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp

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
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var downloaded by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>("Reading position saved") }
    val keyboard = LocalSoftwareKeyboardController.current
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
                notice?.let { Toast(it, onDismiss = { notice = null }) }
            }
        }
        item {
            EmptyState("Your next story awaits", "Add a title to keep your reading in one place.",
                icon = { GalleryIcon(0, false, Modifier.size(40.dp)) },
                action = { PillButton("Browse titles", { notice = "Browse titles clicked" }) })
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
