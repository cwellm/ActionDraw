package de.creaflect.actiondraw.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The atelier's materials, drawn in code (CONCEPT.md, *Materials*): the grain of the table, one
 * lamp's shadows, and the small things on the table — paint dabs, porcelain wells, tape, pins, a
 * pencil. Each is a plain [DrawScope] function, so a test can draw it into a bitmap and read the
 * pixels back.
 */
object Materials {
    /**
     * Per-pixel noise in two-pixel clumps, white where it is light and black where it is dark,
     * at an alpha of at most [STRENGTH]: drawn over anything, it adds a tooth without a tint. A
     * tile of white noise wraps by itself.
     */
    internal const val GRAIN_SKSL = """
uniform float strength;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

half4 main(float2 xy) {
    float2 cell = floor(xy);
    float n = hash(cell) * 0.6 + hash(floor(cell / 2.0) + 17.0) * 0.4;
    float d = n - 0.5;
    float a = abs(d) * 2.0 * strength;
    half3 c = d > 0.0 ? half3(1.0) : half3(0.0);
    return half4(c * a, a);
}
"""

    /** How strong the table's grain is: felt more than seen. */
    const val STRENGTH = 0.06f

    const val GRAIN_TILE = 256

    private val grain: ImageBitmap? by lazy { runCatching { renderGrain(GRAIN_TILE, STRENGTH) }.getOrNull() }

    /** The grain as a seamless tile, made once; null if the shader cannot run. */
    fun grainTile(): ImageBitmap? = grain

    internal fun renderGrain(size: Int, strength: Float): ImageBitmap {
        val effect = RuntimeEffect.makeForShader(GRAIN_SKSL)
        val builder = RuntimeShaderBuilder(effect).also { it.uniform("strength", strength) }
        val surface = Surface.makeRasterN32Premul(size, size)
        val paint = Paint().apply { shader = builder.makeShader(null) }
        surface.canvas.drawRect(org.jetbrains.skia.Rect.makeWH(size.toFloat(), size.toFloat()), paint)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }
}

/** The table's grain over whatever this draws, from the one cached tile. */
fun Modifier.grain(): Modifier = drawWithCache {
    val tile = Materials.grainTile()
    val brush = tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    onDrawWithContent {
        drawContent()
        if (brush != null) drawRect(brush)
    }
}

/** The table's grain under whatever this draws: for a surface things lie on, like a board. */
fun Modifier.grainBehind(): Modifier = drawWithCache {
    val tile = Materials.grainTile()
    val brush = tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    onDrawBehind { if (brush != null) drawRect(brush) }
}

/**
 * How far a thing stands off the table. One lamp, at the top left, casts every shadow: the
 * higher a thing is, the further its shadow falls to the bottom right and the softer it gets.
 */
enum class Lift(val depth: Dp) {
    RESTING(2.dp),
    LIFTED(10.dp),
    HELD(18.dp),
}

/** A shadow of [path] cast by the lamp, for something [depth] px off the table. */
fun DrawScope.drawLampShadow(path: Path, depth: Float, alpha: Float = 0.45f) {
    val paint = Paint().apply {
        color = Color.Black.copy(alpha = alpha).toArgb()
        isAntiAlias = true
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, depth * 0.6f + 0.5f)
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.translate(depth * 0.35f, depth * 0.7f)
        native.drawPath(path.asSkiaPath(), paint)
        native.restore()
    }
}

/** The lamp's shadow under this element, in the outline of [shape]. */
fun Modifier.lampShadow(lift: Lift, shape: Shape = RectangleShape, alpha: Float = 0.45f): Modifier = drawBehind {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    drawLampShadow(path, lift.depth.toPx(), alpha)
}

/**
 * A dab of paint as a closed, slightly irregular blob filling [size]: seven points round an
 * ellipse, each a little in or out, joined by a smooth closed curve. The same [seed] always gives
 * the same dab, so a room's dab keeps its shape.
 */
fun dabPath(seed: Int, size: Size, topLeft: Offset = Offset.Zero): Path {
    val rnd = Random(seed.toLong() * 7919L + 13L)
    val n = 7
    val rx = size.width / 2f
    val ry = size.height / 2f
    val c = topLeft + Offset(rx, ry)
    val points = List(n) { i ->
        val a = 2 * PI * i / n + rnd.nextDouble() * 0.35
        val r = 0.86 + rnd.nextDouble() * 0.14
        c + Offset((rx * r * cos(a)).toFloat(), (ry * r * sin(a)).toFloat())
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until n) {
            // Catmull-Rom through the points, as cubic Béziers.
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            val c1 = p1 + (p2 - p0) / 6f
            val c2 = p2 - (p3 - p1) / 6f
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        close()
    }
}

/**
 * A dab of [pigment] in the rectangle at [topLeft], [size]. Wet paint is the full mass, darker at
 * its edge, with the lamp's highlight on it; dry paint has gone pale and matte.
 */
fun DrawScope.drawDab(topLeft: Offset, size: Size, pigment: Pigment, seed: Int, wet: Boolean = true) {
    val path = dabPath(seed, size, topLeft)
    val mass = if (wet) pigment.mass else lerp(pigment.mass, Color(0xFFCFC8BC), 0.55f)
    val edge = lerp(mass, Color.Black, if (wet) 0.32f else 0.12f)
    val centre = topLeft + Offset(size.width * 0.42f, size.height * 0.38f)
    drawPath(path, Brush.radialGradient(0f to mass, 0.7f to mass, 1f to edge, center = centre, radius = size.maxDimension * 0.62f))
    if (wet) {
        val gloss = Size(size.width * 0.24f, size.height * 0.15f)
        drawOval(Color.White.copy(alpha = 0.72f), topLeft = topLeft + Offset(size.width * 0.24f, size.height * 0.19f), size = gloss)
    }
}

