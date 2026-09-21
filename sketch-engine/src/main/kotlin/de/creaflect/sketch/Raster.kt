package de.creaflect.sketch

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Surface

/**
 * Puts a stroke on a canvas. This is the path-based pencil of the exploration's step 2: each
 * pair of points is a round-capped line at their mean width and alpha. The stamp rasteriser with
 * grain and paper tooth (LEARNINGS L2, L3) replaces it in the pencil study; the interface stays.
 */
object Rasterizer {
    fun draw(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        when (points.size) {
            0 -> return
            1 -> dot(canvas, stroke.brush.color, points[0])
            else -> points.zipWithNext { a, b -> segment(canvas, stroke.brush.color, a, b) }
        }
    }

    /** One step of a stroke being drawn live: [a] was the previous point, [b] the new one. */
    fun segment(canvas: Canvas, color: Int, a: StrokePoint, b: StrokePoint) {
        val width = (a.width + b.width) / 2f
        val alpha = (a.alpha + b.alpha) / 2f
        Paint().use { paint ->
            paint.color = withAlpha(color, alpha)
            paint.mode = PaintMode.STROKE
            paint.strokeWidth = width.coerceAtLeast(0.5f)
            paint.strokeCap = PaintStrokeCap.ROUND
            paint.isAntiAlias = true
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    fun dot(canvas: Canvas, color: Int, p: StrokePoint) {
        Paint().use { paint ->
            paint.color = withAlpha(color, p.alpha)
            paint.isAntiAlias = true
            canvas.drawCircle(p.x, p.y, (p.width / 2f).coerceAtLeast(0.5f), paint)
        }
    }

    /** [color]'s RGB with [alpha] (0..1) as its alpha channel. */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}

/**
 * The page: a Skia raster surface of the sketch's size, strokes drawn into it as they come. The
 * app shows [snapshot]; tests read [pixel]. Closing it frees the native memory.
 */
class SketchSurface(
    val width: Int,
    val height: Int,
    val background: Int = 0xFFFFFFFF.toInt(),
) : AutoCloseable {
    private val surface: Surface = Surface.makeRasterN32Premul(width, height)

    init {
        clear()
    }

    val canvas: Canvas get() = surface.canvas

    fun clear() {
        surface.canvas.clear(background)
    }

    fun draw(stroke: Stroke) = Rasterizer.draw(surface.canvas, stroke)

    fun drawSegment(brush: Brush, from: StrokePoint, to: StrokePoint) = Rasterizer.segment(surface.canvas, brush.color, from, to)

    fun drawDot(brush: Brush, at: StrokePoint) = Rasterizer.dot(surface.canvas, brush.color, at)

    /** The page as it is now; cheap (copy-on-write) and safe to hand to the UI. */
    fun snapshot(): Image = surface.makeImageSnapshot()

    /** The ARGB colour at ([x], [y]) — for tests, not for drawing. */
    fun pixel(x: Int, y: Int): Int {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32Premul(1, 1))
        check(surface.readPixels(bitmap, x, y)) { "readPixels failed at $x,$y" }
        return bitmap.getColor(0, 0).also { bitmap.close() }
    }

    /** How dark ([x], [y]) is, 0 = the white page, 1 = black. */
    fun darkness(x: Int, y: Int): Float {
        val c = pixel(x, y)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return 1f - (r + g + b) / (3f * 255f)
    }

    override fun close() = surface.close()
}
