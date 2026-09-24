package de.creaflect.sketch

import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The papers on offer: how deep the tooth is ([floor] is how much of the lead the deepest pit
 * still takes), how far apart its pits and peaks are ([contrast] stretches the noise about its
 * middle — summed octaves crowd it there), and how coarse (the noise's cells across a tile,
 * with their weights). One grain each, made when first wanted; a sketch keeps its paper by name.
 */
enum class Paper(val label: String, val floor: Float, val contrast: Float, val octaves: List<Pair<Int, Float>>) {
    /** Hot-pressed: a shallow, fine tooth; lines stay whole. */
    SMOOTH("smooth", 0.45f, 0.6f, listOf(32 to 0.1f, 85 to 0.3f, 256 to 0.6f)),
    /** Cartridge paper: one- and three-pixel tooth with a little eight-pixel unevenness. */
    MEDIUM("medium", 0.2f, 1f, listOf(32 to 0.2f, 85 to 0.35f, 256 to 0.45f)),
    /** Cold-pressed: deep and coarse, two to sixteen pixels; a light line breaks up on it. */
    ROUGH("rough", 0.08f, 1.8f, listOf(16 to 0.3f, 43 to 0.35f, 128 to 0.35f));

    val grain: PaperGrain by lazy { PaperGrain(floor = floor, contrast = contrast, octaves = octaves) }

