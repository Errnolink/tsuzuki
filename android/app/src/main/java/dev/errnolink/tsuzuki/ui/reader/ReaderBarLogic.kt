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

fun scrubBubbleLeftPx(
    trackLeftPx: Float,
    trackWidthPx: Float,
    padPx: Float,
    fraction: Float,
    containerWidthPx: Float,
    bubbleWidthPx: Float,
): Float {
    val thumb = trackLeftPx + padPx + fraction.coerceIn(0f, 1f) * (trackWidthPx - padPx * 2).coerceAtLeast(0f)
    val maxLeft = (containerWidthPx - bubbleWidthPx).coerceAtLeast(0f)
    return (thumb - bubbleWidthPx / 2f).coerceIn(0f, maxLeft)
}

fun formatPageCountText(current: Int, total: Int): String = "$current / $total"

fun formatChapterShortName(readerChapter: ReaderChapter?, chapterShortPattern: String): String? {
    val chapter = readerChapter?.chapter ?: return null
    return formatChapterShortName(
        chapterNumber = chapter.chapter_number.toDouble(),
        isRecognized = chapter.isRecognizedNumber,
        name = chapter.name,
        chapterShortPattern = chapterShortPattern,
    )
}


@Immutable
data class ChapterLabels(val previous: String?, val current: String, val next: String?)

fun formatChapterShortName(
    chapterNumber: Double,
    isRecognized: Boolean,
    name: String?,
    chapterShortPattern: String,
): String? {
    return if (isRecognized && chapterNumber.isFinite() && chapterNumber >= 0.0) {
        chapterShortPattern.format(formatChapterNumber(chapterNumber))
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

fun resolveLeftArrow(isRtl: Boolean, enabledPrevious: Boolean, enabledNext: Boolean, previousLabel: String, nextLabel: String): ReaderArrowConfig {
    return if (isRtl) {
        ReaderArrowConfig(
            contentDescription = nextLabel,
            enabled = enabledNext,
            isNext = true,
        )
    } else {
        ReaderArrowConfig(
            contentDescription = previousLabel,
            enabled = enabledPrevious,
            isNext = false,
        )
    }
}

fun resolveRightArrow(isRtl: Boolean, enabledPrevious: Boolean, enabledNext: Boolean, previousLabel: String, nextLabel: String): ReaderArrowConfig {
    return if (isRtl) {
        ReaderArrowConfig(
            contentDescription = previousLabel,
            enabled = enabledPrevious,
            isNext = false,
        )
    } else {
        ReaderArrowConfig(
            contentDescription = nextLabel,
            enabled = enabledNext,
            isNext = true,
        )
    }
}

fun resolveEffectiveBarStyle(barStyle: ReaderBarStyle, navBarType: NavBarType): ReaderBarStyle {
    return if (navBarType == NavBarType.Bottom) barStyle else ReaderBarStyle.Classic
}

/** The page count the chrome shows: "9 / 30", or nothing before the chapter has pages. */
fun readerPageCount(currentPage: Int, totalPages: Int): String? {
    if (totalPages <= 0 || currentPage < 1 || currentPage > totalPages) return null
    return "$currentPage / $totalPages"
}

/** The scrubber's resting page. `currentPage` is already 1-based (`ReaderViewModel` stores index + 1). */
fun scrubberRestPage(currentPage: Int, totalPages: Int): Int = currentPage.coerceIn(1, totalPages.coerceAtLeast(1))

/** Whether the viewer's report confirms a jump to [landing]; a double-page spread reports both of its pages. */
fun reportsLanding(page: Int, pageText: String, landing: Int): Boolean =
    page == landing || pageText.split('-').any { it.toIntOrNull() == landing }

/** How far through the chapter the reader is, for the status pill. */
fun readerProgressFraction(currentPage: Int, totalPages: Int): Float =
    if (totalPages > 0) (currentPage.toFloat() / totalPages).coerceIn(0.04f, 1f) else 0.04f
