package de.creaflect.actiondraw.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Loads small preview bitmaps for the picture-picker grid. Decodes the file and downscales it to
 * [maxSize] on the long edge, so a big folder of large photos stays cheap to keep in memory.
 * Call off the main thread; returns null for unreadable files.
 */
object Thumbnails {
    fun load(file: File, maxSize: Int = 192): ImageBitmap? = runCatching {
        val img = Image.makeFromEncoded(file.readBytes())
        val scale = maxSize.toFloat() / max(img.width, img.height)
        if (scale >= 1f) return@runCatching img.toComposeImageBitmap() // already small
        val w = (img.width * scale).roundToInt().coerceAtLeast(1)
        val h = (img.height * scale).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(img, Rect.makeWH(w.toFloat(), h.toFloat()))
        surface.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull()
}
