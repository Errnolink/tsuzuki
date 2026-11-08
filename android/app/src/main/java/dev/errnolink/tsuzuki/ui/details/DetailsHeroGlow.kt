package dev.errnolink.tsuzuki.ui.details

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asAndroidBitmap
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import kotlin.math.max
import kotlin.math.roundToInt

private const val GlowSourceWidth = 48
private const val GlowSourceHeight = 72

// Hero cover width (spec: 112x168); the glow decodes at the same size so MangaDex
// variant selection and the memory cache line up with the hero's own request.
internal val DetailsCoverWidth = 112.dp
private const val GlowSaturation = 1.15f
private val GlowTail = 200.dp
private const val GlowFadeStartFraction = 0.55f
private const val GlowAlphaDark = 0.40f
private const val GlowAlphaLight = 0.28f
private val GlowPopSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 250f)

class DetailsGlow internal constructor() {
    val bitmap = mutableStateOf<ImageBitmap?>(null)
    val accent = mutableStateOf<Color?>(null)
    val popIn = Animatable(0f)
    val scale = Animatable(1f)
    internal var settled = false
}

private class GlowEntry(val bitmap: ImageBitmap, val accent: Color?, val card: Color?)

private val glowCache = android.util.LruCache<Long, GlowEntry>(48)

private fun computeGlow(source: Bitmap): GlowEntry {
    val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    return GlowEntry(processGlowSource(readable).asImageBitmap(), dominantAccent(readable), DetailsCardColor.fromBitmap(readable))
}

private fun adoptCard(mangaId: Long, entry: GlowEntry) {
    val card = entry.card ?: return
    if (dev.errnolink.tsuzuki.ui.shell.MangaHero.cardColor(mangaId) == null) {
        dev.errnolink.tsuzuki.ui.shell.MangaHero.reportCardColor(mangaId, card)
    }
}

@Composable
fun rememberDetailsGlow(manga: Manga, coverReady: Boolean): DetailsGlow {
    val glow = remember(manga.id) {
        DetailsGlow().also { fresh ->
            glowCache.get(manga.id)?.let { entry ->
                fresh.bitmap.value = entry.bitmap
                fresh.accent.value = entry.accent
                fresh.settled = true
                adoptCard(manga.id, entry)
            }
        }
    }
    val context = LocalContext.current
    val reduced = TsuzukiTheme.motion.reduced
    // Same resolved size as the hero's cover box so MangaDex variant selection
    // picks the same URL and the decode comes from cache, never the network.
    val coverWidthPx = with(LocalDensity.current) { DetailsCoverWidth.roundToPx() }
    val coverKey = manga.coverLastModified to manga.thumbnailUrl
    LaunchedEffect(manga.id) {
        if (glow.bitmap.value != null) return@LaunchedEffect
        val hero = dev.errnolink.tsuzuki.ui.shell.MangaHero.source(manga.id)?.image ?: return@LaunchedEffect
        val entry = withContext(Dispatchers.Default) { runCatching { computeGlow(hero.asAndroidBitmap()) }.getOrNull() }
        if (entry != null && glow.bitmap.value == null) {
            glow.settled = true
            glow.accent.value = entry.accent
            glow.bitmap.value = entry.bitmap
            adoptCard(manga.id, entry)
        }
    }
    LaunchedEffect(coverKey, coverReady) {
        if (!coverReady) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(manga.asMangaCover())
            .size(coverWidthPx, (coverWidthPx * 3f / 2f).roundToInt())
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()
        val source = context.imageLoader.execute(request).image
            ?.asDrawable(context.resources)
            ?.getBitmapOrNull()
        val entry = source?.let { withContext(Dispatchers.Default) { computeGlow(it) } }
        if (entry == null) {
            if (glow.bitmap.value == null) glow.accent.value = null
        } else {
            glowCache.put(manga.id, entry)
            glow.accent.value = entry.accent
            glow.bitmap.value = entry.bitmap
            adoptCard(manga.id, entry)
        }
    }
    LaunchedEffect(glow.bitmap.value != null, reduced) {
        if (glow.bitmap.value == null) {
            glow.scale.snapTo(1f)
            glow.popIn.snapTo(0f)
        } else if (reduced || glow.settled) {
            glow.scale.snapTo(1f)
            glow.popIn.snapTo(1f)
        } else {
            glow.scale.snapTo(1.08f)
            glow.popIn.snapTo(0f)
            launch { glow.popIn.animateTo(1f, GlowPopSpec) }
            glow.scale.animateTo(1f, GlowPopSpec)
        }
    }
    return glow
}

