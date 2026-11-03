package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun lucideIcon(name: String, vararg paths: String, filled: Boolean = false, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        for (p in paths) {
            addPath(
                pathData = addPathNodes(p),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

val TsuzukiIcons.BookOpen: ImageVector by lazy {
    lucideIcon(
        "BookOpen",
        "M12 5v16",
        "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
    )
}

val TsuzukiIcons.LucideLibrary: ImageVector by lazy {
    lucideIcon(
        "LucideLibrary",
        "m16 6 4 14",
        "M12 6v14",
        "M8 8v12",
        "M4 4v16",
    )
}

val TsuzukiIcons.Bell: ImageVector by lazy {
    lucideIcon(
        "Bell",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
}

val TsuzukiIcons.HistoryLucide: ImageVector by lazy {
    lucideIcon(
        "HistoryLucide",
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    )
}

val TsuzukiIcons.Compass: ImageVector by lazy {
    lucideIcon(
        "Compass",
        "M 2.0 12.0 a 10.0 10.0 0 1 0 20.0 0 a 10.0 10.0 0 1 0 -20.0 0",
        "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
    )
}

val TsuzukiIcons.Ellipsis: ImageVector by lazy {
    lucideIcon(
        "Ellipsis",
        "M 11.0 12.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
        "M 18.0 12.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
        "M 4.0 12.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
    )
}

val TsuzukiIcons.Search: ImageVector by lazy {
    lucideIcon(
        "Search",
        "m21 21-4.34-4.34",
        "M 3.0 11.0 a 8.0 8.0 0 1 0 16.0 0 a 8.0 8.0 0 1 0 -16.0 0",
    )
}

val TsuzukiIcons.EllipsisVertical: ImageVector by lazy {
    lucideIcon(
        "EllipsisVertical",
        "M 11.0 12.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
        "M 11.0 5.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
        "M 11.0 19.0 a 1.0 1.0 0 1 0 2.0 0 a 1.0 1.0 0 1 0 -2.0 0",
    )
}

val TsuzukiIcons.SlidersHorizontal: ImageVector by lazy {
    lucideIcon(
        "SlidersHorizontal",
        "M10 5H3",
        "M12 19H3",
        "M14 3v4",
        "M16 17v4",
        "M21 12h-9",
        "M21 19h-5",
        "M21 5h-7",
        "M8 10v4",
        "M8 12H3",
    )
}

val TsuzukiIcons.ChevronDown: ImageVector by lazy {
    lucideIcon(
        "ChevronDown",
        "m6 9 6 6 6-6",
    )
}

val TsuzukiIcons.ChevronRight: ImageVector by lazy {
    lucideIcon(
        "ChevronRight",
        "m9 18 6-6-6-6",
        autoMirror = true,
    )
}

val TsuzukiIcons.X: ImageVector by lazy {
    lucideIcon(
        "X",
        "M18 6 6 18",
        "m6 6 12 12",
    )
}

val TsuzukiIcons.EyeOff: ImageVector by lazy {
    lucideIcon(
        "EyeOff",
        "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
        "M14.084 14.158a3 3 0 0 1-4.242-4.242",
        "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
        "m2 2 20 20",
    )
}

val TsuzukiIcons.SettingsLucide: ImageVector by lazy {
    lucideIcon(
        "SettingsLucide",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M 9.0 12.0 a 3.0 3.0 0 1 0 6.0 0 a 3.0 3.0 0 1 0 -6.0 0",
    )
}

val TsuzukiIcons.ChartLine: ImageVector by lazy {
    lucideIcon(
        "ChartLine",
        "M3 3v16a2 2 0 0 0 2 2h16",
        "m19 9-5 5-4-4-3 3",
    )
}

val TsuzukiIcons.TrendingUp: ImageVector by lazy {
    lucideIcon(
        "TrendingUp",
        "M16 7h6v6",
        "m22 7-8.5 8.5-5-5L2 17",
    )
}

val TsuzukiIcons.Info: ImageVector by lazy {
    lucideIcon(
        "Info",
        "M 2.0 12.0 a 10.0 10.0 0 1 0 20.0 0 a 10.0 10.0 0 1 0 -20.0 0",
        "M12 16v-4",
        "M12 8h.01",
    )
}

val TsuzukiIcons.Check: ImageVector by lazy {
    lucideIcon(
        "Check",
        "M20 6 9 17l-5-5",
    )
}
val TsuzukiIcons.ArrowUp: ImageVector by lazy {
    lucideIcon(
        "ArrowUp",
        "m5 12 7-7 7 7",
        "M12 19V5",
    )
}

val TsuzukiIcons.ArrowDown: ImageVector by lazy {
    lucideIcon(
        "ArrowDown",
        "m19 12-7 7-7-7",
        "M12 5v14",
    )
}

val TsuzukiIcons.ChevronsUpDown: ImageVector by lazy {
    lucideIcon(
        "ChevronsUpDown",
        "m7 15 5 5 5-5",
        "m7 9 5-5 5 5",
    )
}

val TsuzukiIcons.Download: ImageVector by lazy {
    lucideIcon(
        "Download",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
        "M12 15V3",
    )
}

val TsuzukiIcons.Link: ImageVector by lazy {
    lucideIcon(
        "Link",
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    )
}

val TsuzukiIcons.RotateCcw: ImageVector by lazy {
    lucideIcon(
        "RotateCcw",
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
    )
}

val TsuzukiIcons.Trash2: ImageVector by lazy {
    lucideIcon(
        "Trash2",
        "M3 6h18",
        "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
        "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        "M10 11v6",
        "M14 11v6",
    )
}

val TsuzukiIcons.RefreshCw: ImageVector by lazy {
    lucideIcon(
        "RefreshCw",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )
}

val TsuzukiIcons.Pencil: ImageVector by lazy {
    lucideIcon(
        "Pencil",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4",
    )
}

val TsuzukiIcons.Copy: ImageVector by lazy {
    lucideIcon(
        "Copy",
        "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )
}

val TsuzukiIcons.Calendar: ImageVector by lazy {
    lucideIcon(
        "Calendar",
        "M8 2v4",
        "M16 2v4",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M3 10h18",
    )
}

val TsuzukiIcons.Tag: ImageVector by lazy {
    lucideIcon(
        "Tag",
        "M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z",
        "M7 7h.01",
    )
}

val TsuzukiIcons.Folder: ImageVector by lazy {
    lucideIcon(
        "Folder",
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
    )
}

val TsuzukiIcons.Sparkles: ImageVector by lazy {
    lucideIcon(
        "Sparkles",
        "m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z",
    )
}

val TsuzukiIcons.Palette: ImageVector by lazy {
    lucideIcon(
        "Palette",
        "M12 2a10 10 0 0 0-10 10c0 4.42 3.58 8 8 8h1a2 2 0 0 0 2-2v-1a2 2 0 0 1 2-2h2a6 6 0 0 0 6-6c0-5.52-4.48-10-10-10z",
        "M8.5 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
        "M12.5 6.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
        "M16.5 8.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
    )
}

val TsuzukiIcons.Images: ImageVector by lazy {
    lucideIcon(
        "Images",
        "M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z",
        "M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z",
        "m21 15-5-5L5 21",
    )
}

val TsuzukiIcons.ArrowLeftRight: ImageVector by lazy {
    lucideIcon(
        "ArrowLeftRight",
        "m16 3 4 4-4 4",
        "M20 7H4",
        "m8 21-4-4 4-4",
        "M4 17h16",
    )
}

val TsuzukiIcons.Bookmark: ImageVector by lazy {
    lucideIcon("Bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
}

val TsuzukiIcons.BookmarkFilled: ImageVector by lazy {
    lucideIcon("BookmarkFilled", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z", filled = true)
}

val TsuzukiIcons.Crop: ImageVector by lazy {
    lucideIcon("Crop", "M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2")
}

val TsuzukiIcons.Plus: ImageVector by lazy {
    lucideIcon("Plus", "M5 12h14", "M12 5v14")
}

val TsuzukiIcons.CircleCheck: ImageVector by lazy {
    lucideIcon("CircleCheck", "M21.801 10A10 10 0 1 1 17 3.335", "m9 11 3 3L22 4")
}

val TsuzukiIcons.CircleAlert: ImageVector by lazy {
    lucideIcon("CircleAlert", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 8v4", "M12 16h.01")
}

val TsuzukiIcons.Save: ImageVector by lazy {
    lucideIcon(
        "Save",
        "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
        "M7 3v4a1 1 0 0 0 1 1h7",
    )
}

val TsuzukiIcons.ListChecks: ImageVector by lazy {
    lucideIcon("ListChecks", "m3 17 2 2 4-4", "m3 7 2 2 4-4", "M13 6h8", "M13 12h8", "M13 18h8")
}

val TsuzukiIcons.ArrowDownUp: ImageVector by lazy {
    lucideIcon("ArrowDownUp", "m3 16 4 4 4-4", "M7 20V4", "m21 8-4-4-4 4", "M17 4v16")
}

val TsuzukiIcons.ArrowUpDown: ImageVector by lazy {
    lucideIcon("ArrowUpDown", "m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16")
}

val TsuzukiIcons.TriangleAlert: ImageVector by lazy {
    lucideIcon(
        "TriangleAlert",
        "M21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    )
}

val TsuzukiIcons.Minus: ImageVector by lazy {
    lucideIcon("Minus", "M5 12h14")
}

val TsuzukiIcons.Pause: ImageVector by lazy {
    lucideIcon(
        "Pause",
        "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
    )
}

val TsuzukiIcons.Star: ImageVector by lazy {
    lucideIcon(
        "Star",
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
    )
}

val TsuzukiIcons.StarFilled: ImageVector by lazy {
    lucideIcon(
        "StarFilled",
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
        filled = true,
    )
}

val TsuzukiIcons.Play: ImageVector by lazy {
    lucideIcon(
        "Play",
        "M6 3l14 9-14 9V3z",
    )
}

val TsuzukiIcons.GripVertical: ImageVector by lazy {
    lucideIcon(
        "GripVertical",
        "M8 5a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M8 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M8 19a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M14 5a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M14 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M14 19a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
    )
}

val TsuzukiIcons.ListFilter: ImageVector by lazy {
    lucideIcon("ListFilter", "M3 6h18", "M7 12h10", "M10 18h4")
}

val TsuzukiIcons.Heart: ImageVector by lazy {
    lucideIcon(
        "Heart",
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z",
    )
}

val TsuzukiIcons.ChevronLeft: ImageVector by lazy {
    lucideIcon("ChevronLeft", "m15 18-6-6 6-6", autoMirror = true)
}

val TsuzukiIcons.ChevronUp: ImageVector by lazy {
    lucideIcon("ChevronUp", "m18 15-6-6-6 6")
}

val TsuzukiIcons.Grid3x3: ImageVector by lazy {
    lucideIcon(
        "Grid3x3",
        "M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M3 9h18",
        "M3 15h18",
        "M9 3v18",
        "M15 3v18",
    )
}

val TsuzukiIcons.LayoutGrid: ImageVector by lazy {
    lucideIcon(
        "LayoutGrid",
        "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
        "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
    )
}

val TsuzukiIcons.Rows2: ImageVector by lazy {
    lucideIcon(
        "Rows2",
        "M4 3h16a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M4 14h16a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
    )
}

val TsuzukiIcons.List: ImageVector by lazy {
    lucideIcon("List", "M3 12h.01", "M3 18h.01", "M3 6h.01", "M8 12h13", "M8 18h13", "M8 6h13")
}

val TsuzukiIcons.CheckCheck: ImageVector by lazy {
    lucideIcon(
        "CheckCheck",
        "M18 6 7 17l-5-5",
        "m22 10-7.5 7.5L13 16",
    )
}

val TsuzukiIcons.Layers: ImageVector by lazy {
    lucideIcon(
        "Layers",
        "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
        "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
        "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
    )
}

val TsuzukiIcons.Shield: ImageVector by lazy {
    lucideIcon(
        "Shield",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    )
}

val TsuzukiIcons.Pin: ImageVector by lazy {
    lucideIcon(
        "Pin",
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    )
}

val TsuzukiIcons.PinFilled: ImageVector by lazy {
    lucideIcon(
        "PinFilled",
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
        filled = true,
    )
}

val TsuzukiIcons.House: ImageVector by lazy {
    lucideIcon(
        "House",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
}

val TsuzukiIcons.Rss: ImageVector by lazy {
    lucideIcon(
        "Rss",
        "M4 11a9 9 0 0 1 9 9",
        "M4 4a16 16 0 0 1 16 16",
        "M5 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
    )
}

val TsuzukiIcons.LucideIcons: List<ImageVector> by lazy {
    listOf(
        TsuzukiIcons.BookOpen,
        TsuzukiIcons.LucideLibrary,
        TsuzukiIcons.Bell,
        TsuzukiIcons.HistoryLucide,
        TsuzukiIcons.Compass,
        TsuzukiIcons.Ellipsis,
        TsuzukiIcons.Search,
        TsuzukiIcons.EllipsisVertical,
        TsuzukiIcons.SlidersHorizontal,
        TsuzukiIcons.ChevronDown,
        TsuzukiIcons.X,
        TsuzukiIcons.EyeOff,
        TsuzukiIcons.SettingsLucide,
        TsuzukiIcons.ChartLine,
        TsuzukiIcons.TrendingUp,
        TsuzukiIcons.Info,
        TsuzukiIcons.Check,
        TsuzukiIcons.CheckCheck,
        TsuzukiIcons.ChevronsUpDown,
        TsuzukiIcons.Download,
        TsuzukiIcons.Link,
        TsuzukiIcons.Trash2,
        TsuzukiIcons.RefreshCw,
        TsuzukiIcons.RotateCcw,
        TsuzukiIcons.Pencil,
        TsuzukiIcons.Copy,
        TsuzukiIcons.Calendar,
        TsuzukiIcons.Tag,
        TsuzukiIcons.Folder,
        TsuzukiIcons.Sparkles,
        TsuzukiIcons.Palette,
        TsuzukiIcons.Images,
        TsuzukiIcons.ArrowLeftRight,
        TsuzukiIcons.Layers,
        TsuzukiIcons.Shield,
        TsuzukiIcons.Pin,
        TsuzukiIcons.PinFilled,
        TsuzukiIcons.House,
        TsuzukiIcons.Rss,
    )
}
