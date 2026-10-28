package de.creaflect.actiondraw.image

import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.File
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
        // Fallback: whatever ImageIO can read, re-encoded as PNG so Skia can take it from there.
        return runCatching {
            val buffered = ImageIO.read(file) ?: return null
            val png = ByteArrayOutputStream().also { ImageIO.write(buffered, "png", it) }.toByteArray()
            Image.makeFromEncoded(png)
        }.getOrNull()
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