/**
 * A porcelain well, sunk into a palette: lit from the top left, so its inner wall is in shadow
 * along the top and catches the light along the bottom.
 */
fun DrawScope.drawWell(center: Offset, radius: Float) {
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFDED6CA), 0.68f to Color(0xFFEBE5DC), 1f to Color(0xFFF7F4EE), center = center + Offset(0f, radius * 0.18f), radius = radius),
        radius,
        center,
    )
    drawCircle(
        Brush.verticalGradient(0f to Color(0x55463728), 0.5f to Color(0x00463728), startY = center.y - radius, endY = center.y + radius),
        radius,
        center,
    )
    drawArc(
        Color.White.copy(alpha = 0.9f),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = center - Offset(radius, radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = radius * 0.05f),
    )
}

/** Masking tape's colour: paper you can see through. */
val TapeColor = Color(0xD6E8DCC0)

/** A strip of masking tape, [size] long and wide, turned by [degrees] about [center], torn at both ends. */
fun DrawScope.drawTape(center: Offset, size: Size, degrees: Float, seed: Int, color: Color = TapeColor) {
    val rnd = Random(seed.toLong())
    val w = size.width
    val h = size.height
    val left = center.x - w / 2
    val top = center.y - h / 2
    val teeth = 5
    val path = Path().apply {
        moveTo(left, top)
        lineTo(left + w, top)
        for (i in 1..teeth) lineTo(left + w - rnd.nextFloat() * w * 0.04f, top + h * i / teeth)
        lineTo(left, top + h)
        for (i in teeth - 1 downTo 0) lineTo(left + rnd.nextFloat() * w * 0.04f, top + h * i / teeth)
        close()
    }
    rotate(degrees, center) {
        drawPath(path, color)
        drawPath(path, Color(0x22000000), style = Stroke(width = 0.6f))
    }
}

/** A push-pin's head of [pigment], its shadow cast to the bottom right. */
fun DrawScope.drawPin(center: Offset, radius: Float, pigment: Pigment) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius * 0.95f, center + Offset(radius * 0.45f, radius * 0.6f))
    drawCircle(
        Brush.radialGradient(0f to pigment.glow, 0.25f to pigment.mass, 1f to lerp(pigment.mass, Color.Black, 0.35f), center = center - Offset(radius * 0.35f, radius * 0.35f), radius = radius * 1.4f),
        radius,
        center,
    )
    drawOval(Color.White.copy(alpha = 0.7f), topLeft = center - Offset(radius * 0.6f, radius * 0.62f), size = Size(radius * 0.55f, radius * 0.38f))
}

/**
 * A hexagonal pencil lying on the table, its point at [tip], [length] long and [thickness] thick,
 * turned by [degrees]: graphite, sharpened wood, a lacquered barrel in three facets, a ferrule and
 * a rubber.
 */
fun DrawScope.drawPencil(tip: Offset, length: Float, thickness: Float, degrees: Float, lacquer: Color) {
    val t = thickness
    val y = tip.y - t / 2
    rotate(degrees, tip) {
        val shadow = Path().apply { addRect(Rect(tip.x, y, tip.x + length, y + t)) }
        drawLampShadow(shadow, t * 0.8f, alpha = 0.5f)
        translate(tip.x, y) {
            val point = t * 0.8f
            val cone = t * 2.4f
            val ferrule = t * 1.3f
            val rubber = t * 1.1f
            val barrel = length - point - cone - ferrule - rubber
            // Graphite point and sharpened wood.
            drawPath(Path().apply { moveTo(0f, t / 2); lineTo(point, t * 0.31f); lineTo(point, t * 0.69f); close() }, Color(0xFF2E2B28))
            drawPath(
                Path().apply { moveTo(point, t * 0.31f); lineTo(point + cone, 0f); lineTo(point + cone, t); lineTo(point, t * 0.69f); close() },
                Brush.verticalGradient(listOf(Color(0xFFEED4AE), Color(0xFFD9B585), Color(0xFFBE9663)), 0f, t),
            )
            // Three facets of lacquer, lit from above.
            val x0 = point + cone
            drawRect(lerp(lacquer, Color.White, 0.12f), Offset(x0, 0f), Size(barrel, t * 0.34f))
            drawRect(lacquer, Offset(x0, t * 0.34f), Size(barrel, t * 0.33f))
            drawRect(lerp(lacquer, Color.Black, 0.25f), Offset(x0, t * 0.67f), Size(barrel, t * 0.33f))
            // Ferrule in bands, then the rubber.
            val x1 = x0 + barrel
            val bands = 5
            for (i in 0 until bands) {
                drawRect(
                    if (i % 2 == 0) Color(0xFFC8C2B9) else Color(0xFF8F8A83),
                    Offset(x1 + ferrule * i / bands, -t * 0.05f),
                    Size(ferrule / bands, t * 1.1f),
                )
            }
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFE5B3AE), Color(0xFFC68985)), 0f, t),
                Offset(x1 + ferrule, 0f),
                Size(rubber, t),
                androidx.compose.ui.geometry.CornerRadius(t * 0.35f),
            )
        }
    }
}
