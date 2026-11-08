package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

@Immutable
data class TsuzukiColors(
    val background: Color,
    val grouped: Color,
    val elevated: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    val selectedFill: Color,
    val tabTint: Color,
    val glassTint: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val success: Color,
    val isDark: Boolean,
)

internal fun tsuzukiColors(dark: Boolean, accent: Color? = null) = if (dark) {
    TsuzukiColors(
        background = Color(0xFF101114), grouped = Color(0xFF1C1D21), elevated = Color(0xFF292A30),
        text = Color(0xFFF5F5F7), secondary = Color(0xFFB8B8C2), separator = Color(0xFF3C3D44),
        selectedFill = Color(0xFF505159), tabTint = Color(0xFF0091FF), glassTint = Color(0xFF24252B),
        accent = accent ?: Color(0xFF8BB9FF), onAccent = Color(0xFF081B36),
        destructive = Color(0xFFFF8B86), success = Color(0xFF7CDBA3), isDark = true,
    )
} else {
    TsuzukiColors(
        background = Color(0xFFF2F2F7), grouped = Color.White, elevated = Color(0xFFF9F9FC),
        text = Color(0xFF191A20), secondary = Color(0xFF62636E), separator = Color(0xFFDADAE2),
        selectedFill = Color(0xFFDADAE0), tabTint = Color(0xFF0088FF), glassTint = Color(0xFFF6F6FA),
        accent = accent ?: Color(0xFF205DC2), onAccent = Color.White,
        destructive = Color(0xFFBC2925), success = Color(0xFF1E6B39), isDark = false,
    )
}

fun tsuzukiCardColors(card: Color): TsuzukiColors {
    val dark = tsuzukiColors(true)
    fun mix(amount: Float) = androidx.compose.ui.graphics.lerp(card, Color.White, amount)
    val max = maxOf(card.red, card.green, card.blue)
    val min = minOf(card.red, card.green, card.blue)
    val delta = max - min
    val hueDegrees = when {
        delta == 0f -> 0f
        max == card.red -> 60f * (((card.green - card.blue) / delta).mod(6f))
        max == card.green -> 60f * ((card.blue - card.red) / delta + 2f)
        else -> 60f * ((card.red - card.green) / delta + 4f)
    }
    val hue = Color.hsv(hueDegrees.coerceIn(0f, 359.99f), if (max == 0f) 0f else delta / max, 1f)
    return TsuzukiColors(
        background = card,
        grouped = mix(0.07f),
        elevated = mix(0.13f),
        text = dark.text,
        secondary = mix(0.86f),
        separator = mix(0.2f),
        selectedFill = readableUnder(mix(0.1f), dark.text),
        tabTint = dark.tabTint,
        glassTint = mix(0.1f),
        accent = androidx.compose.ui.graphics.lerp(hue, Color.White, 0.86f),
        onAccent = card,
        destructive = dark.destructive,
        success = dark.success,
        isDark = true,
    )
}

private fun readableUnder(fill: Color, text: Color, minContrast: Float = 4.5f): Color {
    val textLum = text.luminance()
    var candidate = fill
    repeat(24) {
        if ((textLum + 0.05f) / (candidate.luminance() + 0.05f) >= minContrast) return candidate
        candidate = Color(candidate.red * 0.94f, candidate.green * 0.94f, candidate.blue * 0.94f, candidate.alpha)
    }
    return candidate
}

fun lerpColors(from: TsuzukiColors, to: TsuzukiColors, fraction: Float): TsuzukiColors {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return from
    if (f >= 1f) return to
    fun l(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, f)
    return TsuzukiColors(
        background = l(from.background, to.background),
        grouped = l(from.grouped, to.grouped),
        elevated = l(from.elevated, to.elevated),
        text = l(from.text, to.text),
        secondary = l(from.secondary, to.secondary),
        separator = l(from.separator, to.separator),
        selectedFill = l(from.selectedFill, to.selectedFill),
        tabTint = l(from.tabTint, to.tabTint),
        glassTint = l(from.glassTint, to.glassTint),
        accent = l(from.accent, to.accent),
        onAccent = l(from.onAccent, to.onAccent),
        destructive = l(from.destructive, to.destructive),
        success = l(from.success, to.success),
        isDark = if (f < 0.5f) from.isDark else to.isDark,
    )
}

object BrandColors {
    val mangaDex = Color(0xFFFF6740)
}

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Immutable
class TsuzukiTypography(private val font: FontFamily = Inter) {
    private fun role(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
        TextStyle(
            fontFamily = font,
            fontSize = size.sp,
            lineHeight = height.sp,
            fontWeight = weight,
            letterSpacing = tracking.sp,
        )

    val largeTitle = role(34, 41, FontWeight.Bold, tracking = -0.8)
    val title1 = role(28, 34, FontWeight.Bold, tracking = -0.6)
    val title2 = role(22, 28, FontWeight.SemiBold, tracking = -0.4)
    val title3 = role(20, 25, FontWeight.SemiBold, tracking = -0.3)
    val headline = role(17, 22, FontWeight.SemiBold, tracking = -0.25)
    val body = role(17, 24)
    val callout = role(16, 21)
    val subhead = role(15, 20)
    val footnote = role(13, 18)
    val sectionHeader = role(13, 18, FontWeight.SemiBold)
    val caption1 = role(12, 16)
    val caption2 = role(11, 14)
    val tabLabel = role(10, 12, FontWeight.SemiBold, tracking = -0.1)
}

@Immutable
object TsuzukiSpacing {
    val hairline = 0.5.dp
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val row = 16.dp
    val gutter = 20.dp
    val section = 24.dp
    val large = 32.dp
    val touchTarget = 48.dp
}

@Immutable
object TsuzukiCorners {
    val cover = 12.dp
    val field = 14.dp
    val group = 20.dp
    val sheet = 34.dp
    val capsulePercent = 50
}

@Immutable
data class TsuzukiMotion(val reduced: Boolean = false) {
    val pressScale = 0.97f
    val pressMillis = 80
    val coverPressScale = 0.985f
    val coverPressMillis = 90
    val coverReleaseMillis = 120
    val coverArrivalMillis = 120
    val chromeFadeMillis = 160
    val pressStiffness = 420f
    val pressDamping = 34f
    val pressMass = 0.7f
    val tabStiffness = 400f
    val tabDamping = 38f
    val sheetStiffness = 500f
    val sheetDamping = 45f

    fun <T> pressSpring(): SpringSpec<T> = physicalSpring(pressStiffness, pressDamping, pressMass)
    fun <T> tabSpring(): SpringSpec<T> = physicalSpring(tabStiffness, tabDamping)
    fun <T> sheetSpring(): SpringSpec<T> = physicalSpring(sheetStiffness, sheetDamping)

    fun chromeFade(): FiniteAnimationSpec<Float> = tween(M3Motion.Short3, easing = M3Motion.Standard)

    private fun <T> physicalSpring(stiffness: Float, damping: Float, mass: Float = 1f): SpringSpec<T> =
        spring(stiffness = stiffness / mass, dampingRatio = damping / (2f * sqrt(stiffness * mass)))
}
