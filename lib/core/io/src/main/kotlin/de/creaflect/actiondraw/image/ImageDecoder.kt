package de.creaflect.actiondraw.image

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * Turns an image file into a Skia [Image]. Skia decodes JPEG, PNG, GIF, BMP and WebP itself;
 * anything it rejects is retried through ImageIO, so dropping an ImageIO plugin on the classpath
 * (AVIF, HEIC, …) is all it takes to teach the app another format. Returns null when neither can
 * read the file — callers show a placeholder rather than hanging on a decode that will never come.
 */
object ImageDecoder {
    fun decode(file: File): Image? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        runCatching { Image.makeFromEncoded(bytes) }.getOrNull()?.let { return it }
        // Fallback: whatever ImageIO can read, its pixels handed to Skia as they are seen.
        return runCatching { ImageIO.read(file)?.let(::fromImageIo) }.getOrNull()
    }

    /**
     * [picture] as Skia draws it: its pixels as eight bits a channel, through the picture's own
     * colour model. Not re-encoded as PNG on the way — that writes a deep picture's samples as they
     * are stored, and a 10-bit AVIF's, held in 16 bits but no larger than 1023, came out all but
     * black.
     */
    internal fun fromImageIo(picture: BufferedImage): Image {
        val w = picture.width
        val h = picture.height
        val argb = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        argb.createGraphics().apply {
            drawImage(picture, 0, 0, null)
            dispose()
        }
        val ints = (argb.raster.dataBuffer as DataBufferInt).data
        // 0xAARRGGBB, little end first: B, G, R, A.
        val bytes = ByteBuffer.allocate(ints.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(ints) }.array()
        return Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), bytes, w * 4)
    }

    /**
     * How many pixels wide and high [file] is, as [decode] would give it — turned upright as its
     * camera says — without decoding it: Skia reads only the header until the pixels are drawn.
     * What Skia rejects, ImageIO's reader measures from its header. Null when neither can read it.
     */
    fun size(file: File): Pair<Int, Int>? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        runCatching { Image.makeFromEncoded(bytes).use { it.width to it.height } }.getOrNull()?.let { return it }
        return runCatching {
            ImageIO.createImageInputStream(file).use { stream ->
                val reader = ImageIO.getImageReaders(stream).next()
                try {
                    reader.input = stream
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull()
    }

    /** Whether an ImageIO reader for [extension] is installed in this runtime. */
    fun imageIoCanRead(extension: String): Boolean =
        runCatching { ImageIO.getImageReadersBySuffix(extension).hasNext() }.getOrDefault(false)
}
