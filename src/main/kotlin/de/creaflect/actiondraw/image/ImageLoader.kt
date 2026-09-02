package de.creaflect.actiondraw.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File

/**
 * Decodes an image file into a Compose [ImageBitmap] via [ImageDecoder]. Call off the main thread;
 * throws when the file cannot be decoded (callers wrap this in `runCatching`).
 */
object ImageLoader {
    fun load(file: File): ImageBitmap =
        (ImageDecoder.decode(file) ?: error("No decoder for ${file.name}")).toComposeImageBitmap()
}
