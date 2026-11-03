package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object TsuzukiIcons {
    val Back = symbol("Back", mirrored = true) {
        moveTo(15f, 4f); lineTo(7f, 12f); lineTo(15f, 20f)
    }
    val Forward = symbol("Forward") {
        moveTo(9f, 4f); lineTo(17f, 12f); lineTo(9f, 20f)
    }
    val Share = symbol("Share") {
        moveTo(8f, 9f); lineTo(5f, 9f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 9f); lineTo(16f, 9f)
        moveTo(12f, 15f); lineTo(12f, 2f); moveTo(8f, 6f); lineTo(12f, 2f); lineTo(16f, 6f)
    }
    val Chapters = symbol("Chapters") {
        moveTo(4f, 5f); lineTo(4.1f, 5f); moveTo(8f, 5f); lineTo(21f, 5f)
        moveTo(4f, 12f); lineTo(4.1f, 12f); moveTo(8f, 12f); lineTo(21f, 12f)
        moveTo(4f, 19f); lineTo(4.1f, 19f); moveTo(8f, 19f); lineTo(21f, 19f)
    }
    val Settings = symbol("Settings") {
        moveTo(3f, 6f); lineTo(7f, 6f); moveTo(11f, 6f); lineTo(21f, 6f)
        moveTo(3f, 12f); lineTo(14f, 12f); moveTo(18f, 12f); lineTo(21f, 12f)
        moveTo(3f, 18f); lineTo(6f, 18f); moveTo(10f, 18f); lineTo(21f, 18f)
        moveTo(9f, 3f); lineTo(9f, 9f); moveTo(16f, 9f); lineTo(16f, 15f); moveTo(8f, 15f); lineTo(8f, 21f)
    }
    val Library = symbol("Library") {
        moveTo(3f, 4f); lineTo(8f, 4f); lineTo(8f, 21f); lineTo(3f, 21f); close()
        moveTo(11f, 3f); lineTo(16f, 3f); lineTo(16f, 21f); lineTo(11f, 21f); close()
        moveTo(19f, 5f); lineTo(22f, 20f)
        moveTo(4f, 8f); lineTo(7f, 8f); moveTo(12f, 7f); lineTo(15f, 7f)
    }
    val Updates = symbol("Updates") {
        moveTo(12f, 3f); curveTo(8f, 3f, 6f, 6f, 6f, 10f)
        lineTo(6f, 14f); lineTo(4f, 17f); lineTo(20f, 17f); lineTo(18f, 14f); lineTo(18f, 10f)
        curveTo(18f, 6f, 16f, 3f, 12f, 3f); close()
        moveTo(10f, 21f); lineTo(14f, 21f)
    }
    val History = symbol("History") {
        moveTo(4f, 7f); curveTo(6f, 3f, 12f, 2f, 17f, 5f)
        curveTo(23f, 9f, 21f, 18f, 16f, 21f); curveTo(11f, 24f, 4f, 21f, 3f, 16f)
        moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
        moveTo(12f, 7f); lineTo(12f, 13f); lineTo(16f, 15f)
    }
    val Browse = symbol("Browse") {
        moveTo(12f, 2f); curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
        curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f); curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
        curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f); close()
        moveTo(16f, 7f); lineTo(14f, 14f); lineTo(7f, 17f); lineTo(10f, 10f); close()
    }
    val Globe = symbol("Globe") {
        moveTo(12f, 2f); curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
        curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f); curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
        curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f); close()
        moveTo(2f, 12f); lineTo(22f, 12f)
        moveTo(12f, 2f); curveTo(6f, 7f, 6f, 17f, 12f, 22f); curveTo(18f, 17f, 18f, 7f, 12f, 2f)
    }
    val More = symbol("More") {
        moveTo(4f, 12f); lineTo(4.1f, 12f); moveTo(12f, 12f); lineTo(12.1f, 12f); moveTo(20f, 12f); lineTo(20.1f, 12f)
    }
    val Book = symbol("Book") {
        moveTo(12f, 6f); curveTo(9f, 3f, 5f, 3f, 2f, 4f); lineTo(2f, 19f)
        curveTo(6f, 18f, 9f, 19f, 12f, 21f); curveTo(15f, 19f, 18f, 18f, 22f, 19f)
        lineTo(22f, 4f); curveTo(19f, 3f, 15f, 3f, 12f, 6f); lineTo(12f, 21f)
    }

    private fun symbol(name: String, mirrored: Boolean = false, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirrored).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
}
