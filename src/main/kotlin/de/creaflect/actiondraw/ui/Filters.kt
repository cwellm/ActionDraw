package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Fully desaturated color filter for the black-and-white view. */
fun grayscaleFilter(): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * "Squint" view: low contrast + reduced saturation, so only the big value masses read — mimicking
 * squinting at the subject. Saturation is applied first, then contrast (matrix concatenation).
 */
fun squintFilter(): ColorFilter {
    val contrast = 0.45f
    val offset = (1f - contrast) * 127.5f // pivot contrast around mid-grey (0..255 scale)
    val matrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    matrix.timesAssign(ColorMatrix().apply { setToSaturation(0.6f) })
    return ColorFilter.colorMatrix(matrix)
}

/** Sobel edge detection: dark contour lines on a white ground, for line/contour study. */
internal const val EDGE_SKSL = """
uniform shader content;

float luma(float2 c) {
    half4 px = content.eval(c);
    return dot(float3(px.rgb), float3(0.299, 0.587, 0.114));
}

half4 main(float2 coord) {
    float tl = luma(coord + float2(-1.0, -1.0));
    float t  = luma(coord + float2( 0.0, -1.0));
    float tr = luma(coord + float2( 1.0, -1.0));
    float l  = luma(coord + float2(-1.0,  0.0));
    float r  = luma(coord + float2( 1.0,  0.0));
    float bl = luma(coord + float2(-1.0,  1.0));
    float b  = luma(coord + float2( 0.0,  1.0));
    float br = luma(coord + float2( 1.0,  1.0));
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    float g = clamp(sqrt(gx * gx + gy * gy), 0.0, 1.0);
    half e = half(1.0 - g);
    half a = content.eval(coord).a;
    return half4(e, e, e, 1.0) * a;
}
"""

/** Threshold to a flat black/white silhouette, for gesture and negative-space study. */
internal const val SILHOUETTE_SKSL = """
uniform shader content;
uniform float threshold;

half4 main(float2 coord) {
    half4 px = content.eval(coord);
    float l = dot(float3(px.rgb), float3(0.299, 0.587, 0.114));
    half v = half(step(threshold, l));
    return half4(v, v, v, 1.0) * px.a;
}
"""

private val edgeEffect: RenderEffect by lazy { runtimeShaderEffect(EDGE_SKSL) }
private val silhouetteEffect: RenderEffect by lazy {
    runtimeShaderEffect(SILHOUETTE_SKSL) { it.uniform("threshold", 0.5f) }
}

fun edgeRenderEffect(): RenderEffect = edgeEffect
fun silhouetteRenderEffect(): RenderEffect = silhouetteEffect

/** Builds a Compose [RenderEffect] from an SkSL shader that samples the layer content as `content`. */
private fun runtimeShaderEffect(
    sksl: String,
    configure: (RuntimeShaderBuilder) -> Unit = {},
): RenderEffect {
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(sksl)).also(configure)
    return ImageFilter.makeRuntimeShader(builder, "content", null).asComposeRenderEffect()
}
