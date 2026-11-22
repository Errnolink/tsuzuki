package dev.errnolink.tsuzuki.designsystem

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect

/**
 * Kasane Liquid Glass: one AGSL pass over the blurred backdrop, after the iOS 26 glass model
 * (research: `docs/research/uikit-gallery/README.md`). Per pixel it takes the shape's signed
 * distance and outward normal, then:
 * - refracts: reads the backdrop along the normal with a circular-arc profile, strongest at the
 *   rim and zero `height` inside it (inner layer pulls inward; a thin outer layer at the rim
 *   reads from outside the shape);
 * - lifts the face colour with a luma-complement step and a saturation matrix with a small
 *   offset, instead of a flat grey tint;
 * - lights the rim with two light lobes along [KasaneGlassSpec.highlightDirection].
 */
internal object KasaneGlassSpec {
    /** Refraction height: half the short side, at most 14 dp (iOS: 14 on a 28 pt capsule, 11 on a 22 pt thumb). */
    fun refractionHeight(shortSidePx: Float, density: Density): Float =
        minOf(shortSidePx / 2f, with(density) { 14.dp.toPx() })

    const val InnerAmountPerHeight = -2f
    val OuterAmount = 16.dp
    val OuterHeight = 16.dp
    const val RefractionOpacity = 0.6f
    val RimBand = 1.dp
    val BlurRadius = 6.dp

    /** Face matrix rows (r, g, b, offset): +24 % saturation with a 0.125 lift (iOS Regular glass, dark). */
    val FaceMatrixDark = floatArrayOf(
        1.2363f, -0.2146f, -0.0216f, 0.125f,
        -0.0638f, 1.0859f, -0.0217f, 0.125f,
        -0.0638f, -0.2145f, 1.2783f, 0.125f,
    )

    /** Light glass keeps the saturation and lifts toward white less; the tint carries the rest. */
    val FaceMatrixLight = floatArrayOf(
        1.2363f, -0.2146f, -0.0216f, 0.06f,
        -0.0638f, 1.0859f, -0.0217f, 0.06f,
        -0.0638f, -0.2145f, 1.2783f, 0.06f,
    )
    /** Dark glass dims bright backdrops so white glyphs stay legible; light glass must not grey a white page. */
    const val LumaComplementDark = 0.4f
    const val LumaComplementLight = 0f
    const val HighlightSpread = 0.2588f
    const val HighlightAmount = 0.5f
    val HighlightWidth = 1.5.dp
}

private const val KasaneGlassShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float2 layerSize;
uniform float4 radii;
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

float cornerRadius(float2 p, float4 r) {
    if (p.x >= 0.0) { return p.y <= 0.0 ? r.y : r.z; }
    return p.y <= 0.0 ? r.x : r.w;
}

float3 sdf(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - (halfSize - float2(radius));
    float outside = length(max(q, 0.0)) - radius;
    float inside = min(max(q.x, q.y), 0.0);
    float2 n;
    if (q.x >= 0.0 || q.y >= 0.0) {
        n = sign(p) * normalize(max(q, float2(1e-4)));
    } else {
        float gx = step(q.y, q.x);
        n = sign(p) * float2(gx, 1.0 - gx);
    }
    return float3(outside + inside, n);
}

