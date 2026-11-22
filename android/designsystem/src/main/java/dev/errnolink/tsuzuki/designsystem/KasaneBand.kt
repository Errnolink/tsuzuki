package dev.errnolink.tsuzuki.designsystem

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect

/**
 * Kasane progressive blur (iOS 26 top and bottom edges): a full gaussian blur of the backdrop,
 * drawn with an alpha that fades along a smoothstep from the band's start to [toY], over the sharp
 * content. The fade reads as the blur thinning out; no sparse taps, so no streaks.
 */
internal object KasaneBandSpec {
    val Blur = 15.dp
}

private const val KasaneBandShader = """
uniform shader content;
uniform float pad;
uniform float fromY;
uniform float toY;
uniform half4 tint;
uniform half4 page;

half4 main(float2 coord) {
    float t = clamp((coord.y - pad - fromY) / max(toY - fromY, 1.0), 0.0, 1.0);
    float mask = 1.0 - t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    if (mask <= 0.0) { return half4(0.0); }
    half4 c = content.eval(coord);
    half3 rgb = mix(c.rgb + page.rgb * (1.0 - c.a), tint.rgb, tint.a * mask);
    half alpha = half(mask);
    return half4(rgb * alpha, alpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun BackdropEffectScope.kasaneBand(fromY: Float, toY: Float, tint: Color, page: Color) {
    blur(KasaneBandSpec.Blur.toPx())
    val shader = obtainRuntimeShader("KasaneBand", KasaneBandShader)
    shader.setFloatUniform("pad", padding)
    shader.setFloatUniform("fromY", fromY)
    shader.setFloatUniform("toY", toY)
    shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    shader.setFloatUniform("page", page.red, page.green, page.blue, 1f)
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}

private const val ProgressiveBandShader = """
uniform shader content;
uniform float2 layerSize;
uniform float pad;
uniform float fromY;
uniform float toY;
uniform float maxRadius;
uniform half4 tint;
uniform half4 page;

half4 sampleAt(float2 c) {
    return content.eval(clamp(c, float2(0.5), layerSize - 0.5));
}

half4 main(float2 coord) {
    float t = clamp((coord.y - pad - fromY) / max(toY - fromY, 1.0), 0.0, 1.0);
    float mask = 1.0 - t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    if (mask <= 0.001) { return half4(0.0); }
    float r = maxRadius * mask;
    half4 acc = half4(0.0);
    float wSum = 0.0;
    if (r < 0.75) {
        acc = sampleAt(coord);
        wSum = 1.0;
    } else {
        for (int i = 0; i < 24; i++) {
            float fi = float(i);
            float u = fi / 23.0;
            float rad = sqrt(u) * r;
            float ang = fi * 2.3999632;
            float2 d = float2(cos(ang), sin(ang)) * rad;
            float w = exp(-2.2 * u);
            acc += sampleAt(coord + d) * half(w);
            wSum += w;
        }
    }
    half4 c = acc / half(max(wSum, 0.0001));
    half3 rgb = mix(c.rgb + page.rgb * (1.0 - c.a), tint.rgb, tint.a * half(mask));
    half alpha = half(clamp(mask / 0.16, 0.0, 1.0));
    return half4(rgb * alpha, alpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun BackdropEffectScope.progressiveBand(fromY: Float, toY: Float, tint: Color, page: Color) {
    val preBlurPx = 2.dp.toPx()
    val maxRadiusPx = 16.dp.toPx()
    val reach = kotlin.math.ceil(maxRadiusPx + preBlurPx * 2f)
    if (padding < reach) padding = reach
    blur(preBlurPx)
    val shader = obtainRuntimeShader("ProgressiveBand", ProgressiveBandShader)
    shader.setFloatUniform("layerSize", size.width + 2f * padding, size.height + 2f * padding)
    shader.setFloatUniform("pad", padding)
    shader.setFloatUniform("fromY", fromY)
    shader.setFloatUniform("toY", toY)
    shader.setFloatUniform("maxRadius", maxRadiusPx)
    shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    shader.setFloatUniform("page", page.red, page.green, page.blue, 1f)
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}
