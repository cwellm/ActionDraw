package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.image.Thumbnails
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File

/**
 * The colours a picture is actually made of — handy when a board is a colour reference and you
 * want the scheme rather than the picture.
 *
 * The image is decoded small (via [Thumbnails]), its pixels dropped into a coarse 4×4×4 colour
 * cube, and the fullest buckets returned as their own average colour. Cheap, deterministic, and
 * good enough to read a palette off a photograph; near-greyscale pictures simply return greys.
 */
object Palette {
    private const val BITS = 2 // 4 levels per channel
    private const val SIDE = 1 shl BITS

    /** Up to [count] dominant colours, most common first, as `0xRRGGBB` values. */
    fun of(file: File, count: Int = 6): List<Int> {
        val image = Thumbnails.loadSkia(file, maxSize = 96) ?: return emptyList()
        val info = ImageInfo(image.width, image.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val bitmap = Bitmap().apply { allocPixels(info) }
        if (!image.readPixels(bitmap, 0, 0)) return emptyList()
        val pixels = bitmap.readPixels() ?: return emptyList()

        val counts = IntArray(SIDE * SIDE * SIDE)
        val sums = Array(SIDE * SIDE * SIDE) { IntArray(3) }
        var i = 0
        while (i + 3 < pixels.size) {
            val r = pixels[i].toInt() and 0xFF
            val g = pixels[i + 1].toInt() and 0xFF
            val b = pixels[i + 2].toInt() and 0xFF
            val alpha = pixels[i + 3].toInt() and 0xFF
            i += 4
            if (alpha < 128) continue
            val bucket = (r shr (8 - BITS)) * SIDE * SIDE + (g shr (8 - BITS)) * SIDE + (b shr (8 - BITS))
            counts[bucket]++
            sums[bucket][0] += r
            sums[bucket][1] += g
            sums[bucket][2] += b
        }

        return counts.indices
            .filter { counts[it] > 0 }
            .sortedByDescending { counts[it] }
            .take(count)
            .map { bucket ->
                val n = counts[bucket]
                val r = sums[bucket][0] / n
                val g = sums[bucket][1] / n
                val b = sums[bucket][2] / n
                (r shl 16) or (g shl 8) or b
            }
    }

    /** `#rrggbb`, for showing a swatch's value or copying it into a paint program. */
    fun hex(color: Int): String = "#%06x".format(color and 0xFFFFFF)
}
