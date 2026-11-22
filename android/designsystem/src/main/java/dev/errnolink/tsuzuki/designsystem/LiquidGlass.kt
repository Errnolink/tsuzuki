package dev.errnolink.tsuzuki.designsystem

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import kotlin.math.min

/* Liquid glass with a moving outline (iOS 26, uikit.even.how): the same Kasane face as KasaneGlass,
   but the shape is a signed-distance field of up to two rounded boxes merged with a smooth union,
   so a drop leaving a button grows a liquid neck, and the glass can magnify what is under it (the
   held selection lens). The pass writes its own antialiased coverage: draw it unclipped. */

/** A rounded box by centre, half size and corner radius (px). Empty when either half is under 0.5 px. */
@Immutable
data class GlassBox(
    val cx: Float,
    val cy: Float,
    val halfW: Float,
    val halfH: Float,
    val radius: Float,
) {
    val isEmpty: Boolean get() = halfW < 0.5f || halfH < 0.5f
    val top: Float get() = cy - halfH
    val bottom: Float get() = cy + halfH
    val left: Float get() = cx - halfW
    val right: Float get() = cx + halfW

    fun toRoundRect(): RoundRect {
        val r = radius.coerceIn(0f, min(halfW, halfH))
        return RoundRect(left, top, right, bottom, CornerRadius(r))
    }

    companion object {
        val Empty = GlassBox(0f, 0f, 0f, 0f, 0f)

        fun of(rect: Rect, radius: Float) = GlassBox(rect.center.x, rect.center.y, rect.width / 2f, rect.height / 2f, radius)
    }
}

/**
 * Smooth-union width for [a] and [b]: zero while [b] sits wholly inside [a] (so the union is
 * exactly [a], no bulge on the first and last frames), growing to [mergePx] as [b] pokes out.
 */
fun liquidMergeFor(a: GlassBox, b: GlassBox, mergePx: Float): Float {
    if (a.isEmpty || b.isEmpty || mergePx <= 0f) return 0f
    val out = maxOf(0f, b.bottom - a.bottom, a.top - b.top, b.right - a.right, a.left - b.left)
    return mergePx * (out / mergePx).coerceIn(0f, 1f)
}

/** One frame of liquid glass: the boxes, their merge width and the magnification about [zoomCenter]. */
@Immutable
data class LiquidGlassGeometry(
    val a: GlassBox,
    val b: GlassBox,
    val merge: Float,
    val zoom: Float = 1f,
    val zoomCenter: Offset = Offset.Zero,
) {
    /** The union of both boxes without the neck, for clips and the fallback paths. */
    fun outline(): Path {
        val pa = Path().apply { if (!a.isEmpty) addRoundRect(a.toRoundRect()) }
        val pb = Path().apply { if (!b.isEmpty) addRoundRect(b.toRoundRect()) }
        return when {
            a.isEmpty -> pb
            b.isEmpty -> pa
            else -> Path().apply { op(pa, pb, PathOperation.Union) }
        }
    }
}

/** Clip / shadow shape of one liquid frame: the two boxes' union, in the layout's own coordinates. */
data class LiquidGlassShape(private val geometry: LiquidGlassGeometry) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(geometry.outline())
}

private const val LiquidGlassShader = """
uniform shader content;
uniform float2 offset;
uniform float2 layerSize;
uniform float4 boxA;
uniform float radiusA;
uniform float4 boxB;
uniform float radiusB;
uniform float merge;
uniform float zoom;
uniform float2 zoomCenter;
uniform float innerAmount;
uniform float innerHeight;
uniform float outerAmount;
uniform float outerHeight;
uniform float refractionOpacity;
uniform float rimBand;
uniform float4 faceRow0;
uniform float4 faceRow1;
uniform float4 faceRow2;
uniform float lumaComplement;
uniform float2 highlightDir;
uniform float highlightSpread;
uniform float highlightAmount;
uniform float highlightWidth;
uniform float highlightStrength;
uniform half4 tint;
uniform half4 page;

float sdBox(float2 p, float4 box, float r) {
    if (box.z < 0.5 || box.w < 0.5) {
        return 100000.0;
    }
    float rr = min(r, min(box.z, box.w));
    float2 q = abs(p - box.xy) - box.zw + rr;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rr;
}

float scene(float2 p) {
    float a = sdBox(p, boxA, radiusA);
    float b = sdBox(p, boxB, radiusB);
    if (merge <= 0.001) {
        return min(a, b);
    }
    float h = clamp(0.5 + 0.5 * (b - a) / merge, 0.0, 1.0);
    return mix(b, a, h) - merge * h * (1.0 - h);
}

float profile(float inside, float amount, float height) {
    float s = clamp(inside / height, 0.0, 1.0);
    return amount - sqrt(clamp((2.0 - s) * s, 0.0, 1.0)) * amount;
}

half3 lumaLift(half3 c, float complement) {
    float luma = dot(float3(0.2126, 0.7152, 0.0722), float3(c));
    float s = clamp(1.0 - luma * complement, 0.0, 1.0);
    return half3(mix(float3(s * luma), float3(s) * float3(c), float3((1.0 - s) * 0.3 + 1.0)));
}

half3 faceMatrix(half3 c) {
    float3 f = float3(c);
    return half3(
        dot(f, faceRow0.xyz) + faceRow0.w,
        dot(f, faceRow1.xyz) + faceRow1.w,
        dot(f, faceRow2.xyz) + faceRow2.w
    );
}

half4 sampleAt(float2 c) {
    return content.eval(clamp(c, float2(0.5), layerSize - 0.5));
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float d = scene(p);
    if (d > 1.0) {
        return half4(0.0);
    }
    float2 g = float2(scene(p + float2(1.0, 0.0)) - scene(p - float2(1.0, 0.0)),
                      scene(p + float2(0.0, 1.0)) - scene(p - float2(0.0, 1.0)));
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0);
    float inside = max(-d, 0.0);

    float2 zoomed = zoomCenter + (p - zoomCenter) / max(zoom, 0.01) - offset;
    float inner = profile(inside, innerAmount, innerHeight);
    half4 face = sampleAt(zoomed + inner * n);
    float rim = clamp((-inside + rimBand) / rimBand, 0.0, 1.0);
    if (rim > 0.0) {
        float outer = profile(inside, outerAmount, outerHeight);
        half4 outerRead = sampleAt(zoomed + outer * n);
        face = mix(face, outerRead, half(refractionOpacity * rim));
    }
    half3 rgb = face.rgb + page.rgb * (1.0 - face.a);
    rgb = clamp(faceMatrix(lumaLift(rgb, lumaComplement)), half3(0.0), half3(1.0));
    rgb = mix(rgb, tint.rgb, tint.a);

    float band = clamp(1.0 - inside / highlightWidth, 0.0, 1.0);
    float cosine = dot(highlightDir, n);
    float2 lobes = clamp((float2(cosine, -cosine) - highlightSpread) / (1.0 - highlightSpread), 0.0, 1.0) * band;
    float2 response = lobes / (highlightAmount * (1.0 - lobes) + 1.0);
    float light = (response.x + response.y) * highlightStrength;
    rgb = mix(rgb, half3(1.0), half(clamp(light, 0.0, 1.0)));

    float coverage = clamp(0.5 - d, 0.0, 1.0);
    return half4(rgb * half(coverage), half(coverage));
}
"""