float profile(float inside, float amount, float height) {
    float s = clamp(inside / max(height, 1e-4), 0.0, 1.0);
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
    float2 hi = max(layerSize - float2(0.5), float2(0.5));
    return content.eval(clamp(c, float2(0.5), hi));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord + offset - halfSize;
    float radius = min(cornerRadius(p, radii), min(halfSize.x, halfSize.y));
    float3 field = sdf(p, halfSize, radius);
    float d = field.x;
    float2 n = field.yz;
    float inside = -d;

    float inner = profile(inside, innerAmount, innerHeight);
    half4 face = sampleAt(coord + inner * n);
    float rim = clamp((d + rimBand) / rimBand, 0.0, 1.0);
    if (rim > 0.0) {
        float outer = profile(inside, outerAmount, outerHeight);
        half4 outerRead = sampleAt(coord + outer * n);
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
    return half4(rgb, 1.0);
}
"""

/** Corner radii (top-left, top-right, bottom-right, bottom-left) in px, or null when the shape has no SDF here. */
/** A shape whose outline is always a rounded rectangle, so the Kasane glass can take its radii from it. */
internal interface RoundedRectShape : Shape

internal fun glassCornerRadii(shape: Shape, size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray? {
    if (shape is RoundedRectShape) {
        val outline = shape.createOutline(size, layoutDirection, density)
        val max = size.minDimension / 2f
        return when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded -> outline.roundRect.let {
                floatArrayOf(
                    it.topLeftCornerRadius.x.coerceAtMost(max),
                    it.topRightCornerRadius.x.coerceAtMost(max),
                    it.bottomRightCornerRadius.x.coerceAtMost(max),
                    it.bottomLeftCornerRadius.x.coerceAtMost(max),
                )
            }
            is androidx.compose.ui.graphics.Outline.Rectangle -> floatArrayOf(0f, 0f, 0f, 0f)
            else -> null
        }
    }
    if (shape !is CornerBasedShape) return null
    val max = size.minDimension / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr
    val tl = (if (ltr) shape.topStart else shape.topEnd).toPx(size, density)
    val tr = (if (ltr) shape.topEnd else shape.topStart).toPx(size, density)
    val br = (if (ltr) shape.bottomEnd else shape.bottomStart).toPx(size, density)
    val bl = (if (ltr) shape.bottomStart else shape.bottomEnd).toPx(size, density)
    return floatArrayOf(tl.coerceAtMost(max), tr.coerceAtMost(max), br.coerceAtMost(max), bl.coerceAtMost(max))
}

/**
 * Applies Kasane glass inside a backdrop effect scope: a light blur, then the glass pass.
 * [tint] carries the remaining legibility tint (alpha 0 = clear glass).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun BackdropEffectScope.kasaneGlass(
    radii: FloatArray,
    dark: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    page: androidx.compose.ui.graphics.Color,
    highlightStrength: Float,
    blurRadius: androidx.compose.ui.unit.Dp = KasaneGlassSpec.BlurRadius,
) {
    // Zero and sub-pixel layers make the shader's height and sampling undefined (NaN on Adreno).
    if (size.minDimension <= 0.5f) return
    val spec = KasaneGlassSpec
    val outerPx = spec.OuterAmount.toPx()
    val reach = kotlin.math.ceil(outerPx + blurRadius.toPx() * 2f)
    if (padding < reach) padding = reach
    blur(blurRadius.toPx())
    val height = spec.refractionHeight(size.minDimension, this)
    val shader: RuntimeShader = obtainRuntimeShader("KasaneGlass", KasaneGlassShader)
    val face = if (dark) spec.FaceMatrixDark else spec.FaceMatrixLight
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -padding, -padding)
    shader.setFloatUniform("layerSize", size.width + 2f * padding, size.height + 2f * padding)
    shader.setFloatUniform("radii", radii)
    shader.setFloatUniform("innerAmount", spec.InnerAmountPerHeight * height)
    shader.setFloatUniform("innerHeight", height)
    shader.setFloatUniform("outerAmount", outerPx)
    shader.setFloatUniform("outerHeight", spec.OuterHeight.toPx())
    shader.setFloatUniform("refractionOpacity", spec.RefractionOpacity)
    shader.setFloatUniform("rimBand", spec.RimBand.toPx())
    shader.setFloatUniform("faceRow0", face[0], face[1], face[2], face[3])
    shader.setFloatUniform("faceRow1", face[4], face[5], face[6], face[7])
    shader.setFloatUniform("faceRow2", face[8], face[9], face[10], face[11])
    shader.setFloatUniform("lumaComplement", if (dark) spec.LumaComplementDark else spec.LumaComplementLight)
    shader.setFloatUniform("highlightDir", 1f, 0f)
    shader.setFloatUniform("highlightSpread", spec.HighlightSpread)
    shader.setFloatUniform("highlightAmount", spec.HighlightAmount)
    shader.setFloatUniform("highlightWidth", spec.HighlightWidth.toPx())
    shader.setFloatUniform("highlightStrength", highlightStrength)
    shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    shader.setFloatUniform("page", page.red, page.green, page.blue, 1f)
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}
