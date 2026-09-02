package de.creaflect.actiondraw.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest

/**
 * Disk-backed thumbnail cache: downscaled previews are stored as PNGs under `~/.actiondraw/thumbs`
 * so a board (or the picker) full of large photos opens fast after the first time. The cache key
 * includes size and mtime, so an edited image misses cleanly and re-renders. All IO is
 * best-effort — on any failure this degrades to the uncached [Thumbnails] path.
 *
 * [dir] is injectable so tests never touch the real user cache.
 */
class ThumbCache(private val dir: File = defaultDir()) {
    fun load(file: File, maxSize: Int = 192): ImageBitmap? {
        val cached = File(dir, cacheName(file, maxSize))
        runCatching {
            if (cached.isFile) return Image.makeFromEncoded(cached.readBytes()).toComposeImageBitmap()
        }
        val thumb = Thumbnails.loadSkia(file, maxSize) ?: return null
        runCatching {
            dir.mkdirs()
            thumb.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { cached.writeBytes(it) }
        }
        return thumb.toComposeImageBitmap()
    }

    /** Cache file name for [file] at [maxSize]; changes whenever the file's size or mtime does. */
    fun cacheName(file: File, maxSize: Int): String {
        val id = "${file.absolutePath}|${file.length()}|${file.lastModified()}|$maxSize"
        val digest = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".png"
    }

    companion object {
        private fun defaultDir(): File =
            File(File(System.getProperty("user.home") ?: ".", ".actiondraw"), "thumbs")
    }
}