fun Modifier.detailsHeroGlow(
    glow: DetailsGlow,
    topInset: Dp,
    isDark: Boolean,
    backgroundColor: Color,
    strength: Float = 1f,
): Modifier = drawWithCache {
    val baseAlpha = (if (isDark) GlowAlphaDark else GlowAlphaLight) * strength
    val topPx = topInset.roundToPx()
    val glowTop = -topPx.toFloat()
    val glowHeight = topPx.toFloat() + size.height + GlowTail.roundToPx()
    val glowBottom = glowTop + glowHeight
    val fadeTop = glowTop + glowHeight * GlowFadeStartFraction
    val fadeBrush = Brush.verticalGradient(
        0f to backgroundColor.copy(alpha = 0f),
        1f to backgroundColor,
        startY = fadeTop,
        endY = glowBottom,
    )
    onDrawBehind {
        val bitmap = glow.bitmap.value ?: return@onDrawBehind
        val pop = glow.popIn.value
        if (pop <= 0.005f) return@onDrawBehind
        val zoom = glow.scale.value
        val fit = max(size.width / bitmap.width, glowHeight / bitmap.height)
        val dstWidth = (bitmap.width * fit * zoom).roundToInt()
        val dstHeight = (bitmap.height * fit * zoom).roundToInt()
        val left = ((size.width - dstWidth) / 2f).roundToInt()
        val top = (glowTop + (glowHeight - dstHeight) / 2f).roundToInt()
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(dstWidth, dstHeight),
            alpha = pop * baseAlpha,
            filterQuality = FilterQuality.High,
        )
        drawRect(brush = fadeBrush, topLeft = Offset(0f, fadeTop), size = Size(size.width, glowBottom - fadeTop))
        val overshoot = top + dstHeight - glowBottom
        if (overshoot > 0f) drawRect(color = backgroundColor, topLeft = Offset(0f, glowBottom), size = Size(size.width, overshoot))
    }
}

private fun processGlowSource(source: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(GlowSourceWidth, GlowSourceHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val fit = max(GlowSourceWidth / source.width.toFloat(), GlowSourceHeight / source.height.toFloat())
    val srcW = GlowSourceWidth / fit
    val srcH = GlowSourceHeight / fit
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(GlowSaturation) })
    }
    canvas.drawBitmap(
        source,
        Rect(
            (source.width - srcW).roundToInt() / 2,
            (source.height - srcH).roundToInt() / 2,
            (source.width + srcW).roundToInt() / 2,
            (source.height + srcH).roundToInt() / 2,
        ),
        Rect(0, 0, GlowSourceWidth, GlowSourceHeight),
        paint,
    )
    boxBlur(out, GlowSourceBlurRadius, passes = 3)
    return out
}

private const val GlowSourceBlurRadius = 6

private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    var src = IntArray(w * h)
    var dst = IntArray(w * h)
    bitmap.getPixels(src, 0, w, 0, 0, w, h)
    repeat(passes) {
        blurLine(src, dst, w, h, radius, horizontal = true)
        blurLine(dst, src, w, h, radius, horizontal = false)
    }
    bitmap.setPixels(src, 0, w, 0, 0, w, h)
}

private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until length) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, length - 1)
                val p = if (horizontal) src[line * w + j] else src[j * w + line]
                a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            val n = radius * 2 + 1
            val out = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
        }
    }
}

private fun dominantAccent(source: Bitmap): Color? {
    val palette = Palette.from(source).maximumColorCount(24).generate()
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
        ?: return null
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(swatch.rgb, hsv)
    if (hsv[1] < 0.15f) return null
    hsv[1] = hsv[1].coerceIn(0.55f, 0.9f)
    hsv[2] = hsv[2].coerceIn(0.5f, 0.62f)
    return Color.hsv(hsv[0], hsv[1], hsv[2])
}
