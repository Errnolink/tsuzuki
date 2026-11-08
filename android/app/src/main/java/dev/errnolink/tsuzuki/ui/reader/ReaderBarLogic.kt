package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.database.models.isRecognizedNumber
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlin.math.roundToInt

fun calculateSliderFraction(displayed: Int, valueRange: IntRange, reversed: Boolean): Float {
    val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
    var f = (displayed - valueRange.first).toFloat() / span
    f = f.coerceIn(0f, 1f)
    return if (reversed) 1f - f else f
}

fun calculateSliderValueAt(
    positionPx: Float,
    widthPx: Float,
    padPx: Float,
    valueRange: IntRange,
    reversed: Boolean,
): Int {
    val trackWidth = (widthPx - padPx * 2).coerceAtLeast(1f)
    var f = (positionPx - padPx) / trackWidth
    if (reversed) f = 1f - f
    f = f.coerceIn(0f, 1f)
    return (valueRange.first + f * (valueRange.last - valueRange.first)).roundToInt()
}

fun calculateBubbleOffsetPx(
    fraction: Float,
    maxWidthPx: Float,
    bubbleWidthPx: Float,
): Float {
    val halfBubble = bubbleWidthPx / 2f
    val x = maxWidthPx * fraction.coerceIn(0f, 1f)
    val maxOffset = (maxWidthPx - bubbleWidthPx).coerceAtLeast(0f)
    return (x - halfBubble).coerceIn(0f, maxOffset)
}

fun formatPageChipText(page: Int, total: Int): String = "Page $page of $total"

fun formatPageCountText(current: Int, total: Int): String = "$current / $total"

fun formatChapterShortName(readerChapter: ReaderChapter?): String? {
    val chapter = readerChapter?.chapter ?: return null
    return formatChapterShortName(
        chapterNumber = chapter.chapter_number.toDouble(),
        isRecognized = chapter.isRecognizedNumber,
        name = chapter.name,
    )
}

@Immutable
data class ChapterLabels(val previous: String?, val current: String, val next: String?)

fun formatChapterShortName(
    chapterNumber: Double,
    isRecognized: Boolean,
    name: String?,
): String? {
    return if (isRecognized && chapterNumber.isFinite() && chapterNumber >= 0.0) {
        "Ch. ${formatChapterNumber(chapterNumber)}"
    } else {
        name?.trim()?.ifBlank { null }
    }
}

@Immutable
data class CarouselSide(
    val label: String?,
    val isNext: Boolean,
)

@Immutable
data class CarouselNeighbours(
    val left: CarouselSide,
    val right: CarouselSide,
)

fun resolveCarouselNeighbours(labels: ChapterLabels, isRtl: Boolean): CarouselNeighbours {
    return if (isRtl) {
        CarouselNeighbours(
            left = CarouselSide(labels.next, isNext = true),
            right = CarouselSide(labels.previous, isNext = false),
        )
    } else {
        CarouselNeighbours(
            left = CarouselSide(labels.previous, isNext = false),
            right = CarouselSide(labels.next, isNext = true),
        )
    }
}

@Immutable
data class ReaderArrowConfig(
    val contentDescription: String,
    val enabled: Boolean,
    val isNext: Boolean,
)

fun resolveLeftArrow(isRtl: Boolean, enabledPrevious: Boolean, enabledNext: Boolean): ReaderArrowConfig {
    return if (isRtl) {
        ReaderArrowConfig(
            contentDescription = "Next chapter",
            enabled = enabledNext,
            isNext = true,
        )
    } else {
        ReaderArrowConfig(
            contentDescription = "Previous chapter",
            enabled = enabledPrevious,
            isNext = false,
        )
    }
}

fun resolveRightArrow(isRtl: Boolean, enabledPrevious: Boolean, enabledNext: Boolean): ReaderArrowConfig {
    return if (isRtl) {
        ReaderArrowConfig(
            contentDescription = "Previous chapter",
            enabled = enabledPrevious,
            isNext = false,
        )
    } else {
        ReaderArrowConfig(
            contentDescription = "Next chapter",
            enabled = enabledNext,
            isNext = true,
        )
    }
}

fun resolveEffectiveBarStyle(barStyle: ReaderBarStyle, navBarType: NavBarType): ReaderBarStyle {
    return if (navBarType == NavBarType.Bottom) barStyle else ReaderBarStyle.Classic
}

fun pagesLeftInChapter(currentPage: Int, totalPages: Int): String? {
    if (totalPages <= 0 || currentPage < 1 || currentPage > totalPages) return null
    return when (val left = totalPages - currentPage) {
        0 -> "Last page in chapter"
        1 -> "1 page left in chapter"
        else -> "$left pages left in chapter"
    }
}
