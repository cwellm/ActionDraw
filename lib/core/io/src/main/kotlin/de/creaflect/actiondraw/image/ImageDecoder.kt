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

    /** Whether an ImageIO reader for [extension] is installed in this runtime. */
    fun imageIoCanRead(extension: String): Boolean =
        runCatching { ImageIO.getImageReadersBySuffix(extension).hasNext() }.getOrDefault(false)
}
