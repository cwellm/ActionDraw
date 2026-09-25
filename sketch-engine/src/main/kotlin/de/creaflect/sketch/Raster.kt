package de.creaflect.sketch

import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Surface
import kotlin.math.floor

/**
 * The plain, path-based pencil: each pair of points a round-capped line at their mean width and
 * alpha. It was the exploration's step 2 and stays as the lightest hard pencil and as the
 * reference the stamp renderer ([StampRenderer]) is compared against.
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
 * A page's strokes at one moment, tile for tile — what undo goes back to. Holding one costs only
 * the tiles drawn on since: an unchanged tile shares its pixels with the page until it changes.
 */
class PageSnapshot internal constructor(internal val tiles: Array<Image>) : AutoCloseable {
    override fun close() = tiles.forEach { it.close() }
}

/**
 * The page: a transparent layer of strokes over a paper colour, kept as tiles of [tileSize]
 * pixels. Strokes go into the layer (which is what lets an eraser take graphite away again); a
 * drawing touches only the tiles under it, so the screen re-uploads only those ([tileImage] keeps
 * every other tile's picture as it was) and an undo snapshot costs only what changed. The UI
 * draws the paper itself and the tiles over it; [compose] flattens both for export. Tests read
 * [darkness] as it would look on the paper.
 */
class SketchSurface(
    val width: Int,
    val height: Int,
    val paper: Int = 0xFFFFFFFF.toInt(),
    val tileSize: Int = TILE,
) : AutoCloseable {
    val columns: Int = (width + tileSize - 1) / tileSize
    val rows: Int = (height + tileSize - 1) / tileSize
    val tileCount: Int get() = columns * rows

    private val tiles: Array<Surface> = Array(columns * rows) { i ->
        Surface.makeRaster(ImageInfo.makeN32Premul(tileWidth(i), tileHeight(i)))
    }
    /** The picture of each tile the screen was last given, until the tile is drawn on again. */
    private val shown = arrayOfNulls<Image>(columns * rows)

    init {
        clear()
    }

    fun tileLeft(i: Int): Int = (i % columns) * tileSize
    fun tileTop(i: Int): Int = (i / columns) * tileSize
    fun tileWidth(i: Int): Int = minOf(tileSize, width - tileLeft(i))
    fun tileHeight(i: Int): Int = minOf(tileSize, height - tileTop(i))

    /**
     * Draws into every tile the page rectangle ([left], [top])–([right], [bottom]) touches; [block]
     * gets each tile's canvas already translated to page coordinates, so a paper-space shader
     * lines up across tiles. The tile's shown picture is let go first, so the draw need not copy
     * the tile to keep it.
     */
    fun paint(left: Float, top: Float, right: Float, bottom: Float, block: (Canvas) -> Unit) {
        val c0 = floor(left / tileSize).toInt().coerceAtLeast(0)
        val c1 = floor(right / tileSize).toInt().coerceAtMost(columns - 1)
        val r0 = floor(top / tileSize).toInt().coerceAtLeast(0)
        val r1 = floor(bottom / tileSize).toInt().coerceAtMost(rows - 1)
        if (c0 > c1 || r0 > r1) return
        for (r in r0..r1) for (c in c0..c1) {
            val i = r * columns + c
            release(i)
            val canvas = tiles[i].canvas
            canvas.save()
            canvas.translate(-(c * tileSize).toFloat(), -(r * tileSize).toFloat())
            block(canvas)
            canvas.restore()
        }
    }

    fun clear() {
        for (i in tiles.indices) {
            release(i)
            tiles[i].canvas.clear(0)
        }
    }

    /** A whole stroke with the path-based pencil. */
    fun draw(stroke: Stroke) {
        if (stroke.isEmpty) return
        val pad = stroke.points.maxOf { it.width } / 2f + 2f
        paint(
            stroke.points.minOf { it.x } - pad, stroke.points.minOf { it.y } - pad,
            stroke.points.maxOf { it.x } + pad, stroke.points.maxOf { it.y } + pad,
        ) { Rasterizer.draw(it, stroke) }
    }

    fun drawSegment(brush: Brush, from: StrokePoint, to: StrokePoint) {
        val pad = maxOf(from.width, to.width) / 2f + 2f
        paint(minOf(from.x, to.x) - pad, minOf(from.y, to.y) - pad, maxOf(from.x, to.x) + pad, maxOf(from.y, to.y) + pad) {
            Rasterizer.segment(it, brush.color, from, to)
        }
    }

    fun drawDot(brush: Brush, at: StrokePoint) {
        val pad = at.width / 2f + 2f
        paint(at.x - pad, at.y - pad, at.x + pad, at.y + pad) { Rasterizer.dot(it, brush.color, at) }
    }

    /** Tile [i]'s current picture, for the screen: the same object until the tile is drawn on. */
    fun tileImage(i: Int): Image = shown[i] ?: tiles[i].makeImageSnapshot().also { shown[i] = it }

    /** The layer as it is now, for undo. */
    fun snapshot(): PageSnapshot = PageSnapshot(Array(tiles.size) { tiles[it].makeImageSnapshot() })

    /** Puts an earlier [snapshot] back, tile for tile. */
    fun restore(snapshot: PageSnapshot) {
        require(snapshot.tiles.size == tiles.size) { "a snapshot of another page" }
        Paint().use { copy ->
            copy.blendMode = BlendMode.SRC
            for (i in tiles.indices) {
                release(i)
                tiles[i].canvas.drawImage(snapshot.tiles[i], 0f, 0f, copy)
            }
        }
    }

    /** Paper and strokes flattened into one opaque picture — what is saved. */
    fun compose(): Image {
        val flat = Surface.makeRasterN32Premul(width, height)
        flat.canvas.clear(paper)
        for (i in tiles.indices) {
            tiles[i].makeImageSnapshot().use { flat.canvas.drawImage(it, tileLeft(i).toFloat(), tileTop(i).toFloat()) }
        }
        return flat.makeImageSnapshot().also { flat.close() }
    }

    /** The layer's unpremultiplied ARGB at ([x], [y]) — for tests, not for drawing. */
    fun pixel(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "($x, $y) is off the page" }
        val i = (y / tileSize) * columns + (x / tileSize)
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32Premul(1, 1))
        check(tiles[i].readPixels(bitmap, x - tileLeft(i), y - tileTop(i))) { "readPixels failed at $x,$y" }
        return bitmap.getColor(0, 0).also { bitmap.close() }
    }

    /** How dark ([x], [y]) looks on the paper: 0 = bare paper, 1 = solid black. */
    fun darkness(x: Int, y: Int): Float {
        val c = pixel(x, y)
        val a = ((c ushr 24) and 0xFF) / 255f
        val lum = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / (3f * 255f)
        val paperLum = (((paper shr 16) and 0xFF) + ((paper shr 8) and 0xFF) + (paper and 0xFF)) / (3f * 255f)
        val shown = a * lum + (1f - a) * paperLum
        return 1f - shown
    }

    override fun close() {
        for (i in tiles.indices) release(i)
        tiles.forEach { it.close() }
    }

    private fun release(i: Int) {
        shown[i]?.close()
        shown[i] = null
    }

    companion object {
        const val TILE = 256
    }
}