    companion object {
        /** The paper of that name, or medium for a name unknown or missing. */
        fun of(name: String?): Paper = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * The paper's tooth: a tileable grey noise in page space — fixed to the page, never to the
 * stroke — that every dab is multiplied with. Light pressure marks only the tops of the tooth,
 * heavy pressure fills the valleys (LEARNINGS L2). Value noise in three octaves, scaled into
 * [floor]..1 so no part of the paper is fully blind to the lead. Deterministic for a seed, so a
 * replayed sketch renders pixel for pixel the same.
 */
class PaperGrain(
    val size: Int = 256,
    seed: Long = 7L,
    floor: Float = 0.2f,
    private val contrast: Float = 1f,
    private val octaves: List<Pair<Int, Float>> = Paper.MEDIUM.octaves,
) : AutoCloseable {
    val image: Image
    /** The same tooth as a shade for the screen: black at alpha 1 − g; see [shade]. */
    val shadow: Image

    init {
        val values = noise(size, seed)
        val bytes = ByteArray(size * size * 4)
        val shade = ByteArray(size * size * 4)
        var i = 0
        for (v in values) {
            val g = (floor + (1f - floor) * v).coerceIn(0f, 1f)
            val b = (g * 255f + 0.5f).toInt().toByte()
            // Premultiplied BGRA, white at alpha g: r = g = b = a = g. Screen and modulate then
            // act on alpha and colour alike, which is what the dab shader relies on.
            bytes[i] = b; bytes[i + 1] = b; bytes[i + 2] = b; bytes[i + 3] = b
            // And black at alpha 1 − g: the deeper the pit, the darker its shade.
            shade[i + 3] = ((1f - g) * 255f + 0.5f).toInt().toByte()
            i += 4
        }
        image = Image.makeRaster(ImageInfo.makeN32Premul(size, size), bytes, size * 4)
        shadow = Image.makeRaster(ImageInfo.makeN32Premul(size, size), shade, size * 4)
    }

    /** The grain as a shader in page coordinates, repeating. */
    fun shader(): Shader = image.makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, null)

    /**
     * Draws the tooth as a faint shade over a rectangle of the canvas — for showing the paper on
     * screen, where a page pixel is [scale] canvas pixels: black at an alpha rising where the
     * tooth is low, [strength] at most, so the pits read darker and the peaks the graphite lands
     * on stay light. ([image] itself is white at alpha g; multiplied over white paper it shows
     * nothing at all, which is how the first screen drew it.)
     */
    fun shade(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, scale: Float, strength: Float = SHADE, sampling: SamplingMode = SamplingMode.DEFAULT) {
        val step = size * scale
        if (step <= 0f || strength <= 0f) return
        canvas.save()
        canvas.clipRect(Rect.makeLTRB(left, top, right, bottom))
        val tile = Rect.makeWH(size.toFloat(), size.toFloat())
        Paint().use { paint ->
            paint.setAlphaf(strength)
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    canvas.drawImageRect(shadow, tile, Rect.makeXYWH(x, y, step, step), sampling, paint, true)
                    x += step
                }
                y += step
            }
        }
        canvas.restore()
    }

    override fun close() {
        image.close()
        shadow.close()
    }

    private fun noise(size: Int, seed: Long): FloatArray {
        val out = FloatArray(size * size)
        // Cells across the tile and their weight, per paper. The first grain had 8- to 32-pixel
        // blobs, which read as stains, not paper; tooth is one to a few pixels.
        for (y in 0 until size) for (x in 0 until size) {
            var v = 0f
            for ((cells, weight) in octaves) v += weight * valueNoise(x, y, size, cells, seed + cells)
            // Contrast stretches the noise about its middle: pits and peaks farther apart on rough paper.
            out[y * size + x] = (0.5f + (v - 0.5f) * contrast).coerceIn(0f, 1f)
        }
        return out
    }

    /** Bilinear value noise on a lattice of [cells] × [cells] that wraps, so the tile tiles. */
    private fun valueNoise(x: Int, y: Int, size: Int, cells: Int, seed: Long): Float {
        val fx = x.toFloat() / size * cells
        val fy = y.toFloat() / size * cells
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = smooth(fx - x0)
        val ty = smooth(fy - y0)
        fun lattice(cx: Int, cy: Int): Float = hash(((cx % cells) + cells) % cells, ((cy % cells) + cells) % cells, seed)
        val top = lattice(x0, y0) + (lattice(x0 + 1, y0) - lattice(x0, y0)) * tx
        val bottom = lattice(x0, y0 + 1) + (lattice(x0 + 1, y0 + 1) - lattice(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    private fun hash(x: Int, y: Int, seed: Long): Float {
        var h = seed xor (x.toLong() * 374761393L) xor (y.toLong() * 668265263L)
        h = (h xor (h ushr 13)) * 1274126177L
        h = h xor (h ushr 16)
        return ((h and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat())
    }

    companion object {
        /** The medium paper's grain — one per paper for the whole app, made when first wanted. */
        val default: PaperGrain get() = Paper.MEDIUM.grain

        /** How dark the shade gets in the deepest pit: faint, so the page still reads as white paper. */
        const val SHADE = 0.1f
    }
}

/**
 * Puts one dab on the canvas. A dab is a disc of the point's width, its edge softened for a
 * soft lead, filled by a shader that is the paper grain screened with the pressure (so pressure
 * raises the floor: `g' = 1 − (1 − g)(1 − p)`) and modulated by the brush colour at the dab's
 * alpha. Dabs land at a third of the width, so a point is covered by about three of them;
 * [dabAlpha] turns the point's target darkness into what each dab must contribute for their
 * stack to reach it. An eraser is the same dab taking coverage away instead. A tilted pen
 * draws with the side of the lead: the dab is the lead's width across the tilt's direction and
 * stretched along it, spaced by its extent along the stroke so a band is as dark whichever way
 * it is drawn, and less of the pressure fills the tooth's valleys.
 */
class StampRenderer(private val grain: PaperGrain = PaperGrain.default) {
    /** The paper as a shader: made once, since every dab of every stroke uses the same paper. */
    private val paper: Shader by lazy { grain.shader() }

    /**
     * Where the next dab goes: a third of the dab's extent along the stroke, never under half a
     * pixel — so a dab on its side, lying across the stroke or along it, is overlapped by its
     * neighbours as a round one is.
     */
    fun spacing(point: StrokePoint, model: PencilModel): Float = (2f * alongStroke(point, model) / 3f).coerceAtLeast(0.5f)

    /** Half of the dab's extent along the stroke's heading: the ellipse's support in that direction. */
    private fun alongStroke(point: StrokePoint, model: PencilModel): Float {
        val short = point.width / 2f
        val tilt = point.tilt
        val long = short * model.stretch(tilt)
        if (long <= short || tilt <= 0f) return short
        val head = hypot(point.headX, point.headY)
        if (head <= 0f) return short
        // The cosine of the angle between the heading and the tilt's direction.
        val c = (point.headX * point.tiltX + point.headY * point.tiltY) / (head * tilt)
        val c2 = (c * c).coerceIn(0f, 1f)
        return sqrt(long * long * c2 + short * short * (1f - c2))
    }

    /** Per-dab alpha so that [DABS_PER_POINT] overlapping dabs reach the point's [StrokePoint.alpha]. */
    fun dabAlpha(target: Float): Float = 1f - (1f - target.coerceIn(0f, 0.999f)).pow(1f / DABS_PER_POINT)

    /** One dab on the page, in every tile it touches. */
    fun dab(surface: SketchSurface, brush: Brush, point: StrokePoint, eraser: Boolean = false) {
        val radius = (point.width / 2f).coerceAtLeast(0.35f)
        val alpha = dabAlpha(point.alpha)
        val model = if (eraser) Pencils.ERASER else brush.model
        val edge = model.edge
        // The side of the lead: the mark is the lead's own width across the tilt's direction and
        // [stretch] times that along it. And the side skims the tooth — less pressure fills it.
        val stretch = model.stretch(point.tilt)
        val along = radius * stretch
        val filling = point.pressure * (1f - TILT_GRAIN * point.tilt)
        val sigma = if (edge > 0f) (radius * edge).coerceAtLeast(0.3f) else 0f
        // A blurred edge reaches about three sigma past the disc.
        val reach = along + 3f * sigma + 1.5f
        Shader.makeColor(Rasterizer.withAlpha(0xFFFFFF, filling)).use { pressure ->
            Shader.makeBlend(BlendMode.SCREEN, paper, pressure).use { toothed ->
                Shader.makeColor(Rasterizer.withAlpha(brush.color, alpha)).use { tint ->
                    Shader.makeBlend(BlendMode.MODULATE, toothed, tint).use { shader ->
                        Paint().use { paint ->
                            paint.shader = shader
                            paint.isAntiAlias = true
                            if (eraser) paint.blendMode = BlendMode.DST_OUT
                            val blur = if (sigma > 0f) MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma) else null
                            try {
                                if (blur != null) paint.maskFilter = blur
                                surface.paint(point.x - reach, point.y - reach, point.x + reach, point.y + reach) { canvas ->
                                    if (stretch > 1.001f) {
                                        canvas.save()
                                        canvas.translate(point.x, point.y)
                                        canvas.rotate(Math.toDegrees(point.tiltAngle.toDouble()).toFloat())
                                        canvas.drawOval(Rect.makeLTRB(-along, -radius, along, radius), paint)
                                        canvas.restore()
                                    } else {
                                        canvas.drawCircle(point.x, point.y, radius, paint)
                                    }
                                }
                            } finally {
                                blur?.close()
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DABS_PER_POINT = 3f

        /** How much of the pressure a lead on its side loses for filling the tooth's valleys, at a full tilt. */
        const val TILT_GRAIN = 0.6f
    }
}