/** Identity face: no lift, no saturation (clear glass, the held selection lens). */
private val ClearFace = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
)

/**
 * Liquid glass inside a backdrop effect scope (draw it with a rectangle shape: the pass writes its
 * own coverage). [geometry] is in the drawing node's own coordinates.
 *
 * [clear]: the held selection lens (no lift, no frost, a faint [tint]); otherwise the Kasane face
 * of [KasaneGlassSpec] with [blurRadius] of frost.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun BackdropEffectScope.liquidGlass(
    geometry: LiquidGlassGeometry,
    dark: Boolean,
    tint: Color,
    page: Color,
    highlightStrength: Float,
    blurRadius: Dp = KasaneGlassSpec.BlurRadius,
    clear: Boolean = false,
    refractionHeight: Dp = 14.dp,
    outerAmount: Dp = KasaneGlassSpec.OuterAmount,
) {
    val spec = KasaneGlassSpec
    val outerPx = outerAmount.toPx()
    val blurPx = blurRadius.toPx()
    val reach = kotlin.math.ceil(outerPx + blurPx * 2f)
    if (padding < reach) padding = reach
    if (blurPx >= 0.5f) blur(blurPx)
    var minHalf = Float.MAX_VALUE
    for (box in arrayOf(geometry.a, geometry.b)) {
        if (!box.isEmpty) minHalf = min(minHalf, min(box.halfW, box.halfH))
    }
    if (minHalf == Float.MAX_VALUE) minHalf = 0f
    val height = min(refractionHeight.toPx(), minHalf).coerceAtLeast(0.001f)
    val shader = obtainRuntimeShader("LiquidGlass", LiquidGlassShader)
    val face = when {
        clear -> ClearFace
        dark -> spec.FaceMatrixDark
        else -> spec.FaceMatrixLight
    }
    val a = geometry.a
    val b = geometry.b
    shader.setFloatUniform("offset", -padding, -padding)
    shader.setFloatUniform("layerSize", size.width + 2f * padding, size.height + 2f * padding)
    shader.setFloatUniform("boxA", a.cx, a.cy, a.halfW, a.halfH)
    shader.setFloatUniform("radiusA", a.radius)
    shader.setFloatUniform("boxB", b.cx, b.cy, b.halfW, b.halfH)
    shader.setFloatUniform("radiusB", b.radius)
    shader.setFloatUniform("merge", geometry.merge)
    shader.setFloatUniform("zoom", geometry.zoom)
    shader.setFloatUniform("zoomCenter", geometry.zoomCenter.x, geometry.zoomCenter.y)
    shader.setFloatUniform("innerAmount", if (clear) outerPx else spec.InnerAmountPerHeight * height)
    shader.setFloatUniform("innerHeight", height)
    shader.setFloatUniform("outerAmount", outerPx)
    shader.setFloatUniform("outerHeight", spec.OuterHeight.toPx())
    shader.setFloatUniform("refractionOpacity", spec.RefractionOpacity)
    shader.setFloatUniform("rimBand", spec.RimBand.toPx())
    shader.setFloatUniform("faceRow0", face[0], face[1], face[2], face[3])
    shader.setFloatUniform("faceRow1", face[4], face[5], face[6], face[7])
    shader.setFloatUniform("faceRow2", face[8], face[9], face[10], face[11])
    shader.setFloatUniform(
        "lumaComplement",
        when {
            clear -> 0f
            dark -> spec.LumaComplementDark
            else -> spec.LumaComplementLight
        },
    )
    shader.setFloatUniform("highlightDir", 1f, 0f)
    shader.setFloatUniform("highlightSpread", spec.HighlightSpread)
    shader.setFloatUniform("highlightAmount", spec.HighlightAmount)
    shader.setFloatUniform("highlightWidth", spec.HighlightWidth.toPx())
    shader.setFloatUniform("highlightStrength", highlightStrength)
    shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    shader.setFloatUniform("page", page.red, page.green, page.blue, 1f)
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}
