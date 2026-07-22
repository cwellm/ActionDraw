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

/** Warm monochrome sepia tone — partial desaturation with a warm cast, reduces colour distraction. */
fun sepiaFilter(): ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

/** Warm white-balance shift (boost red, cut blue) — practice drawing under warm light. */
fun warmFilter(): ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.10f, 0f, 0f, 0f, 0f,
                0f, 1.00f, 0f, 0f, 0f,
                0f, 0f, 0.82f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

/** Cool white-balance shift (boost blue, cut red) — practice drawing under cool light. */
fun coolFilter(): ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.82f, 0f, 0f, 0f, 0f,
                0f, 1.00f, 0f, 0f, 0f,
                0f, 0f, 1.12f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

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

/** Posterize: collapse each channel to a few value bands, training value grouping. */
internal const val POSTERIZE_SKSL = """
uniform shader content;
uniform float levels;

half4 main(float2 coord) {
    half4 px = content.eval(coord);
    float n = max(levels - 1.0, 1.0);
    half3 q = half3(floor(float3(px.rgb) * n + 0.5) / n);
    return half4(q, 1.0) * px.a;
}
"""

/** Pixelate: snap to coarse blocks to force big-shape thinking and ignore detail. */
internal const val PIXELATE_SKSL = """
uniform shader content;
uniform float block;

half4 main(float2 coord) {
    float2 c = (floor(coord / block) + 0.5) * block;
    return content.eval(c);
}
"""

/** Notan: collapse luminance to two or three flat values — the classic value-grouping study. */
internal const val NOTAN_SKSL = """
uniform shader content;
uniform float bands;
uniform float threshold;

half4 main(float2 coord) {
    half4 px = content.eval(coord);
    float l = dot(float3(px.rgb), float3(0.299, 0.587, 0.114));
    float v;
    if (bands > 2.5) {
        v = l < threshold - 0.1667 ? 0.0 : (l < threshold + 0.1667 ? 0.5 : 1.0);
    } else {
        v = step(threshold, l);
    }
    half hv = half(v);
    return half4(hv, hv, hv, 1.0) * px.a;
}
"""

/**
 * Cubist "defraction": a jittered-Voronoi mosaic of irregular shards, each shard sampling the
 * image with its own random offset and rotation. `seed` re-randomises the whole pattern.
 */
internal const val DEFRACTION_SKSL = """
uniform shader content;
uniform float seed;
uniform float block;
uniform float strength;

float2 hash2(float2 p) {
    float2 q = float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)));
    return fract(sin(q + seed) * 43758.5453);
}

half4 main(float2 coord) {
    // Nearest jittered site of the 3x3 neighbourhood -> irregular polygonal cells.
    float2 g = floor(coord / block);
    float2 bestCell = g;
    float2 bestSite = (g + 0.5) * block;
    float bd = 1e9;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float2 cell = g + float2(float(x), float(y));
            float2 site = (cell + hash2(cell)) * block;
            float d = distance(coord, site);
            if (d < bd) { bd = d; bestCell = cell; bestSite = site; }
        }
    }
    // Per-shard random rotation about its site plus a random offset.
    float2 h = hash2(bestCell + 71.3);
    float a = (h.x - 0.5) * strength;
    float s = sin(a);
    float c = cos(a);
    float2 rel = coord - bestSite;
    float2 rot = float2(rel.x * c - rel.y * s, rel.x * s + rel.y * c);
    float2 off = (h - 0.5) * 1.6 * strength * block;
    return content.eval(bestSite + rot + off);
}
"""

/** Invert the final colours; applied as the outermost layer so it composes with everything. */
internal const val INVERT_SKSL = """
uniform shader content;

half4 main(float2 coord) {
    half4 px = content.eval(coord);
    return half4(half3(1.0) - px.rgb, px.a);
}
"""

// Each SkSL program is compiled once; per-call we only rebuild the (cheap) uniform bindings, so
// dragging a parameter slider never recompiles a shader.
private val edgeRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(EDGE_SKSL) }
private val silhouetteRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(SILHOUETTE_SKSL) }
private val posterizeRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(POSTERIZE_SKSL) }
private val pixelateRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(PIXELATE_SKSL) }
private val notanRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(NOTAN_SKSL) }
private val defractionRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(DEFRACTION_SKSL) }
private val invertRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(INVERT_SKSL) }

fun edgeRenderEffect(): RenderEffect = effectOf(edgeRuntime)

fun silhouetteRenderEffect(threshold: Float = 0.5f): RenderEffect =
    effectOf(silhouetteRuntime) { it.uniform("threshold", threshold) }

fun posterizeRenderEffect(levels: Int = 5): RenderEffect =
    effectOf(posterizeRuntime) { it.uniform("levels", levels.toFloat()) }

fun pixelateRenderEffect(block: Int = 8): RenderEffect =
    effectOf(pixelateRuntime) { it.uniform("block", block.toFloat()) }

fun notanRenderEffect(bands: Int = 2, threshold: Float = 0.5f): RenderEffect =
    effectOf(notanRuntime) {
        it.uniform("bands", bands.toFloat())
        it.uniform("threshold", threshold)
    }

fun defractionRenderEffect(seed: Float, block: Int = 96, strength: Float = 0.5f): RenderEffect =
    effectOf(defractionRuntime) {
        it.uniform("seed", seed)
        it.uniform("block", block.toFloat())
        it.uniform("strength", strength)
    }

fun invertRenderEffect(): RenderEffect = effectOf(invertRuntime)

/** Builds a Compose [RenderEffect] from a compiled shader that samples the layer as `content`. */
private fun effectOf(
    runtime: RuntimeEffect,
    configure: (RuntimeShaderBuilder) -> Unit = {},
): RenderEffect {
    val builder = RuntimeShaderBuilder(runtime).also(configure)
    return ImageFilter.makeRuntimeShader(builder, "content", null).asComposeRenderEffect()
}
