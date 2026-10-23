package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.image.SheetCell
import de.creaflect.actiondraw.image.Thumbnails
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * Renders a board (or a selection) as one printable PNG: a grid of thumbnails with their captions
 * and a title line. For the half of the practice that happens on paper — print the sheet, pin it
 * up, draw from it.
 */
internal object ContactSheet {
    private const val CELL = 320
    private const val PAD = 16
    private const val CAPTION = 26
    private const val HEADER = 64

    /**
     * Draws [pictures] as a sheet [columns] wide and writes a PNG to [target]. Returns false if
     * there was nothing to draw or the file could not be written.
     */
    fun write(title: String, pictures: List<SheetCell>, target: File, columns: Int = 4): Boolean {
        if (pictures.isEmpty()) return false

        val cols = columns.coerceIn(1, 8)
        val rows = ceil(pictures.size / cols.toFloat()).toInt()
        val width = PAD + cols * (CELL + PAD)
        val height = HEADER + PAD + rows * (CELL + CAPTION + PAD)

        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        val paper = Paint().apply { color = 0xFFFFFFFF.toInt() }
        val ink = Paint().apply { color = 0xFF222222.toInt() }
        val faint = Paint().apply { color = 0xFF777777.toInt() }
        val frame = Paint().apply {
            color = 0xFFDDDDDD.toInt()
            mode = org.jetbrains.skia.PaintMode.STROKE
            strokeWidth = 1f
        }
        val typeface: Typeface? = FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL)
        val titleFont = Font(typeface, 28f)
        val captionFont = Font(typeface, 14f)

        canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), paper)
        canvas.drawString(title, PAD.toFloat(), 40f, titleFont, ink)
        canvas.drawString(
            "${pictures.size} pictures · ActionDraw",
            PAD.toFloat(),
            58f,
            captionFont,
            faint,
        )

        pictures.forEachIndexed { index, picture ->
            val col = index % cols
            val row = index / cols
            val x = PAD + col * (CELL + PAD)
            val y = HEADER + PAD + row * (CELL + CAPTION + PAD)
            val cell = Rect.makeXYWH(x.toFloat(), y.toFloat(), CELL.toFloat(), CELL.toFloat())
            canvas.drawRect(cell, frame)

            val thumb = picture.file?.let { Thumbnails.load(it, maxSize = CELL) }
            if (thumb != null) {
                // Fit the picture into its cell, centred.
                val scale = min(CELL.toFloat() / thumb.width, CELL.toFloat() / thumb.height)
                val w = thumb.width * scale
                val h = thumb.height * scale
                canvas.drawImageRect(
                    thumb,
                    Rect.makeXYWH(x + (CELL - w) / 2, y + (CELL - h) / 2, w, h),
                )
            }
            canvas.drawString(picture.caption.take(46), x.toFloat(), (y + CELL + 18).toFloat(), captionFont, ink)
        }

        return runCatching {
            val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG) ?: return false
            target.writeBytes(data.bytes)
            true
        }.getOrDefault(false)
    }
}
