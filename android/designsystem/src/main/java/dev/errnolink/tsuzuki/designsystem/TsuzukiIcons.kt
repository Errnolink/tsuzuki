package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private class IconPath(
    val d: String,
    val fill: Boolean,
    val stroke: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
    val evenOdd: Boolean,
)

private fun p(
    d: String,
    fill: Boolean = false,
    stroke: Float = 0f,
    cap: StrokeCap = StrokeCap.Butt,
    join: StrokeJoin = StrokeJoin.Miter,
    evenOdd: Boolean = false,
) = IconPath(d, fill, stroke, cap, join, evenOdd)

/**
 * [dx]/[dy] move the whole drawing in viewport units, so a glyph drawn off-centre is centred at
 * the source (`IconCenteringTest`) without rewriting its path data.
 */
private fun icon(
    name: String,
    vararg paths: IconPath,
    mirrored: Boolean = false,
    dx: Float = 0f,
    dy: Float = 0f,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrored,
    ).apply {
        addGroup(translationX = dx, translationY = dy)
        for (path in paths) {
            addPath(
                pathData = addPathNodes(path.d),
                pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = if (path.fill) SolidColor(Color.Black) else null,
                stroke = if (path.stroke > 0f) SolidColor(Color.Black) else null,
                strokeLineWidth = path.stroke,
                strokeLineCap = path.cap,
                strokeLineJoin = path.join,
            )
        }
        clearGroup()
    }.build()

