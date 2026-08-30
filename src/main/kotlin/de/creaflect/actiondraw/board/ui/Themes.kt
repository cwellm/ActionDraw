package de.creaflect.actiondraw.board.ui

import androidx.compose.material.Colors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.creaflect.actiondraw.board.BoardThemes
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/**
 * Board surfaces. Cork and papyrus are generated as seamless tiles from SkSL value noise (no
 * bundled image assets); `plain` keeps the app's dark look. Textured boards switch to a light
 * Material palette so text and controls stay readable on paper.
 */
object Themes {
    // Wrapped value noise: lattice coordinates are taken modulo `rep`, so a tile whose
    // coordinates span exactly `rep` lattice cells wraps seamlessly.
    internal const val CORK_SKSL = """
uniform float size;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(float2 p, float2 rep) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(mod(i, rep));
    float b = hash(mod(i + float2(1.0, 0.0), rep));
    float c = hash(mod(i + float2(0.0, 1.0), rep));
    float d = hash(mod(i + float2(1.0, 1.0), rep));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
    float2 p = xy / size * 8.0; // 8 lattice cells across -> the tile wraps seamlessly
    float n = vnoise(p, float2(8.0, 8.0)) * 0.5
            + vnoise(p * 2.0, float2(16.0, 16.0)) * 0.3
            + vnoise(p * 4.0, float2(32.0, 32.0)) * 0.2;
    float3 dark = float3(0.596, 0.435, 0.271);
    float3 light = float3(0.780, 0.612, 0.427);
    float3 col = mix(dark, light, n);
    float speck = vnoise(p * 6.0, float2(48.0, 48.0));
    if (speck > 0.84) col *= 0.76;                                      // dark cork grains
    if (speck < 0.05) col = mix(col, float3(0.874, 0.733, 0.557), 0.7); // light flecks
    return half4(half3(col), 1.0);
}
"""

    internal const val PAPYRUS_SKSL = """
uniform float size;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(float2 p, float2 rep) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(mod(i, rep));
    float b = hash(mod(i + float2(1.0, 0.0), rep));
    float c = hash(mod(i + float2(0.0, 1.0), rep));
    float d = hash(mod(i + float2(1.0, 1.0), rep));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
    float2 p = xy / size * 8.0;
    float grain = vnoise(p * 2.0, float2(16.0, 16.0));
    // Stretched noise reads as horizontal/vertical papyrus fibres.
    float horiz = vnoise(float2(p.x * 1.5, p.y * 24.0), float2(12.0, 192.0));
    float vert = vnoise(float2(p.x * 24.0, p.y * 1.5), float2(192.0, 12.0));
    float3 base = float3(0.925, 0.886, 0.769);
    float3 fibre = float3(0.816, 0.757, 0.604);
    float3 col = mix(base, fibre, horiz * 0.45 + vert * 0.2 + grain * 0.15);
    return half4(half3(col), 1.0);
}
"""

    private val tiles = mutableMapOf<String, ImageBitmap?>()

    /** Seamless background tile for [theme]; null = plain (solid app background). */
    fun tile(theme: String, size: Int = 256): ImageBitmap? = tiles.getOrPut(theme) {
        val sksl = when (theme) {
            BoardThemes.CORK -> CORK_SKSL
            BoardThemes.PAPYRUS -> PAPYRUS_SKSL
            else -> return@getOrPut null
        }
        runCatching { render(sksl, size) }.getOrNull()
    }

    private fun render(sksl: String, size: Int): ImageBitmap {
        val effect = RuntimeEffect.makeForShader(sksl)
        val builder = RuntimeShaderBuilder(effect).also { it.uniform("size", size.toFloat()) }
        val surface = Surface.makeRasterN32Premul(size, size)
        val paint = Paint().apply { shader = builder.makeShader(null) }
        surface.canvas.drawRect(Rect.makeWH(size.toFloat(), size.toFloat()), paint)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    fun isTextured(theme: String): Boolean = theme != BoardThemes.PLAIN

    /** Light Material palette for the textured (paper) boards. */
    val paperColors: Colors = lightColors(
        primary = Color(0xFF6D4C41),
        primaryVariant = Color(0xFF5D4037),
        secondary = Color(0xFF00796B),
        background = Color(0xFFEFE3C8),
        surface = Color(0xFFFBF4E2),
        onPrimary = Color(0xFFFFF8EC),
        onSecondary = Color(0xFFF2FFFC),
        onBackground = Color(0xFF2B2118),
        onSurface = Color(0xFF2B2118),
    )

    /** Card backing — "paper pinned to cork". */
    val cardBacking = Color(0xFFFBF4E2)

    /** Note-card paper, light and dark variants. */
    val noteBacking = Color(0xFFFFF3B8)
    val noteBackingDark = Color(0xFF474021)
    val noteInk = Color(0xFF3A3315)
    val noteInkDark = Color(0xFFF0E7BE)

    fun parseColor(hex: String?): Color? =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)
            ?.let { Color(0xFF000000 or it) }
}
