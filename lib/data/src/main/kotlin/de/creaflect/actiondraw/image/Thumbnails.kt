package de.creaflect.actiondraw.image

import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small pictures: decodes a file and downscales it to [maxSize] on the long edge, so a big folder
 * of large photos stays cheap to hold. Call off the main thread; null for unreadable files.
 */
internal object Thumbnails {
    fun load(file: File, maxSize: Int = 192): Image? = runCatching {
        val img = ImageDecoder.decode(file) ?: return@runCatching null
        val scale = maxSize.toFloat() / max(img.width, img.height)
        if (scale >= 1f) return@runCatching img // already small
        val w = (img.width * scale).roundToInt().coerceAtLeast(1)
        val h = (img.height * scale).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(img, Rect.makeWH(w.toFloat(), h.toFloat()))
        surface.makeImageSnapshot()
    }.getOrNull()
}