object TsuzukiIcons {
    val Back: ImageVector by lazy {
        icon(
            "Back",
            p("M15.5 5 8.5 12l7 7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            mirrored = true,
        )
    }

    val Forward: ImageVector by lazy {
        icon(
            "Forward",
            p("m8.5 5 7 7-7 7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            mirrored = true,
        )
    }

    val Share: ImageVector by lazy {
        icon(
            "Share",
            p("M12 3.5v11.5M7.8 7.5 12 3.3l4.2 4.2", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            p("M8 10.5H6.5a1.5 1.5 0 0 0-1.5 1.5v7a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5v-7a1.5 1.5 0 0 0-1.5-1.5H16", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    val Chapters: ImageVector by lazy {
        icon(
            "Chapters",
            p("M9 6.5h11M9 12h11M9 17.5h11", stroke = 1.9f, cap = StrokeCap.Round),
            p("M3.7 6.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
            p("M3.7 12a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
            p("M3.7 17.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            p("M4 7h9M17 7h3M4 17h3M11 17h9", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            p("M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            p("M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    val Library: ImageVector by lazy {
        icon(
            "Library",
            p("M4.4 4h2.2a1.2 1.2 0 0 1 1.2 1.2v13.6a1.2 1.2 0 0 1 -1.2 1.2h-2.2a1.2 1.2 0 0 1 -1.2 -1.2v-13.6a1.2 1.2 0 0 1 1.2 -1.2z", stroke = 1.9f, join = StrokeJoin.Round),
            p("M10.8 4h2.2a1.2 1.2 0 0 1 1.2 1.2v13.6a1.2 1.2 0 0 1 -1.2 1.2h-2.2a1.2 1.2 0 0 1 -1.2 -1.2v-13.6a1.2 1.2 0 0 1 1.2 -1.2z", stroke = 1.9f, join = StrokeJoin.Round),
            p("m16.2 5.6 3.6-.9 3 14.5-3.6.9z", stroke = 1.9f, join = StrokeJoin.Round),
            dx = -1f,
        )
    }

    val Updates: ImageVector by lazy {
        icon(
            "Updates",
            p("M6 14.5V9a6 6 0 1 1 12 0v5.5l1.6 1.8H4.4z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            p("M9.8 18.6a2.4 2.4 0 0 0 4.4 0", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    val History: ImageVector by lazy {
        icon(
            "History",
            p("M3.4 12a8.6 8.6 0 1 0 17.2 0a8.6 8.6 0 1 0 -17.2 0z", stroke = 1.9f, cap = StrokeCap.Round),
            p("M12 7.5V12l3 2", stroke = 1.9f, cap = StrokeCap.Round),
        )
    }

    val Browse: ImageVector by lazy {
        icon(
            "Browse",
            p("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0z", stroke = 1.9f, join = StrokeJoin.Round),
            p("m15.6 8.4-2 5.2-5.2 2 2-5.2z", stroke = 1.9f, join = StrokeJoin.Round),
        )
    }

    val Globe: ImageVector by lazy {
        icon(
            "Globe",
            p("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0z", stroke = 1.8f),
            p("M3.5 12h17M12 3.2c2.6 2.6 3.8 5.6 3.8 8.8s-1.2 6.2-3.8 8.8c-2.6-2.6-3.8-5.6-3.8-8.8S9.4 5.8 12 3.2z", stroke = 1.8f),
        )
    }

    val More: ImageVector by lazy {
        icon(
            "More",
            p("M3.6 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
            p("M10.1 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
            p("M16.6 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
        )
    }

    val Book: ImageVector by lazy {
        icon(
            "Book",
            p("M12 6.5C10 5 6.5 4.5 3 5v13.5c3.5-.5 7 0 9 1.5 2-1.5 5.5-2 9-1.5V5c-3.5-.5-7 0-9 1.5zM12 6.5V20", stroke = 1.9f, join = StrokeJoin.Round),
        )
    }
}

val TsuzukiIcons.ArrowDown: ImageVector by lazy {
    icon(
        "ArrowDown",
        p("M12 4.5v15m-6-6 6 6 6-6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ArrowDownUp: ImageVector by lazy {
    icon(
        "ArrowDownUp",
        p("M7 4v16m-3.5-3.5L7 20l3.5-3.5M17 20V4m-3.5 3.5L17 4l3.5 3.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ArrowLeftRight: ImageVector by lazy {
    icon(
        "ArrowLeftRight",
        p("M4 8h16m-4-4 4 4-4 4M20 16H4m4-4-4 4 4 4", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ArrowUp: ImageVector by lazy {
    icon(
        "ArrowUp",
        p("M12 19.5v-15m-6 6 6-6 6 6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ArrowUpDown: ImageVector by lazy {
    icon(
        "ArrowUpDown",
        p("M7 4v16m-3.5-3.5L7 20l3.5-3.5M17 20V4m-3.5 3.5L17 4l3.5 3.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Bell: ImageVector by lazy {
    icon(
        "Bell",
        p("M6 16.5V11a6 6 0 1 1 12 0v5.5l1.6 1.8H4.4z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M9.8 20.6a2.4 2.4 0 0 0 4.4 0", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        dy = -1.5f,
    )
}

val TsuzukiIcons.BookOpen: ImageVector by lazy {
    icon(
        "BookOpen",
        p("M12 6.5C10 5 6.5 4.5 3 5v13.5c3.5-.5 7 0 9 1.5 2-1.5 5.5-2 9-1.5V5c-3.5-.5-7 0-9 1.5zM12 6.5V20", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Bookmark: ImageVector by lazy {
    icon(
        "Bookmark",
        p("M6.5 3.8h11v16.6L12 16.3l-5.5 4.1z", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.BookmarkFilled: ImageVector by lazy {
    icon(
        "BookmarkFilled",
        p("M6.4 3h11.2c.5 0 .9.4.9.9v16.8c0 .6-.7 1-1.2.6L12 17.2l-5.3 4.1c-.5.4-1.2 0-1.2-.6V3.9c0-.5.4-.9.9-.9z", fill = true),
    )
}

val TsuzukiIcons.Calendar: ImageVector by lazy {
    icon(
        "Calendar",
        p("M6.5 5.5h11a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1 -2.5 2.5h-11a2.5 2.5 0 0 1 -2.5 -2.5v-10a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M4 10h16M8.5 3.5v4M15.5 3.5v4", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ChartLine: ImageVector by lazy {
    icon(
        "ChartLine",
        p("M4.5 19.5h15M7.5 16v-5M12 16V7M16.5 16v-8", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        dy = -1.25f,
    )
}

val TsuzukiIcons.Check: ImageVector by lazy {
    icon(
        "Check",
        p("m5 12.5 4.5 4.5L19 7.5", stroke = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.CheckCheck: ImageVector by lazy {
    icon(
        "CheckCheck",
        p("m2.5 12.5 4.5 4.5L16.5 7.5M11.5 16l1 1L22 7.5", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ChevronDown: ImageVector by lazy {
    icon(
        "ChevronDown",
        p("m5 9.5 7 7 7-7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        dy = -1f,
    )
}

val TsuzukiIcons.ChevronLeft: ImageVector by lazy {
    icon(
        "ChevronLeft",
        p("M14.5 5 7.5 12l7 7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mirrored = true,
        dx = 1f,
    )
}

val TsuzukiIcons.ChevronRight: ImageVector by lazy {
    icon(
        "ChevronRight",
        p("m9.5 5 7 7-7 7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mirrored = true,
        dx = -1f,
    )
}

val TsuzukiIcons.ChevronUp: ImageVector by lazy {
    icon(
        "ChevronUp",
        p("m5 14.5 7-7 7 7", stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        dy = 1f,
    )
}

val TsuzukiIcons.ChevronsUpDown: ImageVector by lazy {
    icon(
        "ChevronsUpDown",
        p("m7.5 9 4.5-4.5L16.5 9M7.5 15l4.5 4.5 4.5-4.5", stroke = 2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.CircleAlert: ImageVector by lazy {
    icon(
        "CircleAlert",
        p("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M12 7.5V13", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M10.9 16.3a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z", fill = true),
    )
}

val TsuzukiIcons.CircleCheck: ImageVector by lazy {
    icon(
        "CircleCheck",
        p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM7.222 13.078L9.922 15.778A1.1 1.1 0 0 0 11.478 15.778L16.978 10.278A1.1 1.1 0 0 0 15.422 8.722L10.7 13.444L8.778 11.522A1.1 1.1 0 0 0 7.222 13.078Z", fill = true, evenOdd = true),
    )
}

val TsuzukiIcons.Compass: ImageVector by lazy {
    icon(
        "Compass",
        p("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0z", stroke = 1.9f, join = StrokeJoin.Round),
        p("m15.6 8.4-2 5.2-5.2 2 2-5.2z", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Copy: ImageVector by lazy {
    icon(
        "Copy",
        p("M11 8.5h6a2.5 2.5 0 0 1 2.5 2.5v6a2.5 2.5 0 0 1 -2.5 2.5h-6a2.5 2.5 0 0 1 -2.5 -2.5v-6a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, join = StrokeJoin.Round),
        p("M15.5 8.5V6a1.5 1.5 0 0 0-1.5-1.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Crop: ImageVector by lazy {
    icon(
        "Crop",
        p("M6.5 3v12.5c0 1.1.9 2 2 2H21M3 6.5h12.5c1.1 0 2 .9 2 2V21", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Download: ImageVector by lazy {
    icon(
        "Download",
        p("M12 4v11m-4.5-4.4L12 15l4.5-4.4M5 19.8h14", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Ellipsis: ImageVector by lazy {
    icon(
        "Ellipsis",
        p("M3.6 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
        p("M10.1 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
        p("M16.6 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
    )
}

val TsuzukiIcons.EllipsisVertical: ImageVector by lazy {
    icon(
        "EllipsisVertical",
        p("M10.1 5.5a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
        p("M10.1 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
        p("M10.1 18.5a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z", fill = true),
    )
}

val TsuzukiIcons.Eye: ImageVector by lazy {
    icon(
        "Eye",
        p("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", stroke = 1.9f),
        p("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", stroke = 1.9f),
    )
}

val TsuzukiIcons.EyeOff: ImageVector by lazy {
    icon(
        "EyeOff",
        p("M9.9 5.8A9 9 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.4 3.2M14.1 14.2A3 3 0 0 1 9.8 9.9M6.6 7.4C3.9 9.1 2.5 12 2.5 12S6 18.5 12 18.5c1.7 0 3.2-.5 4.5-1.3M3.5 3.5l17 17", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Folder: ImageVector by lazy {
    icon(
        "Folder",
        p("M3.5 6.5a1.5 1.5 0 0 1 1.5-1.5h4.2l2 2.2H19a1.5 1.5 0 0 1 1.5 1.5v9.3A1.5 1.5 0 0 1 19 19.5H5a1.5 1.5 0 0 1-1.5-1.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Gear: ImageVector by lazy {
    icon(
        "Gear",
        p("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", stroke = 1.8f, join = StrokeJoin.Round),
        p("M12 2.8l1.6 2.1 2.6-.6.6 2.6 2.4 1.1-.9 2.5 1.6 2.1-2.1 1.6.6 2.6-2.6.6-1.1 2.4-2.5-.9-2.1 1.6-1.6-2.1-2.6.6-.6-2.6-2.4-1.1.9-2.5-1.6-2.1 2.1-1.6-.6-2.6 2.6-.6 1.1-2.4 2.5.9z", stroke = 1.8f, join = StrokeJoin.Round),
        dx = 0.95f, dy = 0.35f,
    )
}

val TsuzukiIcons.Grid3x3: ImageVector by lazy {
    icon(
        "Grid3x3",
        p("M6.5 3.5h11a3 3 0 0 1 3 3v11a3 3 0 0 1 -3 3h-11a3 3 0 0 1 -3 -3v-11a3 3 0 0 1 3 -3z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M9.2 3.5v17M14.8 3.5v17M3.5 9.2h17M3.5 14.8h17", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.GripVertical: ImageVector by lazy {
    icon(
        "GripVertical",
        p("M7.4 5.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
        p("M13.4 5.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
        p("M7.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
        p("M13.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
        p("M7.4 18.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
        p("M13.4 18.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
    )
}

val TsuzukiIcons.Heart: ImageVector by lazy {
    icon(
        "Heart",
        p("M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z", stroke = 1.9f, join = StrokeJoin.Round),
        dy = -0.9f,
    )
}

val TsuzukiIcons.House: ImageVector by lazy {
    icon(
        "House",
        p("M4 10.5 12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19z", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Images: ImageVector by lazy {
    icon(
        "Images",
        p("M9.5 3.5h8.5a2.5 2.5 0 0 1 2.5 2.5v7.0a2.5 2.5 0 0 1 -2.5 2.5h-8.5a2.5 2.5 0 0 1 -2.5 -2.5v-7.0a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M4 7.5V17a3 3 0 0 0 3 3h9.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m7.5 13.5 3.5-3.5 2.5 2.5 2-2 4.5 4.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Info: ImageVector by lazy {
    icon(
        "Info",
        p("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0z", stroke = 1.9f, cap = StrokeCap.Round),
        p("M12 11v5.5", stroke = 1.9f, cap = StrokeCap.Round),
        p("M11.4 7.8a0.6 0.6 0 1 0 1.2 0a0.6 0.6 0 1 0 -1.2 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Layers: ImageVector by lazy {
    icon(
        "Layers",
        p("M12 3.5 21 8l-9 4.5L3 8z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m3 12 9 4.5 9-4.5M3 16l9 4.5 9-4.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.LayoutGrid: ImageVector by lazy {
    icon(
        "LayoutGrid",
        p("M5.8 4h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1 -1.8 1.8h-2.9a1.8 1.8 0 0 1 -1.8 -1.8v-2.9a1.8 1.8 0 0 1 1.8 -1.8z", stroke = 1.9f),
        p("M15.3 4h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1 -1.8 1.8h-2.9a1.8 1.8 0 0 1 -1.8 -1.8v-2.9a1.8 1.8 0 0 1 1.8 -1.8z", stroke = 1.9f),
        p("M5.8 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1 -1.8 1.8h-2.9a1.8 1.8 0 0 1 -1.8 -1.8v-2.9a1.8 1.8 0 0 1 1.8 -1.8z", stroke = 1.9f),
        p("M15.3 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1 -1.8 1.8h-2.9a1.8 1.8 0 0 1 -1.8 -1.8v-2.9a1.8 1.8 0 0 1 1.8 -1.8z", stroke = 1.9f),
    )
}

val TsuzukiIcons.Link: ImageVector by lazy {
    icon(
        "Link",
        p("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.List: ImageVector by lazy {
    icon(
        "List",
        p("M9 6.5h11M9 12h11M9 17.5h11", stroke = 1.9f, cap = StrokeCap.Round),
        p("M3.7 6.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
        p("M3.7 12a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
        p("M3.7 17.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.ListChecks: ImageVector by lazy {
    icon(
        "ListChecks",
        p("m3.5 6.5 1.6 1.6 3-3.1M3.5 15.5l1.6 1.6 3-3.1M11.5 7h9M11.5 16h9", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        dy = 0.95f,
    )
}

val TsuzukiIcons.ListFilter: ImageVector by lazy {
    icon(
        "ListFilter",
        p("M4 7h16M7 12h10M10 17h4", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Minus: ImageVector by lazy {
    icon(
        "Minus",
        p("M5 12h14", stroke = 2.2f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Palette: ImageVector by lazy {
    icon(
        "Palette",
        p("M12 3.5a8.5 8.5 0 0 0 0 17c1.4 0 2-1 1.6-2.1-.5-1.3.4-2.4 1.8-2.4H17a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9-8.5-9z", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M7.5 11.5h.01M10 7.5h.01M14.5 7.5h.01M17 11h.01", stroke = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Pause: ImageVector by lazy {
    icon(
        "Pause",
        p("M7.4 5h1.2a1.4 1.4 0 0 1 1.4 1.4v11.2a1.4 1.4 0 0 1 -1.4 1.4h-1.2a1.4 1.4 0 0 1 -1.4 -1.4v-11.2a1.4 1.4 0 0 1 1.4 -1.4z", fill = true),
        p("M15.4 5h1.2a1.4 1.4 0 0 1 1.4 1.4v11.2a1.4 1.4 0 0 1 -1.4 1.4h-1.2a1.4 1.4 0 0 1 -1.4 -1.4v-11.2a1.4 1.4 0 0 1 1.4 -1.4z", fill = true),
    )
}

val TsuzukiIcons.Pencil: ImageVector by lazy {
    icon(
        "Pencil",
        p("M4.5 19.5l1-4.2L16 4.8a2 2 0 0 1 2.8 0l.4.4a2 2 0 0 1 0 2.8L8.7 18.5z", stroke = 1.9f, join = StrokeJoin.Round),
        p("M14 7l3 3", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Pin: ImageVector by lazy {
    icon(
        "Pin",
        p("M9 4.5h6l-1 5 3.5 3.5h-11L10 9.5zM12 13v7", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.PinFilled: ImageVector by lazy {
    icon(
        "PinFilled",
        p("M9 3.6h6a.9.9 0 0 1 .9 1.1l-.9 4.5 3.2 3.2c.6.6.2 1.5-.6 1.5H6.4c-.8 0-1.2-.9-.6-1.5L9 9.2 8.1 4.7A.9.9 0 0 1 9 3.6z", fill = true),
        p("M12 13v7", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Play: ImageVector by lazy {
    icon(
        "Play",
        p("M7.5 5.6v12.8c0 .8.9 1.3 1.6.9l10.2-6.4c.6-.4.6-1.4 0-1.8L9.1 4.7c-.7-.4-1.6.1-1.6.9z", fill = true),
        dx = -1f,
    )
}

val TsuzukiIcons.Plus: ImageVector by lazy {
    icon(
        "Plus",
        p("M12 5v14M5 12h14", stroke = 2.2f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.RefreshCw: ImageVector by lazy {
    icon(
        "RefreshCw",
        p("M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4v4h-4", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.RotateCcw: ImageVector by lazy {
    icon(
        "RotateCcw",
        p("M4.5 12a7.5 7.5 0 1 0 2.2-5.3M4.5 4v4h4", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mirrored = true,
    )
}

val TsuzukiIcons.Rows2: ImageVector by lazy {
    icon(
        "Rows2",
        p("M5.5 4h13a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M5.5 13h13a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Rss: ImageVector by lazy {
    icon(
        "Rss",
        p("M4.5 11a8.5 8.5 0 0 1 8.5 8.5M4.5 4.5a15 15 0 0 1 15 15", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M3.9 18.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", fill = true),
    )
}

val TsuzukiIcons.Save: ImageVector by lazy {
    icon(
        "Save",
        p("M5.5 4h10.3c.5 0 1 .2 1.4.6l2.2 2.2c.4.4.6.9.6 1.4v10.3a1.5 1.5 0 0 1-1.5 1.5H5.5A1.5 1.5 0 0 1 4 18.5v-13A1.5 1.5 0 0 1 5.5 4z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M8 4v4h7V4M7.5 20v-5.5h9V20", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ScanQrCode: ImageVector by lazy {
    icon(
        "ScanQrCode",
        p("M4 8.5V6a2 2 0 0 1 2-2h2.5M15.5 4H18a2 2 0 0 1 2 2v2.5M20 15.5V18a2 2 0 0 1-2 2h-2.5M8.5 20H6a2 2 0 0 1-2-2v-2.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M8.6 8h1.8a0.6 0.6 0 0 1 0.6 0.6v1.8a0.6 0.6 0 0 1 -0.6 0.6h-1.8a0.6 0.6 0 0 1 -0.6 -0.6v-1.8a0.6 0.6 0 0 1 0.6 -0.6z", stroke = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M13.6 13h1.8a0.6 0.6 0 0 1 0.6 0.6v1.8a0.6 0.6 0 0 1 -0.6 0.6h-1.8a0.6 0.6 0 0 1 -0.6 -0.6v-1.8a0.6 0.6 0 0 1 0.6 -0.6z", stroke = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M13.4 9.5a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z", fill = true),
        p("M8.4 14.5a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z", fill = true),
    )
}

val TsuzukiIcons.Search: ImageVector by lazy {
    icon(
        "Search",
        p("M4.5 10.8a6.3 6.3 0 1 0 12.6 0a6.3 6.3 0 1 0 -12.6 0z", stroke = 2.0f, cap = StrokeCap.Round),
        p("m15.6 15.6 4.6 4.6", stroke = 2.0f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Shield: ImageVector by lazy {
    icon(
        "Shield",
        p("M12 3 4.5 6v5.5c0 4.6 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.9 7.5-9.5V6z", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m9 12 2 2 4-4", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.SkipBack: ImageVector by lazy {
    icon(
        "SkipBack",
        p("M6.2 5h0a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-0a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z", fill = true),
        p("M18.4 5.9v12.2c0 .8-.9 1.3-1.6.8L9.4 13c-.6-.5-.6-1.4 0-1.9l7.4-5.9c.7-.6 1.6-.1 1.6.7z", fill = true),
    )
}

val TsuzukiIcons.SkipForward: ImageVector by lazy {
    icon(
        "SkipForward",
        p("M17.8 5h0a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-0a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z", fill = true),
        p("M5.6 5.9v12.2c0 .8.9 1.3 1.6.8l7.4-5.9c.6-.5.6-1.4 0-1.9L7.2 5.2c-.7-.6-1.6-.1-1.6.7z", fill = true),
    )
}

val TsuzukiIcons.SlidersHorizontal: ImageVector by lazy {
    icon(
        "SlidersHorizontal",
        p("M4 7h9M17 7h3M4 17h3M11 17h9", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Sparkles: ImageVector by lazy {
    icon(
        "Sparkles",
        p("M12 2.5c.6 4.8 2.7 6.9 7.5 7.5-4.8.6-6.9 2.7-7.5 7.5-.6-4.8-2.7-6.9-7.5-7.5 4.8-.6 6.9-2.7 7.5-7.5z", fill = true),
        dy = 2f,
    )
}

val TsuzukiIcons.Star: ImageVector by lazy {
    icon(
        "Star",
        p("m12 3.4 2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.8l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.StarFilled: ImageVector by lazy {
    icon(
        "StarFilled",
        p("m12 3.4 2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.8l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8z", fill = true),
    )
}

val TsuzukiIcons.Tag: ImageVector by lazy {
    icon(
        "Tag",
        p("M3.5 5v5.2c0 .5.2 1 .6 1.4l8.3 8.3c.8.8 2 .8 2.8 0l5-5c.8-.8.8-2 0-2.8L11.9 3.8c-.4-.4-.9-.6-1.4-.6H5.3A1.8 1.8 0 0 0 3.5 5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M6.4 7.8a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", fill = true),
    )
}

val TsuzukiIcons.Trash2: ImageVector by lazy {
    icon(
        "Trash2",
        p("M4.5 6.5h15M9.5 6.5V4.8c0-.7.5-1.3 1.2-1.3h2.6c.7 0 1.2.6 1.2 1.3v1.7M6.5 6.5l.9 12.6c.1 1 .9 1.4 1.7 1.4h5.8c.8 0 1.6-.4 1.7-1.4l.9-12.6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.TrendingUp: ImageVector by lazy {
    icon(
        "TrendingUp",
        p("m3.5 17 6-6 4 4 7-7M14.5 8h6v6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.TriangleAlert: ImageVector by lazy {
    icon(
        "TriangleAlert",
        p("M10.3 4.2 2.9 17.5c-.7 1.3.2 2.8 1.7 2.8h14.8c1.5 0 2.4-1.5 1.7-2.8L13.7 4.2a2 2 0 0 0-3.4 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M12 9.5v4.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M11.4 17a0.6 0.6 0 1 0 1.2 0a0.6 0.6 0 1 0 -1.2 0z", fill = true, stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Users: ImageVector by lazy {
    icon(
        "Users",
        p("M5.5 8.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M2.5 19.5c.7-3.1 3.2-4.8 6.5-4.8s5.8 1.7 6.5 4.8M15.5 5.3a3.5 3.5 0 0 1 0 6.4M17.5 14.9c2 .6 3.4 2.2 4 4.6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.X: ImageVector by lazy {
    icon(
        "X",
        p("m7 7 10 10M17 7 7 17", stroke = 2.2f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.ArrowLeft: ImageVector by lazy {
    icon(
        "ArrowLeft",
        p("M19.5 12h-15m6-6-6 6 6 6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mirrored = true,
    )
}

val TsuzukiIcons.ArrowRight: ImageVector by lazy {
    icon(
        "ArrowRight",
        p("M4.5 12h15m-6-6 6 6-6 6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mirrored = true,
    )
}

val TsuzukiIcons.BellFill: ImageVector by lazy {
    icon(
        "BellFill",
        p("M12 3.6a6.6 6.6 0 0 0-6.6 6.6v5.9l-1.7 1.9c-.5.6-.1 1.4.7 1.4h15.2c.8 0 1.2-.8.7-1.4l-1.7-1.9v-5.9A6.6 6.6 0 0 0 12 3.6zM9.3 20.4h5.4a2.8 2.8 0 0 1-5.4 0z", fill = true),
        dy = -1f,
    )
}

val TsuzukiIcons.BooksFill: ImageVector by lazy {
    icon(
        "BooksFill",
        p("M4.4 3.8h2.2a1.4 1.4 0 0 1 1.4 1.4v13.6a1.4 1.4 0 0 1 -1.4 1.4h-2.2a1.4 1.4 0 0 1 -1.4 -1.4v-13.6a1.4 1.4 0 0 1 1.4 -1.4z", fill = true),
        p("M10.8 3.8h2.2a1.4 1.4 0 0 1 1.4 1.4v13.6a1.4 1.4 0 0 1 -1.4 1.4h-2.2a1.4 1.4 0 0 1 -1.4 -1.4v-13.6a1.4 1.4 0 0 1 1.4 -1.4z", fill = true),
        p("M15.9 5.3c-.1-.5.2-.9.6-1l2.8-.7c.5-.1.9.2 1 .6l2.8 13.9c.1.5-.2.9-.6 1l-2.8.7c-.5.1-.9-.2-1-.6z", fill = true),
        dx = -1.1f, dy = 0.15f,
    )
}

val TsuzukiIcons.Collapse: ImageVector by lazy {
    icon(
        "Collapse",
        p("M19.5 9.5h-5v-5M4.5 14.5h5v5M14.5 9.5l6-6M9.5 14.5l-6 6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.CompassFill: ImageVector by lazy {
    icon(
        "CompassFill",
        p("M2.6 12a9.4 9.4 0 1 0 18.8 0a9.4 9.4 0 1 0 -18.8 0zm13.2-3.8-2.1 5.5-5.5 2.1 2.1-5.5z", fill = true, evenOdd = true),
    )
}

val TsuzukiIcons.Expand: ImageVector by lazy {
    icon(
        "Expand",
        p("M14.5 4.5h5v5M9.5 19.5h-5v-5M19.5 4.5 13.5 10.5M4.5 19.5l6-6", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.External: ImageVector by lazy {
    icon(
        "External",
        p("M14 4.5h5.5V10M19.5 4.5 11 13M17 13.5V18a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 18V8.5A1.5 1.5 0 0 1 6 7h4.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.File: ImageVector by lazy {
    icon(
        "File",
        p("M6 3.5h8l4.5 4.5v12.5H6z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M14 3.5V8h4.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Image: ImageVector by lazy {
    icon(
        "Image",
        p("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-10a3 3 0 0 1 3 -3z", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M7.2 10a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0z", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m4 18 5-5 3.5 3.5L16 13l4 4", stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.LeftToRight: ImageVector by lazy {
    icon(
        "LeftToRight",
        p("M6 4h12a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-11a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M12 4v16", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m7.5 10 2 2-2 2M14.5 10l2 2-2 2", stroke = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Lines: ImageVector by lazy {
    icon(
        "Lines",
        p("M4 6h16M4 12h16M4 18h10", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.LinesAiry: ImageVector by lazy {
    icon(
        "LinesAiry",
        p("M4 4h16M4 12h16M4 20h10", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.LinesTight: ImageVector by lazy {
    icon(
        "LinesTight",
        p("M4 8h16M4 12h16M4 16h10", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Menu: ImageVector by lazy {
    icon(
        "Menu",
        p("M4 7h16M4 12h16M4 17h16", stroke = 2.0f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Moon: ImageVector by lazy {
    icon(
        "Moon",
        p("M19.8 14.6A8 8 0 1 1 9.4 4.2a6.4 6.4 0 0 0 10.4 10.4z", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.RightToLeft: ImageVector by lazy {
    icon(
        "RightToLeft",
        p("M6 4h12a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-11a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M12 4v16", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m16.5 10-2 2 2 2M9.5 10l-2 2 2 2", stroke = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Spread: ImageVector by lazy {
    icon(
        "Spread",
        p("M12 6.5C10 5 6.5 4.5 3 5v13.5c3.5-.5 7 0 9 1.5 2-1.5 5.5-2 9-1.5V5c-3.5-.5-7 0-9 1.5zM12 6.5V20", stroke = 1.9f, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Sun: ImageVector by lazy {
    icon(
        "Sun",
        p("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z", stroke = 1.9f, cap = StrokeCap.Round),
        p("M12 2.8v2M12 19.2v2M2.8 12h2M19.2 12h2M5.5 5.5l1.4 1.4M17.1 17.1l1.4 1.4M5.5 18.5l1.4-1.4M17.1 6.9l1.4-1.4", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.TextSize: ImageVector by lazy {
    icon(
        "TextSize",
        p("m2.5 18.5 4.6-13 4.6 13M4.2 14h5.8", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m13.4 18.5 3.4-8.8 3.4 8.8m-5.6-3h4.4", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ThemeAuto: ImageVector by lazy {
    icon(
        "ThemeAuto",
        p("M3.6 12a8.4 8.4 0 1 0 16.8 0a8.4 8.4 0 1 0 -16.8 0z", stroke = 1.9f),
        p("M12 3.6v16.8A8.4 8.4 0 0 0 12 3.6z", fill = true, stroke = 1.9f),
    )
}

val TsuzukiIcons.Upload: ImageVector by lazy {
    icon(
        "Upload",
        p("M12 15.5V4M7.5 8.5 12 4l4.5 4.5M5 20h14", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.User: ImageVector by lazy {
    icon(
        "User",
        p("M8 8.5a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z", stroke = 1.9f),
        p("M4.5 20c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5", stroke = 1.9f, cap = StrokeCap.Round),
    )
}

val TsuzukiIcons.Vertical: ImageVector by lazy {
    icon(
        "Vertical",
        p("M9 2.8h6a2.5 2.5 0 0 1 2.5 2.5v13.4a2.5 2.5 0 0 1 -2.5 2.5h-6a2.5 2.5 0 0 1 -2.5 -2.5v-13.4a2.5 2.5 0 0 1 2.5 -2.5z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("M6.5 9.5h11M6.5 15h11", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.ZoomIn: ImageVector by lazy {
    icon(
        "ZoomIn",
        p("M4.5 10.8a6.3 6.3 0 1 0 12.6 0a6.3 6.3 0 1 0 -12.6 0z", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        p("m15.6 15.6 4.6 4.6M10.8 8.3v5M8.3 10.8h5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

val TsuzukiIcons.Hash: ImageVector by lazy {
    icon(
        "Hash",
        p("M9.6 4.5 7.9 19.5M16.1 4.5 14.4 19.5M5 9h14.5M4.5 15h14.5", stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
